# Android field validation checklist

CI checks lint, Java unit tests and build artifacts. **Real VPN behavior and UI must still be verified on Android devices.** Record model, Android version, network and sanitized evidence.

| Scenario | Android 8/9 | Android 13 | Android 14/15 | Android 16 |
| --- | --- | --- | --- | --- |
| Install and grant VPN consent | Pending | Pending | Pending | Pending |
| Start, stop, cancel during initialization | Pending | Pending | Pending | Pending |
| Known blocked and allowed DNS domains | Pending | Pending | Pending | Pending |
| Custom allowlist and blocklist precedence | Pending | Pending | Pending | Pending |
| Service/notification in background | Pending | Pending | Pending | Pending |
| Wi-Fi to cellular handoff | Pending | Pending | Pending | Pending |
| Offline restart with stale cache | Pending | Pending | Pending | Pending |
| Manual list refresh and malformed response | Pending | Pending | Pending | Pending |
| Per-app exclusions after VPN restart | Pending | Pending | Pending | Pending |
| Arabic RTL, English, system language | Pending | Pending | Pending | Pending |
| DNS activity search and bounded history | Pending | Pending | Pending | Pending |
| DNS burst, memory usage and battery | Pending | Pending | Pending | Pending |
| App-originated DNS/TCP over IPv4 and IPv6 | Pending | Pending | Pending | Pending |
| Split DNS/TCP request, large response, retransmission | Pending | Pending | Pending | Pending |
| TCP late SYN/RST and invalid ACK handling | Pending | Pending | Pending | Pending |
| Release signing/upgrade validation | Pending | Pending | Pending | Pending |

Useful local commands:

```bash
gradle --no-daemon lintDebug testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell dumpsys vpn
adb shell dumpsys activity services com.example.adblocker
adb logcat -d -s AdBlockVpn AdBlockList
```

Logcat may contain network metadata. Obtain consent and redact domain names, tokens or private data before sharing results. Full VPN products cannot always run together.

**Release gate:** Do not describe the app as production-validated until a signed APK passes the matrix on representative devices and critical defects are resolved.

## Android 16 automated smoke check

The separate **Android 16 emulator smoke** workflow launches an API 36
emulator on PRs that change the Android app or its build, and can also be
started manually via GitHub Actions. It verifies that the debug APK installs,
that the launcher activity starts, survives portrait/landscape transitions,
and can be relaunched without the process disappearing.

This is a narrow emulator smoke test only. It does **not** prove DNS forwarding,
VPN consent/foreground service behavior, per-app exclusions, encrypted DNS
compatibility, display-cutout layout or battery consumption. All cells in the
physical-device matrix above remain **Pending** until tested and recorded on
real devices. Do not treat an emulator green check as release approval.

## AdBlocker 1.7.1 device acceptance (pending)

The unit tests in 1.7.1 cover isolated staging files, atomic cache replacement,
clock rollback handling and DNS64 address validation. They **do not** prove
mobile-network behavior. Record the phone model, Android version, Wi-Fi/cellular
network, time, and sanitized logs for each of the following before publishing:

- [ ] Start two close-together manual/source refreshes; verify both complete
      without a truncated shared temporary file or losing the last valid cache.
- [ ] Test offline startup and a deliberately invalid/oversized list; confirm
      the previously validated cache is still used.
- [ ] Set the phone clock backwards across a cache refresh, return to the
      correct time, and verify a remote refresh is attempted when online.
- [ ] Test IPv6-only/DNS64/NAT64 cellular networking with a legitimate public
      list host; confirm private-address translations fail closed.
- [ ] Switch Wi-Fi to cellular and back during a list refresh and during VPN
      filtering. Check DNS/DoH and resume/recovery behavior.
- [ ] Test TLS hostname mismatch, expired certificate, HTTP redirects and
      local/reserved DNS answers with a controlled test endpoint.
- [ ] Validate the existing application's signed upgrade from versionCode 9
      to 10, keeping package identity and user settings. **Do not change**
      `com.example.adblocker` without an explicit migration/distribution plan.
- [ ] Record battery/memory consumption over several hours of idle VPN use.

All items are pending physical-device sign-off. CI/emulator success does not
substitute for this checklist.

## AdBlocker 1.9.6: physical-device IPv4 UDP checksum checks

- [ ] Confirm on physical phones that normal DNS works for apps sending
      IPv4 UDP with a zero checksum (legal under RFC 768) or a valid one.
- [ ] In a controlled test, inject a nonzero incorrect IPv4 UDP checksum and
      verify the packet is discarded without breaking subsequent valid DNS.
- [ ] Confirm IPv4 options, odd-length DNS payloads, IPv6 DNS, protected DoH
      and TCP fallback continue working as before.
- [ ] Observe real-device Android 8-16 behavior before enabling production
      release signing. The automated JVM cases do not replace device QA.
