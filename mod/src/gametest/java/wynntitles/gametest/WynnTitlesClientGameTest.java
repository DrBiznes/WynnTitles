package wynntitles.gametest;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import wynntitles.WynnTitles;
import wynntitles.account.Account;
import wynntitles.api.ApiClient;
import wynntitles.api.ApiTypes.Me;
import wynntitles.catalog.Catalog;
import wynntitles.config.WynnTitlesConfig;
import wynntitles.mc.Icons;
import wynntitles.mc.UnlockToast;
import wynntitles.mc.WynnFonts;
import wynntitles.mc.screen.TitlesScreen;
import wynntitles.titles.TitleStyle;

/**
 * End-to-end smoke test in a real client, fully offline: a local server with
 * WT_OFFLINE_AUTH=true stands in for the production one (scripts/smoke-test.sh
 * starts it), and a client-side fake player stands in for another Wynncraft
 * player. Screenshots land in build/run/clientGameTest/screenshots.
 *
 * <p>src/gametest/resources/assets/minecraft stands in for the Wynncraft
 * resource pack, which is not ours to ship: it selects models the way the real
 * pack does (damage on a diamond sword, custom model data on paper) with
 * vanilla textures.
 */
@SuppressWarnings("UnstableApiUsage")
public class WynnTitlesClientGameTest implements FabricClientGameTest {
	private static final int TIMEOUT_TICKS = 20 * 30;
	private static final String DUMMY_NAME = "TitleDummy";

