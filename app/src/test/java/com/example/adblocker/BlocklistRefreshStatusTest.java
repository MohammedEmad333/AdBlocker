package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class BlocklistRefreshStatusTest {
    @Test public void noEnabledSourcesIsNotSuccess() {
        BlocklistRefreshStatus s = new BlocklistRefreshStatus();
        assertEquals(BlocklistRefreshStatus.Outcome.NO_SOURCES, s.outcome());
        assertEquals(0, s.successfulSources());
        assertEquals(0, s.failedSources());
    }

    @Test public void allSourcesUpdated() {
        BlocklistRefreshStatus s = new BlocklistRefreshStatus();
        s.recordSuccess();
        s.recordSuccess();
        assertEquals(BlocklistRefreshStatus.Outcome.SUCCESS, s.outcome());
        assertEquals(2, s.successfulSources());
    }

    @Test public void cachedFallbackIsNotSuccessfulDownload() {
        BlocklistRefreshStatus s = new BlocklistRefreshStatus();
        s.recordFailure();
        assertEquals(BlocklistRefreshStatus.Outcome.FAILED, s.outcome());
        assertEquals(1, s.failedSources());
    }

    @Test public void partialFailureReportedSeparately() {
        BlocklistRefreshStatus s = new BlocklistRefreshStatus();
        s.recordSuccess();
        s.recordFailure();
        assertEquals(BlocklistRefreshStatus.Outcome.PARTIAL, s.outcome());
        assertEquals(1, s.successfulSources());
        assertEquals(1, s.failedSources());
    }
}
