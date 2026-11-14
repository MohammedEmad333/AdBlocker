package com.example.adblocker;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import static org.junit.Assert.*;

public class TemporaryAllowancesTest {
    @Test public void expiredRulesAreNotAccepted() {
        Map<String, Long> result = TemporaryAllowances.parse(
                new HashSet<>(Arrays.asList("ads.example|1000", "fresh.example|60000")), 10000);
        assertFalse(result.containsKey("ads.example"));
        assertTrue(result.containsKey("fresh.example"));
    }
    @Test public void permitsExactDomainOnly() {
        Map<String, Long> rules = Collections.singletonMap("ads.example", 60000L);
        assertTrue(TemporaryAllowances.allows(rules, "ads.example", 10000));
        assertFalse(TemporaryAllowances.allows(rules, "sub.ads.example", 10000));
        assertFalse(TemporaryAllowances.allows(rules, "ads.example", 60000));
    }
    @Test public void ignoresMalformedAndImplausiblyLongAllowances() {
        Map<String, Long> result = TemporaryAllowances.parse(
                new HashSet<>(Arrays.asList("bad line", "example.com|NOPE",
                    "example.com|999999999999", "http://bad.example|1000")), 100);
        assertTrue(result.isEmpty());
    }
    @Test public void roundTripsValidExceptions() {
        Map<String, Long> initial = Collections.singletonMap("a.example", 60000L);
        assertEquals(initial, TemporaryAllowances.parse(TemporaryAllowances.encode(initial), 10000));
    }
}
