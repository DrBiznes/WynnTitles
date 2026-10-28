package wynntitles.gametest.mixin;

import com.wynntils.core.WynntilsMod;
import com.wynntils.core.events.MixinHelper;
import com.wynntils.mc.event.PlayerNametagRenderEvent;
import net.neoforged.bus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wynntils only posts its events while connected to Wynncraft. For the smoke
 * test, let just the player nametag event through in singleplayer so the real
 * Wynntils nametag feature (and our hook into it) runs. Everything else in
 * Wynntils still believes it is offline.
 */
@Mixin(value = MixinHelper.class, remap = false)
public class ForwardNametagEventsMixin {
	@Inject(method = "post", at = @At("HEAD"), cancellable = true)
	private static void wynntitlesTest$forwardNametags(Event event, CallbackInfo ci) {
		if (event instanceof PlayerNametagRenderEvent) {
			WynntilsMod.postEvent(event);
			ci.cancel();
		}
	}
}
