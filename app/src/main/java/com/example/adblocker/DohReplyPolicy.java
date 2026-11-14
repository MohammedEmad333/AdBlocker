package com.example.adblocker;

/** DNS response checks required for a complete RFC 8484 HTTPS answer. */
final class DohReplyPolicy {
    private DohReplyPolicy() {}

    static boolean isAcceptable(byte[] query, byte[] response) {
        if (!DnsTcpWire.matchesQuery(query, response)) return false;
        // Unlike classic UDP DNS, DoH must return the complete DNS message.
        // A TC=1 reply must not silently be treated as a usable answer.
        if ((response[2] & 0x02) != 0) return false;
        // This client sends only one DNS question per request.
        return (response[4] & 0xff) == 0 && (response[5] & 0xff) == 1;
    }
}
