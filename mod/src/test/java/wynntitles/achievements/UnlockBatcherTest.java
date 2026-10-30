package wynntitles.achievements;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import wynntitles.catalog.Catalog;

class UnlockBatcherTest {
	private final AtomicLong now = new AtomicLong(1_000);
	private final List<List<String>> batches = new ArrayList<>();
	private final UnlockBatcher batcher = new UnlockBatcher(
			batch -> batches.add(batch.stream().map(Catalog.Achievement::id).toList()), now::get);

	@Test
	void burstIsAnnouncedOnceItGoesQuiet() {
		batcher.add(achievement("a"));
		batcher.add(achievement("b"));
		advance(UnlockBatcher.QUIET_MS - 1);
		batcher.tick();
		assertEquals(List.of(), batches, "still inside the quiet gap");

		batcher.add(achievement("c"));
		advance(UnlockBatcher.QUIET_MS);
		batcher.tick();
		assertEquals(List.of(List.of("a", "b", "c")), batches);

		batcher.tick();
		assertEquals(1, batches.size(), "an empty batch is not announced");
	}

	@Test
	void steadyTrickleIsAnnouncedAfterTheMaxHold() {
		for (int i = 0; now.get() - 1_000 < UnlockBatcher.MAX_HOLD_MS; i++) {
			batcher.add(achievement("t" + i));
			advance(UnlockBatcher.QUIET_MS / 2);
			batcher.tick();
		}
		assertEquals(1, batches.size());
		assertEquals(10, batches.get(0).size());
	}

	@Test
	void laterUnlocksStartANewBatch() {
		batcher.add(achievement("a"));
		advance(UnlockBatcher.QUIET_MS);
		batcher.tick();
		batcher.add(achievement("b"));
		advance(UnlockBatcher.QUIET_MS);
		batcher.tick();
		assertEquals(List.of(List.of("a"), List.of("b")), batches);
	}

	private void advance(long ms) {
		now.addAndGet(ms);
	}

	private static Catalog.Achievement achievement(String id) {
		return new Catalog.Achievement(id, "category", "Name", "", "bronze", 1, null, null, null);
	}
}
