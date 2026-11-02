# Android field validation checklist

CI checks lint, Java unit tests and build artifacts. **Real VPN behavior and UI must still be verified on Android devices.** Record model, Android version, network and sanitized evidence.

| Scenario | Android 8/9 | Android 13 | Android 14/15 |
| --- | --- | --- | --- |
| Install and grant VPN consent | Pending | Pending | Pending |
| Start, stop, cancel during initialization | Pending | Pending | Pending |
| Known blocked and allowed DNS domains | Pending | Pending | Pending |
| Custom allowlist and blocklist precedence | Pending | Pending | Pending |
| Service/notification in background | Pending | Pending | Pending |
| Wi-Fi to cellular handoff | Pending | Pending | Pending |
| Offline restart with stale cache | Pending | Pending | Pending |
| Manual list refresh and malformed response | Pending | Pending | Pending |
| Per-app exclusions after VPN restart | Pending | Pending | Pending |
| Arabic RTL, English, system language | Pending | Pending | Pending |
| DNS activity search and bounded history | Pending | Pending | Pending |
| DNS burst, memory usage and battery | Pending | Pending | Pending |
| App-originated DNS/TCP over IPv4 and IPv6 | Pending | Pending | Pending |
| Split DNS/TCP request, large response, retransmission | Pending | Pending | Pending |
| TCP late SYN/RST and invalid ACK handling | Pending | Pending | Pending |
| Release signing/upgrade validation | Pending | Pending | Pending |

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
