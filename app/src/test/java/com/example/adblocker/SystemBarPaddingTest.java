package com.example.adblocker;

import org.junit.Test;

import static org.junit.Assert.*;

public class SystemBarPaddingTest {
    @Test public void preservesLayoutPaddingWhileAvoidingBothSystemBars() {
        assertArrayEquals(new int[]{22, 50, 22, 64},
                SystemBarPadding.withInsets(new int[]{22, 26, 22, 36},
                        new int[]{0, 24, 0, 28}));
    }

    @Test public void insetUpdatesUseOriginalPaddingNotAccumulatedPadding() {
        int[] original = {20, 18, 20, 0};
        assertArrayEquals(new int[]{20, 42, 20, 24},
                SystemBarPadding.withInsets(original, new int[]{0, 24, 0, 24}));
        assertArrayEquals(new int[]{20, 18, 20, 32},
                SystemBarPadding.withInsets(original, new int[]{0, 0, 0, 32}));
        assertArrayEquals(new int[]{20, 18, 20, 0}, original);
    }

    @Test public void cutoutInsetsProtectLandscapeContent() {
        assertArrayEquals(new int[]{68, 18, 20, 24},
                SystemBarPadding.withInsets(new int[]{20, 18, 20, 0},
                        new int[]{48, 0, 0, 24}));
    }

    @Test public void zeroOrNegativeInsetsCannotRemoveExistingPadding() {
        assertArrayEquals(new int[]{12, 8, 12, 8},
                SystemBarPadding.withInsets(new int[]{12, 8, 12, 8},
                        new int[]{-10, 0, -5, 0}));
    }

    @Test public void malformedInputIsRejected() {
        try {
            SystemBarPadding.withInsets(new int[]{1}, new int[]{0, 0, 0, 0});
            fail("Invalid padding length accepted");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
