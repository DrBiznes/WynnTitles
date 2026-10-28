package wynntitles.titles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import wynntitles.WynnTitles;
import wynntitles.api.ApiClient;

/**
 * Titles of nearby players. Asking for a title that is missing or stale queues
 * it, and queued UUIDs are fetched together so a crowded world event costs a
 * handful of requests instead of one per player.
 */
public final class TitleCache {
	private static final long FRESH_MS = 5 * 60_000;
	private static final long RETRY_AFTER_ERROR_MS = 30_000;
	private static final int FLUSH_EVERY_TICKS = 10;
	private static final int MAX_BATCH = 500;

	private record Entry(Title title, long expiresAt) {}

	private final ApiClient api;
	private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
	private final Set<UUID> queued = ConcurrentHashMap.newKeySet();
	private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
	private int ticks;

	public TitleCache(ApiClient api) {
		this.api = api;
	}

	/** Returns the cached title (possibly stale) or null, queueing a fetch if needed. Cheap enough to call per frame. */
	public Title get(UUID uuid) {
		Entry entry = entries.get(uuid);
		if ((entry == null || entry.expiresAt() < System.currentTimeMillis()) && !inFlight.contains(uuid)) {
			queued.add(uuid);
		}
		return entry == null ? null : entry.title();
	}

	/** Stores a title we already know, such as our own right after equipping it. */
	public void put(UUID uuid, Title title) {
		entries.put(uuid, new Entry(title, System.currentTimeMillis() + FRESH_MS));
	}

	public void clear() {
		entries.clear();
	}

	/** Call once per client tick. */
	public void tick() {
		if (++ticks < FLUSH_EVERY_TICKS && queued.size() < MAX_BATCH / 2) {
			return;
		}
		ticks = 0;
		while (!queued.isEmpty()) {
			flush();
		}
	}

	private void flush() {
		List<UUID> batch = new ArrayList<>();
		for (UUID uuid : queued) {
			if (batch.size() >= MAX_BATCH) {
				break;
			}
			batch.add(uuid);
		}
		batch.forEach(queued::remove);
		inFlight.addAll(batch);

		api.titles(batch).whenComplete((titles, error) -> {
			long now = System.currentTimeMillis();
			if (error != null) {
				WynnTitles.LOGGER.debug("Title lookup failed", error);
				// Keep showing what we had and try again later instead of every frame.
				for (UUID uuid : batch) {
					Entry old = entries.get(uuid);
					entries.put(uuid, new Entry(old == null ? null : old.title(), now + RETRY_AFTER_ERROR_MS));
				}
			} else {
				for (UUID uuid : batch) {
					entries.put(uuid, new Entry(titles.get(uuid), now + FRESH_MS));
				}
			}
			batch.forEach(inFlight::remove);
		});
	}
}
