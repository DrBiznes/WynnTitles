package wynntitles.mc;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import wynntitles.catalog.Catalog;
import wynntitles.mc.screen.UnlockToastArt;

/** Announces an unlocked achievement in the screens' Wynncraft style; the look is in UnlockToastArt. */
public class UnlockToast implements Toast {
	private static final long DISPLAY_MS = 5000;

	private final Catalog.Achievement achievement;
	private Visibility visibility = Visibility.SHOW;

	public UnlockToast(Catalog.Achievement achievement) {
		this.achievement = achievement;
	}

	@Override
	public Visibility getWantedVisibility() {
		return visibility;
	}

	@Override
	public void update(ToastManager manager, long elapsedMs) {
		visibility = elapsedMs >= DISPLAY_MS * manager.getNotificationDisplayTimeMultiplier()
				? Visibility.HIDE
				: Visibility.SHOW;
	}

	@Override
	public int width() {
		return UnlockToastArt.width(Mc.client().font, achievement);
	}

	@Override
	public void render(GuiGraphics graphics, Font font, long elapsedMs) {
		UnlockToastArt.draw(graphics, font, achievement, width(), height());
	}
}
