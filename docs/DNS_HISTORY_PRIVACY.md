# Optional DNS activity history (AdBlocker 1.7)

In Settings, **Record recent DNS activity in memory** is enabled by default to
preserve the existing recent-activity experience. Switching it off immediately
erases the in-RAM DNS event list and prevents future history entries.
The preference survives VPN restarts, but DNS history never persists to disk.

Turning recording back on captures **only new** DNS events. The RAM-only
history retains at most 512 events and clears on every VPN session reset.
Local domain filtering, protection status and aggregate session counters
continue to work while history is disabled. Top blocked-domain insights,
recent activity and the DNS-log quick-rule buttons have no data while history
is off.

The setting does not disable Android/OS network logging or logs held by
external DNS providers. Real-device verification must check toggles during
active DNS traffic, rotation and VPN restart, as well as privacy labels.
