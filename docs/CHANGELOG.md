# Changelog

One entry per commit, newest first. Versions start at 0.0.1 for the initial commit and are assigned retroactively from the diffs. The latest one is also `mod_version` in `mod/gradle.properties`. The bump follows how much the commit changed:

- **Minor** (0.x.0): a new feature, screen, system or content batch.
- **Patch** (0.0.x): a fix, retune, chore or small tweak.

The two merge commits (`bd48998`, `2c35a71`) bring the shop-animations branch into `main`. They add nothing of their own, so they have no version.

Catalog size is noted where it changed (achievements / points earned, shop items / total price).

---

## 0.20.0 · 2026-11-01
**Last Rites: win a raid as the last one alive**

- **Last Rites** (gold) rewards the suffix **Pallbearer** for winning The Nameless Anomaly or The Wartorn Palace as the last one alive in the party.
- A new `raidLastStanding` trigger takes `raidNames`. `RaidLastStand` reads the party list Wynntils parses from the scoreboard, where a dead player's name is struck through, and needs at least one fallen party member, so solo clears and parties emptied by disconnects don't count.
- There is no API for this, so only wins seen while the game is running count.
- A new native-only animation, **vigil**: letters break and fall dark at random until one burns alone, then it relights the word.
- Catalog: 159 → **160 achievements / 566 pts**; shop stays at **65 items** and **678**, now 83.5% earnable.
- `mod_version` goes from 0.19.1 to 0.20.0.

## 0.19.1 · 2026-11-01
**Release cleanup and a player-facing README**

- `README.md` is rewritten for players: what the mod does, install, commands, config, requirements and privacy. Screenshots and GIFs are dragged in on GitHub rather than committed.
- The old architecture, running-locally, tests and API sections moved to `docs/ARCHITECTURE.md`, and the references to them are updated.
- The author and license holder is DrBiznes, not DrBzines, in `LICENSE`, `fabric.mod.json` and the Go module path (`github.com/DrBiznes/WynnTitles/server`).
- `docs/TODO.md`, `docs/ACHIEVEMENT_IDEAS.md` and `.claude/launch.json` are removed; the work they tracked is done.
- `mod_version` goes from 0.19.0 to 0.19.1.

## 0.19.0 · 2026-11-01
**A smaller, stranger collection category**

- Nine collection achievements are cut: Grandmother, Alkatraz, Pupil Hoarder, Wealthy Beyond Measure, Eyes Yet Open, Rib Collector, Redbeard's Beard, Mega Fern and Blazing Fireball.
- Five replace them: **Warp** (Phased Drifter), **Idol** (Drowned Devotee), **Mama Zomble's Memory** (Mama's Favourite), **Coalescence** (Coalesced) and **Canned Abis** at 420 carried (Baked).
- Five native-only animations: **phase**, **undertow**, **coalesce**, **kaleido** and **lullaby**. An achievement's two words may now share one native style.
- Ingredients now unlock `inventoryItem` achievements. Their names end in a star tier, so Borange Fluff could never match before.
- `inventoryItem` takes an optional `rarity`, so Idol ignores the False Idol helmet.
- Every font is 1 point cheaper: 12 → 11, 18 → 17 and 22 → 21.
- The title word test still rejects any word two achievements share, ignoring case, and now also rejects a word used as both a prefix and a suffix. The check has its own test.
- Catalog: 163 → **159 achievements / 561 pts**; shop stays at **65 items**, 688 → **678**, which keeps it 82.7% earnable.
- `mod_version` goes from 0.18.3 to 0.19.0.

## 0.18.3 · 2026-11-01
**Smoke test exits cleanly on Windows**

- `scripts/smoke-test.sh` waits for the test server to exit before deleting its database, so a passing run no longer exits 1 on `Device or resource busy`.
- A temp file that can't be deleted no longer fails the run.
- `mod_version` goes from 0.18.2 to 0.18.3.

## 0.18.2 · 2026-11-01
**Tab bar rivets sit centered**

- The two rivets on the tab bar move up one pixel, to the middle of the bar's flat stone.
- `mod_version` goes from 0.18.1 to 0.18.2.

