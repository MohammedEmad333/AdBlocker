# AdBlocker physical-device QA evidence template

Use one copy of this template per device and test session. The current development candidate is version 1.9.7, versionCode 19, application ID com.example.adblocker.

## Privacy rules

- Use controlled test domains and synthetic fixtures. Do not record ordinary browsing history.
- Never attach raw logcat, unreviewed dumpsys output, packet captures, DNS event history, full settings backups, exported profiles, keystores, passwords, tokens, or private keys.
- Evidence should contain test case ID, pass/fail, device/API metadata, and a short redacted observation only.
- Do not include device serial numbers, IMEI, phone numbers, account identifiers, IP addresses, Wi-Fi names, or actual visited domains.
- If a diagnostic excerpt is essential, review it locally and remove hostnames, addresses, credentials, URLs, and other identifying data before sharing.
- Keep real settings exports and signing material on the device or in the owner's secure storage. Never upload them to a public issue or pull request.

## Session metadata

| Field | Value |
| --- | --- |
| Test date/time (UTC) | |
| Tester alias (optional) | |
| Manufacturer | |
| Device model | |
| Android release | |
| Android API level | |
| Security patch | |
| AdBlocker versionName | |
| AdBlocker versionCode | |
| APK SHA-256 | |
| Build type (debug/signed release) | |
| Network type (Wi-Fi/cellular) | |
| Network capabilities (IPv4/IPv6/DNS64/NAT64) | |
| Metered network (yes/no) | |
| Concurrent VPN present (yes/no) | |

Run the read-only preflight before testing:

    bash scripts/device-qa-preflight.sh

The preflight deliberately omits device identifiers, network identifiers, DNS activity, and logs. It is not a VPN or DNS health test.

## Result legend

Use only these statuses:

- PASS: the expected behavior was observed.
- FAIL: the behavior differed from the expected result.
- BLOCKED: the test could not be run because a prerequisite was unavailable.
- NOT RUN: the test has not been attempted.

For every row, record a short observation and a sanitized evidence reference. Do not paste raw logs.

## System and VPN lifecycle

| Test ID | Scenario | Expected result | Status | Redacted observation / evidence |
| --- | --- | --- | --- | --- |
| SYS-01 | Install candidate and grant VPN consent | Android consent is shown; protection starts only after consent | NOT RUN | |
| SYS-02 | Start, stop, revoke consent, and start again | Service state follows the user's action; no stuck foreground service | NOT RUN | |
| SYS-03 | Background notification, screen-off, Doze, and reboot | Service and notification behavior remain consistent with the design | NOT RUN | |
| SYS-04 | Cancel startup during resolver probe or blocklist refresh | Stop completes safely and no unwanted tunnel remains | NOT RUN | |
| SYS-05 | Run with another VPN product installed | Conflicts are documented; do not claim both VPNs can run together | NOT RUN | |

## DNS transport and filtering

| Test ID | Scenario | Expected result | Status | Redacted observation / evidence |
| --- | --- | --- | --- | --- |
| DNS-UDP4-01 | IPv4 UDP DNS with zero checksum | Legal zero-checksum traffic continues to work | NOT RUN | |
| DNS-UDP4-02 | IPv4 UDP DNS with a valid nonzero checksum | Valid packets are accepted | NOT RUN | |
| DNS-UDP4-03 | Controlled invalid nonzero IPv4 UDP checksum | Invalid packet is rejected; later valid DNS still works | NOT RUN | |
| DNS-UDP6-01 | IPv6 UDP DNS | Valid IPv6 checksums and replies work | NOT RUN | |
| DNS-TCP4-01 | DNS/TCP over IPv4 with split request and segmented response | Supported in-order single-query stream completes | NOT RUN | |
| DNS-TCP6-01 | DNS/TCP over IPv6 | Supported in-order single-query stream completes | NOT RUN | |
| DNS-TCP-02 | Retransmission, late SYN/RST, invalid ACK, and disconnect | Invalid or stale segments do not corrupt another session | NOT RUN | |
| DNS-TCP-03 | Pipelining or out-of-order data | Unsupported behavior is rejected safely; no general TCP support is implied | NOT RUN | |
| DNS-RULE-01 | Known controlled allowed and blocked test names | Decisions match local rules and enabled sources | NOT RUN | |
| DNS-RULE-02 | Allowlist versus custom/downloaded block rules | Explicit allowlist precedence is preserved | NOT RUN | |
| DNS-RESP-01 | Response with mismatched transaction/question/recursion intent | Mismatched upstream response is not accepted | NOT RUN | |

## Encrypted DNS and network changes

