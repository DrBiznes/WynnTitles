package wynntitles.icons;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Checks every catalog icon against a real Wynncraft resource pack. The pack is
 * not ours to ship, so this only runs when pointed at a cached copy:
 *
 * <pre>./gradlew test -Dwynntitles.wynnPack="&lt;profile&gt;/downloads/&lt;id&gt;/&lt;hash&gt;"</pre>
 */
class WynncraftPackTest {
	// Durability of the vanilla items Wynncraft uses as model carriers. Add one
	// here if the test names an item it does not know.
	private static final Map<String, Integer> MAX_DAMAGE = Map.ofEntries(
			Map.entry("minecraft:wooden_shovel", 59), Map.entry("minecraft:stone_shovel", 131),
			Map.entry("minecraft:stone_axe", 131), Map.entry("minecraft:iron_shovel", 250),
			Map.entry("minecraft:golden_shovel", 32), Map.entry("minecraft:golden_pickaxe", 32),
			Map.entry("minecraft:golden_axe", 32), Map.entry("minecraft:diamond_sword", 1561),
			Map.entry("minecraft:diamond_axe", 1561), Map.entry("minecraft:diamond_shovel", 1561),
			Map.entry("minecraft:diamond_pickaxe", 1561), Map.entry("minecraft:diamond_hoe", 1561),
			Map.entry("minecraft:bow", 384), Map.entry("minecraft:shears", 238),
			Map.entry("minecraft:flint_and_steel", 64), Map.entry("minecraft:leather_helmet", 55),
			Map.entry("minecraft:leather_chestplate", 80), Map.entry("minecraft:leather_leggings", 75),
			Map.entry("minecraft:leather_boots", 65));

	@Test
	void everyCatalogIconResolves() throws Exception {
		String pack = System.getProperty("wynntitles.wynnPack");
		Assumptions.assumeTrue(pack != null && !pack.isBlank(), "set -Dwynntitles.wynnPack to run");

		ItemModelIndex index = new ItemModelIndex();
		try (ZipFile zip = new ZipFile(pack)) {
			for (ZipEntry entry : zip.stream().toList()) {
				String name = entry.getName();
				if (name.startsWith("assets/minecraft/items/") && name.endsWith(".json")) {
					String item = "minecraft:" + name.substring("assets/minecraft/items/".length(), name.length() - 5);
					try (var reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
						index.add(item, JsonParser.parseReader(reader).getAsJsonObject());
					}
				}
			}
		}

		Path catalog = Path.of(System.getProperty("user.dir"), "..", "server", "catalog", "achievements.json");
		JsonArray achievements = JsonParser.parseString(Files.readString(catalog)).getAsJsonArray();
		List<String> problems = new ArrayList<>();
		for (JsonElement element : achievements) {
			JsonObject achievement = element.getAsJsonObject();
			JsonElement model = achievement.getAsJsonObject("icon").get("model");
			if (model == null) {
				continue;
			}
			String id = "minecraft:item/" + model.getAsString();
			var selector = index.find(id);
			if (selector.isEmpty()) {
				problems.add(achievement.get("id").getAsString() + ": no item shows " + id);
				continue;
			}
			if (!selector.get().damage().isEmpty()) {
				Integer max = MAX_DAMAGE.get(selector.get().item());
				if (max == null) {
					problems.add(achievement.get("id").getAsString() + ": add " + selector.get().item() + " to MAX_DAMAGE");
				} else if (selector.get().damageFor(max) < 0) {
					problems.add(achievement.get("id").getAsString() + ": no damage value selects " + id);
				}
			}
		}
		System.out.println("Indexed " + index.size() + " models from the Wynncraft pack");
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}
}
