package com.example.adblocker;

import org.junit.Test;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class BlocklistRefreshGuardTest {
    @Test public void acceptsActiveJobAndRejectsCancelledJob() throws Exception {
        AtomicBoolean active = new AtomicBoolean(true);
        BlocklistRefreshGuard.requireActive(active::get);
        active.set(false);
        assertFalse(BlocklistRefreshGuard.isActive(active::get));
        try {
            BlocklistRefreshGuard.requireActive(active::get);
            fail("Cancelled downloads must not continue");
        } catch (InterruptedIOException expected) { /* fail closed */ }
    }

    @Test public void respectsThreadInterruptionEvenWithLiveJobToken() throws Exception {
        try {
            Thread.currentThread().interrupt();
            assertFalse(BlocklistRefreshGuard.isActive(() -> true));
            try {
                BlocklistRefreshGuard.requireActive(() -> true);
                fail("Interrupted worker may not commit");
            } catch (InterruptedIOException expected) { /* preserved */ }
        } finally {
            Thread.interrupted(); // clear interruption for the JUnit runner
        }
    }
}
