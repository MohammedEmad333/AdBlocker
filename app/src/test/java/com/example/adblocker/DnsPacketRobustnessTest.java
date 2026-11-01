package com.example.adblocker;

import org.junit.Test;
import java.util.Arrays;
import java.util.Random;
import static org.junit.Assert.*;

public class DnsPacketRobustnessTest {
    @Test public void malformedOffsetsAreRejected() {
        byte[] q = DnsPacket.buildQuery("example.org", 0x1234);
        assertNull(DnsPacket.extractDomain(null, 0, 30));
        assertNull(DnsPacket.extractDomain(q, -1, q.length));
        assertNull(DnsPacket.extractDomain(q, 0, Integer.MAX_VALUE));
        assertNull(DnsPacket.extractDomain(q, q.length, 1));
        assertEquals(-1, DnsPacket.questionEnd(q, 0, 0));
    }

    @Test public void malformedRootIsRejected() {
        byte[] q = DnsPacket.buildQuery("ads.example.org", 0x3344);
        q[q.length - 5] = 63;
        assertNull(DnsPacket.extractDomain(q, 0, q.length));
        assertEquals(-1, DnsPacket.questionEnd(q, 0, q.length));
    }

    @Test public void responseRejectsInvalidBounds() {
        byte[] q = DnsPacket.buildQuery("example.org", 0x3344);
        byte[] a = DnsPacket.buildNxDomain(q, 0, q.length);
        try {
            DnsPacket.buildResponsePacket(new byte[16], 20, a);
            fail("short IP header");
        } catch (IllegalArgumentException expected) {}
        try {
            DnsPacket.buildResponsePacket(new byte[28], 19, a);
            fail("invalid IPv4 header");
        } catch (IllegalArgumentException expected) {}
    }

    @Test public void responseRetainsTransactionAndQuestion() {
        byte[] q = DnsPacket.buildQuery("ads.example.org", 0xF00D);
        byte[] a = DnsPacket.buildNxDomain(q, 0, q.length);
        assertEquals(0xF0, a[0] & 0xFF);
        assertEquals(0x0D, a[1] & 0xFF);
        assertArrayEquals(Arrays.copyOfRange(q, 12, q.length),
                Arrays.copyOfRange(a, 12, a.length));
    }

    @Test public void randomizedPayloadsDoNotThrow() {
        Random random = new Random(0xAABBCCDD);
        for (int i = 0; i < 1500; i++) {
            byte[] data = new byte[random.nextInt(180)];
            random.nextBytes(data);
            int start = random.nextInt(data.length + 1);
            int length = random.nextInt(data.length - start + 1);
            String name = DnsPacket.extractDomain(data, start, length);
            int end = DnsPacket.questionEnd(data, start, length);
            if (end < 0) assertNull(name);
        }
    }
}
