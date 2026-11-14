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
