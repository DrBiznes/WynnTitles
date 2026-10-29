package wynntitles.catalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import wynntitles.titles.EffectColors;
import wynntitles.titles.TitleAnimation;
import wynntitles.titles.TitleEffect;
import wynntitles.titles.TitleRun;
import wynntitles.titles.TitleStyle;
import wynntitles.titles.TitleWord;

/** The achievement catalog and style shop served by GET /v1/catalog. */
public record Catalog(String version, List<Achievement> achievements, List<ShopItem> shop) {
	public Catalog {
		achievements = achievements == null ? List.of() : List.copyOf(achievements);
		// A catalog cached by an older mod version has no shop.
		shop = shop == null ? List.of() : List.copyOf(shop);
	}

	public Optional<Achievement> get(String id) {
		return achievements.stream().filter(a -> a.id().equals(id)).findFirst();
	}

	public Map<String, Achievement> byId() {
		return achievements.stream().collect(Collectors.toMap(Achievement::id, Function.identity(), (a, b) -> a));
	}

	public Optional<ShopItem> shopItem(String id) {
		return shop.stream().filter(i -> i.id().equals(id)).findFirst();
	}

	/**
	 * The word as one run in the given style, the same way the server's
	 * catalog.Restyle builds it, so the screen can preview styles before they
	 * are bought.
	 */
	public TitleWord restyle(TitleWord word, TitleStyle style) {
		if (word == null || style == null || style.isEmpty()) {
			return word;
		}
		String color = null;
		TitleEffect effect = null;
		ShopItem source = style.source() == null ? null : shopItem(style.source()).orElse(null);
		if (source != null) {
			switch (source.kind()) {
				case ShopItem.COLOR -> color = source.color();
				case ShopItem.PALETTE -> effect = new TitleEffect("gradient", null, source.palette(), false, 0, null);
				case ShopItem.ANIMATED_PALETTE ->
						effect = new TitleEffect("gradient", null, source.palette(), true, 0, source.animation());
				case ShopItem.RAINBOW -> effect = new TitleEffect("rainbow", null, null, true, 0, null);
				default -> {}
			}
		}
		boolean bold = false, italic = false, underlined = false, strikethrough = false;
		for (String id : style.formats()) {
			String format = shopItem(id).map(ShopItem::format).orElse("");
			switch (format) {
				case "bold" -> bold = true;
				case "italic" -> italic = true;
				case "underlined" -> underlined = true;
				case "strikethrough" -> strikethrough = true;
				default -> {}
			}
		}
		return new TitleWord(word.text(),
				List.of(new TitleRun(word.text(), color, bold, italic, underlined, strikethrough, false, effect)));
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
			String statistic,
			String raidName) {}

	public record Rewards(TitleWord prefix, TitleWord suffix) {}

	/**
	 * A style piece bought with achievement points. Kind is a color source
	 * (color, palette, animatedPalette, rainbow) or a format; an unknown kind
	 * comes from a newer catalog and is shown as needing an update. An animated
	 * palette also names its animation.
	 */
	public record ShopItem(
			String id, String kind, String name, int price, String color, String palette, String animation, String format) {
		public static final String COLOR = "color";
		public static final String PALETTE = "palette";
		public static final String ANIMATED_PALETTE = "animatedPalette";
		public static final String RAINBOW = "rainbow";
		public static final String FORMAT = "format";

		public boolean isSource() {
			return !FORMAT.equals(kind);
		}

		/** False for kinds added to the shop after this mod version shipped. */
		public boolean supported() {
			return switch (kind) {
				case COLOR, RAINBOW -> true;
				// A newer catalog may add palettes or animations this mod cannot draw yet.
				case PALETTE -> EffectColors.knowsPalette(palette);
				case ANIMATED_PALETTE -> EffectColors.knowsPalette(palette) && TitleAnimation.byId(animation) != null;
				case FORMAT -> switch (format == null ? "" : format) {
					case "bold", "italic", "underlined", "strikethrough" -> true;
					default -> false;
				};
				default -> false;
			};
		}

		/** How an animated palette moves, or null for every other kind. */
		public TitleAnimation motion() {
			return ANIMATED_PALETTE.equals(kind) ? TitleAnimation.byId(animation) : null;
		}
	}
}
