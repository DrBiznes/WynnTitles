# TODO

## 1. Redo achievement icons

Reviewed all 164 current achievements against the wiki, cached Wynntils item data and the Oct. 28, 2026 Wynncraft pack. Updated 91 icon entries in `server/catalog/achievements.json`; see [the icon review](docs/achievement-icons.md) for sources, symbolic choices and alternatives.

- [x] Pick relevant, selectable Wynncraft item models and distinguish class/profession tiers. Keep each raid's emblem across its tiers, as requested; Borange Fluff, Eyes Yet Open and Dynamite intentionally use vanilla items
- [x] Give each one a vanilla fallback that still makes sense without the Wynncraft pack
- [x] Run the `wynnPack` test against a cached pack from a Wynncraft profile's `downloads/` folder
- [x] Check the icons in the smoke-test screenshots, including contact sheets for every achievement with and without the real pack

Point at models only. Never copy textures out of the Wynncraft pack.

## 2. Add more achievements

The catalog now has **164 achievements worth 592 points**, across quests (20), raids (17), professions (31), class levels (24), exploration (11), collection (18), statistics (10), dungeons (16), gamemodes (7), guilds (6) and secrets (4).

- [x] Draft the new list, then add it to `server/catalog/achievements.json`
- [x] For any new trigger type, add a validation case in `server/catalog/catalog.go` and a detector in `WynntilsDetectors.java`
- [x] Keep most title words plain; give a native style only to the big ones
- [x] Revisit shop prices afterwards (section 5)

### Data sources

- [ ] Check whether any achievement data currently read from the Wynncraft API can come from Wynntils events or Wynntils info instead; move it where reliable, and keep the API where Wynntils does not expose equivalent data.

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

## 5. Shop pricing scales with achievements

Retuned before launch so completionists can buy most of the shop: it costs **720 points** against **592 earnable**, so all achievements buy **82.2%** of it. `TestFontRulesAndPricing` checks the ratio stays within 80–85%. Ship future achievements and shop items together in batches to stay in that band; roughly 100 new achievement points pays for about 120 points of new items. Purchases keep their price paid, so never lower an existing price after launch; to make the shop cheaper, raise achievement points instead, which lifts every balance. New item kinds (glow, shaders) should cost more than rainbow.

| Kind | Items | Price |
| --- | --- | --- |
| Color | 15 | 4 |
| Format | 4 | 6 |
| Palette | 11 | 12 |
| Animated palette | 11 | 20 |
| Rainbow | 1 | 45 |
| Plain font | 3 | 15 |
| Script font | 3 | 22 |
| Banner font | 4 | 32 |

- [x] Recheck shop total vs earnable points after this batch
- [x] Add a pricier new item kind (fonts) alongside retuned existing prices
- [x] Keep resets free, returning the full price paid (dashboard only; players cannot refund)

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

## 7. Font styles in the shop

Implemented a new shop item kind that draws the title in one of the Wynncraft pack's fonts, the same way Wynncraft and Wynntils use them. Point at font ids only (`minecraft:<id>`, through `WynnFonts`). Never copy font sheets or Wynntils' fontmaps; write our own codepoint tables, as `WynnFonts.pill` already does.

### Fonts to offer

Checked against the cached pack and Wynntils 4.2.16 (`core/text/fonts`).

| Font | Pack id | What it looks like | Colors it can show | Formats |
| --- | --- | --- | --- | --- |
| Wynncraft | `language/wynncraft` | Wynncraft's own text font | Everything | All |
| Small caps | `language/five` | Menu header small capitals (upper-cased) | Everything | All |
| Outline | `language/wynncraft_outline` | Letters with a built-in dark outline | Everything; outline stays dark under tints | All; shadow off |
| Wynnic | `language/wynnic` | Wynnic script, ASCII-mapped | Everything | All |
| High Gavellian | `language/high_gavellian` | High Gavellian script, ASCII-mapped | Everything | All |
| Old Fruman | `language/old_fruman` | Old Fruman script, ASCII-mapped; U+E000–E004 are extra symbols | Everything | All |
| Pill banner | `banner/pill` | Letters on a rounded pill, like Wynncraft's chat badges | Solid only: one pill color and one letter color | None |
| Box banner | `banner/box` | Letters on a square box | Solid only, two colors | None |
| Flag banner | `banner/flag` | Letters on a flag with notched ends | Solid only, two colors | None |
| Ribbon banner | `banner/ribbon` | Letters on a ribbon | Solid only, two colors | None |

