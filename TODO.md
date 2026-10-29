# TODO

## 1. Redo achievement icons

All 35 achievements need a fresh pass on `icon.model` and `icon.fallback` in `server/catalog/achievements.json`. Right now 20 of them are class-level tiers sharing five fallbacks (bow, trident, stick, iron sword, blaze rod, four each).

- [ ] Pick a distinct Wynncraft item model per achievement so tiers look different at a glance
- [ ] Give each one a vanilla fallback that still makes sense without the Wynncraft pack
- [ ] Run the `wynnPack` test against a cached pack from a Wynncraft profile's `downloads/` folder
- [ ] Check the icons in the smoke-test screenshots

Point at models only. Never copy textures out of the Wynncraft pack.

## 2. Add more achievements

The catalog has 35 achievements worth 129 points, and 21 of those are class levels. The goal is 50–150, spread across more categories.

| Category | Today | Ideas |
| --- | --- | --- |
| Quests | 1 | Main story milestones, mini-quest sets |
| Raids | 5 | Clear counts, per-raid mastery |
| Professions | 2 | Per-profession level tiers |
| Exploration | 2 | Discoveries, territories, cave sets |
| Collection | 2 | Mythics, mounts, cosmetics |
| Statistics | 2 | Mobs killed, dungeons, chests opened |

- [ ] Draft the new list, then add it to `server/catalog/achievements.json`
- [ ] For any new trigger type, add a validation case in `server/catalog/catalog.go` and a detector in `WynntilsDetectors.java`
- [ ] Keep most title words plain; give a native style only to the big ones
- [ ] Revisit shop prices afterwards (section 5)

## 3. Wynncraft-style chat prefix

Chat lines currently start with a plain aqua `[WynnTitles] ` tag (`Mc.tagged` in `mc/Mc.java`). The unlock message in `WynnTitles.java` builds its own gold text and has no tag at all.

- [x] Replace the bracket tag with a unicode prefix badge in the style Wynntils and Wynncraft use
- [x] Use the Wynncraft pack's font only when it is loaded (through `WynnFonts`), with a plain fallback otherwise. Never copy font sheets.
- [x] Send the unlock message through the same prefix so every mod line looks the same
- [x] Check it in the smoke-test screenshots, with and without the real pack

## 4. Toast redesign

The unlock toast (`mc/UnlockToast.java`) still draws vanilla's `toast/advancement` sprite with a hard-coded gold title, so it doesn't match the rest of the mod.

- [x] Add a `toast` sprite to `scripts/draw-ui-sprites.py` using the `WynnUi` palette and frame style (edit the script, not the PNG)
- [x] Take the title and text colors from the `WynnUi` palette instead of constants
- [x] Consider drawing the tier frame (bronze, silver, gold) around the icon
- [x] Check the toast in the smoke-test screenshots

## 5. Shop pricing scales with achievements (ongoing)

The whole shop costs 216 points against 129 earnable today, so current points buy about 60% of it. Keep roughly that ratio as achievements are added.

| Kind | Items | Price |
| --- | --- | --- |
| Color | 15 | 2 |
| Format | 4 | 3 |
| Palette | 11 | 5 |
| Animated palette | 11 | 9 |
| Rainbow | 1 | 20 |

- [ ] After each batch of achievements, recheck shop total vs earnable points
- [ ] Prefer adding pricier new item kinds (glow outline, shader effects) over only raising prices
- [ ] Resets stay free and refund everything while there are no real players

## 6. Shop overhaul before release

Every animated palette uses the same scrolling gradient, just with different colors. Each option should feel different, and the shop itself should look cooler.

- [x] Redo the 11 palettes (red, red purple, flame, ember, sunset, ocean, ice, toxic, forest, royal, void) so each has its own clear identity
- [x] Add distinct animation styles, for example:
    - Pulse: the whole word breathes between two colors
    - Shimmer: a bright highlight sweeps across the word
    - Wave: colors ripple letter by letter
    - Flicker: fire or glitch style random flashes
- [x] Shop page: animated previews and a stronger look for the pricier tiers
- [x] Each new item kind needs a case in `server/catalog/shop.go`, `Catalog.restyle` and `ShopItem.supported` in the mod, and a group in `StyleChips.java`. Keep `Catalog.restyle` in step with the server's `Restyle` and run the contract test.
- [x] Never sell `obfuscated`; it stays reserved for native styles
