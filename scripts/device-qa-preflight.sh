#!/usr/bin/env bash
# Read-only, privacy-safe Android device metadata preflight for manual QA.
set -eo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
gradle_file="$repo_root/app/build.gradle"

if ! command -v adb >/dev/null 2>&1; then
  echo "ERROR: adb is not installed or is not available on PATH." >&2
  exit 127
fi

if [ ! -f "$gradle_file" ]; then
  echo "ERROR: app/build.gradle was not found." >&2
  exit 1
fi

application_id="$(sed -nE 's/^[[:space:]]*applicationId[[:space:]]+"([^"]+)".*/\1/p' "$gradle_file" | sed -n '1p')"
version_code="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]+([0-9]+).*/\1/p' "$gradle_file" | sed -n '1p')"
version_name="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]+"([^"]+)".*/\1/p' "$gradle_file" | sed -n '1p')"

if [ -z "$application_id" ] || [ -z "$version_code" ] || [ -z "$version_name" ]; then
  echo "ERROR: Could not read application identity and version from app/build.gradle." >&2
  exit 1
fi

selected="${ADB_SERIAL:-}"
if [ -z "$selected" ]; then
  devices="$(adb devices 2>/dev/null)" || {
    echo "ERROR: Could not query ADB devices." >&2
    exit 1
  }
  connected="$(printf '%s\n' "$devices" | awk 'NR > 1 && $2 == "device" { print $1 }')"
  count="$(printf '%s\n' "$connected" | awk 'NF { count++ } END { print count + 0 }')"

  if [ "$count" -eq 0 ]; then
    echo "ERROR: No authorized ADB device is connected." >&2
    exit 1
  fi
  if [ "$count" -gt 1 ]; then
    echo "ERROR: Multiple ADB devices are connected. Set ADB_SERIAL to select one; the identifier will not be written to the report." >&2
    exit 2
  fi
  selected="$(printf '%s\n' "$connected" | sed -n '1p')"
fi

state="$(adb -s "$selected" get-state 2>/dev/null)" || {
  echo "ERROR: The selected ADB device is not available. Check the connection and authorization." >&2
  exit 1
}
if [ "$state" != "device" ]; then
  echo "ERROR: The selected ADB device is not online and authorized." >&2
  exit 1
fi

read_prop() {
  local key="$1"
  local value
  value="$(adb -s "$selected" shell getprop "$key" 2>/dev/null | tr -d '\r' | sed -n '1p')"
  value="$(printf '%s' "$value" | tr -cd '[:alnum:] ._+()/-')"
  if [ -n "$value" ]; then
    printf '%s' "$value"
  else
    printf 'Unknown'
  fi
}

manufacturer="$(read_prop ro.product.manufacturer)"
model="$(read_prop ro.product.model)"
android_release="$(read_prop ro.build.version.release)"
android_api="$(read_prop ro.build.version.sdk)"
security_patch="$(read_prop ro.build.version.security_patch)"
timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

installed_path="$(adb -s "$selected" shell pm path "$application_id" 2>/dev/null | tr -d '\r' | sed -n '1p' || true)"

printf '%s\n' "AdBlocker device QA preflight"
printf '%s\n' "=============================="
printf 'Captured at (UTC): %s\n' "$timestamp"
printf '%s\n' "ADB target: one connected device (identifier omitted)"
printf 'Manufacturer: %s\n' "$manufacturer"
printf 'Model: %s\n' "$model"
printf 'Android release: %s\n' "$android_release"
printf 'Android API level: %s\n' "$android_api"
printf 'Security patch: %s\n' "$security_patch"
printf '%s\n' ""
printf '%s\n' "Repository build"
printf 'Application ID: %s\n' "$application_id"
printf 'Version name: %s\n' "$version_name"
printf 'Version code: %s\n' "$version_code"

if [ -n "$installed_path" ]; then
  package_dump="$(adb -s "$selected" shell dumpsys package "$application_id" 2>/dev/null || true)"
  installed_code="$(printf '%s\n' "$package_dump" | sed -nE 's/.*versionCode=([^[:space:]]+).*/\1/p' | sed -n '1p')"
  installed_name="$(printf '%s\n' "$package_dump" | sed -nE 's/^[[:space:]]*versionName=([^[:space:]]+).*/\1/p' | sed -n '1p')"
  if [ -z "$installed_code" ]; then installed_code="Unknown"; fi
  if [ -z "$installed_name" ]; then installed_name="Unknown"; fi
  printf '%s\n' ""
  printf '%s\n' "Installed app: yes"
  printf 'Installed version name: %s\n' "$installed_name"
  printf 'Installed version code: %s\n' "$installed_code"
else
  printf '%s\n' ""
  printf '%s\n' "Installed app: no"
fi

printf '%s\n' ""
printf '%s\n' "Privacy scope: read-only device metadata and installed app version only."
printf '%s\n' "Not collected: device serial/IMEI, IP addresses, Wi-Fi names, DNS names/history, logcat, packet captures, app data, backups, or signing material."
printf '%s\n' "This preflight does not test VPN permission, DNS forwarding, DoH, network handoff, battery use, or signed upgrades."
printf '%s\n' "Preflight result: PASS"
