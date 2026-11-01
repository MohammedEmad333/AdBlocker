# Signed release preparation

## Requirements

The repository owner must create and securely back up an Android app signing keystore, outside the repository. Do not send keys or passwords in chat, source files, PRs, issues, or public build logs.

Create the following four **repository Actions secrets**:

- `ADBLOCKER_KEYSTORE_BASE64`: a one-line base64 representation of the binary keystore.
- `ADBLOCKER_KEYSTORE_PASSWORD`: the keystore password.
- `ADBLOCKER_KEY_ALIAS`: key alias.
- `ADBLOCKER_KEY_PASSWORD`: signing key password.

To create a keystore locally (store the original securely):

```bash
keytool -genkeypair -v -keystore adblocker-release.jks -alias adblocker -keyalg RSA -keysize 3072 -validity 10000
base64 -w0 adblocker-release.jks
```

Paste the encoded value into its GitHub secret. The encoded value is still sensitive.

## Build

1. Verify that Android CI is green on main.
2. Go to Actions > **Signed Release APK (manual)** > Run workflow; choose `main`.
3. The workflow fails explicitly when any required secret is missing.
4. The job decodes the keystore into an ephemeral runner file, performs `lintRelease testDebugUnitTest assembleRelease`, verifies the generated APK using Android `apksigner`, and writes its SHA-256 checksum.
5. Download artifact **AdBlocker-signed-release**. Verify locally with `sha256sum -c SHA256SUMS`, then install and test on physical Android devices.

The workflow does not create a GitHub Release or publish to any store. **No release has been signed or published automatically by this PR.**

## Versioning and upgrade compatibility

Increment `versionCode` for every new release, and set a new `versionName`. Ensure the permanent application ID is chosen before the first public signed release (currently `com.example.adblocker`). Keep the signing key securely backed up for compatible upgrades. Never replace it casually.

## Safety and limits

Secrets are excluded from PR jobs, and the signed build runs only from main. GitHub Actions permissions are read-only, and the temporary keystore is removed by a shell trap. The signed artifact is retained for 30 days. Manual installation and network tests remain mandatory.
