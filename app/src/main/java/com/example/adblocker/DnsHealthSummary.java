package com.example.adblocker;

/** A read-only, local DNS diagnostics summary. Not a real connectivity probe. */
final class DnsHealthSummary {
    enum State { STOPPED, STARTING, IDLE, HEALTHY, DEGRADED }
    private DnsHealthSummary() {}

    static State assess(boolean running, boolean starting, int answered,
                        int timeouts, int errors) {
        if (starting && !running) return State.STARTING;
        if (!running) return State.STOPPED;
        if (answered == 0 && timeouts == 0 && errors == 0) return State.IDLE;
        if (timeouts + errors > 0 && (answered == 0 || timeouts + errors >= answered))
            return State.DEGRADED;
        return State.HEALTHY;
    }
}
