package com.example.adblocker;

import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.*;

public class DnsResponseValidationTest {
    @Test public void validDnsReplyMatchesActualReceivedBytes() {
        byte[] q = DnsPacket.buildQuery("example.com", 0x2201);
        byte[] reply = DnsPacket.buildNxDomain(q, 0, q.length);
        byte[] receiveBuffer = Arrays.copyOf(reply, reply.length + 200);
        assertTrue(DnsTcpWire.matchesQuery(q, receiveBuffer, reply.length));
        assertTrue(DnsTcpWire.matchesQuery(q, reply));
    }

    @Test public void missingTailOfDnsQuestionCannotUseBufferPadding() {
        byte[] q = DnsPacket.buildQuery("example.com", 0x2202);
        byte[] reply = DnsPacket.buildNxDomain(q, 0, q.length);
        byte[] receiveBuffer = Arrays.copyOf(reply, reply.length + 200);
        assertFalse(DnsTcpWire.matchesQuery(q, receiveBuffer, reply.length - 1));
        assertFalse(DnsTcpWire.matchesQuery(q, receiveBuffer, 12));
    }

    @Test public void responseOutsideReceiveBufferIsRejected() {
        byte[] q = DnsPacket.buildQuery("example.com", 0x2203);
        byte[] reply = DnsPacket.buildNxDomain(q, 0, q.length);
        assertFalse(DnsTcpWire.matchesQuery(q, reply, reply.length + 1));
        assertFalse(DnsTcpWire.matchesQuery(q, reply, -1));
    }

    @Test public void mismatchedDnsOpcodeIsRejected() {
        byte[] q = DnsPacket.buildQuery("example.com", 0x2204);
        byte[] reply = DnsPacket.buildNxDomain(q, 0, q.length);
        reply[2] = (byte) (reply[2] | 0x08); // non-query opcode
        assertFalse(DnsTcpWire.matchesQuery(q, reply, reply.length));
    }

    @Test public void originalQuestionTypeAndDomainStillRequired() {
        byte[] q = DnsPacket.buildQuery("example.com", 0x2205);
        byte[] wrong = DnsPacket.buildQuery("example.net", 0x2205);
        byte[] reply = DnsPacket.buildNxDomain(wrong, 0, wrong.length);
        assertFalse(DnsTcpWire.matchesQuery(q, reply, reply.length));

        byte[] normal = DnsPacket.buildNxDomain(q, 0, q.length);
        normal[normal.length - 3] = 28; // QTYPE AAAA instead of A
        assertFalse(DnsTcpWire.matchesQuery(q, normal, normal.length));
    }
}
