package wynntitles.mc.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import wynntitles.WynnTitles;
import wynntitles.catalog.Catalog;
import wynntitles.mc.Icons;
import wynntitles.mc.screen.ui.Element;
import wynntitles.mc.screen.ui.Size;

/**
 * An achievement's item inside its hexagonal tier frame, dimmed while locked.
 * The frames come from scripts/draw-icon-frames.py; keep the sizes in step.
 */
public class AchievementIcon extends Element {
	public static final int WIDTH = 28;
	public static final int HEIGHT = 32;
	private static final int ITEM_X = 6;
	private static final int ITEM_Y = 7;
	private static final Identifier LOCKED_SHADE = sprite("locked_shade");

	private final Catalog.Achievement achievement;
	private final boolean unlocked;

	public AchievementIcon(Catalog.Achievement achievement, boolean unlocked) {
		this.achievement = achievement;
		this.unlocked = unlocked;
		sizing(Size.fixed(WIDTH), Size.fixed(HEIGHT));
	}

	@Override
	public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
		draw(graphics, achievement, unlocked, x, y);
	}

	/** Also used by the unlock toast. */
	public static void draw(GuiGraphics graphics, Catalog.Achievement achievement, boolean unlocked, int x, int y) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, frame(achievement.tier()), x, y, WIDTH, HEIGHT);
		graphics.renderItem(Icons.stack(achievement.icon()), x + ITEM_X, y + ITEM_Y);
		if (!unlocked) {
			// Items draw after sprites in the same stratum, so shade on the next one.
			// The shade is hex shaped, so it darkens the item without the border.
			graphics.nextStratum();
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOCKED_SHADE, x, y, WIDTH, HEIGHT);
		}
	}

	private static Identifier frame(String tier) {
		String name = switch (tier) {
			case "silver", "gold" -> tier;
			default -> "bronze";
		};
		return sprite("frame_" + name);
	}

	private static Identifier sprite(String name) {
		return Identifier.fromNamespaceAndPath(WynnTitles.MOD_ID, "icon/" + name);
	}
}