## 0.18.1 · 2026-11-01
**Titles show in raids by default**

- `hideTitlesInRaids` is now off by default. Configs that already saved the option keep their value.
- `mod_version` goes from 0.18.0 to 0.18.1.

## 0.18.0 · 2026-11-01
**Live quest and dungeon unlocks, and fewer Content Book scans**

- Quests and dungeons unlock from Wynncraft's chat messages as soon as they finish, instead of waiting for the Wynncraft API. The API still covers past completions, other characters and anything chat misses.
- A corrupted win counts as its own dungeon, and a dungeon win that arrives twice counts once.
- Dungeon counts add this session's clears to the API's count, so a 10-clear achievement no longer waits for the API on the last clear.
- The `Models.Quest` check is gone, because nothing fills it. Evidence `source` is now `questChat`, `dungeonChat` or `wynncraftApi`.
- The Content Book is scanned once per character per game launch, not on every world join, and `territorySet` and `territoryAny` share that scan. Places that are not territories now unlock at the next launch.
- The Content Book goes back to the filter it was on before the scan.
- New plan doc: `docs/LIVE_COMPLETION_EVENTS.md`.
- `mod_version` goes from 0.17.2 to 0.18.0.

## 0.17.2 · 2026-10-31
**Readable Emissary title**

- Emissary's first gradient stop goes from `#5B6FA8` to `#7C8CB9`, which clears the contrast minimum in `EffectColorsTest.nativeStylesAreReadable`.
- `mod_version` goes from 0.17.1 to 0.17.2.

## 0.17.1 · 2026-10-31
**Config menu fix**

- The options screen no longer lists `DEFAULT_SERVER_URL` and `lastServerUrl` as raw `text.autoconfig` rows. Cloth lists every declared field, statics included, so they are no longer fields of `WynnTitlesConfig`.
- Save & Quit no longer crashes the game. It tried to write to the `DEFAULT_SERVER_URL` constant.
- The client game test now opens the options screen, checks every row is translated and saves it.
- `mod_version` goes from 0.17.0 to 0.17.1.

## 0.17.0 · 2026-10-31
**Fewer profession achievements and a retuned catalog**

- Each profession now has one achievement at level 100 instead of one at 100 and one at 132. It awards both title words, such as Timbered Lumberjack, and Perfectionist still needs all twelve at 132.
- Harvest-Blessed becomes **Sun-Blessed**, so the Farming title doesn't repeat itself.
- The shop is repriced to match: palettes 10 → 9, animated palettes 16 → 14, and the Pill, Box, Flag and Ribbon fonts 27 → 22.
- The rule against lowering prices after launch is gone. Prices will change often during the beta, and purchases keep their price paid.
- New trigger: `territoryAny`, met by any one of the listed places.
- New exploration achievements Mind the Gap and Port of Call replace Old Man Martyn's Cabin and Gone to Travel.
- Into the Void and the Moon achievement move to secrets.
- The raid ladder moves up: Seasoned Raider at 100 raids and Raid Syndicate at 1,000.
- Hardcore achievements no longer say "living", because the API can't tell whether a character is alive.
- `mod/MODRINTH.md` is removed.
- Catalog: 175 → **163 achievements / 569 pts**; shop stays at **65 items**, 761 → **688**, which keeps it 82.7% earnable.
- `mod_version` goes from 0.16.0 to 0.17.0.

## 0.16.0 · 2026-10-31
**Launch moderation tools**

- Suspend a player, optionally revoking every claim. The API answers 403 with an optional appeal link from `WT_APPEAL_URL`, the title stays hidden across restarts, and suspended players leave rarity.
- Revoke or restore all of a player's claims in one action.
- New Achievements pages list holders, and an achievement's claims can be cleared for everyone so honest players can earn it again. Each cleared claim is copied into the audit history.
- New Claims and Flagged pages. Flags cover invalid, old or unreleased mod versions and missing or malformed evidence.
- Live claim controls with no deploy: pause all claims, pause one achievement, and set a minimum mod version. Claims get a new `paused` or `updateRequired` status, which the mod retries.
- The dashboard overview shows player, claim and error counts, mod versions, the build and the database size. The player list shows points, claims, join date and last seen.
- `last_seen_at` now follows API use instead of logins, and `/healthz` queries the database.
- `moderation_log.uuid` is nullable so server-wide actions can be audited.
- `mod_version` goes from 0.15.1 to 0.16.0.

