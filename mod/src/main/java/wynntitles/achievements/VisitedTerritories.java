package wynntitles.achievements;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import wynntitles.WynnTitles;

/**
 * Places each character has been to, for territorySet triggers, by Wynntils
 * character ID. They come from the Content Book's discoveries and from the
 * territory the character stands in, which also counts below a discovery's
 * level requirement, so they are kept on disk.
 */
final class VisitedTerritories {
	private static final Gson GSON = new Gson();
	/** Content Book names that differ from the Wynncraft territory name. */
	private static final Map<String, String> ALIASES = Map.of("town of lutho", "lutho");

	private final Path file;
	private final Map<String, Set<String>> visited;

	VisitedTerritories(Path file) {
		this.file = file;
		this.visited = read(file);
	}

	/** The form names are compared in: ignoring case, with a discovery and its territory the same. */
	static String key(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		return ALIASES.getOrDefault(lower, lower);
	}

	synchronized boolean contains(String character, String territory) {
		return visited.getOrDefault(character, Set.of()).contains(key(territory));
	}

	synchronized void add(String character, String territory) {
		addAll(character, Set.of(territory));
	}

	synchronized void addAll(String character, Collection<String> territories) {
		Set<String> names = visited.computeIfAbsent(character, c -> new HashSet<>());
		boolean changed = false;
		for (String territory : territories) {
			changed |= names.add(key(territory));
		}
		if (changed) {
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
					read.forEach((character, names) -> copy.put(character, new HashSet<>(names)));
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
