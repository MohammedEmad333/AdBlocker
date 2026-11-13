package com.example.adblocker;

import java.util.Set;

/**
 * Prevent a one-tap block from promising an effect that a broader allow rule
 * would override. A direct allow for this exact domain may be replaced.
 */
final class DnsLogRuleEligibility {
    private DnsLogRuleEligibility() {}

    static boolean canBlock(String input, Set<String> allowed) {
        String domain = DomainRules.normalize(input);
        return domain != null
                && (allowed == null || !DomainRules.matches(allowed, domain)
                        || allowed.contains(domain));
    }
}