## 0.15.1 · 2026-10-31
**Mod icon and banner**

- The placeholder mod icon is replaced: the DrBiznes head under a gold King's Recruit nametag.
- The icon and a matching 468×200 banner live in `docs/branding`, and the README opens with the banner.
- `mod_version` goes from 0.15.0 to 0.15.1.

## 0.15.0 · 2026-10-31
**Native style for Sleuth**

- New animation: **oculus**. Scrambled lids open from the middle, a bold iris glances around, then the lids shut.
- The shop sells it too, as **The Eye's Gaze**: crimson through amber to a pale iris, for 16 points. Its palette is sold animated only, so there are now more animated palettes than plain ones.
- Sleuth's title goes from Hidden Sleuth to **Omniscient Pupil**, in the Eye's honor. Both words wear oculus in their own colors, which the shop can't buy.
- The catalog validation knows the new animation id. README, `AGENTS.md` and the achievement list updated.
- `mod_version` goes from 0.14.0 to 0.15.0.
- Catalog unchanged: **175 achievements / 629 pts**; shop 64 → **65 items / 761**, which keeps it 82.7% earnable.

## 0.14.0 · 2026-10-31
**Release hardening: batch claims, connection retries, safer Wynntils hook, self-hosting guide**

- `POST /v1/claims` now takes a list and records up to 200 claims in one transaction, replacing the single-claim format. The mod sends everything met in a check as one request, so a first login no longer queues one write per achievement behind the mutation lock. **Deploy this server and this mod together**: neither works with the old version of the other, and an old mod's claims now get a 400.
- Achievements are only checked on a Wynncraft world. Before, the tracker kept running after leaving, so position triggers could fire in singleplayer or on another server, and the Wynncraft API kept being polled.
- If the server can't be reached at join, the mod says so in chat and retries after 30 seconds, 1 minute and 2 minutes, then tells the player how to try again. Before, it stayed silently offline.
- A Wynntils update that moves the private nametag method now loses titles instead of crashing the game. The hook is optional and matched by exact signature, the render path catches everything, and the log says when the hook is gone. Requires Wynntils 4.2.16 or newer, the version the mod is built against.
- `deploy/` is now a general guide for guilds running a private server, with a generic compose file.

## 0.13.1 · 2026-10-30 · `3bfa216`
**Retune four title words, test that title words stay unique**

- Armouring 132: Plate-Forged becomes **Unbreachable**, so only Anvil-Forged and Hand-Forged are left in the Forged family.
- Mining 132: Deep-Delved becomes **Obsidian**.
- Colossus Felled: Colossal + Colossus-Slayer becomes **Merciless** + **Colossus Killer**.
- Put an End to War: Spire-Crowned becomes **Wrathful**, keeping its gradient.
- New `TestEmbeddedTitleWordsAreUnique` fails on any repeated prefix or suffix, ignoring case. The title builder keeps only the first match, so a repeat would leave an achievement with no word to wear.
- Players who had a replaced word equipped lose it until they pick again, which is fine before release.
- `mod_version` in `mod/gradle.properties` goes from 0.0.1 to 0.13.1 to match this changelog.
- Catalog unchanged: **175 achievements / 629 pts**.

## 0.13.0 · 2026-10-30 · `1918db9`
**Character discovery sets and secret achievements**

- `territorySet` achievements now read each character's Content Book through Wynntils and keep the results per character. Cumulative places count from what a character has already found.
- Places that are not territories unlock at the next world join. The mod starts the scan once per join, and only while a set is still locked.
- The Moon and the libraries moved onto this system.
- 11 new achievements: Death's Door, Bak'al's Wake, Edge of Nothing, Sleuth, The Queen's Census, five more libraries and Complete Works.
- A Go test checks every listed place name against a copy of the Content Book names (`server/catalog/testdata/discoveries.json`).
- New Lutho palette pair in the shop.
- Catalog: 164 → **175 achievements / 629 pts**; shop 62 → **64 items / 745**, which keeps it 84.4% earnable.

