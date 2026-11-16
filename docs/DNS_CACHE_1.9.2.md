# AdBlocker 1.9.2: conservative DNS response cache validation

## Why this change

The existing RAM-only response cache already requires a single IN address
record, matching transaction ID and question, with a bounded TTL. However,
the resource-record type could differ from the requested QTYPE: an A answer
was still accepted for an AAAA query (or vice versa). That mismatch is valid
to *parse* but inappropriate to cache as the requested address family.

## Changes

- Only cache one positive IPv4 A answer for an IN/A question, or one positive
  IPv6 AAAA answer for an IN/AAAA question.
- Do not cache other classes, response types, truncated answers, EDNS,
  negative answers or mixed/multiple records. Those requests continue
  through the normal upstream resolver.
- Reject response-cache reuse if the elapsed time goes backward or its
  expiry would overflow; keep the existing 60-second TTL cap, 128-entry
  limit and RAM-only policy.
- Add regression tests for query/answer family mismatches, valid AAAA
  answers, invalid classes, elapsed-time rollback and overflow.

## Required verification

Android CI (Lint, JUnit and debug APK) and Android 16 emulator smoke before
merging. This does not replace network, VPN/DoH, cellular and real-device
validation in issue #64. No APK signing or public release here.
