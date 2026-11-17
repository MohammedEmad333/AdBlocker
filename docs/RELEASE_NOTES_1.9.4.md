# AdBlocker 1.9.4 - DNS safety maintenance candidate

**Status: code-only development candidate; not signed or publicly released.**

## Improvements

- A temporary 5/15/30-minute blocking pause ends immediately if the
  process's elapsed monotonic clock regresses or becomes invalid. The old
  deadline can no longer extend the time DNS blocking is disabled.
- The 128-entry RAM-only DNS answer cache only stores simple recursive
  single-question A/AAAA requests with normal query flags and zero
  answer, authority and additional counts.
- DNS responses with mismatched recursion flags, truncation, nonzero
  RCODE or DNSSEC/reserved header flags are never cached. The VPN's
  upstream DNS forwarding is unchanged; these requests/responses simply
  bypass cache reuse.
- Regression tests cover clock rollback, invalid timestamps, fresh
  user-requested pauses, unusual query headers and reply flags.

## Compatibility and release gates

- `versionName 1.9.4`, `versionCode 16`, unchanged app ID
  `com.example.adblocker` (signed upgrade continuity).
- No change to privacy defaults, settings backup format, DNS history,
  remote blocklists or automatic refresh scheduling.
- Android Lint, JVM tests, APK debug assembly and Android 16 emulator
  launch/rotation smoke must pass before merging.
- Physical Android device QA remains pending in issue #64, including
  VPN consent, DoH, job lifecycle, DNS64/NAT64, network handoffs, timed
  pause behavior and signed upgrade from previous versions.
- Do not publish debug APKs as signed release builds and do not commit
  signing credentials.
