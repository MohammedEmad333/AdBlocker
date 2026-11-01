package com.example.adblocker;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class DomainRulesTest {
    @Test public void normalizesDomains() {
        assertEquals("ads.example.com", DomainRules.normalize(" Ads.Example.COM. "));
        assertEquals("xn--bcher-kva.example", DomainRules.normalize("bücher.example"));
    }

    @Test public void rejectsUrlsWildcardsAndBrokenNames() {
        for (String s : Arrays.asList("https://example.com", "*.example.com", "example.com/path",
                "127.0.0.1", "localhost", "one..example", "-bad.example",
                "example.com:443", "foo_bar.com", "a@b.com")) {
            assertNull(s, DomainRules.normalize(s));
        }
    }

    @Test public void respectsSubdomainBoundaries() {
        Set<String> blocked = new HashSet<>(Collections.singletonList("ads.example.com"));
        assertTrue(DomainRules.matches(blocked, "ads.example.com"));
        assertTrue(DomainRules.matches(blocked, "pixel.ads.example.com"));
        assertFalse(DomainRules.matches(blocked, "notads.example.com"));
        assertFalse(DomainRules.matches(blocked, "ads.example.com.attacker.test"));
    }

    @Test public void allowlistOverridesSourcesAndCustomBlocks() {
        Set<String> allowed = new HashSet<>(Collections.singletonList("example.com"));
        Set<String> custom = new HashSet<>(Collections.singletonList("ads.example.com"));
        Set<String> remote = new HashSet<>(Collections.singletonList("tracker.example.net"));
        DomainRules.Policy policy = new DomainRules.Policy(allowed, custom, remote);
        assertFalse(policy.shouldBlock("ads.example.com"));
        assertTrue(policy.shouldBlock("tracker.example.net"));
        assertTrue(policy.shouldBlock("x.tracker.example.net"));
        assertFalse(policy.shouldBlock("example.net"));
    }

    @Test public void snapshotDoesNotChangeWithMutableInput() {
        Set<String> custom = new HashSet<>(Collections.singletonList("ads.example.org"));
        DomainRules.Policy policy = new DomainRules.Policy(Collections.emptySet(),
                custom, Collections.emptySet());
        custom.clear();
        assertTrue(policy.shouldBlock("ads.example.org"));
    }
}
