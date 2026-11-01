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
| `territorySet` | every listed place visited on one character: the Content Book's world, territorial and secret discoveries, which Wynntils scans when the mod asks, plus the territory the character stands in. Kept by character in `config/wynntitles/territories.json` |
| `territoryAny` | any one of the listed places, read the same way as `territorySet` |
| `itemRarityCount`, `shinyItem`, `itemSet` | Wynntils item annotations over every carried and worn stack |

### Checked against live data

- **Quest names:** every quest name used matches a real player's API quest list, including "???" and "A Hunter's Calling" (the API has no "???" prefix on it).
- **Dungeon names:** the API keys match, including "Timelost Sanctum" and "Corrupted Lost Sanctuary". The API also still lists a legacy "Lost Sanctuary", which no set requires.
- **Territory names:** every `location` name used is in the API's territory list.
- **`territorySet` names:** every name must be a discovery. `TestTerritorySetNamesAreDiscoveries` checks them against a copy of the names in Wynntils' `content_book_dump.json`. A name that is also a territory unlocks on the spot; any other, like The Moon or a library, unlocks at the next game launch. "Town of Lutho" is the one discovery named differently from its territory, and the mod treats the two as the same place.
- **`discoveries` in the API** is the character's world, territorial and secret discoveries added together, so it can't say which places were visited. Fully completed characters read 674 (177 + 376 + 121).
- **Profession max:** 132 per profession (1584 total). The top players on the professions leaderboard are at exactly that.
- **`completedQuests`:** the sum over every character, with mini-quests included. Top accounts are near 3,000, so the ladder is 50, 300 and 1,000.
- **`lootruns`:** reads 7 for every top player, so it counts lootrun camps finished, not runs. It is one achievement for all seven camps instead of a ladder.
- **Caves and world events** are summed across characters too, so the ladders are 25 and 250 for caves, and 100 and 500 for world events.

### Still to check in game

- **Into the Void** uses the wiki's coordinates for The Lonely Abyss (13777, -4042) with a 64-block radius, because The Void is not a territory.
- **Little Rocket Mushroom** uses the coordinates of Mehme's NPC (-7112, -12825), and the wiki doesn't say whether his home can be reached outside the ??? quest. Also check that the Talking Mushroom item exists outside the quest.
- **Item names** in the item sets (Morph pieces, Hive armour) come from the wiki's set and Hive Shop pages.

### Problems in shipped achievements

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

### profession (19)

| Achievement | Tier | Trigger | Prefix | Suffix |
|---|---|---|---|---|
| Diligent Journeyman | silver | totalProfessionLevel 250 | Diligent | Journeyman |
| Renaissance | gold | totalProfessionLevel 1000 | Renaissance | Polymath |
| Perfectionist | gold | totalProfessionLevel 1584 |  | Perfectionist ✦ |
| Woodcutting 100 | silver | professionLevel 100 | Timbered | Lumberjack |
| Mining 100 | silver | professionLevel 100 | Obsidian | Prospector |
| Farming 100 | silver | professionLevel 100 | Sun-Blessed | Harvester |
| Fishing 100 | silver | professionLevel 100 | Tidal | Angler |
| Alchemism 100 | silver | professionLevel 100 | Transmuting | Alchemist |
| Armouring 100 | silver | professionLevel 100 | Unbreachable | Armourer |
| Cooking 100 | silver | professionLevel 100 | Savoury | Gourmand |
| Jeweling 100 | silver | professionLevel 100 | Gem-Cut | Lapidary |
| Scribing 100 | silver | professionLevel 100 | Ink-Stained | Lorekeeper |
| Tailoring 100 | silver | professionLevel 100 | Silk-Spun | Weaver |
| Weaponsmithing 100 | silver | professionLevel 100 | Anvil-Forged | Bladesmith |
| Woodworking 100 | silver | professionLevel 100 | Fletched | Bowyer |
| Bountiful Gatherer | gold | professionLevelAll (4) 125 | Bountiful | Gatherer |
| Master Tradesman | gold | professionLevelAll (8) 117 | Craft-Honed | Tradesman |

### exploration (10)

| Achievement | Tier | Trigger | Prefix | Suffix |
|---|---|---|---|---|
| Mind the Gap | bronze | territoryAny "Jofash Tunnel" or "Entrance to Gavel" | | Emissary |
| Port of Call | bronze | territorySet "Corkus City" |  | Wavebreaker |
| Good Neighbour | silver | territorySet (22) | Well-Travelled | Neighbour |
| Mapmaker of Gavel | gold | territorySet (13) | Worldly | Cartographer |
| The Queen's Census | gold | territorySet (6) | Registered | Subject |
| Spelunker | bronze | statistic caves 25 |  | Spelunker |
| Cave-Warden | silver | statistic caves 250 | Deepdelving | Cave-Warden |
| Stormchaser | silver | statistic worldEvents 100 |  | Stormchaser |
| Restless Defender | gold | statistic worldEvents 500 | Restless | Defender |

### collection (14)

