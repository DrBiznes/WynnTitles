# Achievement ideas

Brainstorm from wiki research (wynncraft.wiki.gg, docs.wynncraft.com, the Wynncraft API and the Wynntils jar), started 2026-10-28.

- ✦ marks a word with a native style.
- `server/catalog/achievements.json` is the source of truth. The tables below are a snapshot of what stage 1 added.

## Stage 1: added to the catalog

Stage 1 adds 129 achievements worth 463 points, bringing the catalog to 164 achievements and 592 points. It also adds 16 new trigger types (validated in `server/catalog/catalog.go`, detected in `WynntilsDetectors.java`) and 7 new statistic keys.

| Trigger | Reads |
|---|---|
| `statistic` (new keys `wars`, `dungeonsCompleted`, `raidsCompleted`, `lootruns`, `caves`, `worldEvents`, `completedQuests`) | `GlobalData` from Wynntils' account info |
| `dungeon`, `dungeonSet` | API dungeon completions, like `raid` |
| `professionLevel`, `professionLevelAll` | `Models.Profession` for the current character |
| `characterRoster`, `gamemodeLevel`, `playtime`, `accountAge`, `guild`, `leaderboard` | the full API profile, shared through `PlayerProfile` and refreshed every 5 minutes |
| `coordinates`, `itemAtLocation` | player position (a circle of `radius` around `x`, `z`) plus the inventory |
| `territorySet` | the Wynntils territory model; visits are kept in `config/wynntitles/territories.json` |
| `itemRarityCount`, `shinyItem`, `itemSet` | Wynntils item annotations over every carried and worn stack |

### Checked against live data

- **Quest names:** every quest name used matches a real player's API quest list, including "???" and "A Hunter's Calling" (the API has no "???" prefix on it).
- **Dungeon names:** the API keys match, including "Timelost Sanctum" and "Corrupted Lost Sanctuary". The API also still lists a legacy "Lost Sanctuary", which no set requires.
- **Territory names:** every territory name used is in the API's territory list.
- **Profession max:** 132 per profession (1584 total). The top players on the professions leaderboard are at exactly that.
- **`completedQuests`:** the sum over every character, with mini-quests included. Top accounts are near 3,000, so the ladder is 50, 300 and 1,000.
- **`lootruns`:** reads 7 for every top player, so it counts lootrun camps finished, not runs. It is one achievement for all seven camps instead of a ladder.
- **Caves and world events** are summed across characters too, so the ladders are 25 and 250 for caves, and 100 and 500 for world events.

### Still to check in game

- **Into the Void** uses the wiki's coordinates for The Lonely Abyss (13777, -4042) with a 64-block radius, because The Void is not a territory.
- **Little Rocket Mushroom** uses the coordinates of Mehme's NPC (-7112, -12825), and the wiki doesn't say whether his home can be reached outside the ??? quest. Also check that the Talking Mushroom item exists outside the quest.
- **Library coordinates** for the Overdue, Tears for Bob and Shh, Library achievements come from the Secret Discoveries page, each with an 8-block radius.
- **Hardcore:** gamemodeLevel assumes a dead Hardcore character drops the `hardcore` mode in the API.
- **Item names** in the item sets (Morph pieces, Hive armour) come from the wiki's set and Hive Shop pages.

### Problems in shipped achievements

- `loc_moon` triggers on the territory "The Moon", which is not in the API territory list, so it may never unlock. It probably needs a `coordinates` trigger.
- `item_li_token` checks for "Legendary Island Token", but the wiki lists only Bronze, Silver, Gold and Diamond Tokens.

### quest (19)

| Achievement | Tier | Trigger | Prefix | Suffix |
|---|---|---|---|---|
| Queen's Recruit | silver | quest "Queen's Recruit" | Queen's | Vanguard |
| A Journey Further | silver | quest "A Journey Further" | Sacrificial | Friendslayer |
| Sacred Animals | bronze | quest "Tunnel Trouble" |  | Bovemist |
| Cowfusion | silver | quest "Cowfusion" | Cowfused |  |
| A Hideous Crime | bronze | quest "Cluck Cluck" | Fowl | Cluckslayer |
| Stable Story | bronze | quest "Stable Story" |  | Cavalryman |
| Off the Rails | silver | quest "Off the Rails" |  | Railfan |
| ??? | gold | quest "???" | Mehme's | ??? |
| A Hunter's Calling | gold | quest "A Hunter's Calling" |  | Quarry |
| Reincarnation | gold | quest "Reincarnation" |  | Bobslayer ✦ |
| Tower of Ascension | silver | quest "Tower of Ascension" | Ankou's | Death-Defier |
| Overture to Despair | silver | quest "Overture to Despair" | Despairing |  |
| Apotheosis | gold | quest "Apotheosis" | Deified ✦ | Demigod |
| The Qira Hive | gold | quest "The Qira Hive" | Hive-Touched | Hivebreaker |
| Dwarves and Doguns | silver | quest "Dwarves and Doguns Part IV" | Dogun-Allied | Dwarf-Friend |
| WynnExcavation | bronze | quest "WynnExcavation Site D" | Excavating | Digger |
| Adventurer | bronze | statistic completedQuests 50 |  | Adventurer |
| Storied | silver | statistic completedQuests 300 | Storied |  |
| Loremaster | gold | statistic completedQuests 1000 | Questing ✦ | Loremaster |

