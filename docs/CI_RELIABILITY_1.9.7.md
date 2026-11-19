# AdBlocker 1.9.7: CI reliability and release gates

## Incident and scope

On 18 November 2026, push CI run 44329180030 initially failed while
resolving jsr305:3.0.2 from Maven Central (HTTP 429 Too Many Requests).
The failed-job rerun (attempt 2) succeeded. It was a dependency fetch
failure, **not an observed Java test or Android lint regression**.

This patch changes workflow orchestration and version metadata only; DNS/VPN
behavior and user settings are unchanged.

## Mitigations

1. Switch CI and emulator jobs to Gradle setup action v6.
   Enable the documented open-source/basic Gradle dependency cache.
   Main writes Gradle cache entries; PR builds consume them read-only.
   Emulator jobs read them without writing competing entries.
2. Limit Gradle worker concurrency to two for CI build invocations.
3. Retry only failed Gradle processes with both a dependency retrieval
   error and transient HTTP status (429, 502, 503 or 504). Make at most
   three attempts total with 15/30-second backoff. No retries for actual
   compile, lint or test failures; nonzero exit status remains nonzero.
4. Run an offline mock-Gradle self-test for successful, transient, exhausted,
   compilation-error and mixed test-failure scenarios in every Android CI.
5. Independently verify debug APK ZIP integrity, application ID,
   versionName and versionCode against app/build.gradle, plus SHA-256.
6. Keep Gradle User Home caching **disabled** in owner-controlled manual
   signed-release jobs so signing-sensitive build state is not persisted.
   Apply the same bounded dependency failure retry there, without
   publishing a release or changing signing permissions.

There is no proxy, third-party Maven mirror, dependency-version upgrade,
new token or repository secret.

## Acceptance and limitations

- Android CI self-test, lint, Java unit tests, debug APK build and APK
  identity/checksum must pass on the PR and again after merging to main.
- Android 16 emulator install, launch, rotation and relaunch smoke must pass.
- HTTP rate limiting may still exhaust the bounded retries; classify and
  report it rather than silently marking the workflow green.
- Cache availability and hit rates depend on GitHub Actions cache retention
  and branch access rules. First/uncached runs may still require downloads.
- Manual VPN and network QA issue #64 is still open. No public/signed
  release is authorized by this change.
