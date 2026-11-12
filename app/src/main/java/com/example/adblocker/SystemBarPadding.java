package com.example.adblocker;

/** Pure inset arithmetic; indexes are left, top, right, bottom. */
final class SystemBarPadding {
    private SystemBarPadding() {}

    static int[] withInsets(int[] original, int[] bars) {
        if (original == null || bars == null
                || original.length != 4 || bars.length != 4) {
            throw new IllegalArgumentException("Expected four padding and inset values");
        }
        int[] result = new int[4];
        for (int i = 0; i < result.length; i++) {
            result[i] = original[i] + Math.max(0, bars[i]);
        }
        return result;
    }
}
