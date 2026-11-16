package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class DnsSessionTrendsTest {
    @Test public void aggregatesWithoutPerDomainData() {
        DnsSessionTrends trends = new DnsSessionTrends();
        trends.record(true, 2000);
        trends.record(false, 2100);
        trends.record(false, 2200);
        DnsSessionTrends.Snapshot s = trends.snapshot(2300);
        assertArrayEquals(new int[]{1, 0, 0, 0}, s.blocked);
        assertArrayEquals(new int[]{2, 0, 0, 0}, s.passed);
    }

    @Test public void rollsAcrossBucketsAndExpiresOldCounts() {
        DnsSessionTrends trends = new DnsSessionTrends();
        trends.record(true, 0);
        trends.record(false, DnsSessionTrends.BUCKET_MS + 2);
        DnsSessionTrends.Snapshot s = trends.snapshot(DnsSessionTrends.BUCKET_MS + 3);
        assertArrayEquals(new int[]{0, 1, 0, 0}, s.blocked);
        assertArrayEquals(new int[]{1, 0, 0, 0}, s.passed);
        s = trends.snapshot(5 * DnsSessionTrends.BUCKET_MS);
        assertArrayEquals(new int[]{0, 0, 0, 0}, s.blocked);
        assertArrayEquals(new int[]{0, 0, 0, 0}, s.passed);
    }

    @Test public void rejectsStaleMonotonicSamplesAndResetsBetweenSessions() {
        DnsSessionTrends trends = new DnsSessionTrends();
        trends.record(true, 4 * DnsSessionTrends.BUCKET_MS);
        trends.record(false, 1);
        assertEquals(1, trends.snapshot(4 * DnsSessionTrends.BUCKET_MS).blocked[0]);
        assertEquals(0, trends.snapshot(4 * DnsSessionTrends.BUCKET_MS).passed[0]);
        trends.clear();
        assertEquals(0, trends.snapshot(4 * DnsSessionTrends.BUCKET_MS).blocked[0]);
    }

    @Test public void snapshotsCannotMutateStoredCounts() {
        DnsSessionTrends trends = new DnsSessionTrends();
        trends.record(true, 2);
        DnsSessionTrends.Snapshot s = trends.snapshot(3);
        s.blocked[0] = 100;
        assertEquals(1, trends.snapshot(3).blocked[0]);
    }
}
