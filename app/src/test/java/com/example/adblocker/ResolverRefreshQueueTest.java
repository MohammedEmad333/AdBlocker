package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class ResolverRefreshQueueTest {
    @Test public void startsOneWorkerForOverlappingNetworkEvents() {
        ResolverRefreshQueue<String> q = new ResolverRefreshQueue<>();
        assertTrue(q.request("wifi"));
        assertFalse(q.request("mobile"));
        assertEquals("mobile", q.next().preferred);
        assertFalse(q.hasPending());
        assertNull(q.next());
        assertTrue(q.request("wifi"));
        assertEquals("wifi", q.next().preferred);
        assertNull(q.next());
    }

    @Test public void replaysNetworkChangeAfterSlowProbe() {
        ResolverRefreshQueue<String> q = new ResolverRefreshQueue<>();
        assertTrue(q.request("wifi"));
        assertEquals("wifi", q.next().preferred);
        assertFalse(q.request("mobile"));
        assertTrue(q.hasPending());
        assertEquals("mobile", q.next().preferred);
        assertNull(q.next());
    }

    @Test public void latestNetworkWinsAcrossMultipleEvents() {
        ResolverRefreshQueue<String> q = new ResolverRefreshQueue<>();
        assertTrue(q.request("wifi"));
        assertEquals("wifi", q.next().preferred);
        assertFalse(q.request("mobile"));
        assertFalse(q.request("wifi-2"));
        assertEquals("wifi-2", q.next().preferred);
        assertNull(q.next());
    }

    @Test public void nullRequestTriggersFreshNetworkSelection() {
        ResolverRefreshQueue<String> q = new ResolverRefreshQueue<>();
        assertTrue(q.request("wifi"));
        assertEquals("wifi", q.next().preferred);
        assertFalse(q.request(null));
        assertTrue(q.hasPending());
        assertNull(q.next().preferred);
        assertNull(q.next());
    }

    @Test public void newNetworkOverridesPreviousNullRecheck() {
        ResolverRefreshQueue<String> q = new ResolverRefreshQueue<>();
        assertTrue(q.request(null));
        assertFalse(q.request("mobile"));
        assertEquals("mobile", q.next().preferred);
        assertNull(q.next());
    }

    @Test public void resetAllowsNewSessionToStartWorker() {
        ResolverRefreshQueue<String> q = new ResolverRefreshQueue<>();
        assertTrue(q.request("wifi"));
        q.reset();
        assertFalse(q.hasPending());
        assertTrue(q.request("mobile"));
        assertEquals("mobile", q.next().preferred);
        assertNull(q.next());
    }
}
