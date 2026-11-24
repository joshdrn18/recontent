# Building CardUniverse Native Android

Requirements: JDK 17, Android SDK Platform 35, Android Build Tools 35.0.0, and Gradle 8.9.

From the repository root:

```bash
gradle -p carduniverse-android :app:assembleRelease
```

Output:

`carduniverse-android/app/build/outputs/apk/release/app-release.apk`

The native app calls the existing Cloudflare Worker API directly. It does not load the Pages website or use a WebView.

Release APKs are signed with one permanent upload keystore restored from GitHub Actions Secrets. The workflow never creates a new key per build and never falls back to a temporary debug key.

## One-time signing setup

Add these four repository secrets in GitHub:

- `CU_RELEASE_KEYSTORE_BASE64`: the entire contents of the private base64 file supplied with the release keystore.
- `CU_RELEASE_KEYSTORE_PASSWORD`: keystore password.
- `CU_RELEASE_KEY_ALIAS`: key alias.
- `CU_RELEASE_KEY_PASSWORD`: private-key password.

Then run **Actions → Build CardUniverse Native Android APK → Run workflow**. Keep the original JKS keystore and its passwords in a secure backup. Do not commit them.

The currently installed preview APKs were signed with temporary keys. Uninstall the old preview once when moving to the first permanent-signed release; after that, new releases with the same package and signing key can install over the previous version without deleting app data or reinstalling. Player progression remains on the CardUniverse server.
