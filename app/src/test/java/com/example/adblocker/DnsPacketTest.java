package com.example.adblocker;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class DnsPacketTest {

    private static byte[] query(String domain) {
        return DnsPacket.buildQuery(domain, 0xBEEF);
    }

    @Test public void extractsDomainAndQuestionEnd() {
        byte[] q = query("Ads.Example.com");
        assertEquals("ads.example.com", DnsPacket.extractDomain(q, 0, q.length));
        assertEquals(q.length, DnsPacket.questionEnd(q, 0, q.length));
    }

    @Test public void rejectsTruncatedAndMalformedQueries() {
        byte[] q = query("example.com");
        assertNull(DnsPacket.extractDomain(q, 0, q.length - 3));
        assertEquals(-1, DnsPacket.questionEnd(q, 0, q.length - 3));
        assertNull(DnsPacket.extractDomain(q, 0, q.length + 1));
        q[12] = (byte) 0xC0; // compressed labels are not supported here
        assertNull(DnsPacket.extractDomain(q, 0, q.length));
        assertEquals(-1, DnsPacket.questionEnd(q, 0, q.length));
    }

    @Test public void rejectsMultipleQuestions() {
        byte[] q = query("example.com");
        q[5] = 2;
        assertNull(DnsPacket.extractDomain(q, 0, q.length));
        assertEquals(-1, DnsPacket.questionEnd(q, 0, q.length));
    }

    @Test public void nxdomainStripsExtraDataAndPreservesTransactionId() {
        byte[] q = query("blocked.example");
        byte[] edns = Arrays.copyOf(q, q.length + 11);
        edns[11] = 1; // ARCOUNT, pretend trailing bytes represent EDNS OPT
        byte[] response = DnsPacket.buildNxDomain(edns, 0, edns.length);
        assertEquals(q.length, response.length);
        assertEquals(q[0], response[0]);
        assertEquals(q[1], response[1]);
        assertEquals(0x80, response[2] & 0xFF);
        assertEquals(0x83, response[3] & 0xFF);
        assertEquals(0, response[11] & 0xFF);
    }

    @Test public void saturatedForwardingRespondsServfail() {
        byte[] q = query("example.com");
        byte[] response = DnsPacket.buildServFail(q, 0, q.length);
        assertEquals(2, response[3] & 0x0F);
        assertEquals(1, response[5] & 0xFF);
    }

    @Test public void wrapsDnsReplyAndSwapsAddressesAndPorts() {
        byte[] q = query("example.com");
        byte[] ip = new byte[28 + q.length];
        ip[0] = 0x45;
        ip[9] = 17;
        ip[12] = 10; ip[13] = 0; ip[14] = 0; ip[15] = 2;
        ip[16] = 10; ip[17] = 0; ip[18] = 0; ip[19] = 1;
        ip[20] = (byte) 0xAB; ip[21] = (byte) 0xCD; // source port
        ip[22] = 0; ip[23] = 53;
        byte[] reply = DnsPacket.buildResponsePacket(ip, 20,
                DnsPacket.buildNxDomain(q, 0, q.length));
        assertEquals(10, reply[12] & 0xFF);
        assertEquals(1, reply[15] & 0xFF);
        assertEquals(2, reply[19] & 0xFF);
        assertEquals(53, reply[21] & 0xFF);
        assertEquals(0xAB, reply[22] & 0xFF);
        assertEquals(0xCD, reply[23] & 0xFF);
    }
}
