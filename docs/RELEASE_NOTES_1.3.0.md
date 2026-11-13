# AdBlocker 1.3.0 - Release candidate notes

**Status: code preparation only, not a production release.**

## Included changes (subject to required PR merges)

- Improve DNS resolver recovery on unstable Wi-Fi and cellular networks.
  Error-driven probes use a cooldown and retry periodically after connectivity
  failures instead of repeatedly probing for every failing query.
- Show the outcome of manual blocklist refreshes in Settings even when
  protection is running, distinguishing complete, partial and failed updates.
- Extend bounded DNS/TCP unit tests for ACK completion, retransmission limits
  and malformed upstream responses.
- Repair the Android 16 device test matrix, and clarify that DNS/TCP support
  is limited to the app's virtual resolver.
- Keep API 36 targeting and the Android 16 emulator launch/rotation smoke test.

## Release blockers

1. Run `gradle --no-daemon lintDebug testDebugUnitTest assembleDebug`,
   inspect its reports, and confirm Android 16 emulator smoke passes on the
   final merged commit.
2. Complete `docs/DEVICE_TEST_MATRIX.md` on representative physical devices.
   Record Android 8/9, Android 13, Android 14/15, and Android 16 where possible.
3. Validate real DNS filtering, TCP/53 fallback, airplane mode,
   Wi-Fi-to-cellular handoff, offline cache use, and manual list update results.
4. Confirm no signed public release or Store submission occurs before owner
   approval and verified upgrade/signing tests.

The application ID remains `com.example.adblocker`. Choose a permanent ID
and signing identity before first public publication. The project is still
a DNS-only VPN, not a full-traffic firewall and does not intercept DoH/DoT
from other applications.
