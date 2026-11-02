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
- DNS-over-HTTPS and DNS-over-TLS are not intercepted.
  App-originated TCP/53 to the synthetic VPN resolvers is handled by a
  purpose-built bounded DNS/TCP stream endpoint. This is not a general TCP stack
  and does not intercept encrypted DNS. Some apps bypass the blocker.
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


## Dashboard, activity log and localization (phase 3)

- The main dashboard exposes current-session blocked, allowed and total DNS
  query counts, blocking percentage, number of cached filter domains, resolver
  and timeout/error diagnostics.
- The DNS activity screen searches and filters recent hostnames by decision,
  with local timestamps. At most 512 events are held **in memory only**;
  the UI shows up to 150 matching events and retains no persistent log.
- Log history is cleared at every VPN session start and when the Android app
  process exits. Dashboard counters are current-session only, not historical
  global statistics.
- Settings includes an English/Arabic/system language selector backed by
  AndroidX AppCompat app locales. Resources use RTL-friendly layouts.
- Notification text is translated using the current service context locale
  (which may reflect the system locale on older Android releases).

### Phase 3 acceptance tests

1. Start protection and check that blocked + allowed equals total DNS queries.
2. Open DNS activity, search mixed-case hostnames, and use all/blocked/allowed
   filters; check newest-first order and timestamps.
3. Restart protection and confirm the previous session log is gone.
4. Change language between Arabic, English and system; inspect home, log,
   settings and navigation, including RTL and long text.
5. With large DNS bursts, confirm the activity screen stays responsive and
   the event history never exceeds 512 entries.


## Release readiness (phase 4)

CI now runs Android lint, unit tests, APK ZIP/metadata verification and SHA-256 generation. Downloaded blocklists are capped at 16 MiB; app backup and cleartext application traffic are disabled. A separate manual release workflow requires owner-controlled signing secrets and checks APK signatures. See [Release guide](RELEASE.md) and [Device matrix](DEVICE_TEST_MATRIX.md). No real-device verification is implied by CI success.
 

## Fifth batch: network transport hardening

- Use blocking TUN reads to prevent a hot loop; closing TUN unblocks the read.
- Support DNS-over-UDP for virtual IPv4 10.0.0.1 and IPv6 fd00::1, including
  a valid UDP checksum over IPv6 and rejection of invalid headers/checksums.
- React to network changes using callbacks and reprobe upstream DNS on a
  background worker. Update the VPN underlying networks on success.
- Retry truncated upstream DNS/UDP replies using TCP/53, with bounded response
  size and timeout. This was the upstream-only TCP implementation in phase 5.
- Reject oversized replies beyond the 1500-byte tunnel MTU with SERVFAIL.
- Unit tests cover IPv6 validation, responses, malformed packets, random data
  and TCP framing; physical-device tests are still needed.

Test on dual-stack Wi-Fi and cellular, verify A/AAAA queries and blocking
decisions, switch between networks, and observe restart/stop reliability.
Do not claim encrypted DNS or complete TCP interception.


## Sixth batch: TCP/53 DNS traffic sent by apps

- The VPN now recognizes TCP packets to virtual IPv4 and IPv6 DNS resolvers.
  A strict packet parser checks destination, TCP port, segment bounds, fragment
  headers and TCP checksum, and generates valid IPv4/IPv6 TCP replies.
- A bounded per-connection state machine performs SYN/ACK, collects one
  length-prefixed DNS question, acknowledges bytes and sends blocked NXDOMAIN
  or allowed upstream DNS replies over TCP.
- Allowed DNS/TCP queries are resolved over a protected TCP socket first; if
  the upstream refuses TCP, the service falls back to UDP only when the reply
  is complete (not truncated). This is a DNS transport fallback, not HTTPS.
- A maximum of 64 sessions, 4096 DNS query bytes and one outstanding request
  per TCP connection are supported. Response frames use conservative 512-byte payloads (below the
  IPv4 TCP default MSS),, sent one at a time after ACK, and retransmitted with a 3-retry
  maximum. Inactive connections expire in 30 seconds.
- DNS/TCP pipelining, out-of-order segment reassembly, advanced TCP options and
  general TCP proxying are **not** supported; unsupported streams are reset.
- Sessions and queued responses are cleared when the VPN stops. DNS activity
  history is still private to memory and reset with the session.

### On-device acceptance checklist

Test unencrypted DNS/TCP with a client that explicitly selects TCP port 53
against the local VPN resolver, on both IPv4 and IPv6. Confirm allow/block
precedence, split TCP DNS frames, long replies spanning multiple TCP segments,
disconnect/reconnect, stop while a lookup is pending, and network switching.
Monitor stability and memory under repeated connection attempts. CI tests do
not replace packet captures or Android device tests.

## Phase 7 — DNS/TCP session hardening

- In-order TCP sequence and acknowledgement checks now precede accepting application data. A stale or future ACK cannot advance the DNS request buffer.
- TCP resets must carry the expected client sequence. Unrelated or delayed reset packets cannot remove the active session.
- Duplicate SYNs during a handshake retransmit SYN/ACK, while late SYNs cannot overwrite established TCP sessions.
- Out-of-order FINs are acknowledged without closing the connection. Invalid SYN/FIN and SYN/RST combinations and port-zero TCP packets are rejected.
- Upstream DNS replies are accepted only when the transaction ID, domain name (case-insensitive), QTYPE and QCLASS match the original request, avoiding mismatched data under the same transaction ID.
- Pure-Java regression tests cover stale SYN, unexpected RST, invalid ACK, malformed flags and randomized packet input.

**Remaining limits:** this is intentionally a narrow DNS/TCP implementation, not a general TCP stack. Real-device verification of reordering, retransmits, Wi-Fi/cellular handoff and VPN stop/restart is still required. DNS-over-HTTPS/TLS remains outside scope.