### class_level (3)

| Achievement | Tier | Trigger | Prefix | Suffix |
|---|---|---|---|---|
| Many Faces | bronze | characterRoster 1 |  | Many-Faced |
| Versatile | silver | characterRoster 50 | Versatile |  |
| Pentarch | gold | characterRoster 120 | Five-Sworn ✦ | Pentarch |

### profession (29)

| Achievement | Tier | Trigger | Prefix | Suffix |
|---|---|---|---|---|
| Diligent Journeyman | silver | totalProfessionLevel 250 | Diligent | Journeyman |
| Renaissance | gold | totalProfessionLevel 1000 | Renaissance | Polymath |
| Perfectionist | gold | totalProfessionLevel 1584 |  | Perfectionist ✦ |
| Woodcutting 100 | silver | professionLevel 100 |  | Lumberjack |
| Woodcutting 132 | gold | professionLevel 132 | Timbered |  |
| Mining 100 | silver | professionLevel 100 |  | Prospector |
| Mining 132 | gold | professionLevel 132 | Deep-Delved |  |
| Farming 100 | silver | professionLevel 100 |  | Harvester |
| Farming 132 | gold | professionLevel 132 | Harvest-Blessed |  |
| Fishing 100 | silver | professionLevel 100 |  | Angler |
| Fishing 132 | gold | professionLevel 132 | Tidal |  |
| Alchemism 100 | silver | professionLevel 100 |  | Alchemist |
| Alchemism 132 | gold | professionLevel 132 | Transmuting |  |
| Armouring 100 | silver | professionLevel 100 |  | Armourer |
| Armouring 132 | gold | professionLevel 132 | Plate-Forged |  |
| Cooking 100 | silver | professionLevel 100 |  | Gourmand |
| Cooking 132 | gold | professionLevel 132 | Savoury |  |
| Jeweling 100 | silver | professionLevel 100 |  | Lapidary |
| Jeweling 132 | gold | professionLevel 132 | Gem-Cut |  |
| Scribing 100 | silver | professionLevel 100 |  | Lorekeeper |
| Scribing 132 | gold | professionLevel 132 | Ink-Stained |  |
| Tailoring 100 | silver | professionLevel 100 |  | Weaver |
| Tailoring 132 | gold | professionLevel 132 | Silk-Spun |  |
| Weaponsmithing 100 | silver | professionLevel 100 |  | Bladesmith |
| Weaponsmithing 132 | gold | professionLevel 132 | Anvil-Forged |  |
| Woodworking 100 | silver | professionLevel 100 |  | Bowyer |
| Woodworking 132 | gold | professionLevel 132 | Fletched |  |
| Bountiful Gatherer | gold | professionLevelAll (4) 125 | Bountiful | Gatherer |
| Master Tradesman | gold | professionLevelAll (8) 117 | Craft-Honed | Tradesman |

### exploration (9)

| Achievement | Tier | Trigger | Prefix | Suffix |
|---|---|---|---|---|
| Into the Void | gold | coordinates | Void-Touched ✦ | Voidwalker |
| Old Man Martyn's Cabin | bronze | location "Little Wood" | Little | Woodsman |
| Gone to Travel | bronze | location "Time Valley" |  | Wanderer |
| Good Neighbour | silver | territorySet (22) | Well-Travelled | Neighbour |
| Mapmaker of Gavel | gold | territorySet (13) | Worldly | Cartographer |
| Spelunker | bronze | statistic caves 25 |  | Spelunker |
| Cave-Warden | silver | statistic caves 250 | Deepdelving | Cave-Warden |
| Stormchaser | silver | statistic worldEvents 100 |  | Stormchaser |
| Restless Defender | gold | statistic worldEvents 500 | Restless | Defender |

### collection (16)