## 0.12.1 · 2026-10-30 · `f8c9fea`
**Fix: heartbeat no longer shakes gamemode titles**

- Heartbeat used to bold the whole word on each thump. That widened it by a pixel per letter and shook centered nametags. It now beats by brightness alone.
- Deathless, Solitary and Unyielding stay bold so they keep their weight.
- Updated the `EffectColors` tests to match.

## 0.12.0 · 2026-10-30 · `7117972`
**Native styles for the leaderboard achievements**

- Three new native-only animations, never sold in the shop:
  - **ascend**: Ranked climbs bronze to silver to gold.
  - **reel**: Chart-Topping locks in from spinning letters.
  - **prism**: Apex flashes white and scatters into pastels.
- Added them to `EffectColors` and `TitleAnimation`, and gave the three leaderboard achievements their styles.
- The catalog validation knows the new animation ids. README and `AGENTS.md` updated.

## 0.11.1 · 2026-10-30 · `832b7de`
**Move planning docs into `docs/`, retune Acrobat and Pentarch**

- `ACHIEVEMENT_IDEAS.md`, `AGENTS.md` and `TODO.md` moved into `docs/`.
- Acrobat becomes a single animated gradient.
- Pentarch now needs a level 120 character of every class instead of level 100.

## 0.11.0 · 2026-10-30 · `b461f7a`
**Hide titles in raids**

- New `hideTitlesInRaids` option, on by default, with a config label and tooltip.
- `Nametags` reads Wynntils' current raid instead of following its raid events, because an interrupted raid posts no end event.
- Added a client game test and extended `NametagProbe`. Updated the Modrinth description.

## 0.10.1 · 2026-10-30 · `8eaa047`
**Reprice the shop for the five new animated palettes**

- The shop grew from 52 to 62 items, so every tier drops by about 0.82 to stay 80–85% buyable (592 points against 719, 82.3%).
- New prices: color 4 → 3, format 6 → 5, palette 12 → 10, animated palette 20 → 16, fonts 15/22/32 → 12/18/27, rainbow 45 → 40. Tier order is unchanged.

## 0.10.0 · 2026-10-30 · `1421e71`
**New title animations and effects**

- Six native-only animations: **decipher**, **glitch**, **eclipse**, **strike**, **heartbeat** and **swell**. `TitleEffect` supports a "still" state for them.
- The catalog now guarantees native styles are unique and never for sale, and shop validation rejects native animations.
- Reworked several achievement titles to use animated gradients. Renamed palettes and animated palettes for thematic consistency.
- New config options:
  - **Title size**: a 50–150% slider, default 80.
  - **Animate titles**: a toggle for still titles everywhere.
  - **Unlock pop-ups**: split from the chat message, so toasts and chat lines can be turned off separately.
- Changing the server URL in the config now clears the old session and logs in to the new server.
- The equip hint in the unlock message names whatever key the player has bound.

## 0.9.0 · 2026-10-30 · `0ec3fda`
**Shop animations: comet, aurora, charge, ripple, converge**

- Five new per-letter animations in `EffectColors`, each sold as an animated palette on a new palette (static 12, animated 20):
  - nebula/comet
  - borealis/aurora
  - storm/charge
  - blossom/ripple
  - brass/converge
- Backlight (cycling text shadow) was left out because vanilla nametags draw no text shadow.
- The client game test now shoots each animation 15% into its beat, so animations that rest between passes are caught moving.
- Shop: 52 → 62 items. The shop total (880) deliberately exceeded the earnable points until the next batch of achievements, so `TestFontRulesAndPricing` failed on this commit alone.

## 0.8.1 · 2026-10-29 · `ffa0ebf`
**Ritualist text styling**

- Simplified the Ritualist achievement's styled title in the catalog.

## 0.8.0 · 2026-10-29 · `71815c7`
**Admin purchase reset, audit log and detector expansion**

