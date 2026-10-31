package wynntitles.mixin;

import com.wynntils.mc.event.PlayerNametagRenderEvent;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wynntitles.mc.Nametags;

/**
 * Pseudo, by name, with the full descriptor and require = 0, so a Wynntils update
 * that moves or changes this private method loses the titles instead of crashing
 * the game. Nametags.hookPresent reports it when that happens.
 */
@Pseudo
@Mixin(targets = "com.wynntils.features.players.CustomNametagRendererFeature", remap = false)
public class WynntilsNametagMixin {
	@SuppressWarnings("rawtypes")
	@Inject(
			method = "addAccountTypeNametag(Lcom/wynntils/mc/event/PlayerNametagRenderEvent;Ljava/util/List;)V",
			at = @At("RETURN"),
			require = 0)
	private void wynntitles$addTitle(PlayerNametagRenderEvent event, List nametags, CallbackInfo ci) {
		Nametags.addTitle(event, nametags);
	}
}
