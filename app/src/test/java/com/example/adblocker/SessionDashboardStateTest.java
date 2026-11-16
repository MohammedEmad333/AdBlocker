package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class SessionDashboardStateTest {
    @Test public void privacyOptOutShowsDisabledStateNotEmptyHistory() {
        assertFalse(SessionDashboardState.recentHistoryAvailable(false, 32));
        assertFalse(SessionDashboardState.recentHistoryAvailable(true, 0));
        assertTrue(SessionDashboardState.recentHistoryAvailable(true, 1));
    }

    @Test public void updateFreshnessHandlesClockChangeAndBoundary() {
        long now = 1_800_000_000_000L;
        long day = BlocklistSourcePolicy.MAX_CACHE_AGE_MS;
        assertTrue(SessionDashboardState.needsSourceUpdate(0, now));
        assertTrue(SessionDashboardState.needsSourceUpdate(now + 1, now));
        assertTrue(SessionDashboardState.needsSourceUpdate(now - day, now));
        assertFalse(SessionDashboardState.needsSourceUpdate(now - day + 1, now));
    }

    @Test public void percentagesAreClampedAndHandleNoQueries() {
        assertEquals(0, SessionDashboardState.blockPercentage(0, 0));
        assertEquals(0, SessionDashboardState.blockPercentage(-10, 100));
        assertEquals(35, SessionDashboardState.blockPercentage(35, 100));
        assertEquals(100, SessionDashboardState.blockPercentage(200, 100));
    }
}
