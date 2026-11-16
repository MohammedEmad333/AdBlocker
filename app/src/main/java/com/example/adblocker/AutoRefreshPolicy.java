package com.example.adblocker;

/** Scheduling inputs are validated before persisting or passing to Android. */
final class AutoRefreshPolicy {
    static final String DAILY = "daily";
    static final String WEEKLY = "weekly";
    static final long DAY_MS = 24L * 60 * 60 * 1000;

    private AutoRefreshPolicy() {}

    static String normalize(String frequency) {
        return WEEKLY.equals(frequency) ? WEEKLY : DAILY;
    }

    static long intervalMs(String frequency) {
        return WEEKLY.equals(normalize(frequency)) ? DAY_MS * 7 : DAY_MS;
    }
}
