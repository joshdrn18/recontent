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

- `CU_RELEASE_KEYSTORE_BASE64`: edit the existing secret and replace its value with the complete JSON contents of `CardUniverse-v2.10.0-Signing-Secret.txt` (also included in the private signing bundle ZIP).

The secret bundle contains the same signing certificate, plus the keystore, alias and newly rotated passwords. The workflow source contains no signing passwords. After replacing the value, run **Actions → Build CardUniverse Native Android APK → Run workflow** once. Keep the original JKS keystore and the setup ZIP in a secure backup. Do not commit them.

Older preview APKs up to 2.8.0 were signed with temporary keys. Moving from those to the first permanent-signed release (2.9.0) required one uninstall. The 2.10.0 signing bundle preserves the same certificate already used by 2.9.0, while rotating the keystore passwords, so users already on 2.9.0 can install 2.10.0 and later updates in place. Player progression remains on the CardUniverse server.


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
