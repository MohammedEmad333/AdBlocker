# Temporary domain allowance (1.6.0)

Blocked entries in the activity log offer **Allow for 15 minutes** and **Allow
for 1 hour**. These create exact-host exceptions, never wildcard or
parent-domain allowances. Custom permanent rules are unchanged.

Expiry is checked against the current clock on each DNS query, so no
background alarm or network connection is necessary. The service refreshes
the in-memory allowance snapshot when a rule is added. Maximum active
exceptions: 100. Entries beyond one hour from the current time, expired
records and malformed records are ignored after process restart.

This feature depends on the user-controlled device clock. Large manual clock
changes can alter remaining duration. A production release should verify
that removing/canceling an exception and unexpected clock changes are handled
as expected. Android UI tests for Wi-Fi, cellular, app restart, expiration,
blocked domain with child subdomains, and simultaneous VPN policy refreshes
remain required.
