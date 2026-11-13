package com.example.adblocker;

/** Rate limit error-driven upstream DNS recovery using monotonic elapsed time. */
final class DnsRecoveryCooldown {
    static final long INTERVAL_MS = 15_000L;
    private long lastAttempt = Long.MIN_VALUE;

    synchronized boolean tryAcquire(long elapsedMs) {
        if (lastAttempt != Long.MIN_VALUE && elapsedMs >= lastAttempt
                && elapsedMs - lastAttempt < INTERVAL_MS) return false;
        lastAttempt = elapsedMs;
        return true;
    }

    synchronized void reset() {
        lastAttempt = Long.MIN_VALUE;
    }
}
