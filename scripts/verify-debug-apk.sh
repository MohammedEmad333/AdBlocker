#!/usr/bin/env bash
# Verify actual APK identity and checksum against app/build.gradle.
set -euo pipefail
apk="app/build/outputs/apk/debug/app-debug.apk"
test -s "$apk"
unzip -t "$apk" >/dev/null

expected_id="$(sed -nE 's/^[[:space:]]*applicationId[[:space:]]+"([^"]+)".*/\1/p' app/build.gradle)"
expected_code="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]+([0-9]+).*/\1/p' app/build.gradle)"
expected_name="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]+"([^"]+)".*/\1/p' app/build.gradle)"

if [ -z "$expected_id" ] || [ -z "$expected_code" ] || [ -z "$expected_name" ]; then
  echo "::error::Could not read application identity/version from app/build.gradle" >&2
  exit 1
fi

aapt="$ANDROID_HOME/build-tools/35.0.0/aapt"
package_line="$("$aapt" dump badging "$apk" | grep '^package: ')"
if [[ "$package_line" != *"name='$expected_id'"* ||
      "$package_line" != *"versionCode='$expected_code'"* ||
      "$package_line" != *"versionName='$expected_name'"* ]]; then
  echo "::error::APK package/version does not match app/build.gradle" >&2
  exit 1
fi

(cd app/build/outputs/apk/debug && sha256sum app-debug.apk > SHA256SUMS &&
  sha256sum -c SHA256SUMS)
echo "PASS: debug APK identity/version match $expected_id ($expected_name, code $expected_code)"
