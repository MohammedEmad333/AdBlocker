package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class BlocklistSourcePolicyTest {
    @Test public void tinyButValidCustomListCanBeAccepted() {
        assertEquals(10, BlocklistSourcePolicy.minimumDomains(true));
        assertEquals(1, BlocklistSourcePolicy.minimumBytes(true));
        assertTrue(BlocklistSourcePolicy.cacheUsable(170, true));
        assertFalse(BlocklistSourcePolicy.cacheUsable(0, true));
    }

    @Test public void curatedListRetainsStrongerChecks() {
        assertEquals(1000, BlocklistSourcePolicy.minimumDomains(false));
        assertEquals(1024, BlocklistSourcePolicy.minimumBytes(false));
        assertFalse(BlocklistSourcePolicy.cacheUsable(170, false));
        assertTrue(BlocklistSourcePolicy.cacheUsable(1024, false));
    }

    @Test public void customSourceOnGithubUsesCustomPolicy() {
        String custom = "https://raw.githubusercontent.com/example/repo/main/hosts";
        assertNotNull(CustomBlocklistUrl.normalize(custom));
        // Host names do not affect validation policy; the source type does.
        assertEquals(10, BlocklistSourcePolicy.minimumDomains(true));
    }

    @Test public void cacheFreshnessRespectsBoundariesAndClockRollback() {
        long now = 1_800_000_000_000L;
        long day = BlocklistSourcePolicy.MAX_CACHE_AGE_MS;
        assertTrue(BlocklistSourcePolicy.cacheFresh(4096, now - 1000, now, false));
        assertTrue(BlocklistSourcePolicy.cacheFresh(250, now - day + 1, now, true));
        assertFalse(BlocklistSourcePolicy.cacheFresh(250, now - day, now, true));
        assertFalse(BlocklistSourcePolicy.cacheFresh(250, now + 1, now, true));
        assertFalse(BlocklistSourcePolicy.cacheFresh(250, 0, now, true));
        assertFalse(BlocklistSourcePolicy.cacheFresh(0, now - 1000, now, true));
        assertFalse(BlocklistSourcePolicy.cacheFresh(17L * 1024 * 1024,
                now - 1000, now, true));
    }

    @Test public void alwaysRejectOversizedCache() {
        long huge = 16L * 1024 * 1024 + 1;
        assertFalse(BlocklistSourcePolicy.cacheUsable(huge, false));
        assertFalse(BlocklistSourcePolicy.cacheUsable(huge, true));
    }
}
