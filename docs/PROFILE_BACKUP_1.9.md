# AdBlocker 1.9: portable settings backup

Versioned plain UTF-8 format: ADBLOCKER-PROFILE-V1.

- Export, via Android's document picker, user allow/block rules, excluded app
  package IDs, builtin list toggles, up to three custom HTTPS source URLs with
  enabled state, encrypted DNS setting/provider, local DNS-history recording
  preference, and the opt-in automatic refresh settings.
- Import first validates the **entire** file: strict UTF-8, supported version,
  byte/rule/source limits, canonical domains, valid HTTPS hosts, allowed DNS
  providers, valid package names, duplicate entries, conflicts and valid flags.
- A confirmation dialog describes the number of rules, excluded packages and
  HTTPS sources before replacing any settings. The resulting profile is saved
  with one SharedPreferences commit, not a series of partial writes.
- After import, a running VPN refreshes its filter lists/rules. Android VPN
  application exclusions and DNS transport still require VPN restart.
  If automatic job scheduling fails, the opt-in flag is turned back off rather
  than falsely implying the schedule is running.
- A restored history-recording opt-out takes effect immediately and clears
  any existing in-memory DNS events.
- Downloaded blocklist cache files, historical DNS queries, credentials,
  timestamps and telemetry are **never** included in the backup.
- Existing ADBLOCKER-RULES-V1 domain-only import/export stays supported as-is.

The backup **is not encrypted**. It contains domains, app identifiers and
possibly custom HTTPS list addresses. Keep it on private storage and never
upload it publicly. Deleted or no-longer-selected cache files remain local,
but are not used unless their source is re-enabled; cache cleanup requires
separate validation.

## Before publishing

Test import/export with Android storage providers, Android 8-16, Arabic/English,
rotation, canceled document flows, damaged files, unsupported file versions,
app upgrades and with a running/stopped VPN. Verify job rescheduling and that
DoH/app-exclusion changes take effect after restart. Real-device signoff
remains pending.
