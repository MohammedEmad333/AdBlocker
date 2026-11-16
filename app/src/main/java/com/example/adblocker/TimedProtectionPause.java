package com.example.adblocker;

/**
 * RAM-only whole-filter pause. Uses monotonic time: wall-clock adjustments
 * cannot prolong a pause. Expiry is checked on every query, including TCP DNS.
 */
final class TimedProtectionPause {
    static final long FIVE_MINUTES = 5L * 60 * 1000;
    static final long FIFTEEN_MINUTES = 15L * 60 * 1000;
    static final long THIRTY_MINUTES = 30L * 60 * 1000;
    private long expiresAt;

    synchronized boolean pause(long durationMs, long nowMs) {
        if (durationMs != FIVE_MINUTES && durationMs != FIFTEEN_MINUTES
                && durationMs != THIRTY_MINUTES || nowMs < 0
                || nowMs > Long.MAX_VALUE - durationMs) return false;
        expiresAt = nowMs + durationMs;
        return true;
    }

    synchronized long remaining(long nowMs) {
        if (expiresAt == 0 || nowMs < 0 || nowMs >= expiresAt) {
            expiresAt = 0;
            return 0;
        }
        return expiresAt - nowMs;
    }

    synchronized boolean isPaused(long nowMs) { return remaining(nowMs) > 0; }

    synchronized void clear() { expiresAt = 0; }
}
