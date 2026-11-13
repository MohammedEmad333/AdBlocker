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

    @Test public void wrongSequenceAckCannotAdvanceSegmentedResponse() {
        Session s = new Session(4, 30103);
        DnsTcpTunnel.Request req = s.query("example.com", false);
        byte[] large = Arrays.copyOf(DnsPacket.buildNxDomain(req.dns, 0, req.dns.length), 1300);
        DnsTcpTunnel.Result first = s.engine.complete(req, large, s.now);
        assertEquals(1, first.replies.size());
        assertEquals(512, first.replies.get(0).length - 40);
        long nextAck = DnsTcpTunnelPacket.plus(s.ack, 512);

        byte[] forged = client(4, s.port, s.seq + 20, nextAck,
                DnsTcpTunnelPacket.ACK, new byte[0]);
        DnsTcpTunnel.Result denied = s.engine.receive(forged, forged.length, ++s.now);
        assertEquals(1, denied.replies.size());
        assertEquals(DnsTcpTunnelPacket.ACK, denied.replies.get(0)[33] & 255);
        assertEquals(40, denied.replies.get(0).length);

        // A legitimate in-order ACK must still advance to the next chunk.
        s.ack = nextAck;
        DnsTcpTunnel.Result second = s.send(DnsTcpTunnelPacket.ACK, new byte[0]);
        assertEquals(1, second.replies.size());
        assertEquals(512, second.replies.get(0).length - 40);
    }

    @Test public void wrongSequenceDataCannotAcknowledgePendingResponse() {
        Session s = new Session(6, 30104);
        DnsTcpTunnel.Request req = s.query("example.org", false);
        byte[] large = Arrays.copyOf(DnsPacket.buildNxDomain(req.dns, 0, req.dns.length), 800);
        DnsTcpTunnel.Result first = s.engine.complete(req, large, s.now);
        assertEquals(1, first.replies.size());
        long nextAck = DnsTcpTunnelPacket.plus(s.ack, 512);

        byte[] unexpected = client(6, s.port, s.seq + 1, nextAck,
                DnsTcpTunnelPacket.ACK | DnsTcpTunnelPacket.PSH, new byte[]{1});
        DnsTcpTunnel.Result denied = s.engine.receive(unexpected, unexpected.length, ++s.now);
        assertEquals(1, denied.replies.size());
        assertEquals(40 + 20, denied.replies.get(0).length);
        assertEquals(DnsTcpTunnelPacket.ACK, denied.replies.get(0)[53] & 255);

        s.ack = nextAck;
        DnsTcpTunnel.Result next = s.send(DnsTcpTunnelPacket.ACK, new byte[0]);
        assertEquals(1, next.replies.size());
        assertEquals(290, next.replies.get(0).length - 60);
    }

    @Test public void missingAckRetransmitsExactDnsChunkThenExpiresFlow() {
        Session s = new Session(4, 30105);
        DnsTcpTunnel.Request req = s.query("retry.example", false);
        assertNotNull(req);
        byte[] dns = DnsPacket.buildNxDomain(req.dns, 0, req.dns.length);
        byte[] first = s.engine.complete(req, dns, s.now).replies.get(0);
        for (int retry = 1; retry <= DnsTcpTunnel.MAX_RETRIES; retry++) {
            long at = s.now + retry * DnsTcpTunnel.RETRANSMIT_MILLIS + 1;
            java.util.List<byte[]> packets = s.engine.tick(at);
            assertEquals(1, packets.size());
            assertArrayEquals(first, packets.get(0));
        }
        assertTrue(s.engine.tick(s.now + (DnsTcpTunnel.MAX_RETRIES + 1)
                * DnsTcpTunnel.RETRANSMIT_MILLIS + 2).isEmpty());
        assertEquals(0, s.engine.activeFlows());
    }

    @Test public void ackedDnsChunkStopsRetransmissions() {
        Session s = new Session(6, 30106);
        DnsTcpTunnel.Request req = s.query("ack.example", false);
        assertNotNull(req);
        byte[] reply = s.engine.complete(req,
                DnsPacket.buildNxDomain(req.dns, 0, req.dns.length), s.now)
                .replies.get(0);
        s.ack = DnsTcpTunnelPacket.plus(s.ack, reply.length - 60);
        assertTrue(s.send(DnsTcpTunnelPacket.ACK, new byte[0]).replies.isEmpty());
        assertTrue(s.engine.tick(s.now + DnsTcpTunnel.RETRANSMIT_MILLIS + 10)
                .isEmpty());
        assertEquals(1, s.engine.activeFlows());
    }

    @Test public void malformedCompletionResetsOnlyTheOwningFlow() {
        Session first = new Session(4, 30107);
        DnsTcpTunnel.Request req = first.query("valid.example", false);
        assertNotNull(req);
        byte[] malformed = DnsPacket.buildQuery("other.example", 0x8877);
        DnsTcpTunnel.Result result = first.engine.complete(req, malformed, first.now);
        assertEquals(1, result.replies.size());
        assertEquals(DnsTcpTunnelPacket.RST | DnsTcpTunnelPacket.ACK,
                result.replies.get(0)[33] & 255);
        assertEquals(0, first.engine.activeFlows());
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
    @Test public void staleSynDoesNotReplaceEstablishedConnection() {
        Session s = new Session(4, 30200);
        byte[] oldSyn = client(4, 30200, s.seq - 1, 0, DnsTcpTunnelPacket.SYN, new byte[0]);
        DnsTcpTunnel.Result duplicate = s.engine.receive(oldSyn, oldSyn.length, ++s.now);
        assertTrue(duplicate.replies.isEmpty());
        assertEquals(1, s.engine.activeFlows());
        assertNotNull(s.query("still-connected.example", false));
    }

    @Test public void rstRequiresExpectedSequenceNumber() {
        Session s = new Session(4, 30201);
        byte[] forged = client(4, 30201, s.seq + 900, 0,
                DnsTcpTunnelPacket.RST, new byte[0]);
        s.engine.receive(forged, forged.length, ++s.now);
        assertEquals(1, s.engine.activeFlows());
        assertNotNull(s.query("valid.example", false));
        // A valid reset with the in-order sequence closes the flow.
        byte[] valid = client(4, 30201, s.seq, 0, DnsTcpTunnelPacket.RST, new byte[0]);
        s.engine.receive(valid, valid.length, ++s.now);
        assertEquals(0, s.engine.activeFlows());
    }

    @Test public void futureAckCannotConsumeDnsRequest() {
        Session s = new Session(6, 30202);
        byte[] q = DnsPacket.buildQuery("example.net", 0x1111);
        byte[] frame = new byte[q.length + 2];
        DnsTcpTunnelPacket.put16(frame, 0, q.length);
        System.arraycopy(q, 0, frame, 2, q.length);
        byte[] invalid = client(6, 30202, s.seq, s.ack + 500,
                DnsTcpTunnelPacket.ACK | DnsTcpTunnelPacket.PSH, frame);
        DnsTcpTunnel.Result wrong = s.engine.receive(invalid, invalid.length, ++s.now);
        assertNull(wrong.request);
        assertTrue(wrong.replies.isEmpty());
        assertNotNull(s.query("example.net", false));
    }

    @Test public void malformedSynFinAndZeroSourcePortAreRejected() {
        byte[] b = client(4, 30203, 55, 0,
                DnsTcpTunnelPacket.SYN | DnsTcpTunnelPacket.FIN, new byte[0]);
        assertNull(DnsTcpTunnelPacket.parse(b, b.length));
        b = client(6, 30204, 55, 0,
                DnsTcpTunnelPacket.SYN | DnsTcpTunnelPacket.RST, new byte[0]);
        assertNull(DnsTcpTunnelPacket.parse(b, b.length));
        b = client(4, 0, 55, 0, DnsTcpTunnelPacket.SYN, new byte[0]);
        assertNull(DnsTcpTunnelPacket.parse(b, b.length));
    }

    @Test public void dnsRepliesMustMatchQuestionNotJustTransactionId() {
        byte[] q = DnsPacket.buildQuery("example.net", 0xAA10);
        byte[] a = DnsPacket.buildNxDomain(q, 0, q.length);
        assertTrue(DnsTcpWire.matchesQuery(q, a));
        byte[] changedType = a.clone();
        changedType[changedType.length - 3] = 28;
        assertFalse(DnsTcpWire.matchesQuery(q, changedType));
        byte[] changedDomain = a.clone();
        changedDomain[13] = (byte) 'X';
        assertFalse(DnsTcpWire.matchesQuery(q, changedDomain));
        byte[] changedTxId = a.clone();
        changedTxId[1] ^= 0x04;
        assertFalse(DnsTcpWire.matchesQuery(q, changedTxId));
        byte[] mixedCase = a.clone();
        mixedCase[13] = (byte) 'E';
        assertTrue(DnsTcpWire.matchesQuery(q, mixedCase));
    }

    @Test public void randomTcpPacketInputNeverThrows() {
        java.util.Random random = new java.util.Random(0xFF0011);
        DnsTcpTunnel state = new DnsTcpTunnel();
        for (int i = 0; i < 2500; i++) {
            byte[] b = new byte[random.nextInt(160)];
            random.nextBytes(b);
            assertNull(DnsTcpTunnelPacket.parse(b, b.length));
            DnsTcpTunnel.Result result = state.receive(b, b.length, i);
            assertNull(result.request);
            assertTrue(result.replies.isEmpty());
        }
    }

}
