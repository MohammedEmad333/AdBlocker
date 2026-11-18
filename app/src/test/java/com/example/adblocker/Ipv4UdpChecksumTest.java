package com.example.adblocker;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/** Pure-JVM regression checks for RFC 768 IPv4 UDP DNS checksum handling. */
public class Ipv4UdpChecksumTest {
    private static byte[] query(String hostname, boolean options) {
        byte[] dns = DnsPacket.buildQuery(hostname, 0x41c7);
        int header = options ? 24 : 20;
        int udpLength = 8 + dns.length;
        byte[] packet = new byte[header + udpLength];
        packet[0] = (byte) (0x40 | (header / 4));
        packet[2] = (byte) (packet.length >>> 8);
        packet[3] = (byte) packet.length;
        packet[8] = 64; // TTL
        packet[9] = 17; // UDP
        packet[12] = 10; packet[15] = 2; // source 10.0.0.2
        packet[16] = 10; packet[19] = 1; // DNS endpoint 10.0.0.1
        packet[header] = 0x12; packet[header + 1] = 0x34;
        packet[header + 2] = 0; packet[header + 3] = 53;
        packet[header + 4] = (byte) (udpLength >>> 8);
        packet[header + 5] = (byte) udpLength;
        System.arraycopy(dns, 0, packet, header + 8, dns.length);
        return packet;
    }

    private static void sign(byte[] packet) {
        int header = (packet[0] & 15) * 4;
        int udpLength = ((packet[header + 4] & 255) << 8)
                | (packet[header + 5] & 255);
        packet[header + 6] = 0;
        packet[header + 7] = 0;
        int checksum = DnsTunnelPacket.ipv4UdpChecksum(packet, header, udpLength);
        packet[header + 6] = (byte) (checksum >>> 8);
        packet[header + 7] = (byte) checksum;
        assertEquals(0xffff, DnsTunnelPacket.ipv4UdpChecksum(packet, header, udpLength));
    }

    @Test public void zeroChecksumIsAllowedInIpv4() {
        byte[] packet = query("example.com", false);
        assertNotNull(DnsTunnelPacket.parse(packet, packet.length));
        // With checksum disabled, ordinary ID changes remain structurally valid.
        packet[28] ^= 1;
        assertNotNull(DnsTunnelPacket.parse(packet, packet.length));
    }

    @Test public void validNonzeroChecksumPassesAndDataTamperingFails() {
        byte[] packet = query("example.com", false);
        sign(packet);
        assertNotNull(DnsTunnelPacket.parse(packet, packet.length));
        packet[28] ^= 1; // changes DNS transaction ID without changing its framing
        assertNull(DnsTunnelPacket.parse(packet, packet.length));
        packet[28] ^= 1;
        assertNotNull(DnsTunnelPacket.parse(packet, packet.length));
    }

    @Test public void rejectsTamperingInSourceAddressOrChecksumField() {
        byte[] packet = query("example.net", false);
        sign(packet);
        packet[15] ^= 1; // pseudo-header is part of the checksum
        assertNull(DnsTunnelPacket.parse(packet, packet.length));

        byte[] packet2 = query("example.net", false);
        sign(packet2);
        packet2[26] ^= 1; // directly corrupt checksum
        assertNull(DnsTunnelPacket.parse(packet2, packet2.length));
    }

    @Test public void handlesOddLengthUdpAndIpv4Options() {
        byte[] packet = query("x.example", true);
        int header = 24;
        int udpLength = packet.length - header;
        assertEquals(1, udpLength & 1); // RFC zero-padding of the final byte
        sign(packet);
        DnsTunnelPacket.Query parsed = DnsTunnelPacket.parse(packet, packet.length);
        assertNotNull(parsed);
        assertEquals(header, parsed.ipHeaderLength);
        assertEquals("x.example", DnsPacket.extractDomain(
                packet, parsed.dnsOffset, parsed.dnsLength));
        packet[header + 8] ^= 1;
        assertNull(DnsTunnelPacket.parse(packet, packet.length));
    }

    @Test public void ignoresTrailingIpPaddingAndUnusedTunBuffer() {
        byte[] packet = query("example.com", false);
        sign(packet);
        byte[] padded = Arrays.copyOf(packet, packet.length + 10);
        // Actual IP payload may contain bytes after UDP's declared length.
        padded[2] = (byte) (padded.length >>> 8);
        padded[3] = (byte) padded.length;
        Arrays.fill(padded, packet.length, padded.length, (byte) 0x5a);
        assertNotNull(DnsTunnelPacket.parse(padded, padded.length));
        assertNotNull(DnsTunnelPacket.parse(padded, packet.length));
    }

    @Test public void helperRejectsMalformedBounds() {
        byte[] packet = query("example.com", false);
        try {
            DnsTunnelPacket.ipv4UdpChecksum(packet, 20, packet.length);
            fail("Declared UDP bytes exceed packet bounds");
        } catch (IllegalArgumentException expected) { /* safe */ }
        try {
            DnsTunnelPacket.ipv4UdpChecksum(packet, 24, 8);
            fail("IPv4 header offset must match IHL");
        } catch (IllegalArgumentException expected) { /* safe */ }
        try {
            DnsTunnelPacket.ipv4UdpChecksum(null, 20, 8);
            fail("Null packet must be rejected");
        } catch (IllegalArgumentException expected) { /* safe */ }
    }
}