| Achievement | Tier | Trigger | Prefix | Suffix |
|---|---|---|---|---|
| Token of Excellence | gold | inventoryItem "Legendary Island Token" x1 | Legendary ✦ | Hero ✦ |
| Block Party | bronze | inventoryItem "Emerald Block" x64 |  | Banker |
| Ten Stacks | gold | inventoryItem "Liquid Emerald" x640 | Gilded ✦ | Magnate |
| Warp | gold | inventoryItem "Warp" x1, mythic | Phased ✦ | Drifter |
| Idol | gold | inventoryItem "Idol" x1, mythic | Drowned ✦ | Devotee ✦ |
| Mama Zomble's Memory | gold | inventoryItem "Mama Zomble's Memory" x1 | Mama's ✦ | Favourite ✦ |
| Archaic Writing | gold | inventoryItem "Archaic Writing" x1 | Gavellian ✦ | Scribe |
| Borange | silver | inventoryItem "Borange Fluff" x1 | Borange |  |
| Coalescence | silver | inventoryItem "Coalescence" x1 | Coalesced ✦ |  |
| Canned Abis | silver | inventoryItem "Canned Abis" x420 | Baked ✦ |  |
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
| Seasoned Raider | silver | statistic raidsCompleted 100 | Seasoned |  |
| Raid Syndicate | gold | statistic raidsCompleted 1000 | Syndicate | Raidmaster |

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

### secret (16)

The libraries are the seven secret discoveries with "Library" in the name, plus The Grand Archive, which the Content Book calls a library. The Grand Archive is bronze because the wiki lists no entry requirement for it, and that also keeps the shop ratio under 85%.

| Achievement | Tier | Trigger | Prefix | Suffix |
|---|---|---|---|---|
| Little Rocket Mushroom | gold | itemAtLocation "Talking Mushroom" | Unwelcome | Mycophile |
| Overdue | silver | territorySet "Llevigar's Secret Library" | Overdue | Borrower |
| Tears for Bob | silver | territorySet "Ragni's Secret Library" | Tearful | Bookworm |
| Shh, Library | silver | territorySet "Lusuco's Secret Library" | Explosive | Archivist |
| Classified | silver | territorySet "Relos' Secret Library" | Classified | Whistleblower |
| Written by the Victor | silver | territorySet "Gylia Watch Library" | Revisionist | Chronicler |
| Not All Scriptures | bronze | territorySet "The Grand Archive" | Archival | Curator |
| Four Petals | silver | territorySet "Aldwell Library" | Petal-Pressed | Bibliophile |
| Permission Slip | silver | territorySet "The Royal University's Library" | Permitted | Scholar |
| Complete Works | gold | territorySet (all 8 libraries) | Lorebound ✦ | Librarian ✦ |
| Moon Walker | gold | territorySet "The Moon" | | Moon-Walker ✦ |
| Into the Void | gold | coordinates | Void-Touched ✦ | Voidwalker |
| Death's Door | silver | territorySet "Death's Realm" | Departed | Revenant |
| Bak'al's Wake | bronze | territorySet (3) | Scorched | Witness |
| Edge of Nothing | gold | territorySet (4) | Unmoored | Castaway |
| Sleuth | gold | territorySet (all 121 secret discoveries) | Omniscient ✦ | Pupil ✦ |

## Later: possible additions (chat parsing and other low-confidence triggers)

These depend on exact chat text, festival menus or boss messages that still need to be captured in game.

| Achievement | Tier | Trigger | Prefix | Suffix | Note |
|---|---|---|---|---|---|
| That Didn't Happen | silver | chatLine: Tasim's "Pfft, hey, that didn't actually happen!" | Embellished | Storyteller | |
| Herbert's Bane | gold | bossKilled "Doctor Legendary" | Unbowed | Challenger | |
| Grandmaster | gold | raidRank Grandmaster | Grandmaster ✦ | | |
| All In | gold | raidGambits ≥ 3 in one clear | Reckless | Gambler | |
| Last One Standing | gold | raidLastAlive "Wartorn Palace" or "The Nameless Anomaly" | Lone | Holdout | Every other party member is dead when the raid is won. A solo clear must not count. Still need to capture how the game shows party deaths in a raid. |
| Prelude Survivor | gold | worldEventNamed "Prelude to Annihilation" | | Survivor | |
| Keen-Eyed | silver | secretDiscoveryCount 40 | Keen-Eyed | | |
| Pathfinder | silver | discoveryCount territorial 200 | Far-Roaming | Pathfinder | |
| Bombardier | silver | bombUsed 25 | Blasting | Bombardier | |
| Freshly Forged | bronze | professionCraft x1 | Freshly | Forged | |
| Soul-Guide | gold | festivalMilestone Spirits: 21 Lost Souls | Haunted | Soul-Guide | |
| Hearthkeeper | silver | festivalMilestone Bonfire: 21 bonfires | Ember | Hearthkeeper | |
| Frostbound Host | silver | festivalMilestone Blizzard: 25 invites | Frostbound | Host | |

## Cut

- Quests: The Olmic Rune, Journey Beyond, Point of No Return, The Dark Descent, and single-step Fruma quests beyond Apotheosis and Overture to Despair.
- Collection: Warp, Neon Seawater, Charm Bracelet, Clockwork, Boundless.
- Exploration: Nesaak Forest, Pirate Cove, Dead Island, Eltom, Thanos, Light Peninsula.
- Statistics: PvP kills and deaths, and the lootrun count ladder (the API only counts camps).
- Position: Pink Toll, Two Emeralds of Regret, Waterfall Stash, Lonely Snowman, Edge of the World, Cloudwalker, Squeaky Clean, Third-Floor Vault, Lunar Casualty.
- Earlier: support rank, veteran flag, trade market, Party Finder, duels, Realm of Light, and unverified items (Corkian Amplifier, Tomes of Allegiance, Diamond Token).

## Shop impact

Earnable points go from 129 to 592. The shop is 216 points, about 36% of what can now be earned, against the roughly 60% the shop roadmap aims for. Keeping 60% would need about 355 points of new items, such as the glow outline and shader kinds. (Since then the target moved to 80–85% and the shop was repriced to 720; see TODO.md section 5.)
