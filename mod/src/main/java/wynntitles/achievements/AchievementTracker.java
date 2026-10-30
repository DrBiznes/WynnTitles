package wynntitles.achievements;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import wynntitles.WynnTitles;
import wynntitles.account.Account;
import wynntitles.api.ApiClient;
import wynntitles.api.ApiException;
import wynntitles.api.ApiTypes.Me;
import wynntitles.api.ApiTypes.Unlock;
import wynntitles.catalog.Catalog;
import wynntitles.catalog.CatalogStore;

/** Watches every locked achievement in the catalog and claims it once its trigger is met. */
public final class AchievementTracker {
	private static final int CHECK_EVERY_TICKS = 20;
	private static final long RETRY_AFTER_ERROR_MS = 60_000;

	private final ApiClient api;
	private final Account account;
	private final CatalogStore catalogs;
	private final Map<String, Detector> detectors;
	private final Consumer<Catalog.Achievement> onUnlocked;
	private final String modVersion;

	/** Claimed this session; the profile catches up after the next refresh. */
	private final Set<String> claimed = ConcurrentHashMap.newKeySet();
	private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
	private final Map<String, Long> retryAt = new ConcurrentHashMap<>();
	private final Set<String> failingTypes = ConcurrentHashMap.newKeySet();
	/** Set by a successful claim; one profile refresh follows once no claim is in flight. */
	private final AtomicBoolean refreshPending = new AtomicBoolean();
	private int ticks;

	public AchievementTracker(
			ApiClient api,
			Account account,
			CatalogStore catalogs,
			Map<String, Detector> detectors,
			Consumer<Catalog.Achievement> onUnlocked,
			String modVersion) {
		this.api = api;
		this.account = account;
		this.catalogs = catalogs;
		this.detectors = detectors;
		this.onUnlocked = onUnlocked;
		this.modVersion = modVersion;
	}

	/** Call once per client tick, on the client thread. */
	public void tick() {
		if (++ticks < CHECK_EVERY_TICKS) {
			return;
		}
		ticks = 0;

		// A first login claims dozens at once; refresh the profile once, not per claim.
		if (inFlight.isEmpty() && refreshPending.compareAndSet(true, false)) {
			account.refresh();
		}

		Catalog catalog = catalogs.catalog();
		Me me = account.me();
		if (catalog == null || me == null) {
			return;
		}

		Set<String> unlocked = me.unlocked().stream().map(Unlock::achievementId).collect(Collectors.toSet());
		long now = System.currentTimeMillis();

		for (Catalog.Achievement achievement : catalog.achievements()) {
			String id = achievement.id();
			if (unlocked.contains(id) || claimed.contains(id) || inFlight.contains(id)
					|| retryAt.getOrDefault(id, 0L) > now) {
				continue;
			}
			if (!canDetect(achievement)) {
				continue;
			}
			Detector detector = detectors.get(achievement.trigger().type());

			JsonObject evidence;
			try {
				evidence = detector.check(achievement.trigger());
			} catch (Throwable t) {
				// Usually Wynntils data that is not loaded yet. Log once per type.
				if (failingTypes.add(achievement.trigger().type())) {
					WynnTitles.LOGGER.warn("Detector {} failed", achievement.trigger().type(), t);
				}
				continue;
			}
			if (evidence != null) {
				claim(achievement, evidence);
			}
		}
	}

	/** False when this mod version can never claim the achievement and needs an update. */
	public boolean canDetect(Catalog.Achievement achievement) {
		Detector detector = detectors.get(achievement.trigger().type());
		return detector != null && detector.supports(achievement.trigger());
	}

	/** The detector's progress line for a locked achievement, or null when it has none. */
	public String progress(Catalog.Achievement achievement) {
		if (!canDetect(achievement)) {
			return null;
		}
		try {
			return detectors.get(achievement.trigger().type()).progress(achievement.trigger());
		} catch (Throwable t) {
			// Wynntils data that is not loaded yet; the tick loop logs detector failures.
			return null;
		}
	}

	private void claim(Catalog.Achievement achievement, JsonObject evidence) {
		String id = achievement.id();
		inFlight.add(id);
		account.withToken(token -> api.claim(token, id, achievement.trigger().type(), evidence, modVersion))
				.whenComplete((result, error) -> {
					inFlight.remove(id);
					if (error != null) {
						retryAt.put(id, System.currentTimeMillis() + RETRY_AFTER_ERROR_MS);
						if (unwrap(error) instanceof ApiException e && e.status() == 409) {
							// Our catalog is older than the server's.
							catalogs.refresh();
						}
						WynnTitles.LOGGER.warn("Claim for {} failed: {}", id, ApiException.describe(error));
						return;
					}
					claimed.add(id);
					if (result.newlyUnlocked()) {
						onUnlocked.accept(achievement);
					}
					refreshPending.set(true);
				});
	}

	private static Throwable unwrap(Throwable t) {
		while (t.getCause() != null && !(t instanceof ApiException)) {
			t = t.getCause();
		}
		return t;
	}
}