- **Admin:**
  - Reset every player's purchases at once, with refunds and title styles cleared.
  - Added a confirmation page that spells out what the reset changes.
  - Dashboard messages now show after actions.
  - The moderation log records refunds and the action taken.
  - Players can no longer refund their own purchases.
  - Added tests for the reset and the moderation log.
- **Detectors:** many new `WynntilsDetectors` trigger types:
  - `combatLevel`, `combatLevelAnyClass` and `totalProfessionLevel`
  - `inventoryItem`, `itemSet`, `itemRarityCount` and `shinyItem`
  - `dungeon` and `dungeonSet`
  - `characterRoster`, `gamemodeLevel`, `playtime`, `accountAge` and `leaderboard`
- **Unlock batching:** new `UnlockBatcher` holds unlocks until they stop arriving, then announces them together. First logins, or a catalog that gained achievements, claim dozens at once and Wynntils loads its data in stages, so announcing each one buried the player in toasts.
- The unlock toast, achievements page and shop page were updated to match.
- Repriced the shop after the font batch: total 994 → 720.

## 0.7.1 · 2026-10-29 · `048421a`
**Update deployment configuration for the server**

- Revised `deploy/README.md`, `compose.yaml` and `.env.example`.
- Added more steps to the server image workflow.

## 0.7.0 · 2026-10-29 · `16ea821`
**Wynncraft font styles**

- New `TitleFonts` and an expanded `WynnFonts`: ten font styles in three groups, still loaded from the player's own Wynncraft pack (nothing is copied):
  - 3 text fonts
  - 3 scripts: Wynnic, High Gavellian and Old Fruman
  - 4 banners: pill, box, flag and ribbon
- Banners use one solid background and a separately picked letter color. The title must be ASCII letters, digits and spaces, and incompatible choices are disabled with explanations.
- Shop: 42 → 52 items. Font items declare which color-source kinds and formats they allow.
- The Styles page, style chips, `TitleRenderer` and `TitleRun` handle fonts, with a vanilla fallback when the pack is missing.
- New `TitleFontsTest`, expanded Go catalog tests and a font gallery game test.
- Shop pricing rules rewritten; the TODO list was reorganized.

## 0.6.0 · 2026-10-29 · `2b98b49`
**Docker image, moderation dashboard and icon review**

- Server `Dockerfile`, `.dockerignore`, `deploy/` (compose file, `.env.example`, a long deployment guide) and a GitHub Actions workflow that builds the server image.
- Optional moderation console (`internal/api/admin.go`, dashboard HTML/CSS, `store/moderation.go`) on its own `WT_ADMIN_ADDR` listener, never mounted on the public API. It has player search, reported claim evidence, confirmed revoke/restore actions and an audit history. Developer grants are limited to `WT_DEV_UUID`.
- Reviewed the achievement icons against the wiki and the Wynncraft pack, updating 91 icon entries. The sources and choices are in `docs/achievement-icons.md`.
- New icon gallery game-test screen.

## 0.5.0 · 2026-10-28 · `a284f1b`
**Own UI kit replaces owo-lib, 129 new achievements**

- Removed the owo-lib dependency. It was replaced by an in-house kit in `mc/screen/ui` (`Box`, `Element`, `Label`, `ScrollView`, `UiScreen`, `Tween`, `Size`, `Insets`, `Align`) plus a new `TabBar`.
- Reworked the Titles, Styles, Shop and Achievements screens and `StyleChips` onto the kit, with an `owned` marker sprite.
- Catalog: 35 → **164 achievements / 592 pts**, in 11 categories.

## 0.4.0 · 2026-10-28 · `93d4401`
**Redesigned toasts, animated palettes and rim sprites**

- New unlock toast art (`UnlockToastArt`) with bronze, silver, gold and stone plaques and a `toast` sprite.
- New `EffectColors`, the single place nametags, chat and screens get their per-letter colors, plus the `TitleAnimation` enum.
- New shop items: animated palettes (Heartbeat, Blood Tide, Wildfire, Cinders, Golden Hour, Riptide, Frostglint, Meltdown, Canopy, Regalia, Starfall).
- Gold, silver and prismatic chip rims for the shop.
- `EffectColorsTest`, Go tests for the new rules, and a `TODO.md`.
- Banner pill glyph support in `WynnFonts`.
- Shop: 42 items, total 205 → 216.

