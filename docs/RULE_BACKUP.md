# Rule import and export (AdBlocker 1.5)

Open Settings and choose **Export allowed and blocked domains** to save a
`text/plain` backup using the Android document picker. Choose **Import
domain rules from a file** to validate and preview counts, then explicitly
confirm replacement of the existing custom allow and block lists.

The portable UTF-8 format starts with `ADBLOCKER-RULES-V1` and uses
`ALLOW<TAB>domain.example` or `BLOCK<TAB>domain.example` per line.
Records are sorted on export. Import rejects unknown formats, malformed
domains, duplicate conflicting allow/block entries and files larger than
128 KiB or with over 2,000 rules; no partial replacement is made.

These files contain custom DNS domain rules only. DNS history, passwords,
app exclusions, source subscriptions, and VPN settings are not exported.
Android system document providers control where exported files are stored,
so users should treat backups as private data and share them intentionally.
Importing new rules while VPN is active requests a policy reload.

## Remaining validation

- Android 16 emulator CI, Android 8-16 device document pickers.
- Empty files, malformed text, internationalized domains, denied providers,
  inaccessible removable storage, cancellation and VPN-on refresh.
- No public release until signed-upgrade and DNS filtering device tests pass.
