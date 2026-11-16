# AdBlocker 1.8 dashboard clarity

This is a small, backward-compatible improvement to the existing screen.

- Adds last-successful blocklist download freshness to the home dashboard.
- Shows a privacy-specific state instead of an empty DNS history when recording
  is off. DNS filtering and aggregate counters still work.
- Unifies block-rate calculation with bounded testable presentation logic.
- Existing live totals, loaded-domain counts, diagnostics and settings remain.

"Recently downloaded" means the app has recorded a successful download in the
last 24 hours. It does not claim that every enabled source is currently fresh
or online. Hardware DNS leak and VPN reliability tests are still separate.
