package wynntitles.icons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class ItemModelIndexTest {
	private static final int DIAMOND_SWORD = 1561;

	@Test
	void damageDispatchPicksADamageInsideTheEntry() {
		ItemModelIndex index = new ItemModelIndex();
		index.add("minecraft:diamond_sword", json("""
				{"model": {"type": "minecraft:range_dispatch", "property": "minecraft:damage",
				  "fallback": {"type": "minecraft:model", "model": "minecraft:item/diamond_sword"},
				  "entries": [
				    {"threshold": 0.0390775, "model": {"type": "minecraft:model", "model": "minecraft:item/wynn/prop/quest_scroll"}},
				    {"threshold": 0.0006406, "model": {"type": "minecraft:model", "model": "minecraft:item/wynn/prop/hay_bale"}},
				    {"threshold": 0.0397181, "model": {"type": "minecraft:model", "model": "minecraft:item/wynn/prop/next"}}
				  ]}}
				"""));

		ItemModelIndex.Selector scroll = index.find("minecraft:item/wynn/prop/quest_scroll").orElseThrow();
		assertEquals("minecraft:diamond_sword", scroll.item());
		int damage = scroll.damageFor(DIAMOND_SWORD);
		assertEquals(61, damage);
		// 61 / 1561 is past this entry's threshold and before the next one.
		float value = (float) damage / DIAMOND_SWORD;
		assertTrue(value >= 0.0390775f && value < 0.0397181f);

		assertEquals(1, index.find("item/wynn/prop/hay_bale").orElseThrow().damageFor(DIAMOND_SWORD));
		// The fallback shows at damage 0, with no constraint needed.
		assertEquals(0, index.find("minecraft:item/diamond_sword").orElseThrow().damageFor(DIAMOND_SWORD));
	}

	@Test
	void entryTooNarrowForAnyDamageHasNoValue() {
		ItemModelIndex index = new ItemModelIndex();
		index.add("minecraft:stone_shovel", json("""
				{"model": {"type": "range_dispatch", "property": "damage", "entries": [
				  {"threshold": 0.5, "model": {"type": "model", "model": "item/a"}},
				  {"threshold": 0.5001, "model": {"type": "model", "model": "item/b"}}
				]}}
				"""));
		assertEquals(-1, index.find("item/a").orElseThrow().damageFor(131));
		assertTrue(index.find("item/b").orElseThrow().damageFor(131) > 0);
	}

	@Test
	void customModelDataSetsFloatsStringsAndFlags() {
		ItemModelIndex index = new ItemModelIndex();
		index.add("minecraft:paper", json("""
				{"model": {"type": "minecraft:select", "property": "minecraft:display_context",
				  "cases": [{"when": ["gui", "fixed"], "model":
				    {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 1,
				     "entries": [
				       {"threshold": 7, "model": {"type": "minecraft:model", "model": "minecraft:item/wynn/seven"}},
				       {"threshold": 8, "model": {"type": "minecraft:condition", "property": "minecraft:custom_model_data",
				         "on_true": {"type": "minecraft:model", "model": "minecraft:item/wynn/eight_flagged"},
				         "on_false": {"type": "minecraft:model", "model": "minecraft:item/wynn/eight"}}}
				     ]}}],
				  "fallback": {"type": "minecraft:select", "property": "minecraft:custom_model_data",
				    "cases": [{"when": "pouch", "model": {"type": "minecraft:model", "model": "minecraft:item/wynn/pouch"}}],
				    "fallback": {"type": "minecraft:model", "model": "minecraft:item/paper"}}}}
				"""));

		ItemModelIndex.Selector seven = index.find("minecraft:item/wynn/seven").orElseThrow();
		assertEquals(List.of(0f, 7f), seven.floatList());
		assertTrue(seven.hasCustomModelData());

		ItemModelIndex.Selector flagged = index.find("minecraft:item/wynn/eight_flagged").orElseThrow();
		assertEquals(List.of(0f, 8f), flagged.floatList());
		assertEquals(List.of(true), flagged.flagList());
		assertEquals(List.of(false), index.find("minecraft:item/wynn/eight").orElseThrow().flagList());

		assertEquals(List.of("pouch"), index.find("minecraft:item/wynn/pouch").orElseThrow().stringList());
		assertFalse(index.find("minecraft:item/paper").orElseThrow().hasCustomModelData());
	}

	@Test
	void scaledFloatDispatchStillLandsInItsEntry() {
		ItemModelIndex index = new ItemModelIndex();
		index.add("minecraft:stick", json("""
				{"model": {"type": "range_dispatch", "property": "custom_model_data", "scale": 0.1,
				  "entries": [
				    {"threshold": 0.3, "model": {"type": "model", "model": "item/three"}},
				    {"threshold": 0.4, "model": {"type": "model", "model": "item/four"}}
				  ]}}
				"""));
		float value = index.find("item/three").orElseThrow().floatList().get(0);
		assertTrue(value * 0.1f >= 0.3f && value * 0.1f < 0.4f, "got " + value);
	}

	@Test
	void unknownModelIsMissing() {
		ItemModelIndex index = new ItemModelIndex();
		index.add("minecraft:stick", json("{\"model\": {\"type\": \"minecraft:model\", \"model\": \"minecraft:item/stick\"}}"));
		assertTrue(index.find("minecraft:item/wynn/nope").isEmpty());
		assertEquals(1, index.size());
	}

	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}
}
