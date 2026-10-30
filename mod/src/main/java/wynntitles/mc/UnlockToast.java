package wynntitles.mc;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import wynntitles.catalog.Catalog;
import wynntitles.mc.screen.UnlockToastArt;

/**
 * Announces an unlocked achievement, or a batch of them as one summary, in the
 * screens' Wynncraft style; the look is in UnlockToastArt.
 */
public class UnlockToast implements Toast {
	private static final long DISPLAY_MS = 5000;

	private final List<Catalog.Achievement> unlocked;
	private Visibility visibility = Visibility.SHOW;

	public UnlockToast(Catalog.Achievement achievement) {
		this(List.of(achievement));
	}

	public UnlockToast(List<Catalog.Achievement> unlocked) {
		this.unlocked = List.copyOf(unlocked);
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
		return UnlockToastArt.width(Mc.client().font, unlocked);
	}

	@Override
	public void render(GuiGraphics graphics, Font font, long elapsedMs) {
		UnlockToastArt.draw(graphics, font, unlocked, width(), height());
	}
}
