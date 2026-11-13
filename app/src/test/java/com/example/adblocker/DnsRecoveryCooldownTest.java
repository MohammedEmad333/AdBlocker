package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class DnsRecoveryCooldownTest {
    @Test public void repeatedErrorsDoNotTriggerRepeatedProbes() {
        DnsRecoveryCooldown cooldown = new DnsRecoveryCooldown();
        assertTrue(cooldown.tryAcquire(100));
        assertFalse(cooldown.tryAcquire(101));
        assertFalse(cooldown.tryAcquire(15_099));
        assertTrue(cooldown.tryAcquire(15_100));
    }

    @Test public void rebootOrNewSessionCanRetryImmediately() {
        DnsRecoveryCooldown cooldown = new DnsRecoveryCooldown();
        assertTrue(cooldown.tryAcquire(20_000));
        assertTrue(cooldown.tryAcquire(100)); // monotonic clock reset
        assertFalse(cooldown.tryAcquire(101));
        cooldown.reset();
        assertTrue(cooldown.tryAcquire(101));
    }

    @Test public void periodIsMeasuredFromLastAcceptedAttempt() {
        DnsRecoveryCooldown cooldown = new DnsRecoveryCooldown();
        assertTrue(cooldown.tryAcquire(0));
        for (long ms = 1000; ms < 15000; ms += 1000) {
            assertFalse(cooldown.tryAcquire(ms));
        }
        assertTrue(cooldown.tryAcquire(DnsRecoveryCooldown.INTERVAL_MS));
        assertFalse(cooldown.tryAcquire(DnsRecoveryCooldown.INTERVAL_MS + 1));
    }
}
