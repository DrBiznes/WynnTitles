package wynntitles.mc.screen;

import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import wynntitles.catalog.Catalog;
import wynntitles.mc.WynnFonts;

/**
 * The unlock toast's face, in the screens' style, for one unlock or a batch: a stone-rimmed purple field
 * with the achievement's tier-framed hex in a dark niche at the left. Lives here
 * for the palette; mc/UnlockToast handles the toast's lifetime. The sprite comes
 * from scripts/draw-ui-sprites.py; the offsets below must match its TOAST_ layout.
 */
public final class UnlockToastArt {
	private static final Identifier BACKGROUND = WynnUi.sprite("toast");
	/** The niche starts inside the 3px rim; the hex's points rest on the rim. */
	private static final int ICON_X = 4;
	/** Past the divider and the field's corner ticks. */
	private static final int TEXT_X = 42;
	/** Clear of the right-hand corner ticks, which reach 9px in from the edge. */
	private static final int TEXT_RIGHT = 11;
	/** Vanilla's toast width; the toast widens up to MAX_WIDTH to fit its text. */
	private static final int MIN_WIDTH = 160;
	private static final int MAX_WIDTH = 200;
	private static final String TITLE = "Achievement unlocked";
	private static final String SHORT_TITLE = "Unlocked";
	private static final int TITLE_Y = 7;
	private static final int NAME_Y = 18;
	/** Lowest first, for headline(); an unknown tier ranks below bronze. */
	private static final List<String> TIERS = List.of("bronze", "silver", "gold");

	private UnlockToastArt() {}

	public static void draw(GuiGraphics graphics, Font font, List<Catalog.Achievement> unlocked, int width, int height) {
		WynnUi.blit(graphics, BACKGROUND, 0, 0, width, height);
		AchievementIcon.draw(graphics, headline(unlocked), true, ICON_X, (height - AchievementIcon.HEIGHT) / 2);
		// Colours are ARGB since 1.21.6; the palette is RGB.
		int room = width - TEXT_X - TEXT_RIGHT;
		String title = font.width(WynnFonts.small(title(unlocked))) <= room ? title(unlocked) : shortTitle(unlocked);
		graphics.drawString(font, WynnFonts.small(title), TEXT_X, TITLE_Y, 0xFF000000 | WynnUi.GOLD, false);
		graphics.drawString(font, WynnFonts.body(fit(font, body(unlocked), room)),
				TEXT_X, NAME_Y, 0xFF000000 | WynnUi.TEXT, false);
	}

	/**
	 * Wide enough for the title and name in the current font, within limits:
	 * the vanilla font's capitals are wider than the pack's.
	 */
	public static int width(Font font, List<Catalog.Achievement> unlocked) {
		int text = Math.max(font.width(WynnFonts.small(title(unlocked))), font.width(WynnFonts.body(body(unlocked))));
		return Math.clamp(TEXT_X + text + TEXT_RIGHT, MIN_WIDTH, MAX_WIDTH);
	}

	/** Highest tier first, then the most points; the first leads a batch's toast. */
	public static final Comparator<Catalog.Achievement> BEST_FIRST = Comparator
			.comparingInt((Catalog.Achievement a) -> TIERS.indexOf(a.tier()))
			.thenComparingInt(Catalog.Achievement::points)
			.reversed();

	public static Catalog.Achievement headline(List<Catalog.Achievement> unlocked) {
		return unlocked.stream().min(BEST_FIRST).orElseThrow();
	}

	private static String title(List<Catalog.Achievement> unlocked) {
		return unlocked.size() == 1 ? TITLE : unlocked.size() + " achievements unlocked";
	}

	private static String shortTitle(List<Catalog.Achievement> unlocked) {
		return unlocked.size() == 1 ? SHORT_TITLE : unlocked.size() + " unlocked";
	}

	/** A lone unlock shows its name; a batch shows what it earned. */
	private static String body(List<Catalog.Achievement> unlocked) {
		if (unlocked.size() == 1) {
			return unlocked.get(0).name();
		}
		return "+" + unlocked.stream().mapToInt(Catalog.Achievement::points).sum() + " points";
	}

	/** The name, cut short with dots if it would run into the rim. */
	private static String fit(Font font, String name, int maxWidth) {
		if (font.width(WynnFonts.body(name)) <= maxWidth) {
			return name;
		}
		String cut = name;
		while (!cut.isEmpty() && font.width(WynnFonts.body(cut + "...")) > maxWidth) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut.stripTrailing() + "...";
	}
}
