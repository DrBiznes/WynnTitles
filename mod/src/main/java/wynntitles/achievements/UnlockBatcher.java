package wynntitles.achievements;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import wynntitles.catalog.Catalog;

/**
 * Holds unlocks until they stop arriving, then hands them over together. On a
 * first login (or a catalog that gained achievements the player already meets)
 * the tracker claims dozens at once, and Wynntils loads its data in stages, so
 * they come in bursts over the first minute; announcing each one would bury the
 * player in toasts. A lone unlock in normal play waits only QUIET_MS.
 */
public final class UnlockBatcher {
	/** How long no new unlock must arrive before the batch is announced. */
	static final long QUIET_MS = 2_000;
	/** Announce anyway after this long, so a steady trickle is not held forever. */
	static final long MAX_HOLD_MS = 10_000;

	private final Consumer<List<Catalog.Achievement>> onBatch;
	private final LongSupplier clock;
	private final List<Catalog.Achievement> pending = new ArrayList<>();
	private long firstAt;
	private long lastAt;

	public UnlockBatcher(Consumer<List<Catalog.Achievement>> onBatch) {
		this(onBatch, System::currentTimeMillis);
	}

	UnlockBatcher(Consumer<List<Catalog.Achievement>> onBatch, LongSupplier clock) {
		this.onBatch = onBatch;
		this.clock = clock;
	}

	/** Safe to call from HTTP callbacks. */
	public synchronized void add(Catalog.Achievement achievement) {
		long now = clock.getAsLong();
		if (pending.isEmpty()) {
			firstAt = now;
		}
		lastAt = now;
		pending.add(achievement);
	}

	/** Call once per client tick; announces the batch once it has gone quiet. */
	public void tick() {
		List<Catalog.Achievement> batch;
		synchronized (this) {
			long now = clock.getAsLong();
			if (pending.isEmpty() || (now - lastAt < QUIET_MS && now - firstAt < MAX_HOLD_MS)) {
				return;
			}
			batch = List.copyOf(pending);
			pending.clear();
		}
		onBatch.accept(batch);
	}
}
