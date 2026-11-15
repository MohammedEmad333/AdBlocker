package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppExclusionSearchTest {
    @Test public void matchesCaseInsensitiveName() {
        assertTrue(AppExclusionSearch.matches("YouTube", "com.google.android.youtube", "TUBE"));
    }

    @Test public void matchesPackageAndIgnoresWhitespace() {
        assertTrue(AppExclusionSearch.matches("Videos", "com.google.android.youtube", "  GOOGLE.android "));
    }

    @Test public void blankSearchShowsAll() {
        assertTrue(AppExclusionSearch.matches("Maps", "com.maps.example", ""));
        assertTrue(AppExclusionSearch.matches("Maps", "com.maps.example", null));
    }

    @Test public void unrelatedNameAndPackageAreExcluded() {
        assertFalse(AppExclusionSearch.matches("Maps", "com.maps.example", "mail"));
    }
}
