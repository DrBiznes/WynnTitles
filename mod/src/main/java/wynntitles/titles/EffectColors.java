package wynntitles.titles;

import java.awt.Color;
import java.util.List;
import java.util.Map;

/**
 * The color of every letter of an effect at a given moment: the palettes and
 * the animations. Pure Java, so it is unit tested, and the only place effects
 * are colored, so nametags, chat and screens all draw them the same. Time is
 * wall-clock milliseconds, which keeps every player's copy of a title in step.
 */
public final class EffectColors {
	private static final int WHITE = 0xFFFFFF;
	/** Vanilla's bright colors in hue order, with blue lifted to read (see {@link #readable}). */
	private static final List<Integer> RAINBOW = List.of(0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF)
			.stream().map(EffectColors::readable).toList();
	/** The hue wheel for plain rainbow, sampled once, with its dark blues lifted to read. */
	private static final int[] HUES = new int[256];

	static {
		for (int i = 0; i < HUES.length; i++) {
			HUES[i] = readable(Color.HSBtoRGB(i / (float) HUES.length, 0.8f, 0.9f) & 0xFFFFFF);
		}
	}

	/**
	 * Each palette is its own look, ordered as it lies along a word. Every color
	 * on the way, stops and the blends between them, reads at least as well as
	 * vanilla red on a shop chip's dark stone (see {@link #MIN_CONTRAST}), and a
	 * nametag's backing is darker still, so no stop is a deep shade.
	 */
	static final Map<String, List<Integer>> PALETTES = Map.ofEntries(
			Map.entry("rainbow", RAINBOW),
			// Crimson: one saturated red, scarlet to a hot coral.
			Map.entry("red", List.of(0xFF4050, 0xFF6470, 0xFF9C92, 0xFF6470)),
			// Blood Moon: red turning through rose to violet.
			Map.entry("red_purple", List.of(0xFF4A55, 0xE8559A, 0xB47CFF)),
			// Flame: a white-hot core through gold and orange to red tips.
			Map.entry("flame", List.of(0xFFF2A8, 0xFFC93C, 0xFF8A1F, 0xFF5A2A)),
			// Ember: copper coals glowing amber, no white or yellow.
			Map.entry("ember", List.of(0xE0702A, 0xF08A35, 0xFFB45A, 0xF08A35)),
			// Sunset: violet sky, pink, orange, the last gold.
			Map.entry("sunset", List.of(0xC86BFA, 0xFF6B9A, 0xFF9F45, 0xFFE066)),
			// Ocean: saturated blue to teal and seafoam.
			Map.entry("ocean", List.of(0x4F86FF, 0x2FB0EA, 0x22D3C5, 0x9DF7E5)),
			// Ice: pale frost blues, lighter and greyer than Ocean.
			Map.entry("ice", List.of(0xC4F1FF, 0x86D6F7, 0x5BB4EE, 0xA9E4FB)),
			// Toxic: acid yellow-lime to neon green, louder than Forest.
			Map.entry("toxic", List.of(0xE4FF3A, 0x9CFF1E, 0x3CF04F)),
			// Forest: leaf, moss and lichen, ending in bark brown.
			Map.entry("forest", List.of(0x45B05A, 0x6CBF55, 0xA8C860, 0xB58A55)),
			// Royal: a band of gold across regal purple, like a crown.
			Map.entry("royal", List.of(0xA070FF, 0xC4A2FF, 0xF7C948, 0xC4A2FF, 0xA070FF)),
			// Void: cold periwinkle and violet starlight, bluer than Royal.
			Map.entry("void", List.of(0x7C8CFF, 0x9F7BFF, 0x8C9CFF, 0xD8CCFF)));

	/**
	 * The least contrast (WCAG ratio) a palette color may have against {@link #CHIP},
	 * the stone behind shop chips: vanilla red (#FF5555) has 3.4 there.
	 */
	static final double MIN_CONTRAST = 3.0;
	/** The fill of a shop chip, WynnUi's darkest common text background after nametags. */
	static final int CHIP = 0x443D41;

	private static final Map<String, Integer> NAMED = Map.ofEntries(
			Map.entry("black", 0x000000), Map.entry("dark_blue", 0x0000AA), Map.entry("dark_green", 0x00AA00),
			Map.entry("dark_aqua", 0x00AAAA), Map.entry("dark_red", 0xAA0000), Map.entry("dark_purple", 0xAA00AA),
			Map.entry("gold", 0xFFAA00), Map.entry("gray", 0xAAAAAA), Map.entry("dark_gray", 0x555555),
			Map.entry("blue", 0x5555FF), Map.entry("green", 0x55FF55), Map.entry("aqua", 0x55FFFF),
			Map.entry("red", 0xFF5555), Map.entry("light_purple", 0xFF55FF), Map.entry("yellow", 0xFFFF55),
			Map.entry("white", 0xFFFFFF));

