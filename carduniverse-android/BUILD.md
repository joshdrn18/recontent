# Building CardUniverse Native Android

Requirements: JDK 17, Android SDK Platform 35, Android Build Tools 35.0.0, and Gradle 8.9.

From the repository root:

```bash
gradle -p carduniverse-android :app:assembleRelease
```

Output:

`CardUniverse-v2.10.0.apk` (the Gradle intermediate file remains `app-release.apk` in its build directory).

The native app calls the existing Cloudflare Worker API directly. It does not load the Pages website or use a WebView.

Release APKs are signed with one permanent upload keystore restored from GitHub Actions Secrets. The workflow never creates a new key per build and never falls back to a temporary debug key.

## One-time signing setup

Add exactly one repository secret in GitHub:

- `CU_RELEASE_KEYSTORE_BASE64`: replace its value with the complete JSON contents of `CU_RELEASE_KEYSTORE_BASE64.txt` from the updated private one-secret signing bundle ZIP.

The secret bundle contains the keystore, alias and rotated passwords. The workflow source contains no signing passwords. Then run **Actions → Build CardUniverse Native Android APK → Run workflow**. Keep the original JKS keystore and the setup ZIP in a secure backup. Do not commit them.

The currently installed preview APKs were signed with temporary keys. Uninstall the old preview once when moving to the first permanent-signed release; after that, new releases with the same package and signing key can install over the previous version without deleting app data or reinstalling. Player progression remains on the CardUniverse server.


## Native Android 2.9.0

- Battle Arena now presents active card character artwork and animated Strike, Guard and Focus effects.
- Release signing uses one permanent keystore restored from the single `CU_RELEASE_KEYSTORE_BASE64` GitHub Actions secret.
- Release build: version code 13, version name 2.9.0.


## Native Android 2.10.0

- Download asset filename: `CardUniverse-v2.10.0.apk`.
- Account refresh runs automatically while foregrounded and when returning to the app.
- God Mode status loads from `/api/admin/profile` and Level 1000 is displayed as the level cap.
- Dark fantasy RPG theme, custom confirmation/result dialogs and improved touch feedback.
- Version code 14, version name 2.10.0.
