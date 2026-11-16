# AdBlocker 1.9.1: background refresh cancellation hardening

## Problem

Android can call `JobService.onStopJob` when an app's JobScheduler network or
battery constraints are no longer satisfied, or when the system reclaims
resources. Previously the worker thread was interrupted but an HTTPS request
could finish later, and its result could still replace the last validated
cache. This is particularly undesirable if the network changes while
downloading or if the VPN starts during a background refresh.

## Change

The JobService passes a live, non-persisted cancellation predicate into
`BlocklistManager`, the HTTPS streaming transport and cache promotion.
The predicate fails if the job is stopped, the preference is disabled or
the VPN is starting/running. The same guard also rejects thread interruption.

- The download checks before connection, before/after each streaming read,
  periodically while parsing, and immediately before the atomic cache move.
- A cancelled download raises `InterruptedIOException`. The temp file is
  deleted and any previously validated cache remains in place.
- No change to manual/active-VPN refresh behavior or HTTPS/TLS DNS checks.
- The network read itself can still block up to its configured timeout; the
  cancellation checks stop any subsequent application of stale results.
- The JobService does not record completion for stopped jobs.

## Release QA

CI unit tests cover cancellation at the cache boundary, thread interruption,
streaming read cancellation and response size limits. Real Android tests
must still validate JobScheduler stop/reschedule semantics, screen-off/Doze,
metered and unmetered handoffs and stopping the job mid-download.
The physical-device release gate in issue #64 remains open.
