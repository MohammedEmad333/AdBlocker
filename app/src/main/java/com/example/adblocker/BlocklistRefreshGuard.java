package com.example.adblocker;

import java.io.InterruptedIOException;
import java.util.function.BooleanSupplier;

/** Shared interrupt and job-ownership checks for background HTTPS downloads. */
final class BlocklistRefreshGuard {
    private BlocklistRefreshGuard() {}

    static boolean isActive(BooleanSupplier stillAllowed) {
        return !Thread.currentThread().isInterrupted()
                && (stillAllowed == null || stillAllowed.getAsBoolean());
    }

    static void requireActive(BooleanSupplier stillAllowed) throws InterruptedIOException {
        if (!isActive(stillAllowed))
            throw new InterruptedIOException("Blocklist refresh cancelled");
    }
}
