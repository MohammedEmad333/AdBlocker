#!/usr/bin/env bash
# Bounded retries for transient dependency download errors, never failing tests.
set -euo pipefail

if [ "$#" -eq 0 ]; then
  echo "Usage: bash scripts/ci-gradle.sh TASK [TASK ...]" >&2
  exit 64
fi
attempt=1
max_attempts=3
attempt_log="$(mktemp)"
trap 'rm -f "$attempt_log"' EXIT

transient_dependency_error() {
  # A download failure plus a transient HTTP status must BOTH be present.
  grep -Eqi 'Could not (GET|HEAD|download|resolve)|Failed to get resource' "$attempt_log" &&
    grep -Eqi 'Received status code (429|502|503|504)|HTTP (429|502|503|504)|Too Many Requests' "$attempt_log" &&
    ! grep -Eqi 'There were failing tests|Lint found [0-9]+ errors|Compilation failed' "$attempt_log"
}

while true; do
  echo "Gradle attempt $attempt/$max_attempts: $*"
  set +e
  gradle --no-daemon --stacktrace --max-workers=2 "$@" 2>&1 | tee "$attempt_log"
  result="$?"
  set -e
  if [ "$result" -eq 0 ]; then
    echo "PASS: Gradle completed in $attempt attempt(s)"
    exit 0
  fi

  if ! transient_dependency_error; then
    echo "::error::Gradle failed without a recognized transient dependency HTTP error; not retrying tests or compilation." >&2
    exit "$result"
  fi
  if [ "$attempt" -ge "$max_attempts" ]; then
    echo "::error::Dependency download still failed with transient HTTP status after $max_attempts attempts." >&2
    exit "$result"
  fi

  echo "::warning::Transient Gradle dependency HTTP error detected; retrying after backoff (attempt $attempt/$max_attempts)."
  sleep "$((attempt * 15))"
  attempt="$((attempt + 1))"
done
