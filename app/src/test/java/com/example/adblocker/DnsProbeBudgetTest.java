package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class DnsProbeBudgetTest {
    @Test public void fullBudgetUsesPerServerDeadline() {
        assertEquals(1500, DnsProbeBudget.timeoutMillis(0));
        assertEquals(1500, DnsProbeBudget.timeoutMillis(3000));
    }

    @Test public void finalProbeRespectsRemainingBudget() {
        assertEquals(400, DnsProbeBudget.timeoutMillis(5600));
        assertEquals(1, DnsProbeBudget.timeoutMillis(5999));
    }

    @Test public void noProbeAfterDeadline() {
        assertEquals(0, DnsProbeBudget.timeoutMillis(6000));
        assertEquals(0, DnsProbeBudget.timeoutMillis(12000));
        assertEquals(1500, DnsProbeBudget.timeoutMillis(-10));
    }
}
