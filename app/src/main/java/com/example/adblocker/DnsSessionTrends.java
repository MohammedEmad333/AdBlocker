package com.example.adblocker;

import java.util.Arrays;

/**
 * Private, RAM-only, bounded DNS decision totals for four 15-minute buckets.
 * Stores no domain names, timestamps per event, IP addresses or app identifiers.
 * Uses monotonic time, not wall clock.
 */
final class DnsSessionTrends {
    static final long BUCKET_MS = 15L * 60 * 1000;
    static final int BUCKET_COUNT = 4;

    static final class Snapshot {
        final int[] blocked;
        final int[] passed;
        Snapshot(int[] blocked, int[] passed) {
            this.blocked = blocked;
            this.passed = passed;
        }
    }

    private final int[] blocked = new int[BUCKET_COUNT];
    private final int[] passed = new int[BUCKET_COUNT];
    private long latestBucket = Long.MIN_VALUE;

    synchronized void record(boolean isBlocked, long elapsedMs) {
        if (elapsedMs < 0 || !advance(elapsedMs / BUCKET_MS)) return;
        int[] counts = isBlocked ? blocked : passed;
        if (counts[0] < Integer.MAX_VALUE) counts[0]++;
    }

    synchronized Snapshot snapshot(long elapsedMs) {
        if (elapsedMs < 0) return new Snapshot(new int[BUCKET_COUNT], new int[BUCKET_COUNT]);
        advance(elapsedMs / BUCKET_MS);
        return new Snapshot(blocked.clone(), passed.clone());
    }

    private boolean advance(long bucket) {
        if (latestBucket == Long.MIN_VALUE) {
            latestBucket = bucket;
            return true;
        }
        if (bucket < latestBucket) return false;
        long delta = bucket - latestBucket;
        if (delta >= BUCKET_COUNT) {
            Arrays.fill(blocked, 0);
            Arrays.fill(passed, 0);
        } else if (delta > 0) {
            for (int i = BUCKET_COUNT - 1; i >= delta; i--) {
                blocked[i] = blocked[(int) (i - delta)];
                passed[i] = passed[(int) (i - delta)];
            }
            for (int i = 0; i < delta; i++) {
                blocked[i] = 0;
                passed[i] = 0;
            }
        }
        latestBucket = bucket;
        return true;
    }

    synchronized void clear() {
        Arrays.fill(blocked, 0);
        Arrays.fill(passed, 0);
        latestBucket = Long.MIN_VALUE;
    }
}
