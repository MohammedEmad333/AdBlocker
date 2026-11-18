# AdBlocker 1.9.6: IPv4 UDP DNS checksum validation

**Code-only maintenance candidate. Not signed, published or validated on
physical devices.**

## Changes

- Validate the UDP checksum for incoming IPv4 DNS query packets whenever
  the sender provides a nonzero checksum. Reject packets with corrupted
  DNS bytes, source/destination IP addresses, UDP headers or checksum.
- Preserve IPv4 compatibility: a zero UDP checksum is allowed under RFC 768.
  IPv6 continues to require and validate a nonzero UDP checksum.
- Check exactly the UDP length declared in the header. IP padding and
  unused bytes from a larger TUN buffer do not affect checksum validation.
- Unit tests cover valid/invalid checksums, optional IPv4 zero checksum,
  pseudo-header changes, odd-length UDP payloads, IPv4 options and bounds.
- Bump versionName to 1.9.6 and versionCode to 18; keep the existing
  `com.example.adblocker` application ID and all user data formats.

## Release gates

- Android CI: Lint, Java/JVM tests and debug APK build must pass.
- Android 16 emulator launch/rotation smoke must pass.
- Physical Android 8-16 VPN, DNS/DoH, Wi-Fi/cellular handoff, signing
  and upgrade validation still pending in issue #64.
- Do not equate emulator success with hardware VPN validation, and
  do not publish an unsigned/debug APK as a production release.
