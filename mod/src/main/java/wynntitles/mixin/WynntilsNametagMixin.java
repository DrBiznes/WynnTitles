package wynntitles.mixin;

import com.wynntils.features.players.CustomNametagRendererFeature;
import com.wynntils.mc.event.PlayerNametagRenderEvent;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wynntitles.mc.Nametags;

@Mixin(value = CustomNametagRendererFeature.class, remap = false)
public class WynntilsNametagMixin {
	@SuppressWarnings("rawtypes")
	@Inject(method = "addAccountTypeNametag", at = @At("RETURN"))
	private void wynntitles$addTitle(PlayerNametagRenderEvent event, List nametags, CallbackInfo ci) {
		Nametags.addTitle(event, nametags);
	}
}