| Achievement | Tier | Trigger | Prefix | Suffix |
|---|---|---|---|---|
| Block Party | bronze | inventoryItem "Emerald Block" x64 |  | Banker |
| Ten Stacks | gold | inventoryItem "Liquid Emerald" x640 | Gilded ✦ | Magnate |
| Grandmother | gold | inventoryItem "Grandmother" x1 | Grandmother's | Heirloom |
| Alkatraz | gold | inventoryItem "Alkatraz" x1 |  | Escapee |
| Archaic Writing | gold | inventoryItem "Archaic Writing" x1 | Gavellian | Scribe |
| Borange | silver | inventoryItem "Borange Fluff" x1 | Borange |  |
| Eyes Yet Open | silver | inventoryItem "Eyes Yet Open" x1 | Unblinking |  |
| Mega Fern | bronze | inventoryItem "Mega Fern" x1 | Fernly |  |
| Blazing Fireball | bronze | inventoryItem "Blazing Fireball" x1 | Blazing |  |
| Rib Collector | silver | inventoryItem "Witherhead's Rib" x10 | Rib-Rattled |  |
| Redbeard's Beard | silver | inventoryItem "Redbeard's Hair" x10 | Plundering | Buccaneer |
| Pupil Hoarder | gold | inventoryItem "The Eye's Pupil" x10 | All-Seeing |  |
| Mythic Hoard | gold | itemRarityCount mythic 3 | Mythic ✦ | Hoarder |
| Shiny! | gold | shinyItem 1 | Shining ✦ | Prodigy |
| Morphling | gold | itemSet (1 set) | Shapeshifting | Morphling |
| Hive Mind | gold | itemSet (5 sets) | Hive-Minded | Drone |

### statistics (8)

| Achievement | Tier | Trigger | Prefix | Suffix |
|---|---|---|---|---|
| Committed | bronze | playtime 100 | Committed |  |
| Long Haul | silver | playtime 500 |  | Wayfarer |
| Timeless | gold | playtime 1500 | Timeless ✦ | Eternal |
| Elder | silver | accountAge 5 | Elder |  |
| Ancient Veteran | gold | accountAge 10 | Ancient | Veteran |
| Ranked | silver | leaderboard 1000 | Ranked |  |
| Chart-Topper | gold | leaderboard 100 | Chart-Topping ✦ | Apex |
| Trail-Worn | silver | statistic lootruns 7 | Trail-Worn |  |

### raid (12)

| Achievement | Tier | Trigger | Prefix | Suffix |
|---|---|---|---|---|
| Wormfeeder | silver | raid x10 |  | Wormfeeder |
| Wyrmbane | gold | raid x50 |  | Wyrmbane |
| Parasite Purger | silver | raid x10 |  | Parasite-Purger |
| Unfading | gold | raid x50 | Unfading |  |
| Shard-Breaker | silver | raid x10 |  | Shard-Breaker |
| Gavel's Protector | gold | raid x50 | Gavel's | Protector |
| Anomaly | silver | raid x10 |  | Anomaly |
| Nameless No More | gold | raid x50 | Nameless ✦ |  |
| Anathema's End | silver | raid x10 | Anathema's |  |
| Scourge of the Palace | gold | raid x50 |  | Scourge |
| Seasoned Raider | silver | statistic raidsCompleted 25 | Seasoned |  |
| Raid Syndicate | gold | statistic raidsCompleted 100 | Syndicate | Raidmaster |

### dungeon (16)

| Achievement | Tier | Trigger | Prefix | Suffix |
|---|---|---|---|---|
| Sewer Rat | bronze | dungeon "Decrepit Sewers" x1 |  | Sewer-Rat |
| Arachnophobe | bronze | dungeon "Infested Pit" x1 |  | Webcutter |
| Ferryman's Toll | bronze | dungeon "Underworld Crypt" x1 | Charon's | Ferryman |
| Timelost | silver | dungeon "Timelost Sanctum" x1 |  | Timewalker |
| Sand-Swept | silver | dungeon "Sand-Swept Tomb" x1 | Sand-Swept |  |
| Frostbitten | silver | dungeon "Ice Barrows" x1 | Frostbitten |  |
| Overgrown | silver | dungeon "Undergrowth Ruins" x1 | Overgrown |  |
| Le Big Fishe | silver | dungeon "Galleon's Graveyard" x1 | Le Big | Fishe |
| Cogbreaker | gold | dungeon "Fallen Factory" x1 |  | Cogbreaker |
| Eye to Eye | gold | dungeon "Eldritch Outlook" x1 | Eldritch |  |
| Mutiny! | silver | dungeon "Galleon's Graveyard" x10 |  | Mutineer |
| Corruption Cleansed | gold | dungeonSet (8) | Corrupted ✦ | Cleanser |
| Keymaster | gold | dungeonSet (18) | Unbarred ✦ | Keymaster |
| Delver | bronze | statistic dungeonsCompleted 10 |  | Delver |
| Dungeoneer | silver | statistic dungeonsCompleted 100 |  | Dungeoneer |
| Crypt-Born | gold | statistic dungeonsCompleted 500 | Crypt-Born | Depthwalker |

### guild (6)

