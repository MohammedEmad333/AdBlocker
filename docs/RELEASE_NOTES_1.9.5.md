# AdBlocker 1.9.5: DNS response correlation maintenance candidate

**Status:** code-only candidate. No signed or public release.

## DNS safety

- Require classic UDP DNS, TCP DNS and DNS-over-HTTPS responses to echo
  the original query's recursion-desired (RD) bit as well as matching
  transaction ID, opcode, question domain, QTYPE and QCLASS.
- A same-ID, same-question response with mismatched recursion intent
  is ignored rather than accepted as an answer.
- Non-recursive queries are still accepted when the reply echoes RD=0.
- Add JVM regression tests for both recursive and non-recursive cases.
- No changes to upstream resolver selection, encrypted DNS downgrade
  behavior, blocklists, saved preferences, or DNS history privacy.

## Release gates

- Version name 1.9.5, version code 17; app ID remains
  `com.example.adblocker`.
- Require Android CI (lint, JVM, debug APK) and Android 16 emulator smoke
  before merging.
- Physical-device VPN/DoH, DNS64, network handoff and signed-upgrade
  testing are still required in issue #64. No automatic signed release.
