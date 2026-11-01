package com.example.adblocker;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DNS-specific bounded TCP endpoint, not a general-purpose TCP proxy.
 *
 * Handshakes, ACKs, TCP stream framing, one in-flight request per connection,
 * segmented responses and bounded retransmission are implemented in memory.
 * No packet is emitted to the network except through the existing TUN writer.
 */
public final class DnsTcpTunnel {
    static final int MAX_FLOWS = 64;
    static final int MAX_QUERY_BYTES = 4096;
    static final int RESPONSE_SEGMENT_BYTES = 1000;
    static final long IDLE_MILLIS = 30_000;
    static final long RETRANSMIT_MILLIS = 2_000;
    static final int MAX_RETRIES = 3;

    public static final class Request {
        public final String flowId;
        public final long token;
        public final byte[] dns;
        Request(String flowId, long token, byte[] dns) {
            this.flowId = flowId; this.token = token; this.dns = dns;
        }
    }

    public static final class Result {
        public final List<byte[]> replies = new ArrayList<>();
        public Request request;
    }

    private static final class Flow {
        final long token;
        DnsTcpTunnelPacket.Segment lastClient;
        long nextClient, nextServer, lastSeen;
        boolean established, pending;
        final ByteArrayOutputStream buffered = new ByteArrayOutputStream();
        byte[] outbound, lastSent;
        int nextOffset, retries;
        long lastSentAt;

        Flow(long token, DnsTcpTunnelPacket.Segment syn, long isn, long now) {
            this.token = token;
            this.lastClient = syn;
            nextClient = DnsTcpTunnelPacket.plus(syn.seq, 1);
            nextServer = DnsTcpTunnelPacket.plus(isn, 1);
            lastSeen = now;
        }
    }

    private final SecureRandom random = new SecureRandom();
    private final LinkedHashMap<String, Flow> flows = new LinkedHashMap<>(16, 0.75f, true);
    private long nextToken;

    public synchronized void clear() { flows.clear(); }

    public synchronized int activeFlows() { return flows.size(); }

    /** All inbound events are handled on the TUN thread, with bounded allocations. */
    public synchronized Result receive(byte[] packet, int size, long now) {
        Result result = new Result();
        DnsTcpTunnelPacket.Segment incoming = DnsTcpTunnelPacket.parse(packet, size);
        if (incoming == null) return result;
        Flow flow = flows.get(incoming.flowId);
        if ((incoming.flags & DnsTcpTunnelPacket.RST) != 0) {
            flows.remove(incoming.flowId);
            return result;
        }
        if ((incoming.flags & DnsTcpTunnelPacket.SYN) != 0) {
            if ((incoming.flags & DnsTcpTunnelPacket.ACK) != 0) return result;
            if (flow != null && !flow.established
                    && incoming.seq == DnsTcpTunnelPacket.plus(flow.nextClient, -1)) {
                result.replies.add(flow.lastSent);
                return result;
            }
            // Don't silently evict a live connection to accept unbounded SYN floods.
            expireOld(now);
            if (flows.size() >= MAX_FLOWS) {
                result.replies.add(DnsTcpTunnelPacket.reply(incoming, 0,
                        DnsTcpTunnelPacket.plus(incoming.seq, 1),
                        DnsTcpTunnelPacket.RST | DnsTcpTunnelPacket.ACK, new byte[0]));
                return result;
            }
            long isn = random.nextInt() & 0xffffffffL;
            Flow created = new Flow(++nextToken, incoming, isn, now);
            byte[] synAck = DnsTcpTunnelPacket.reply(incoming, isn, created.nextClient,
                    DnsTcpTunnelPacket.SYN | DnsTcpTunnelPacket.ACK, new byte[0]);
            created.lastSent = synAck;
            created.lastSentAt = now;
            created.retries = 0;
            flows.put(incoming.flowId, created);
            result.replies.add(synAck);
            return result;
        }
        if (flow == null || (incoming.flags & DnsTcpTunnelPacket.ACK) == 0) return result;
        flow.lastSeen = now;
        flow.lastClient = incoming;

        if (!flow.established) {
            if (incoming.ack != flow.nextServer) return result;
            flow.established = true;
            flow.lastSent = null;
        }

        if (incoming.ack == flow.nextServer && flow.lastSent != null) {
            // All outstanding response bytes have been acknowledged.
            flow.lastSent = null;
            flow.retries = 0;
            if (flow.outbound != null && flow.nextOffset >= flow.outbound.length)
                flow.outbound = null;
            else if (flow.outbound != null)
                sendNext(flow, result.replies, now);
        }

        if ((incoming.flags & DnsTcpTunnelPacket.FIN) != 0) {
            long ack = DnsTcpTunnelPacket.plus(incoming.seq, incoming.payload.length + 1L);
            result.replies.add(DnsTcpTunnelPacket.reply(incoming, flow.nextServer, ack,
                    DnsTcpTunnelPacket.FIN | DnsTcpTunnelPacket.ACK, new byte[0]));
            flows.remove(incoming.flowId);
            return result;
        }

        if (incoming.payload.length == 0) return result;
        if (incoming.seq != flow.nextClient) {
            // Duplicate or out-of-order TCP segment: re-ACK the last in-order byte.
            ackOnly(flow, result.replies);
            return result;
        }

        flow.nextClient = DnsTcpTunnelPacket.plus(flow.nextClient, incoming.payload.length);
        // DNS/TCP pipelining is intentionally out of scope. Reject excess data
        // explicitly instead of buffering unbounded requests.
        if (flow.pending || flow.outbound != null
                || flow.buffered.size() + incoming.payload.length > MAX_QUERY_BYTES + 2) {
            reset(flow, incoming, result.replies);
            flows.remove(incoming.flowId);
            return result;
        }
        flow.buffered.write(incoming.payload, 0, incoming.payload.length);
        ackOnly(flow, result.replies);
        byte[] input = flow.buffered.toByteArray();
        if (input.length < 2) return result;
        int dnsLength = DnsTcpTunnelPacket.u16(input, 0);
        if (dnsLength < 17 || dnsLength > MAX_QUERY_BYTES || input.length > dnsLength + 2) {
            reset(flow, incoming, result.replies);
            flows.remove(incoming.flowId);
            return result;
        }
        if (input.length != dnsLength + 2) return result;
        byte[] dns = Arrays.copyOfRange(input, 2, input.length);
        if ((dns[2] & 0xf8) != 0 || DnsPacket.extractDomain(dns, 0, dns.length) == null) {
            reset(flow, incoming, result.replies);
            flows.remove(incoming.flowId);
            return result;
        }
        flow.buffered.reset();
        flow.pending = true;
        result.request = new Request(incoming.flowId, flow.token, dns);
        return result;
    }

