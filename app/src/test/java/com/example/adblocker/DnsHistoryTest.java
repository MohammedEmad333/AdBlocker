package com.example.adblocker;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class DnsHistoryTest {
    @Test public void newestFirstAndBounded() {
        DnsHistory h = new DnsHistory(2);
        h.add("first.example", false, 100L);
        h.add("second.example", true, 200L);
        h.add("third.example", false, 300L);
        List<DnsHistory.Entry> rows = h.search("", DnsHistory.Filter.ALL, 20);
        assertEquals(2, rows.size());
        assertEquals("third.example", rows.get(0).domain);
        assertEquals("second.example", rows.get(1).domain);
        assertEquals(300L, rows.get(0).timeMillis);
    }

    @Test public void filtersBlockedAndAllowed() {
        DnsHistory h = new DnsHistory(10);
        h.add("safe.example", false, 1);
        h.add("ads.example", true, 2);
        h.add("tracking.example", true, 3);
        assertEquals(2, h.search("", DnsHistory.Filter.BLOCKED, 10).size());
        assertEquals("safe.example", h.search("", DnsHistory.Filter.ALLOWED, 10).get(0).domain);
    }

    @Test public void searchIsCaseInsensitiveAndLimited() {
        DnsHistory h = new DnsHistory(10);
        h.add("ADS.Example.org", true, 1);
        h.add("safe.org", false, 2);
        h.add("pixel.ads.example.org", true, 3);
        List<DnsHistory.Entry> rows = h.search("Ads.Example", DnsHistory.Filter.ALL, 1);
        assertEquals(1, rows.size());
        assertEquals("pixel.ads.example.org", rows.get(0).domain);
    }

    @Test public void clearingRemovesSensitiveHistoryAndBumpsVersion() {
        DnsHistory h = new DnsHistory(5);
        long first = h.version();
        h.add("example.org", true, 1);
        assertTrue(h.version() > first);
        long added = h.version();
        h.clear();
        assertEquals(0, h.size());
        assertTrue(h.version() > added);
    }

    @Test public void rejectsInvalidCapacityAndNonPositiveLimits() {
        try {
            new DnsHistory(0);
            fail("Should reject capacity zero");
        } catch (IllegalArgumentException expected) {}
        DnsHistory h = new DnsHistory(3);
        h.add(null, false, 1);
        assertEquals(0, h.size());
        h.add("a.example", true, 2);
        assertTrue(h.search("", null, 0).isEmpty());
    }
}
