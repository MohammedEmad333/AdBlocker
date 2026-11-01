package com.example.adblocker;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** DNS-over-TCP framing (RFC 7766), for upstream fallback after UDP truncation. */
public final class DnsTcpWire {
    public static final int MAX_DNS_RESPONSE = 4096;

    private DnsTcpWire() {}

    public static void writeQuery(OutputStream output, byte[] dns) throws IOException {
        if (dns == null || dns.length < 12 || dns.length > 65535)
            throw new IOException("Invalid DNS message length");
        output.write(dns.length >>> 8);
        output.write(dns.length & 0xff);
        output.write(dns);
        output.flush();
    }

    public static byte[] readResponse(InputStream input) throws IOException {
        DataInputStream d = new DataInputStream(input);
        int length = d.readUnsignedShort();
        if (length < 12 || length > MAX_DNS_RESPONSE)
            throw new IOException("Unsupported TCP DNS response length: " + length);
        byte[] result = new byte[length];
        d.readFully(result);
        return result;
    }

    public static boolean matchesQuery(byte[] query, byte[] response) {
        if (query == null || response == null || query.length < 17
                || response.length < 17 || query[0] != response[0]
                || query[1] != response[1] || (response[2] & 0x80) == 0)
            return false;
        // Transaction ID alone is insufficient: ensure the reply contains the
        // *same question*, QTYPE and QCLASS. Name comparisons ignore case.
        int qEnd = DnsPacket.questionEnd(query, 0, query.length);
        int aEnd = DnsPacket.questionEnd(response, 0, response.length);
        if (qEnd < 0 || aEnd < 0) return false;
        String asked = DnsPacket.extractDomain(query, 0, query.length);
        String replied = DnsPacket.extractDomain(response, 0, response.length);
        if (asked == null || !asked.equals(replied)) return false;
        for (int i = 1; i <= 4; i++) {
            if (query[qEnd - i] != response[aEnd - i]) return false;
        }
        return true;
    }
}
