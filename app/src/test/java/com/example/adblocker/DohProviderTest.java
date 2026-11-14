package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class DohProviderTest {
    @Test public void defaultsToCloudflare() {
        assertEquals(DohProvider.CLOUDFLARE, DohProvider.normalize(null));
        assertEquals(DohProvider.CLOUDFLARE, DohProvider.normalize("unknown"));
        assertEquals("https://cloudflare-dns.com/dns-query", DohProvider.endpoint("unknown"));
    }

    @Test public void usesExpectedEncryptedEndpoints() {
        assertEquals("https://dns.google/dns-query",
                DohProvider.endpoint(DohProvider.GOOGLE));
        assertEquals("https://dns.quad9.net/dns-query",
                DohProvider.endpoint(DohProvider.QUAD9));
    }

    @Test public void disallowsUserControlledUrls() {
        assertEquals(DohProvider.CLOUDFLARE,
                DohProvider.normalize("https://127.0.0.1/dns-query"));
        assertEquals(DohProvider.CLOUDFLARE,
                DohProvider.normalize("http://dns.google/dns-query"));
    }
}
