package wynntitles.mc;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Every call into Minecraft APIs that 26.x moved lives here, so a port only has
 * to touch this file (26.2 moved screens to Minecraft#gui and chat to Hud).
 */
public final class Mc {
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

	public static void toast(Toast toast) {
		run(() -> client().getToastManager().addToast(toast));
	}

	/** A chat line with the [WynnTitles] tag in front. */
	public static MutableComponent tagged(Component message) {
		return Component.empty()
				.append(Component.literal("[WynnTitles] ").withStyle(ChatFormatting.AQUA))
				.append(message);
	}
}
