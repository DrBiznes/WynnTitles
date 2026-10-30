package wynntitles.catalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import wynntitles.titles.EffectColors;
import wynntitles.titles.TitleAnimation;
import wynntitles.titles.TitleEffect;
import wynntitles.titles.TitleFonts;
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
		ShopItem font = shopItem(style.font()).orElse(null);
		if (font != null && ((!Boolean.TRUE.equals(font.formats()) && !style.formats().isEmpty())
				|| (style.source() != null && shopItem(style.source()).map(i -> !font.allows(i)).orElse(true))
				|| (font.banner() && TitleFonts.bannerCharacter(word.text()) != 0))) {
			return word;
		}
		String color = style.color();
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
				List.of(new TitleRun(word.text(), color, bold, italic, underlined, strikethrough, false, effect,
						font == null ? null : font.font(), style.letterColor())));
	}

	/** Switching fonts keeps only supported pieces. A dropped effect becomes
	 * its first palette stop, making the preview identical to what is saved. */
	public TitleStyle selectFont(TitleStyle current, ShopItem font) {
		TitleStyle d = current == null ? new TitleStyle(null, List.of()) : current;
		String source = d.source(), color = d.color();
		if (source != null && !font.allows(shopItem(source).orElseThrow())) {
			TitleRun run = restyle(new TitleWord("Preview", null), d).runs().get(0);
			color = String.format("#%06X", EffectColors.firstColor(run.effect()));
			source = null;
		}
		return new TitleStyle(source, Boolean.TRUE.equals(font.formats()) ? d.formats() : List.of(),
				font.id(), color, font.banner() ? d.letterColor() : null);
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

	/**
	 * Unused fields are null or 0 depending on the trigger type. ItemSets is met
	 * when every item of any one set is held or worn; x, z and radius are a
	 * circle on the map.
	 */
	public record Trigger(
			String type,
			String questName,
			String className,
			int level,
			String territoryName,
			String itemName,
			int amount,
			String statistic,
			String raidName,
			String dungeonName,
			List<String> dungeonNames,
			String professionName,
			List<String> professionNames,
			List<String> territoryNames,
			List<String> gamemodes,
			String guildRank,
			String rarity,
			List<List<String>> itemSets,
			int x,
			int z,
			int radius) {}

	public record Rewards(TitleWord prefix, TitleWord suffix) {}

	/**
	 * A style piece bought with achievement points. Kind is a color source
	 * (color, palette, animatedPalette, rainbow), a format or a font; an unknown kind
	 * comes from a newer catalog and is shown as needing an update. An animated
	 * palette also names its animation.
	 */
	public record ShopItem(
			String id, String kind, String name, int price, String color, String palette, String animation, String format,
			String font, List<String> allows, Boolean formats) {

		public ShopItem {
			allows = allows == null ? List.of() : List.copyOf(allows);
		}

		public boolean banner() {
			return TitleFonts.banner(font);
		}

		public boolean allows(ShopItem item) {
			return FORMAT.equals(item.kind()) ? Boolean.TRUE.equals(formats) : allows.contains(item.kind());
		}

		public static final String COLOR = "color";
		public static final String PALETTE = "palette";
		public static final String ANIMATED_PALETTE = "animatedPalette";
		public static final String RAINBOW = "rainbow";
		public static final String FONT = "font";
		public static final String FORMAT = "format";

		public boolean isSource() {
			return List.of(COLOR, PALETTE, ANIMATED_PALETTE, RAINBOW).contains(kind);
		}

		/** False for kinds added to the shop after this mod version shipped. */
		public boolean supported() {
			return switch (kind) {
				case FONT -> TitleFonts.known(font);
				case COLOR, RAINBOW -> true;
				// A newer catalog may add palettes or animations this mod cannot draw yet.
				case PALETTE -> EffectColors.knowsPalette(palette);
				case ANIMATED_PALETTE -> EffectColors.knowsPalette(palette) && TitleAnimation.byId(animation) != null
						&& !TitleAnimation.byId(animation).nativeOnly;
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
