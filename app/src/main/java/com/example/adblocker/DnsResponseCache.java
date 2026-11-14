package com.example.adblocker;

import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deliberately narrow in-RAM cache for one positive A/AAAA DNS answer.
 * No NXDOMAIN, SERVFAIL, EDNS, DNSSEC, multi-record or TCP responses.
 */
final class DnsResponseCache {
    static final int LIMIT = 128;
    static final long MAX_TTL_SEC = 60L;
    private static final class Entry {
        final byte[] response;
        final long expiresAt;
        final int ttlOffset;
        Entry(byte[] response, long expiresAt, int ttlOffset) {
            this.response = response;
            this.expiresAt = expiresAt;
            this.ttlOffset = ttlOffset;
        }
    }

    private final LinkedHashMap<String, Entry> entries =
            new LinkedHashMap<String, Entry>(LIMIT + 1, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                    return size() > LIMIT;
                }
            };

    private static String key(byte[] query) {
        if (query == null || query.length < 17 || query.length > 4096) return null;
        int end = DnsPacket.questionEnd(query, 0, query.length);
        // No EDNS or any additional query data in this conservative cache.
        if (end < 0 || end != query.length
                || query[10] != 0 || query[11] != 0) return null;
        return Base64.getEncoder().encodeToString(
                Arrays.copyOfRange(query, 2, query.length));
    }

    synchronized byte[] get(byte[] query, long now) {
        String key = key(query);
        if (key == null) return null;
        Entry entry = entries.get(key);
        if (entry == null) return null;
        long remaining = entry.expiresAt - now;
        if (remaining < 1000) {
            entries.remove(key);
            return null;
        }
        byte[] result = entry.response.clone();
        result[0] = query[0];
        result[1] = query[1];
        write32(result, entry.ttlOffset, remaining / 1000);
        return result;
    }

    synchronized void put(byte[] query, byte[] reply, long now) {
        String key = key(query);
        if (key == null || reply == null || !DnsTcpWire.matchesQuery(query, reply)
                || (reply[2] & 2) != 0 || (reply[3] & 15) != 0
                || (reply[6] & 255) != 0 || (reply[7] & 255) != 1
                || reply[8] != 0 || reply[9] != 0
                || reply[10] != 0 || reply[11] != 0) return;

        int end = DnsPacket.questionEnd(reply, 0, reply.length);
        if (end != query.length || !Arrays.equals(
                Arrays.copyOfRange(query, 2, end),
                Arrays.copyOfRange(reply, 2, end))) return;

        // Support one standard compressed answer pointing at the question.
        if (reply.length < end + 16 || (reply[end] & 255) != 0xc0
                || (reply[end + 1] & 255) != 0x0c) return;
        int type = ((reply[end + 2] & 255) << 8) | (reply[end + 3] & 255);
        int clazz = ((reply[end + 4] & 255) << 8) | (reply[end + 5] & 255);
        int length = ((reply[end + 10] & 255) << 8) | (reply[end + 11] & 255);
        if (clazz != 1 || !((type == 1 && length == 4)
                || (type == 28 && length == 16))
                || reply.length != end + 12 + length) return;
        long ttl = read32(reply, end + 6);
        if (ttl <= 0) return;
        ttl = Math.min(ttl, MAX_TTL_SEC);
        entries.put(key, new Entry(reply.clone(), now + ttl * 1000, end + 6));
    }

    synchronized void clear() { entries.clear(); }

    synchronized int size() { return entries.size(); }

    private static long read32(byte[] b, int at) {
        return ((b[at] & 255L) << 24) | ((b[at + 1] & 255L) << 16)
                | ((b[at + 2] & 255L) << 8) | (b[at + 3] & 255L);
    }
    private static void write32(byte[] b, int at, long value) {
        b[at] = (byte) (value >>> 24);
        b[at + 1] = (byte) (value >>> 16);
        b[at + 2] = (byte) (value >>> 8);
        b[at + 3] = (byte) value;
    }
}