| Test ID | Scenario | Expected result | Status | Redacted observation / evidence |
| --- | --- | --- | --- | --- |
| DOH-01 | Each supported DoH provider | Selected provider resolves through HTTPS | NOT RUN | |
| DOH-02 | Controlled TLS or provider failure | No silent plaintext fallback or unapproved provider switch | NOT RUN | |
| NET-01 | Wi-Fi to cellular and back during active filtering | Resolver recovery follows the active network | NOT RUN | |
| NET-02 | Offline startup, loss of connectivity, and reconnect | Cached rules remain safe; recovery is bounded and understandable | NOT RUN | |
| NET-03 | IPv6-only, DNS64/NAT64, and captive portal | Supported behavior is verified; unsupported network cases are recorded | NOT RUN | |
| NET-04 | Blocklist refresh during network handoff | Previous validated cache remains intact if refresh fails | NOT RUN | |

## Rules, schedules, pauses, and settings

| Test ID | Scenario | Expected result | Status | Redacted observation / evidence |
| --- | --- | --- | --- | --- |
| RULE-03 | Multiple custom HTTPS sources and independent toggles | Each source follows its enabled state and validated cache | NOT RUN | |
| RULE-04 | App exclusion change followed by VPN restart | Excluded app bypasses DNS filtering; other apps remain filtered | NOT RUN | |
| TIME-01 | 5, 15, and 30-minute protection pause | Protection resumes at the selected monotonic deadline | NOT RUN | |
| TIME-02 | 15 and 60-minute temporary exact-domain allowance | Only the exact allowed domain is exempted until expiry | NOT RUN | |
| JOB-01 | Scheduled daily/weekly refresh, reboot, Doze, metered/unmetered network | Job constraints and cancellation behave as designed | NOT RUN | |
| DATA-01 | Valid settings export/import using Android document picker | Supported settings restore without exposing the exported file | NOT RUN | |
| DATA-02 | Malformed or oversized settings import | Invalid input is rejected without damaging existing settings | NOT RUN | |
| DATA-03 | Imported DoH provider and app exclusions | Changes apply after the documented VPN restart | NOT RUN | |

## Privacy, localization, accessibility, and performance

| Test ID | Scenario | Expected result | Status | Redacted observation / evidence |
| --- | --- | --- | --- | --- |
| PRIV-01 | DNS activity recording disabled | Existing in-memory history clears and future hostnames are not recorded | NOT RUN | |
| PRIV-02 | VPN stop/restart and app process exit | Session-only DNS history and RAM-only trends reset as documented | NOT RUN | |
| PRIV-03 | Sanitized diagnostics review | No DNS names, IP addresses, secrets, or profile contents are exported by default | NOT RUN | |
| UI-01 | Arabic RTL and English across dashboard, settings, and activity | Text, navigation, and controls remain readable without clipping | NOT RUN | |
| UI-02 | Large font, TalkBack, dark theme, and touch targets | Controls remain discoverable and usable | NOT RUN | |
| UI-03 | Android 16 edge-to-edge and display cutouts | Important content and controls are not obscured | NOT RUN | |
| PERF-01 | DNS bursts and large rule sets | Memory and queue bounds hold; app remains responsive | NOT RUN | |
| PERF-02 | Extended idle VPN session (target: 24 hours) | No crash, leak, unexpected history persistence, or unbounded battery use | NOT RUN | |
| PERF-03 | Repeated VPN start/stop and network switching | No stuck workers, stale cache replacement, or resource leak | NOT RUN | |

## Signed upgrade gate

Perform this only with an owner-controlled, release-key-signed APK and a securely backed-up key. Do not put signing material in this report.

| Test ID | Scenario | Expected result | Status | Redacted observation / evidence |
| --- | --- | --- | --- | --- |
| REL-01 | Upgrade a previously distributed signed version in place | Same application ID and signing key; install succeeds and user settings remain | NOT RUN | |
| REL-02 | Verify installed candidate signature and SHA-256 | Signature and checksum match the owner-generated release artifact | NOT RUN | |
| REL-03 | Rollback/recovery procedure | Recovery steps are documented and tested without publishing an unapproved build | NOT RUN | |

## Defect summary

| Test ID | Severity (critical/high/medium/low) | Short sanitized description | Reproducible (yes/no) | Regression test / issue |
| --- | --- | --- | --- | --- |
| | | | | |

## Sign-off

- Critical test rows passed: NOT CONFIRMED
- Physical-device reviewer:
- Release owner approval: NOT GRANTED
- Notes: Automated CI and emulator smoke do not replace this sign-off.
