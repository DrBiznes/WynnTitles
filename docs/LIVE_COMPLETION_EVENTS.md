# Live quest and dungeon completions

Plan for detecting quest and dungeon completions from Wynncraft's chat messages, so that these achievements come from the Wynncraft API plus our own live events. No Content Book scan is involved.

Status: planned, nothing built yet. Step 1 needs a real game session, because the exact chat text has not been captured.

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

We do not know the exact messages. The dev logs have no chat lines, and web searches did not find them. Do not write a pattern from memory: Wynntils' own patterns include exact color codes, so ours may need to as well.

1. Add a temporary listener that logs every `ChatMessageEvent.Match`, title and subtitle. For chat, log `getRecipientType()` and the raw `StyledText` with its formatting codes.
2. Play these cases and record the output in the table below:

| Case | Why it is needed | Captured text | Recipient type |
|---|---|---|---|
| Normal dungeon win | The main pattern | | |
| Corrupted dungeon win | Must be told apart from normal | | |
| Dungeon win in a party | Must count for each member | | |
| Quest finished | The main pattern | | |
| Mini-quest finished | Must not count as a quest, unless the names overlap harmlessly | | |
| Quest with odd characters in its name | `???`, `Dwarves and Doguns Part IV` | | |
| Failed dungeon | Must not match | | |
| Another player's completion, if it is announced | Must not match | | |

3. Answer these from the capture:
   - Does the name appear on the same line as the completion text? If not, the listener has to remember the header line and read the name from the next message.
   - Is the name in the message spelled the same as in `server/catalog/achievements.json`? Check apostrophes in `Galleon's Graveyard` and the hyphen in `Sand-Swept Tomb`.
   - Does the message come through as chat at all, or only as a title?
4. Remove the temporary listener before merging.

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

Registration: `WynntilsDetectors.eventListener()` returns only `RAIDS` today, and `WynnTitles.java` registers that one object. Make it register `LiveCompletions` as well, inside the existing `try` block, because that block already handles Wynntils changing its events.

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

- [ ] The capture table is filled in and the temporary listener is gone
- [ ] `LiveCompletions` is added, registered and tested
- [ ] `quest`, `dungeon` and `dungeonSet` use it, and `Models.Quest` is gone
- [ ] Tested in game
- [ ] `docs/CHANGELOG.md` is updated and this doc is removed or marked as shipped
