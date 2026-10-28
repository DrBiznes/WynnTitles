package wynntitles.mc;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import wynntitles.titles.Title;
import wynntitles.titles.TitleEffect;
import wynntitles.titles.TitleRun;
import wynntitles.titles.TitleWord;

/** Turns title runs into chat Components, including gradient and rainbow effects. */
public final class TitleRenderer {
	private static final List<Integer> RAINBOW = List.of(0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF);

	private static final Map<String, List<Integer>> PALETTES = Map.ofEntries(
			Map.entry("rainbow", RAINBOW),
			Map.entry("red", List.of(0x330000, 0x7F0000, 0xAA0000, 0xFF5555, 0xFF9999)),
			Map.entry("red_purple", List.of(0x550000, 0xAA0000, 0xFF5555, 0xAA00AA, 0x5500AA)),
			Map.entry("flame", List.of(0x4A1200, 0xAA2A00, 0xFF5555, 0xFFAA00, 0xFFFF55)),
			Map.entry("ember", List.of(0x1F0000, 0x550000, 0xAA0000, 0xFF5555, 0xFFAA00)),
			Map.entry("sunset", List.of(0x5F1B78, 0xFF55FF, 0xFF5555, 0xFFAA00, 0xFFFF55)),
			Map.entry("ocean", List.of(0x001B44, 0x0000AA, 0x00AAAA, 0x55FFFF, 0xFFFFFF)),
			Map.entry("ice", List.of(0xDFFCFF, 0x55FFFF, 0x55AAFF, 0x5555FF, 0xFFFFFF)),
			Map.entry("toxic", List.of(0x0C3B00, 0x00AA00, 0x55FF55, 0xFFFF55, 0xFFFFFF)),
			Map.entry("forest", List.of(0x123600, 0x00AA00, 0x55FF55, 0xFFAA00, 0xFFFFFF)),
			Map.entry("royal", List.of(0x000044, 0x0000AA, 0x5555FF, 0xAA00AA, 0xFF55FF)),
			Map.entry("void", List.of(0x050008, 0x2A0044, 0x5500AA, 0xAA00AA, 0x000000)));

	private TitleRenderer() {}

	public static MutableComponent render(Title title) {
		return title == null ? Component.empty() : render(title.runs());
	}

	public static MutableComponent render(TitleWord word) {
		return word == null ? Component.empty() : render(word.runs());
	}

	/** Animated effects depend on the current time, so call this every frame rather than caching it. */
	public static MutableComponent render(List<TitleRun> runs) {
		MutableComponent out = Component.empty();
		for (TitleRun run : runs) {
			if (run.text().isEmpty()) {
				continue;
			}
			if (run.effect() == null) {
				out.append(Component.literal(run.text()).withStyle(style(run, null)));
				continue;
			}
			int length = run.text().length();
			for (int i = 0; i < length; i++) {
				int color = effectColor(run.effect(), i, length);
				out.append(Component.literal(String.valueOf(run.text().charAt(i))).withStyle(style(run, color)));
			}
		}
		return out;
	}

	private static Style style(TitleRun run, Integer effectColor) {
		Style style = Style.EMPTY;
		if (effectColor != null) {
			style = style.withColor(effectColor);
		} else if (run.color() != null) {
			Integer hex = parseHex(run.color());
			ChatFormatting named = ChatFormatting.getByName(run.color());
			if (hex != null) {
				style = style.withColor(hex);
			} else if (named != null && named.isColor()) {
				style = style.withColor(named);
			}
		}
		return style.withBold(run.bold() ? true : null)
				.withItalic(run.italic() ? true : null)
				.withUnderlined(run.underlined() ? true : null)
				.withStrikethrough(run.strikethrough() ? true : null)
				.withObfuscated(run.obfuscated() ? true : null);
	}

	private static int effectColor(TitleEffect effect, int index, int length) {
		float position = length <= 1 ? 0f : (float) index / (length - 1);
		if (effect.animated()) {
			position = (position + (System.currentTimeMillis() % effect.cycleMs()) / (float) effect.cycleMs()) % 1f;
		}

		if ("rainbow".equals(effect.type()) && effect.colors().isEmpty() && effect.palette() == null) {
			return Color.HSBtoRGB(position, 0.8f, 0.9f) & 0xFFFFFF;
		}

		List<Integer> colors = effect.colors().isEmpty()
				? PALETTES.getOrDefault(effect.palette(), RAINBOW)
				: effect.colors().stream().map(TitleRenderer::colorValue).toList();
		return gradient(colors, position);
	}

	private static int gradient(List<Integer> colors, float position) {
		if (colors.size() == 1) {
			return colors.get(0);
		}
		float scaled = position * (colors.size() - 1);
		int left = Math.min((int) scaled, colors.size() - 2);
		float t = scaled - left;
		int a = colors.get(left);
		int b = colors.get(left + 1);
		return lerp(a >> 16 & 0xFF, b >> 16 & 0xFF, t) << 16 | lerp(a >> 8 & 0xFF, b >> 8 & 0xFF, t) << 8 | lerp(a & 0xFF, b & 0xFF, t);
	}

	private static int lerp(int a, int b, float t) {
		return Math.round(a + (b - a) * t);
	}

	private static int colorValue(String color) {
		Integer hex = parseHex(color);
		if (hex != null) {
			return hex;
		}
		ChatFormatting named = ChatFormatting.getByName(color);
		return named != null && named.getColor() != null ? named.getColor() : 0xFFFFFF;
	}

	private static Integer parseHex(String color) {
		if (color == null || color.length() != 7 || color.charAt(0) != '#') {
			return null;
		}
		try {
			return Integer.parseInt(color.substring(1), 16);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
