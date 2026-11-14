# AdBlocker 1.5.0 - candidate notes

**Development candidate only. No public APK signed or published.**

## Included after accepted PR merges

- Import/export custom allow and block domain rules in a portable versioned
  UTF-8 format using Android document pickers. Entire imports are validated
  before replacing local rules; DNS history and app exclusions are excluded.
- Add one optional custom HTTPS hosts/plain-domain list source with bounded
  downloads, cache fallback, URL checks and controls in Settings.
- Add an optional Cloudflare DNS-over-HTTPS transport (disabled by default).
  The queries this DNS-only VPN handles are forwarded over HTTPS when enabled.
  This is *not* interception of DoH/DoT from other applications.
  The app does not silently downgrade to plaintext DNS when DoH fails.

## Privacy, compatibility and remaining release blockers

1. Verify latest merged commit with Android lint, JUnit tests, debug APK
   assembly and Android 16 emulator smoke.
2. Run real-device checks on Android 8/9, 13, 14/15 and 16 covering document
   provider import/export, rules replacement, blocked and allowed domains.
3. Test custom HTTPS downloads: offline, malformed, oversized, redirects,
   invalid TLS certificate, source removal and cached-list recovery.
4. Test DoH on physical Wi-Fi/cellular networks, captive portals,
   certificate failures, server unavailability, UDP/TCP client queries,
   network handoffs and intentional no-plaintext-fallback behavior.
5. Select a permanent application ID and verify signed upgrades prior to any
   Play Store release.

DoH uses a standard HTTPS hostname. DNS resolution needed to bootstrap that
hostname may still use the device's network DNS, so avoid describing this
option as complete DNS encryption for every system lookup.

The application remains a DNS-only VpnService. Existing real-device VPN
verification is still a release gate. Signing secrets, production deployment
and user accounts are not touched by this version metadata PR.
