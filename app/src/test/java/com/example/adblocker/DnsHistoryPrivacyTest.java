package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class DnsHistoryPrivacyTest {
    @Test public void disablingImmediatelyErasesAndBlocksNewEvents() {
        DnsHistory history = new DnsHistory(4);
        history.add("ads.example", true, 1);
        assertEquals(1, history.size());
        long previousVersion = history.version();
        history.setRecordingEnabled(false);
        assertFalse(history.recordingEnabled());
        assertEquals(0, history.size());
        assertTrue(history.version() > previousVersion);
        history.add("private.example", false, 2);
        assertEquals(0, history.size());
        assertTrue(history.topBlocked(2).isEmpty());
    }

    @Test public void enablingOnlyCapturesFutureEvents() {
        DnsHistory history = new DnsHistory(4);
        history.add("old.example", true, 1);
        history.setRecordingEnabled(false);
        history.setRecordingEnabled(true);
        assertTrue(history.recordingEnabled());
        assertEquals(0, history.size());
        history.add("new.example", true, 2);
        assertEquals("new.example", history.search("", DnsHistory.Filter.ALL, 5).get(0).domain);
        assertEquals(1, history.size());
    }

    @Test public void historyCapacityStillAppliesAfterPrivacyToggle() {
        DnsHistory history = new DnsHistory(2);
        history.setRecordingEnabled(false);
        history.setRecordingEnabled(true);
        for (int i = 0; i < 4; i++) history.add("domain" + i + ".example", true, i);
        assertEquals(2, history.size());
        assertEquals("domain3.example",
                history.search("", DnsHistory.Filter.ALL, 1).get(0).domain);
    }
}
