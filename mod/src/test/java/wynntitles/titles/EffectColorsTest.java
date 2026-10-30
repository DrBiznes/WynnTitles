package wynntitles.titles;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import wynntitles.catalog.Catalog.Achievement;
import wynntitles.catalog.Catalog.ShopItem;

class EffectColorsTest {
	private static final String WORD = "Sentinel";
	private static final long T0 = 1_700_000_000_000L;

	private static TitleEffect animated(String palette, TitleAnimation animation) {
		return new TitleEffect("gradient", null, palette, true, 0, animation.id);
	}

	@Test
	void staticGradientRunsAcrossThePalette() {
		TitleEffect effect = new TitleEffect("gradient", List.of("#000000", "white"), null, false, 0, null);
		int[] colors = EffectColors.colors(effect, 3, T0);
		assertArrayEquals(new int[] {0x000000, 0x808080, 0xFFFFFF}, colors);
		// Not animated, so time does not matter.
		assertArrayEquals(colors, EffectColors.colors(effect, 3, T0 + 1234));
	}

	@Test
	void everyAnimationMovesAndIsTheSameEverywhereAtOneMoment() {
		for (TitleAnimation animation : TitleAnimation.values()) {
			TitleEffect effect = animated("ocean", animation);
			Set<String> frames = new HashSet<>();
			for (int ms = 0; ms < 3000; ms += 37) {
				frames.add(Arrays.toString(EffectColors.colors(effect, WORD.length(), T0 + ms)));
			}
			assertTrue(frames.size() > 5, animation + " should move, got " + frames.size() + " frames");
			assertArrayEquals(EffectColors.colors(effect, WORD.length(), T0 + 500),
					EffectColors.colors(effect, WORD.length(), T0 + 500), animation + " should not be random per call");
		}
	}

	@Test
	void pulseColorsTheWholeWordAtOnce() {
		for (int ms = 0; ms < 2400; ms += 150) {
			int[] colors = EffectColors.colors(animated("red", TitleAnimation.PULSE), WORD.length(), T0 + ms);
			assertEquals(1, Arrays.stream(colors).distinct().count());
		}
	}

	@Test
	void flowGoesRoundWithoutASeam() {
		TitleEffect effect = animated("sunset", TitleAnimation.FLOW);
		long start = T0 - T0 % 5000;
		assertArrayEquals(EffectColors.colors(effect, WORD.length(), start),
				EffectColors.colors(effect, WORD.length(), start + 5000));
		// Neighbouring letters stay close: no jump from the last color to the first.
		for (int ms = 0; ms < 5000; ms += 250) {
			int[] colors = EffectColors.colors(effect, 30, start + ms);
			for (int i = 1; i < colors.length; i++) {
				assertTrue(distance(colors[i - 1], colors[i]) < 90, "seam at " + ms + "ms letter " + i);
			}
		}
	}

	@Test
	void shimmerRestsBetweenGleams() {
		TitleEffect effect = animated("ice", TitleAnimation.SHIMMER);
		long start = T0 - T0 % 2600;
		int[] resting = EffectColors.colors(effect, WORD.length(), start + 2500);
		int[] still = EffectColors.colors(new TitleEffect("gradient", null, "ice", false, 0, null), WORD.length(), start);
		assertArrayEquals(still, resting);
		assertFalse(Arrays.equals(still, EffectColors.colors(effect, WORD.length(), start + 700)));
	}

	@Test
	void cometAndConvergeRestAtTheStillGradient() {
		int[] still = EffectColors.colors(new TitleEffect("gradient", null, "nebula", false, 0, null), WORD.length(), T0);
		for (TitleAnimation animation : List.of(TitleAnimation.COMET, TitleAnimation.CONVERGE)) {
			int beat = animation.defaultCycleMs;
			long start = T0 - T0 % beat;
			TitleEffect effect = animated("nebula", animation);
			assertArrayEquals(still, EffectColors.colors(effect, WORD.length(), start + beat - 50), animation + " should rest");
			assertFalse(Arrays.equals(still, EffectColors.colors(effect, WORD.length(), start + beat / 5)), animation + " should move");
		}
		// Comet: the head is lightest, and letters it has not reached keep their color.
		long start = T0 - T0 % 3200;
		int[] mid = EffectColors.colors(animated("nebula", TitleAnimation.COMET), WORD.length(), start + 900);
		assertEquals(still[WORD.length() - 1], mid[WORD.length() - 1]);
	}

