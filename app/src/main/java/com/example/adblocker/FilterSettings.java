package com.example.adblocker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Settings stored only on-device. Always copy SharedPreferences string sets. */
public final class FilterSettings {
    public static final String STEVENBLACK = "stevenblack";
    public static final String ADAWAY = "adaway";

    private static final String PREFS = "filter_settings";
    private static final String ALLOW = "allow_domains";
    private static final String BLOCK = "block_domains";
    private static final String EXCLUDED = "excluded_packages";
    private static final String LAST_REFRESH = "last_successful_download";

    private final SharedPreferences prefs;

    public FilterSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Set<String> allowedDomains() {
        return copy(ALLOW);
    }

    public Set<String> blockedDomains() {
        return copy(BLOCK);
    }

    public Set<String> excludedPackages() {
        return copy(EXCLUDED);
    }

    private Set<String> copy(String key) {
        return new HashSet<>(prefs.getStringSet(key, Collections.emptySet()));
    }

    public boolean sourceEnabled(String id) {
        if (STEVENBLACK.equals(id)) return prefs.getBoolean(id, true);
        if (ADAWAY.equals(id)) return prefs.getBoolean(id, false);
        return false;
    }

    public void setSourceEnabled(String id, boolean enabled) {
        if (!STEVENBLACK.equals(id) && !ADAWAY.equals(id))
            throw new IllegalArgumentException("Unknown source");
        prefs.edit().putBoolean(id, enabled).apply();
    }

    /**
     * Allow rules outrank all block rules. Adding the opposite kind of rule
     * also removes the prior rule, avoiding a contradictory custom entry.
     */
    public boolean addDomain(boolean allow, String input) {
        String value = DomainRules.normalize(input);
        if (value == null) return false;
        String current = allow ? ALLOW : BLOCK;
        String opposite = allow ? BLOCK : ALLOW;
        Set<String> updated = copy(current);
        Set<String> other = copy(opposite);
        updated.add(value);
        other.remove(value);
        prefs.edit().putStringSet(current, updated).putStringSet(opposite, other).apply();
        return true;
    }

    public void removeDomain(boolean allow, String domain) {
        String normalized = DomainRules.normalize(domain);
        if (normalized == null) return;
        String key = allow ? ALLOW : BLOCK;
        Set<String> updated = copy(key);
        if (updated.remove(normalized)) prefs.edit().putStringSet(key, updated).apply();
    }

    public void setExcluded(String packageName, boolean excluded) {
        if (packageName == null || packageName.length() == 0) return;
        Set<String> packages = copy(EXCLUDED);
        if (excluded) packages.add(packageName);
        else packages.remove(packageName);
        prefs.edit().putStringSet(EXCLUDED, packages).apply();
    }

    public void markDownloaded() {
        prefs.edit().putLong(LAST_REFRESH, System.currentTimeMillis()).apply();
    }

    public long lastDownloadedAt() {
        return prefs.getLong(LAST_REFRESH, 0);
    }
}
