package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class DnsHealthSummaryTest {
    @Test public void identifiesStoppedAndStarting() {
        assertEquals(DnsHealthSummary.State.STOPPED,
                DnsHealthSummary.assess(false, false, 0, 0, 0));
        assertEquals(DnsHealthSummary.State.STARTING,
                DnsHealthSummary.assess(false, true, 0, 0, 0));
    }
    @Test public void noQueriesIsIdleNotVerifiedHealthy() {
        assertEquals(DnsHealthSummary.State.IDLE,
                DnsHealthSummary.assess(true, false, 0, 0, 0));
    }
    @Test public void errorsDoNotCountAsSuccessfulResponses() {
        assertEquals(DnsHealthSummary.State.DEGRADED,
                DnsHealthSummary.assess(true, false, 0, 3, 0));
        assertEquals(DnsHealthSummary.State.DEGRADED,
                DnsHealthSummary.assess(true, false, 2, 1, 1));
    }
    @Test public void healthyRequiresGoodSessionResponses() {
        assertEquals(DnsHealthSummary.State.HEALTHY,
                DnsHealthSummary.assess(true, false, 100, 1, 2));
    }
}
