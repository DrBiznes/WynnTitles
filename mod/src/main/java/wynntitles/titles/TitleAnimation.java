package wynntitles.titles;

import java.util.Locale;

/**
 * How an animated effect moves. Mirrors the server's catalog animations; an
 * effect with no animation flows, which is how native styles and rainbow move
 * and how mods older than animations draw every animated effect.
 */
public enum TitleAnimation {
	FLOW("Flow", "drifting along the word", 5000),
	PULSE("Pulse", "the whole word pulsing at once", 2400),
	SHIMMER("Shimmer", "a bright gleam sweeping across", 2600),
	WAVE("Wave", "rippling letter by letter", 1800),
	FLICKER("Flicker", "letters wavering and flaring", 110),
	SPARKLE("Sparkle", "letters twinkling like stars", 1700);

	public final String id;
	public final String label;
	public final String blurb;
	/** One beat of the animation when the effect does not set cycleMs. */
	public final int defaultCycleMs;

	TitleAnimation(String label, String blurb, int defaultCycleMs) {
		this.id = name().toLowerCase(Locale.ROOT);
		this.label = label;
		this.blurb = blurb;
		this.defaultCycleMs = defaultCycleMs;
	}

	/** The animation with this catalog id, flow for none, or null if a newer catalog added it. */
	public static TitleAnimation byId(String id) {
		if (id == null || id.isBlank()) {
			return FLOW;
		}
		for (TitleAnimation animation : values()) {
			if (animation.id.equals(id)) {
				return animation;
			}
		}
		return null;
	}
}
