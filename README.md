# WynnTitles

Earn title words from Wynncraft achievements and wear them above your name. Players use everything in game, with no separate account to create. An optional private moderation dashboard runs on its own listener.

```
mod/      Fabric client mod (Wynntils addon, Cloth Config options)
server/   Go API server with SQLite; also the home of the achievement catalog
```

## How it fits together

- **Login** uses the Mojang session handshake, the same check a Minecraft server does when you join it. The mod asks the server for a challenge, calls Mojang's `joinServer` with it, and the server confirms with Mojang's `hasJoined`. The resulting session token is cached in `config/wynntitles/session.json`.
- **The catalog** (`server/catalog/achievements.json`) is the single source of truth. It is embedded in the server binary and validated at startup. The mod downloads it from `GET /v1/catalog`, so adding an achievement that uses an existing trigger type needs no mod update.
- **Title words are plain text** unless their achievement gives them a **native style** (`runs` in the catalog). Only the big ones have one: the level 120s, The Moon, the Legendary Island token, The Wartorn Palace, and Greg from The Nameless Anomaly, whose native style is dark, obfuscated text like the raid's own title. Native styles may use anything, including obfuscated text and custom hex colors.
- **The style shop** (`server/catalog/shop.json`) sells style pieces for achievement points: solid colors, palettes, animated palettes, rainbow, bold, italic, underlined and strikethrough, and ten Wynncraft font styles (three text fonts, three scripts and four banners). Each animated palette pairs a palette with one of six animations (flow, pulse, shimmer, wave, flicker, sparkle), named by the item's `animation`; the mod colors every letter in `titles/EffectColors.java`, the one place nametags, chat and screens all go through, and an older mod shows any animation as flow. Obfuscated is never sold. The 164 achievements award 592 points; the full shop costs 720 (82.2% earnable), so completionists can buy most of it. Each slot (prefix and suffix) wears either the word's native style or a shop style built from one color source, an optional font and compatible formats, and a shop style replaces the native one completely, so restyling Greg reveals the name. The server builds the styled title, so the nametag code only ever sees runs. Font items declare allowed color-source kinds and format support in the shop catalog. Banners use one solid background and a separately picked letter color (automatic black/white contrast by default); switching to a banner previews removing formats and replacing an effect with its first palette color before Wear saves it. Banners require ASCII letters, digits and spaces, and incompatible titles or missing-pack font choices are disabled with explanations. Without the pack, equipped fonts fall back to vanilla text in their solid color. A player's balance is points earned minus prices paid; purchases record the price paid, so repricing never changes a balance. Purchases are final for players, so Buy asks once before spending; a moderator can reset one player's or everyone's purchases from the dashboard, which refunds every price paid. Prices and items are data, so they can be retuned or added to without a mod update; an older mod hides item kinds it does not know.
- **Claims are trusted.** The mod detects triggers with Wynntils data and claims them, and the server records every claim with its evidence in the `unlocks` table. Moderation can revoke a row later by setting `revoked_at`.
- **Achievement icons** are the achievement's Wynncraft item inside a hexagonal bronze, silver or gold frame. The catalog names an item model from the Wynncraft resource pack (for example `wynn/prop/quest_scroll`) and a vanilla fallback. Whenever resource packs load, the mod indexes the loaded item definitions to find which item and damage or custom model data show that model, so nothing from the pack is copied, and renumbering in a pack update is picked up automatically. Off Wynncraft, or if a model disappears, the fallback item shows.
- **The screens** (`mod/.../mc/screen`) are built on a small UI kit of our own (`mc/screen/ui`), laid out like Wynncraft's raid start screen, in our own regal purple and gold: a field between two stone pillars with a skull on the bottom ledge, a wine-red ribbon for the title, a stone tab bar with the Wynncraft eye carved into it, dark stone wells and stone buttons. Achievements are a trophy list: categories with their progress, then each achievement with its reward, unlock date and rarity. The sprites are drawn by `scripts/draw-ui-sprites.py`. The panel is pixel art drawn at a whole number of window pixels per pixel: as big as the player's GUI scale, and smaller when that would not fit the window. Tabs switch with a gliding gold frame and the page slides in; lists glide when scrolled. Text uses the Wynncraft pack's fonts by id (`language/wynncraft` and the small-caps `language/five`) when the loaded packs define them, and the vanilla font otherwise (`mc/WynnFonts.java`); like icons, nothing is copied from the pack.
- **Rarity** (`GET /v1/stats/achievements`) is the share of WynnTitles players (everyone who has logged in with the mod) holding each achievement, not counting revoked unlocks. The mod names it after Wynncraft's item tiers, from Normal down to Mythic below 1%. The server counts every unlock to answer, so it reuses the result for `WT_STATS_TTL`.
- **Title lookups** (`POST /v1/titles/batch`) are served from an in-memory map rebuilt from SQLite at startup, so crowded world events never touch the database.

