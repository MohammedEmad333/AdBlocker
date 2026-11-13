package com.example.adblocker;

/**
 * User-selected source combinations. Custom domain rules and per-app
 * exclusions are never modified by presets.
 */
final class ProtectionPresets {
    enum Mode { BALANCED, STRONG, CUSTOM }

    private ProtectionPresets() {}

    static Mode current(boolean stevenBlack, boolean adAway) {
        if (stevenBlack && adAway) return Mode.STRONG;
        if (stevenBlack) return Mode.BALANCED;
        return Mode.CUSTOM;
    }

    static boolean sourceEnabled(Mode mode, String source) {
        if (mode == Mode.CUSTOM) throw new IllegalArgumentException("Custom is manual");
        if (FilterSettings.STEVENBLACK.equals(source)) return true;
        if (FilterSettings.ADAWAY.equals(source)) return mode == Mode.STRONG;
        throw new IllegalArgumentException("Unknown source");
    }
}
