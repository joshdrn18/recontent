# Building CardUniverse Android

The project produces a sideloadable Android APK from the current CardUniverse web game.

## Build locally

Requirements: JDK 17, Android SDK Platform 35, Android Build Tools 35.0.0, and Gradle 8.9.

From the repository root:

```bash
gradle -p carduniverse-android :app:assembleRelease
```

Output:

`carduniverse-android/app/build/outputs/apk/release/app-release.apk`

The APK loads `https://carduniverse.pages.dev/` inside the installed Android application. An internet connection is required to play.

## Signing

The CI build uses Gradle's debug signing configuration when no release keystore variables are configured. This is suitable for private sideload testing, not a final Play Store signing setup. Configure `CU_KEYSTORE_PATH`, `CU_KEYSTORE_PASSWORD`, `CU_KEY_ALIAS`, and `CU_KEY_PASSWORD` as CI environment values to sign with a dedicated release keystore.
