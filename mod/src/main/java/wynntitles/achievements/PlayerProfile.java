package wynntitles.achievements;

import com.wynntils.core.components.Models;
import com.wynntils.models.players.type.wynnplayer.WynnPlayerInfo;
import wynntitles.mc.Mc;

/**
 * The player's full Wynncraft API profile (every character included), shared
 * by the detectors that read it and fetched again at most every few minutes, so
 * any number of locked achievements cost one request per refresh. The API lags
 * the game by a few minutes, and fields the player hides stay empty.
 */
final class PlayerProfile {
	private static final long REFRESH_MS = 5 * 60_000;

	private volatile WynnPlayerInfo info;
	private long lastFetch;

	/** The last profile received, or null before the first; asks for a fresh one when due. */
	WynnPlayerInfo get() {
		long now = System.currentTimeMillis();
		if (now - lastFetch >= REFRESH_MS && Mc.client().player != null) {
			lastFetch = now;
			Models.Player.getPlayerFullInfo(Mc.client().player.getStringUUID()).thenAccept(fresh -> {
				if (fresh != null) {
					info = fresh;
				}
			});
		}
		return info;
	}
}
