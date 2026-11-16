# AdBlocker 1.9.2 - source-only development candidate

**Not a public release.** Physical-device QA and signed upgrade testing remain pending.

## Changes

- Reject an A resource record in a DNS cache response to an AAAA question and
  reject an AAAA resource record in a response to an A question.
- Only cache a matching IN/A or IN/AAAA address record for exactly one positive
  standard DNS question/answer. All other answers continue through the resolver.
- Treat elapsed-clock rollback and timestamp overflow as invalid cache states,
  with no extension of a DNS TTL beyond the configured maximum.
- Preserve the narrow 128-entry RAM-only positive cache and 60-second TTL cap.
- Add JVM tests for address-family mismatch, valid AAAA caching, non-IN class,
  backward time and expiry overflow.

## Compatibility and release gates

- `versionName 1.9.2`, `versionCode 14`.
- Maintain application ID `com.example.adblocker` for in-place upgrade continuity.
- Android CI (Lint/JUnit/debug APK) and Android 16 emulator smoke must pass on
  the combined feature and release metadata before merging.
- Physical-device VPN, DNS/DoH, background refresh, signed upgrade and
  battery/privacy QA remain blocked by issue #64.
- No signed APK, Google Play release, signing secret or public release here.
