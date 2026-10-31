package wynntitles.mc;

import com.wynntils.core.components.Models;
import com.wynntils.mc.event.PlayerNametagRenderEvent;
import com.wynntils.mc.extension.EntityRenderStateExtension;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import wynntitles.WynnTitles;
import wynntitles.config.WynnTitlesConfig;
import wynntitles.titles.Title;

/**
 * Adds a title line to the nametag stack Wynntils' CustomNametagRendererFeature
 * draws. This hooks a private Wynntils method (see WynntilsNametagMixin), so
 * it is the first thing to re-check after a Wynntils update.
 */
public final class Nametags {
	private static final String FEATURE_CLASS = "com.wynntils.features.players.CustomNametagRendererFeature";
	private static final String HOOKED_METHOD = "addAccountTypeNametag";
	private static final String NAMETAG_CLASS = FEATURE_CLASS + "$CustomNametag";

	private static final MethodHandle NEW_NAMETAG = findConstructor();

	/** Set by the first failure; a broken hook would otherwise log every frame. */
	private static boolean failed;

	private Nametags() {}

	/**
	 * Called after Wynntils has filled in its nametag lines (bottom to top). Our
	 * title goes on top. When Wynntils added nothing, it will let vanilla draw
	 * the name, so we add the name ourselves to make Wynntils draw the stack.
	 */
	@SuppressWarnings("rawtypes")
	public static void addTitle(PlayerNametagRenderEvent event, List nametags) {
		if (failed) {
			return;
		}
		try {
			add(event, nametags);
		} catch (Throwable t) {
			// This runs inside rendering, where anything thrown crashes the game,
			// including a linkage error from a changed Wynntils class.
			failed = true;
			WynnTitles.LOGGER.error("Could not add title nametag; titles are off until the next launch", t);
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void add(PlayerNametagRenderEvent event, List nametags) throws Throwable {
		if (NEW_NAMETAG == null || !titlesVisible()) {
			return;
		}
		Entity entity = ((EntityRenderStateExtension) event.getEntityRenderState()).getEntity();
		if (!(entity instanceof AbstractClientPlayer player) || Models.Player.isNpc(player)) {
			return;
		}

		UUID uuid = WynnTitlesConfig.get().previewOwnTitleOnEveryone
				? Mc.client().getUser().getProfileId()
				: player.getUUID();
		Title title = WynnTitles.titles().get(uuid);
		if (title == null) {
			return;
		}

		if (nametags.isEmpty()) {
			nametags.add(NEW_NAMETAG.invoke(event.getEntityRenderState().nameTag, 1f));
		}
		Component text = TitleRenderer.render(title);
		nametags.add(NEW_NAMETAG.invoke(text, WynnTitlesConfig.get().titleScale / 100f));
	}

	/**
	 * Reads Wynntils' current raid rather than following its raid events: it
	 * clears the raid when one is interrupted too, which posts no end event.
	 */
	private static boolean titlesVisible() {
		WynnTitlesConfig config = WynnTitlesConfig.get();
		return config.showTitles && !(config.hideTitlesInRaids && Models.Raid.getCurrentRaid() != null);
	}

	private static MethodHandle findConstructor() {
		try {
			Class<?> feature = Class.forName(FEATURE_CLASS);
			feature.getDeclaredMethod(HOOKED_METHOD, PlayerNametagRenderEvent.class, List.class);
			Class<?> nametag = Class.forName(NAMETAG_CLASS);
			MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(nametag, MethodHandles.lookup());
			return lookup.findConstructor(nametag, MethodType.methodType(void.class, Component.class, float.class));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			WynnTitles.LOGGER.error("Wynntils changed its nametag internals; titles will not render", e);
			return null;
		}
	}

	/** Loads this class's checks at startup, so a broken hook is in the log even with nobody around. */
	public static boolean hookPresent() {
		return NEW_NAMETAG != null;
	}
}
