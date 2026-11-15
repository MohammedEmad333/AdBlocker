package com.example.adblocker;

import java.util.Locale;

/** Case-insensitive, offline app-name or package-ID matching. */
final class AppExclusionSearch {
    private AppExclusionSearch() {}

    static boolean matches(String label, String packageName, String rawSearch) {
        String needle = rawSearch == null ? "" : rawSearch.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return true;
        return (label != null && label.toLowerCase(Locale.ROOT).contains(needle))
                || (packageName != null
                    && packageName.toLowerCase(Locale.ROOT).contains(needle));
    }
}
