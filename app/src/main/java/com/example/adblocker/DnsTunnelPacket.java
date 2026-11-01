package com.example.adblocker;

import java.util.Arrays;

/**
 * Validates exactly the DNS/UDP packets routed to the virtual resolver.
 * Supports IPv4 and IPv6 without attempting to parse extension headers or
 * fragments. Pure Java so malicious packet boundaries can be tested in CI.
 */
public final class DnsTunnelPacket {
    private DnsTunnelPacket() {}

    private static final int UDP = 17;
    private static final int MAX_IP_BYTES = 65535;

    public static final class Query {
        public final int ipVersion;
        public final int ipHeaderLength;
        public final int packetLength;
        public final int dnsOffset;
        public final int dnsLength;

        private Query(int family, int header, int length, int start, int size) {
            ipVersion = family;
            ipHeaderLength = header;
            packetLength = length;
            dnsOffset = start;
            dnsLength = size;
        }
    }

    private static int u16(byte[] b, int offset) {
        return ((b[offset] & 0xff) << 8) | (b[offset + 1] & 0xff);
    }

    /** Null on malformed or unsupported traffic; fail closed on bounds. */
    public static Query parse(byte[] packet, int bytesRead) {
        if (packet == null || bytesRead < 0 || bytesRead > packet.length
                || bytesRead < 28) return null;
        int version = (packet[0] & 0xf0) >>> 4;
        int header, length;
        if (version == 4) {
            header = (packet[0] & 0x0f) * 4;
            if (header < 20 || header > bytesRead - 8) return null;
            length = u16(packet, 2);
            if (length < header + 25 || length > bytesRead) return null;
            if ((packet[6] & 0x3f) != 0 || packet[7] != 0) return null;
            if ((packet[9] & 0xff) != UDP) return null;
            if (packet[16] != 10 || packet[17] != 0
                    || packet[18] != 0 || packet[19] != 1) return null;
        } else if (version == 6) {
            if (bytesRead < 40 + 8 + 17) return null;
            header = 40; // no extension headers supported
            if ((packet[6] & 0xff) != UDP) return null;
            length = header + u16(packet, 4);
            if (length < 65 || length > bytesRead || length > MAX_IP_BYTES) return null;
            // Resolver address fd00::1, never take over unrelated IPv6 traffic.
            if (packet[24] != (byte) 0xfd || packet[25] != 0) return null;
            for (int i = 26; i < 39; i++) if (packet[i] != 0) return null;
            if (packet[39] != 1) return null;
        } else {
            return null;
        }
        int udp = header;
        if (u16(packet, udp + 2) != 53) return null;
        int udpLen = u16(packet, udp + 4);
        if (udpLen < 25 || udpLen > length - header) return null;
        if (version == 6) {
            if (udpLen != length - header || u16(packet, udp + 6) == 0) return null;
        }
        int dnsOffset = udp + 8, dnsLength = udpLen - 8;
        if ((packet[dnsOffset + 2] & 0xf8) != 0) return null;
        if (DnsPacket.questionEnd(packet, dnsOffset, dnsLength) < 0) return null;
        return new Query(version, header, length, dnsOffset, dnsLength);
    }

    /** Reply carries the original 5-tuple in reverse, with a valid IPv6 UDP checksum. */
    public static byte[] wrapReply(byte[] original, Query query, byte[] dnsResponse) {
        if (query == null || original == null || dnsResponse == null
                || query.packetLength > original.length)
            throw new IllegalArgumentException("Invalid DNS reply");
        if (query.ipVersion == 4) {
            return DnsPacket.buildResponsePacket(original, query.ipHeaderLength, dnsResponse);
        }
        if (query.ipVersion != 6) throw new IllegalArgumentException("Unsupported IP family");
        int udpLength = dnsResponse.length + 8;
        if (udpLength > 65535 - 40) throw new IllegalArgumentException("Reply exceeds IPv6 MTU");
        byte[] answer = new byte[40 + udpLength];
        System.arraycopy(original, 0, answer, 0, 40);
        answer[4] = (byte) (udpLength >>> 8);
        answer[5] = (byte) udpLength;
        answer[6] = UDP;
        answer[7] = 64;
        // Swap IPv6 source and destination addresses.
        for (int i = 0; i < 16; i++) {
            answer[8 + i] = original[24 + i];
            answer[24 + i] = original[8 + i];
        }
        int srcPort = u16(original, 40);
        answer[40] = 0;
        answer[41] = 53;
        answer[42] = (byte) (srcPort >>> 8);
        answer[43] = (byte) srcPort;
        answer[44] = (byte) (udpLength >>> 8);
        answer[45] = (byte) udpLength;
        System.arraycopy(dnsResponse, 0, answer, 48, dnsResponse.length);
        int checksum = ipv6UdpChecksum(answer);
        answer[46] = (byte) (checksum >>> 8);
        answer[47] = (byte) checksum;
        return answer;
    }

    /**
     * Checksummed UDP over IPv6 pseudo-header (RFC 8200). For verification,
     * a complete packet with its checksum included must yield 0xffff.
     */
    static int ipv6UdpChecksum(byte[] packet) {
        if (packet == null || packet.length < 48
                || ((packet[0] >>> 4) & 0xf) != 6)
            throw new IllegalArgumentException("Invalid IPv6 packet");
        int udpLen = packet.length - 40;
        long sum = 0;
        sum += words(packet, 8, 32); // both IPv6 addresses
        sum += udpLen & 0xffff;
        sum += UDP;
        sum += words(packet, 40, udpLen);
        while ((sum >>> 16) != 0) sum = (sum & 0xffff) + (sum >>> 16);
        int checksum = (int) (~sum & 0xffff);
        return checksum == 0 ? 0xffff : checksum;
    }

    private static long words(byte[] b, int off, int len) {
        long sum = 0;
        for (int i = 0; i < len; i += 2) {
            int high = b[off + i] & 0xff;
            int low = i + 1 < len ? b[off + i + 1] & 0xff : 0;
            sum += (high << 8) | low;
        }
        return sum;
    }
}
