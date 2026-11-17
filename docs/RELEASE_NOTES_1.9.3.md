# AdBlocker 1.9.3: maintenance development candidate

**Status: code-only maintenance candidate. No public or signed APK release.**

## Security and reliability

- Temporary exact-domain allowlist clears itself if monotonic elapsed time
  regresses, preventing an older exemption deadline from remaining active
  after a clock reset or anomalous process lifecycle.
- Reject negative/invalid timer input without mutating active exemptions.
- Add regression tests for rollback, fail-closed behavior, normal renewal and
  unchanged bounded in-memory storage.
- Keep the existing application ID `com.example.adblocker`.
  Development candidate versionCode 15, versionName 1.9.3.

## Validation gates

- CI: Android Lint, JUnit tests and debug APK must pass.
- Android 16 emulator launch/rotation smoke must pass.
- Physical-device VPN/DoH, DNS64/NAT64, network handoff, signed in-place
  upgrade and lifecycle tests are still required in issue #64.
- No release artifact should be published or signed automatically.
