package wynntitles.achievements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscoveryScansTest {
	@Test
	void eachCharacterScansOnce() {
		DiscoveryScans scans = new DiscoveryScans();

		assertTrue(scans.scanDue("archer", 0, false, null));
		assertFalse(scans.scanDue("archer", 3_600_000, true, null), "a world hop does not scan again");
		assertTrue(scans.scanDue("mage", 3_600_000, true, null));
	}

	@Test
	void savedResultsDoNotSkipTheFirstScan() {
		assertTrue(new DiscoveryScans().scanDue("archer", 0, true, null));
	}

	@Test
	void twoDetectorsOnOneTickScanOnce() {
		DiscoveryScans scans = new DiscoveryScans();

		assertTrue(scans.scanDue("archer", 0, false, null));
		assertFalse(scans.scanDue("archer", 0, false, null));
	}

	@Test
	void scanWithNoResultsIsRetriedThenGivenUp() {
		DiscoveryScans scans = new DiscoveryScans();

		assertTrue(scans.scanDue("archer", 0, false, null));
		assertFalse(scans.scanDue("archer", 59_999, false, null));
		assertTrue(scans.scanDue("archer", 60_000, false, null));
		assertTrue(scans.scanDue("archer", 120_000, false, null));
		assertFalse(scans.scanDue("archer", 180_000, false, null));
	}

	@Test
	void savedScanIsKeptWhileTheApiCountMatches(@TempDir Path dir) {
		Path file = dir.resolve("scans.json");
		DiscoveryScans first = new DiscoveryScans(file);
		assertTrue(first.scanDue("archer", 0, false, 40));
		assertFalse(first.scanDue("archer", 1_000, true, 40));

		DiscoveryScans next = new DiscoveryScans(file);
		assertFalse(next.scanDue("archer", 0, false, null), "waits for the API's count");
		assertFalse(next.scanDue("archer", 1_000, false, 40));
		assertFalse(next.scanDue("archer", 2_000, false, 41), "compared once per launch");
		assertTrue(next.scanDue("mage", 2_000, false, 12), "a character never scanned");
	}

	@Test
	void changedApiCountScansAgain(@TempDir Path dir) {
		Path file = dir.resolve("scans.json");
		DiscoveryScans first = new DiscoveryScans(file);
		first.scanDue("archer", 0, false, 40);
		first.scanDue("archer", 1_000, true, 40);
		first.scanDue("archer", 2_000, true, 43);

		DiscoveryScans next = new DiscoveryScans(file);
		assertTrue(next.scanDue("archer", 0, false, 43), "the count from the scan is kept, not a later one");
		assertFalse(next.scanDue("archer", 1_000, true, 43));
		assertFalse(new DiscoveryScans(file).scanDue("archer", 0, false, 43));
	}

	@Test
	void scanWithoutAnApiCountIsRepeatedNextLaunch(@TempDir Path dir) {
		Path file = dir.resolve("scans.json");
		DiscoveryScans hidden = new DiscoveryScans(file);
		hidden.scanDue("archer", 0, false, null);
		hidden.scanDue("archer", 1_000, true, null);
		assertTrue(new DiscoveryScans(file).scanDue("archer", 0, false, 40));

		DiscoveryScans late = new DiscoveryScans(file);
		late.scanDue("archer", 0, false, null);
		late.scanDue("archer", 1_000, true, null);
		late.scanDue("archer", 2_000, true, 40);
		assertFalse(new DiscoveryScans(file).scanDue("archer", 0, false, 40), "the count arrived after the scan");
	}

	@Test
	void requestedScanIgnoresTheSavedOne(@TempDir Path dir) {
		Path file = dir.resolve("scans.json");
		DiscoveryScans scans = new DiscoveryScans(file);
		scans.scanDue("archer", 0, false, 40);
		scans.scanDue("archer", 1_000, true, 40);

		scans.requestScan("archer");
		assertTrue(scans.scanDue("archer", 2_000, true, 40));
		assertFalse(scans.scanDue("archer", 3_000, true, 40));
	}

	@Test
	void discoveriesSeenInChatKeepTheSavedScanCurrent(@TempDir Path dir) {
		Path file = dir.resolve("scans.json");
		DiscoveryScans scans = new DiscoveryScans(file);
		scans.scanDue("archer", 0, false, 40);
		scans.scanDue("archer", 1_000, true, 40);
		scans.discoveryFound("archer", "katoa ranch");
		scans.discoveryFound("archer", "katoa ranch");
		scans.discoveryFound("archer", "detlas suburbs");
		scans.discoveryFound("mage", "katoa ranch");

		assertFalse(new DiscoveryScans(file).scanDue("archer", 0, false, 42));
		assertTrue(new DiscoveryScans(file).scanDue("archer", 0, false, 43), "one was found without the mod");
		assertTrue(new DiscoveryScans(file).scanDue("mage", 0, false, 1), "never scanned");
	}

	@Test
	void readsTheActiveFilter() {
		assertEquals("Recommended", DiscoveryScans.activeFilter(
				List.of("§7Filter the content", "§7- §8Quests", "§f- §7Recommended", "§7- §8Dungeons")));
		assertNull(DiscoveryScans.activeFilter(List.of("§7- §8Quests")));
	}

	@Test
	void retryKeepsTheFilterFromBeforeTheFirstScan() {
		DiscoveryScans scans = new DiscoveryScans();
		scans.saveFilter("Recommended");
		scans.saveFilter("World Discoveries");

		assertEquals("Recommended", scans.savedFilter());
	}

	@Test
	void restoredFilterIsForgotten() {
		DiscoveryScans scans = new DiscoveryScans();
		scans.saveFilter("Recommended");
		scans.filterRestored();
		scans.saveFilter("Quests");

		assertEquals("Quests", scans.savedFilter());
	}

	@Test
	void failedRestoreIsRetriedThenGivenUp() {
		DiscoveryScans scans = new DiscoveryScans();
		scans.saveFilter("Recommended");
		assertFalse(scans.restoreDue(0), "nothing has failed");

		scans.restoreFailed(0);
		assertFalse(scans.restoreDue(59_999));
		assertTrue(scans.restoreDue(60_000));
		assertFalse(scans.restoreDue(60_001), "one retry per failure");

		scans.restoreFailed(60_000);
		assertEquals("Recommended", scans.savedFilter());
		scans.restoreFailed(120_000);
		assertNull(scans.savedFilter());
		assertFalse(scans.restoreDue(300_000));
	}
}
