# CardUniverse for Android — Native Client

CardUniverse Android is an independently designed native Android client. It does not display the website and does not embed the web game in a WebView.

- Package: `com.carduniverse.game`
- App name: `CardUniverse`
- Minimum Android: Android 7.0 (API 24)
- Landscape-first native interface.
- Native login and registration call the existing CardUniverse authentication API directly.
- Session credentials are stored encrypted with Android Keystore-backed AES-GCM.
- The app connects directly to the existing CardUniverse Cloudflare Worker API.
- Player accounts, inventory, cards, progression, and game state remain in the existing Cloudflare backend.
- Website UI and web deployments remain separate and unchanged.

This is the native client foundation. Gameplay modules are being migrated to native screens in stages; a native screen should only expose an action once it is connected to the existing API.
