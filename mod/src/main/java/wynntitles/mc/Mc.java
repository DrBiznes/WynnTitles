package wynntitles.mc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Every call into Minecraft APIs that 26.x moved lives here, so a port only has
 * to touch this file (26.2 moved screens to Minecraft#gui and chat to Hud).
 */
public final class Mc {
	private static final String BADGE = "TITLES";
	/** The gold of the screens' labels (WynnUi.GOLD). */
	private static final int BADGE_COLOR = 0xF7C948;

	private Mc() {}

	public static Minecraft client() {
		return Minecraft.getInstance();
	}

	/** Runs on the client thread; safe to call from HTTP callbacks. */
	public static void run(Runnable task) {
		client().execute(task);
	}

	public static Screen screen() {
		return client().screen;
	}

	public static void openScreen(Screen screen) {
		run(() -> client().setScreen(screen));
	}

	public static void chat(Component message) {
		run(() -> {
			if (client().player != null) {
				client().gui.getChat().addMessage(message);
			}
		});
	}

	/** Every stack the player carries, worn armour and the offhand included. */
	public static List<ItemStack> inventory() {
		if (client().player == null) {
			return List.of();
		}
		Inventory inventory = client().player.getInventory();
		List<ItemStack> items = new ArrayList<>(inventory.getContainerSize());
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			items.add(inventory.getItem(slot));
		}
		return items;
	}

	public static void toast(Toast toast) {
		run(() -> client().getToastManager().addToast(toast));
	}

	/** Shows tooltip when the chat text is hovered (HoverEvent became records in 1.21.5). */
	public static MutableComponent withHover(MutableComponent text, Component tooltip) {
		return text.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(tooltip)));
	}

	/**
	 * A chat line with the mod's badge in front: a gold TITLES pill like the
	 * ones Wynncraft puts on its own lines when the pack is loaded, and a
	 * star and the word in bold otherwise. Every chat line the mod writes goes
	 * through here.
	 */
	public static MutableComponent tagged(Component message) {
		MutableComponent badge = WynnFonts.pillsLoaded()
				? WynnFonts.pill(BADGE)
				: Component.literal("\u2726 " + BADGE).withStyle(ChatFormatting.BOLD);
		return Component.empty()
				.append(badge.withColor(BADGE_COLOR))
				.append(" ")
				.append(message);
	}
}
