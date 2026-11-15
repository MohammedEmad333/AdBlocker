# Optional HTTPS blocklist source (AdBlocker 1.5)

The Settings screen permits **one** extra HTTPS hosts/plain-domain source.
Paste its URL, save, then use **Download / refresh lists**. The existing
StevenBlack and AdAway toggles remain independent. The custom URL is
stored on-device and each URL has its own SHA-256-derived cache filename.

Security boundaries:
- HTTPS with a public DNS host only, no IP literal, username/password, custom
  port, query, fragment, URL encoding, or HTTP redirects.
- An initial DNS check rejects loopback, link-local, site-local, multicast,
  IPv6 ULA and selected reserved address ranges. Do not use an untrusted
  list host; hostile DNS changes can occur between validation and connection.
- Downloads are limited to 16 MiB and 10 normalized domains, with connection
  and read timeouts. Invalid updates retain an existing cached copy.
- The app's domain allowlist still overrides downloaded blocklists.
- Custom lists may contain false positives or lead to site breakage. Only add
  a source you trust and can review. No arbitrary scripts are executed.

Device checks: downloading over cellular/Wi-Fi, offline cache, malformed and
oversized lists, HTTP redirects, invalid certificate, DNS changes, source
removal and in-session policy refresh. Real-device validation is required
before a public release.

## AdBlocker 1.7: manage up to three sources

The HTTPS list manager accepts up to three custom URLs, each with its own
enable/disable control and private SHA-256-based cache filename. Only enabled
sources contribute to live blocking. The app displays the last cached download
time and cached domain count, which may be stale until the next refresh.

Existing users' single saved HTTPS URL is transparently migrated on the first
catalog edit. Removing one source also attempts to delete its local cache.
Disabled sources keep their cache for offline reactivation. Source links are
validated with the existing strict HTTPS URL rules and downloads retain their
size, TLS and redirect limits.

Do not mistake the host's preflight DNS check for comprehensive DNS-rebinding
protection. A peer-IP pinning or equivalent verified connection strategy
remains necessary before public release. All three sources should be tested
with offline/invalid certificate conditions and partial refresh failures on
real devices.
