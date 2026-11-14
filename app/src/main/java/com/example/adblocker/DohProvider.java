package com.example.adblocker;

/** Fixed HTTPS endpoints only: no arbitrary URLs or silent plaintext fallback. */
final class DohProvider {
    static final String CLOUDFLARE = "cloudflare";
    static final String GOOGLE = "google";
    static final String QUAD9 = "quad9";

    private DohProvider() {}

    static String normalize(String id) {
        if (GOOGLE.equals(id) || QUAD9.equals(id)) return id;
        return CLOUDFLARE;
    }

    static String endpoint(String id) {
        switch (normalize(id)) {
            case GOOGLE: return "https://dns.google/dns-query";
            case QUAD9: return "https://dns.quad9.net/dns-query";
            default: return "https://cloudflare-dns.com/dns-query";
        }
    }

    static String label(String id) {
        switch (normalize(id)) {
            case GOOGLE: return "Google Public DNS";
            case QUAD9: return "Quad9";
            default: return "Cloudflare";
        }
    }
}
