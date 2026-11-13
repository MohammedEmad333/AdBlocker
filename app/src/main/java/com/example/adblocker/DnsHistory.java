package com.example.adblocker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Bounded, in-memory DNS event history. Never written to disk or transmitted.
 * A session restart clears the history. Newest events are returned first.
 */
public final class DnsHistory {
    public enum Filter { ALL, BLOCKED, ALLOWED }

    public static final class Entry {
        public final String domain;
        public final boolean blocked;
        public final long timeMillis;

        Entry(String domain, boolean blocked, long timeMillis) {
            this.domain = domain;
            this.blocked = blocked;
            this.timeMillis = timeMillis;
        }
    }

    /** Aggregated session-only count. Individual domains never leave RAM. */
    public static final class DomainCount {
        public final String domain;
        public final int count;

        DomainCount(String domain, int count) {
            this.domain = domain;
            this.count = count;
        }
    }

    private final int capacity;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private long version;

    public DnsHistory(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public synchronized void add(String domain, boolean blocked, long timeMillis) {
        if (domain == null || domain.isEmpty()) return;
        entries.addFirst(new Entry(domain, blocked, timeMillis));
        while (entries.size() > capacity) entries.removeLast();
        version++;
    }

    public synchronized long version() {
        return version;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
        version++;
    }

    /** Top blocked domains among only the latest capacity-bounded events. */
    public synchronized List<DomainCount> topBlocked(int limit) {
        List<DomainCount> result = new ArrayList<>();
        if (limit <= 0) return result;
        Map<String, Integer> counts = new HashMap<>();
        for (Entry entry : entries) {
            if (entry.blocked) {
                counts.put(entry.domain, counts.getOrDefault(entry.domain, 0) + 1);
            }
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            result.add(new DomainCount(e.getKey(), e.getValue()));
        }
        result.sort((a, b) -> {
            int byCount = Integer.compare(b.count, a.count);
            return byCount != 0 ? byCount : a.domain.compareTo(b.domain);
        });
        if (result.size() > limit) return new ArrayList<>(result.subList(0, limit));
        return result;
    }

    /** A stable snapshot, limited to the requested number of matching rows. */
    public synchronized List<Entry> search(String text, Filter filter, int limit) {
        List<Entry> result = new ArrayList<>();
        if (limit <= 0) return result;
        String needle = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        Filter mode = filter == null ? Filter.ALL : filter;
        for (Entry entry : entries) {
            if (mode == Filter.BLOCKED && !entry.blocked) continue;
            if (mode == Filter.ALLOWED && entry.blocked) continue;
            if (!entry.domain.toLowerCase(Locale.ROOT).contains(needle)) continue;
            result.add(entry);
            if (result.size() >= limit) break;
        }
        return result;
    }
}
