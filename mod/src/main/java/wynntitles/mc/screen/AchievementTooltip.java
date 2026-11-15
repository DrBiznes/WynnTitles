package wynntitles.mc.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import wynntitles.api.ApiTypes.AchievementStats;
import wynntitles.catalog.Catalog;
import wynntitles.mc.TitleRenderer;
import wynntitles.mc.WynnFonts;
import wynntitles.titles.Title;

/**
 * What the achievements tab says about an achievement, as one tooltip: name,
 * description, points, reward and rarity. The unlock chat line shows it when
 * the achievement's name is hovered.
 */
public final class AchievementTooltip {
	private AchievementTooltip() {}

	/** Stats may be null before the server has answered; the rarity line is left out then. */
	public static MutableComponent of(Catalog.Achievement achievement, AchievementStats stats) {
		MutableComponent tooltip = WynnFonts.body(achievement.name()).withColor(WynnUi.GOLD);
		if (achievement.description() != null && !achievement.description().isBlank()) {
			tooltip.append(line(achievement.description(), WynnUi.TEXT_DIM));
		}
		tooltip.append(line(StyleChips.points(achievement.points()), AchievementsPage.tierColor(achievement.tier())));
		Title reward = achievement.rewards() == null ? null
				: Title.of(achievement.rewards().prefix(), achievement.rewards().suffix());
		if (reward != null) {
			tooltip.append(line("Title words: ", WynnUi.TEXT_DIM)).append(TitleRenderer.render(reward));
		}
		if (stats != null && stats.players() > 0) {
			double percent = stats.percent(achievement.id());
			AchievementsPage.Rarity rarity = AchievementsPage.Rarity.of(percent);
			tooltip.append(line(AchievementsPage.formatPercent(percent) + " of players · " + rarity.label, rarity.color));
		}
		return tooltip;
	}

	private static Component line(String text, int color) {
		return Component.literal("\n").append(WynnFonts.body(text).withColor(color));
	}
}
