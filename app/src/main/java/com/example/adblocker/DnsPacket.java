package com.example.adblocker; // change to match your app's package name

/**
 * Static helpers for the two things we do with DNS at the byte level:
 *   1. read the domain name out of a query
 *   2. build the reply packet we write back into the tunnel
 *
 * Everything here works on raw IPv4 + UDP + DNS bytes. No allocations we
 * can avoid, because this runs once per DNS query.
 */
public final class DnsPacket {

    private DnsPacket() {}

    /**
     * Build a minimal DNS A-record query. Used to probe which upstream
     * resolver actually answers on this network before we commit to one.
     */
    public static byte[] buildQuery(String domain, int txid) {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        b.write(txid >> 8); b.write(txid & 0xFF);
        b.write(0x01); b.write(0x00);   // flags: RD=1
        b.write(0); b.write(1);         // QDCOUNT = 1
        b.write(0); b.write(0);         // ANCOUNT
        b.write(0); b.write(0);         // NSCOUNT
        b.write(0); b.write(0);         // ARCOUNT
        for (String label : domain.split("\\.")) {
            byte[] l = label.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            b.write(l.length);
            b.write(l, 0, l.length);
        }
        b.write(0);                     // end of QNAME
        b.write(0); b.write(1);         // QTYPE  = A
        b.write(0); b.write(1);         // QCLASS = IN
        return b.toByteArray();
    }

    /**
     * Pull the queried domain out of a DNS message.
     *
     * A DNS query starts with a 12-byte header, then the QNAME: a series of
     * labels, each one a length byte followed by that many ASCII bytes,
     * terminated by a zero byte. "ads.example.com" is encoded as
     * [3]"ads"[7]"example"[3]"com"[0].
     *
     * @param packet    the full IP packet
     * @param dnsStart  index where the DNS message begins
     * @param dnsLength length of the DNS message
     * @return lower-cased domain, or null if malformed/empty
     */
    public static String extractDomain(byte[] packet, int dnsStart, int dnsLength) {
        int end = validatedDnsEnd(packet, dnsStart, dnsLength);
        if (end < 0 || (packet[dnsStart + 4] & 0xFF) != 0
                || (packet[dnsStart + 5] & 0xFF) != 1) return null;

        int pos = dnsStart + 12;
        StringBuilder domain = new StringBuilder();
        boolean terminated = false;
        while (pos < end) {
            int labelLen = packet[pos++] & 0xFF;
            if (labelLen == 0) { terminated = true; break; }
            // Compressed QNAMEs and labels above 63 bytes are not supported.
            if (labelLen > 63 || labelLen > end - pos) return null;
            if (domain.length() > 0) domain.append('.');
            domain.append(new String(packet, pos, labelLen,
                    java.nio.charset.StandardCharsets.US_ASCII));
            if (domain.length() > 253) return null;
            pos += labelLen;
        }
        if (!terminated || domain.length() == 0 || end - pos < 4) return null;
        return domain.toString().toLowerCase(java.util.Locale.ROOT);
    }

    private static int validatedDnsEnd(byte[] packet, int start, int length) {
        if (packet == null || start < 0 || length < 17
                || start > packet.length || length > packet.length - start) return -1;
        return start + length;
    }

    /**
     * Offset after QNAME + QTYPE + QCLASS, or -1 for a malformed query.
     * Every accepted DNS query must contain precisely one question.
     */
    public static int questionEnd(byte[] packet, int dnsStart, int dnsLength) {
        int end = validatedDnsEnd(packet, dnsStart, dnsLength);
        if (end < 0 || (packet[dnsStart + 4] & 0xFF) != 0
                || (packet[dnsStart + 5] & 0xFF) != 1) return -1;

        int pos = dnsStart + 12;
        boolean terminated = false;
        while (pos < end) {
            int labelLen = packet[pos++] & 0xFF;
            if (labelLen == 0) { terminated = true; break; }
            if (labelLen > 63 || labelLen > end - pos) return -1;
            pos += labelLen;
        }
        if (!terminated || end - pos < 4) return -1;
        return pos + 4;
    }

