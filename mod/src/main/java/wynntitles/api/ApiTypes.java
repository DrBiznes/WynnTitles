package wynntitles.api;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import wynntitles.titles.Title;
import wynntitles.titles.TitleStyle;
import wynntitles.titles.TitleWord;

/** Bodies exchanged with the WynnTitles server. Field names match the JSON. */
public final class ApiTypes {
	private ApiTypes() {}

	public record Session(String token, String uuid, String username, long expiresAt) {}

	/** Points is everything earned from achievements; balance is what is left to spend in the shop. */
	public record Me(
			String uuid,
			String username,
			List<Unlock> unlocked,
			int points,
			int balance,
			List<String> owned,
			Words words,
			Equipped equipped,
			Styles styles,
			Title title) {
		public Me {
			unlocked = unlocked == null ? List.of() : List.copyOf(unlocked);
			owned = owned == null ? List.of() : List.copyOf(owned);
			words = words == null ? new Words(List.of(), List.of()) : words;
			equipped = equipped == null ? new Equipped(null, null) : equipped;
			styles = styles == null ? new Styles(null, null) : styles;
		}

		public boolean owns(String itemId) {
			return owned.contains(itemId);
		}
	}

	public record Unlock(String achievementId, long claimedAt) {}

	public record Words(List<TitleWord> prefixes, List<TitleWord> suffixes) {
		public Words {
			prefixes = prefixes == null ? List.of() : List.copyOf(prefixes);
			suffixes = suffixes == null ? List.of() : List.copyOf(suffixes);
		}
	}

	public record Equipped(String prefix, String suffix) {}

	/** Each slot's shop style, or null for the word's native style. */
	public record Styles(TitleStyle prefix, TitleStyle suffix) {}

	/** How many of the mod's players hold each achievement (GET /v1/stats/achievements). */
	public record AchievementStats(int players, Map<String, Integer> unlocks) {
		public AchievementStats {
			unlocks = unlocks == null ? Map.of() : Map.copyOf(unlocks);
		}

		/** The share of players holding the achievement, 0 to 100. */
		public double percent(String achievementId) {
			return players == 0 ? 0 : 100.0 * unlocks.getOrDefault(achievementId, 0) / players;
		}
	}

	public record Claim(String achievementId, String triggerType, JsonObject evidence) {}

	public record ClaimResult(String status, String achievementId) {
		public boolean newlyUnlocked() {
			return "unlocked".equals(status);
		}

		/** The server holds this achievement for the player, whether new or not. */
		public boolean recorded() {
			return newlyUnlocked() || "alreadyUnlocked".equals(status);
		}

		/** The server's catalog disagrees with ours, so ours is out of date. */
		public boolean outdated() {
			return "outdated".equals(status) || "unknown".equals(status);
		}
	}

	public record ClaimResults(List<ClaimResult> results) {
		public ClaimResults {
			results = results == null ? List.of() : List.copyOf(results);
		}
	}
}
