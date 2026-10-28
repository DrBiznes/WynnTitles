package wynntitles.icons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Indexes item model definitions (assets/{@code <ns>}/items/*.json) by the models
 * they can show, so an icon can name a model and we work out which item and
 * component values make the game draw it.
 *
 * <p>Resource packs pick models with damage and custom model data, which we can
 * set on an ItemStack. Other properties (display context, selected, ...) cannot
 * be set, so every branch under them is indexed with the same selector: the
 * stack then renders the way that item looks in an inventory.
 *
 * <p>Plain JSON with no Minecraft types, so it is unit tested and survives ports.
 */
public final class ItemModelIndex {
	private final Map<String, Selector> byModel = new HashMap<>();

	/** Adds one item definition, for example {@code minecraft:diamond_sword}. */
	public void add(String itemId, JsonObject definition) {
		walk(definition.get("model"), new Selector(itemId, List.of(), Map.of(), Map.of(), Map.of()));
	}

	/** The first selector found for a model id such as {@code minecraft:item/wynn/prop/quest_scroll}. */
	public Optional<Selector> find(String modelId) {
		return Optional.ofNullable(byModel.get(normalize(modelId)));
	}

	public int size() {
		return byModel.size();
	}

	private void walk(JsonElement element, Selector selector) {
		if (element == null || !element.isJsonObject()) {
			return;
		}
		JsonObject node = element.getAsJsonObject();
		String property = unprefixed(string(node, "property"));
		int index = integer(node, "index", 0);

		switch (unprefixed(string(node, "type"))) {
			case "model" -> {
				String model = string(node, "model");
				if (model != null) {
					byModel.putIfAbsent(normalize(model), selector);
				}
			}
			case "range_dispatch" -> {
				float scale = number(node, "scale", 1f);
				boolean normalized = bool(node, "normalize", true);
				List<JsonObject> entries = objects(node.get("entries"));
				entries.sort(Comparator.comparingDouble(e -> number(e, "threshold", 0f)));
				for (int i = 0; i < entries.size(); i++) {
					float threshold = number(entries.get(i), "threshold", 0f);
					Float next = i + 1 < entries.size() ? number(entries.get(i + 1), "threshold", 0f) : null;
					Range range = new Range(threshold, next, scale);
					JsonElement model = entries.get(i).get("model");
					switch (property) {
						case "damage" -> walk(model, selector.withDamage(range, normalized));
						case "custom_model_data" -> range.pick().ifPresent(v -> walk(model, selector.withFloat(index, v)));
						default -> walk(model, selector);
					}
				}
				walk(node.get("fallback"), selector);
			}
			case "select" -> {
				for (JsonObject entry : objects(node.get("cases"))) {
					JsonElement model = entry.get("model");
					if (property.equals("custom_model_data")) {
						forEachString(entry.get("when"), when -> walk(model, selector.withString(index, when)));
					} else {
						walk(model, selector);
					}
				}
				walk(node.get("fallback"), selector);
			}
			case "condition" -> {
				boolean flag = property.equals("custom_model_data");
				walk(node.get("on_true"), flag ? selector.withFlag(index, true) : selector);
				walk(node.get("on_false"), flag ? selector.withFlag(index, false) : selector);
			}
			case "composite" -> objects(node.get("models")).forEach(model -> walk(model, selector));
			default -> {
				// special, empty, bundle/selected_item: nothing we can show.
			}
		}
	}

	/**
	 * Which item to create and what to set on it. Damage ranges are in the
	 * dispatch's own units; {@link #damageFor} turns them into a damage value.
	 */
	public record Selector(
			String item,
			List<DamageRange> damage,
			Map<Integer, Float> floats,
			Map<Integer, Boolean> flags,
			Map<Integer, String> strings) {

		public boolean hasCustomModelData() {
			return !floats.isEmpty() || !flags.isEmpty() || !strings.isEmpty();
		}

		/** A damage value that falls in every range, or -1 if there is none. */
		public int damageFor(int maxDamage) {
			if (damage.isEmpty()) {
				return 0;
			}
			if (maxDamage <= 0) {
				return -1;
			}
			// Start just below the first range's threshold and walk up, using the
			// game's float math so rounding never lands us in a neighbouring entry.
			DamageRange first = damage.get(0);
			float start = first.normalized() ? first.range().threshold() / first.range().scale() * maxDamage
					: first.range().threshold() / first.range().scale();
			for (int d = Math.max(0, (int) start - 2); d <= Math.min(maxDamage, (int) start + 4); d++) {
				int value = d;
				if (damage.stream().allMatch(r -> r.contains(value, maxDamage))) {
					return d;
				}
			}
			return -1;
		}

		/** Custom model data floats with gaps filled by 0. */
		public List<Float> floatList() {
			return fill(floats, 0f);
		}

		public List<Boolean> flagList() {
			return fill(flags, false);
		}

		public List<String> stringList() {
			return fill(strings, "");
		}

		Selector withDamage(Range range, boolean normalized) {
			List<DamageRange> next = new ArrayList<>(damage);
			next.add(new DamageRange(range, normalized));
			return new Selector(item, List.copyOf(next), floats, flags, strings);
		}

		Selector withFloat(int index, float value) {
			return new Selector(item, damage, with(floats, index, value), flags, strings);
		}

		Selector withFlag(int index, boolean value) {
			return new Selector(item, damage, floats, with(flags, index, value), strings);
		}

		Selector withString(int index, String value) {
			return new Selector(item, damage, floats, flags, with(strings, index, value));
		}

		private static <T> Map<Integer, T> with(Map<Integer, T> map, int index, T value) {
			if (map.containsKey(index)) {
				// An outer dispatch already fixed this index; an inner one cannot change it.
				return map;
			}
			Map<Integer, T> next = new TreeMap<>(map);
			next.put(index, value);
			return Map.copyOf(next);
		}

		private static <T> List<T> fill(Map<Integer, T> map, T gap) {
			int size = map.keySet().stream().mapToInt(i -> i + 1).max().orElse(0);
			List<T> list = new ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				list.add(map.getOrDefault(i, gap));
			}
			return List.copyOf(list);
		}
	}

	public record DamageRange(Range range, boolean normalized) {
		boolean contains(int damage, int maxDamage) {
			// Mirrors the vanilla damage property: damage / maxDamage, clamped to 0..1.
			float value = normalized ? Math.clamp((float) damage / (float) maxDamage, 0f, 1f) : damage;
			return range.contains(value);
		}
	}

	/** One range_dispatch entry: the property times scale is in [threshold, next). */
	public record Range(float threshold, Float next, float scale) {
		boolean contains(float value) {
			float scaled = value * scale;
			return scaled >= threshold && (next == null || scaled < next);
		}

		/** A property value inside this range, if the float math allows one. */
		Optional<Float> pick() {
			if (scale == 0f) {
				return Optional.empty();
			}
			float value = threshold / scale;
			for (int i = 0; i < 4 && !contains(value); i++) {
				value = Math.nextUp(value);
			}
			return contains(value) ? Optional.of(value) : Optional.empty();
		}
	}

	/** {@code item/wynn/x} and {@code minecraft:item/wynn/x} name the same model. */
	static String normalize(String id) {
		return id.contains(":") ? id : "minecraft:" + id;
	}

	private static String unprefixed(String id) {
		if (id == null) {
			return "";
		}
		return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
	}

	private static List<JsonObject> objects(JsonElement element) {
		List<JsonObject> out = new ArrayList<>();
		if (element instanceof JsonArray array) {
			for (JsonElement e : array) {
				if (e.isJsonObject()) {
					out.add(e.getAsJsonObject());
				}
			}
		}
		return out;
	}

	private static void forEachString(JsonElement element, Consumer<String> action) {
		if (element instanceof JsonArray array) {
			array.forEach(e -> forEachString(e, action));
		} else if (element != null && element.isJsonPrimitive()) {
			action.accept(element.getAsString());
		}
	}

	private static String string(JsonObject node, String key) {
		JsonElement e = node.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	private static float number(JsonObject node, String key, float otherwise) {
		JsonElement e = node.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsFloat() : otherwise;
	}

	private static int integer(JsonObject node, String key, int otherwise) {
		JsonElement e = node.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : otherwise;
	}

	private static boolean bool(JsonObject node, String key, boolean otherwise) {
		JsonElement e = node.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : otherwise;
	}
}
