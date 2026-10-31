package wynntitles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import wynntitles.account.Account;
import wynntitles.achievements.AchievementTracker;
import wynntitles.achievements.Detector;
import wynntitles.api.ApiClient;
import wynntitles.api.ApiTypes.AchievementStats;
import wynntitles.api.ApiTypes.Claim;
import wynntitles.api.ApiTypes.ClaimResult;
import wynntitles.api.ApiTypes.Me;
import wynntitles.catalog.Catalog;
import wynntitles.catalog.CatalogStore;
import wynntitles.titles.Title;
import wynntitles.titles.TitleStyle;

/**
 * Runs the mod's API client against a real server to catch JSON mismatches
 * between the Java records and the Go handlers. Start the server with
 * WT_OFFLINE_AUTH=true, then run:
 *
 *   ./gradlew test -Dwynntitles.testServer=http://localhost:8080
 */
class ServerContractTest {
	private static final String SERVER = System.getProperty("wynntitles.testServer");

	@Test
	void fullFlow() throws Exception {
		assumeTrue(SERVER != null, "set -Dwynntitles.testServer to run");

		String username = "Contract" + (System.nanoTime() % 100_000);
		// The offline-auth server derives this same UUID from the username, like Minecraft does.
		UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
		Path dir = Files.createTempDirectory("wynntitles-test");
		ApiClient api = new ApiClient(() -> SERVER, "test");
		Account account = new Account(api, new FakeSession(uuid, username), dir.resolve("session.json"));

		Catalog catalog = new CatalogStore(api, dir.resolve("catalog.json")).refresh().get();
		assertFalse(catalog.achievements().isEmpty());
		Catalog.Achievement first = catalog.achievements().get(0);
		assertNotNull(first.rewards().prefix().runs().get(0).text());

		Me me = account.refresh().get();
		assertEquals(username, me.username());
		assertTrue(me.unlocked().isEmpty());
		assertTrue(Files.exists(dir.resolve("session.json")), "session should be cached on disk");

		JsonObject evidence = new JsonObject();
		evidence.addProperty("test", true);
		ClaimResult claim = account.withToken(t -> api.claims(t, List.of(new Claim(first.id(), first.trigger().type(), evidence)), "test")).get().get(0);
		assertTrue(claim.newlyUnlocked());

		// Other tests share the server, so only check what this player's claim guarantees.
		AchievementStats stats = api.achievementStats().get();
		assertTrue(stats.players() >= 1);
		assertTrue(stats.unlocks().getOrDefault(first.id(), 0) >= 1, "the claim should be counted");
		assertEquals(catalog.achievements().size(), stats.unlocks().size());
		assertTrue(stats.percent(first.id()) > 0 && stats.percent(first.id()) <= 100);

		me = account.setTitle(first.rewards().prefix().text(), first.rewards().suffix().text()).get();
		assertEquals(1, me.unlocked().size());
		assertNotNull(me.title());
		assertEquals(first.rewards().prefix().text() + " " + first.rewards().suffix().text(), me.title().plain());

		UUID serverUuid = UUID.fromString(me.uuid());
		assertEquals(uuid, serverUuid);
		UUID nobody = new UUID(0, 1);
		Map<UUID, Title> titles = api.titles(List.of(serverUuid, nobody)).get();
		assertEquals(me.title(), titles.get(serverUuid));
		assertTrue(titles.containsKey(nobody));
		assertNull(titles.get(nobody));

		// The shop: Greg starts obfuscated, and a bought style replaces that. The
		// mod's own restyle, used for previews, must match what the server wears.
		assertFalse(catalog.shop().isEmpty());
		assertTrue(catalog.shop().stream().allMatch(Catalog.ShopItem::supported), "this mod should know every shop item");
		Catalog.Achievement greg = catalog.get("raid_tna").orElseThrow();
		assertTrue(greg.rewards().prefix().runs().get(0).obfuscated());
		account.withToken(t -> api.claims(t, List.of(new Claim(greg.id(), greg.trigger().type(), evidence)), "test")).get();
		me = account.setTitle("Greg", null).get();
		assertEquals(first.points() + greg.points(), me.balance());
		assertEquals(greg.rewards().prefix().runs(), me.title().runs());

		// Pricing follows all 164 achievements; earn the full test budget.
		for (Catalog.Achievement a : catalog.achievements()) {
			account.withToken(t -> api.claims(t, List.of(new Claim(a.id(), a.trigger().type(), evidence)), "test")).get();
		}
		int earned = catalog.achievements().stream().mapToInt(Catalog.Achievement::points).sum();

		me = account.buy(List.of("color_red", "bold")).get();
		assertEquals(earned - catalog.shopItem("color_red").orElseThrow().price() - catalog.shopItem("bold").orElseThrow().price(), me.balance());
		assertTrue(me.owns("color_red") && me.owns("bold"));
		TitleStyle style = new TitleStyle("color_red", List.of("bold"));
		me = account.setStyle(style, null).get();
		assertEquals(style, me.styles().prefix());
		assertNull(me.styles().suffix());
		assertEquals(catalog.restyle(greg.rewards().prefix(), style).runs(), me.title().runs());
		assertFalse(me.title().runs().get(0).obfuscated());
		assertEquals(me.title(), api.titles(List.of(serverUuid)).get().get(serverUuid));

		// An animated palette carries its animation through the server to the nametag.
		// Purchases are final, so each step below buys only pieces it does not own yet.
		Catalog.Achievement palace = catalog.get("raid_twp").orElseThrow();
		account.withToken(t -> api.claims(t, List.of(new Claim(palace.id(), palace.trigger().type(), evidence)), "test")).get();
		Catalog.ShopItem animated = catalog.shopItem("animated_ocean").orElseThrow();
		account.buy(List.of(animated.id())).get();
		TitleStyle wave = new TitleStyle(animated.id(), List.of());
		me = account.setStyle(wave, null).get();
		assertEquals(catalog.restyle(greg.rewards().prefix(), wave).runs(), me.title().runs());
		assertEquals(animated.animation(), me.title().runs().get(0).effect().animation());

		// Every font survives purchase, persistence, public lookup and preview
		// exactly. A bought font replaces native obfuscation just like a color.
		account.buy(catalog.shop().stream().filter(i -> Catalog.ShopItem.FONT.equals(i.kind()))
				.map(Catalog.ShopItem::id).toList()).get();
		account.buy(List.of("palette_ice")).get();
		for (Catalog.ShopItem font : catalog.shop()) {
			if (!Catalog.ShopItem.FONT.equals(font.kind())) continue;
			TitleStyle fontStyle = new TitleStyle("color_red", font.banner() ? List.of() : List.of("bold"),
					font.id(), null, font.banner() ? "yellow" : null);
			me = account.setStyle(fontStyle, null).get();
			assertEquals(fontStyle, me.styles().prefix());
			assertEquals(catalog.restyle(greg.rewards().prefix(), fontStyle).runs(), me.title().runs());
			assertEquals(font.font(), me.title().runs().get(0).font());
			assertEquals(me.title(), api.titles(List.of(serverUuid)).get().get(serverUuid));
			if (font.banner()) {
				org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> account.setStyle(
						new TitleStyle("palette_ice", List.of(), font.id(), null, null), null).get());
				org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> account.setStyle(
						new TitleStyle("color_red", List.of("bold"), font.id(), null, null), null).get());
				TitleStyle fallback = new TitleStyle(null, List.of(), font.id(), "#C4F1FF", null);
				me = account.setStyle(fallback, null).get();
				assertEquals(catalog.restyle(greg.rewards().prefix(), fallback).runs(), me.title().runs());
			}
		}
		Me owner = me;
		int spent = catalog.shop().stream().filter(i -> owner.owns(i.id())).mapToInt(Catalog.ShopItem::price).sum();
		assertEquals(earned - spent, me.balance());

		// A second Account reuses the cached token instead of logging in again.
		Account restarted = new Account(api, new FakeSession(uuid, username) {
			@Override
			public void joinServer(String serverId) {
				throw new AssertionError("should reuse the cached session");
			}
		}, dir.resolve("session.json"));
		assertEquals(username, restarted.refresh().get().username());
	}

	@Test
	void trackerClaimsWhenDetectorFires() throws Exception {
		assumeTrue(SERVER != null, "set -Dwynntitles.testServer to run");

		String username = "Tracker" + (System.nanoTime() % 100_000);
		UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
		Path dir = Files.createTempDirectory("wynntitles-test");
		ApiClient api = new ApiClient(() -> SERVER, "test");
		Account account = new Account(api, new FakeSession(uuid, username), dir.resolve("session.json"));
		CatalogStore catalogs = new CatalogStore(api, dir.resolve("catalog.json"));
		Catalog.Achievement target = catalogs.refresh().get().achievements().get(0);
		account.refresh().get();

		// A detector that only fires for the target, standing in for Wynntils data.
		Detector fake = trigger -> {
			if (!trigger.equals(target.trigger())) {
				return null;
			}
			JsonObject evidence = new JsonObject();
			evidence.addProperty("fake", true);
			return evidence;
		};
		List<String> announced = new CopyOnWriteArrayList<>();
		AtomicBoolean inGame = new AtomicBoolean(false);
		AchievementTracker tracker = new AchievementTracker(
				api, account, catalogs, Map.of(target.trigger().type(), fake), a -> announced.add(a.id()), "test", inGame::get);

		// Off a Wynncraft world nothing is checked, even when the trigger is met.
		for (int i = 0; i < 100; i++) {
			tracker.tick();
		}
		Thread.sleep(200);
		assertTrue(account.refresh().get().unlocked().isEmpty(), "claimed outside the game");

		inGame.set(true);
		long deadline = System.currentTimeMillis() + 10_000;
		while (!hasUnlock(account.me(), target.id()) && System.currentTimeMillis() < deadline) {
			tracker.tick();
			Thread.sleep(10);
		}
		assertTrue(hasUnlock(account.me(), target.id()), "tracker should have claimed " + target.id());
		assertEquals(List.of(target.id()), announced, "unlock should be announced exactly once");

		// Further ticks must not claim again.
		for (int i = 0; i < 100; i++) {
			tracker.tick();
		}
		Thread.sleep(200);
		assertEquals(1, announced.size());
	}

	private static boolean hasUnlock(Me me, String achievementId) {
		return me != null && me.unlocked().stream().anyMatch(u -> u.achievementId().equals(achievementId));
	}

	private static class FakeSession implements Account.MinecraftSession {
		private final UUID uuid;
		private final String username;

		FakeSession(UUID uuid, String username) {
			this.uuid = uuid;
			this.username = username;
		}

		@Override
		public UUID uuid() {
			return uuid;
		}

		@Override
		public String username() {
			return username;
		}

		@Override
		public void joinServer(String serverId) {
			// The offline-auth server does not ask Mojang.
		}
	}
}
