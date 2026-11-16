# AdBlocker 1.9: local session DNS decision trends

The main dashboard displays four bounded 15-minute DNS decision buckets:
blocked by current rules versus passed through to DNS forwarding.
These are **decisions**, not confirmed successful upstream DNS answers.
The buckets are aligned to elapsed monotonic time, so their "minutes ago"
labels describe approximate recent intervals rather than precise rolling
15-minute windows.

- RAM-only, session-only, no domain names, apps, IPs or DNS history stored.
- Works with DNS history recording disabled. It is separate from the
  optional RAM-only list of individual DNS requests.
- Uses a fixed 4-bucket integer array; updates take constant time and do
  not grow with session duration.
- Clears when the VPN session starts; old buckets expire automatically.
- This is not a network-wide count or proof of encrypted DNS coverage.

Real-device checks: high traffic, screen-off time, rotation, clock changes,
VPN stop/restart, TCP/UDP DNS and Arabic RTL rendering before public release.
