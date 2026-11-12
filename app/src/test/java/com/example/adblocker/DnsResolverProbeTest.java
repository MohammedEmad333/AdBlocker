package com.example.adblocker;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class DnsResolverProbeTest {
    @Test public void acceptsReplyToExactQuestion() {
        byte[] query = DnsPacket.buildQuery("example.com", 0x4A21);
        byte[] response = DnsPacket.buildNxDomain(query, 0, query.length);
        assertTrue(DnsResolverProbe.accepts(query, response, response.length));
    }

    @Test public void rejectsSameTransactionForDifferentQuestion() {
        byte[] query = DnsPacket.buildQuery("example.com", 0x4A21);
        byte[] wrong = DnsPacket.buildQuery("another.com", 0x4A21);
        byte[] response = DnsPacket.buildNxDomain(wrong, 0, wrong.length);
        assertFalse(DnsResolverProbe.accepts(query, response, response.length));
    }

    @Test public void doesNotInspectPastReceivedPacketLength() {
        byte[] query = DnsPacket.buildQuery("example.com", 0x4A21);
        byte[] response = DnsPacket.buildNxDomain(query, 0, query.length);
        byte[] padded = Arrays.copyOf(response, response.length + 100);
        assertTrue(DnsResolverProbe.accepts(query, padded, response.length));
        assertFalse(DnsResolverProbe.accepts(query, padded, response.length - 1));
        assertFalse(DnsResolverProbe.accepts(query, padded, padded.length + 1));
    }

    @Test public void rejectsWrongTypeAndMalformedReplies() {
        byte[] query = DnsPacket.buildQuery("example.com", 0x4A21);
        byte[] answer = DnsPacket.buildNxDomain(query, 0, query.length);
        byte[] changed = answer.clone();
        changed[changed.length - 3] = 28;
        assertFalse(DnsResolverProbe.accepts(query, changed, changed.length));
        assertFalse(DnsResolverProbe.accepts(query, null, 0));
        assertFalse(DnsResolverProbe.accepts(query, new byte[4], 4));
    }
}
