package com.example.adblocker;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Exact-domain temporary exceptions, never wildcard or subdomain exceptions. */
final class TemporaryAllowances {
    static final long FIFTEEN_MINUTES = 15L * 60 * 1000;
    static final long ONE_HOUR = 60L * 60 * 1000;
    static final int LIMIT = 100;
    private TemporaryAllowances() {}

    static Map<String, Long> parse(Set<String> values, long now) {
        Map<String, Long> result = new HashMap<>();
        if (values == null) return result;
        for (String value : values) {
            if (value == null) continue;
            int separator = value.lastIndexOf('|');
            if (separator < 0) continue;
            String domain = value.substring(0, separator);
            if (!domain.equals(DomainRules.normalize(domain))) continue;
            try {
                long until = Long.parseLong(value.substring(separator + 1));
                if (until > now && until - now <= ONE_HOUR) result.put(domain, until);
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    static Set<String> encode(Map<String, Long> rules) {
        Set<String> entries = new HashSet<>();
        for (Map.Entry<String, Long> item : rules.entrySet()) {
            entries.add(item.getKey() + "|" + item.getValue());
        }
        return entries;
    }

    static boolean allows(Map<String, Long> rules, String domain, long now) {
        Long until = rules.get(domain);
        return until != null && until > now;
    }

    static Map<String, Long> immutable(Map<String, Long> input) {
        return Collections.unmodifiableMap(new HashMap<>(input));
    }
}
