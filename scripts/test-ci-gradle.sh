#!/usr/bin/env bash
# Self-test retry classification using a stub Gradle; no network required.
set -euo pipefail
repo="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin"

cat > "$tmp/bin/gradle" <<'MOCK_GRADLE'
#!/usr/bin/env bash
set -euo pipefail
count=0
if [ -f "$MOCK_CALLS" ]; then count="$(cat "$MOCK_CALLS")"; fi
count="$((count + 1))"
echo "$count" > "$MOCK_CALLS"
case "$MOCK_SCENARIO" in
  transient429|transient503)
    if [ "$count" -eq 1 ]; then
      echo "Could not GET 'https://repo.maven.apache.org/maven2/example/library.pom'"
      if [ "$MOCK_SCENARIO" = transient429 ]; then
        echo "Received status code 429 from server: Too Many Requests"
      else
        echo "Received status code 503 from server: Service Unavailable"
      fi
      exit 1
    fi
    ;;
  persistent429)
    echo "Could not GET 'https://repo.maven.apache.org/maven2/example/library.pom'"
    echo "Received status code 429 from server: Too Many Requests"
    exit 1
    ;;
  compile_error)
    echo "Compilation failed: symbol not found"
    exit 1
    ;;
  mixed_test_failure)
    echo "Could not GET 'https://repo.maven.apache.org/maven2/example/library.pom'"
    echo "Received status code 429 from server: Too Many Requests"
    echo "There were failing tests"
    exit 1
    ;;
  success)
    ;;
  *)
    echo "Unexpected mock scenario" >&2
    exit 2
    ;;
esac
echo "BUILD SUCCESSFUL"
MOCK_GRADLE

cat > "$tmp/bin/sleep" <<'MOCK_SLEEP'
#!/usr/bin/env bash
exit 0
MOCK_SLEEP
chmod +x "$tmp/bin/gradle" "$tmp/bin/sleep"

assert_case() {
  local scenario="$1" expected_exit="$2" expected_calls="$3"
  rm -f "$tmp/calls"
  local actual_exit=0
  PATH="$tmp/bin:$PATH" MOCK_CALLS="$tmp/calls" MOCK_SCENARIO="$scenario" \
    bash "$repo/scripts/ci-gradle.sh" lintDebug testDebugUnitTest assembleDebug \
    > "$tmp/output" 2>&1 || actual_exit="$?"
  local actual_calls
  actual_calls="$(cat "$tmp/calls")"
  if [ "$actual_exit" -ne "$expected_exit" ] || [ "$actual_calls" -ne "$expected_calls" ]; then
    echo "FAIL: $scenario (exit=$actual_exit, calls=$actual_calls)" >&2
    cat "$tmp/output" >&2
    exit 1
  fi
  echo "PASS: $scenario (exit=$actual_exit, calls=$actual_calls)"
}

assert_case success 0 1
assert_case transient429 0 2
assert_case transient503 0 2
assert_case persistent429 1 3
assert_case compile_error 1 1
assert_case mixed_test_failure 1 1
echo "PASS: CI Gradle bounded retry classification"
