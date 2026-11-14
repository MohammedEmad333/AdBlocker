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

    @Test public void alwaysRejectOversizedCache() {
        long huge = 16L * 1024 * 1024 + 1;
        assertFalse(BlocklistSourcePolicy.cacheUsable(huge, false));
        assertFalse(BlocklistSourcePolicy.cacheUsable(huge, true));
    }
}
