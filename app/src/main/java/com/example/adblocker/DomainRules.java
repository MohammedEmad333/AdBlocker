package com.example.adblocker;

import java.net.IDN;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Pure-Java filter rules, shared by persisted settings and the VPN engine. */
public final class DomainRules {
    private DomainRules() {}

    /** A hostname only: no scheme, URL, wildcard, port, path or IP literal. */
    public static String normalize(String input) {
        if (input == null) return null;
        String value = input.trim();
        if (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        if (value.length() == 0 || value.length() > 253 || value.contains("://")
                || value.contains("/") || value.contains(":")
                || value.contains("*") || value.contains("@")) return null;
        try {
            value = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (value.length() > 253 || value.indexOf('.') < 0) return null;
        String[] labels = value.split("\\.", -1);
        for (String label : labels) {
            if (label.length() < 1 || label.length() > 63
                    || label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') {
                return null;
            }
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '-') {
                    return null;
                }
            }
        }
        // A numeric IPv4 literal is not a hostname.
        if (value.matches("[0-9.]+")) return null;
        return value;
    }

    /** Match exact hostname or a subdomain; never a partial string suffix. */
    public static boolean matches(Set<String> domains, String hostname) {
        if (domains == null || domains.isEmpty() || hostname == null) return false;
        String d = hostname.toLowerCase(Locale.ROOT);
        while (true) {
            if (domains.contains(d)) return true;
            int dot = d.indexOf('.');
            if (dot < 0) return false;
            d = d.substring(dot + 1);
        }
    }

    /** Immutable snapshot prevents mixed old/new rule sets during refresh. */
    public static final class Policy {
        private final Set<String> allowed;
        private final Set<String> blocked;
        private final Set<String> downloaded;

        public Policy(Set<String> allowed, Set<String> blocked, Set<String> downloaded) {
            this.allowed = immutableCopy(allowed);
            this.blocked = immutableCopy(blocked);
            this.downloaded = immutableCopy(downloaded);
        }

        private static Set<String> immutableCopy(Set<String> input) {
            return Collections.unmodifiableSet(new HashSet<>(input));
        }

        public boolean shouldBlock(String domain) {
            return !matches(allowed, domain)
                    && (matches(blocked, domain) || matches(downloaded, domain));
        }

        public int downloadedDomainCount() {
            return downloaded.size();
        }
    }
}
