# CardUniverse for Android

A native Android APK wrapper for the live CardUniverse game at https://carduniverse.pages.dev/.

- Package: `com.carduniverse.game`
- App name: `CardUniverse`
- Minimum Android: Android 7.0 (API 24)
- Runs inside Android System WebView, not a Chrome tab or PWA shortcut.
- Game accounts, inventory and progress continue to use the existing CardUniverse Cloudflare backend.
- An internet connection is required to play. The APK does not replace the live game or backend.

The GitHub Actions workflow builds a signed release APK and attaches it to a GitHub Release.
