# AdBlocker

AdBlocker is a DNS-based Android ad and tracker blocker using Java and Android VpnService. It does not require root.

## Features

- Start/stop DNS protection and view a live session dashboard.
- Search or filter up to 512 recent DNS events, stored in RAM only.
- Choose StevenBlack or optional AdAway sources and maintain custom allow/block rules.
- Exclude selected apps from VPN (requires VPN restart).
- Arabic and English interfaces.

## Development

The project builds with Java 17, Gradle 8.13, Android SDK 36. Install Gradle 8.13 or open it in a compatible Android Studio; this repository currently does not commit a Gradle wrapper JAR or launcher scripts.

Run:

    gradle --no-daemon lintDebug testDebugUnitTest assembleDebug
    adb install -r app/build/outputs/apk/debug/app-debug.apk

The Android CI workflow builds a debug APK and SHA-256 checksum and uploads lint/test reports on every PR and push to main. Download the artifact from the GitHub Actions run.

## Signed release

The separate **Signed Release APK (manual)** workflow is available only on main. It requires an owner-provided keystore and four GitHub Actions secrets, verifies the APK signature, and uploads the signed APK and SHA-256 checksum. It never publishes an external release automatically. No signing key or password is stored in this repo.

See [Release guide](docs/RELEASE.md) for setup, and [Device testing](docs/DEVICE_TEST_MATRIX.md) for acceptance criteria.

## Technical limitations

This implementation filters supported DNS-over-UDP and bounded DNS-over-TCP queries addressed to the app's virtual IPv4/IPv6 resolvers. Client DNS/TCP supports one outstanding question per flow with bounded retransmissions, but not pipelining or arbitrary TCP forwarding. DNS-over-HTTPS, DNS-over-TLS, and encrypted DNS inside other apps are not intercepted. It is not a complete network firewall. Successful APK compilation does not establish real-world VPN reliability.

## Phase 5: DNS transport hardening

The TUN reader now blocks rather than spins. IPv4 and IPv6 DNS-over-UDP use
synthetic VPN resolvers. Network callbacks help recover upstream DNS after
connection changes. Truncated UDP answers can be retried over TCP to the
upstream DNS server; limited client-originated DNS-over-TCP is supported,
but DoH and DoT remain outside this DNS-only tunnel's scope.
See docs/VPN_STABILITY.md for scope and device validation.


## DNS/TCP support

DNS questions sent by applications over plaintext TCP/53 to the VPN's virtual
IPv4/IPv6 DNS servers are now handled by a **bounded DNS-specific TCP endpoint**.
It supports a single in-flight DNS query per session, segmented replies and
limited retransmission. Pipelining, out-of-order TCP segment reassembly, DoH,
DoT and general TCP connections are not supported. Device validation is pending.
