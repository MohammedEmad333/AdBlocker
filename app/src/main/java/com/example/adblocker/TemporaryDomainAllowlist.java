package com.example.adblocker;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Session-only exact-domain exemptions, never persisted to device storage.
 * Uses monotonic elapsed time so wall-clock changes cannot extend access.
 */
final class TemporaryDomainAllowlist {
    static final long FIFTEEN_MINUTES = 15L * 60 * 1000;
    static final long ONE_HOUR = 60L * 60 * 1000;
    static final int MAX_ENTRIES = 128;

    private final Map<String, Long> expiry = new HashMap<>();
    private long lastElapsedMs = -1;

    /**
     * Fail closed if the monotonic clock goes backwards (such as a reboot
     * in a restored process). Old temporary exemptions must never get longer.
     */
    private void observeElapsed(long nowElapsedMs) {
        if (lastElapsedMs >= 0 && nowElapsedMs < lastElapsedMs) expiry.clear();
        lastElapsedMs = nowElapsedMs;
    }

    synchronized boolean grant(String input, long durationMs, long nowElapsedMs) {
        String domain = DomainRules.normalize(input);
        if (domain == null || nowElapsedMs < 0
                || (durationMs != FIFTEEN_MINUTES && durationMs != ONE_HOUR)
                || nowElapsedMs > Long.MAX_VALUE - durationMs) return false;
        observeElapsed(nowElapsedMs);
        prune(nowElapsedMs);
        if (!expiry.containsKey(domain) && expiry.size() >= MAX_ENTRIES) return false;
        expiry.put(domain, nowElapsedMs + durationMs);
        return true;
    }

    synchronized boolean isAllowed(String input, long nowElapsedMs) {
        String domain = DomainRules.normalize(input);
        if (domain == null || nowElapsedMs < 0) return false;
        observeElapsed(nowElapsedMs);
        Long end = expiry.get(domain);
        if (end == null) return false;
        if (end <= nowElapsedMs) {
            expiry.remove(domain);
            return false;
        }
        return true;
    }

    synchronized int activeCount(long nowElapsedMs) {
        if (nowElapsedMs < 0) return 0;
        observeElapsed(nowElapsedMs);
        prune(nowElapsedMs);
        return expiry.size();
    }

    synchronized void clear() {
        expiry.clear();
        lastElapsedMs = -1;
    }

    private void prune(long nowElapsedMs) {
        Iterator<Map.Entry<String, Long>> it = expiry.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() <= nowElapsedMs) it.remove();
        }
    }
}
