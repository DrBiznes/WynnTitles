package wynntitles.catalog;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import wynntitles.WynnTitles;
import wynntitles.api.ApiClient;

/**
 * Keeps the achievement catalog from the server, cached on disk so achievements
 * keep tracking while the server is unreachable.
 */
public final class CatalogStore {
	private static final Gson GSON = new Gson();

	private record Cached(String etag, Catalog catalog) {}

	private final ApiClient api;
	private final Path cacheFile;
	private final List<Consumer<Catalog>> listeners = new ArrayList<>();
	private volatile Cached cached;

	public CatalogStore(ApiClient api, Path cacheFile) {
		this.api = api;
		this.cacheFile = cacheFile;
		this.cached = readCache();
	}

	public Catalog catalog() {
		Cached c = cached;
		return c == null ? null : c.catalog();
	}

	/** Runs on whatever thread the catalog arrives on. */
	public void onChange(Consumer<Catalog> listener) {
		listeners.add(listener);
	}

	public CompletableFuture<Catalog> refresh() {
		Cached current = cached;
		return api.catalog(current == null ? null : current.etag()).thenApply(resp -> {
			if (resp.catalog() == null) {
				return current.catalog();
			}
			cached = new Cached(resp.etag(), resp.catalog());
			writeCache();
			WynnTitles.LOGGER.info("Loaded catalog {} ({} achievements)",
					resp.catalog().version(), resp.catalog().achievements().size());
			listeners.forEach(l -> l.accept(resp.catalog()));
			return resp.catalog();
		});
	}

	private Cached readCache() {
		try {
			return Files.exists(cacheFile) ? GSON.fromJson(Files.readString(cacheFile), Cached.class) : null;
		} catch (IOException | RuntimeException e) {
			WynnTitles.LOGGER.warn("Ignoring unreadable catalog cache", e);
			return null;
		}
	}

	private void writeCache() {
		try {
			Files.createDirectories(cacheFile.getParent());
			Files.writeString(cacheFile, GSON.toJson(cached));
		} catch (IOException e) {
			WynnTitles.LOGGER.warn("Could not cache catalog", e);
		}
	}
}
