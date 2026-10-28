package wynntitles.catalog;

import java.util.concurrent.CompletableFuture;
import wynntitles.api.ApiClient;
import wynntitles.api.ApiTypes.AchievementStats;

/**
 * Keeps the latest achievement rarity from the server, so the achievements
 * screen shows the last known numbers at once and updates when fresh ones
 * arrive. The server caches them too, so refreshing on every open is cheap.
 */
public final class StatsStore {
	private final ApiClient api;
	private volatile AchievementStats stats;

	public StatsStore(ApiClient api) {
		this.api = api;
	}

	/** The last stats received, or null before the first answer. */
	public AchievementStats stats() {
		return stats;
	}

	public CompletableFuture<AchievementStats> refresh() {
		return api.achievementStats().thenApply(fresh -> {
			stats = fresh;
			return fresh;
		});
	}
}
