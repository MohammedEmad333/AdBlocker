package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class TemporaryDomainAllowlistTest {
    @Test public void expiresExactlyAtFifteenMinutes() {
        TemporaryDomainAllowlist list = new TemporaryDomainAllowlist();
        assertTrue(list.grant(" Ads.Example.com ", TemporaryDomainAllowlist.FIFTEEN_MINUTES, 100));
        assertTrue(list.isAllowed("ads.example.com", 100));
        assertTrue(list.isAllowed("ads.example.com", 100 + TemporaryDomainAllowlist.FIFTEEN_MINUTES - 1));
        assertFalse(list.isAllowed("ads.example.com", 100 + TemporaryDomainAllowlist.FIFTEEN_MINUTES));
        assertEquals(0, list.activeCount(100 + TemporaryDomainAllowlist.FIFTEEN_MINUTES));
    }

    @Test public void oneHourRenewalReplacesPreviousExpiry() {
        TemporaryDomainAllowlist list = new TemporaryDomainAllowlist();
        assertTrue(list.grant("ads.example.com", TemporaryDomainAllowlist.FIFTEEN_MINUTES, 100));
        assertTrue(list.grant("ads.example.com", TemporaryDomainAllowlist.ONE_HOUR, 500));
        assertTrue(list.isAllowed("ads.example.com", 500 + TemporaryDomainAllowlist.ONE_HOUR - 1));
        assertFalse(list.isAllowed("ads.example.com", 500 + TemporaryDomainAllowlist.ONE_HOUR));
    }

    @Test public void exactDomainDoesNotAllowParentOrSubdomain() {
        TemporaryDomainAllowlist list = new TemporaryDomainAllowlist();
        assertTrue(list.grant("ads.example.com", TemporaryDomainAllowlist.FIFTEEN_MINUTES, 0));
        assertFalse(list.isAllowed("example.com", 1));
        assertFalse(list.isAllowed("pixel.ads.example.com", 1));
        assertFalse(list.isAllowed("notads.example.com", 1));
    }

    @Test public void refusesInvalidOrUnboundedDurations() {
        TemporaryDomainAllowlist list = new TemporaryDomainAllowlist();
        assertFalse(list.grant("https://example.com", TemporaryDomainAllowlist.ONE_HOUR, 0));
        assertFalse(list.grant("example.com", -1, 0));
        assertFalse(list.grant("example.com", 7 * TemporaryDomainAllowlist.ONE_HOUR, 0));
        assertFalse(list.grant("example.com", TemporaryDomainAllowlist.ONE_HOUR, Long.MAX_VALUE));
        assertEquals(0, list.activeCount(0));
    }

    @Test public void limitsMemoryAndClearsOnSessionReset() {
        TemporaryDomainAllowlist list = new TemporaryDomainAllowlist();
        for (int i = 0; i < TemporaryDomainAllowlist.MAX_ENTRIES; i++) {
            assertTrue(list.grant("ads" + i + ".example.com",
                    TemporaryDomainAllowlist.ONE_HOUR, 0));
        }
        assertFalse(list.grant("overflow.example.com", TemporaryDomainAllowlist.ONE_HOUR, 0));
        assertEquals(TemporaryDomainAllowlist.MAX_ENTRIES, list.activeCount(1));
        list.clear();
        assertEquals(0, list.activeCount(1));
        assertTrue(list.grant("overflow.example.com", TemporaryDomainAllowlist.ONE_HOUR, 1));
    }
}
