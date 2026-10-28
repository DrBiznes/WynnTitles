package wynntitles;

import com.mojang.blaze3d.platform.InputConstants;
import com.wynntils.core.WynntilsMod;
import com.wynntils.core.mod.event.WynncraftConnectionEvent;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wynntitles.account.Account;
import wynntitles.achievements.AchievementTracker;
import wynntitles.achievements.WynntilsDetectors;
import wynntitles.api.ApiClient;
import wynntitles.api.ApiException;
import wynntitles.catalog.Catalog;
import wynntitles.catalog.CatalogStore;
import wynntitles.config.WynnTitlesConfig;
import wynntitles.mc.Commands;
import wynntitles.mc.Icons;
import wynntitles.mc.Mc;
import wynntitles.mc.MojangSession;
import wynntitles.mc.TitleRenderer;
import wynntitles.mc.UnlockToast;
import wynntitles.mc.screen.TitlesScreen;
import wynntitles.titles.Title;
import wynntitles.titles.TitleCache;

public class WynnTitles implements ClientModInitializer {
	public static final String MOD_ID = "wynntitles";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Account account;
	private static CatalogStore catalogs;
	private static TitleCache titles;

	private KeyMapping openKey;
	private static boolean loadedThisSession;

	public static Account account() {
		return account;
	}

	public static CatalogStore catalogs() {
		return catalogs;
	}

	public static TitleCache titles() {
		return titles;
	}

	@Override
	public void onInitializeClient() {
		WynnTitlesConfig.register();

		String version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
		Path dir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);

		ApiClient api = new ApiClient(WynnTitlesConfig::resolvedServerUrl, version);
		account = new Account(api, new MojangSession(), dir.resolve("session.json"));
		catalogs = new CatalogStore(api, dir.resolve("catalog.json"));
		titles = new TitleCache(api);
		AchievementTracker tracker = new AchievementTracker(
				api, account, catalogs, WynntilsDetectors.create(), WynnTitles::announceUnlock, version);

		// Show our own new title right away instead of waiting for the cache to expire.
		account.onChange(me -> titles.put(Mc.client().getUser().getProfileId(), me.title()));

		Commands.register();
		Icons.register();
		openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.wynntitles.open",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_GRAVE_ACCENT,
				KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"))));

		// Only contact the WynnTitles server on Wynncraft, not on every world or server.
		WynntilsMod.registerEventListener(this);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			titles.tick();
			tracker.tick();
			while (openKey.consumeClick()) {
				if (Mc.screen() == null) {
					Mc.openScreen(new TitlesScreen());
				}
			}
		});
	}

	@SubscribeEvent
	public void onWynncraftConnected(WynncraftConnectionEvent.Connected event) {
		onWynncraftJoin();
	}

	/** Loads the catalog and logs in, once per launch (retried on the next join if login fails). */
	public static void onWynncraftJoin() {
		if (loadedThisSession) {
			return;
		}
		loadedThisSession = true;
		catalogs.refresh().exceptionally(t -> {
			LOGGER.warn("Could not load catalog: {}", ApiException.describe(t));
			return null;
		});
		account.refresh().exceptionally(t -> {
			LOGGER.warn("Could not log in to WynnTitles: {}", ApiException.describe(t));
			loadedThisSession = false;
			return null;
		});
	}

	private static void announceUnlock(Catalog.Achievement achievement) {
		LOGGER.info("Unlocked {}", achievement.id());
		if (!WynnTitlesConfig.get().announceUnlocks) {
			return;
		}
		Title reward = Title.of(achievement.rewards().prefix(), achievement.rewards().suffix());
		var message = Component.literal("Achievement unlocked: ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(achievement.name()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
		if (reward != null) {
			message.append(Component.literal("\n New title words: ").withStyle(ChatFormatting.GRAY))
					.append(TitleRenderer.render(reward))
					.append(Component.literal(" (press ` or /wt to equip)").withStyle(ChatFormatting.DARK_GRAY));
		}
		Mc.chat(Mc.tagged(message));
		Mc.toast(new UnlockToast(achievement));
	}
}
