# AdBlocker 1.4.0 - candidate notes

**Status: development code only. Not publicly released or signed.**

## Planned user-facing improvements

- Quick Settings protection tile that opens the app for VPN consent and controls.
- Allow or block a domain from the in-memory DNS activity log, with a one-step
  undo and safeguards for allowlist precedence.
- Show top blocked recent domains using RAM-only, capacity-limited aggregation.
- Offer Balanced (StevenBlack), Stronger (StevenBlack + AdAway), and Custom
  source choices without changing saved custom domain rules or app exclusions.

These changes are only included after their respective PRs are merged.

## Release gates

- All feature PRs and this version update must pass Android CI, including
  lint, Java tests, APK build and checksum verification.
- The Android 16 emulator smoke test must pass on the latest code, including
  installing, launching, rotating and relaunching MainActivity.
- Validate the Quick Settings tile, lock screen and VPN consent on real
  devices; verify log rules and undo with actual DNS queries.
- Verify Arabic/English, gesture navigation, lists, policy refresh,
  DNS/TCP, Wi-Fi/cellular handoff and battery behavior on Android devices.
- Choose a stable application ID and verify signed install/upgrade before
  any public Play Store release.

AdBlocker remains a DNS-only VPN; DoH, DoT, encrypted queries from other apps
and arbitrary TCP forwarding are outside the scope of this version.
