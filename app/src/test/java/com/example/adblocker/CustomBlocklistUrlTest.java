package com.example.adblocker;

import org.junit.Test;
import java.net.InetAddress;
import static org.junit.Assert.*;

public class CustomBlocklistUrlTest {
    @Test public void acceptsSecurePublicHosts() {
        assertEquals("https://lists.example.org/hosts.txt",
                CustomBlocklistUrl.normalize(" https://lists.example.org/hosts.txt "));
    }

    @Test public void refusesUnsafeUrls() {
        for (String url : new String[]{
                "http://lists.example.org/hosts.txt",
                "https://user:pw@lists.example.org/hosts",
                "https://lists.example.org:8443/hosts",
                "https://lists.example.org/hosts?token=abc",
                "https://lists.example.org/hosts#fragment",
                "https://localhost/hosts", "https://10.0.0.1/hosts",
                "https://router.local/hosts", "https://lists.example.org/%2e%2e/x"}) {
            assertNull(url, CustomBlocklistUrl.normalize(url));
        }
    }

    @Test public void deniesLocalAndSpecialAddressResolution() throws Exception {
        assertFalse(CustomBlocklistUrl.publicAddresses(
                new InetAddress[]{InetAddress.getByName("127.0.0.1")}));
        assertFalse(CustomBlocklistUrl.publicAddresses(
                new InetAddress[]{InetAddress.getByName("10.1.2.3")}));
        assertFalse(CustomBlocklistUrl.publicAddresses(
                new InetAddress[]{InetAddress.getByName("100.64.2.3")}));
        assertFalse(CustomBlocklistUrl.publicAddresses(
                new InetAddress[]{InetAddress.getByName("fc00::1")}));
        assertTrue(CustomBlocklistUrl.publicAddresses(
                new InetAddress[]{InetAddress.getByName("1.1.1.1")}));
    }

    @Test public void createsStableDistinctCacheKeys() {
        String first = CustomBlocklistUrl.cacheName("https://a.example/hosts");
        assertEquals(first, CustomBlocklistUrl.cacheName("https://a.example/hosts"));
        assertNotEquals(first, CustomBlocklistUrl.cacheName("https://b.example/hosts"));
        assertTrue(first.startsWith("blocklist-custom-"));
    }
}
