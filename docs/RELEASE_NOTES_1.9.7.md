# AdBlocker 1.9.7: CI resilience development candidate

**Code-only candidate. No public or signed APK release.**

- Restore Gradle dependency caching across CI runs, with main-branch cache
  writes and read-only PR/emulator jobs.
- Add bounded, HTTP-specific dependency download retries, excluding
  compilation, lint and test failures.
- Self-test retry classification with offline mock-Gradle scenarios.
- Check APK package, versionCode, versionName, ZIP integrity and SHA-256.
- Keep manual signed release secret handling and cache isolation intact.
- Maintain the application ID com.example.adblocker and all user settings.
- Version code 19, version name 1.9.7.

Before merging: Android CI and Android 16 emulator smoke must both pass.
After merging: verify main-branch CI. Issue #64's physical-device VPN/DoH,
network, backup and signed-upgrade gates remain required before publication.
