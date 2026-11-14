package com.example.adblocker;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Strict URL validation for one optional third-party HTTPS hosts source. */
final class CustomBlocklistUrl {
    private CustomBlocklistUrl() {}

    static String normalize(String raw) {
        if (raw == null || raw.length() > 1500) return null;
        try {
            URI uri = new URI(raw.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getUserInfo() != null || uri.getPort() != -1
                    || uri.getFragment() != null || uri.getRawQuery() != null
                    || host.endsWith(".local") || host.endsWith(".internal")
                    || host.endsWith(".localhost") || host.endsWith(".lan")
                    || DomainRules.normalize(host) == null) return null;
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            if (!path.startsWith("/") || path.length() > 1024
                    || path.contains("\\") || path.contains("%")) return null;
            return "https://" + host.toLowerCase(java.util.Locale.ROOT) + path;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** Reject destinations that resolve to private or special-purpose addresses. */
    static boolean publicAddresses(InetAddress[] addresses) {
        if (addresses == null || addresses.length == 0) return false;
        for (InetAddress a : addresses) {
            if (a == null || a.isAnyLocalAddress() || a.isLoopbackAddress()
                    || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                    || a.isMulticastAddress()) return false;
            byte[] b = a.getAddress();
            if (b.length == 16 && (b[0] & 0xfe) == 0xfc) return false; // IPv6 ULA
            if (b.length == 4 && ((b[0] & 255) == 0
                    || ((b[0] & 255) == 100 && (b[1] & 0xc0) == 64)
                    || ((b[0] & 255) == 198 && (b[1] & 255) == 18)
                    || ((b[0] & 255) >= 224))) return false;
        }
        return true;
    }

    static String cacheName(String normalized) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder("blocklist-custom-");
            for (int i = 0; i < 12; i++) name.append(String.format("%02x", hash[i] & 255));
            return name.append(".txt").toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
