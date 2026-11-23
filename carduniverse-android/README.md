# CardUniverse for Android — Native Client

CardUniverse Android is a native Android game client. It does not display the website and does not embed the web game in a WebView.

- Package: `com.carduniverse.game`
- Minimum Android: Android 7.0 (API 24)
- Landscape-only native interface, optimized for horizontal gameplay.
- Native login and registration call the existing CardUniverse authentication API directly.
- Session credentials are stored encrypted with Android Keystore-backed AES-GCM.
- The app connects directly to the canonical CardUniverse Cloudflare Worker API.
- The CardUniverse website and Android app use the same Cloudflare D1 game database. Player accounts, inventory, cards, progression, events, and game state are shared.
- Android refreshes account/progression data when it returns to the foreground and provides a manual **SYNC** control to reload the current screen after web-side changes.
- Admin management stays on the website. The native Android client does not expose the Admin Console.
- Card Archive now uses a visual card grid with search, ownership and rarity filters; card details are loaded from the shared game API.
- Website UI and web deployments remain separate and unchanged.

Native player modules include Summon, Adventure, Arena, Missions, Shop, Inbox, Guild, Settings, Dungeon, Raid, Trade Desk, Evolution, Formation, Binders, Achievement Hall, Level Rewards, Live Events, Leaderboards and Ranked Season.
