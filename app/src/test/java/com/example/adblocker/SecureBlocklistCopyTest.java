package com.example.adblocker;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class SecureBlocklistCopyTest {
    @Test public void completesBoundedCopyForActiveJob() throws Exception {
        byte[] payload = {1, 2, 3, 4, 5};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SecureBlocklistTransport.copyBounded(
                new ByteArrayInputStream(payload), out, payload.length, () -> true);
        assertArrayEquals(payload, out.toByteArray());
    }

    @Test public void rejectsOversizedBodyWithoutCommitting() throws Exception {
        try {
            SecureBlocklistTransport.copyBounded(
                    new ByteArrayInputStream(new byte[5]), new ByteArrayOutputStream(),
                    4, () -> true);
            fail("Oversized HTTPS response must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("safe download limit"));
        }
    }

    @Test public void cancellingDuringNetworkReadStopsBeforeWriting() throws Exception {
        AtomicBoolean active = new AtomicBoolean(true);
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[]{7, 8, 9}) {
            @Override public synchronized int read(byte[] buffer, int offset, int length) {
                int n = super.read(buffer, offset, length);
                active.set(false); // models onStopJob while read() is in progress
                return n;
            }
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            SecureBlocklistTransport.copyBounded(input, out, 10, active::get);
            fail("Read finished after job cancellation");
        } catch (InterruptedIOException expected) {
            assertEquals(0, out.size());
        }
    }
}
