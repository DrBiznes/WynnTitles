package wynntitles.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;
import wynntitles.WynnTitles;
import wynntitles.api.ApiClient;
import wynntitles.api.ApiException;
import wynntitles.api.ApiTypes.Me;
import wynntitles.api.ApiTypes.Session;

/**
 * Owns the session token and the player's profile. Logging in needs no setup:
 * the Mojang handshake proves account ownership, and the token is cached on
 * disk so the handshake only happens when the token expires.
 */
public final class Account {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Abstracts the Minecraft session so this class stays free of game code. */
	public interface MinecraftSession {
		UUID uuid();

		String username();

		/** Calls Mojang's joinServer with the given serverId. Blocking. */
		void joinServer(String serverId) throws Exception;
	}

	private record StoredSession(String uuid, String token, long expiresAt) {}

	private final ApiClient api;
	private final MinecraftSession minecraft;
	private final Path sessionFile;
	private final List<Consumer<Me>> listeners = new ArrayList<>();

	private volatile StoredSession session;
	private volatile Me me;
	private CompletableFuture<String> pendingLogin;

	public Account(ApiClient api, MinecraftSession minecraft, Path sessionFile) {
		this.api = api;
		this.minecraft = minecraft;
		this.sessionFile = sessionFile;
		this.session = readSession();
	}

	/** The last profile loaded from the server, or null before the first load. */
	public Me me() {
		return me;
	}

	/** Runs on whatever thread the response arrives on. */
	public void onChange(Consumer<Me> listener) {
		listeners.add(listener);
	}

	public CompletableFuture<Me> refresh() {
		return withToken(api::me).thenApply(this::update);
	}

	public CompletableFuture<Me> setTitle(String prefix, String suffix) {
		return withToken(token -> api.setTitle(token, prefix, suffix)).thenApply(this::update);
	}

	/** Runs an authenticated call, logging in first if needed and once more if the token was rejected. */
	public <T> CompletableFuture<T> withToken(Function<String, CompletableFuture<T>> call) {
		return token().thenCompose(call).exceptionallyCompose(t -> {
			if (unwrap(t) instanceof ApiException e && e.unauthorized()) {
				forgetSession();
				return token().thenCompose(call);
			}
			return CompletableFuture.failedFuture(t);
		});
	}

	/** Forces a fresh Mojang handshake, e.g. from /wt login. */
	public CompletableFuture<Me> relogin() {
		forgetSession();
		return refresh();
	}

	private synchronized CompletableFuture<String> token() {
		StoredSession current = session;
		if (current != null
				&& current.uuid().equals(minecraft.uuid().toString())
				&& current.expiresAt() > System.currentTimeMillis() + 60_000) {
			return CompletableFuture.completedFuture(current.token());
		}
		if (pendingLogin == null || pendingLogin.isDone()) {
			pendingLogin = login();
		}
		return pendingLogin;
	}

	private CompletableFuture<String> login() {
		String username = minecraft.username();
		return api.challenge(username)
				.thenApplyAsync(serverId -> {
					try {
						minecraft.joinServer(serverId);
					} catch (Exception e) {
						throw new CompletionException(new ApiException(
								"Mojang rejected the login. Restart your launcher and try again.", e));
					}
					return serverId;
				})
				.thenCompose(serverId -> api.verify(username, serverId))
				.thenApply(this::storeSession);
	}

	private synchronized String storeSession(Session s) {
		session = new StoredSession(s.uuid(), s.token(), s.expiresAt());
		try {
			Files.createDirectories(sessionFile.getParent());
			Files.writeString(sessionFile, GSON.toJson(session));
		} catch (IOException e) {
			WynnTitles.LOGGER.warn("Could not save WynnTitles session", e);
		}
		WynnTitles.LOGGER.info("Logged in to WynnTitles as {}", s.username());
		return s.token();
	}

	private synchronized void forgetSession() {
		session = null;
		try {
			Files.deleteIfExists(sessionFile);
		} catch (IOException e) {
			WynnTitles.LOGGER.warn("Could not delete WynnTitles session", e);
		}
	}

	private StoredSession readSession() {
		try {
			return Files.exists(sessionFile) ? GSON.fromJson(Files.readString(sessionFile), StoredSession.class) : null;
		} catch (IOException | RuntimeException e) {
			WynnTitles.LOGGER.warn("Ignoring unreadable WynnTitles session file", e);
			return null;
		}
	}

	private Me update(Me updated) {
		me = updated;
		for (Consumer<Me> listener : listeners) {
			listener.accept(updated);
		}
		return updated;
	}

	private static Throwable unwrap(Throwable t) {
		while (t instanceof CompletionException && t.getCause() != null) {
			t = t.getCause();
		}
		return t;
	}
}
