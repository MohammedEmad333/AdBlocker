package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

/** Existing question/transaction validation also gates DoH responses. */
public class DohResponseValidationTest {
    @Test public void acceptsMatchingDnsReply() {
        byte[] q = DnsPacket.buildQuery("example.com", 0x2501);
        byte[] reply = DnsPacket.buildNxDomain(q, 0, q.length);
        assertTrue(DnsTcpWire.matchesQuery(q, reply));
    }

    @Test public void rejectsMismatchedDnsReply() {
        byte[] q = DnsPacket.buildQuery("example.com", 0x2502);
        byte[] other = DnsPacket.buildQuery("wrong.example", 0x2502);
        byte[] reply = DnsPacket.buildNxDomain(other, 0, other.length);
        assertFalse(DnsTcpWire.matchesQuery(q, reply));
    }

    @Test public void rejectsQueryWithoutResponseFlag() {
        byte[] q = DnsPacket.buildQuery("example.com", 0x2503);
        assertFalse(DnsTcpWire.matchesQuery(q, q));
    }
}
