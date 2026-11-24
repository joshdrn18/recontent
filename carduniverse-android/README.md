# CardUniverse for Android — Native Client

CardUniverse Android is a native Android game client. It does not display the website and does not embed the web game in a WebView.

- Package: `com.carduniverse.game`
- Current release: `2.9.0` (version code `13`)
- Minimum Android: Android 7.0 (API 24)
- Landscape-only native interface, optimized for horizontal gameplay.
- Native login and registration call the existing CardUniverse authentication API directly.
- Session credentials are stored encrypted with Android Keystore-backed AES-GCM.
- The app connects directly to the canonical CardUniverse Cloudflare Worker API.
- The CardUniverse website and Android app use the same Cloudflare D1 game database. Player accounts, inventory, cards, progression, events, and game state are shared.
- Android refreshes account/progression data when it returns to the foreground and provides a manual **SYNC** control to reload the current screen after web-side changes.
- Admin management stays on the website. The native Android client does not expose the Admin Console.
- Card Archive includes a visual card grid with search, ownership and rarity filters.
- Formation includes five visual card slots and still saves selected card IDs through the shared API.
- Arena includes a native versus stage with character artwork from the active formation, opponent artwork when shared by match data, animated Strike/Guard/Focus effects, health readouts and queue controls. Battle moves and outcomes remain server-authoritative.
- Summon displays an animated card reveal gallery after the server confirms a pack opening.
- Android release APKs use one permanent upload keystore restored from GitHub Actions Secrets. Install the permanent-signed release once; later releases preserve the package signature and can update in place.
- Website UI and web deployments remain separate and unchanged.

Native player modules include Summon, Adventure, Arena, Missions, Shop, Inbox, Guild, Settings, Dungeon, Raid, Trade Desk, Evolution, Formation, Binders, Achievement Hall, Level Rewards, Live Events, Leaderboards and Ranked Season.