## 0.3.0 · 2026-10-28 · `fa23766`
**Title styles and the shop**

- **Server:**
  - Shop catalog (`shop.json`, `shop.go`) with colors, palettes, rainbow and formats.
  - Style validation and `Restyle`.
  - `GET/POST /v1/me/style`, `POST /v1/shop/buy` and `POST /v1/shop/reset`.
  - Per-player balance: points earned minus prices paid.
  - Purchases are final and store the price paid.
- **Mod:**
  - New Styles page, Shop page and `StyleChips`.
  - `TitleStyle`, with a `RaidDetector`.
  - Extended `Catalog` and `ApiClient`.
- Chip sprites.
- Achievements rewritten and expanded: 30 → 35 (129 pts, 7 categories). Shop starts at 42 items / 205.

## 0.2.0 · 2026-10-28 · `2df4da9`
**New UI look**

- Redrew the whole sprite set through `draw-ui-sprites.py`: a bigger stone panel, ribbon, eye, tab states (normal, hover, selected) and a plaque and gem cleanup.
- Added `OutlinedText`, a refreshed `WynnUi` palette and the new `TitlesScreen` layout.
- Art and layout only; no server or catalog changes.

## 0.1.0 · 2026-10-28 · `ce8ee36`
**Achievements screen, rarity and the owo-lib UI**

- New owo-lib screens: an `AchievementsPage` trophy list with categories, progress bars, rewards, unlock dates and rarity, and a `WynnUi` palette.
- New `WynnFonts` loads the Wynncraft pack's body and small-caps fonts by id, with a vanilla fallback.
- **Rarity:**
  - `GET /v1/stats/achievements` returns the share of WynnTitles players holding each achievement.
  - The server caches the result for `WT_STATS_TTL`.
  - The mod gets it through `StatsStore`.
- Tooling: `scripts/smoke-test.sh` (the one-command offline test), `draw-icon-frames.py` and `draw-ui-sprites.py`.
- Extended the game test and the server contract test.

## 0.0.5 · 2026-10-28 · `37bba96`
**Start the GUI rebuild**

- Removed the first-generation widget sprites (panel, list rows, divider and similar) ahead of the rebuild. Binary deletions only; the new screens arrive in 0.1.0.

## 0.0.4 · 2026-10-28 · `ac5093d`
**Update git ignore again**

- One more `.gitignore` entry.

## 0.0.3 · 2026-10-28 · `6de2ad9`
**Update git ignore**

- Stopped tracking `mod/logs/` (the game logs committed in the initial commit).

## 0.0.2 · 2026-10-28 · `14cda19`
**Achievement detection updates**

- `Detector` gained `supports(trigger)`. An achievement whose trigger the mod can't check is skipped, and the achievements screen asks the player to update.
- The statistics detector became a class backed by a lookup table (`mobsKilled`, `chestsFound`).
- `AchievementTracker` and `WynnTitles` wiring updated, with new unit tests (`AchievementTrackerTest`).

## 0.0.1 · 2026-10-28 · `7b1a8cf`
**Initial commit**

- **Fabric client mod** (a Wynntils addon):
  - Mojang session login.
  - Titles over nametags.
  - A titles and achievements screen.
  - Unlock toasts.
  - Cloth Config options.
  - Icons that resolve from the Wynncraft pack's item models by index. Nothing is copied from the pack, with a vanilla fallback.
- **Go server** with SQLite:
  - Auth through the Mojang session handshake.
  - Embedded, validated achievement catalog (30 achievements / 106 pts, 6 categories).
  - Batch title lookups served from memory.
  - Claim, equip and catalog endpoints.
- Offline JUnit tests, Go tests, a client game test, a README, `AGENTS.md`, `MODRINTH.md`, `PORTING.md` and an MIT license.