	@Test
	void chargeFillsFromTheLeftAndRestsDim() {
		TitleEffect effect = animated("storm", TitleAnimation.CHARGE);
		long start = T0 - T0 % 3000;
		int[] resting = EffectColors.colors(effect, WORD.length(), start + 2900);
		assertEquals(1, Arrays.stream(resting).distinct().count());
		int dim = resting[0];
		int[] half = EffectColors.colors(effect, WORD.length(), start + 700);
		assertNotEquals(dim, half[0]);
		assertEquals(dim, half[WORD.length() - 1]);
	}

	@Test
	void rippleSpreadsEvenlyFromTheMiddle() {
		TitleEffect effect = animated("blossom", TitleAnimation.RIPPLE);
		for (int ms = 0; ms < 2000; ms += 130) {
			int[] colors = EffectColors.colors(effect, 9, T0 + ms);
			for (int i = 0; i < 4; i++) {
				assertEquals(colors[i], colors[8 - i], "letter " + i + " at " + ms + "ms");
			}
		}
	}

	@Test
	void auroraDoesNotRepeatEachBeat() {
		TitleEffect effect = animated("borealis", TitleAnimation.AURORA);
		assertFalse(Arrays.equals(EffectColors.colors(effect, WORD.length(), T0),
				EffectColors.colors(effect, WORD.length(), T0 + 6000)));
	}

	@Test
	void plainRainbowAndUnknownAnimationsStillDraw() {
		int[] rainbow = EffectColors.colors(new TitleEffect("rainbow", null, null, true, 0, null), WORD.length(), T0);
		assertEquals(WORD.length(), rainbow.length);
		// A newer catalog's animation flows rather than breaking.
		assertArrayEquals(EffectColors.colors(animated("ocean", TitleAnimation.FLOW), 5, T0),
				EffectColors.colors(new TitleEffect("gradient", null, "ocean", true, 0, "spin"), 5, T0));
	}

	/**
	 * The real shop: this mod knows every item, no two animated looks match, and
	 * every color any item can show, in any frame, reads against a shop chip's
	 * stone as well as vanilla red does. Nametag backings are darker, so they pass too.
	 */
	@Test
	void shopItemsAreReadableAndDistinct() throws Exception {
		ShopItem[] shop = new Gson().fromJson(Files.readString(Path.of("../server/catalog/shop.json")), ShopItem[].class);
		Set<String> looks = new HashSet<>();
		for (ShopItem item : shop) {
			assertTrue(item.supported(), item.id() + " should be supported");
			TitleEffect effect = switch (item.kind()) {
				case ShopItem.PALETTE -> new TitleEffect("gradient", null, item.palette(), false, 0, null);
				case ShopItem.ANIMATED_PALETTE -> new TitleEffect("gradient", null, item.palette(), true, 0, item.animation());
				case ShopItem.RAINBOW -> new TitleEffect("rainbow", null, null, true, 0, null);
				default -> null;
			};
			if (effect == null) {
				continue;
			}
			if (ShopItem.ANIMATED_PALETTE.equals(item.kind())) {
				assertNotEquals(null, item.motion(), item.id());
				assertTrue(looks.add(item.palette() + "/" + item.animation()), item.id() + " repeats a look");
				// Flow wraps the last stop into the first, so check that blend as well.
				TitleEffect flow = new TitleEffect("gradient", null, item.palette(), true, 5000, null);
				for (int ms = 0; ms < 5000; ms += 50) {
					assertReadable(item.id() + " flowing", EffectColors.colors(flow, 40, T0 + ms));
				}
			}
			for (int ms = 0; ms < 6000; ms += 23) {
				assertReadable(item.id() + " at " + ms + "ms", EffectColors.colors(effect, 12, T0 + ms));
			}
			assertReadable(item.id() + " long", EffectColors.colors(effect, 40, T0));
		}
		assertEquals(16, looks.size());
	}

