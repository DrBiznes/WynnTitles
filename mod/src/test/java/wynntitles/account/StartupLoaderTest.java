package wynntitles.account;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import wynntitles.account.StartupLoader.Status;

class StartupLoaderTest {
	private final AtomicLong clock = new AtomicLong(1_000);
	private final AtomicInteger attempts = new AtomicInteger();
	private final AtomicBoolean onWynncraft = new AtomicBoolean(true);
	private final List<Status> statuses = new ArrayList<>();
	private volatile boolean serverUp;

	private final StartupLoader loader = new StartupLoader(
			() -> {
				attempts.incrementAndGet();
				return serverUp
						? CompletableFuture.completedFuture(null)
						: CompletableFuture.failedFuture(new RuntimeException("down"));
			},
			onWynncraft::get,
			statuses::add,
			clock::get);

	private void waitFor(long ms) {
		clock.addAndGet(ms);
		loader.tick();
	}

	@Test
	void aWorkingServerConnectsOnceQuietly() {
		serverUp = true;
		loader.start();
		loader.start();
		waitFor(600_000);

		assertEquals(1, attempts.get());
		assertEquals(List.of(), statuses);
	}

	@Test
	void retriesAfterEachDelayAndSaysWhenItRecovers() {
		loader.start();
		assertEquals(List.of(Status.RETRYING), statuses);

		waitFor(StartupLoader.RETRY_DELAYS_MS[0] - 1);
		assertEquals(1, attempts.get(), "not due yet");
		waitFor(1);
		assertEquals(2, attempts.get());

		serverUp = true;
		waitFor(StartupLoader.RETRY_DELAYS_MS[1]);
		assertEquals(3, attempts.get());
		assertEquals(List.of(Status.RETRYING, Status.RECOVERED), statuses);

		waitFor(600_000);
		assertEquals(3, attempts.get(), "connected, so no more retries");
	}

	@Test
	void givesUpAfterTheLastRetryAndTheNextJoinStartsOver() {
		loader.start();
		for (long delay : StartupLoader.RETRY_DELAYS_MS) {
			waitFor(delay);
		}
		assertEquals(1 + StartupLoader.RETRY_DELAYS_MS.length, attempts.get());
		assertEquals(List.of(Status.RETRYING, Status.GAVE_UP), statuses);

		waitFor(600_000);
		assertEquals(1 + StartupLoader.RETRY_DELAYS_MS.length, attempts.get(), "gave up");

		serverUp = true;
		loader.start();
		assertEquals(2 + StartupLoader.RETRY_DELAYS_MS.length, attempts.get());
	}

	@Test
	void stopsRetryingAfterLeavingWynncraft() {
		loader.start();
		onWynncraft.set(false);
		waitFor(600_000);
		assertEquals(1, attempts.get());

		onWynncraft.set(true);
		loader.start();
		assertEquals(2, attempts.get(), "leaving cancels the retries, so the next join connects again");
	}

	@Test
	void anAttemptFromBeforeAResetIsIgnored() {
		CompletableFuture<Object> slow = new CompletableFuture<>();
		List<Status> seen = new ArrayList<>();
		StartupLoader slowLoader = new StartupLoader(() -> slow, () -> true, seen::add, clock::get);
		slowLoader.start();
		slowLoader.reset();
		slow.completeExceptionally(new RuntimeException("down"));

		assertEquals(List.of(), seen);
	}
}
