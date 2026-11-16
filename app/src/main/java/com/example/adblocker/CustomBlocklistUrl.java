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
            String normalizedHost = host == null ? null
                    : host.toLowerCase(java.util.Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || normalizedHost == null
                    || uri.getUserInfo() != null || uri.getPort() != -1
                    || uri.getFragment() != null || uri.getRawQuery() != null
                    || normalizedHost.endsWith(".")
                    || normalizedHost.endsWith(".local")
                    || normalizedHost.endsWith(".internal")
                    || normalizedHost.endsWith(".localhost")
                    || normalizedHost.endsWith(".lan")
                    || DomainRules.normalize(normalizedHost) == null) return null;
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            if (!path.startsWith("/") || path.length() > 1024
                    || path.contains("\\") || path.contains("%")) return null;
            return "https://" + normalizedHost + path;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** Refuse private, reserved and tunnel destinations even in mixed DNS answers. */
    static boolean publicAddresses(InetAddress[] addresses) {
        if (addresses == null || addresses.length == 0) return false;
        for (InetAddress address : addresses) {
            if (address == null || address.isAnyLocalAddress()
                    || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress())
                return false;
            byte[] bytes = address.getAddress();
            if (bytes.length == 4) {
                if (!publicIpv4(bytes)) return false;
            } else if (bytes.length == 16) {
                if (!publicIpv6(bytes)) return false;
            } else {
                return false;
            }
        }
        return true;
    }

    private static boolean publicIpv4(byte[] bytes) {
        int a = bytes[0] & 255, b = bytes[1] & 255, c = bytes[2] & 255;
        return a != 0 && a != 10 && a != 127 && a < 224
                && !(a == 100 && b >= 64 && b <= 127)    // CGNAT
                && !(a == 169 && b == 254)               // link-local
                && !(a == 172 && b >= 16 && b <= 31)
                && !(a == 192 && b == 168)
                && !(a == 192 && b == 0 && (c == 0 || c == 2))
                && !(a == 192 && b == 88 && c == 99)
                && !(a == 198 && (b == 18 || b == 19))    // benchmark networks
                && !(a == 198 && b == 51 && c == 100)     // TEST-NET-2
                && !(a == 203 && b == 0 && c == 113);     // TEST-NET-3
    }

    private static boolean publicIpv6(byte[] bytes) {
        int first = bytes[0] & 255, second = bytes[1] & 255;
        // Well-known DNS64/NAT64: assess the embedded IPv4 destination.
        if (first == 0x00 && second == 0x64
                && (bytes[2] & 255) == 0xff && (bytes[3] & 255) == 0x9b) {
            for (int i = 4; i < 12; i++) if (bytes[i] != 0) return false;
            return publicIpv4(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]});
        }
        // Global unicast only, excluding special transition and documentation ranges.
        if ((first & 0xe0) != 0x20) return false;
        if (first == 0x20 && second == 0x02) return false; // 6to4 tunnel
        if (first == 0x20 && second == 0x01) {
            int third = bytes[2] & 255, fourth = bytes[3] & 255;
            if (third == 0 && fourth == 0) return false; // Teredo tunnel
            if (third == 0x0d && fourth == 0xb8) return false; // RFC 3849
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
