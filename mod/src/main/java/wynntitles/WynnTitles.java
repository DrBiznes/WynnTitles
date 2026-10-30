package wynntitles;

import com.mojang.blaze3d.platform.InputConstants;
import com.wynntils.core.components.Managers;
import com.wynntils.core.WynntilsMod;
import com.wynntils.core.mod.event.WynncraftConnectionEvent;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wynntitles.account.Account;
import wynntitles.achievements.AchievementTracker;
import wynntitles.achievements.UnlockBatcher;
import wynntitles.achievements.WynntilsDetectors;
import wynntitles.api.ApiClient;
import wynntitles.api.ApiException;
import wynntitles.catalog.Catalog;
import wynntitles.catalog.CatalogStore;
import wynntitles.catalog.StatsStore;
import wynntitles.config.WynnTitlesConfig;
import wynntitles.mc.Commands;
import wynntitles.mc.Icons;
import wynntitles.mc.Mc;
import wynntitles.mc.MojangSession;
import wynntitles.mc.TitleRenderer;
import wynntitles.mc.UnlockToast;
import wynntitles.mc.WynnFonts;
import wynntitles.mc.screen.UnlockToastArt;
import wynntitles.mc.screen.TitlesScreen;
import wynntitles.titles.Title;
import wynntitles.titles.TitleCache;

public class WynnTitles implements ClientModInitializer {
	public static final String MOD_ID = "wynntitles";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Account account;
	private static CatalogStore catalogs;
	private static StatsStore stats;
	private static TitleCache titles;
	private static AchievementTracker tracker;
	private static UnlockBatcher unlocks;

	private static KeyMapping openKey;
	private static boolean loadedThisSession;

	public static Account account() {
		return account;
	}

	public static CatalogStore catalogs() {
		return catalogs;
	}

	public static StatsStore stats() {
		return stats;
	}

	public static TitleCache titles() {
		return titles;
	}

	public static AchievementTracker tracker() {
		return tracker;
	}

	@Override
	public void onInitializeClient() {
		WynnTitlesConfig.register(WynnTitles::onServerChanged);

		String version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
		Path dir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);

		ApiClient api = new ApiClient(WynnTitlesConfig::resolvedServerUrl, version);
		account = new Account(api, new MojangSession(), dir.resolve("session.json"));
		catalogs = new CatalogStore(api, dir.resolve("catalog.json"));
		stats = new StatsStore(api);
		titles = new TitleCache(api);
		unlocks = new UnlockBatcher(WynnTitles::announceUnlocks);
		tracker = new AchievementTracker(api, account, catalogs,
				WynntilsDetectors.create(dir.resolve("territories.json")), unlocks::add, version);

		// Show our own new title right away instead of waiting for the cache to expire.
		account.onChange(me -> titles.put(Mc.client().getUser().getProfileId(), me.title()));

		Commands.register();
		Icons.register();
		WynnFonts.register();
		openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.wynntitles.open",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_GRAVE_ACCENT,
				KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"))));

		// Only contact the WynnTitles server on Wynncraft, not on every world or server.
		// Wynntils creates its event bus in its own entrypoint, which may run after ours.
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			WynntilsMod.registerEventListener(this);
			WynntilsMod.registerEventListener(WynntilsDetectors.eventListener());
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			titles.tick();
			tracker.tick();
			unlocks.tick();
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

	/** Forgets everything from the old server and, when on Wynncraft, loads from the new one. */
	private static void onServerChanged() {
		LOGGER.info("Server changed to {}", WynnTitlesConfig.resolvedServerUrl());
		titles.clear();
		account.forgetSession();
		loadedThisSession = false;
		if (Managers.Connection.onServer()) {
			onWynncraftJoin();
		}
	}

	/** Up to this many unlocks at once get a toast and chat line each; more become one summary. */
	private static final int ANNOUNCE_EACH_UP_TO = 2;

	private static void announceUnlocks(List<Catalog.Achievement> unlocked) {
		unlocked.forEach(a -> LOGGER.info("Unlocked {}", a.id()));
		WynnTitlesConfig config = WynnTitlesConfig.get();
		if (unlocked.size() <= ANNOUNCE_EACH_UP_TO) {
			for (Catalog.Achievement achievement : unlocked) {
				if (config.announceUnlocks) {
					Mc.chat(unlockMessage(achievement));
				}
				if (config.unlockToasts) {
					Mc.toast(new UnlockToast(achievement));
				}
			}
			return;
		}
		if (config.announceUnlocks) {
			Mc.chat(unlockSummary(unlocked));
		}
		if (config.unlockToasts) {
			Mc.toast(new UnlockToast(unlocked));
		}
	}

	/** "(press ` or /titles to equip)", naming whatever key the player bound. */
	private static MutableComponent equipHint() {
		MutableComponent hint = Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY);
		if (openKey != null && !openKey.isUnbound()) {
			hint.append("press ").append(openKey.getTranslatedKeyMessage()).append(" or ");
		}
		return hint.append("/titles to equip)");
	}

	/** The chat line for an unlock, badge included. */
	public static MutableComponent unlockMessage(Catalog.Achievement achievement) {
		Title reward = Title.of(achievement.rewards().prefix(), achievement.rewards().suffix());
		var message = Component.literal("Achievement unlocked: ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(achievement.name()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
		if (reward != null) {
			message.append(Component.literal("\n New title words: ").withStyle(ChatFormatting.GRAY))
					.append(TitleRenderer.render(reward))
					.append(equipHint());
		}
		return Mc.tagged(message);
	}

	/** Names in a summary's tooltip before the rest are counted, so it fits on screen. */
	private static final int SUMMARY_NAMES = 12;

	/** One chat line for a batch of unlocks; hovering the count lists them, best first. */
	public static MutableComponent unlockSummary(List<Catalog.Achievement> unlocked) {
		List<Catalog.Achievement> best = unlocked.stream().sorted(UnlockToastArt.BEST_FIRST).toList();
		MutableComponent names = Component.empty();
		for (int i = 0; i < Math.min(best.size(), SUMMARY_NAMES); i++) {
			names.append(Component.literal((i == 0 ? "" : "\n") + best.get(i).name()).withStyle(ChatFormatting.GREEN));
		}
		if (best.size() > SUMMARY_NAMES) {
			names.append(Component.literal("\n...and " + (best.size() - SUMMARY_NAMES) + " more").withStyle(ChatFormatting.GRAY));
		}
		int points = unlocked.stream().mapToInt(Catalog.Achievement::points).sum();
		long words = unlocked.stream()
				.filter(a -> a.rewards() != null)
				.mapToLong(a -> (a.rewards().prefix() != null ? 1 : 0) + (a.rewards().suffix() != null ? 1 : 0))
				.sum();

		var message = Component.literal("Unlocked ").withStyle(ChatFormatting.GOLD)
				.append(Mc.withHover(
						Component.literal(unlocked.size() + " achievements").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD, ChatFormatting.UNDERLINE),
						names))
				.append(Component.literal(" (+" + points + " points)").withStyle(ChatFormatting.GRAY));
		if (words > 0) {
			message.append(Component.literal("\n " + words + " new title words").withStyle(ChatFormatting.GRAY))
					.append(equipHint());
		}
		return Mc.tagged(message);
	}
}
