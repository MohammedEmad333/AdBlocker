package com.example.adblocker;

import java.util.Arrays;

/** Validate that a DNS probe reply answers the actual question asked. */
public final class DnsResolverProbe {
    private DnsResolverProbe() {}

    public static boolean accepts(byte[] query, byte[] response, int receivedLength) {
        return query != null && response != null
                && receivedLength >= 17 && receivedLength <= response.length
                && DnsTcpWire.matchesQuery(query,
                        Arrays.copyOf(response, receivedLength));
    }
}
