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
	private static final float TITLE_SCALE = 0.8f;
	private static final String NAMETAG_CLASS =
			"com.wynntils.features.players.CustomNametagRendererFeature$CustomNametag";

	private static final MethodHandle NEW_NAMETAG = findConstructor();

	private Nametags() {}

	/**
	 * Called after Wynntils has filled in its nametag lines (bottom to top). Our
	 * title goes on top. When Wynntils added nothing, it will let vanilla draw
	 * the name, so we add the name ourselves to make Wynntils draw the stack.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void addTitle(PlayerNametagRenderEvent event, List nametags) {
		if (NEW_NAMETAG == null || !WynnTitlesConfig.get().showTitles) {
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

		try {
			if (nametags.isEmpty()) {
				nametags.add(NEW_NAMETAG.invoke(event.getEntityRenderState().nameTag, 1f));
			}
			Component text = TitleRenderer.render(title);
			nametags.add(NEW_NAMETAG.invoke(text, TITLE_SCALE));
		} catch (Throwable t) {
			WynnTitles.LOGGER.error("Could not add title nametag", t);
		}
	}

	private static MethodHandle findConstructor() {
		try {
			Class<?> nametag = Class.forName(NAMETAG_CLASS);
			MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(nametag, MethodHandles.lookup());
			return lookup.findConstructor(nametag, MethodType.methodType(void.class, Component.class, float.class));
		} catch (ReflectiveOperationException | RuntimeException e) {
			WynnTitles.LOGGER.error("Wynntils changed its nametag internals; titles will not render", e);
			return null;
		}
	}
}
