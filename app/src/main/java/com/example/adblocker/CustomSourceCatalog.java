package com.example.adblocker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Pure-Java catalog for at most three independently enabled HTTPS lists. */
final class CustomSourceCatalog {
    static final int LIMIT = 3;

    private CustomSourceCatalog() {}

    static LinkedHashMap<String, Boolean> decode(Set<String> entries, String legacyUrl) {
        LinkedHashMap<String, Boolean> result = new LinkedHashMap<>();
        List<String> sorted = new ArrayList<>(entries == null
                ? Collections.emptySet() : entries);
        Collections.sort(sorted);
        for (String row : sorted) {
            if (row == null || row.length() < 3 || row.charAt(1) != '|') continue;
            char flag = row.charAt(0);
            if (flag != '0' && flag != '1') continue;
            String url = row.substring(2);
            if (!url.equals(CustomBlocklistUrl.normalize(url))) continue;
            if (!result.containsKey(url) && result.size() >= LIMIT) continue;
            // If old corrupted preferences have both states, enabled wins.
            result.put(url, flag == '1' || Boolean.TRUE.equals(result.get(url)));
        }
        String normalized = CustomBlocklistUrl.normalize(legacyUrl);
        if (normalized != null && !result.containsKey(normalized) && result.size() < LIMIT) {
            result.put(normalized, true);
        }
        return result;
    }

    static LinkedHashMap<String, Boolean> add(Map<String, Boolean> existing, String input) {
        String url = CustomBlocklistUrl.normalize(input);
        if (url == null) throw new IllegalArgumentException("Invalid HTTPS URL");
        LinkedHashMap<String, Boolean> copy = new LinkedHashMap<>(existing);
        if (!copy.containsKey(url) && copy.size() >= LIMIT)
            throw new IllegalArgumentException("At most three custom sources");
        copy.put(url, true);
        return copy;
    }

    static LinkedHashMap<String, Boolean> withEnabled(
            Map<String, Boolean> existing, String url, boolean enabled) {
        if (!existing.containsKey(url)) throw new IllegalArgumentException("Unknown source");
        LinkedHashMap<String, Boolean> copy = new LinkedHashMap<>(existing);
        copy.put(url, enabled);
        return copy;
    }

    static Set<String> encode(Map<String, Boolean> sources) {
        if (sources.size() > LIMIT) throw new IllegalArgumentException("Too many sources");
        Set<String> result = new HashSet<>();
        for (Map.Entry<String, Boolean> entry : sources.entrySet()) {
            String url = entry.getKey();
            if (!url.equals(CustomBlocklistUrl.normalize(url)))
                throw new IllegalArgumentException("Invalid URL");
            result.add((Boolean.TRUE.equals(entry.getValue()) ? "1|" : "0|") + url);
        }
        return result;
    }
}
