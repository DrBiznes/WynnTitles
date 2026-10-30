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
	SPARKLE("Sparkle", "letters twinkling like stars", 1700),
	COMET("Comet", "a white-hot comet streaking across", 3200),
	AURORA("Aurora", "curtains of light that never repeat", 6000),
	CHARGE("Charge", "filling with power, flashing, draining", 3000),
	RIPPLE("Ripple", "rings spreading from the middle", 2000),
	CONVERGE("Converge", "two gleams meeting in a flash", 2800),
	// Native only: never sold, so only achievements' own styles move this way.
	DECIPHER("Decipher", "runes resolving into the word", 9000, true),
	GLITCH("Glitch", "bursts of scrambled letters", 1900, true),
	ECLIPSE("Eclipse", "a shadow crossing the word", 4200, true),
	STRIKE("Strike", "a lightning flash cooling off", 3200, true),
	HEARTBEAT("Heartbeat", "a double beat and a rest", 1500, true),
	SWELL("Swell", "a bold crest rolling along", 2600, true),
	ASCEND("Ascend", "climbing through its colors to a bold finish", 5000, true),
	REEL("Reel", "spinning letters locking in one by one", 5400, true),
	PRISM("Prism", "a blinding flash scattering into colored light", 4000, true);

	public final String id;
	public final String label;
	public final String blurb;
	/** One beat of the animation when the effect does not set cycleMs. */
	public final int defaultCycleMs;
	/** Reserved for native styles, like obfuscated; the server refuses it in the shop. */
	public final boolean nativeOnly;

	TitleAnimation(String label, String blurb, int defaultCycleMs) {
		this(label, blurb, defaultCycleMs, false);
	}

	TitleAnimation(String label, String blurb, int defaultCycleMs, boolean nativeOnly) {
		this.id = name().toLowerCase(Locale.ROOT);
		this.label = label;
		this.blurb = blurb;
		this.defaultCycleMs = defaultCycleMs;
		this.nativeOnly = nativeOnly;
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
