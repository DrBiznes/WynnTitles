package wynntitles.mc;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import wynntitles.WynnTitles;
import wynntitles.titles.TitleFonts;

/**
 * The fonts of the Wynncraft resource pack, used by id the way Wynncraft's own
 * menus use them. Nothing is copied: off Wynncraft the pack is not loaded, so
 * text falls back to the vanilla font. A missing font would draw as boxes,
 * so every reload checks which ones the loaded packs define.
 */
public final class WynnFonts {
	/** The pack's regular text font. */
	public static final Identifier BODY = Identifier.withDefaultNamespace("language/wynncraft");
	/** Small capitals, as on Wynncraft's menu headers and labels. */
	public static final Identifier SMALL = Identifier.withDefaultNamespace("language/five");
	/** The badges in front of Wynncraft's chat lines: letters cut out of a white pill. */
	public static final Identifier PILL = Identifier.withDefaultNamespace("banner/pill");
	/** Negative advances, which the pill font pulls in to butt its glyphs together. */
	private static final Identifier SPACE = Identifier.withDefaultNamespace("space");

	// Pill glyphs, as Wynncraft's own chat lines spell them.
	private static final String PILL_START = "\uE060";
	private static final String PILL_END = "\uE062";
	private static final char PILL_GAP = '\uE061';
	private static final String BACK_ONE = "\uDAFF\uDFFF";

	private static volatile Set<Identifier> available = Set.of();

	private WynnFonts() {}

	public static void register() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(
				Identifier.fromNamespaceAndPath(WynnTitles.MOD_ID, "fonts"),
				(ResourceManagerReloadListener) WynnFonts::reload);
	}

	public static MutableComponent body(String text) {
		return in(BODY, text);
	}

	/** Upper-cased either way, so labels look alike with and without the pack. */
	public static MutableComponent small(String text) {
		return in(SMALL, text.toUpperCase(Locale.ROOT));
	}

	/** Whether the Wynncraft pack's fonts are loaded. */
	public static boolean loaded() {
		return available.contains(BODY);
	}

	/** Whether the pack's chat pills can be drawn; see {@link #pill}. */
	public static boolean pillsLoaded() {
		return available.containsAll(Set.of(PILL, SPACE));
	}

	/**
	 * Text in one of Wynncraft's chat pills, tinted by the text colour. Each
	 * glyph is followed by a one-pixel step back so the pill reads as one
	 * shape. Only letters, digits and spaces have glyphs. Check
	 * {@link #pillsLoaded()} first: without the pack this is private-use boxes.
	 */
	public static MutableComponent pill(String text) {
		return bannerBackground(PILL, text);
	}

	private static MutableComponent bannerBackground(Identifier font, String text) {
		StringBuilder back = new StringBuilder(PILL_START).append(BACK_ONE);
		text.codePoints().forEach(c -> {
			int glyph = TitleFonts.glyph(c);
			back.appendCodePoint(glyph < 0 || glyph == 0xE061 ? PILL_GAP : glyph + 0x30).append(BACK_ONE);
		});
		return in(font, back.append(PILL_END).toString());
	}

	public static boolean available(String id) {
		if (!TitleFonts.known(id)) return false;
		return available.contains(Identifier.parse(id)) && (!TitleFonts.banner(id) || available.contains(SPACE));
	}

	public static MutableComponent title(String id, String text) {
		MutableComponent out = available(id) ? in(Identifier.parse(id), TitleFonts.text(id, text)) : Component.literal(text);
		return "minecraft:language/wynncraft_outline".equals(id) ? out.withStyle(style -> style.withoutShadow()) : out;
	}

	/** Two complete layers with an exact rewind, so Font.width includes both
	 * edges once. The foreground shares the background's glyph advances. */
	public static MutableComponent banner(String id, String text, int background, Integer letters) {
		if (!available(id) || TitleFonts.bannerCharacter(text) != 0) return Component.literal(text).withColor(background);
		Identifier font = Identifier.parse(id);
		// Each foreground letter advances as far as its background slice,
		// whose extra pixel closes the seam with the next slice.
		MutableComponent bg = bannerBackground(font, text).withColor(background);
		int width = Mc.client().font.width(bg);
		MutableComponent fg = in(font, "\uE060" + BACK_ONE).withColor(background);
		text.codePoints().forEach(c -> {
			int glyph = TitleFonts.glyph(c);
			MutableComponent letter = in(font, new String(Character.toChars(glyph)))
					.withColor(glyph == 0xE061 ? background : letters == null ? contrast(background) : letters);
			int slice = Mc.client().font.width(in(font,
					new String(Character.toChars(glyph == 0xE061 ? glyph : glyph + 0x30)) + BACK_ONE));
			fg.append(letter).append(advance(slice - Mc.client().font.width(letter)));
		});
		fg.append(in(font, "\uE062").withColor(background));
		return Component.empty().append(bg).append(advance(-width)).append(fg)
				.withStyle(style -> style.withoutShadow().withBold(false).withItalic(false)
						.withUnderlined(false).withStrikethrough(false).withObfuscated(false));
	}

	private static MutableComponent advance(int pixels) {
		return in(SPACE, new String(Character.toChars(0xD0000 + pixels)));
	}

	public static int contrast(int color) {
		double light = 0.2126 * channel((color >> 16) & 255) + 0.7152 * channel((color >> 8) & 255) + 0.0722 * channel(color & 255);
		return light > 0.179 ? 0x000000 : 0xFFFFFF;
	}
	private static double channel(int n) {
		double s = n / 255.0;
		return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
	}

	private static MutableComponent in(Identifier font, String text) {
		MutableComponent component = Component.literal(text);
		return available.contains(font)
				? component.withStyle(style -> style.withFont(new FontDescription.Resource(font)))
				: component;
	}

	private static void reload(ResourceManager manager) {
		Set<Identifier> next = new HashSet<>();
		Set<Identifier> fonts = new HashSet<>(Set.of(BODY, SMALL, PILL, SPACE));
		TitleFonts.IDS.forEach(id -> fonts.add(Identifier.parse(id)));
		for (Identifier font : fonts) {
			if (manager.getResource(font.withPath(path -> "font/" + path + ".json")).isPresent()) {
				next.add(font);
			}
		}
		available = Set.copyOf(next);
		WynnTitles.LOGGER.debug("Wynncraft fonts available: {}", next);
	}
}
