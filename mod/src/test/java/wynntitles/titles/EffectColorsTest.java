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
		assertEquals(11, looks.size());
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
