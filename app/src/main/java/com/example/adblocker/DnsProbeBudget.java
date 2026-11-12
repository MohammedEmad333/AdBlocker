package com.example.adblocker;

/**
 * Bounds sequential resolver discovery so unreachable DNS servers cannot
 * hold VPN startup for the sum of every individual probe timeout.
 */
final class DnsProbeBudget {
    static final int MAX_TOTAL_MS = 6000;
    static final int PER_SERVER_MS = 1500;

    private DnsProbeBudget() {}

    /**
     * Remaining timeout for one server in milliseconds. Returns zero once
     * the discovery budget expires. Caller measures elapsed with a monotonic clock.
     */
    static int timeoutMillis(long elapsedMs) {
        if (elapsedMs < 0) elapsedMs = 0;
        long remaining = MAX_TOTAL_MS - elapsedMs;
        if (remaining <= 0) return 0;
        return (int) Math.min(PER_SERVER_MS, remaining);
    }
}