	/** Every native effect, in every frame, reads as well as the shop's do. */
	@Test
	void nativeStylesAreReadable() throws Exception {
		Achievement[] achievements = new Gson().fromJson(
				Files.readString(Path.of("../server/catalog/achievements.json")), Achievement[].class);
		int effects = 0;
		for (Achievement achievement : achievements) {
			for (TitleWord word : new TitleWord[] {achievement.rewards().prefix(), achievement.rewards().suffix()}) {
				if (word == null) {
					continue;
				}
				for (TitleRun run : word.runs()) {
					if (run.effect() == null) {
						continue;
					}
					effects++;
					String what = achievement.id() + " " + run.text();
					for (int ms = 0; ms < 10_000; ms += 23) {
						assertReadable(what + " at " + ms + "ms", EffectColors.colors(run.effect(), run.text().length(), T0 + ms));
					}
					assertReadable(what + " long", EffectColors.colors(run.effect(), 40, T0));
				}
			}
		}
		assertTrue(effects > 30, "only " + effects + " native effects");
	}

	@Test
	void decipherScramblesThenReadsLeftToRight() {
		TitleEffect effect = animated("royal", TitleAnimation.DECIPHER);
		int beat = TitleAnimation.DECIPHER.defaultCycleMs;
		long start = T0 - T0 % beat;
		assertTrue(Arrays.stream(EffectColors.letters(effect, WORD.length(), start))
				.allMatch(l -> l.glyph() == EffectColors.Glyph.SCRAMBLED));
		// Partway in, the first letters read, one shows as a rune and the rest are still scrambled.
		List<EffectColors.Glyph> glyphs = Arrays.stream(EffectColors.letters(effect, WORD.length(), start + beat / 5))
				.map(EffectColors.Letter::glyph).toList();
		assertEquals(EffectColors.Glyph.PLAIN, glyphs.get(0));
		assertTrue(glyphs.contains(EffectColors.Glyph.RUNE), glyphs.toString());
		assertEquals(EffectColors.Glyph.SCRAMBLED, glyphs.get(WORD.length() - 1));
		assertTrue(Arrays.stream(EffectColors.letters(effect, WORD.length(), start + beat * 3 / 4))
				.allMatch(l -> l.glyph() == EffectColors.Glyph.PLAIN));
	}

	@Test
	void glitchIsMostlyStill() {
		TitleEffect effect = animated("void", TitleAnimation.GLITCH);
		int[] still = EffectColors.colors(new TitleEffect("gradient", null, "void", false, 0, null), WORD.length(), T0);
		int plain = 0, frames = 0;
		for (int ms = 0; ms < 19_000; ms += 31, frames++) {
			if (Arrays.equals(still, EffectColors.colors(effect, WORD.length(), T0 + ms))) {
				plain++;
			}
		}
		assertTrue(plain > frames * 0.8, plain + " of " + frames + " frames still");
	}

	@Test
	void heartbeatAndSwellBoldOnlyOnTheBeat() {
		TitleEffect heart = animated("red", TitleAnimation.HEARTBEAT);
		TitleEffect swell = animated("sunset", TitleAnimation.SWELL);
		int beat = TitleAnimation.HEARTBEAT.defaultCycleMs;
		long start = T0 - T0 % beat;
		assertTrue(Arrays.stream(EffectColors.letters(heart, WORD.length(), start + beat * 8 / 100)).allMatch(EffectColors.Letter::bold));
		assertTrue(Arrays.stream(EffectColors.letters(heart, WORD.length(), start + beat / 2)).noneMatch(EffectColors.Letter::bold));
		for (int ms = 0; ms < 2600; ms += 50) {
			long bold = Arrays.stream(EffectColors.letters(swell, WORD.length(), T0 + ms)).filter(EffectColors.Letter::bold).count();
			assertTrue(bold <= 1, bold + " letters bold at " + ms + "ms");
		}
	}

