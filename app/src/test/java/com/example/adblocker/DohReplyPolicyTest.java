package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class DohReplyPolicyTest {
    @Test public void acceptsCompleteMatchingDnsAnswer() {
        byte[] query = DnsPacket.buildQuery("example.com", 0x1511);
        byte[] reply = DnsPacket.buildNxDomain(query, 0, query.length);
        assertTrue(DohReplyPolicy.isAcceptable(query, reply));
    }

    @Test public void rejectsTruncatedDnsOverHttpsReply() {
        byte[] query = DnsPacket.buildQuery("example.com", 0x1512);
        byte[] reply = DnsPacket.buildNxDomain(query, 0, query.length);
        reply[2] |= 0x02;
        assertFalse(DohReplyPolicy.isAcceptable(query, reply));
    }

    @Test public void rejectsOtherTransactionAndQuestion() {
        byte[] query = DnsPacket.buildQuery("example.com", 0x1513);
        byte[] wrong = DnsPacket.buildQuery("wrong.example", 0x1513);
        assertFalse(DohReplyPolicy.isAcceptable(query,
                DnsPacket.buildNxDomain(wrong, 0, wrong.length)));
        byte[] answer = DnsPacket.buildNxDomain(query, 0, query.length);
        answer[0] ^= 1;
        assertFalse(DohReplyPolicy.isAcceptable(query, answer));
    }

    @Test public void rejectsInvalidQuestionCountAndShortReplies() {
        byte[] query = DnsPacket.buildQuery("example.com", 0x1514);
        byte[] answer = DnsPacket.buildNxDomain(query, 0, query.length);
        answer[5] = 2;
        assertFalse(DohReplyPolicy.isAcceptable(query, answer));
        assertFalse(DohReplyPolicy.isAcceptable(query, new byte[11]));
    }
}
