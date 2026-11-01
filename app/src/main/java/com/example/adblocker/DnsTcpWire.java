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
        return query != null && response != null && query.length >= 12
                && response.length >= 12 && query[0] == response[0]
                && query[1] == response[1] && (response[2] & 0x80) != 0;
    }
}
