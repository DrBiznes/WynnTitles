package wynntitles.mc;

import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import wynntitles.WynnTitles;
import wynntitles.account.Account;

/** The Minecraft side of the login handshake: proves we own this account to Mojang. */
public final class MojangSession implements Account.MinecraftSession {
	@Override
	public UUID uuid() {
		return Mc.client().getUser().getProfileId();
	}

	@Override
	public String username() {
		return Mc.client().getUser().getName();
	}

	@Override
	public void joinServer(String serverId) throws Exception {
		try {
			Mc.client().services().sessionService()
					.joinServer(uuid(), Mc.client().getUser().getAccessToken(), serverId);
		} catch (Exception e) {
			// Dev clients have no real Mojang session. A local server started with
			// WT_OFFLINE_AUTH=true skips the Mojang check, so carry on.
			if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
				WynnTitles.LOGGER.warn("joinServer failed in dev; continuing for offline auth: {}", e.getMessage());
				return;
			}
			throw e;
		}
	}
}
