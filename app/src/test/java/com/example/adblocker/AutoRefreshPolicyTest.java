package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class AutoRefreshPolicyTest {
    @Test public void dailyIsDefaultAndUsesOneDay() {
        assertEquals(AutoRefreshPolicy.DAILY, AutoRefreshPolicy.normalize(null));
        assertEquals(AutoRefreshPolicy.DAILY, AutoRefreshPolicy.normalize("invalid"));
        assertEquals(24L * 60 * 60 * 1000, AutoRefreshPolicy.intervalMs(null));
    }

    @Test public void weeklyUsesSevenDays() {
        assertEquals(AutoRefreshPolicy.WEEKLY, AutoRefreshPolicy.normalize("weekly"));
        assertEquals(7L * 24 * 60 * 60 * 1000, AutoRefreshPolicy.intervalMs("weekly"));
    }

    @Test public void unexpectedOrCaseChangedSettingFailsToDaily() {
        assertEquals(AutoRefreshPolicy.DAILY, AutoRefreshPolicy.normalize("WEEKLY"));
        assertEquals(AutoRefreshPolicy.DAY_MS, AutoRefreshPolicy.intervalMs(""));
    }
}
