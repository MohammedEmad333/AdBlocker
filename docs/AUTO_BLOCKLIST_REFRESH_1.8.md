# AdBlocker 1.8: scheduled HTTPS blocklist updates

- Opt-in only; off by default. The Settings page offers daily or weekly
  background refreshes and an unmetered-network requirement (Wi-Fi, Ethernet
  or other unmetered connection, not strictly Wi-Fi).
- Android JobScheduler persists the job across a reboot without launching the
  VPN. Its run time is approximate and subject to battery and network constraints.
  This is *not* an exact clock-time scheduler and needs Android's background
  execution tests on physical devices.
- If VPN protection is already running, the job exits. The active VPN's existing
  30-minute scheduler handles stale list updates.
- When VPN is off, the job refreshes enabled HTTPS sources while preserving old
  caches on any download, size, parsing or TLS failure. The updated lists will
  be loaded when protection is next started.
- No new remote analytics, cloud storage, DNS logging or automatic VPN start.

## QA before public release

- Verify opt-in/opt-out, frequency changes, unmetered restrictions, periodic
  rescheduling, reboot persistence, battery saver and Doze on real devices.
- Validate app upgrades, force-stopping and network losses mid-download.
- Confirm disabled sources are not downloaded and cached list results remain
  available while offline.
