package com.example.adblocker;

import org.junit.Test;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class CustomSourceCatalogTest {
    private static final String A = "https://lists.example.com/hosts-a";
    private static final String B = "https://lists.example.org/hosts-b";
    private static final String C = "https://lists.example.net/hosts-c";

    @Test public void migratesLegacySourceWithoutDroppingIt() {
        Map<String, Boolean> catalog = CustomSourceCatalog.decode(Collections.emptySet(), A);
        assertEquals(1, catalog.size());
        assertTrue(catalog.get(A));
        assertEquals(catalog, CustomSourceCatalog.decode(CustomSourceCatalog.encode(catalog), A));
    }

    @Test public void preservesDisabledSourcesAndCanEnableThemAgain() {
        LinkedHashMap<String, Boolean> data = CustomSourceCatalog.add(
                Collections.emptyMap(), A);
        data = CustomSourceCatalog.withEnabled(data, A, false);
        assertFalse(CustomSourceCatalog.decode(CustomSourceCatalog.encode(data), "").get(A));
        assertTrue(CustomSourceCatalog.withEnabled(data, A, true).get(A));
    }

    @Test public void refusesFourthSourceAndInvalidUrl() {
        LinkedHashMap<String, Boolean> data = new LinkedHashMap<>();
        data = CustomSourceCatalog.add(data, A);
        data = CustomSourceCatalog.add(data, B);
        data = CustomSourceCatalog.add(data, C);
        assertEquals(3, data.size());
        try {
            CustomSourceCatalog.add(data, "https://extra.example.edu/hosts");
            fail("Exceeded three sources");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
        try {
            CustomSourceCatalog.add(data, "http://unsafe.example.com/list");
            fail("Accepted insecure source");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test public void deduplicatesAndIgnoresMalformedStoredEntries() {
        Map<String, Boolean> sources = CustomSourceCatalog.decode(
                new java.util.HashSet<>(java.util.Arrays.asList(
                    "0|" + A, "1|" + A, "1|http://bad.example.com", "junk")), "");
        assertEquals(1, sources.size());
        assertTrue(sources.get(A));
    }
}
