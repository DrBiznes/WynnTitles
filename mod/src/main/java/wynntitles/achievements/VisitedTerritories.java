package wynntitles.achievements;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import wynntitles.WynnTitles;

/**
 * Territories each player has stood in, for territorySet triggers. Neither
 * Wynntils nor the API remembers visits, so they are kept on disk, by player
 * UUID so alts sharing an install keep their own.
 */
final class VisitedTerritories {
	private static final Gson GSON = new Gson();

	private final Path file;
	private final Map<String, Set<String>> visited;

	VisitedTerritories(Path file) {
		this.file = file;
		this.visited = read(file);
	}

	/** Names are compared ignoring case. */
	synchronized boolean contains(String player, String territory) {
		return visited.getOrDefault(player, Set.of()).contains(territory.toLowerCase(Locale.ROOT));
	}

	synchronized void add(String player, String territory) {
		if (visited.computeIfAbsent(player, p -> new HashSet<>()).add(territory.toLowerCase(Locale.ROOT))) {
			write();
		}
	}

	private static Map<String, Set<String>> read(Path file) {
		try {
			if (file != null && Files.exists(file)) {
				Map<String, Set<String>> read = GSON.fromJson(
						Files.readString(file), new TypeToken<Map<String, Set<String>>>() {}.getType());
				if (read != null) {
					Map<String, Set<String>> copy = new HashMap<>();
					read.forEach((player, names) -> copy.put(player, new HashSet<>(names)));
					return copy;
				}
			}
		} catch (IOException | RuntimeException e) {
			WynnTitles.LOGGER.warn("Ignoring unreadable visited territories", e);
		}
		return new HashMap<>();
	}

	private void write() {
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(visited));
		} catch (IOException e) {
			WynnTitles.LOGGER.warn("Could not save visited territories", e);
		}
	}
}
