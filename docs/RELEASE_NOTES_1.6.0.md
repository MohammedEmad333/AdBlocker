# AdBlocker 1.6.0 - development candidate

**Status: not signed or publicly published.**

## Features planned for this development candidate

- Select Cloudflare, Google Public DNS or Quad9 as the active encrypted
  DNS provider from Settings. The selection is fixed per VPN session.
  There is no automatic plaintext DNS or operator fallback.
- View local, RAM-only session diagnostics, including the VPN state,
  upstream provider, DNS answers, timeouts and error counts. Diagnostics
  do not establish absence of leaks or complete device-wide coverage.
- Temporarily allow an exact blocked domain for 15 or 60 minutes without
  changing permanent rules. A limit of 100 temporary entries applies.
  Device clock changes can affect expiry times.
- Conservative in-memory TTL-aware 128-entry cache for simple positive
  A/AAAA UDP DNS replies (not negative DNS, TCP, EDNS, DNSSEC or complex
  answers). Cached values are cleared on resolver recovery or VPN startup.

These features are included only after their PRs pass CI and are merged.

## Release blockers

1. All feature and release metadata PRs pass lint, Java unit tests, APK
   assembly/integrity and Android 16 emulator smoke.
2. On representative real Android phones, test VPN consent, toggling,
   DNS filtering, Wi-Fi/cellular handoff, airplane mode and DNS/TCP.
3. Test all three DoH providers with working networks, failures, invalid
   certificates, captive portals and intentional no-plaintext-fallback.
4. Verify temporary allows, expiry and changes to device time, app process
   restart, activity log interactions and active VPN policy reload.
5. Validate TTL cache behavior on IPv4/IPv6, compressed answers,
   simultaneous requests, stale network changes and memory limits.
6. Review custom HTTPS blocklist hostname/IP changes, signing, stable
   application ID and signed upgrade before any public release.

No production signing, user rollout or Play Store submission is part of
this version metadata change.
