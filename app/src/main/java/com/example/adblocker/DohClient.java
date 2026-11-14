package com.example.adblocker;

import android.net.Network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * DNS-over-HTTPS POST transport (RFC 8484).
 *
 * All connections use the chosen physical Network rather than the VPN's
 * virtual resolver. TLS hostname verification is provided by Android.
 */
final class DohClient {
    static final String ENDPOINT = DohProvider.endpoint(DohProvider.CLOUDFLARE);
    static final String MIME = "application/dns-message";
    private static final int MAX_RESPONSE = DnsTcpWire.MAX_DNS_RESPONSE;

    private DohClient() {}

    static byte[] query(Network network, byte[] dns, int timeoutMs) throws IOException {
        return query(network, dns, timeoutMs, DohProvider.CLOUDFLARE);
    }

    static byte[] query(Network network, byte[] dns, int timeoutMs, String provider)
            throws IOException {
        if (network == null) throw new IOException("No underlying network");
        if (dns == null || dns.length < 17 || dns.length > 4096)
            throw new IOException("Invalid DNS query length");
        HttpURLConnection conn = (HttpURLConnection) network
                .openConnection(new URL(DohProvider.endpoint(provider)));
        try {
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Content-Type", MIME);
            conn.setRequestProperty("Accept", MIME);
            conn.setRequestProperty("Accept-Encoding", "identity");
            conn.setFixedLengthStreamingMode(dns.length);
            try (OutputStream output = conn.getOutputStream()) {
                output.write(dns);
            }
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new IOException("DoH returned HTTP " + conn.getResponseCode());
            String contentType = conn.getContentType();
            if (contentType == null
                    || !contentType.toLowerCase(Locale.ROOT).split(";", 2)[0]
                            .trim().equals(MIME))
                throw new IOException("DoH returned unexpected content type");
            String encoding = conn.getContentEncoding();
            if (encoding != null && !encoding.equalsIgnoreCase("identity"))
                throw new IOException("DoH returned encoded DNS data");
            if (conn.getContentLengthLong() > MAX_RESPONSE)
                throw new IOException("DoH response too large");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (output.size() + read > MAX_RESPONSE)
                        throw new IOException("DoH response too large");
                    output.write(buffer, 0, read);
                }
            }
            byte[] response = output.toByteArray();
            if (!DohReplyPolicy.isAcceptable(dns, response))
                throw new IOException("DoH returned incomplete or mismatched DNS reply");
            return response;
        } finally {
            conn.disconnect();
        }
    }

    static boolean probe(Network network) {
        return probe(network, DohProvider.CLOUDFLARE);
    }

    static boolean probe(Network network, String provider) {
        try {
            byte[] query = DnsPacket.buildQuery("example.com", 0x4a21);
            return query(network, query, 4000, provider) != null;
        } catch (IOException e) {
            return false;
        }
    }
}
