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
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
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
import wynntitles.mc.Mc;
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
			screenshotAnimatedNametags(context, catalog, dummyAchievement);

			context.setScreen(TitlesScreen::new);
			context.waitForScreen(TitlesScreen.class);
			context.waitTicks(20);
			screenshot(context, "wynntitles-titles-tab");

			// Earn enough points to shop, then wear Greg in its native obfuscated
			// style next to a bought one, so the styles tab has both to show.
			for (Catalog.Achievement raid : catalog.achievements().stream()
					.filter(a -> List.of("raid_tna", "raid_twp").contains(a.id()) || a.points() >= 5 && a.category().equals("quest")).toList()) {
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
			scrollShopToPalettes(context);
			screenshot(context, "wynntitles-shop-tab-palettes");
			scrollShopToEnd(context);
			screenshot(context, "wynntitles-shop-tab-scrolled");

			// Pretend this mod version cannot detect the first locked achievement,
			// as if the catalog had gained a trigger type after it shipped.
			Catalog.Achievement undetectable = catalog.achievements().stream()
					.filter(a -> !a.id().equals(ownAchievement.id()))
					.findFirst()
					.orElseThrow();
			context.setScreen(() -> TitlesScreen.achievements(a -> !a.id().equals(undetectable.id()), a -> null));
			context.waitTicks(20);
			screenshot(context, "wynntitles-achievements-tab");
			screenshotProgressTooltip(context);
			screenshotWindowSizes(context);

			context.setScreen(() -> null);
			context.runOnClient(client -> client.getToastManager().clear());
			context.runOnClient(client -> client.getToastManager().addToast(new UnlockToast(ownAchievement)));
			// Let the toast slide in fully before the shot.
			context.waitTicks(30);
			context.takeScreenshot("wynntitles-unlock-toast");
			screenshotChat(context, ownAchievement, "wynntitles-chat");
			screenshotUnlockSummary(context, catalog);

			screenshotFonts(context, catalog, "fallbacks");
			screenshotIconGallery(context, catalog, "fallbacks");
			screenshotWithWynncraftPack(context, catalog, ownAchievement);
		}
	}

	/**
	 * With -Dwynntitles.wynnPack pointing at a cached Wynncraft pack (see
	 * README.md), loads it and shoots the screens again, so the screenshots show
	 * the pack's fonts and item models the way players see them. The pack stays
	 * in the run folder, outside the repository.
	 */
	private static void screenshotWithWynncraftPack(ClientGameTestContext context, Catalog catalog, Catalog.Achievement achievement) {
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
			for (Catalog.Achievement entry : catalog.achievements()) {
				check(entry.icon().model() == null || Icons.resolved(entry.icon()),
						entry.id() + ": the loaded Wynncraft pack should resolve " + entry.icon().model());
			}
		});
		screenshotFonts(context, catalog, "wynnpack");
		screenshotIconGallery(context, catalog, "wynnpack");

		context.setScreen(TitlesScreen::new);
		context.waitTicks(20);
		screenshot(context, "wynntitles-titles-tab-wynnpack");
		context.setScreen(TitlesScreen::styles);
		context.waitTicks(20);
		screenshot(context, "wynntitles-styles-tab-wynnpack");
		context.setScreen(TitlesScreen::shop);
		context.waitTicks(20);
		screenshot(context, "wynntitles-shop-tab-wynnpack");
		scrollShopToPalettes(context);
		screenshot(context, "wynntitles-shop-tab-palettes-wynnpack");
		context.setScreen(TitlesScreen::achievements);
		context.waitTicks(20);
		screenshot(context, "wynntitles-achievements-tab-wynnpack");
		context.setScreen(() -> null);
		context.runOnClient(client -> client.getToastManager().clear());
		context.runOnClient(client -> client.getToastManager().addToast(new UnlockToast(achievement)));
		context.waitTicks(30);
		context.takeScreenshot("wynntitles-unlock-toast-wynnpack");
		context.runOnClient(client -> check(WynnFonts.pillsLoaded(), "the Wynncraft pack should provide its chat pills"));
		screenshotChat(context, achievement, "wynntitles-chat-wynnpack");
	}

	/** All ten fonts in chat, the shop, a contact sheet and real nametags. */
	private static void screenshotFonts(ClientGameTestContext context, Catalog catalog, String variant) {
		List<Catalog.ShopItem> fonts = catalog.shop().stream().filter(i -> Catalog.ShopItem.FONT.equals(i.kind())).toList();
		int[] size = context.computeOnClient(client -> new int[] {client.getWindow().getWidth(), client.getWindow().getHeight()});
		int scale = context.computeOnClient(client -> client.options.guiScale().get());
		context.runOnClient(client -> client.options.guiScale().set(2));
		context.getInput().resizeWindow(1920, 1080);
		context.setScreen(() -> new FontGalleryScreen(catalog));
		context.waitTicks(5);
		screenshot(context, "wynntitles-fonts-" + variant);
		context.setScreen(() -> null);
		context.runOnClient(client -> client.options.guiScale().set(scale));
		context.getInput().resizeWindow(size[0], size[1]);
		context.runOnClient(client -> client.gui.getChat().clearMessages(true));
		for (Catalog.ShopItem item : fonts) {
			if (variant.equals("wynnpack")) context.runOnClient(client -> {
				check(WynnFonts.available(item.font()), item.font() + " must load");
				if ("font_pill".equals(item.id())) {
					Component banner = WynnFonts.banner(item.font(), "Wynn Hero 123", 0xFFAA00, null);
					check(client.font.width(banner) == client.font.width(WynnFonts.pill("Wynn Hero 123")),
							"the foreground must not change the banner's advance");
				}
			});
			var style = new TitleStyle("color_gold", List.of(), item.id(), null, null);
			var title = wynntitles.titles.Title.of(catalog.restyle(new wynntitles.titles.TitleWord("Wynn Hero 123", null), style), null);
			context.runOnClient(client -> {
				WynnTitles.titles().put(offlineUuid(DUMMY_NAME), title);
				client.gui.getChat().addMessage(Component.literal(item.name() + ": ").append(wynntitles.mc.TitleRenderer.render(title)));
			});
			context.waitTicks(3);
			screenshot(context, "wynntitles-font-nametag-" + item.id() + "-" + variant);
		}
		context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_T);
		context.waitTicks(5);
		screenshot(context, "wynntitles-font-chat-" + variant);
		context.setScreen(TitlesScreen::shop);
		context.waitTicks(20);
		scrollShopToEnd(context);
		context.getInput().setCursorPos(0, 0);
		screenshot(context, "wynntitles-font-shop-" + variant);
		Me before = WynnTitles.account().me();
		for (Catalog.Achievement a : catalog.achievements()) {
			join(context.computeOnClient(client -> WynnTitles.account().withToken(token ->
					new ApiClient(WynnTitlesConfig::resolvedServerUrl, "gametest").claim(token, a.id(), a.trigger().type(), null, "gametest"))));
		}
		List<String> purchase = catalog.shop().stream()
				.filter(i -> Catalog.ShopItem.FONT.equals(i.kind()) || List.of("color_gold", "palette_ice", "animated_ice", "rainbow").contains(i.id()))
				.filter(i -> !WynnTitles.account().me().owns(i.id())).map(Catalog.ShopItem::id).toList();
		if (!purchase.isEmpty()) join(context.computeOnClient(client -> WynnTitles.account().buy(purchase)));
		join(context.computeOnClient(client -> WynnTitles.account().setTitle("Greg", "Vanguard")));
		join(context.computeOnClient(client -> WynnTitles.account().setStyle(
				new TitleStyle("color_gold", List.of(), "font_pill", null, null), before.styles().suffix())));
		context.setScreen(TitlesScreen::styles);
		context.waitTicks(20);
		context.getInput().setCursorPos(0, 0);
		screenshot(context, "wynntitles-font-styles-disabled-" + variant);
		scrollShopToEnd(context);
		context.getInput().setCursorPos(0, 0);
		screenshot(context, "wynntitles-font-letter-colors-" + variant);
		join(context.computeOnClient(client -> WynnTitles.account().setStyle(null, null)));
		join(context.computeOnClient(client -> WynnTitles.account().setTitle("Queen's", "Vanguard")));
		context.setScreen(TitlesScreen::styles);
		context.waitTicks(20);
		scrollShopToEnd(context);
		context.getInput().setCursorPos(0, 0);
		screenshot(context, "wynntitles-font-unsupported-title-" + variant);
		join(context.computeOnClient(client -> WynnTitles.account().setTitle(before.equipped().prefix(), before.equipped().suffix())));
		join(context.computeOnClient(client -> WynnTitles.account().setStyle(before.styles().prefix(), before.styles().suffix())));

		context.setScreen(() -> null);
	}

	/** Every icon at its actual menu size, including achievements below the first screenful. */
	private static void screenshotIconGallery(ClientGameTestContext context, Catalog catalog, String variant) {
		int[] size = context.computeOnClient(client -> new int[] {
				client.getWindow().getWidth(), client.getWindow().getHeight()});
		int guiScale = context.computeOnClient(client -> client.options.guiScale().get());
		context.runOnClient(client -> client.options.guiScale().set(2));
		context.getInput().resizeWindow(1920, 1080);
		context.runOnClient(client -> {
			for (Catalog.Achievement entry : catalog.achievements()) {
				Catalog.Icon fallback = new Catalog.Icon(null, entry.icon().fallback());
				check(Icons.stack(fallback).is(net.minecraft.core.registries.BuiltInRegistries.ITEM
						.getValue(net.minecraft.resources.Identifier.parse(entry.icon().fallback()))),
						entry.id() + ": invalid vanilla fallback " + entry.icon().fallback());
			}
		});
		for (int offset = 0; offset < catalog.achievements().size(); offset += IconGalleryScreen.PAGE_SIZE) {
			int page = offset / IconGalleryScreen.PAGE_SIZE + 1;
			List<Catalog.Achievement> entries = catalog.achievements().subList(offset,
					Math.min(offset + IconGalleryScreen.PAGE_SIZE, catalog.achievements().size()));
			context.setScreen(() -> new IconGalleryScreen(entries, variant, page));
			context.waitTicks(3);
			screenshot(context, "wynntitles-icons-" + variant + "-" + page);
		}
		context.setScreen(() -> null);
		context.runOnClient(client -> client.options.guiScale().set(guiScale));
		context.getInput().resizeWindow(size[0], size[1]);
	}

	/**
	 * Shoots the titles tab in a window too small for the panel at the GUI
	 * scale, where it should shrink to fit and stay crisp, and in a large one,
	 * then puts the window back.
	 */
	private static void screenshotWindowSizes(ClientGameTestContext context) {
		int[] size = context.computeOnClient(client -> new int[] {
				client.getWindow().getWidth(), client.getWindow().getHeight()});
		for (int[] window : new int[][] {{800, 600}, {1920, 1080}}) {
			context.getInput().resizeWindow(window[0], window[1]);
			context.setScreen(TitlesScreen::new);
			context.waitTicks(20);
			screenshot(context, "wynntitles-titles-tab-" + window[0] + "x" + window[1]);
		}
		context.getInput().resizeWindow(size[0], size[1]);
	}

	/** Writes the unlock line and a command reply to an otherwise empty chat, to show the badge. */
	/** What a first login shows instead of a toast per achievement: one summary toast and chat line. */
	private static void screenshotUnlockSummary(ClientGameTestContext context, Catalog catalog) {
		List<Catalog.Achievement> batch = catalog.achievements().subList(0, 23);
		context.runOnClient(client -> {
			client.getToastManager().clear();
			client.getToastManager().addToast(new UnlockToast(batch));
			client.gui.getChat().clearMessages(false);
			Mc.chat(WynnTitles.unlockSummary(batch));
		});
		context.waitTicks(30);
		context.takeScreenshot("wynntitles-unlock-summary");
	}

	private static void screenshotChat(ClientGameTestContext context, Catalog.Achievement achievement, String name) {
		context.runOnClient(client -> {
			client.gui.getChat().clearMessages(false);
			Mc.chat(WynnTitles.unlockMessage(achievement));
			Mc.chat(Mc.tagged(Component.literal("Reloaded titles and achievements.").withStyle(ChatFormatting.GREEN)));
		});
		context.waitTicks(5);
		screenshot(context, name);
	}

	/** Gives every locked achievement a progress line and hovers the first "Locked" label, to shoot its tooltip. */
	private static void screenshotProgressTooltip(ClientGameTestContext context) {
		context.setScreen(() -> TitlesScreen.achievements(a -> true, a -> "37 / 100 levels"));
		context.waitTicks(20);
		double[] pos = context.computeOnClient(client -> ((TitlesScreen) client.screen).tooltipTarget());
		check(pos != null, "a locked achievement should have a progress tooltip");
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(5);
		screenshot(context, "wynntitles-achievements-progress");
	}

	/** Wheels the shop's list a little way down, to the palette rows, so their names can be checked for contrast. */
	private static void scrollShopToPalettes(ClientGameTestContext context) {
		// Lists scroll 15 panel pixels a notch (ScrollView.NOTCH); four puts the palettes heading at the
		// top of the well, with the palette and animated rows below it.
		double[] pos = context.computeOnClient(client -> new double[] {
				client.getWindow().getScreenWidth() / 2.0,
				client.getWindow().getScreenHeight() / 2.0 + 30 * client.getWindow().getGuiScale()});
		context.getInput().setCursorPos(pos[0], pos[1]);
		for (int i = 0; i < 4; i++) {
			context.getInput().scroll(-1);
		}
		context.waitTicks(20);
	}

	/** Wheels the shop's list as far down as it goes, so the shot shows whether the last row is cut off. */
	private static void scrollShopToEnd(ClientGameTestContext context) {
		// The list's well sits about 30 GUI pixels below the middle of the screen.
		double[] pos = context.computeOnClient(client -> new double[] {
				client.getWindow().getScreenWidth() / 2.0,
				client.getWindow().getScreenHeight() / 2.0 + 30 * client.getWindow().getGuiScale()});
		context.getInput().setCursorPos(pos[0], pos[1]);
		for (int i = 0; i < 20; i++) {
			context.getInput().scroll(-1);
		}
		context.waitTicks(20);
	}

	/**
	 * Dresses the dummy's nametag in one animated palette per animation, as the
	 * mod's own preview builds it, and shoots two frames of each, so the shots
	 * show the nametag moving the way the shop's chips do. Client side only: the
	 * title cache is told the dummy's title directly, then given it back.
	 */
	private static void screenshotAnimatedNametags(ClientGameTestContext context, Catalog catalog,
			Catalog.Achievement achievement) {
		UUID dummy = offlineUuid(DUMMY_NAME);
		wynntitles.titles.TitleWord prefix = achievement.rewards().prefix();
		wynntitles.titles.TitleWord suffix = achievement.rewards().suffix();
		java.util.Set<String> shot = new java.util.HashSet<>();
		for (Catalog.ShopItem item : catalog.shop()) {
			if (!Catalog.ShopItem.ANIMATED_PALETTE.equals(item.kind()) || !shot.add(item.animation())) {
				continue;
			}
			TitleStyle style = new TitleStyle(item.id(), List.of());
			wynntitles.titles.Title title = wynntitles.titles.Title.of(
					catalog.restyle(prefix, style), catalog.restyle(suffix, style));
			context.runOnClient(client -> WynnTitles.titles().put(dummy, title));
			// Shoot 15% into a beat, so animations that rest (comet, converge) are caught moving.
			int beat = item.motion().defaultCycleMs;
			long wait = Math.floorMod(beat * 15 / 100 - System.currentTimeMillis() % beat, (long) beat);
			context.waitTicks(Math.max(3, (int) (wait / 50)));
			screenshot(context, "wynntitles-nametag-" + item.animation() + "-1");
			context.waitTicks(8);
			screenshot(context, "wynntitles-nametag-" + item.animation() + "-2");
		}
		check(shot.size() == wynntitles.titles.TitleAnimation.values().length, "the shop should sell every animation, got " + shot);
		context.runOnClient(client -> WynnTitles.titles().put(dummy, wynntitles.titles.Title.of(prefix, suffix)));
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

			Catalog.Icon missing = catalog.get("warrior_lvl_1").orElseThrow().icon();
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