	/** Shimmer: the share of the beat the gleam takes to cross, then a rest. */
	private static final float SWEEP = 0.55f;
	/** Wave: letters per ripple. */
	private static final float WAVELENGTH = 7f;

	private EffectColors() {}

	/** Whether this mod knows the palette; unknown ones draw as rainbow. */
	public static boolean knowsPalette(String palette) {
		return PALETTES.containsKey(palette);
	}

	/** The colors of a run's letters at {@code nowMs}. */
	public static int[] colors(TitleEffect effect, int length, long nowMs) {
		int[] out = new int[length];
		Ramp ramp = Ramp.of(effect);
		TitleAnimation animation = effect.animated() ? TitleAnimation.byId(effect.animation()) : null;
		if (effect.animated() && animation == null) {
			animation = TitleAnimation.FLOW; // added by a newer catalog
		}
		int cycle = effect.cycleMs() > 0 ? effect.cycleMs() : animation == null ? 1 : animation.defaultCycleMs;
		for (int i = 0; i < length; i++) {
			out[i] = color(ramp, animation, cycle, i, length, nowMs);
		}
		return out;
	}

	/** A color from the rainbow, for things that cycle with it (0 to 1 is once round). */
	public static int rainbow(float t) {
		float scaled = frac(t) * HUES.length;
		int left = Math.min((int) scaled, HUES.length - 1);
		return mixRgb(HUES[left], HUES[(left + 1) % HUES.length], scaled - left);
	}

	/**
	 * The color mixed toward white just enough to read on a chip, with a little
	 * headroom over {@link #MIN_CONTRAST} so blends between neighbours pass too.
	 */
	static int readable(int color) {
		for (int step = 0; step <= 50; step++) {
			int lifted = mixRgb(color, WHITE, step / 50f);
			if (contrast(lifted, CHIP) >= MIN_CONTRAST + 0.1) {
				return lifted;
			}
		}
		return WHITE;
	}

	private static int color(Ramp ramp, TitleAnimation animation, int cycle, int i, int length, long now) {
		float position = length <= 1 ? 0f : (float) i / (length - 1);
		if (animation == null) {
			return ramp.at(position);
		}
		float phase = (now % cycle) / (float) cycle;
		return switch (animation) {
			// The gradient scrolls along the word and round again without a seam.
			case FLOW -> ramp.loop(position + phase);
			// Every letter at once, out through the palette and back.
			case PULSE -> ramp.at(0.5f - 0.5f * (float) Math.cos(2 * Math.PI * phase));
			case SHIMMER -> shimmer(ramp.at(position), i, length, phase);
			case WAVE -> wave(ramp, i, phase);
			case FLICKER -> flicker(ramp, position, i, now, cycle);
			case SPARKLE -> sparkle(ramp, position, i, now, cycle);
		};
	}

	/** A white gleam a few letters wide crosses the word, then rests until the next beat. */
	private static int shimmer(int base, int i, int length, float phase) {
		float progress = phase / SWEEP;
		if (progress > 1f) {
			return base;
		}
		float center = -2.5f + progress * (length + 4f);
		float k = smooth(Math.max(0f, 1f - Math.abs(i - center) / 2.2f));
		return mix(base, WHITE, 0.85f * k);
	}

	/** Each letter swings through the palette a little behind the one before it; crests catch the light. */
	private static int wave(Ramp ramp, int i, float phase) {
		float s = 0.5f + 0.5f * (float) Math.sin(2 * Math.PI * (phase - i / WAVELENGTH));
		int color = ramp.at(s);
		return s > 0.85f ? mix(color, WHITE, (s - 0.85f) / 0.15f * 0.3f) : color;
	}

	/**
	 * Each letter's heat wanders at random from beat to beat, and now and then
	 * one flares bright or gutters to the palette's darkest color for a beat.
	 */
	private static int flicker(Ramp ramp, float position, int i, long now, int beat) {
		long b = now / beat;
		float t = smooth((now % beat) / (float) beat);
		float heat = lerp(hash01(i, b), hash01(i, b + 1), t);
		int color = ramp.at(0.35f * position + 0.65f * heat);
		float roll = hash01(i * 31L + 7, b);
		if (roll < 0.07f) {
			return mix(ramp.lightest(), WHITE, 0.35f);
		}
		if (roll > 0.95f) {
			return ramp.darkest(); // gutters, but never darker than the palette
		}
		return color;
	}

