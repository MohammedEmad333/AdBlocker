# AdBlocker 1.8: temporary DNS blocking pause

The dashboard offers a user-confirmed 5, 15 or 30-minute pause while the VPN
is running. This pauses only blocking, **not** DNS forwarding: applications
still route supported DNS traffic through the same DNS-only VPN and resolver.

The pause is RAM-only and uses elapsed monotonic milliseconds. Every UDP or TCP
DNS query checks its expiry, so blocking resumes on the next query after the
deadline, even if the interface is closed. The UI updates the approximate
remaining minutes while open; no exact alarm permission or background timer
is required. Restarting or stopping the VPN clears any pause.

- User allow/block rules and source choices remain unchanged.
- Existing DNS/TCP and DoH behavior stays the same.
- The dashboard visibly warns when blocking is off.
- Privacy history selection and cached DNS behavior are unchanged.
- Pause controls only appear while the VPN is active.

Before public release, validate expiry across screen-off, system-clock change,
rotation, VPN stop/revoke and connectivity handoff on actual devices.
