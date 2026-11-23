# Building CardUniverse Native Android

Requirements: JDK 17, Android SDK Platform 35, Android Build Tools 35.0.0, and Gradle 8.9.

From the repository root:

```bash
gradle -p carduniverse-android :app:assembleRelease
```

Output:

`carduniverse-android/app/build/outputs/apk/release/app-release.apk`

The native app calls the existing Cloudflare Worker API directly. It does not load the Pages website or use a WebView.

The CI preview build uses an ephemeral signing key. Remove an older preview installation before installing a newly signed preview APK. Player data remains on the CardUniverse server.
