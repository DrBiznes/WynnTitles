# Live quest and dungeon completions

Plan for detecting quest and dungeon completions from Wynncraft's chat messages, so that these achievements come from the Wynncraft API plus our own live events. No Content Book scan is involved.

Status: shipped in 0.18.0 and tested on Wynncraft. Discoveries, party wins, failed dungeons and mini-quests have no live trigger on purpose: they rely on the Wynncraft API and the Content Book scan.

The same update changes the scan that `territorySet` and `territoryAny` use. See [Content Book scans](#content-book-scans).

## Why

| Detector | Today | Problem |
|---|---|---|
| `quest` (17 achievements) | Wynncraft API, plus a `Models.Quest` check | The `Models.Quest` check can never pass, so unlocks wait on the API |
| `dungeon` (11) and `dungeonSet` (2) | Wynncraft API | Unlocks wait on the API |

- `PlayerProfile` refetches at most every 5 minutes, and the API lags the game by a few minutes on top of that.
- `Models.Quest` is only filled by `rescanQuestBook`, and nothing in Wynntils calls that method. Checked in the jar we build against (`hEKOk1N7`) and in Wynntils `main` on 2026-10-31.
- Calling `rescanQuestBook` ourselves would work, but a scan of every quest is the disruptive option we want to avoid.

## What Wynntils offers

Wynntils has no dungeon model, and no event for a dungeon or quest finishing. Checked in the same jar and on `main`. These look useful but are not:

| Signal | Why it does not work |
|---|---|
| `ActivityUpdatedEvent` | Only posted after a Content Book scan finishes |
| `ActivityTrackerUpdatedEvent` | Only covers the tracked quest, and nothing is posted when tracking ends, so finishing and untracking look the same |
| `StatisticKind` | Has raids, lootruns and wars, but no dungeons or quests |

`RaidEndedEvent` is not game data either. `RaidModel` matches on-screen titles such as "Raid Completed!" and posts the event itself. We can do the same thing with a public Wynntils event:

```java
@SubscribeEvent
public void onChat(ChatMessageEvent.Match event) {
	// event.getMessage() is a StyledText, event.getRecipientType() says which channel it came from
}
```

`TitleSetTextEvent` and `SubtitleSetTextEvent` are available too, if a completion turns out to show as a title and not in chat.

## Step 1: capture the real text

Captured on 2026-11-01 with a temporary mod, since removed, that logged every chat message, title and subtitle:

| Case | Captured text | Recipient type |
|---|---|---|
| Normal dungeon win | `§6Great job! You've completed the DecrepitÀÀÀSewers Dungeon!` (from `latest.log`) | Not captured, assumed `INFO` |
| Corrupted dungeon win | `§6Great job! You've completed the CorruptedÀÀÀDecrepitÀÀÀSewers Dungeon!` | `INFO` |
| Quest finished | `<padding>§6[Quest Completed]`, then `<padding>§e§lDearly Departed` as the next message | `INFO` |

What the capture settled:

- Both come through as chat, so titles are not needed.
- A dungeon's name is on the completion line, and a corrupted win says `Corrupted`. Wynncraft draws its spaces with `À`, so a run of those becomes a space.
- A quest's name is the next message after the header.
- Quest lines start with a padding glyph that depends on the name's length, such as `U+D0074`. These are unassigned code points, not private use ones, so the patterns allow any text without a `§` there.
- The whole dungeon win block arrived twice in the same second, as separate events. Repeats within 10 seconds count once. It is not known whether this happens every time.

Not captured: names with `'` or `-`, like `Galleon's Graveyard` and `Sand-Swept Tomb`, and the quests `???` and `Dwarves and Doguns Part IV`. If one of them differs from `server/catalog/achievements.json`, that unlock waits for the API.

## Step 2: the listener

Add `mod/src/main/java/wynntitles/achievements/LiveCompletions.java`, holding what has been seen since the game started:

```java
final class LiveCompletions {
	private final Map<String, Integer> dungeonClears = new ConcurrentHashMap<>();
	private final Set<String> quests = ConcurrentHashMap.newKeySet();

	@SubscribeEvent
	public void onChat(ChatMessageEvent.Match event) { ... }

	void onMessage(String text) { ... } // no Wynntils types, so a unit test can call it

	int dungeonClears(String name) { ... }
	boolean questCompleted(String name) { ... }
}
```

Rules:

- **Only game messages count.** Accept only the recipient type captured in step 1. Reject `GLOBAL`, `LOCAL`, `GUILD`, `PARTY`, `PRIVATE` and `SHOUT`, so that a player typing the same words cannot trigger an unlock.
- **Anchor the patterns.** Use the whole message and its formatting codes, not `contains`.
- **Normalize names** with the existing `WynntilsDetectors.key()`, which lowercases them.
- **Keep the patterns together** at the top of the class, so a Wynncraft wording change is a one-place fix.

Registration: `WynntilsDetectors.eventListeners()` returns `RAIDS` and a small chat listener that passes `INFO` messages to `LiveCompletions`. `LiveCompletions` has no Wynntils types, so unit tests can call it. `WynnTitles.java` registers both inside the existing `try` block, because that block already handles Wynntils changing its events.

## Step 3: the detectors

All changes are in `mod/src/main/java/wynntitles/achievements/WynntilsDetectors.java`.

No new trigger types are needed, so `server/catalog/catalog.go` and `achievements.json` do not change. Evidence stays a free-form record, and claims stay trusted, as `docs/AGENTS.md` requires.

### `quest`

- Replace the `Models.Quest` check with `live.questCompleted(quest)`, and keep `completedOnAccount()`.
- Remove the `ActivityStatus` import, which nothing else uses.
- Evidence `source` becomes `questChat` or `wynncraftApi`. Drop `questBook`.

### `dungeon`

12 of the 13 dungeon achievements need one clear, so `max(thisSession, onAccount)` works, as it does in `RaidDetector`. That rule fails for `dungeon_galleon_graveyard_10`: with 9 on the API and 1 clear now, it stays at 9 until the API catches up.

Use a baseline instead:

```
baseline = the first API count seen this session
count    = max(baseline + clearsThisSession, currentApiCount)
```

- The baseline is kept per dungeon name, taken once from the first profile that arrives.
- Taking the `max` stops clears being counted twice once the API catches up.
- A clear just before the first profile arrives could be counted twice, overcounting by one. That is acceptable for a cosmetic mod.
- With no profile yet, use the session count alone.
- Evidence `source` becomes `dungeonChat` or `wynncraftApi`, and `completions` stays.
- `dungeonProgress` should use the same count, so the progress line and the unlock agree.

### `dungeonSet`

A dungeon counts when either source has it: `live.dungeonClears(d) > 0` or the API has it. Change `dungeonSet` and `dungeonSetProgress` the same way.

`dungeon_corrupted_all` lists 8 corrupted names and `dungeon_all` lists all 18, so a corrupted win must record its `Corrupted ` name. If the capture shows the message does not say which kind it was, a corrupted run can only come from the API, so this has to be settled in step 1.

## Step 4: tests

Add `mod/src/test/java/wynntitles/achievements/LiveCompletionsTest.java`, using the captured text as fixtures:

- A normal win, a corrupted win and a quest finish each record the right name.
- A failed dungeon and a mini-quest record nothing, or behave as decided in step 1.
- Each rejected recipient type records nothing, which means the channel check needs a test seam as well.
- A dungeon count with the API behind, level with and ahead of the session gives the right total.

Then run `scripts/smoke-test.sh`, and in-game finish one dungeon and one quest and confirm each unlocks within a couple of seconds.

## Content Book scans

`territorySet` and `territoryAny` still need `Models.Discovery.reloadDiscoveries`, which scans three filters and leaves the book on the last one. Wynntils has a `RESET_FILTERS` constant, but it is false and the restore step is compiled out of the jar, so there is nothing to switch on.

- **Fewer scans:** `DiscoveryScans` scans each character once per game launch, not once per world join, and both detectors share it. A discovery that is not a territory now counts from the next launch. A scan that leaves nothing to read is still retried up to 3 times.
- **Filter restore:** `ContentBookFilter` queues its own Wynntils queries around the scan: read the active filter from the Filter item in slot 66, run the scan, then click slot 66 until the saved filter is back. Queries run in order, so all three start together.
- **Failures:** a retried scan keeps the filter saved before the first one. A failed restore is retried up to 3 times, a minute apart, and only while a place set is still locked. A failed save skips the restore and never blocks the scan.

This only covers scans that WynnTitles starts. A scan Wynntils starts itself, such as from its Content Book screen, can still change the filter.

## When the text changes

| Failure | Result |
|---|---|
| Wynncraft rewords a message | The pattern stops matching and unlocks fall back to the API |
| Wynntils changes `ChatMessageEvent` | The existing `try` block logs it and the mod does not load on Wynncraft |
| Game restarted right after a completion | Live data is lost, and the API still reports the completion |
| Claim fails while the game is running | The tracker retries, because the live data is still there |

A wording change costs a few minutes of delay and never a wrong unlock, so it needs no version check.

## Limitations

- **Hidden API data:** a player who hides quest data would only unlock quests finished from now on, never their past ones. If that matters later, add one scan that runs only when the API returns no quest data.
- **Raids:** the 10 and 50 clear achievements have the same `max` problem as above. The baseline rule could be reused for them, but that is a separate change.

## Done when

- [x] The capture table is filled in
- [x] The temporary capture mod is deleted
- [x] `LiveCompletions` is added, registered and tested
- [x] `quest`, `dungeon` and `dungeonSet` use it, and `Models.Quest` is gone
- [x] Scans run once per character per launch, and the filter is restored
- [x] Tested on Wynncraft
- [x] `docs/CHANGELOG.md` is updated
- [x] This doc is marked as shipped
