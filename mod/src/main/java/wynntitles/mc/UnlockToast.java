package wynntitles.mc;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import wynntitles.catalog.Catalog;
import wynntitles.mc.screen.AchievementIcon;

/** The vanilla advancement toast, with the achievement's framed icon and Wynncraft's fonts. */
public class UnlockToast implements Toast {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/advancement");
	private static final long DISPLAY_MS = 5000;
	private static final int TITLE_COLOR = 0xFFE8C050;
	private static final int NAME_COLOR = 0xFFFFFFFF;

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
	public void render(GuiGraphics graphics, Font font, long elapsedMs) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, width(), height());
		// The hex is as tall as the toast; only its top and bottom points touch the edge.
		AchievementIcon.draw(graphics, achievement, true, 3, (height() - AchievementIcon.HEIGHT) / 2);
		int textX = 3 + AchievementIcon.WIDTH + 4;
		graphics.drawString(font, WynnFonts.small("Achievement unlocked"), textX, 7, TITLE_COLOR, false);
		String name = achievement.name();
		while (!name.isEmpty() && font.width(WynnFonts.body(name)) > width() - textX - 4) {
			name = name.substring(0, name.length() - 1);
		}
		graphics.drawString(font, WynnFonts.body(name), textX, 18, NAME_COLOR, false);
	}
}
