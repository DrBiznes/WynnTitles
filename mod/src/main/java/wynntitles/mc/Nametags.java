package wynntitles.mc;

import com.wynntils.core.components.Managers;
import com.wynntils.core.components.Models;
import com.wynntils.core.persisted.config.Config;
import com.wynntils.features.players.CustomNametagRendererFeature;
import com.wynntils.mc.event.GetCameraEntityEvent;
import com.wynntils.mc.event.PlayerNametagRenderEvent;
import com.wynntils.mc.extension.EntityRenderStateExtension;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import wynntitles.WynnTitles;
import wynntitles.config.TitlePosition;
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
	/** Wynntils' "Show Own Nametag" option, looked up by its field name. */
	private static final String SHOW_OWN_NAMETAG = "showOwnNametag";

	/** Wynntils stacks each line one line height times its scale up, which leaves the backings touching. */
	private static final float TITLE_GAP = 0.1f;

	private static final MethodHandle NEW_NAMETAG = findConstructor();

	/** Set by the first failure; a broken hook would otherwise log every frame. */
	private static boolean failed;

	/** Wynntils' Show Own Nametag config, found on first use; empty when Wynntils renamed it. */
	private static Optional<Config<?>> showOwnNametag;

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
		boolean self = player == Mc.client().player;
		if (self && !WynnTitlesConfig.get().showOwnTitle) {
			return;
		}

		UUID uuid = WynnTitlesConfig.get().previewOwnTitleOnEveryone
				? Mc.client().getUser().getProfileId()
				: player.getUUID();
		Title title = WynnTitles.titles().get(uuid);
		if (title == null) {
			return;
		}

		Object line = NEW_NAMETAG.invoke(TitleRenderer.render(title), WynnTitlesConfig.get().titleScale / 100f);
		if (self && !wynntilsShowsOwnName()) {
			// Only onCameraCheck made this nametag visible, so draw the title on its own, without the name.
			nametags.clear();
			nametags.add(line);
			return;
		}
		if (nametags.isEmpty()) {
			nametags.add(NEW_NAMETAG.invoke(event.getEntityRenderState().nameTag, 1f));
		}
		Object gap = NEW_NAMETAG.invoke(Component.empty(), TITLE_GAP);
		if (WynnTitlesConfig.get().titlePosition == TitlePosition.BELOW) {
			// The name is always the last line here, so slotting in before it puts the title right under it.
			int name = nametags.size() - 1;
			nametags.add(name, gap);
			nametags.add(name, line);
		} else {
			nametags.add(gap);
			nametags.add(line);
		}
	}

	/**
	 * Vanilla never shows the camera entity's own nametag. Wynntils posts this
	 * event from that check only, and its Show Own Nametag option answers it with
	 * no camera entity. We do the same when the player wants their own title, so
	 * the nametag event fires for them and {@link #add} can draw the title alone.
	 */
	private static final class OwnTitle {
		@SubscribeEvent
		public void onCameraCheck(GetCameraEntityEvent event) {
			if (failed || NEW_NAMETAG == null) {
				return;
			}
			try {
				if (forceOwnNametag()) {
					event.setEntity(null);
				}
			} catch (Throwable t) {
				failed = true;
				WynnTitles.LOGGER.error("Could not show own title; titles are off until the next launch", t);
			}
		}
	}

	public static Object eventListener() {
		return new OwnTitle();
	}

	/** Same screen rule as Wynntils: no menus open except chat. */
	private static boolean forceOwnNametag() {
		if (!WynnTitlesConfig.get().showOwnTitle || !titlesVisible()) {
			return false;
		}
		if (Mc.screen() != null && !(Mc.screen() instanceof ChatScreen)) {
			return false;
		}
		// Without Wynntils' renderer nothing would add the title, and vanilla would draw the bare name.
		if (!Managers.Feature.getFeatureInstance(CustomNametagRendererFeature.class).isEnabled()) {
			return false;
		}
		// With no title to show, forcing the nametag would only show the name the player turned off.
		return WynnTitles.titles().get(Mc.client().getUser().getProfileId()) != null;
	}

	private static boolean wynntilsShowsOwnName() {
		if (showOwnNametag == null) {
			showOwnNametag = Managers.Feature.getFeatureInstance(CustomNametagRendererFeature.class)
					.getConfigOptionFromString(SHOW_OWN_NAMETAG);
			if (showOwnNametag.isEmpty()) {
				WynnTitles.LOGGER.warn("Wynntils has no {} option; own titles show without the name", SHOW_OWN_NAMETAG);
			}
		}
		return showOwnNametag.map(c -> Boolean.TRUE.equals(c.get())).orElse(false);
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
