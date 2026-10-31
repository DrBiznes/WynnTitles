package wynntitles.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import wynntitles.api.ApiTypes.AchievementStats;
import wynntitles.api.ApiTypes.Claim;
import wynntitles.api.ApiTypes.ClaimResult;
import wynntitles.api.ApiTypes.ClaimResults;
import wynntitles.api.ApiTypes.Me;
import wynntitles.api.ApiTypes.Session;
import wynntitles.catalog.Catalog;
import wynntitles.titles.Title;
import wynntitles.titles.TitleStyle;

/**
 * Thin, stateless wrapper over the WynnTitles HTTP API. Every call is async and
 * completes off the client thread; callers hop back with Minecraft#execute.
 */
public final class ApiClient {
	private static final Gson GSON = new Gson();
	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final Supplier<String> baseUrl;
	private final String userAgent;

	public ApiClient(Supplier<String> baseUrl, String modVersion) {
		this.baseUrl = baseUrl;
		this.userAgent = "WynnTitles/" + modVersion;
	}

	/** A catalog response. catalog is null when the server answered 304 Not Modified. */
	public record CatalogResponse(Catalog catalog, String etag) {}

	public CompletableFuture<CatalogResponse> catalog(String etag) {
		HttpRequest.Builder req = request("/v1/catalog").GET();
		if (etag != null) {
			req.header("If-None-Match", etag);
		}
		return send(req.build()).thenApply(resp -> {
			String newEtag = resp.headers().firstValue("ETag").orElse(etag);
			if (resp.statusCode() == 304) {
				return new CatalogResponse(null, newEtag);
			}
			return new CatalogResponse(parse(resp, Catalog.class), newEtag);
		});
	}

	public CompletableFuture<AchievementStats> achievementStats() {
		return send(request("/v1/stats/achievements").GET().build())
				.thenApply(resp -> parse(resp, AchievementStats.class));
	}

	public CompletableFuture<String> challenge(String username) {
		JsonObject body = new JsonObject();
		body.addProperty("username", username);
		return post("/v1/auth/challenge", null, body)
				.thenApply(resp -> parse(resp, JsonObject.class).get("serverId").getAsString());
	}

	public CompletableFuture<Session> verify(String username, String serverId) {
		JsonObject body = new JsonObject();
		body.addProperty("username", username);
		body.addProperty("serverId", serverId);
		return post("/v1/auth/verify", null, body).thenApply(resp -> parse(resp, Session.class));
	}

	public CompletableFuture<Me> me(String token) {
		return send(request("/v1/me").header("Authorization", "Bearer " + token).GET().build())
				.thenApply(resp -> parse(resp, Me.class));
	}

	/** Equips the given words; null unequips that slot. Returns the updated profile. */
	public CompletableFuture<Me> setTitle(String token, String prefix, String suffix) {
		JsonObject body = new JsonObject();
		body.addProperty("prefix", prefix);
		body.addProperty("suffix", suffix);
		HttpRequest req = request("/v1/me/title")
				.header("Authorization", "Bearer " + token)
				.header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
				.build();
		return send(req).thenApply(resp -> parse(resp, Me.class));
	}

	/** Restyles each slot; null goes back to the word's native style. Returns the updated profile. */
	public CompletableFuture<Me> setStyle(String token, TitleStyle prefix, TitleStyle suffix) {
		JsonObject body = new JsonObject();
		body.add("prefix", GSON.toJsonTree(prefix));
		body.add("suffix", GSON.toJsonTree(suffix));
		HttpRequest req = request("/v1/me/style")
				.header("Authorization", "Bearer " + token)
				.header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
				.build();
		return send(req).thenApply(resp -> parse(resp, Me.class));
	}

	/** Buys every item or none of them. Returns the updated profile. */
	public CompletableFuture<Me> buy(String token, List<String> itemIds) {
		JsonObject body = new JsonObject();
		body.add("itemIds", GSON.toJsonTree(itemIds));
		return post("/v1/shop/buy", token, body).thenApply(resp -> parse(resp, Me.class));
	}

	/** Claims up to 200 achievements in one request. Returns a result for each, in order. */
	public CompletableFuture<List<ClaimResult>> claims(String token, List<Claim> claims, String modVersion) {
		JsonObject body = new JsonObject();
		body.add("claims", GSON.toJsonTree(claims));
		body.addProperty("modVersion", modVersion);
		return post("/v1/claims", token, body).thenApply(resp -> parse(resp, ClaimResults.class).results());
	}

	/** Looks up titles for up to 500 players. Every requested UUID is present; null means no title. */
	public CompletableFuture<Map<UUID, Title>> titles(List<UUID> uuids) {
		JsonObject body = new JsonObject();
		body.add("uuids", GSON.toJsonTree(uuids.stream().map(UUID::toString).toList()));
		return post("/v1/titles/batch", null, body).thenApply(resp -> {
			JsonObject titles = parse(resp, JsonObject.class).getAsJsonObject("titles");
			Map<UUID, Title> result = new HashMap<>();
			for (Map.Entry<String, JsonElement> entry : titles.entrySet()) {
				Title title = entry.getValue().isJsonNull() ? null : GSON.fromJson(entry.getValue(), Title.class);
				result.put(UUID.fromString(entry.getKey()), title);
			}
			return result;
		});
	}

	private CompletableFuture<HttpResponse<String>> post(String path, String token, JsonObject body) {
		HttpRequest.Builder req = request(path)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
		if (token != null) {
			req.header("Authorization", "Bearer " + token);
		}
		return send(req.build());
	}

	private HttpRequest.Builder request(String path) {
		String base = baseUrl.get();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		return HttpRequest.newBuilder(URI.create(base + path)).timeout(TIMEOUT).header("User-Agent", userAgent);
	}

	private CompletableFuture<HttpResponse<String>> send(HttpRequest req) {
		return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
				.exceptionally(t -> {
					throw new ApiException("Could not reach the WynnTitles server", t);
				})
				.thenApply(resp -> {
					int status = resp.statusCode();
					if (status >= 400) {
						throw new ApiException(status, errorMessage(resp));
					}
					return resp;
				});
	}

	private static <T> T parse(HttpResponse<String> resp, Class<T> type) {
		return GSON.fromJson(resp.body(), TypeToken.get(type).getType());
	}

	private static String errorMessage(HttpResponse<String> resp) {
		try {
			JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
			if (body.has("error")) {
				return body.get("error").getAsString();
			}
		} catch (RuntimeException ignored) {
			// Not our JSON (e.g. a reverse proxy error page); fall through.
		}
		return "Server returned HTTP " + resp.statusCode();
	}
}
