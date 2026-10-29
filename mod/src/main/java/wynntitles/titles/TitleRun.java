package wynntitles.titles;

/** A styled span of title text. Mirrors catalog.Run on the server. */
public record TitleRun(
		String text,
		String color,
		boolean bold,
		boolean italic,
		boolean underlined,
		boolean strikethrough,
		boolean obfuscated,
		TitleEffect effect,
		String font,
		String letterColor) {
	public TitleRun(String text, String color, boolean bold, boolean italic, boolean underlined,
			boolean strikethrough, boolean obfuscated, TitleEffect effect) {
		this(text, color, bold, italic, underlined, strikethrough, obfuscated, effect, null, null);
	}

	public TitleRun {
		text = text == null ? "" : text;
	}

	public static TitleRun plain(String text) {
		return new TitleRun(text, null, false, false, false, false, false, null);
	}

	public boolean animated() {
		return effect != null && effect.animated();
	}
}
