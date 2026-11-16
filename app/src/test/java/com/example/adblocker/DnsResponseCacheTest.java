package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class DnsResponseCacheTest {
    private static byte[] answer(byte[] query, long ttl) {
        return answer(query, ttl, 1);
    }

    private static byte[] answer(byte[] query, long ttl, int rrType) {
        int dataLength = rrType == 28 ? 16 : 4;
        byte[] result = new byte[query.length + 12 + dataLength];
        System.arraycopy(query, 0, result, 0, query.length);
        result[2] = (byte) 0x81;
        result[3] = (byte) 0x80;
        result[6] = 0; result[7] = 1;
        int pos = query.length;
        result[pos] = (byte) 0xc0; result[pos + 1] = 0x0c;
        result[pos + 2] = 0; result[pos + 3] = (byte) rrType;
        result[pos + 4] = 0; result[pos + 5] = 1;
        result[pos + 6] = (byte)(ttl >>> 24);
        result[pos + 7] = (byte)(ttl >>> 16);
        result[pos + 8] = (byte)(ttl >>> 8);
        result[pos + 9] = (byte)ttl;
        result[pos + 10] = 0; result[pos + 11] = (byte) dataLength;
        for (int i = 0; i < dataLength; i++) result[pos + 12 + i] = (byte)(i + 1);
        return result;
    }

    @Test public void cachesPositiveAddressAndRewritesTxIdAndTtl() {
        DnsResponseCache cache = new DnsResponseCache();
        byte[] query = DnsPacket.buildQuery("example.com", 0x1234);
        cache.put(query, answer(query, 30), 10000);
        byte[] again = DnsPacket.buildQuery("example.com", 0xabcd);
        byte[] cached = cache.get(again, 15000);
        assertNotNull(cached);
        assertEquals(0xab, cached[0] & 255);
        assertEquals(0xcd, cached[1] & 255);
        assertEquals(25, cached[again.length + 9] & 255);
        assertNull(cache.get(again, 40000));
    }

    @Test public void rejectsAddressTypeMismatchEvenWhenQuestionMatches() {
        DnsResponseCache cache = new DnsResponseCache();
        byte[] aQuery = DnsPacket.buildQuery("example.com", 0x4520);
        byte[] aaaaQuery = aQuery.clone();
        aaaaQuery[aaaaQuery.length - 3] = 28;

        // A response to an AAAA question or vice versa is not reusable.
        cache.put(aQuery, answer(aQuery, 30, 28), 1000);
        cache.put(aaaaQuery, answer(aaaaQuery, 30, 1), 1000);
        assertEquals(0, cache.size());

        // A genuine AAAA response is still cached and replayed.
        cache.put(aaaaQuery, answer(aaaaQuery, 30, 28), 1000);
        assertNotNull(cache.get(aaaaQuery, 2000));
        assertNull(cache.get(aQuery, 2000));
    }

    @Test public void rejectsNonInternetQuestionClass() {
        DnsResponseCache cache = new DnsResponseCache();
        byte[] chaosQuery = DnsPacket.buildQuery("example.com", 0x4521);
        chaosQuery[chaosQuery.length - 1] = 3;
        cache.put(chaosQuery, answer(chaosQuery, 30), 2000);
        assertEquals(0, cache.size());
    }

    @Test public void monotonicTimeRollbackDoesNotExtendCacheTtl() {
        DnsResponseCache cache = new DnsResponseCache();
        byte[] query = DnsPacket.buildQuery("example.com", 0x4522);
        cache.put(query, answer(query, 30), 10000);
        assertNull(cache.get(query, 9999));
        assertEquals(0, cache.size());
        cache.put(query, answer(query, 30), Long.MAX_VALUE - 1000);
        assertEquals(0, cache.size());
    }

    @Test public void refusesNegativeOrTruncatedResponses() {
        DnsResponseCache cache = new DnsResponseCache();
        byte[] query = DnsPacket.buildQuery("example.com", 0x3010);
        byte[] negative = DnsPacket.buildNxDomain(query, 0, query.length);
        cache.put(query, negative, 0);
        byte[] truncated = answer(query, 40);
        truncated[2] |= 2;
        cache.put(query, truncated, 0);
        assertEquals(0, cache.size());
    }

    @Test public void limitsTtlAndCanClearOnNetworkChange() {
        DnsResponseCache cache = new DnsResponseCache();
        byte[] query = DnsPacket.buildQuery("example.com", 0x3011);
        cache.put(query, answer(query, 3600), 0);
        assertNotNull(cache.get(query, 59000));
        assertNull(cache.get(query, 61000));
        cache.put(query, answer(query, 10), 0);
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test public void refusesQueriesWithEdnsOrExtraData() {
        DnsResponseCache cache = new DnsResponseCache();
        byte[] query = DnsPacket.buildQuery("example.com", 0x3012);
        byte[] withExtra = java.util.Arrays.copyOf(query, query.length + 3);
        withExtra[11] = 1;
        cache.put(withExtra, answer(query, 30), 0);
        assertEquals(0, cache.size());
    }
}
