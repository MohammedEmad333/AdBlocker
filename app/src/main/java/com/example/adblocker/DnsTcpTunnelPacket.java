package com.example.adblocker;

import java.util.Arrays;

/**
 * Strict IPv4/IPv6 TCP/53 packet codec for the DNS-only VPN.
 * IP fragments, IPv6 extension headers and invalid TCP checksums are rejected.
 * No packets outside the two synthetic DNS destination addresses are accepted.
 */
public final class DnsTcpTunnelPacket {
    static final int SYN = 0x02, ACK = 0x10, FIN = 0x01, RST = 0x04, PSH = 0x08;
    static final int MTU = 1500;
    private DnsTcpTunnelPacket() {}

    static final class Segment {
        final byte[] original;
        final int ipVersion, tcpOffset, tcpHeaderLength, sourcePort, flags, window;
        final long seq, ack;
        final byte[] payload;
        final String flowId;

        Segment(byte[] original, int family, int offset, int headerLength,
                int port, int flags, int window, long seq, long ack, byte[] data, String id) {
            this.original = original;
            this.ipVersion = family;
            this.tcpOffset = offset;
            this.tcpHeaderLength = headerLength;
            this.sourcePort = port;
            this.flags = flags;
            this.window = window;
            this.seq = seq;
            this.ack = ack;
            this.payload = data;
            this.flowId = id;
        }
    }

    static int u16(byte[] b, int at) {
        return (b[at] & 255) << 8 | (b[at + 1] & 255);
    }

    static long u32(byte[] b, int at) {
        return ((long) (b[at] & 255) << 24) |
                ((long) (b[at + 1] & 255) << 16) |
                ((long) (b[at + 2] & 255) << 8) | (b[at + 3] & 255);
    }

    static void put16(byte[] b, int at, int value) {
        b[at] = (byte) (value >>> 8);
        b[at + 1] = (byte) value;
    }

    static void put32(byte[] b, int at, long value) {
        b[at] = (byte) (value >>> 24);
        b[at + 1] = (byte) (value >>> 16);
        b[at + 2] = (byte) (value >>> 8);
        b[at + 3] = (byte) value;
    }

    static long plus(long a, long n) { return (a + n) & 0xffffffffL; }

    /** Return null when the packet is unrelated, malformed or unsafe to process. */
    static Segment parse(byte[] packet, int read) {
        if (packet == null || read < 40 || read > packet.length || read > MTU) return null;
        int family = (packet[0] >>> 4) & 15;
        int offset, length, src, dst;
        if (family == 4) {
            offset = (packet[0] & 15) * 4;
            if (offset < 20 || offset + 20 > read || (packet[9] & 255) != 6) return null;
            length = u16(packet, 2);
            if (length < offset + 20 || length > read) return null;
            if ((packet[6] & 0x3f) != 0 || packet[7] != 0) return null;
            if ((packet[16] & 255) != 10 || packet[17] != 0
                    || packet[18] != 0 || packet[19] != 1) return null;
            src = 12; dst = 16;
        } else if (family == 6) {
            offset = 40;
            length = 40 + u16(packet, 4);
            if (length < 60 || length > read || (packet[6] & 255) != 6) return null;
            if (packet[24] != (byte) 0xfd || packet[25] != 0 || packet[39] != 1) return null;
            for (int i = 26; i < 39; i++) if (packet[i] != 0) return null;
            src = 8; dst = 24;
        } else return null;

        if (u16(packet, offset + 2) != 53) return null;
        int header = ((packet[offset + 12] >>> 4) & 15) * 4;
        if (header < 20 || header > 60 || offset + header > length) return null;
        // Mandatory nonzero TCP checksum; verify pseudoheader + segment.
        if (u16(packet, offset + 16) == 0 ||
                transportChecksum(packet, src, dst, offset, length - offset, family) != 0)
            return null;

        int port = u16(packet, offset);
        byte[] data = Arrays.copyOfRange(packet, offset + header, length);
        StringBuilder key = new StringBuilder(family == 4 ? "4:" : "6:");
        for (int i = 0; i < (family == 4 ? 4 : 16); i++) {
            int value = packet[src + i] & 255;
            if (value < 16) key.append('0');
            key.append(Integer.toHexString(value));
        }
        key.append(':').append(port);
        return new Segment(Arrays.copyOf(packet, length), family, offset, header,
                port, packet[offset + 13] & 255, u16(packet, offset + 14),
                u32(packet, offset + 4), u32(packet, offset + 8), data, key.toString());
    }

    /** Build a checksum-correct TCP packet from the virtual DNS server. */
    static byte[] reply(Segment client, long seq, long ack, int flags, byte[] payload) {
        if (client == null || payload == null || payload.length > MTU - 60)
            throw new IllegalArgumentException("TCP reply exceeds MTU");
        int ipHeader = client.ipVersion == 4 ? 20 : 40;
        int tcp = ipHeader;
        int total = ipHeader + 20 + payload.length;
        if (total > MTU) throw new IllegalArgumentException("TCP response too large");
        byte[] answer = new byte[total];
        byte[] original = client.original;
        if (client.ipVersion == 4) {
            answer[0] = 0x45;
            answer[1] = original[1];
            put16(answer, 2, total);
            answer[8] = 64;
            answer[9] = 6;
            System.arraycopy(original, 16, answer, 12, 4);
            System.arraycopy(original, 12, answer, 16, 4);
            put16(answer, 10, checksum(answer, 0, 20));
        } else {
            answer[0] = 0x60;
            put16(answer, 4, total - 40);
            answer[6] = 6;
            answer[7] = 64;
            System.arraycopy(original, 24, answer, 8, 16);
            System.arraycopy(original, 8, answer, 24, 16);
        }
        put16(answer, tcp, 53);
        put16(answer, tcp + 2, client.sourcePort);
        put32(answer, tcp + 4, seq);
        put32(answer, tcp + 8, ack);
        answer[tcp + 12] = 0x50;
        answer[tcp + 13] = (byte) flags;
        put16(answer, tcp + 14, 16384);
        System.arraycopy(payload, 0, answer, tcp + 20, payload.length);
        put16(answer, tcp + 16, transportChecksum(answer,
                client.ipVersion == 4 ? 12 : 8, client.ipVersion == 4 ? 16 : 24,
                tcp, total - tcp, client.ipVersion));
        return answer;
    }

    private static long words(byte[] b, int at, int length) {
        long sum = 0;
        for (int i = 0; i < length; i += 2)
            sum += ((b[at + i] & 255) << 8) |
                    (i + 1 < length ? (b[at + i + 1] & 255) : 0);
        return sum;
    }

    private static int fold(long sum) {
        while ((sum >>> 16) != 0) sum = (sum & 65535) + (sum >>> 16);
        return (int) (~sum & 65535);
    }

    private static int checksum(byte[] b, int at, int length) {
        return fold(words(b, at, length));
    }

    static int transportChecksum(byte[] b, int src, int dst, int tcp, int len, int family) {
        long sum = words(b, src, family == 4 ? 4 : 16);
        sum += words(b, dst, family == 4 ? 4 : 16);
        if (family == 4) {
            sum += 6 + len;
        } else {
            sum += ((len >>> 16) & 65535) + (len & 65535) + 6;
        }
        sum += words(b, tcp, len);
        return fold(sum);
    }
}