	@Test
	void ascendClimbsItsColorsThenHoldsBold() {
		TitleEffect effect = new TitleEffect("gradient", List.of("#D98A4E", "#D0DAE6", "#FFD23C"), null, true, 0, TitleAnimation.ASCEND.id);
		int beat = TitleAnimation.ASCEND.defaultCycleMs;
		long start = T0 - T0 % beat;
		for (EffectColors.Letter letter : EffectColors.letters(effect, WORD.length(), start)) {
			assertEquals(EffectColors.Letter.of(0xD98A4E), letter);
		}
		// Most of the way through the first wipe, the second color has reached all but the end.
		EffectColors.Letter[] climbing = EffectColors.letters(effect, WORD.length(), start + beat / 5);
		assertEquals(0xD0DAE6, climbing[0].color());
		assertEquals(0xD98A4E, climbing[WORD.length() - 1].color());
		for (EffectColors.Letter letter : EffectColors.letters(effect, WORD.length(), start + beat * 9 / 10)) {
			assertEquals(new EffectColors.Letter(0xFFD23C, true, EffectColors.Glyph.PLAIN), letter);
		}
	}

	@Test
	void reelLocksLeftToRightThenSettles() {
		TitleEffect effect = animated("flame", TitleAnimation.REEL);
		int beat = TitleAnimation.REEL.defaultCycleMs;
		long start = T0 - T0 % beat;
		assertTrue(Arrays.stream(EffectColors.letters(effect, WORD.length(), start))
				.allMatch(l -> l.glyph() == EffectColors.Glyph.SCRAMBLED));
		EffectColors.Letter[] locking = EffectColors.letters(effect, WORD.length(), start + beat / 5);
		assertEquals(EffectColors.Glyph.PLAIN, locking[0].glyph());
		assertEquals(EffectColors.Glyph.SCRAMBLED, locking[WORD.length() - 1].glyph());
		int[] still = EffectColors.colors(new TitleEffect("gradient", null, "flame", false, 0, null), WORD.length(), start);
		assertArrayEquals(still, EffectColors.colors(effect, WORD.length(), start + beat * 47 / 100));
	}

	@Test
	void prismFlashesWhiteThenScattersFromTheMiddle() {
		TitleEffect effect = new TitleEffect("gradient", List.of("#DFF6FF", "#C0C8FF", "#F4C4FF", "#FFD0E0", "#C0FFE8"), null, true, 0, TitleAnimation.PRISM.id);
		int beat = TitleAnimation.PRISM.defaultCycleMs;
		long start = T0 - T0 % beat;
		assertEquals(1, Arrays.stream(EffectColors.colors(effect, 9, start)).distinct().count());
		int[] scattering = EffectColors.colors(effect, 9, start + beat * 20 / 100);
		assertNotEquals(0xFFFFFF, scattering[4]);
		assertTrue(EffectColors.luma(scattering[0]) > EffectColors.luma(scattering[4]), "the ends should still be brighter");
	}

	@Test
	void heldStillEveryLetterIsPlain() {
		for (TitleAnimation animation : TitleAnimation.values()) {
			for (EffectColors.Letter letter : EffectColors.letters(animated("ice", animation).still(), WORD.length(), T0)) {
				assertEquals(EffectColors.Letter.of(letter.color()), letter, animation.id);
			}
		}
	}

	@Test
	void rainbowIsLiftedOnlyWhereItIsDark() {
		for (int i = 0; i < 100; i++) {
			assertTrue(EffectColors.contrast(EffectColors.rainbow(i / 100f), EffectColors.CHIP) >= EffectColors.MIN_CONTRAST);
		}
		// Yellow already reads, so it keeps its color; pure blue is lifted, but stays blue.
		assertEquals(0xFFFF55, EffectColors.readable(0xFFFF55));
		int blue = EffectColors.readable(0x0000FF);
		assertTrue((blue & 0xFF) == 0xFF && (blue >> 16 & 0xFF) < 0xC0, Integer.toHexString(blue));
	}

	private static void assertReadable(String what, int[] colors) {
		for (int color : colors) {
			double contrast = EffectColors.contrast(color, EffectColors.CHIP);
			assertTrue(contrast >= EffectColors.MIN_CONTRAST, String.format(
					"%s shows #%06x, contrast %.2f on a chip; needs %.1f", what, color, contrast, EffectColors.MIN_CONTRAST));
		}
	}

	private static double distance(int a, int b) {
		int dr = (a >> 16 & 0xFF) - (b >> 16 & 0xFF), dg = (a >> 8 & 0xFF) - (b >> 8 & 0xFF), db = (a & 0xFF) - (b & 0xFF);
		return Math.sqrt(dr * dr + dg * dg + db * db);
	}
}
