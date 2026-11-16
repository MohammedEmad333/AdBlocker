package com.example.adblocker;

import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Dns;

import static org.junit.Assert.*;

/** Pure JVM tests for connect-time DNS validation and rebinding prevention. */
public class SecureBlocklistTransportTest {
    @Test public void returnsVerifiedPublicAddresses() throws Exception {
        Dns upstream = host -> Arrays.asList(
                InetAddress.getByName("1.1.1.1"), InetAddress.getByName("8.8.8.8"));
        List<InetAddress> verified = new SecureBlocklistTransport.ValidatedDns(
                "lists.example.org", upstream).lookup("lists.example.org");
        assertEquals(2, verified.size());
        assertEquals(InetAddress.getByName("1.1.1.1"), verified.get(0));
        try {
            verified.add(InetAddress.getByName("127.0.0.1"));
            fail("Verified DNS addresses must not be mutable");
        } catch (UnsupportedOperationException expected) { /* immutable snapshot */ }
    }

    @Test public void rejectsMixedPublicAndPrivateAnswers() throws Exception {
        Dns upstream = host -> Arrays.asList(
                InetAddress.getByName("1.1.1.1"), InetAddress.getByName("10.0.0.1"));
        try {
            new SecureBlocklistTransport.ValidatedDns("lists.example.org", upstream)
                    .lookup("lists.example.org");
            fail("A mixed DNS reply must be rejected");
        } catch (UnknownHostException expected) { /* fails closed */ }
    }

    @Test public void revalidatesEveryLookupIncludingRebindingAttempts() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Dns upstream = host -> Collections.singletonList(InetAddress.getByName(
                calls.getAndIncrement() == 0 ? "8.8.8.8" : "127.0.0.1"));
        SecureBlocklistTransport.ValidatedDns dns =
                new SecureBlocklistTransport.ValidatedDns("lists.example.org", upstream);
        assertEquals("8.8.8.8", dns.lookup("lists.example.org").get(0).getHostAddress());
        try {
            dns.lookup("lists.example.org");
            fail("A changed DNS answer must never be accepted without checking");
        } catch (UnknownHostException expected) { /* rejects rebound address */ }
        assertEquals(2, calls.get());
    }

    @Test public void rejectsUnexpectedHostWithoutResolvingIt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Dns upstream = host -> {
            calls.incrementAndGet();
            return Collections.singletonList(InetAddress.getByName("8.8.8.8"));
        };
        try {
            new SecureBlocklistTransport.ValidatedDns("lists.example.org", upstream)
                    .lookup("other.example.org");
            fail("Transport must never resolve a different host");
        } catch (UnknownHostException expected) { /* refuses host change */ }
        assertEquals(0, calls.get());
    }

    @Test public void rejectsNoAddressesAndReservedAddresses() throws Exception {
        for (Dns upstream : Arrays.<Dns>asList(
                host -> Collections.emptyList(),
                host -> Collections.singletonList(InetAddress.getByName("169.254.169.254")),
                host -> Collections.singletonList(InetAddress.getByName("100.64.1.1")),
                host -> Collections.singletonList(InetAddress.getByName("192.0.2.1")))) {
            try {
                new SecureBlocklistTransport.ValidatedDns("lists.example.org", upstream)
                        .lookup("lists.example.org");
                fail("Empty or reserved DNS answers must be rejected");
            } catch (UnknownHostException expected) { /* safe failure */ }
        }
    }
}
