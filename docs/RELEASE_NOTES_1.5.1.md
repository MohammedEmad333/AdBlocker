# AdBlocker 1.5.1 - stability and security candidate

**Status: source-code candidate only. No signed APK or public release.**

## Included after approved PR merges

- Fix validation of small custom HTTPS blocklists: source-specific thresholds
  instead of treating GitHub-hosted custom sources as built-in lists; preserve
  1,000-domain / 1-KiB requirements for curated lists.
- Reject truncated DNS-over-HTTPS responses (TC=1), malformed or mismatched
  questions and transaction IDs, without plaintext DNS fallback.
- Keep the existing network handoff recovery, rule import/export, local
  blocking policy, and optional DoH configuration unchanged.

## Required release evidence

1. Android Lint, JUnit suite, APK integrity and API 36 emulator smoke must
   pass on the final combined main commit.
2. Validate VPN consent, startup and stop, blocked/allowed queries, UDP/TCP,
   Wi-Fi/cellular handoff and airplane mode on physical Android devices.
3. Verify tiny ten-domain custom HTTPS lists, list caches, offline startup,
   invalid certificates, redirects and oversized/malformed downloads.
4. Verify real DoH responses and failures on actual networks: captive portals,
   no plaintext downgrade, truncated payloads and upstream unavailability.
5. Evaluate potential hostname DNS rebinding during custom HTTPS downloads.
   The current pre-connection DNS check does not pin the connected peer IP,
   so it must not be considered complete SSRF protection.
6. Verify signed installation/upgrade and confirm a permanent application ID
   before any public or Play Store release.

The app remains a DNS-only VPN. Enabling DoH encrypts only DNS messages handled
by AdBlocker, not encrypted DNS initiated directly by other apps.
