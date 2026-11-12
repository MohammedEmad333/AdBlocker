#!/usr/bin/env bash
set -euo pipefail

package="com.example.adblocker"
apk="app/build/outputs/apk/debug/app-debug.apk"
test -s "$apk"

sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ "$sdk" != "36" ]]; then
  echo "ERROR: expected Android API 36 emulator; got $sdk"
  exit 1
fi

adb install -r "$apk"
adb shell am force-stop "$package"
result="$(adb shell am start -W -n "$package/.MainActivity")"
printf '%s\n' "$result"
grep -q 'Status: ok' <<< "$result"
adb shell pidof "$package" >/dev/null

# Rotation should not destroy the activity process due to inset/UI changes.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 3
adb shell pidof "$package" >/dev/null
adb shell settings put system user_rotation 0
sleep 3
adb shell pidof "$package" >/dev/null

# A second normal launch should remain healthy.
adb shell input keyevent KEYCODE_HOME
result="$(adb shell am start -W -n "$package/.MainActivity")"
grep -q 'Status: ok' <<< "$result"
adb shell pidof "$package" >/dev/null

echo "PASS: Android 16 installation, launcher, rotation and relaunch"
echo "NOTE: VPN consent, DNS routing, UI cutout rendering and real devices require manual QA"
