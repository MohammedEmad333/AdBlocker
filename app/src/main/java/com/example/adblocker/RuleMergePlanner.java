package com.example.adblocker;

import java.util.HashSet;
import java.util.Set;

/**
 * Pure Java import planner. Existing exact-domain rules win conflicts.
 * No changes are persisted until the user confirms the preview.
 */
final class RuleMergePlanner {
    static final class Plan {
        final RuleBackupCodec.Rules result;
        final int addedAllowed;
        final int addedBlocked;
        final int conflicts;
        Plan(RuleBackupCodec.Rules result, int addedAllowed,
                int addedBlocked, int conflicts) {
            this.result = result;
            this.addedAllowed = addedAllowed;
            this.addedBlocked = addedBlocked;
            this.conflicts = conflicts;
        }
    }

    private RuleMergePlanner() {}

    static Plan merge(Set<String> existingAllowed, Set<String> existingBlocked,
            RuleBackupCodec.Rules imported) {
        if (existingAllowed == null || existingBlocked == null || imported == null)
            throw new IllegalArgumentException("Missing import rules");
        Set<String> allowed = new HashSet<>(existingAllowed);
        Set<String> blocked = new HashSet<>(existingBlocked);
        int addedAllowed = 0;
        int addedBlocked = 0;
        int conflicts = 0;

        for (String domain : imported.allowed) {
            if (blocked.contains(domain)) {
                conflicts++;
            } else if (allowed.add(domain)) {
                addedAllowed++;
            }
        }
        for (String domain : imported.blocked) {
            if (allowed.contains(domain)) {
                conflicts++;
            } else if (blocked.add(domain)) {
                addedBlocked++;
            }
        }
        if ((long) allowed.size() + blocked.size() > RuleBackupCodec.MAX_RULES)
            throw new IllegalArgumentException("Merged rules exceed maximum");
        return new Plan(new RuleBackupCodec.Rules(allowed, blocked),
                addedAllowed, addedBlocked, conflicts);
    }
}
