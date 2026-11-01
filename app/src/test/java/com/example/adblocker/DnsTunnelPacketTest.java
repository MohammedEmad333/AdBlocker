package com.example.adblocker;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import static org.junit.Assert.*;

public class DnsTunnelPacketTest {
    private static byte[] queryV6(String name) {
        byte[] dns = DnsPacket.buildQuery(name, 0x1234);
        byte[] ip = new byte[48 + dns.length];
        ip[0] = 0x60;
        int len = 8 + dns.length;
        ip[4] = (byte) (len >>> 8); ip[5] = (byte) len;
        ip[6] = 17; ip[7] = 64;
        ip[8] = (byte) 0xfd; ip[23] = 2; // fd00::2
        ip[24] = (byte) 0xfd; ip[39] = 1; // fd00::1
        ip[40] = 0x12; ip[41] = 0x34;
        ip[42] = 0; ip[43] = 53;
        ip[44] = (byte) (len >>> 8); ip[45] = (byte) len;
        System.arraycopy(dns, 0, ip, 48, dns.length);
        int checksum = DnsTunnelPacket.ipv6UdpChecksum(ip);
        ip[46] = (byte) (checksum >>> 8); ip[47] = (byte) checksum;
        return ip;
    }

    @Test public void parsesAndRepliesToIpv6UdpDns() {
        byte[] ip = queryV6("ads.example.com");
        DnsTunnelPacket.Query parsed = DnsTunnelPacket.parse(ip, ip.length);
        assertNotNull(parsed);
        assertEquals(6, parsed.ipVersion);
        assertEquals("ads.example.com", DnsPacket.extractDomain(ip, parsed.dnsOffset, parsed.dnsLength));
        byte[] result = DnsTunnelPacket.wrapReply(ip, parsed,
                DnsPacket.buildNxDomain(ip, parsed.dnsOffset, parsed.dnsLength));
        assertEquals(6, (result[0] & 0xf0) >>> 4);
        assertEquals(53, result[41] & 0xff);
        assertEquals(0x12, result[42] & 0xff);
        assertEquals(0x34, result[43] & 0xff);
        assertEquals((byte) 2, result[39]);
        assertEquals((byte) 1, result[23]);
        assertEquals(0xffff, DnsTunnelPacket.ipv6UdpChecksum(result));
    }

    @Test public void rejectsMalformedAndUnrelatedIpv6Packets() {
        byte[] q = queryV6("example.net");
        assertNull(DnsTunnelPacket.parse(q, 49));
        byte[] modified = q.clone();
        modified[6] = 44; // Fragment header, unsupported
        assertNull(DnsTunnelPacket.parse(modified, modified.length));
        modified = q.clone();
        modified[39] = 3; // unrelated IPv6 address
        assertNull(DnsTunnelPacket.parse(modified, modified.length));
        modified = q.clone();
        modified[47] = 0; modified[46] = 0; // IPv6 requires UDP checksum
        assertNull(DnsTunnelPacket.parse(modified, modified.length));
        modified = q.clone();
        modified[44] = 0; modified[45] = 7;
        assertNull(DnsTunnelPacket.parse(modified, modified.length));
        modified = q.clone();
        modified[43] = 54;
        assertNull(DnsTunnelPacket.parse(modified, modified.length));
    }

    @Test public void dnsTcpFramingRoundTripsAndRejectsOversize() throws Exception {
        byte[] q = DnsPacket.buildQuery("example.net", 0x2233);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DnsTcpWire.writeQuery(out, q);
        byte[] frame = out.toByteArray();
        assertEquals(q.length, ((frame[0] & 0xff) << 8) | (frame[1] & 0xff));
        assertArrayEquals(q, DnsTcpWire.readResponse(new ByteArrayInputStream(frame)));
        assertFalse(DnsTcpWire.matchesQuery(q, q)); // a query is not a response
        byte[] answer = DnsPacket.buildNxDomain(q, 0, q.length);
        assertTrue(DnsTcpWire.matchesQuery(q, answer));
        byte[] oversize = {(byte) 0xff, (byte) 0xff};
        try { DnsTcpWire.readResponse(new ByteArrayInputStream(oversize)); fail(); }
        catch (IOException expected) {}
        try { DnsTcpWire.readResponse(new ByteArrayInputStream(new byte[] {0,12,1,2})); fail(); }
        catch (IOException expected) {}
    }

    @Test public void rejectsIpv6BadChecksumAndHandlesRandomFuzz() {
        byte[] q = queryV6("example.net");
        q[48] ^= 0x01;
        assertNull(DnsTunnelPacket.parse(q, q.length));
        java.util.Random random = new java.util.Random(0xBADF00D);
        for (int i = 0; i < 2000; i++) {
            byte[] data = new byte[random.nextInt(400)];
            random.nextBytes(data);
            DnsTunnelPacket.Query parsed = DnsTunnelPacket.parse(data, data.length);
            if (parsed != null) {
                assertEquals(53, ((data[parsed.ipHeaderLength + 2] & 0xff) << 8)
                        | (data[parsed.ipHeaderLength + 3] & 0xff));
            }
        }
    }

    @Test public void ipv6ResponseRejectsPacketLargerThanTunMtu() {
        byte[] q = queryV6("example.net");
        DnsTunnelPacket.Query parsed = DnsTunnelPacket.parse(q, q.length);
        assertNotNull(parsed);
        try {
            DnsTunnelPacket.wrapReply(q, parsed, new byte[1453]);
            fail("IPv6 reply exceeds MTU");
        } catch (IllegalArgumentException expected) {}
    }

    @Test public void rejectsIpv6OversizedDeclaredPayload() {
        byte[] q = queryV6("x.example.net");
        q[4] = (byte) 0xff; q[5] = (byte) 0xff;
        assertNull(DnsTunnelPacket.parse(q, q.length));
    }
}
