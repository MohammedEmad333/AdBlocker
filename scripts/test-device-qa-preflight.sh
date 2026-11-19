#!/usr/bin/env bash
# Offline tests for the sanitized device QA preflight; no Android device needed.
set -eo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin"

cat > "$tmp/bin/adb" <<'MOCK_ADB'
#!/usr/bin/env bash
set -eo pipefail

if [ "$1" = "devices" ]; then
  printf '%s\n' "List of devices attached"
  if [ "$MOCK_ADB_DEVICES" = "multiple" ]; then
    printf '%s\n' "MOCK_SERIAL_ONE device" "MOCK_SERIAL_TWO device"
  else
    printf '%s\n' "MOCK_SERIAL_ONE device"
  fi
  exit 0
fi

if [ "$1" = "-s" ]; then
  shift 2
fi

case "$*" in
  "get-state")
    printf '%s\n' "device"
    ;;
  "shell getprop ro.product.manufacturer")
    printf '%s\n' "Example Manufacturer"
    ;;
  "shell getprop ro.product.model")
    printf '%s\n' "Example QA Phone"
    ;;
  "shell getprop ro.build.version.release")
    printf '%s\n' "16"
    ;;
  "shell getprop ro.build.version.sdk")
    printf '%s\n' "36"
    ;;
  "shell getprop ro.build.version.security_patch")
    printf '%s\n' "2026-11-01"
    ;;
  "shell pm path com.example.adblocker")
    if [ "$MOCK_APP_INSTALLED" != "no" ]; then
      printf '%s\n' "package:/data/app/example/base.apk"
    fi
    ;;
  "shell dumpsys package com.example.adblocker")
    printf '%s\n' " versionCode=19 minSdk=26 targetSdk=36" " versionName=1.9.7"
    ;;
  *)
    echo "Unexpected mock adb command." >&2
    exit 64
    ;;
esac
MOCK_ADB
chmod +x "$tmp/bin/adb"

output="$(PATH="$tmp/bin:$PATH" MOCK_ADB_DEVICES=single MOCK_APP_INSTALLED=yes bash "$repo_root/scripts/device-qa-preflight.sh" 2>&1)"
printf '%s\n' "$output" | grep -Fq "Manufacturer: Example Manufacturer"
printf '%s\n' "$output" | grep -Fq "Model: Example QA Phone"
printf '%s\n' "$output" | grep -Fq "Android API level: 36"
printf '%s\n' "$output" | grep -Fq "Installed version name: 1.9.7"
printf '%s\n' "$output" | grep -Fq "Installed version code: 19"
printf '%s\n' "$output" | grep -Fq "Preflight result: PASS"
if printf '%s\n' "$output" | grep -Eq "MOCK_SERIAL|/data/app|logcat|packet captures"; then
  echo "FAIL: preflight output exposed a device identifier or an internal path." >&2
  exit 1
fi
echo "PASS: single-device metadata and installed version"

if PATH="$tmp/bin:$PATH" MOCK_ADB_DEVICES=multiple MOCK_APP_INSTALLED=yes bash "$repo_root/scripts/device-qa-preflight.sh" > "$tmp/multiple.out" 2>&1; then
  echo "FAIL: multiple devices should require explicit selection." >&2
  exit 1
fi
grep -Fq "Multiple ADB devices are connected" "$tmp/multiple.out"
if grep -Eq "MOCK_SERIAL" "$tmp/multiple.out"; then
  echo "FAIL: multiple-device error exposed a serial identifier." >&2
  exit 1
fi
echo "PASS: multiple devices require explicit selection without exposing identifiers"

output="$(PATH="$tmp/bin:$PATH" MOCK_ADB_DEVICES=multiple MOCK_APP_INSTALLED=yes ADB_SERIAL=MOCK_SERIAL_TWO bash "$repo_root/scripts/device-qa-preflight.sh" 2>&1)"
printf '%s\n' "$output" | grep -Fq "ADB target: one connected device (identifier omitted)"
if printf '%s\n' "$output" | grep -Eq "MOCK_SERIAL"; then
  echo "FAIL: explicit device selection leaked its identifier." >&2
  exit 1
fi
echo "PASS: explicit device selection keeps the identifier private"

output="$(PATH="$tmp/bin:$PATH" MOCK_ADB_DEVICES=single MOCK_APP_INSTALLED=no bash "$repo_root/scripts/device-qa-preflight.sh" 2>&1)"
printf '%s\n' "$output" | grep -Fq "Installed app: no"
echo "PASS: preflight handles an app that is not installed"

echo "PASS: sanitized device QA preflight self-test"
