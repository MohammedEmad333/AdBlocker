package com.example.adblocker;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class DnsTcpTunnelTest {
    private static byte[] client(int family, int port, long seq, long ack, int flags, byte[] payload) {
        int ip = family == 4 ? 20 : 40;
        byte[] b = new byte[ip + 20 + payload.length];
        if (family == 4) {
            b[0] = 0x45; DnsTcpTunnelPacket.put16(b, 2, b.length);
            b[8] = 64; b[9] = 6; b[12] = 10; b[15] = 2; b[16] = 10; b[19] = 1;
        } else {
            b[0] = 0x60; DnsTcpTunnelPacket.put16(b, 4, b.length - 40);
            b[6] = 6; b[7] = 64;
            b[8] = (byte) 0xfd; b[23] = 2; b[24] = (byte) 0xfd; b[39] = 1;
        }
        DnsTcpTunnelPacket.put16(b, ip, port);
        DnsTcpTunnelPacket.put16(b, ip + 2, 53);
        DnsTcpTunnelPacket.put32(b, ip + 4, seq);
        DnsTcpTunnelPacket.put32(b, ip + 8, ack);
        b[ip + 12] = 0x50; b[ip + 13] = (byte) flags;
        DnsTcpTunnelPacket.put16(b, ip + 14, 16384);
        System.arraycopy(payload, 0, b, ip + 20, payload.length);
        DnsTcpTunnelPacket.put16(b, ip + 16,
                DnsTcpTunnelPacket.transportChecksum(b,
                family == 4 ? 12 : 8, family == 4 ? 16 : 24,
                ip, b.length - ip, family));
        return b;
    }

    private static final class Session {
        final DnsTcpTunnel engine = new DnsTcpTunnel();
        final int family, port;
        long seq = 1000, ack, now = 100;
        Session(int family, int port) {
            this.family = family; this.port = port;
            byte[] syn = client(family, port, seq, 0, DnsTcpTunnelPacket.SYN, new byte[0]);
            DnsTcpTunnel.Result result = engine.receive(syn, syn.length, now);
            assertEquals(1, result.replies.size());
            byte[] synAck = result.replies.get(0);
            int header = family == 4 ? 20 : 40;
            assertEquals(0x12, synAck[header + 13] & 255);
            ack = DnsTcpTunnelPacket.plus(DnsTcpTunnelPacket.u32(synAck, header + 4), 1);
            seq++;
            DnsTcpTunnel.Result established = send(DnsTcpTunnelPacket.ACK, new byte[0]);
            assertTrue(established.replies.isEmpty());
        }
        DnsTcpTunnel.Result send(int flags, byte[] data) {
            byte[] b = client(family, port, seq, ack, flags, data);
            DnsTcpTunnel.Result result = engine.receive(b, b.length, ++now);
            seq = DnsTcpTunnelPacket.plus(seq, data.length);
            return result;
        }
        DnsTcpTunnel.Request query(String domain, boolean split) {
            byte[] dns = DnsPacket.buildQuery(domain, 0x8877);
            byte[] framed = new byte[dns.length + 2];
            DnsTcpTunnelPacket.put16(framed, 0, dns.length);
            System.arraycopy(dns, 0, framed, 2, dns.length);
            if (split) {
                assertNull(send(DnsTcpTunnelPacket.ACK, Arrays.copyOfRange(framed, 0, 3)).request);
                return send(DnsTcpTunnelPacket.ACK,
                        Arrays.copyOfRange(framed, 3, framed.length)).request;
            }
            return send(DnsTcpTunnelPacket.ACK, framed).request;
        }
    }

    @Test public void splitDnsQueryAndCompleteIpv4Tcp() {
        Session s = new Session(4, 30100);
        DnsTcpTunnel.Request req = s.query("ads.example.com", true);
        assertNotNull(req);
        assertEquals("ads.example.com", DnsPacket.extractDomain(req.dns, 0, req.dns.length));
        byte[] dns = DnsPacket.buildNxDomain(req.dns, 0, req.dns.length);
        DnsTcpTunnel.Result r = s.engine.complete(req, dns, s.now);
        assertEquals(1, r.replies.size());
        byte[] packet = r.replies.get(0);
        assertEquals(53, DnsTcpTunnelPacket.u16(packet, 20));
        assertEquals(0, DnsTcpTunnelPacket.transportChecksum(packet, 12, 16, 20,
                packet.length - 20, 4));
        s.ack = DnsTcpTunnelPacket.plus(s.ack, packet.length - 40);
        assertTrue(s.send(DnsTcpTunnelPacket.ACK, new byte[0]).replies.isEmpty());
    }

    @Test public void ipv6TcpDnsResponseHasValidChecksum() {
        Session s = new Session(6, 30101);
        DnsTcpTunnel.Request req = s.query("example.org", false);
        assertNotNull(req);
        DnsTcpTunnel.Result r = s.engine.complete(req,
                DnsPacket.buildNxDomain(req.dns, 0, req.dns.length), s.now);
        assertEquals(1, r.replies.size());
        byte[] packet = r.replies.get(0);
        assertEquals(0, DnsTcpTunnelPacket.transportChecksum(packet, 8, 24, 40,
                packet.length - 40, 6));
        assertEquals(1, packet[23] & 255);
        assertEquals(2, packet[39] & 255);
    }

    @Test public void longTcpResponseWaitsForEachAck() {
        Session s = new Session(4, 30102);
        DnsTcpTunnel.Request req = s.query("example.com", false);
        byte[] large = Arrays.copyOf(DnsPacket.buildNxDomain(req.dns, 0, req.dns.length), 2300);
        DnsTcpTunnel.Result r = s.engine.complete(req, large, s.now);
        int remaining = large.length + 2; // 2-byte DNS/TCP length prefix
        while (remaining > 0) {
            assertEquals(1, r.replies.size());
            int piece = r.replies.get(0).length - 40;
            assertEquals(Math.min(512, remaining), piece);
            remaining -= piece;
            s.ack = DnsTcpTunnelPacket.plus(s.ack, piece);
            r = s.send(DnsTcpTunnelPacket.ACK, new byte[0]);
        }
        assertTrue(r.replies.isEmpty());
    }

    @Test public void flowCapacityTimeoutAndRetransmission() {
        DnsTcpTunnel engine = new DnsTcpTunnel();
        for (int i = 0; i < DnsTcpTunnel.MAX_FLOWS; i++) {
            byte[] syn = client(4, 31000 + i, 1, 0, DnsTcpTunnelPacket.SYN, new byte[0]);
            engine.receive(syn, syn.length, 100);
        }
        assertEquals(64, engine.activeFlows());
        byte[] syn = client(4, 39999, 1, 0, DnsTcpTunnelPacket.SYN, new byte[0]);
        DnsTcpTunnel.Result denied = engine.receive(syn, syn.length, 100);
        assertEquals(0x14, denied.replies.get(0)[33] & 255);
        assertEquals(64, engine.activeFlows());
        assertEquals(64, engine.tick(100 + DnsTcpTunnel.RETRANSMIT_MILLIS + 1).size());
        engine.tick(100 + DnsTcpTunnel.IDLE_MILLIS + 1);
        assertEquals(0, engine.activeFlows());
    }

    @Test public void invalidPacketAndStaleCompletionAreIgnored() {
        byte[] bad = client(4, 30022, 5, 0, DnsTcpTunnelPacket.SYN, new byte[0]);
        bad[bad.length - 1] ^= 31;
        assertNull(DnsTcpTunnelPacket.parse(bad, bad.length));
        bad = client(4, 30022, 5, 0, DnsTcpTunnelPacket.SYN, new byte[0]);
        bad[19] = 3;
        assertNull(DnsTcpTunnelPacket.parse(bad, bad.length));
        Session s = new Session(4, 30023);
        DnsTcpTunnel.Request req = s.query("example.net", false);
        assertNotNull(req);
        s.engine.clear();
        assertTrue(s.engine.complete(req,
                DnsPacket.buildNxDomain(req.dns, 0, req.dns.length), 200).replies.isEmpty());
    }
}
