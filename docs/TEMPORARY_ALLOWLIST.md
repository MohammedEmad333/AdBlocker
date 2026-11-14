# Temporary DNS allow rules (AdBlocker 1.6)

While VPN protection is running, open DNS Activity and choose a blocked domain.
You can allow **that exact domain** for 15 minutes or one hour. Permanent
rules remain unchanged. Subdomains and parent domains are **not** implicitly
allowed. This is useful for a site that needs a temporarily blocked hostname
without changing long-lived privacy settings.

The exception table lives only in the app process memory and is limited to
128 domain entries. Expiration uses Android monotonic elapsed time so wall
clock edits cannot extend an exception. Expired entries are ignored immediately
on subsequent DNS decisions and removed lazily. Stopping or restarting the
VPN clears all temporary exceptions, as does app process termination.

Temporary access applies to DNS requests that are actually handled by this
VPN and does not guarantee application reachability, unblock IP traffic or
intercept encrypted DNS requests from other apps.

## Validation still required

- Run Java unit tests for expiry boundaries, renewal, exact domain matching,
  input validation, capacity and session reset.
- Verify expiry with live DNS lookups on physical Android devices, including
  after screen lock and Wi-Fi/cellular transitions.
- Check the user-facing Arabic/English buttons and accessibility at large
  system font sizes.
- Do not count Android emulator launcher smoke tests as proof of VPN routing.
