# CLAUDE.md

WynnTitles: a Fabric client mod for Wynncraft plus a small Go server. There is deliberately no website; everything happens in game. See README.md for the architecture and API.

## Commands

```bash
scripts/smoke-test.sh                                         # all tests, offline, incl. in-game nametag test
cd server && go test ./...                                   # server tests
cd server && WT_OFFLINE_AUTH=true go run ./cmd/wynntitles     # local server on :8080
cd mod && ./gradlew build                                     # mod jar → mod/build/libs
cd mod && ./gradlew runClient                                 # dev client against localhost:8080
cd mod && ./gradlew test -Dwynntitles.testServer=http://localhost:8080   # Java↔Go contract test
cd mod && ./gradlew test -Dwynntitles.wynnPack="<cached server pack>"     # catalog icons vs the real Wynncraft pack
python3 scripts/draw-icon-frames.py mod/src/main/resources/assets/wynntitles/textures/gui/sprites/icon   # redraw tier frames
python3 scripts/draw-ui-sprites.py mod/src/main/resources/assets/wynntitles/textures/gui/sprites/widget  # redraw screen widgets
WT_WYNN_PACK="<cached server pack>" scripts/smoke-test.sh                                         # also screenshot with the real pack
```

## Rules

- `server/catalog/achievements.json` is the only place achievements are defined, and `server/catalog/shop.json` the only place shop items and prices are. The server validates both at startup (`catalog.Parse`); the mod downloads them. There is no codegen.
- Most title words are plain (no `runs`). Give a word a native style only when the achievement is a big one. Never sell `obfuscated` or a native-only animation (decipher, glitch, eclipse, strike, heartbeat, swell, ascend, reel, prism) in the shop; they are reserved for native styles.
- Native styles must not be buyable: effects use their own hex `colors`, never a shop `palette`; solid colors are hex, not named; no two native effects match (`TestEmbeddedNativeStylesAreUnique`). Native effects must stay readable in every frame (`EffectColorsTest.nativeStylesAreReadable`).
- A new shop item kind needs a validation case in `server/catalog/shop.go`, a case in `Catalog.restyle` and `ShopItem.supported` in the mod, and a group in `mc/screen/StyleChips.java`. Keep `Catalog.restyle` in step with the server's `Restyle`; the contract test compares them.
- A new trigger type needs a validation case in `server/catalog/catalog.go` and a detector in `mod/.../achievements/WynntilsDetectors.java`.
- Claims are trusted on purpose (cosmetic mod). Do not add Wynncraft API verification. Keep evidence in `unlocks` so moderation tools can revoke later.
- Java records in `mod/.../api/ApiTypes.java`, `catalog/Catalog.java` and `titles/*` mirror the Go JSON. Change both sides together and run the contract test.
- Keep Minecraft-version-sensitive code in `mod/.../mc/` and the mixin; see mod/PORTING.md.
- Schema changes are new entries appended to `migrations` in `server/internal/store/store.go`; never edit a shipped migration.
- Never set `WT_OFFLINE_AUTH` in production.
- Achievement icons name a Wynncraft item model (`icon.model`, relative to `minecraft:item/`) and a vanilla `icon.fallback`. The mod renders whatever the loaded pack shows for that model (`mc/Icons.java`). Never copy textures out of the Wynncraft pack into this repo. After changing icons, run the `wynnPack` test against a cached pack from a Wynncraft profile's `downloads/` folder.
- Screens follow Wynncraft's menus: build them from `mc/screen/WynnUi.java` (frame, plaque, buttons, wells, palette) and set text through `mc/WynnFonts.java`, which uses the Wynncraft pack's fonts only when they are loaded. Widget sprites come from `scripts/draw-ui-sprites.py`; edit the script, not the PNGs. Never copy textures or font sheets out of the Wynncraft pack.
- After UI or nametag changes, run `scripts/smoke-test.sh` and look at the screenshots in `mod/build/run/clientGameTest/screenshots`; assertions alone missed invisible text once.
