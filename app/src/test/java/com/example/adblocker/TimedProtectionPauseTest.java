package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class TimedProtectionPauseTest {
    @Test public void pausesForSelectedDurationAndAutoExpires() {
        TimedProtectionPause pause = new TimedProtectionPause();
        assertTrue(pause.pause(TimedProtectionPause.FIVE_MINUTES, 1_000));
        assertTrue(pause.isPaused(1_000));
        assertEquals(1, pause.remaining(1_000 + TimedProtectionPause.FIVE_MINUTES - 1));
        assertFalse(pause.isPaused(1_000 + TimedProtectionPause.FIVE_MINUTES));
        assertEquals(0, pause.remaining(1_000 + TimedProtectionPause.FIVE_MINUTES + 1));
    }

    @Test public void extendingPauseReplacesExistingDeadline() {
        TimedProtectionPause pause = new TimedProtectionPause();
        assertTrue(pause.pause(TimedProtectionPause.FIFTEEN_MINUTES, 10_000));
        assertTrue(pause.pause(TimedProtectionPause.THIRTY_MINUTES, 20_000));
        assertEquals(TimedProtectionPause.THIRTY_MINUTES, pause.remaining(20_000));
        pause.clear();
        assertFalse(pause.isPaused(20_000));
    }

    @Test public void rejectsInvalidOrOverflowDurations() {
        TimedProtectionPause pause = new TimedProtectionPause();
        assertFalse(pause.pause(0, 100));
        assertFalse(pause.pause(1000, 100));
        assertFalse(pause.pause(TimedProtectionPause.FIVE_MINUTES, -1));
        assertFalse(pause.pause(TimedProtectionPause.FIVE_MINUTES, Long.MAX_VALUE));
        assertEquals(0, pause.remaining(100));
    }

    @Test public void monotonicClockRollbackImmediatelyReenablesBlocking() {
        TimedProtectionPause pause = new TimedProtectionPause();
        assertTrue(pause.pause(TimedProtectionPause.THIRTY_MINUTES, 1_000_000));
        assertTrue(pause.isPaused(1_100_000));
        // A regression must fail closed, not extend the blocking bypass.
        assertEquals(0, pause.remaining(100));
        assertFalse(pause.isPaused(101));
        // A fresh pause requires an explicit new user action.
        assertTrue(pause.pause(TimedProtectionPause.FIVE_MINUTES, 102));
        assertEquals(TimedProtectionPause.FIVE_MINUTES, pause.remaining(102));
    }

    @Test public void invalidElapsedTimeClearsExistingPause() {
        TimedProtectionPause pause = new TimedProtectionPause();
        assertTrue(pause.pause(TimedProtectionPause.FIFTEEN_MINUTES, 100));
        assertEquals(0, pause.remaining(-1));
        assertFalse(pause.isPaused(101));
    }

    @Test public void rollbackDuringNewPauseDropsOldDeadline() {
        TimedProtectionPause pause = new TimedProtectionPause();
        assertTrue(pause.pause(TimedProtectionPause.THIRTY_MINUTES, 900_000));
        assertTrue(pause.pause(TimedProtectionPause.FIVE_MINUTES, 10));
        assertEquals(TimedProtectionPause.FIVE_MINUTES, pause.remaining(10));
        assertFalse(pause.isPaused(10 + TimedProtectionPause.FIVE_MINUTES));
    }

    @Test public void repeatedQueriesObserveExpiryWithoutAlarm() {
        TimedProtectionPause pause = new TimedProtectionPause();
        assertTrue(pause.pause(TimedProtectionPause.FIFTEEN_MINUTES, 1_000_000));
        assertTrue(pause.isPaused(1_500_000));
        assertFalse(pause.isPaused(1_900_000));
        assertFalse(pause.isPaused(2_100_000));
    }
}
