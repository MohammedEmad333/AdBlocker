package com.example.adblocker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import okhttp3.ConnectionPool;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * HTTPS-only blocklist transport. DNS validation happens inside OkHttp's DNS
 * callback: the returned, already-validated addresses are the ONLY connection
 * candidates. This closes the preflight DNS-check / connection DNS-rebind gap.
 *
 * The normal system trust store, TLS SNI and hostname verification stay enabled.
 */
final class SecureBlocklistTransport {
    private SecureBlocklistTransport() {}

    static void download(String sourceUrl, File target, long maxBytes) throws IOException {
        download(sourceUrl, target, maxBytes, null);
    }

    static void download(String sourceUrl, File target, long maxBytes,
                         BooleanSupplier stillAllowed) throws IOException {
        BlocklistRefreshGuard.requireActive(stillAllowed);
        String checked = CustomBlocklistUrl.normalize(sourceUrl);
        if (checked == null || !checked.equals(sourceUrl))
            throw new IOException("Invalid blocklist HTTPS URL");
        HttpUrl url = HttpUrl.parse(checked);
        if (url == null || !"https".equals(url.scheme()) || url.port() != 443)
            throw new IOException("HTTPS on the default port required");

        OkHttpClient client = new OkHttpClient.Builder()
                .dns(new ValidatedDns(url.host(), Dns.SYSTEM))
                // Proxy resolution happens outside the custom Dns callback.
                .proxy(Proxy.NO_PROXY)
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.SECONDS))
                .build();

        Request request = new Request.Builder().url(url)
                .header("Accept-Encoding", "identity")
                .build();
        BlocklistRefreshGuard.requireActive(stillAllowed);
        try (Response response = client.newCall(request).execute()) {
            if (response.code() != 200)
                throw new IOException("Blocklist HTTP status: " + response.code());
            String encoding = response.header("Content-Encoding");
            if (encoding != null && !encoding.equalsIgnoreCase("identity"))
                throw new IOException("Encoded blocklists are not supported");
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Empty HTTPS response");
            if (body.contentLength() > maxBytes)
                throw new IOException("Blocklist exceeds safe download limit");

            BlocklistRefreshGuard.requireActive(stillAllowed);
            try (InputStream in = body.byteStream();
                 FileOutputStream out = new FileOutputStream(target)) {
                copyBounded(in, out, maxBytes, stillAllowed);
            }
        } finally {
            client.connectionPool().evictAll();
        }
    }

    /** Bounded streaming copy with cancellation checks around every read. */
    static void copyBounded(InputStream in, OutputStream out, long maxBytes,
                            BooleanSupplier stillAllowed) throws IOException {
        if (maxBytes <= 0) throw new IOException("Invalid blocklist size limit");
        byte[] buffer = new byte[8192];
        long downloaded = 0;
        while (true) {
            BlocklistRefreshGuard.requireActive(stillAllowed);
            int read = in.read(buffer);
            BlocklistRefreshGuard.requireActive(stillAllowed);
            if (read == -1) return;
            downloaded += read;
            if (downloaded > maxBytes)
                throw new IOException("Blocklist exceeds safe download limit");
            out.write(buffer, 0, read);
        }
    }

    /**
     * No separate DNS precheck: OkHttp connects only to IPs returned here.
     * Reject the ENTIRE answer set if any address is unsafe, and reject
     * unexpected hostnames (including potential cross-host connections).
     */
    static final class ValidatedDns implements Dns {
        private final String expectedHost;
        private final Dns delegate;

        ValidatedDns(String expectedHost, Dns delegate) {
            this.expectedHost = expectedHost;
            this.delegate = delegate;
        }

        @Override public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            if (!expectedHost.equalsIgnoreCase(hostname))
                throw new UnknownHostException("Unexpected blocklist hostname");
            List<InetAddress> resolved = delegate.lookup(hostname);
            if (resolved == null || resolved.isEmpty()
                    || !CustomBlocklistUrl.publicAddresses(
                            resolved.toArray(new InetAddress[0])))
                throw new UnknownHostException("Unsafe blocklist DNS addresses");
            return Collections.unmodifiableList(new ArrayList<>(resolved));
        }
    }
}
