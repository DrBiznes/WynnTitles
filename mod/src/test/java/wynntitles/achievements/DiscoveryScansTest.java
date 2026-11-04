package wynntitles.achievements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DiscoveryScansTest {
	@Test
	void eachCharacterScansOnce() {
		DiscoveryScans scans = new DiscoveryScans();

		assertTrue(scans.scanDue("archer", 0, false));
		assertFalse(scans.scanDue("archer", 3_600_000, true), "a world hop does not scan again");
		assertTrue(scans.scanDue("mage", 3_600_000, true));
	}

	@Test
	void savedResultsDoNotSkipTheFirstScan() {
		assertTrue(new DiscoveryScans().scanDue("archer", 0, true));
	}

	@Test
	void twoDetectorsOnOneTickScanOnce() {
		DiscoveryScans scans = new DiscoveryScans();

		assertTrue(scans.scanDue("archer", 0, false));
		assertFalse(scans.scanDue("archer", 0, false));
	}

	@Test
	void scanWithNoResultsIsRetriedThenGivenUp() {
		DiscoveryScans scans = new DiscoveryScans();

		assertTrue(scans.scanDue("archer", 0, false));
		assertFalse(scans.scanDue("archer", 59_999, false));
		assertTrue(scans.scanDue("archer", 60_000, false));
		assertTrue(scans.scanDue("archer", 120_000, false));
		assertFalse(scans.scanDue("archer", 180_000, false));
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
