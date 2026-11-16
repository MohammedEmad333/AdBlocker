package com.example.adblocker;

/** Validation requirements are determined by source identity, not its host. */
final class BlocklistSourcePolicy {
    static final int BUILTIN_MIN_DOMAINS = 1000;
    static final int CUSTOM_MIN_DOMAINS = 10;
    static final long BUILTIN_MIN_BYTES = 1024;
    static final long CUSTOM_MIN_BYTES = 1;
    static final long MAX_CACHE_AGE_MS = 24L * 60 * 60 * 1000;

    private BlocklistSourcePolicy() {}

    static int minimumDomains(boolean custom) {
        return custom ? CUSTOM_MIN_DOMAINS : BUILTIN_MIN_DOMAINS;
    }

    static long minimumBytes(boolean custom) {
        return custom ? CUSTOM_MIN_BYTES : BUILTIN_MIN_BYTES;
    }

    /** Clock rollback/future timestamps must not keep stale caches fresh forever. */
    static boolean cacheFresh(long bytes, long modifiedAt, long now, boolean custom) {
        if (!cacheUsable(bytes, custom) || modifiedAt <= 0 || now < modifiedAt)
            return false;
        return now - modifiedAt < MAX_CACHE_AGE_MS;
    }

    static boolean cacheUsable(long bytes, boolean custom) {
        return bytes >= minimumBytes(custom) && bytes <= 16L * 1024 * 1024;
    }
}