	@Override
	public void runTest(ClientGameTestContext context) {
		ApiClient api = new ApiClient(WynnTitlesConfig::resolvedServerUrl, "gametest");
		Catalog catalog = join(api.catalog(null)).catalog();
		List<Catalog.Achievement> twoWord = catalog.achievements().stream()
				.filter(a -> a.rewards().prefix() != null && a.rewards().suffix() != null)
				.toList();
		Catalog.Achievement dummyAchievement = twoWord.get(0);
		Catalog.Achievement ownAchievement = twoWord.get(1);

		String dummyTitle = seedDummy(api, dummyAchievement);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			singleplayer.getServer().runCommand("time set noon");
			singleplayer.getServer().runCommand("weather clear");
			singleplayer.getServer().runCommand("gamerule doDaylightCycle false");

			// Singleplayer is not Wynncraft, so joining it must not contact the server.
			check(WynnTitles.account().me() == null, "joining a non-Wynncraft world should not log in");

			// Wynntils only fires its Wynncraft connection event on Wynncraft, so call
			// our handler directly. It logs in with the (offline) Mojang handshake.
			context.runOnClient(client -> WynnTitles.onWynncraftJoin());
			context.waitFor(client -> WynnTitles.account().me() != null, TIMEOUT_TICKS);
			Me me = WynnTitles.account().me();
			check(me.unlocked().isEmpty(), "a fresh player should have no unlocks");
			checkIcons(context, catalog);

			// Earn and equip a title for ourselves, the way the tracker and screen would.
			// Account calls read the Minecraft session, so start them on the client thread.
			join(context.computeOnClient(client -> WynnTitles.account().withToken(token -> api.claim(
					token, ownAchievement.id(), ownAchievement.trigger().type(), null, "gametest"))));
			me = join(context.computeOnClient(client -> WynnTitles.account().setTitle(
					ownAchievement.rewards().prefix().text(), ownAchievement.rewards().suffix().text())));
			check(me.title() != null, "equipping should produce a title");

			showDummyInFrontOfPlayer(context);
			try {
				context.waitFor(client -> NametagProbe.drawn(dummyTitle), TIMEOUT_TICKS);
			} catch (RuntimeException e) {
				context.takeScreenshot("wynntitles-nametag-missing");
				throw new AssertionError("title \"" + dummyTitle + "\" never reached the nametag; drew "
						+ NametagProbe.all(), e);
			}
			context.waitTicks(5);
			screenshot(context, "wynntitles-nametag");

			context.setScreen(TitlesScreen::new);
			context.waitForScreen(TitlesScreen.class);
			context.waitTicks(20);
			screenshot(context, "wynntitles-titles-tab");

			// Earn enough points to shop, then wear Greg in its native obfuscated
			// style next to a bought one, so the styles tab has both to show.
			for (String id : List.of("raid_tna", "raid_twp")) {
				Catalog.Achievement raid = catalog.get(id).orElseThrow();
				join(context.computeOnClient(client -> WynnTitles.account().withToken(token -> api.claim(
						token, raid.id(), raid.trigger().type(), null, "gametest"))));
			}
			join(context.computeOnClient(client -> WynnTitles.account().setTitle(
					"Greg", ownAchievement.rewards().suffix().text())));
			join(context.computeOnClient(client -> WynnTitles.account().buy(List.of("palette_flame", "bold"))));
			me = join(context.computeOnClient(client -> WynnTitles.account().setStyle(
					null, new TitleStyle("palette_flame", List.of("bold")))));
			check(me.title().runs().get(0).obfuscated(), "Greg should keep its native style");
			check(!me.title().runs().get(2).obfuscated() && me.title().runs().get(2).bold(),
					"the suffix should wear the bought style");
			context.setScreen(TitlesScreen::styles);
			context.waitTicks(20);
			screenshot(context, "wynntitles-styles-tab");
			context.setScreen(TitlesScreen::shop);
			context.waitTicks(20);
			screenshot(context, "wynntitles-shop-tab");

			// Pretend this mod version cannot detect the first locked achievement,
			// as if the catalog had gained a trigger type after it shipped.
			Catalog.Achievement undetectable = catalog.achievements().stream()
					.filter(a -> !a.id().equals(ownAchievement.id()))
					.findFirst()
					.orElseThrow();
			context.setScreen(() -> TitlesScreen.achievements(a -> !a.id().equals(undetectable.id())));
			context.waitTicks(20);
			screenshot(context, "wynntitles-achievements-tab");

			context.setScreen(() -> null);
			context.runOnClient(client -> client.getToastManager().clear());
			context.runOnClient(client -> client.getToastManager().addToast(new UnlockToast(ownAchievement)));
			// Let the toast slide in fully before the shot.
			context.waitTicks(30);
			context.takeScreenshot("wynntitles-unlock-toast");

			screenshotWithWynncraftPack(context, ownAchievement);
		}
	}

	/**
	 * With -Dwynntitles.wynnPack pointing at a cached Wynncraft pack (see
	 * README.md), loads it and shoots the screens again, so the screenshots show
	 * the pack's fonts and item models the way players see them. The pack stays
	 * in the run folder, outside the repository.
	 */
	private static void screenshotWithWynncraftPack(ClientGameTestContext context, Catalog.Achievement achievement) {
		String pack = System.getProperty("wynntitles.wynnPack");
		if (pack == null || pack.isBlank()) {
			return;
		}
		CompletableFuture<Void> reload = context.computeOnClient(client -> {
			try {
				Files.copy(Path.of(pack), client.getResourcePackDirectory().resolve("wynncraft.zip"),
						StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception e) {
				throw new AssertionError("could not copy the Wynncraft pack " + pack, e);
			}
			PackRepository packs = client.getResourcePackRepository();
			packs.reload();
			check(packs.addPack("file/wynncraft.zip"), "the Wynncraft pack did not load from " + pack);
			client.options.updateResourcePacks(packs);
			return client.reloadResourcePacks();
		});
		context.waitFor(client -> reload.isDone() && client.getOverlay() == null, TIMEOUT_TICKS);
		context.runOnClient(client -> {
			check(WynnFonts.loaded(), "the Wynncraft pack should provide its fonts");
		});

		context.setScreen(TitlesScreen::new);
		context.waitTicks(20);
		screenshot(context, "wynntitles-titles-tab-wynnpack");
		context.setScreen(TitlesScreen::styles);
		context.waitTicks(20);
		screenshot(context, "wynntitles-styles-tab-wynnpack");
		context.setScreen(TitlesScreen::shop);
		context.waitTicks(20);
		screenshot(context, "wynntitles-shop-tab-wynnpack");
		context.setScreen(TitlesScreen::achievements);
		context.waitTicks(20);
		screenshot(context, "wynntitles-achievements-tab-wynnpack");
		context.setScreen(() -> null);
		context.runOnClient(client -> client.getToastManager().clear());
		context.runOnClient(client -> client.getToastManager().addToast(new UnlockToast(achievement)));
		context.waitTicks(30);
		context.takeScreenshot("wynntitles-unlock-toast-wynnpack");
	}

	/** Icons resolve through the fake pack, and fall back when a model is missing. */
	private static void checkIcons(ClientGameTestContext context, Catalog catalog) {
		context.runOnClient(client -> {
			ItemStack scroll = Icons.stack(icon(catalog, "wynn/prop/quest_scroll"));
			check(scroll.is(Items.DIAMOND_SWORD) && scroll.getDamageValue() == 61,
					"quest scroll should be a diamond sword at damage 61, got " + scroll + " damage " + scroll.getDamageValue());

			ItemStack bow = Icons.stack(icon(catalog, "wynn/weapon/archer/bow_basic_wood"));
			var data = bow.get(DataComponents.CUSTOM_MODEL_DATA);
			check(bow.is(Items.PAPER) && data != null && data.floats().equals(List.of(4100f)),
					"archer bow should be paper with custom model data 4100, got " + bow + " " + data);

			Catalog.Icon missing = icon(catalog, "wynn/weapon/warrior/spear_basic_wood");
			check(!Icons.resolved(missing) && Icons.stack(missing).is(Items.TRIDENT),
					"a model the pack lacks should fall back to " + missing.fallback());
		});
	}

	private static Catalog.Icon icon(Catalog catalog, String model) {
		return catalog.achievements().stream()
				.map(Catalog.Achievement::icon)
				.filter(icon -> icon != null && model.equals(icon.model()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no catalog icon uses " + model));
	}

	/** Gives a second player an equipped title on the server and returns its plain text. */
	private static String seedDummy(ApiClient api, Catalog.Achievement achievement) {
		Path dir;
		try {
			dir = Files.createTempDirectory("wynntitles-gametest");
		} catch (Exception e) {
			throw new AssertionError(e);
		}
		Account dummy = new Account(api, new OfflineSession(offlineUuid(DUMMY_NAME), DUMMY_NAME), dir.resolve("s.json"));
		join(dummy.withToken(token -> api.claim(token, achievement.id(), achievement.trigger().type(), null, "gametest")));
		Me me = join(dummy.setTitle(achievement.rewards().prefix().text(), achievement.rewards().suffix().text()));
		check(me.title() != null, "dummy should have a title");
		return me.title().plain();
	}

	/** Adds a client-only player three blocks in front of us, facing the camera. */
	private static void showDummyInFrontOfPlayer(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.player.snapTo(client.player.getX(), client.player.getY(), client.player.getZ(), 0, 0);
			Vec3 pos = client.player.position().add(0, 0, 3);
			RemotePlayer dummy = new RemotePlayer(client.level, new GameProfile(offlineUuid(DUMMY_NAME), DUMMY_NAME));
			dummy.snapTo(pos.x, pos.y, pos.z, 180, 0);
			dummy.setYHeadRot(180);
			dummy.setOldPosAndRot();
			client.level.addEntity(dummy);
		});
	}

	/** Clears toasts (Wynntils posts a few on startup) so they do not cover the UI. */
	private static void screenshot(ClientGameTestContext context, String name) {
		context.runOnClient(client -> client.getToastManager().clear());
		context.waitTick();
		context.takeScreenshot(name);
	}

	private static UUID offlineUuid(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
	}

	private static <T> T join(CompletableFuture<T> future) {
		try {
			return future.get();
		} catch (Exception e) {
			throw new AssertionError("server call failed", e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private record OfflineSession(UUID uuid, String username) implements Account.MinecraftSession {
		@Override
		public void joinServer(String serverId) {
			// The test server runs with WT_OFFLINE_AUTH=true and never asks Mojang.
		}
	}
}
