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
		StringBuilder pill = new StringBuilder(PILL_START).append(BACK_ONE);
		for (char c : text.toUpperCase(Locale.ROOT).toCharArray()) {
			pill.append(c >= 'A' && c <= 'Z' ? (char) ('\uE030' + c - 'A')
					: c >= '0' && c <= '9' ? (char) ('\uE050' + c - '0')
					: PILL_GAP).append(BACK_ONE);
		}
		return in(PILL, pill.append(PILL_END).toString());
	}

	private static MutableComponent in(Identifier font, String text) {
		MutableComponent component = Component.literal(text);
		return available.contains(font)
				? component.withStyle(style -> style.withFont(new FontDescription.Resource(font)))
				: component;
	}

	private static void reload(ResourceManager manager) {
		Set<Identifier> next = new HashSet<>();
		for (Identifier font : Set.of(BODY, SMALL, PILL, SPACE)) {
			if (manager.getResource(font.withPath(path -> "font/" + path + ".json")).isPresent()) {
				next.add(font);
			}
		}
		available = Set.copyOf(next);
		WynnTitles.LOGGER.debug("Wynncraft fonts available: {}", next);
	}
}
