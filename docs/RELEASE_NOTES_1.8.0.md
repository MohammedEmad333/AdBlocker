# AdBlocker 1.8.0 - development candidate

**Status: source-code development candidate only, not signed, published or
production-validated.**

## Features

- Opt-in daily or weekly automatic HTTPS blocklist refresh using Android
  JobScheduler when DNS protection is off; optional unmetered-network constraint.
  The schedule is approximate and respects battery/network limitations.
- Session-only 5, 15 or 30-minute DNS blocking pause, with automatic
  monotonic-clock expiry and an explicit warning on the home dashboard.
  Pausing does not stop DNS forwarding or change permanent user rules.
- Dashboard shows last successful blocklist download freshness and makes
  the local-only DNS history recording preference clear. Aggregate session
  counters remain visible when DNS history recording is disabled.

## Compatibility and privacy

- `versionCode 11`, `versionName 1.8.0`, unchanged package ID
  `com.example.adblocker` for continuity of existing installations.
- Background refresh and temporary blocking pause are optional. Background
  refresh defaults off. Pauses are not persisted across VPN restarts.
- No cloud account, new telemetry server or change to the RAM-only DNS
  history policy. The HTTPS download checks and last-good-cache fallback
  from 1.7.1 stay in place.

## CI and release gates

1. All feature PRs are merged and the resulting `main` passes Android Lint,
   JVM tests and debug APK build.
2. Android 16 emulator launch/rotation smoke passes for affected PRs.
3. Real-device tests confirm JobScheduler on Android 8-16 (screen-off, Doze,
   reboot, unmetered networks and network changes).
4. Real-device tests confirm pause UI and automatic unblock after its deadline,
   including VPN revoke/restart, DNS/TCP and DoH.
5. Test DNS64/NAT64, encrypted DNS, TLS failures, VPN connectivity and
   changes between cellular and Wi-Fi.
6. Choose an owner-approved final Android package identity and validate
   signed in-place upgrades from versionCode 10 before any public release.
7. Confirm notification behavior, Arabic/English RTL, privacy preferences,
   battery and memory usage on physical phones.

No production signing, public APK publication or Play submission is done here.
