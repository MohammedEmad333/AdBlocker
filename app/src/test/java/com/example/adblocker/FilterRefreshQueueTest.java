package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class FilterRefreshQueueTest {
    @Test public void startsOneWorkerAndReleasesOwnershipWhenIdle() {
        FilterRefreshQueue q = new FilterRefreshQueue();
        assertTrue(q.request(false));
        assertFalse(q.request(false));
        assertEquals(Boolean.FALSE, q.next());
        assertEquals(Boolean.FALSE, q.next()); // pending coalesced while running
        assertNull(q.next());
        assertTrue(q.request(true));
        assertEquals(Boolean.TRUE, q.next());
        assertNull(q.next());
    }

    @Test public void concurrentChangesCoalesceAndPreserveForcedRefresh() {
        FilterRefreshQueue q = new FilterRefreshQueue();
        assertTrue(q.request(false));
        assertEquals(Boolean.FALSE, q.next()); // background reload in progress
        assertFalse(q.request(false));
        assertFalse(q.request(true));
        assertFalse(q.request(false));
        assertEquals(Boolean.TRUE, q.next());
        assertNull(q.next());
    }

    @Test public void newChangeAfterWorkerDrainsStartsNewWorker() {
        FilterRefreshQueue q = new FilterRefreshQueue();
        assertTrue(q.request(false));
        assertEquals(Boolean.FALSE, q.next());
        assertNull(q.next());
        assertTrue(q.request(false));
        assertEquals(Boolean.FALSE, q.next());
        assertNull(q.next());
    }

    @Test public void resetAllowsRetryAfterSchedulerShutdown() {
        FilterRefreshQueue q = new FilterRefreshQueue();
        assertTrue(q.request(true));
        q.reset();
        assertTrue(q.request(false));
        assertEquals(Boolean.FALSE, q.next());
        assertNull(q.next());
    }
}
