package com.example.adblocker;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class DnsHistoryInsightTest {
    @Test public void aggregatesBlockedOnlyAndSortsByFrequency() {
        DnsHistory history = new DnsHistory(10);
        history.add("a.example", true, 1);
        history.add("b.example", true, 2);
        history.add("a.example", false, 3);
        history.add("a.example", true, 4);
        history.add("c.example", true, 5);
        List<DnsHistory.DomainCount> top = history.topBlocked(2);
        assertEquals(2, top.size());
        assertEquals("a.example", top.get(0).domain);
        assertEquals(2, top.get(0).count);
        assertEquals("b.example", top.get(1).domain);
    }

    @Test public void respectsBoundedHistoryAndSessionReset() {
        DnsHistory history = new DnsHistory(2);
        history.add("old.example", true, 1);
        history.add("new.example", true, 2);
        history.add("new.example", true, 3);
        List<DnsHistory.DomainCount> top = history.topBlocked(3);
        assertEquals(1, top.size());
        assertEquals(2, top.get(0).count);
        history.clear();
        assertTrue(history.topBlocked(3).isEmpty());
    }

    @Test public void equalCountsSortAlphabeticallyAndLimitIsHonored() {
        DnsHistory history = new DnsHistory(10);
        history.add("z.example", true, 1);
        history.add("a.example", true, 2);
        assertEquals("a.example", history.topBlocked(1).get(0).domain);
        assertTrue(history.topBlocked(0).isEmpty());
        assertTrue(history.topBlocked(-5).isEmpty());
    }
}