	/**
	 * The still gradient, whose letters each keep their own clock; in some beats
	 * a letter twinkles, flashing to near white and fading. Nothing is dimmed, so
	 * the word stays as readable as the palette.
	 */
	private static int sparkle(Ramp ramp, float position, int i, long now, int cycle) {
		int base = ramp.at(position);
		long local = now + (long) (hash01(i, 0x5EED) * cycle);
		long round = local / cycle;
		float phase = (local % cycle) / (float) cycle;
		if (hash01(i, round) >= 0.45f || phase >= 0.3f) {
			return base;
		}
		float glow = (float) Math.sin(Math.PI * phase / 0.3f);
		return mix(base, mix(ramp.lightest(), WHITE, 0.6f), glow);
	}

	/** Colors along 0 to 1, from a palette, explicit colors, or the hue wheel for plain rainbow. */
	private record Ramp(List<Integer> colors) {
		static Ramp of(TitleEffect effect) {
			if (!effect.colors().isEmpty()) {
				return new Ramp(effect.colors().stream().map(EffectColors::colorValue).toList());
			}
			if ("rainbow".equals(effect.type()) && effect.palette() == null) {
				return new Ramp(null);
			}
			return new Ramp(PALETTES.getOrDefault(effect.palette(), RAINBOW));
		}

		int at(float t) {
			if (colors == null) {
				return rainbow(Math.clamp(t, 0f, 1f));
			}
			return gradient(colors, Math.clamp(t, 0f, 1f));
		}

		/** Like at, but the last color runs back into the first, so it can go round forever. */
		int loop(float t) {
			t = frac(t);
			if (colors == null) {
				return rainbow(t);
			}
			int n = colors.size();
			float scaled = t * n;
			int left = Math.min((int) scaled, n - 1);
			return mixRgb(colors.get(left), colors.get((left + 1) % n), scaled - left);
		}

		int lightest() {
			return colors == null ? WHITE : extreme(true);
		}

		int darkest() {
			return colors == null ? rainbow(0f) : extreme(false);
		}

		private int extreme(boolean light) {
			int best = colors.get(0);
			for (int color : colors) {
				if (light ? luma(color) > luma(best) : luma(color) < luma(best)) {
					best = color;
				}
			}
			return best;
		}
	}

	private static int gradient(List<Integer> colors, float position) {
		if (colors.size() == 1) {
			return colors.get(0);
		}
		float scaled = position * (colors.size() - 1);
		int left = Math.min((int) scaled, colors.size() - 2);
		return mixRgb(colors.get(left), colors.get(left + 1), scaled - left);
	}

	/** {@code a} moved {@code t} of the way to {@code b}. */
	static int mix(int a, int b, float t) {
		return mixRgb(a, b, Math.clamp(t, 0f, 1f));
	}

	private static int mixRgb(int a, int b, float t) {
		return channel(a >> 16, b >> 16, t) << 16 | channel(a >> 8, b >> 8, t) << 8 | channel(a, b, t);
	}

	private static int channel(int a, int b, float t) {
		return Math.round(lerp(a & 0xFF, b & 0xFF, t));
	}

	static float luma(int color) {
		return 0.2126f * (color >> 16 & 0xFF) + 0.7152f * (color >> 8 & 0xFF) + 0.0722f * (color & 0xFF);
	}

	/** The WCAG contrast ratio between two colors, from 1 (same) to 21 (black on white). */
	static double contrast(int a, int b) {
		double la = luminance(a), lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	private static double luminance(int color) {
		return 0.2126 * linear(color >> 16 & 0xFF) + 0.7152 * linear(color >> 8 & 0xFF) + 0.0722 * linear(color & 0xFF);
	}

	private static double linear(int channel) {
		double c = channel / 255.0;
		return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}

	private static float frac(float t) {
		return t - (float) Math.floor(t);
	}

	/** A fixed pseudo-random number in [0, 1) for a pair of values, the same on every client. */
	private static float hash01(long a, long b) {
		long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (h >>> 40) / (float) (1L << 24);
	}

	/** A Minecraft color name or #rrggbb; anything else is white. */
	public static int colorValue(String color) {
		if (color == null) {
			return WHITE;
		}
		Integer named = NAMED.get(color.toLowerCase(java.util.Locale.ROOT));
		if (named != null) {
			return named;
		}
		if (color.length() == 7 && color.charAt(0) == '#') {
			try {
				return Integer.parseInt(color.substring(1), 16);
			} catch (NumberFormatException e) {
				return WHITE;
			}
		}
		return WHITE;
	}
}
