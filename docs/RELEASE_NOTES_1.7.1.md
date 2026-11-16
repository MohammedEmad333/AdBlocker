# AdBlocker 1.7.1 - maintenance candidate

**Status: source-code candidate only. Not signed, published or production-validated.**

## Changes

- Bump versionName to 1.7.1 and versionCode to 10; keep the existing
  `com.example.adblocker` application ID to avoid breaking upgrade continuity.
- Give each blocklist download its own temporary staging file so overlapping
  refreshes cannot overwrite each other's partial content.
- Keep the previous validated cached list until the new download passes HTTPS,
  size and domain-count checks and has been safely promoted.
- Treat cache files with future timestamps as stale after device clock rollback.
  Stale files remain available if offline.
- Reject case-varied local hostname suffixes and trailing-dot hostnames.
- Extend JVM regressions for concurrent staging, failed staging, clock changes
  and public/private DNS64 answers; remove full custom list URL logging.

## Automated checks required before merging

1. Android Lint, Java unit tests and debug APK assembly pass on this PR.
2. Android 16 emulator launch/rotation smoke passes on this PR.
3. After merging, the main branch's Android CI passes.

## Release blockers (not completed by these code changes)

- Physical Android 8-16 VPN/DNS/DoH tests and network handoff checks.
- Real HTTPS/TLS, IPv6-only/DNS64/NAT64, and parallel list refresh tests.
- Privacy, battery/memory and imported-rule migration testing.
- Owner-approved Android application identity and signed upgrade verification
  from versionCode 9 to 10; signing credentials stay out of Git.

This candidate is not a public APK release or Google Play submission.
