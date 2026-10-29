package wynntitles.titles;

import java.util.Set;

/** Codepoints we use from the pack; no font assets or external fontmaps. */
public final class TitleFonts {
	public static final Set<String> IDS = Set.of(
			"minecraft:language/wynncraft", "minecraft:language/five", "minecraft:language/wynncraft_outline",
			"minecraft:language/wynnic", "minecraft:language/high_gavellian", "minecraft:language/old_fruman",
			"minecraft:banner/pill", "minecraft:banner/box", "minecraft:banner/flag", "minecraft:banner/ribbon");
	private TitleFonts() {}
	public static boolean known(String id) { return id != null && IDS.contains(id); }
	public static boolean banner(String id) { return known(id) && id.startsWith("minecraft:banner/"); }
	public static int bannerCharacter(String text) {
		return text.codePoints().filter(c -> glyph(c) < 0).findFirst().orElse(0);
	}
	public static int glyph(int c) {
		if (c >= 'a' && c <= 'z') c -= 32;
		if (c >= 'A' && c <= 'Z') return 0xE000 + c - 'A';
		if (c >= '0' && c <= '9') return 0xE020 + c - '0';
		return c == ' ' ? 0xE061 : -1;
	}
	public static String text(String id, String text) {
		// Old Fruman has ASCII alphabet glyphs in the cached pack. U+E000–E004
		// are extra symbols, not the alphabet; ASCII needs no remapping.
		return "minecraft:language/five".equals(id) ? text.toUpperCase(java.util.Locale.ROOT) : text;
	}
}
