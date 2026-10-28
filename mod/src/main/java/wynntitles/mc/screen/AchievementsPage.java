package wynntitles.mc.screen;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;
import wynntitles.api.ApiTypes.AchievementStats;
import wynntitles.api.ApiTypes.Me;
import wynntitles.api.ApiTypes.Unlock;
import wynntitles.catalog.Catalog;
import wynntitles.mc.TitleRenderer;
import wynntitles.mc.WynnFonts;
import wynntitles.titles.Title;

/**
 * The achievements tab, laid out like a trophy list: categories with their
 * progress on the left, and every achievement in the chosen one on the right
 * with its reward, when it was unlocked and how rare it is.
 */
final class AchievementsPage {
	static final String UPDATE_TO_UNLOCK = "Update WynnTitles to unlock";

	private static final int SIDEBAR_WIDTH = 86;
	private static final int GAP = 6;
	private static final int ROW_GAP = 5;
	private static final int ROW_PADDING = 3;
	private static final int ICON_WIDTH = 30;
	private static final int RARITY_WIDTH = 66;
	private static final DateTimeFormatter UNLOCK_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ROOT);

	/**
	 * Rarity named and coloured after Wynncraft's item tiers, from the share of
	 * players who hold the achievement. Mythic is rarest.
	 */
	enum Rarity {
		MYTHIC("Mythic", 0xAA00AA, 1),
		FABLED("Fabled", 0xFF5555, 5),
		LEGENDARY("Legendary", 0x55FFFF, 10),
		RARE("Rare", 0xFF55FF, 25),
		UNIQUE("Unique", 0xFFFF55, 50),
		NORMAL("Normal", 0xFFFFFF, Double.MAX_VALUE);

		final String label;
		final int color;
		/** Percentages below this get this rarity. */
		final double below;

		Rarity(String label, int color, double below) {
			this.label = label;
			this.color = color;
			this.below = below;
		}

		static Rarity of(double percent) {
			for (Rarity rarity : values()) {
				if (percent < rarity.below) {
					return rarity;
				}
			}
			return NORMAL;
		}
	}

	private final Predicate<Catalog.Achievement> canDetect;
	/** Null shows every category. */
	private String category;
	private Map<String, Long> unlockedAt = Map.of();
	private Catalog catalog;
	private AchievementStats stats;
	private FlowLayout sidebar;
	private FlowLayout list;
	private int textWidth;

	AchievementsPage(Predicate<Catalog.Achievement> canDetect) {
		this.canDetect = canDetect;
	}

	/** Stats are null while they load, or when the server could not send them. */
	FlowLayout build(Catalog catalog, Me me, AchievementStats stats, int width, int height) {
		this.catalog = catalog;
		this.stats = stats;
		// Labels wrap to an exact width, so work out what a row has left for text.
		int rowWidth = width - SIDEBAR_WIDTH - GAP - WynnUi.WELL_INSET - 2 * ROW_PADDING;
		textWidth = rowWidth - ICON_WIDTH - RARITY_WIDTH - 2 * ROW_GAP;
		unlockedAt = me.unlocked().stream()
				.collect(Collectors.toMap(Unlock::achievementId, Unlock::claimedAt, (a, b) -> a));
		if (category != null && !categories().containsKey(category)) {
			category = null;
		}

		FlowLayout page = UIContainers.horizontalFlow(Sizing.fixed(width), Sizing.fixed(height));
		page.gap(GAP);
		sidebar = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		sidebar.gap(1);
		page.child(WynnUi.well(Sizing.fixed(SIDEBAR_WIDTH), Sizing.fill(), sidebar));
		list = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		list.gap(1);
		page.child(WynnUi.well(Sizing.expand(), Sizing.fill(), list));
		fill();
		return page;
	}

	/** Progress toward every achievement, for the panel's footer. */
	FlowLayout footer(Me me) {
		int total = catalog.achievements().size();
		long done = catalog.achievements().stream().filter(a -> unlockedAt.containsKey(a.id())).count();
		FlowLayout footer = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		footer.gap(6);
		footer.verticalAlignment(VerticalAlignment.CENTER);
		footer.child(WynnUi.heading("Progress"));
		footer.child(new ProgressBar(total == 0 ? 0 : (float) done / total).sizing(Sizing.fixed(120), Sizing.fixed(8)));
		footer.child(WynnUi.label(WynnFonts.body(done + " / " + total), WynnUi.TEXT));
		footer.child(WynnUi.label(WynnFonts.small(me.points() + (me.points() == 1 ? " point" : " points")), WynnUi.GOLD));
		return footer;
	}

	/** Categories in catalog order, each with its achievements. */
	private Map<String, List<Catalog.Achievement>> categories() {
		Map<String, List<Catalog.Achievement>> categories = new LinkedHashMap<>();
		for (Catalog.Achievement achievement : catalog.achievements()) {
			categories.computeIfAbsent(achievement.category(), c -> new ArrayList<>()).add(achievement);
		}
		return categories;
	}

	private void fill() {
		Map<String, List<Catalog.Achievement>> categories = categories();
		sidebar.clearChildren();
		sidebar.child(categoryEntry(null, "All", catalog.achievements()));
		categories.forEach((id, achievements) -> sidebar.child(categoryEntry(id, categoryName(id), achievements)));

		list.clearChildren();
		List<Catalog.Achievement> shown = category == null ? catalog.achievements() : categories.get(category);
		for (Catalog.Achievement achievement : shown) {
			list.child(row(achievement));
		}
		if (shown.isEmpty()) {
			list.child(WynnUi.label(WynnFonts.body("No achievements yet."), WynnUi.TEXT_DIM).margins(Insets.of(4)));
		}
	}

	private FlowLayout categoryEntry(String id, String name, List<Catalog.Achievement> achievements) {
		boolean selected = Objects.equals(id, category);
		long done = achievements.stream().filter(a -> unlockedAt.containsKey(a.id())).count();

		FlowLayout entry = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		entry.gap(2);
		entry.padding(Insets.of(3, 3, 5, 3));
		entry.child(WynnUi.label(WynnFonts.body(name), selected ? WynnUi.GOLD : WynnUi.TEXT));
		FlowLayout progress = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
		progress.gap(3);
		progress.verticalAlignment(VerticalAlignment.CENTER);
		progress.child(new ProgressBar(achievements.isEmpty() ? 0 : (float) done / achievements.size())
				.sizing(Sizing.expand(), Sizing.fixed(5)));
		progress.child(WynnUi.label(WynnFonts.small(done + "/" + achievements.size()), WynnUi.TEXT_DIM));
		entry.child(progress);

		highlight(entry, () -> selected);
		entry.cursorStyle(CursorStyle.HAND);
		entry.mouseDown().subscribe((click, doubled) -> {
			if (!selected) {
				category = id;
				WynnUi.playClick();
				fill();
			}
			return true;
		});
		return entry;
	}

	private FlowLayout row(Catalog.Achievement achievement) {
		Long claimedAt = unlockedAt.get(achievement.id());
		boolean unlocked = claimedAt != null;
		boolean needsUpdate = !unlocked && !canDetect.test(achievement);

		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
		row.gap(ROW_GAP);
		row.padding(Insets.of(ROW_PADDING)).verticalAlignment(VerticalAlignment.CENTER);

		FlowLayout icon = UIContainers.verticalFlow(Sizing.fixed(ICON_WIDTH), Sizing.content());
		icon.gap(1);
		icon.horizontalAlignment(HorizontalAlignment.CENTER);
		icon.child(new AchievementIcon(achievement, unlocked));
		icon.child(WynnUi.label(WynnFonts.small(achievement.points() + (achievement.points() == 1 ? " pt" : " pts")),
				tierColor(achievement.tier())));
		row.child(icon);

		FlowLayout text = UIContainers.verticalFlow(Sizing.fixed(textWidth), Sizing.content());
		text.gap(2);
		text.child(WynnUi.label(WynnFonts.body(achievement.name()), unlocked ? WynnUi.GOLD : WynnUi.TEXT).maxWidth(textWidth));
		text.child(WynnUi.label(WynnFonts.body(achievement.description()), WynnUi.TEXT_DIM).maxWidth(textWidth));
		Title reward = Title.of(achievement.rewards().prefix(), achievement.rewards().suffix());
		if (reward != null) {
			text.child(new TitleText(() -> TitleRenderer.render(reward), 1f, WynnUi.TEXT));
		}
		if (needsUpdate) {
			text.child(WynnUi.label(WynnFonts.body(UPDATE_TO_UNLOCK), WynnUi.NEEDS_UPDATE).maxWidth(textWidth));
		}
		row.child(text);
		row.child(rarity(achievement, claimedAt));

		highlight(row, () -> false);
		return row;
	}

	/** The rarity column: share of players, the Wynncraft tier that maps to, and the unlock date. */
	private FlowLayout rarity(Catalog.Achievement achievement, Long claimedAt) {
		FlowLayout column = UIContainers.verticalFlow(Sizing.fixed(RARITY_WIDTH), Sizing.content());
		column.gap(2);
		column.horizontalAlignment(HorizontalAlignment.RIGHT);
		if (stats != null) {
			double percent = stats.percent(achievement.id());
			Rarity rarity = Rarity.of(percent);
			column.child(WynnUi.label(WynnFonts.body(formatPercent(percent)), rarity.color));
			column.child(WynnUi.label(WynnFonts.small(rarity.label), rarity.color));
			column.tooltip(WynnFonts.body(stats.unlocks().getOrDefault(achievement.id(), 0) + " of "
					+ stats.players() + " WynnTitles players have this"));
		} else {
			column.child(WynnUi.label(WynnFonts.body("..."), WynnUi.TEXT_DIM));
		}
		column.child(claimedAt != null
				? WynnUi.label(WynnFonts.small(UNLOCK_DATE.format(
						Instant.ofEpochMilli(claimedAt).atZone(ZoneId.systemDefault()))), WynnUi.UNLOCKED)
				: WynnUi.label(WynnFonts.small("Locked"), WynnUi.TEXT_DIM));
		return column;
	}

	/** Gold while selected, a soft glow while hovered. */
	private static void highlight(FlowLayout layout, BooleanSupplier selected) {
		boolean[] hovered = {false};
		Surface selectedSurface = WynnUi.surface(WynnUi.ROW_SELECTED);
		Surface hoverSurface = WynnUi.surface(WynnUi.ROW_HOVER);
		layout.surface((graphics, c) -> {
			if (selected.getAsBoolean()) {
				selectedSurface.draw(graphics, c);
			} else if (hovered[0]) {
				hoverSurface.draw(graphics, c);
			}
		});
		layout.mouseEnter().subscribe(() -> hovered[0] = true);
		layout.mouseLeave().subscribe(() -> hovered[0] = false);
	}

	/** One decimal below 10%, so rare achievements do not all read as 0%. */
	static String formatPercent(double percent) {
		if (percent > 0 && percent < 0.1) {
			return "<0.1%";
		}
		return (percent < 10 ? String.format(Locale.ROOT, "%.1f", percent) : String.valueOf(Math.round(percent))) + "%";
	}

	private static String categoryName(String id) {
		StringBuilder name = new StringBuilder();
		for (String word : id.split("_")) {
			if (!word.isEmpty()) {
				name.append(name.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return name.toString();
	}

	private static int tierColor(String tier) {
		return switch (tier) {
			case "silver" -> 0xD2D6E0;
			case "gold" -> 0xF2CF4A;
			default -> 0xE0A060;
		};
	}

	/** A gold bar in a dark trough. */
	private static final class ProgressBar extends BaseUIComponent {
		private static final Identifier TROUGH = WynnUi.sprite("progress");
		private static final Identifier FILL = WynnUi.sprite("progress_fill");
		private final float progress;

		ProgressBar(float progress) {
			this.progress = Math.clamp(progress, 0f, 1f);
		}

		@Override
		public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
			WynnUi.blit(graphics, TROUGH, x, y, width, height);
			int fill = Math.round((width - 2) * progress);
			if (fill > 0) {
				WynnUi.blit(graphics, FILL, x + 1, y + 1, fill, height - 2);
			}
		}
	}
}