## Running locally

Server (needs Go 1.26+):

```bash
cd server && WT_OFFLINE_AUTH=true go run ./cmd/wynntitles
```

`WT_OFFLINE_AUTH=true` skips the Mojang check so the dev client's offline account can log in. Never set it in production. Other settings: `WT_ADDR` (default `:8080`), `WT_DB` (default `data/wynntitles.db`), `WT_SESSION_TTL` (default `720h`), `WT_STATS_TTL` (how long rarity is cached, default `1m`).

Mod (Gradle downloads its own Java 25 to run; the mod targets Java 21):

```bash
cd mod && ./gradlew runClient
```

The dev client points at `http://localhost:8080`; release builds default to `https://wynntitles.jamino.me`. Join a singleplayer world, then press `` ` `` or run `/titles` to open the title screen. `/titles status`, `/titles login` and `/titles reload` help with troubleshooting.

## Hosting and moderation

See [the complete Docker, Cloudflare and dashboard setup](deploy/README.md).
The Go binary includes an optional server-rendered moderation console on `WT_ADMIN_ADDR`.
It never mounts dashboard routes on the public API. The console supports player search,
reported claim evidence, confirmed revoke/restore actions, purchase resets (one player or everyone), and an append-only audit history.
Developer grants of catalog achievements and free shop styles are limited to `WT_DEV_UUID`.
Minecraft authentication and the mod's API response shapes are unchanged.

## Tests

Everything runs offline with one command:

```bash
scripts/smoke-test.sh
```

It runs the Go tests, starts a throwaway server with offline auth and a temporary database, then runs:

- **Mod JUnit tests** (`mod/src/test`): the real API client against that server (login, cached session, claim, equip, batch lookup), plus the achievement tracker with a fake detector standing in for Wynntils data.
- **Client game test** (`mod/src/gametest`): boots the real client with Wynntils into a singleplayer world, checks that joining logs in, equips a title, spawns a fake player in front of the camera, and asserts that player's title reaches the Wynntils nametag stack. A stand-in pack in `src/gametest/resources` selects models the way Wynncraft's does, so the test also checks that icons resolve and fall back. It saves screenshots of the nametag, the Titles tab, the Achievements tab and the unlock toast to `mod/build/run/clientGameTest/screenshots`.

To check every catalog icon against the real Wynncraft pack, point the unit tests at a cached copy (Minecraft keeps them in the profile's `downloads/` folder after you join Wynncraft):

```bash
cd mod && ./gradlew test -Dwynntitles.wynnPack="<profile>/downloads/<id>/<hash>"
```

To also see the screens the way players do, with the Wynncraft pack's fonts and item models, pass the same cached pack to the smoke test. The client test loads it after its own checks and saves `*-wynnpack` screenshots:

```bash
WT_WYNN_PACK="<profile>/downloads/<id>/<hash>" scripts/smoke-test.sh
```

Wynntils only fires nametag events on Wynncraft, so a test-only mixin forwards that one event in singleplayer. Use `scripts/smoke-test.sh --no-game` to skip the client test.

## API

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/healthz` | | Liveness |
| GET | `/v1/catalog` | | Achievements, title words and the style shop (ETag cached) |
| GET | `/v1/stats/achievements` | | `{players, unlocks: {achievementId: count}}` for rarity (cached for `WT_STATS_TTL`) |
| POST | `/v1/auth/challenge` | | `{username}` → `{serverId}` |
| POST | `/v1/auth/verify` | | `{username, serverId}` → `{token, uuid, username, expiresAt}` |
| POST | `/v1/auth/logout` | Bearer | Ends the session |
| GET | `/v1/me` | Bearer | Unlocks, points, balance, owned items, available words, equipped words and styles, title |
| PUT | `/v1/me/title` | Bearer | `{prefix, suffix}` (null unequips) |
| PUT | `/v1/me/style` | Bearer | `{prefix, suffix}`, each `{source, formats, font, color, letterColor}` or null for native style; source/formats/font are owned item ids, color is a solid fallback when a font replaces an effect, and letterColor is a banner color |
| POST | `/v1/shop/buy` | Bearer | `{itemIds: [...]}`, all or nothing |
| POST | `/v1/claims` | Bearer | `{achievementId, triggerType, evidence, modVersion}` |
| POST | `/v1/titles/batch` | | `{uuids: [...]}` (max 500) → `{titles: {uuid: title or null}}` |
