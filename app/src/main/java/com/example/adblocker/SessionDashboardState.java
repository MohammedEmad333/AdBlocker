package com.example.adblocker;

/** Pure-Java display decisions shared across dashboard states and unit tests. */
final class SessionDashboardState {
    private SessionDashboardState() {}

    static boolean recentHistoryAvailable(boolean recordingEnabled, int entries) {
        return recordingEnabled && entries > 0;
    }

    static boolean needsSourceUpdate(long lastUpdate, long now) {
        return lastUpdate <= 0 || now < lastUpdate
                || now - lastUpdate >= BlocklistSourcePolicy.MAX_CACHE_AGE_MS;
    }

    static int blockPercentage(int blocked, int total) {
        if (total <= 0) return 0;
        int percentage = (int) Math.round(100.0 * Math.max(0, blocked) / total);
        return Math.min(100, percentage);
    }
}
