package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class DnsResponseCacheTest {
    private static byte[] answer(byte[] query, long ttl) {
        byte[] result = new byte[query.length + 16];
        System.arraycopy(query, 0, result, 0, query.length);
        result[2] = (byte) 0x81;
        result[3] = (byte) 0x80;
        result[6] = 0; result[7] = 1;
        int pos = query.length;
        result[pos] = (byte) 0xc0; result[pos + 1] = 0x0c;
        result[pos + 2] = 0; result[pos + 3] = 1;
        result[pos + 4] = 0; result[pos + 5] = 1;
        result[pos + 6] = (byte)(ttl >>> 24);
        result[pos + 7] = (byte)(ttl >>> 16);
        result[pos + 8] = (byte)(ttl >>> 8);
        result[pos + 9] = (byte)ttl;
        result[pos + 10] = 0; result[pos + 11] = 4;
        result[pos + 12] = 1; result[pos + 13] = 2;
        result[pos + 14] = 3; result[pos + 15] = 4;
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
