# Achievement icon review

Reviewed 2026-10-29 against the current **164-achievement** catalog, the official
[Wynncraft Wiki](https://wynncraft.wiki.gg/), and the cached Wynncraft 2.2 pack
whose `pack.mcmeta` says **Oct. 28, 2026**. The pack was found in the Modrinth
`wynn testing` profile, download hash `a43ae4e632c33f91d6cdb9131f8b646e15a05869`.
Cached Wynntils `dataStaticGear` and `dataStaticIngredients` supplied the item-icon
selectors; those were compared with the actual pack item definitions. No pack
textures, fonts, or models are included in the repository.

The catalog remains the only icon configuration. This document records the
reasoning and distinctions between exact item matches and thematic symbols.
Existing relevant icons were retained; changing every model merely to make it
unique would replace recognizable content with unrelated artwork.

The completed pass changes 91 icon entries (56 model choices and 66 fallbacks,
with overlap). All 161 selected pack models resolve, all 164 fallback items are
registered, and the full smoke test including the Java/Go contract and in-game
checks passed. The twelve contact sheets, achievement menus and unlock toasts
were inspected. A copy of this review's screenshots is saved in
`mod/build/icon-review/` so another client-test run does not replace the previews.

## Selected preferences

- Keep each raid's emblem at every completion tier. Tier frames identify bronze,
  silver and gold. The pack has individual emblems for the first four raids;
  Wartorn Palace retains the generic raid emblem because this cached pack has no
  dedicated Wartorn Palace content emblem.
- Borange Fluff uses **orange wool**; Eyes Yet Open uses an **Eye of Ender**.
  Cached ingredient data identifies both actual items as custom player heads,
  not selectable resource-pack models. They intentionally have no `icon.model`.
- Fruma uses crown and flame imagery: Queen's Crown for Queen's Recruit, soul
  flame for Overture to Despair, and burning flame for Apotheosis. These are
  symbols for the quests, not assertions about quest rewards.

## Specific item and reward matches

All paths below are relative to `minecraft:item/`.

| Achievement | Model or item | Evidence |
| --- | --- | --- |
| Grandmother | `wynn/weapon/archer/bow_earth_c`; crossbow fallback | [Wiki](https://wynncraft.wiki.gg/wiki/Grandmother) calls it a bow with crossbow lore; cached icon `bow.earth3` maps to this model. |
| Alkatraz | `wynn/weapon/warrior/spear_earth_a`; mace fallback | [Wiki](https://wynncraft.wiki.gg/wiki/Alkatraz) identifies an earth spear with maul lore; cached icon `spear.earth1` maps to this model. |
| Little Rocket Mushroom | `wynn/ingredient/brown_mushroom`; brown mushroom fallback | [Talking Mushroom](https://wynncraft.wiki.gg/wiki/Talking_Mushroom) is a Brown Mushroom, rather than a garden gnome. |
| Tower of Ascension | `wynn/armor/helmet/pale_iron_helmet`; iron helmet fallback | [Champion Helmet](https://wynncraft.wiki.gg/wiki/Champion_Helmet) is a quest reward; cached gear icon is `helmet.pale_iron`. |
| The Qira Hive | `wynn/weapon/warrior/spear_multi_c`; purple concrete fallback | [Infused Hive weapons](https://wynncraft.wiki.gg/wiki/Master_Weapon_Merchant) cost a Master Voucher. Cached Infused Hive Spear icon is `spear.multi3`. [Master Voucher](https://wynncraft.wiki.gg/wiki/Hive_Vouchers) uses Purple Concrete. |
| Morph armour set | `wynn/armor/helmet/diamond_helmet`; diamond helmet fallback | [Morph-Stardust](https://wynncraft.wiki.gg/wiki/Morph-Stardust) is the set's Diamond Helmet; cached icon is `helmet.diamond`. |
| Hive armour set (achievement named Hive Mind) | `wynn/armor/chestplate/diamond_chestplate`; diamond chestplate fallback | [Soulflare](https://wynncraft.wiki.gg/wiki/Soulflare) is a Diamond Chestplate in one of the accepted sets. This achievement does **not** require the unrelated helmet also named Hive Mind. |
| Shiny item | `wynn/gui/store/consumable/token/shiny_adder` | Uses a shiny-specific symbol rather than suggesting all shiny items are Fabled. |
| Shh, Library | Vanilla TNT | [Dynamite](https://wynncraft.wiki.gg/wiki/Dynamite) uses TNT. The similarly named pack pet is not the item. |
| Bob's Tear, Mega Fern, Blazing Fireball | Existing named ingredient models | Match cached ingredient names and pack models. |
| Witherhead's Ribs, Redbeard's Hair, The Eye's Pupils | Existing dungeon token models | Keep the relevant boss-token artwork. |
| Archaic Writing | Existing named Outer Void drop model | Keep the actual item model. |

## Class and profession progression

Class tiers keep four silhouettes: wood starter, gold weapon, advanced fire
weapon, multi-element weapon. The starter Mage now uses `wand_basic_wood`, and
the level-50 Mage uses `wand_basic_gold`, matching the other classes. These are
class progression symbols; they do not imply owning a specific weapon is required.

| Class | Vanilla fallbacks: level 1 → 50 → 100 → 120 |
| --- | --- |
| Archer | Bow → Crossbow → Spectral Arrow → Target |
| Warrior | Trident → Iron Axe → Diamond Axe → Netherite Axe |
| Mage | Stick → Blaze Rod → End Rod → Nether Star |
| Assassin | Wooden Sword → Iron Sword → Diamond Sword → Netherite Sword |
| Shaman | Torch → Soul Torch → Amethyst Shard → Totem of Undying |

Profession level-100 icons retain their profession emblems. Level-132 icons use
higher-tier tools, materials, or products: titanium gathering tools, wisdom potion,
titanium chestplate, spicy meal, cinnabar gem/ingot, redwood paper/plank and heather
string. [Gathering Tools](https://wynncraft.wiki.gg/wiki/Gathering_tools) lists
Titanium T13 above Dernic T12. The total-professions cap uses the profession-info
emblem instead of an empty trophy base.

The pack's `wynn/housing/profession/trophy/<profession>` files are **not selected
by an inventory item**. File existence alone is insufficient: the pack test
rejected these models, so they are not used.

## Other thematic choices

These models are usable in the cached pack but are intentionally interpretations,
not exact quest items or earned rewards. Alternatives can be swapped in the catalog.

| Achievement or group | Choice | Alternative |
| --- | --- | --- |
| A Journey Further | Basic spear, representing Aledar | Story-quest book emblem |
| Off the Rails | Minecart pet's menu icon | Vanilla minecart only |
| ??? | Astral Alloy, representing the White Meteor | Quest-book emblem |
| Reincarnation | Bob housing statue | Bob's Tear |
| Dwarves and Doguns | Dogun Alert Sigil | Molten mining material |
| WynnExcavation | Amadel crystals effect icon | Crystal Dust ingredient |
| Legendary Island token | Golden Coin silhouette; diamond fallback | Vanilla diamond only; [the token tiers use different vanilla carriers](https://wynncraft.wiki.gg/wiki/Legendary_Tokens) |
| Great Bridge | Troll Hair ingredient | Wooden plank or map |
| Moon | Existing Halfmoon Crescent cosmetic | Moonlight Essence ingredient |
| Time Valley | Time-trial hourglass | Vanilla clock only |
| Playtime | Elapsed-time icon → Luxury Timepiece → character history | Same timepiece at all tiers |
| Account age | Green present → red present | Vanilla cake at both tiers |
| Leaderboard placements | Medallion → Honoured Commander's Medal | Crown imagery |
| Guild wars | Guild tome → Bloodstained Steel → Crown of Chains | Guild tome at all tiers |
| Guild membership/rank | Guild icon → guild boost icon → Fancy Crown | Vanilla banners and helmet |
| Hardcore levels 50 / 100 | Bat Heart → Undead Heart; redstone → totem fallbacks | Other heart-shaped ingredients |
| Hunted / Hunter’s Calling | Thunder dagger as a combat symbol | Bounty Hunter cosmetic |
| Ironman | Iron ingot | Other iron crafting material |
| Craftsman | Weaponsmithing station | Profession-info emblem |
| HUICH | Jagged Crown for the combined challenge | Insanity Star ingredient |
| Ultimate Ironman | Locked chest; Ender Chest fallback | Standard Ironman emblem |
| Dusty Key library secret | Skeleton Key silhouette | Vanilla tripwire hook only |

Menu models for Hunted, Hardcore, Ironman, Craftsman, reskin controls, lootrun
start and crafting were rejected in the screenshot review: several include
built-in parchment labels or other geometry extending outside the icon frame.
The meteor spell and raid sigils also overflow. Roster milestones use the
ability-tree and skill-crystal emblems, crafting uses the profession-info emblem,
lootrun camps use the lootrun tome, and total raid counts use armour/weapon tomes.
The individual raid emblems remain unchanged, as requested.

## Verification

Run `WT_WYNN_PACK="<cached pack>" scripts/smoke-test.sh`. In addition to the unit
and Java/Go contract tests, the client test checks every model resolves after the
real pack loads and every vanilla fallback is registered. It produces six contact
sheets each for vanilla and Wynncraft rendering, covering all 164 achievements at
their actual 28×32 frame size, with a separate small fallback beside each icon:

- `mod/build/run/clientGameTest/screenshots/*_wynntitles-icons-fallbacks-1.png` through `-6.png`
- `mod/build/run/clientGameTest/screenshots/*_wynntitles-icons-wynnpack-1.png` through `-6.png`

Inspect all sheets, the achievement menu, and the unlock toast. A selectable model
can still be visually unsuitable, so a passing model-index test is not the final
visual check. Screenshots stay in ignored build output.
