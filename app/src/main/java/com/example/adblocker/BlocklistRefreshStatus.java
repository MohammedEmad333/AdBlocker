package com.example.adblocker;

/** Per-attempt results for manually requested list downloads. */
public final class BlocklistRefreshStatus {
    public enum Outcome { NO_SOURCES, SUCCESS, PARTIAL, FAILED }

    private int successful;
    private int failed;

    void recordSuccess() { successful++; }
    void recordFailure() { failed++; }

    public int successfulSources() { return successful; }
    public int failedSources() { return failed; }

    public Outcome outcome() {
        if (successful == 0 && failed == 0) return Outcome.NO_SOURCES;
        if (failed == 0) return Outcome.SUCCESS;
        return successful == 0 ? Outcome.FAILED : Outcome.PARTIAL;
    }
}
