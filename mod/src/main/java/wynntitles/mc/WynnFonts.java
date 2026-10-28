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

	private static MutableComponent in(Identifier font, String text) {
		MutableComponent component = Component.literal(text);
		return available.contains(font)
				? component.withStyle(style -> style.withFont(new FontDescription.Resource(font)))
				: component;
	}

	private static void reload(ResourceManager manager) {
		Set<Identifier> next = new HashSet<>();
		for (Identifier font : Set.of(BODY, SMALL)) {
			if (manager.getResource(font.withPath(path -> "font/" + path + ".json")).isPresent()) {
				next.add(font);
			}
		}
		available = Set.copyOf(next);
		WynnTitles.LOGGER.debug("Wynncraft fonts available: {}", next);
	}
}
