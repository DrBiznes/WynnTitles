package wynntitles.mc;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import wynntitles.config.WynnTitlesConfig;
import wynntitles.titles.EffectColors;
import wynntitles.titles.Title;
import wynntitles.titles.TitleFonts;
import wynntitles.titles.TitleRun;
import wynntitles.titles.TitleWord;

/**
 * Turns title runs into chat Components. Effects are colored letter by letter
 * by {@link EffectColors}, so nametags, chat and every screen animate alike,
 * or all hold still when the player turns animation off.
 */
public final class TitleRenderer {
	/** The script decipher's runes are drawn in. */
	private static final String WYNNIC = "minecraft:language/wynnic";

	private TitleRenderer() {}

	public static MutableComponent render(Title title) {
		return title == null ? Component.empty() : render(title.runs());
	}

	public static MutableComponent render(TitleWord word) {
		return word == null ? Component.empty() : render(word.runs());
	}

	/** Animated effects depend on the current time, so call this every frame rather than caching it. */
	public static MutableComponent render(List<TitleRun> runs) {
		boolean animate = WynnTitlesConfig.get().animateTitles;
		return render(runs, animate ? System.currentTimeMillis() : 0, animate);
	}

	/** The runs as they look at {@code nowMs}, in wall-clock milliseconds; still ones are never obfuscated. */
	private static MutableComponent render(List<TitleRun> runs, long nowMs, boolean animate) {
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
				out.append(WynnFonts.title(run.font(), run.text()).withStyle(style(run, null, animate)));
				continue;
			}
			int length = run.text().length();
			// Held still, every letter is plain: no scrambling or bold beats.
			EffectColors.Letter[] letters = EffectColors.letters(animate ? run.effect() : run.effect().still(), length, nowMs);
			for (int i = 0; i < length; i++) {
				out.append(letter(run, run.text().charAt(i), letters[i], animate));
			}
		}
		return out;
	}

	/**
	 * One letter of an effect. A rune is the letter in the Wynncraft pack's
	 * Wynnic font; without the pack, or for anything but a letter, it scrambles instead.
	 */
	private static MutableComponent letter(TitleRun run, char c, EffectColors.Letter letter, boolean animate) {
		boolean rune = letter.glyph() == EffectColors.Glyph.RUNE && Character.isLetter(c) && WynnFonts.available(WYNNIC);
		boolean scrambled = letter.glyph() == EffectColors.Glyph.SCRAMBLED
				|| letter.glyph() == EffectColors.Glyph.RUNE && !rune;
		Style style = style(run, letter.color(), animate);
		if (letter.bold()) {
			style = style.withBold(true);
		}
		if (scrambled) {
			style = style.withObfuscated(true);
		}
		return WynnFonts.title(rune ? WYNNIC : run.font(), String.valueOf(c)).withStyle(style);
	}

	private static Style style(TitleRun run, Integer effectColor, boolean animate) {
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
				.withObfuscated(run.obfuscated() && animate ? true : null);
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
