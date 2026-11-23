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

Native modules now include Summon, Adventure, Arena, Missions, Shop, Inbox, Guild, Settings, Dungeon, Raid, Trade Desk, Evolution, Formation, Binders, Achievement Hall, Level Rewards, Live Events, Leaderboards and Ranked Season. Master Admin tools include overview, card creation, player inspection, mail publishing and read-only management panels for additional server modules.
