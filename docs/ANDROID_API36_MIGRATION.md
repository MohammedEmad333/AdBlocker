# Android API 36 build migration

This project compiles and targets API 36 with Android Gradle Plugin 8.13.2,
Gradle 8.13, and JDK 17. The minimum supported API remains 26 and the app
package ID and signing configuration are unchanged.

## Required verification

- GitHub CI: lintDebug, testDebugUnitTest, assembleDebug, APK integrity.
- Manual release workflow: validate signing secrets only from the repository
  owner; never store or commit signing credentials.
- Physical Android 16 tests: VPN consent and foreground-service notification,
  start/stop and background operation, system back navigation, notification
  permission, and edge-to-edge/insets for main and settings screens.
- Android 8/9, 13, 14/15 regression tests: VPN startup/offline startup, Wi-Fi
  and cellular handoff, DNS over UDP/TCP, app exclusion, Arabic UI and restart.

## Known remaining limits

- CI compilation is not evidence of behavioral compatibility on Android 16.
- The repository currently uses an external Gradle 8.13 installation rather
  than shipping Gradle Wrapper scripts/JAR. No wrapper binaries are committed.
- This PR does not change DNS routing, encryption support, Android UI insets,
  application ID, or release version. Release requires a separate signoff after
  running the device test matrix.
