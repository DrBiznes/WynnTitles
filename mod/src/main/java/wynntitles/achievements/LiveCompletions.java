package wynntitles.achievements;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dungeons and quests finished since the game started, read from Wynncraft's
 * chat messages, so they unlock before the Wynncraft API catches up. The patterns
 * are the real text with its formatting codes. If Wynncraft rewords a message,
 * they stop matching and unlocks wait for the API instead.
 */
final class LiveCompletions {
	private static final Pattern DUNGEON = Pattern.compile("^§6Great job! You've completed the (.+) Dungeon!$");
	// Quest lines start with an invisible padding glyph that depends on the name's length.
	private static final Pattern QUEST_HEADER = Pattern.compile("^[^§]*§6\\[Quest Completed\\]$");
	private static final Pattern QUEST_NAME = Pattern.compile("^[^§]*§e§l(.+)$");
	/** Wynncraft can send a dungeon win twice, and no dungeon is cleared twice in this time. */
	private static final long REPEAT_MS = 10_000;

	private final Map<String, Integer> dungeonClears = new HashMap<>();
	private final Map<String, Long> lastClear = new HashMap<>();
	private final Map<String, Integer> baselines = new HashMap<>();
	private final Set<String> quests = new HashSet<>();
	private boolean questNameNext;

	/** Takes a message on the game's own channel, with its formatting codes. */
	synchronized void onMessage(String text, long now) {
		if (questNameNext) {
			questNameNext = false;
			Matcher name = QUEST_NAME.matcher(text);
			if (name.matches()) {
				quests.add(key(name.group(1)));
				return;
			}
		}
		if (QUEST_HEADER.matcher(text).matches()) {
			questNameNext = true;
			return;
		}
		Matcher dungeon = DUNGEON.matcher(text);
		if (dungeon.matches()) {
			// Wynncraft draws the spaces in a dungeon's name with À.
			String name = key(dungeon.group(1).replaceAll("À+", " "));
			Long last = lastClear.put(name, now);
			if (last == null || now - last >= REPEAT_MS) {
				dungeonClears.merge(name, 1, Integer::sum);
			}
		}
	}

	synchronized int dungeonClears(String name) {
		return dungeonClears.getOrDefault(key(name), 0);
	}

	synchronized boolean questCompleted(String name) {
		return quests.contains(key(name));
	}

	/**
	 * Clears on the whole account. onAccount is the API's count, or null until
	 * the profile arrives. The first count seen is kept, because the API catches up
	 * with this session's clears and adding to its latest count would count those twice.
	 */
	synchronized int dungeonCount(String name, Integer onAccount) {
		int clears = dungeonClears(name);
		if (onAccount == null) {
			return clears;
		}
		int baseline = baselines.computeIfAbsent(key(name), n -> onAccount);
		return Math.max(baseline + clears, onAccount);
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
