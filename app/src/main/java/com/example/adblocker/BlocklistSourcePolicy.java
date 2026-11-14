package com.example.adblocker;

/** Validation requirements are determined by source identity, not its host. */
final class BlocklistSourcePolicy {
    static final int BUILTIN_MIN_DOMAINS = 1000;
    static final int CUSTOM_MIN_DOMAINS = 10;
    static final long BUILTIN_MIN_BYTES = 1024;
    static final long CUSTOM_MIN_BYTES = 1;

    private BlocklistSourcePolicy() {}

    static int minimumDomains(boolean custom) {
        return custom ? CUSTOM_MIN_DOMAINS : BUILTIN_MIN_DOMAINS;
    }

    static long minimumBytes(boolean custom) {
        return custom ? CUSTOM_MIN_BYTES : BUILTIN_MIN_BYTES;
    }

    static boolean cacheUsable(long bytes, boolean custom) {
        return bytes >= minimumBytes(custom) && bytes <= 16L * 1024 * 1024;
    }
}
