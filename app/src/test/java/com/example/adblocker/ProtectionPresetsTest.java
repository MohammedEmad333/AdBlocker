package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProtectionPresetsTest {
    @Test public void balancedActivatesOnlyDefaultList() {
        assertTrue(ProtectionPresets.sourceEnabled(ProtectionPresets.Mode.BALANCED,
                FilterSettings.STEVENBLACK));
        assertFalse(ProtectionPresets.sourceEnabled(ProtectionPresets.Mode.BALANCED,
                FilterSettings.ADAWAY));
    }

    @Test public void strongerActivatesBothSources() {
        assertTrue(ProtectionPresets.sourceEnabled(ProtectionPresets.Mode.STRONG,
                FilterSettings.STEVENBLACK));
        assertTrue(ProtectionPresets.sourceEnabled(ProtectionPresets.Mode.STRONG,
                FilterSettings.ADAWAY));
    }

    @Test public void identifiesCustomSourceSelections() {
        assertEquals(ProtectionPresets.Mode.BALANCED, ProtectionPresets.current(true, false));
        assertEquals(ProtectionPresets.Mode.STRONG, ProtectionPresets.current(true, true));
        assertEquals(ProtectionPresets.Mode.CUSTOM, ProtectionPresets.current(false, true));
        assertEquals(ProtectionPresets.Mode.CUSTOM, ProtectionPresets.current(false, false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void neverOverwriteCustomModeAutomatically() {
        ProtectionPresets.sourceEnabled(ProtectionPresets.Mode.CUSTOM, FilterSettings.STEVENBLACK);
    }
}
