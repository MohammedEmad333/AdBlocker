# AdBlocker 1.9.1 - development maintenance candidate

**Source-only development candidate, not signed or publicly published.**

## Changes

- JobScheduler background list updates now cooperatively abort when Android
  stops the job, the user disables auto-refresh or VPN starts.
- Validate cancellation during streaming, while parsing and immediately before
  promoting a temporary download to the last-good blocklist cache.
- Keep the existing validated cache and remove staging files after cancellation.
- Add JVM regression tests for cancellation, thread interruption, streaming
  size limits and preserving existing cached lists.
- Maintain existing Android package ID `com.example.adblocker`. VersionCode
  increases from 12 to 13, versionName is 1.9.1.

## Quality and release gates

- Android CI (Lint/JUnit/debug APK) and Android 16 emulator launch/rotation
  smoke must succeed on the complete development candidate.
- Verify Android JobScheduler cancellation, Doze, network handoffs and
  background constraints on physical devices before public release.
- Follow open issue #64 for physical-device VPN/DoH, profile backup restore,
  signed upgrade, Android compatibility, battery and RAM-only privacy checks.
- Never commit signing credentials or publicly release an unsigned/debug APK
  as a production build.

No signed/public release is created by this PR.
