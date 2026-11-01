package wynntitles.achievements;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * When to scan the Content Book, and the filter to put back afterwards. A scan
 * opens the book several times and Wynntils leaves it on the last filter it used,
 * so each character is scanned once per game launch, plus retries while a scan
 * has left nothing to read.
 */
final class DiscoveryScans {
	private static final long RETRY_MS = 60_000;
	private static final int ATTEMPTS = 3;
	private static final Pattern ACTIVE_FILTER = Pattern.compile("^§f- §7(.*)$");

	private final Map<String, Integer> scanAttempts = new HashMap<>();
	private long scannedAt;
	private String savedFilter;
	private int restoreFailures;
	private long restoreAt = -1;

	/** The active filter from the lore of the book's Filter item, or null if it has none. */
	static String activeFilter(List<String> lore) {
		for (String line : lore) {
			Matcher filter = ACTIVE_FILTER.matcher(line);
			if (filter.matches()) {
				return filter.group(1);
			}
		}
		return null;
	}

	/** Whether to scan now, which counts as an attempt if so. */
	synchronized boolean scanDue(String character, long now, boolean hasResults) {
		Integer attempts = scanAttempts.get(character);
		if (attempts != null && (hasResults || attempts >= ATTEMPTS || now - scannedAt < RETRY_MS)) {
			return false;
		}
		scanAttempts.merge(character, 1, Integer::sum);
		scannedAt = now;
		return true;
	}

	/** Keeps the first filter seen: a retry finds the book on the filter the last scan left. */
	synchronized void saveFilter(String filter) {
		if (savedFilter == null) {
			savedFilter = filter;
		}
	}

	synchronized String savedFilter() {
		return savedFilter;
	}

	synchronized void filterRestored() {
		savedFilter = null;
		restoreFailures = 0;
		restoreAt = -1;
	}

	/** A menu opened by the player or a world change can stop a restore, so it is tried again. */
	synchronized void restoreFailed(long now) {
		if (++restoreFailures >= ATTEMPTS) {
			filterRestored();
		} else {
			restoreAt = now + RETRY_MS;
		}
	}

	/** Whether a failed restore should be tried again now. */
	synchronized boolean restoreDue(long now) {
		if (restoreAt < 0 || now < restoreAt) {
			return false;
		}
		restoreAt = -1;
		return true;
	}
}
