package wynntitles.mc;

import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import wynntitles.WynnTitles;
import wynntitles.catalog.Catalog;
import wynntitles.icons.ItemModelIndex;

/**
 * Turns catalog icons into ItemStacks. On every resource reload (joining
 * Wynncraft loads its server pack) the loaded item definitions are indexed, so
 * an icon naming a Wynncraft model renders whatever the active pack shows for
 * it. Nothing from the pack is copied or shipped.
 */
public final class Icons {
	private static ItemModelIndex index = new ItemModelIndex();
	private static final Map<Catalog.Icon, ItemStack> stacks = new HashMap<>();

	private Icons() {}

	public static void register() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(
				Identifier.fromNamespaceAndPath(WynnTitles.MOD_ID, "icons"),
				(ResourceManagerReloadListener) Icons::reload);
	}

	/** The stack to draw for an icon. Call on the client thread. */
	public static ItemStack stack(Catalog.Icon icon) {
		if (icon == null) {
			return new ItemStack(Items.PAPER);
		}
		return stacks.computeIfAbsent(icon, Icons::resolve);
	}

	/** Whether the icon's model was found in the loaded packs, not replaced by its fallback. */
	public static boolean resolved(Catalog.Icon icon) {
		return icon != null && icon.model() != null && fromModel(icon.model()).isPresent();
	}

	private static void reload(ResourceManager manager) {
		ItemModelIndex next = new ItemModelIndex();
		manager.listResources("items", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
			String path = id.getPath();
			String item = id.getNamespace() + ":" + path.substring("items/".length(), path.length() - ".json".length());
			try (Reader reader = resource.openAsReader()) {
				next.add(item, JsonParser.parseReader(reader).getAsJsonObject());
			} catch (Exception e) {
				WynnTitles.LOGGER.debug("Skipping item definition {}: {}", id, e.toString());
			}
		});
		index = next;
		stacks.clear();
		WynnTitles.LOGGER.debug("Indexed {} item models for achievement icons", next.size());
	}

	private static ItemStack resolve(Catalog.Icon icon) {
		if (icon.model() != null) {
			Optional<ItemStack> stack = fromModel(icon.model());
			if (stack.isPresent()) {
				return stack.get();
			}
		}
		return item(icon.fallback()).map(ItemStack::new).orElseGet(() -> new ItemStack(Items.PAPER));
	}

	private static Optional<ItemStack> fromModel(String model) {
		return index.find("minecraft:item/" + model).flatMap(selector -> item(selector.item()).flatMap(item -> {
			ItemStack stack = new ItemStack(item);
			if (!selector.damage().isEmpty()) {
				int damage = selector.damageFor(stack.getMaxDamage());
				if (damage < 0) {
					return Optional.empty();
				}
				stack.setDamageValue(damage);
			}
			if (selector.hasCustomModelData()) {
				stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
						selector.floatList(), selector.flagList(), selector.stringList(), List.of()));
			}
			return Optional.of(stack);
		}));
	}

	private static Optional<Item> item(String id) {
		Identifier parsed = id == null ? null : Identifier.tryParse(id);
		if (parsed == null) {
			return Optional.empty();
		}
		return BuiltInRegistries.ITEM.getOptional(parsed).filter(item -> item != Items.AIR);
	}
}
