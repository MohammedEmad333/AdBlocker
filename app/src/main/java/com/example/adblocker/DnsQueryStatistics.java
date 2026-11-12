package com.example.adblocker;

import java.util.concurrent.atomic.AtomicInteger;

/** Successful allowed responses, not merely requests that passed the blocklist. */
public final class DnsQueryStatistics {
    private final AtomicInteger resolvedAllowed = new AtomicInteger();

    public void recordAllowedResponse() {
        resolvedAllowed.incrementAndGet();
    }

    public int successfulAllowedResponses() {
        return resolvedAllowed.get();
    }

    public void reset() {
        resolvedAllowed.set(0);
    }
}
