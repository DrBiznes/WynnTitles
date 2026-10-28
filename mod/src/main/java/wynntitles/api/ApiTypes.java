package wynntitles.api;

import java.util.List;
import java.util.Map;
import wynntitles.titles.Title;
import wynntitles.titles.TitleWord;

/** Response bodies from the WynnTitles server. Field names match the JSON. */
public final class ApiTypes {
	private ApiTypes() {}

	public record Session(String token, String uuid, String username, long expiresAt) {}

	public record Me(
			String uuid,
			String username,
			List<Unlock> unlocked,
			int points,
			Words words,
			Equipped equipped,
			Title title) {
		public Me {
			unlocked = unlocked == null ? List.of() : List.copyOf(unlocked);
			words = words == null ? new Words(List.of(), List.of()) : words;
			equipped = equipped == null ? new Equipped(null, null) : equipped;
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

	public record ClaimResult(String status, String achievementId) {
		public boolean newlyUnlocked() {
			return "unlocked".equals(status);
		}
	}
}
