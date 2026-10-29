package wynntitles.mc;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import wynntitles.titles.EffectColors;
import wynntitles.titles.Title;
import wynntitles.titles.TitleFonts;
import wynntitles.titles.TitleRun;
import wynntitles.titles.TitleWord;

/**
 * Turns title runs into chat Components. Effects are colored letter by letter
 * by {@link EffectColors}, so nametags, chat and every screen animate alike.
 */
public final class TitleRenderer {
	private TitleRenderer() {}

	public static MutableComponent render(Title title) {
		return title == null ? Component.empty() : render(title.runs());
	}

	public static MutableComponent render(TitleWord word) {
		return word == null ? Component.empty() : render(word.runs());
	}

	/** Animated effects depend on the current time, so call this every frame rather than caching it. */
	public static MutableComponent render(List<TitleRun> runs) {
		return render(runs, System.currentTimeMillis());
	}

	/** The runs as they look at {@code nowMs}, in wall-clock milliseconds. */
	public static MutableComponent render(List<TitleRun> runs, long nowMs) {
		MutableComponent out = Component.empty();
		for (TitleRun run : runs) {
			if (run.text().isEmpty()) {
				continue;
			}
			if (TitleFonts.banner(run.font())) {
				out.append(WynnFonts.banner(run.font(), run.text(), EffectColors.colorValue(run.color() == null ? "white" : run.color()),
						run.letterColor() == null ? null : EffectColors.colorValue(run.letterColor())));
				continue;
			}
			if (run.font() != null && !WynnFonts.available(run.font())) {
				int color = run.color() != null ? EffectColors.colorValue(run.color())
						: run.effect() == null ? 0xFFFFFF : EffectColors.firstColor(run.effect());
				out.append(Component.literal(run.text()).withColor(color));
				continue;
			}
			if (run.effect() == null) {
				out.append(WynnFonts.title(run.font(), run.text()).withStyle(style(run, null)));
				continue;
			}
			int length = run.text().length();
			int[] colors = EffectColors.colors(run.effect(), length, nowMs);
			for (int i = 0; i < length; i++) {
				int color = colors[i];
				out.append(WynnFonts.title(run.font(), String.valueOf(run.text().charAt(i))).withStyle(style(run, color)));
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
