package com.example.adblocker;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.Test;
import static org.junit.Assert.*;

public class RuleMergePlannerTest {
    @Test public void addsNonconflictingRulesWithoutLosingExisting() {
        RuleBackupCodec.Rules incoming = new RuleBackupCodec.Rules(
                new HashSet<>(Arrays.asList("existing.example", "new.example")),
                Collections.singleton("block.example"));
        RuleMergePlanner.Plan plan = RuleMergePlanner.merge(
                Collections.singleton("existing.example"), Collections.emptySet(), incoming);
        assertEquals(1, plan.addedAllowed);
        assertEquals(1, plan.addedBlocked);
        assertEquals(0, plan.conflicts);
        assertTrue(plan.result.allowed.contains("existing.example"));
        assertTrue(plan.result.allowed.contains("new.example"));
        assertTrue(plan.result.blocked.contains("block.example"));
    }

    @Test public void existingExactDomainRulesWinConflicts() {
        RuleBackupCodec.Rules incoming = new RuleBackupCodec.Rules(
                Collections.singleton("blocked.example"),
                Collections.singleton("allowed.example"));
        RuleMergePlanner.Plan plan = RuleMergePlanner.merge(
                Collections.singleton("allowed.example"),
                Collections.singleton("blocked.example"), incoming);
        assertEquals(2, plan.conflicts);
        assertEquals(0, plan.addedAllowed + plan.addedBlocked);
        assertTrue(plan.result.allowed.contains("allowed.example"));
        assertTrue(plan.result.blocked.contains("blocked.example"));
    }

    @Test public void mergeIsIdempotentForSameInput() {
        RuleBackupCodec.Rules incoming = new RuleBackupCodec.Rules(
                Collections.singleton("new.example"), Collections.emptySet());
        RuleMergePlanner.Plan first = RuleMergePlanner.merge(
                Collections.emptySet(), Collections.emptySet(), incoming);
        RuleMergePlanner.Plan second = RuleMergePlanner.merge(
                first.result.allowed, first.result.blocked, incoming);
        assertEquals(0, second.addedAllowed + second.addedBlocked);
        assertEquals(0, second.conflicts);
        assertEquals(first.result.allowed, second.result.allowed);
    }

    @Test public void refusesOversizedCombinedSetBeforePersisting() {
        HashSet<String> current = new HashSet<>();
        for (int i = 0; i < RuleBackupCodec.MAX_RULES; i++)
            current.add("host" + i + ".example.com");
        RuleBackupCodec.Rules incoming = new RuleBackupCodec.Rules(
                Collections.singleton("new.example"), Collections.emptySet());
        try {
            RuleMergePlanner.merge(current, Collections.emptySet(), incoming);
            fail("Exceeded max rule count");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("maximum"));
        }
    }
}