    /**
     * Turn a DNS query into an NXDOMAIN ("no such domain") response. Apps
     * treat this as "the domain doesn't exist" and give up -- the ad never
     * loads.
     *
     * We keep ONLY the header and the question, dropping everything after it.
     * That matters: modern resolvers attach an EDNS0 OPT record in the
     * additional section of every query. If we echo those bytes back while
     * setting ARCOUNT to 0, the message claims no additional records but has
     * one anyway -- malformed. The resolver throws the answer away, retries,
     * gets malformed again, and Android eventually marks our DNS server as
     * broken and stops using it, which kills name resolution device-wide.
     */
    public static byte[] buildNxDomain(byte[] packet, int dnsStart, int dnsLength) {
        int qEnd = questionEnd(packet, dnsStart, dnsLength);
        if (qEnd == -1) throw new IllegalArgumentException("Malformed DNS question");
        int len = qEnd - dnsStart;

        byte[] dns = new byte[len];
        System.arraycopy(packet, dnsStart, dns, 0, len);

        // Flags: QR=1 (response), RD=1, RA=1, RCODE=3 (NXDOMAIN)
        dns[2] = (byte) (0x80 | (packet[dnsStart + 2] & 0x01)); // QR + original RD
        dns[3] = (byte) 0x83; // RA + NXDOMAIN

        // Counts now match what the message actually contains.
        dns[4] = 0; dns[5] = 1;   // QDCOUNT = 1 (the echoed question)
        dns[6] = 0; dns[7] = 0;   // ANCOUNT
        dns[8] = 0; dns[9] = 0;   // NSCOUNT
        dns[10] = 0; dns[11] = 0; // ARCOUNT
        return dns;
    }

    /** Retryable failure when no capacity exists to relay a valid question. */
    public static byte[] buildServFail(byte[] packet, int dnsStart, int dnsLength) {
        byte[] dns = buildNxDomain(packet, dnsStart, dnsLength);
        dns[3] = (byte) 0x82; // RA + SERVFAIL
        return dns;
    }

    /**
     * Wrap a DNS response payload back into a full IP+UDP packet that we can
     * write into the tunnel. We reuse the original packet's IP header, swap
     * the source/destination so it flows back to the app, fix the length
     * fields, and recompute the IP checksum.
     *
     * @param original    the incoming query packet (for its IP header)
     * @param ipHeaderLen length of that IP header
     * @param dnsResponse the DNS message to send back
     */
    public static byte[] buildResponsePacket(byte[] original, int ipHeaderLen, byte[] dnsResponse) {
        if (original == null || dnsResponse == null || ipHeaderLen < 20
                || ipHeaderLen > original.length - 8) {
            throw new IllegalArgumentException("Invalid IPv4/UDP response envelope");
        }
        int udpLen = 8 + dnsResponse.length;
        int totalLen = ipHeaderLen + udpLen;
        if (totalLen > 65535) throw new IllegalArgumentException("IP packet too large");
        byte[] out = new byte[totalLen];

        // ---- IP header: copy, then rewrite ----
        System.arraycopy(original, 0, out, 0, ipHeaderLen);

        // Total length (bytes 2-3)
        out[2] = (byte) (totalLen >> 8);
        out[3] = (byte) (totalLen & 0xFF);

        // Swap source (bytes 12-15) and destination (bytes 16-19) addresses,
        // so the reply travels app <- us instead of app -> us.
        for (int i = 0; i < 4; i++) {
            byte tmp = out[12 + i];
            out[12 + i] = out[16 + i];
            out[16 + i] = tmp;
        }

        // Recompute the IP header checksum: zero the field first (bytes 10-11).
        out[10] = 0;
        out[11] = 0;
        int ipChecksum = checksum(out, 0, ipHeaderLen);
        out[10] = (byte) (ipChecksum >> 8);
        out[11] = (byte) (ipChecksum & 0xFF);

        // ---- UDP header ----
        int udp = ipHeaderLen;
        int originalSrcPort = ((original[udp] & 0xFF) << 8) | (original[udp + 1] & 0xFF);
        // Response source port = 53, destination = whatever port the app used.
        out[udp]     = 0;
        out[udp + 1] = 53;
        out[udp + 2] = (byte) (originalSrcPort >> 8);
        out[udp + 3] = (byte) (originalSrcPort & 0xFF);
        out[udp + 4] = (byte) (udpLen >> 8);
        out[udp + 5] = (byte) (udpLen & 0xFF);
        // UDP checksum is optional in IPv4; 0 means "not computed".
        out[udp + 6] = 0;
        out[udp + 7] = 0;

        // ---- payload ----
        System.arraycopy(dnsResponse, 0, out, ipHeaderLen + 8, dnsResponse.length);
        return out;
    }

    /**
     * Standard one's-complement Internet checksum (RFC 1071), used for the
     * IPv4 header. Sum the data as 16-bit words, fold the carries back in,
     * then invert.
     */
    private static int checksum(byte[] data, int offset, int length) {
        long sum = 0;
        int i = offset;
        while (length > 1) {
            sum += ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
            i += 2;
            length -= 2;
        }
        if (length == 1) { // odd byte left over
            sum += (data[i] & 0xFF) << 8;
        }
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (int) (~sum & 0xFFFF);
    }
}
