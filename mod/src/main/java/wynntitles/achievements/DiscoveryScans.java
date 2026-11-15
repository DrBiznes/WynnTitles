package wynntitles.achievements;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import wynntitles.WynnTitles;

/**
 * When to scan the Content Book, and the filter to put back afterwards. A scan
 * opens the book several times and Wynntils leaves it on the last filter it used,
 * so a character is scanned once and the places it found are kept on disk by
 * VisitedTerritories. It is scanned again on a later launch only if the Wynncraft
 * API counts a different number of discoveries than it did at the last scan, or
 * when the player asks. A scan that has left nothing to read is retried.
 */
final class DiscoveryScans {
	private static final long RETRY_MS = 60_000;
	private static final int ATTEMPTS = 3;
	private static final Pattern ACTIVE_FILTER = Pattern.compile("^§f- §7(.*)$");

	private static final Gson GSON = new Gson();
	/** Saved for a scan made before the API's count was known, so the next launch scans again. */
	private static final int UNKNOWN = -1;

	private final Path file;
	/** The API's discovery count at each character's last scan, kept on disk. */
	private final Map<String, Integer> scanned;
	/** Scans started this launch. */
	private final Map<String, Integer> scanAttempts = new HashMap<>();
	/** Characters whose saved scan was found current or renewed this launch, so they are left alone until the next. */
	private final Set<String> settled = new HashSet<>();
	private final Set<String> requested = new HashSet<>();
	/** Discoveries seen in chat this launch, as character and place, so a repeated message counts once. */
	private final Set<String> found = new HashSet<>();
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

	/** Keeps nothing on disk. */
	DiscoveryScans() {
		this(null);
	}

	DiscoveryScans(Path file) {
		this.file = file;
		this.scanned = read(file);
	}

	/**
	 * Whether to scan now, which counts as an attempt if so. apiCount is the
	 * character's discovery count from the Wynncraft API, or null while it is not
	 * known. It is compared once per launch: the count rises as the character
	 * plays, and following it would scan in the middle of a session.
	 */
	synchronized boolean scanDue(String character, long now, boolean hasResults, Integer apiCount) {
		Integer attempts = scanAttempts.get(character);
		if (attempts != null) {
			if (hasResults) {
				finished(character, apiCount);
				return false;
			}
			if (attempts >= ATTEMPTS || now - scannedAt < RETRY_MS) {
				return false;
			}
		} else if (!requested.contains(character) && current(character, apiCount)) {
			return false;
		}
		requested.remove(character);
		scanAttempts.merge(character, 1, Integer::sum);
		scannedAt = now;
		return true;
	}

	/** Scans the character again at the next check, whatever is saved for it. */
	synchronized void requestScan(String character) {
		requested.add(character);
		scanAttempts.remove(character);
		settled.remove(character);
	}

	/**
	 * A discovery seen in chat, which VisitedTerritories keeps. The saved count
	 * goes up with it, so the API's count still matches at the next launch and
	 * the character is not scanned for a place already known.
	 */
	synchronized void discoveryFound(String character, String place) {
		Integer saved = scanned.get(character);
		if (found.add(character + "/" + place) && saved != null && saved != UNKNOWN) {
			save(character, saved + 1);
		}
	}

	/** Whether the saved scan still stands. With no count yet, it stands until one arrives. */
	private boolean current(String character, Integer apiCount) {
		if (settled.contains(character)) {
			return true;
		}
		Integer saved = scanned.get(character);
		if (saved == null || saved == UNKNOWN) {
			return false;
		}
		if (apiCount == null) {
			return true;
		}
		if (!saved.equals(apiCount)) {
			return false;
		}
		settled.add(character);
		return true;
	}

	/**
	 * Saves the count a finished scan goes with. The first known count is kept:
	 * a later one may include discoveries made since the scan.
	 */
	private void finished(String character, Integer apiCount) {
		if (settled.contains(character)) {
			return;
		}
		if (apiCount != null) {
			settled.add(character);
			save(character, apiCount);
		} else if (!Integer.valueOf(UNKNOWN).equals(scanned.get(character))) {
			save(character, UNKNOWN);
		}
	}

	private void save(String character, int count) {
		scanned.put(character, count);
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(scanned));
		} catch (IOException e) {
			WynnTitles.LOGGER.warn("Could not save discovery scans", e);
		}
	}

	private static Map<String, Integer> read(Path file) {
		try {
			if (file != null && Files.exists(file)) {
				Map<String, Integer> read =
						GSON.fromJson(Files.readString(file), new TypeToken<Map<String, Integer>>() {}.getType());
				if (read != null) {
					Map<String, Integer> copy = new HashMap<>(read);
					copy.values().removeIf(count -> count == null);
					return copy;
				}
			}
		} catch (IOException | RuntimeException e) {
			WynnTitles.LOGGER.warn("Ignoring unreadable discovery scans", e);
		}
		return new HashMap<>();
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
