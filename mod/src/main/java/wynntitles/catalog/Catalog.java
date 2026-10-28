package wynntitles.catalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import wynntitles.titles.TitleWord;

/** The achievement catalog served by GET /v1/catalog. */
public record Catalog(String version, List<Achievement> achievements) {
	public Catalog {
		achievements = achievements == null ? List.of() : List.copyOf(achievements);
	}

	public Optional<Achievement> get(String id) {
		return achievements.stream().filter(a -> a.id().equals(id)).findFirst();
	}

	public Map<String, Achievement> byId() {
		return achievements.stream().collect(Collectors.toMap(Achievement::id, Function.identity(), (a, b) -> a));
	}

	public record Achievement(
			String id,
			String category,
			String name,
			String description,
			String tier,
			int points,
			Icon icon,
			Trigger trigger,
			Rewards rewards) {}

	/**
	 * Model is an item model from the Wynncraft pack relative to minecraft:item/,
	 * or null; fallback is a vanilla item id. A catalog cached by an older mod
	 * version may have no icon at all.
	 */
	public record Icon(String model, String fallback) {}

	/** Unused fields are null or 0 depending on the trigger type. */
	public record Trigger(
			String type,
			String questName,
			String className,
			int level,
			String territoryName,
			String itemName,
			int amount,
			String statistic) {}

	public record Rewards(TitleWord prefix, TitleWord suffix) {}
}