    /** Complete one asynchronous DNS lookup; stale connections cannot be resurrected. */
    public synchronized Result complete(Request request, byte[] response, long now) {
        Result result = new Result();
        if (request == null) return result;
        Flow flow = flows.get(request.flowId);
        if (flow == null || flow.token != request.token || !flow.pending) return result;
        if (response == null || response.length < 12 || response.length > DnsTcpWire.MAX_DNS_RESPONSE
                || !DnsTcpWire.matchesQuery(request.dns, response)) {
            flow.pending = false;
            reset(flow, flow.lastClient, result.replies);
            flows.remove(request.flowId);
            return result;
        }
        flow.pending = false;
        flow.lastSeen = now;
        byte[] framed = new byte[response.length + 2];
        DnsTcpTunnelPacket.put16(framed, 0, response.length);
        System.arraycopy(response, 0, framed, 2, response.length);
        flow.outbound = framed;
        flow.nextOffset = 0;
        sendNext(flow, result.replies, now);
        return result;
    }

    private void sendNext(Flow flow, List<byte[]> replies, long now) {
        if (flow.outbound == null || flow.lastSent != null) return;
        int bytes = Math.min(RESPONSE_SEGMENT_BYTES, flow.outbound.length - flow.nextOffset);
        if (bytes <= 0) return;
        byte[] part = Arrays.copyOfRange(flow.outbound, flow.nextOffset, flow.nextOffset + bytes);
        byte[] packet = DnsTcpTunnelPacket.reply(flow.lastClient, flow.nextServer,
                flow.nextClient, DnsTcpTunnelPacket.PSH | DnsTcpTunnelPacket.ACK, part);
        flow.nextOffset += bytes;
        flow.nextServer = DnsTcpTunnelPacket.plus(flow.nextServer, bytes);
        flow.lastSent = packet;
        flow.lastSentAt = now;
        flow.retries = 0;
        replies.add(packet);
    }

    private void ackOnly(Flow flow, List<byte[]> replies) {
        replies.add(DnsTcpTunnelPacket.reply(flow.lastClient, flow.nextServer,
                flow.nextClient, DnsTcpTunnelPacket.ACK, new byte[0]));
    }

    private void reset(Flow flow, DnsTcpTunnelPacket.Segment last, List<byte[]> replies) {
        replies.add(DnsTcpTunnelPacket.reply(last, flow.nextServer, flow.nextClient,
                DnsTcpTunnelPacket.RST | DnsTcpTunnelPacket.ACK, new byte[0]));
    }

    /** Retransmit one in-flight frame with an upper bound; expire idle sessions. */
    public synchronized List<byte[]> tick(long now) {
        List<byte[]> packets = new ArrayList<>();
        java.util.Iterator<Map.Entry<String, Flow>> i = flows.entrySet().iterator();
        while (i.hasNext()) {
            Flow f = i.next().getValue();
            if (now - f.lastSeen > IDLE_MILLIS) {
                i.remove();
                continue;
            }
            if (f.lastSent != null && now - f.lastSentAt >= RETRANSMIT_MILLIS) {
                if (f.retries >= MAX_RETRIES) {
                    i.remove();
                } else {
                    f.retries++;
                    f.lastSentAt = now;
                    packets.add(f.lastSent);
                }
            }
        }
        return packets;
    }

    private void expireOld(long now) {
        java.util.Iterator<Map.Entry<String, Flow>> i = flows.entrySet().iterator();
        while (i.hasNext()) {
            if (now - i.next().getValue().lastSeen > IDLE_MILLIS) i.remove();
        }
    }
}
