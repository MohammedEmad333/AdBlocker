package com.example.adblocker;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class DnsLogRuleEligibilityTest {
    @Test public void permitsDomainWithoutHigherPriorityParentAllow() {
        assertTrue(DnsLogRuleEligibility.canBlock("ads.example.com",
                Collections.emptySet()));
    }

    @Test public void refusesIneffectiveBlockUnderAllowedParent() {
        Set<String> allowed = new HashSet<>(Collections.singletonList("example.com"));
        assertFalse(DnsLogRuleEligibility.canBlock("ads.example.com", allowed));
    }

    @Test public void permitsReplacingExactAllowWithBlock() {
        Set<String> allowed = new HashSet<>(Collections.singletonList("ads.example.com"));
        assertTrue(DnsLogRuleEligibility.canBlock("ads.example.com", allowed));
    }

    @Test public void rejectsInvalidDomains() {
        assertFalse(DnsLogRuleEligibility.canBlock("https://example.com",
                Collections.emptySet()));
    }
}
