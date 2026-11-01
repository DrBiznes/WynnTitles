package wynntitles.achievements;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Raids won as the last one alive in the party, read from the party list on
 * Wynncraft's scoreboard, which strikes through a dead player's name. Nothing
 * else records this, so only wins seen since the game started count.
 */
final class RaidLastStand {
	/** One row of the party list. A row that is offline is not alive either. */
	record Member(String name, boolean online, boolean alive) {}

	/**
	 * How long before the win a last stand still counts, since rows are read a
	 * tick late and the win may show after the fight ends. The dead only return
	 * when a room is finished, so nobody has been revived by the win.
	 */
	private static final long RECENT_MS = 10_000;

	private final Map<String, Integer> wins = new HashMap<>();
	private int fallen;
	private long seenAt;

	/** Takes the party list as it is now. partySize counts the player; 0 when the party is not known yet. */
	synchronized void onParty(String self, int partySize, List<Member> rows, long now) {
		int dead = fallen(self, partySize, rows);
		if (dead > 0) {
			fallen = dead;
			seenAt = now;
		}
	}

	synchronized void onCompleted(String raid, long now) {
		if (fallen > 0 && now - seenAt <= RECENT_MS) {
			wins.put(key(raid), fallen);
		}
		fallen = 0;
	}

	/** Party members who had fallen in the win, or 0 if this raid has not been won that way. */
	synchronized int fallen(String raid) {
		return wins.getOrDefault(key(raid), 0);
	}

	/**
	 * How many party members lie dead with nobody else alive, or 0 if that is
	 * not the case. The player's own row counts for nothing unless it is dead:
	 * if nobody else is alive, the player is the one who won. Someone must have
	 * fallen, so a party emptied by disconnects is not a last stand, and a row
	 * Wynntils could not read leaves the party short, which could hide a survivor.
	 */
	private static int fallen(String self, int partySize, List<Member> rows) {
		int others = 0, dead = 0;
		for (Member row : rows) {
			if (row.name().equalsIgnoreCase(self)) {
				if (!row.alive()) {
					return 0;
				}
				continue;
			}
			if (row.alive()) {
				return 0;
			}
			others++;
			if (row.online()) {
				dead++;
			}
		}
		return others + 1 < partySize ? 0 : dead;
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
