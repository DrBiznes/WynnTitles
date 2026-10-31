package wynntitles.account;

import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import wynntitles.WynnTitles;
import wynntitles.api.ApiException;

/**
 * Connects to the WynnTitles server once per launch, on the first Wynncraft
 * join. A failed attempt is retried a few times while the player stays on
 * Wynncraft, and the player is told how it went, since nothing is tracked until
 * it works.
 */
public final class StartupLoader {
	public enum Status {
		/** The first attempt failed; retries are scheduled. */
		RETRYING,
		/** A retry worked after a RETRYING notice. */
		RECOVERED,
		/** Every retry failed. The next Wynncraft join starts over. */
		GAVE_UP
	}

	/** The wait before each retry. */
	static final long[] RETRY_DELAYS_MS = {30_000, 60_000, 120_000};

	private final Supplier<CompletableFuture<?>> load;
	private final BooleanSupplier onWynncraft;
	private final Consumer<Status> onStatus;
	private final LongSupplier clock;

	private boolean started;
	private int failures;
	/** When the next retry is due, or 0 if none is. */
	private long retryAt;
	/** Bumped by reset, so an attempt still running then is ignored. */
	private int generation;

	public StartupLoader(Supplier<CompletableFuture<?>> load, BooleanSupplier onWynncraft, Consumer<Status> onStatus) {
		this(load, onWynncraft, onStatus, System::currentTimeMillis);
	}

	StartupLoader(
			Supplier<CompletableFuture<?>> load,
			BooleanSupplier onWynncraft,
			Consumer<Status> onStatus,
			LongSupplier clock) {
		this.load = load;
		this.onWynncraft = onWynncraft;
		this.onStatus = onStatus;
		this.clock = clock;
	}

	/** Starts connecting unless that already happened or is underway. */
	public void start() {
		int attempt;
		synchronized (this) {
			if (started) {
				return;
			}
			started = true;
			attempt = generation;
		}
		attempt(attempt);
	}

	/** Forgets any progress, so the next start connects from scratch. */
	public synchronized void reset() {
		started = false;
		failures = 0;
		retryAt = 0;
		generation++;
	}

	/** Call once per client tick; runs a retry when one is due. */
	public void tick() {
		int attempt;
		synchronized (this) {
			if (retryAt == 0 || clock.getAsLong() < retryAt) {
				return;
			}
			if (!onWynncraft.getAsBoolean()) {
				reset();
				return;
			}
			retryAt = 0;
			attempt = generation;
		}
		attempt(attempt);
	}

	private void attempt(int attempt) {
		CompletableFuture<?> result;
		try {
			result = load.get();
		} catch (RuntimeException e) {
			result = CompletableFuture.failedFuture(e);
		}
		result.whenComplete((value, error) -> finished(attempt, error));
	}

	private void finished(int attempt, Throwable error) {
		Status status = null;
		synchronized (this) {
			if (attempt != generation) {
				return;
			}
			if (error == null) {
				if (failures > 0) {
					status = Status.RECOVERED;
				}
				failures = 0;
			} else {
				WynnTitles.LOGGER.warn("Could not connect to WynnTitles: {}", ApiException.describe(error));
				if (failures == RETRY_DELAYS_MS.length) {
					status = Status.GAVE_UP;
					started = false;
					failures = 0;
				} else {
					if (failures == 0) {
						status = Status.RETRYING;
					}
					retryAt = clock.getAsLong() + RETRY_DELAYS_MS[failures];
					failures++;
				}
			}
		}
		if (status != null) {
			onStatus.accept(status);
		}
	}
}