| Achievement | Tier | Trigger | Prefix | Suffix |
|---|---|---|---|---|
| Blooded | bronze | statistic wars 1 | Blooded |  |
| Warmonger | silver | statistic wars 100 |  | Warmonger |
| Warlord | gold | statistic wars 1000 | Ironclad ✦ | Warlord |
| Guildmate | bronze | guild recruit |  | Guildmate |
| Banner-Bearer | silver | guild chief | Banner-Bearing |  |
| Guildmaster | gold | guild owner | Crowned | Guildmaster |

### gamemode (7)

| Achievement | Tier | Trigger | Prefix | Suffix |
|---|---|---|---|---|
| Hardened | silver | gamemodeLevel hardcore 50 | Hardened |  |
| Deathless | gold | gamemodeLevel hardcore 100 | Deathless ✦ | Unscathed |
| Self-Reliant | silver | gamemodeLevel ironman 50 |  | Self-Reliant |
| Hermit | gold | gamemodeLevel ultimate_ironman 100 | Solitary | Hermit |
| Hand-Forged | gold | gamemodeLevel craftsman 100 | Hand-Forged | Artisan |
| Hunted | gold | gamemodeLevel hunted 100 | Hunted | Prey |
| HUICH | gold | gamemodeLevel hunted+ultimate_ironman+craftsman+hardcore 80 | Unyielding ✦ | Masochist |

### secret (4)

| Achievement | Tier | Trigger | Prefix | Suffix |
|---|---|---|---|---|
| Little Rocket Mushroom | gold | itemAtLocation "Talking Mushroom" | Unwelcome | Mycophile |
| Overdue | silver | itemAtLocation "Dusty Key" | Overdue | Borrower |
| Tears for Bob | silver | itemAtLocation "Bob's Tear" | Tearful | Bookworm |
| Shh, Library | silver | itemAtLocation "Dynamite" | Explosive | Archivist |
## Later: possible additions (chat parsing and other low-confidence triggers)

These depend on exact chat text, festival menus or boss messages that still need to be captured in game.

| Achievement | Tier | Trigger | Prefix | Suffix | Note |
|---|---|---|---|---|---|
| That Didn't Happen | silver | chatLine: Tasim's "Pfft, hey, that didn't actually happen!" | Embellished | Storyteller | |
| Herbert's Bane | gold | bossKilled "Doctor Legendary" | Unbowed | Challenger | |
| Grandmaster | gold | raidRank Grandmaster | Grandmaster ✦ | | |
| All In | gold | raidGambits ≥ 3 in one clear | Reckless | Gambler | |
| Prelude Survivor | gold | worldEventNamed "Prelude to Annihilation" | | Survivor | |
| Keen-Eyed | silver | secretDiscoveryCount 40 | Keen-Eyed | | |
| Sleuth | gold | secretDiscoveryCount, all (the wiki says 117 in one place and 121 in another) | Hidden ✦ | Sleuth | |
| Pathfinder | silver | discoveryCount territorial 200 | Far-Roaming | Pathfinder | |
| Bombardier | silver | bombUsed 25 | Blasting | Bombardier | |
| Freshly Forged | bronze | professionCraft x1 | Freshly | Forged | |
| Soul-Guide | gold | festivalMilestone Spirits: 21 Lost Souls | Haunted | Soul-Guide | |
| Hearthkeeper | silver | festivalMilestone Bonfire: 21 bonfires | Ember | Hearthkeeper | |
| Frostbound Host | silver | festivalMilestone Blizzard: 25 invites | Frostbound | Host | |

## Cut

- Quests: The Olmic Rune, Journey Beyond, Point of No Return, The Dark Descent, and single-step Fruma quests beyond Apotheosis and Overture to Despair.
- Collection: Warp, Neon Seawater, Charm Bracelet, Clockwork, Boundless.
- Exploration: Nesaak Forest, Pirate Cove, Dead Island, Eltom, Thanos, Light Peninsula. Death's Realm is cut too: it is not a territory and the wiki gives no coordinates.
- Statistics: PvP kills and deaths, and the lootrun count ladder (the API only counts camps).
- Position: Pink Toll, Two Emeralds of Regret, Waterfall Stash, Lonely Snowman, Edge of the World, Cloudwalker, Squeaky Clean, Third-Floor Vault, Lunar Casualty.
- Earlier: support rank, veteran flag, trade market, Party Finder, duels, Realm of Light, and unverified items (Corkian Amplifier, Tomes of Allegiance, Diamond Token).

## Shop impact

Earnable points go from 129 to 592. The shop is 216 points, about 36% of what can now be earned, against the roughly 60% the shop roadmap aims for. Keeping 60% would need about 355 points of new items, such as the glow outline and shader kinds. (Since then the target moved to 80–85% and the shop was repriced to 720; see TODO.md section 5.)
