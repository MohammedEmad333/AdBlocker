# VPN and DNS stability

This Android app implements a **DNS-only** VPN. It routes IPv4 DNS traffic
addressed to its synthetic resolver into the TUN device and forwards allowed
UDP queries over the underlying network.

## Behavior and limits

- Malformed, fragmented, non-IPv4, and non-UDP packets are ignored.
- Blocking responses are NXDOMAIN. If the forwarding queue is full, a valid
  DNS question receives SERVFAIL so the client can retry rather than wait.
- A maximum of 16 upstream requests execute concurrently; 64 more may wait.
- Three consecutive upstream forwarding failures trigger a guarded resolver
  rediscovery using the device's available non-VPN networks.
- Cancellation is checked between download, resolver probe, and TUN setup.
- Downloads are validated before replacing the cached blocklist; stale cache
  remains available offline.
- DNS-over-HTTPS, DNS-over-TLS and DNS-over-TCP aren't intercepted. Some apps
  and sites can therefore bypass the blocker.
- This is not a full network VPN, a comprehensive tracker firewall, or a
  guarantee that all advertisements will disappear.

## Validation

GitHub Actions builds the APK and executes `testDebugUnitTest` on every pull
request targeting main and push to main. DNS packet unit tests cover valid
queries, malformed input, the reply envelope and SERVFAIL responses.

Manual device tests are still required before declaring production readiness:

1. Give VPN consent and start protection; check blocked and allowed domains.
2. Stop during the blocklist download/probe; verify it stays off.
3. Switch between Wi-Fi and cellular data; verify DNS recovers.
4. Disconnect and reconnect internet; verify normal browsing resumes.
5. Generate bursts of DNS queries; verify the app remains responsive.
6. Check Android 8/13/14/15 and normal service notification behavior.

The repository currently uses the system Gradle executable in CI. To run
locally without a checked-in Gradle wrapper, install Gradle 8.6 and use
`gradle testDebugUnitTest assembleDebug`.


## Filter controls (phase 2)

- Settings lets users choose StevenBlack (enabled by default) and/or AdAway
  (optional, disabled by default), each with a separate validated cached file.
- A local allowlist takes precedence over custom blocks and downloaded lists.
  Rules match exact domains and their subdomains, never partial suffixes.
  Invalid hosts, URLs, wildcard rules, and IP literals are rejected.
- Editing sources or custom rules requests an in-process reload while the VPN
  runs. Enabled sources refresh automatically when the cache is older than
  24 hours; the service checks every 30 minutes.
- Manual forced updates are available, including while VPN is stopped.
  If downloading fails, the previous cache remains intact.
- Per-app exclusions use Android's VPN disallowed-app list. These changes
  require stopping and starting the VPN so the tunnel can be rebuilt.
- Only launchable applications are shown in the exclusion selector. The
  app intentionally does not request QUERY_ALL_PACKAGES.
- These settings are private to the local Android installation and are
  neither synced nor uploaded to a backend.

### Phase 2 acceptance tests

1. Add a domain to custom blocks, check it is blocked and its subdomains too.
2. Allow that domain, confirm the allowlist wins over the downloaded sources.
3. Toggle source selection offline, confirm stale cache loads when available.
4. Force a refresh; verify that invalid/short downloads never replace cache.
5. Exclude a launchable app, stop/start VPN, and verify that app bypasses DNS
   filtering without disabling filtering in other apps.
6. Leave VPN running >24 hours to verify the periodic refresh behaves correctly.
