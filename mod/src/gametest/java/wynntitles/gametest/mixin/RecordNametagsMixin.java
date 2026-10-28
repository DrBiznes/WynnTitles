package wynntitles.gametest.mixin;

import com.wynntils.features.players.CustomNametagRendererFeature;
import com.wynntils.mc.event.PlayerNametagRenderEvent;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wynntitles.gametest.NametagProbe;

/** Records every nametag stack Wynntils is about to draw, so the test can assert on it. */
@Mixin(value = CustomNametagRendererFeature.class, remap = false)
public class RecordNametagsMixin {
	@SuppressWarnings("rawtypes")
	@Inject(method = "drawNametags", at = @At("HEAD"))
	private void wynntitlesTest$record(PlayerNametagRenderEvent event, List nametags, CallbackInfo ci) {
		NametagProbe.record(nametags);
	}
}