Not worth it for now: `banner/keybind` (key caps, 12 px tall), the `offset/*` fonts (big or shifted text that breaks nametag line height), `nameplate/*` (health bar pieces), and the icon fonts (`common`, `merchant`, `profession`, `chat/tile`).

How the banners work: each banner font has a `_background` glyph per character (U+E030–E05F, U+E080–E082, edges at U+E060/E062, gap U+E061) and a matching `_foreground` glyph (U+E000–E02F). Wynntils' `BannerBoxFont.buildMessage` draws the background run in one color, steps back with the `space` font, then draws the foreground letters over it in a second color. `WynnFonts.banner` draws both layers, matching each foreground advance to its background slice and disabling shadow so chat stays legible. `WynnFonts.pill` shares the background builder for the existing cutout chat badge.

### Why banners are solid only

- The tint applies per glyph, so a gradient or animation would paint each letter's slice of the pill a different color and the shape breaks into stripes.
- Bold draws every glyph twice one pixel over, italic skews it, and underline and strikethrough draw lines through the shape. All of them wreck the banner.
- Obfuscated swaps glyphs and would scramble the pill pieces (and stays native-only anyway).
- The supported title banner alphabet is ASCII letters, digits and spaces. Other characters (apostrophes, accents and punctuation) leave the banner unavailable for that title, with the first unsupported character in its tooltip. No characters are silently replaced.

### Customization screen

- [x] Each font item declares what it supports. Put the rule in `server/catalog/shop.json` (`"allows": ["color"]` and `"formats": false`) so the server and mod read one source instead of hard-coding it
- [x] When a font is equipped, grey out every chip it can't show (palettes, animated palettes, rainbow, formats for banners). Greyed chips are desaturated, can't be clicked, and their tooltip says why ("Pill banner: solid colors only")
- [x] Picking a font while an incompatible style is equipped: the preview shows exactly what will be kept, and confirming drops the rest (a palette falls back to its first color). Nothing gets silently mixed
- [x] Banner fonts get a second color pick for the letters, defaulting to white or black by contrast with the pill color
- [x] Grey out banner fonts for titles with characters that have no banner glyph, and say which character is the problem
- [x] Native style decision: choosing any shop font explicitly replaces the whole native style, including `obfuscated`, just like other shop styles. Selecting Native restores it. Banner coverage is checked against the word’s plain text
- [x] When the Wynncraft pack isn't loaded, fonts can't be previewed. Show the chips with a "Needs the Wynncraft resource pack" tooltip and render titles in the vanilla font with their solid color

### Plumbing

- [x] New `font` kind: a validation case in `server/catalog/shop.go`, a case in `Catalog.restyle` and `ShopItem.supported` in the mod, and a group in `StyleChips.java`
- [x] The server's `Restyle` rejects incompatible combos too (banner plus palette, banner plus formats), and the contract test covers it
- [x] Render fonts in `TitleText` (chat and screens) and `TitleRenderer` (nametags). Measure banner width including the edges so nametags stay centered
- [x] Generalize `WynnFonts.pill` into banner drawing with background and foreground layers for pill, box, flag and ribbon. Add all the new font ids to the reload check
- [x] Price them as a new tier (see section 5). Prices were 22/30/44 at first, now plain fonts 15, scripts 22, banners 32 (section 5). Shop total is 720 vs 592 earnable points
- [x] Run `WT_WYNN_PACK=... scripts/smoke-test.sh` and look at every font in chat, the shop and nametags, with and without the pack

Font checks include all ten server/mod round trips, incompatible-combination rejections, font-switch previews, a banner width assertion, and screenshots of every font in contact sheets, chat, the shop and nametags, with and without the real pack. Assets remain in the external pack; the mod only supplies ids and its own codepoint logic.
