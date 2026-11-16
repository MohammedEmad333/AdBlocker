# AdBlocker 1.9.0 - source-only development candidate

**Status: not a signed or publicly published release. Physical-device QA
and signed in-place upgrade testing are pending.**

## Highlights

- **Portable settings profile:** export and import allow/block rules, app
  exclusions, built-in and custom HTTPS blocklist settings, encrypted DNS
  choices and the automatic refresh schedule. Invalid, duplicated, oversized
  or unsupported input is rejected before overwriting settings, and the user
  confirms replacement in advance.
- **Session DNS trends:** four bounded 15-minute decision-count buckets on
  the dashboard. All counters stay in RAM and are cleared on VPN restart.
  Counts are blocked versus forwarded decisions, not successful resolver
  replies, and contain no domain names.
- Arabic and English UI and regression tests for both features.

## Compatibility and data safety

- VersionName 1.9.0, versionCode 12, applicationId
  `com.example.adblocker` unchanged to retain the current upgrade path.
- No cloud service, extra analytics provider, new credentials or remote
  collection. DNS activity capture remains optional and RAM-only.
- Full settings backups are unencrypted plain UTF-8: user-selected domains,
  app package identifiers and custom list URLs can be sensitive. Users must
  protect exported backup files.
- DNS history, timestamps, downloaded lists, cached data and transient pause
  status are *not* exported or restored.
- The existing domain-rules-only backup format remains supported.
- Imported app exclusion and DoH settings require the VPN to restart.

## Automated acceptance and outstanding device gates

1. Run Android Lint, JVM tests, debug APK build and Android 16 emulator smoke
   against the combined 1.9.0 code before merging version metadata.
2. Check Android 8-16 device picker import/export workflows, invalid or
   cancelled files, multi-language RTL, and Android process recreation.
3. Check VPN and DNS forwarding/DoH after profile import, with and without
   any cached custom blocklists, plus loss of network connectivity.
4. Test 15-minute bucket rollover, pause interactions, timer behavior and
   screen-off periods on a real phone; verify there are no saved query names
   when history recording is disabled.
5. Check automatic job scheduling after settings restore under battery saver,
   reboot and metered-network constraints.
6. Validate a signed upgrade from versionCode 11 and owner-approved package
   identity before shipping; never check signing secrets into Git.

No production signing, Play Store submission or public release is performed
by this source-only version update.
