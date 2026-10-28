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
import org.junit.jupiter.api.Test;
import wynntitles.account.Account;
import wynntitles.achievements.AchievementTracker;
import wynntitles.achievements.Detector;
import wynntitles.api.ApiClient;
import wynntitles.api.ApiTypes.AchievementStats;
import wynntitles.api.ApiTypes.ClaimResult;
import wynntitles.api.ApiTypes.Me;
import wynntitles.catalog.Catalog;
import wynntitles.catalog.CatalogStore;
import wynntitles.titles.Title;

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
		ClaimResult claim = account.withToken(t -> api.claim(t, first.id(), first.trigger().type(), evidence, "test")).get();
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
		AchievementTracker tracker = new AchievementTracker(
				api, account, catalogs, Map.of(target.trigger().type(), fake), a -> announced.add(a.id()), "test");

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
