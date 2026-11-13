package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class ManualRefreshStateTest {
    @Test public void mapsAllManualDownloadOutcomes() {
        for (BlocklistRefreshStatus.Outcome outcome : BlocklistRefreshStatus.Outcome.values()) {
            FilterSettings.ManualRefreshState state =
                    FilterSettings.ManualRefreshState.completed(outcome);
            assertEquals(outcome.name(), state.name());
        }
    }

    @Test public void missingOrInvalidPersistedStateFallsBackToNever() {
        assertEquals(FilterSettings.ManualRefreshState.NEVER,
                FilterSettings.ManualRefreshState.parse(null));
        assertEquals(FilterSettings.ManualRefreshState.NEVER,
                FilterSettings.ManualRefreshState.parse("UNRECOGNIZED"));
    }

    @Test public void savedRunningStateCanBeRestored() {
        assertEquals(FilterSettings.ManualRefreshState.RUNNING,
                FilterSettings.ManualRefreshState.parse("RUNNING"));
        assertEquals(FilterSettings.ManualRefreshState.FAILED,
                FilterSettings.ManualRefreshState.completed(null));
    }
}
