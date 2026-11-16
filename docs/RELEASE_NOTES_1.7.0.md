# AdBlocker 1.7.0 - development candidate

**Status: source-code candidate only, not a signed/public release.**

## Features (only after their PRs are accepted and merged)

- Merge imported custom allow/block rules with a preview of added rules and
  exact-domain conflicts, preserving existing rules on conflict. Replacing
  imported rules remains available. A 2,000-rule total limit applies.
- Optional in-memory DNS history recording. Turning capture off erases
  existing history immediately; re-enabling captures future events only.
  Filtering and aggregate session counts continue.
- Up to three independently enabled/disabled custom HTTPS blocklists, with
  migration of the old single URL, per-source cached date and domain count,
  existing safe-URL checks, and bounded 16-MiB downloads.
- Live search of app exclusions by display label or Android package name,
  without clearing selected exceptions. A VPN restart remains required to
  apply app exclusions.
- HTTPS blocklist transport validates DNS at connection time and uses only
  approved IPs for the corresponding TLS connection (no DNS preflight race).
  Proxies and redirects are disabled and cached fallbacks remain available.

## Release blockers

1. All PRs and the final merged code pass Java tests, Android Lint,
   debug APK assembly and Android 16 emulator smoke.
2. Real Android 8-16 device QA: VPN consent/stop/revoke, filtering,
   encrypted DNS, network handoffs, temporary exceptions, DNS/TCP and
   cache behavior. Do not claim these are covered by emulator launch smoke.
3. Import/merge QA with conflicting rules, duplicate imports, 2,000-rule
   limit, Activity recreation and storage-provider failures.
4. Test three lists over HTTPS: enable/disable/remove/re-add, migration,
   invalid TLS, redirects, stale offline cache and partial refresh failures.
5. Privacy testing of recording opt-out during live traffic, and of app
   exclusions after VPN restart in Arabic and English.
6. Verify the connect-time validated DNS transport with real HTTPS servers,
   invalid certificates, DNS changes, DNS64/NAT64 and network handoffs.
7. Choose a permanent Android application ID and verify signed upgrades
   and production release credentials before public distribution.

No production signing, public APK publication or Google Play submission is
performed by this code-only release version bump.
