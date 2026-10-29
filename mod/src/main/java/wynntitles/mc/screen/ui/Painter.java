package wynntitles.mc.screen.ui;

import net.minecraft.client.gui.GuiGraphics;

/** Draws an element's background, such as a sprite stretched over its bounds. */
@FunctionalInterface
public interface Painter {
	void paint(GuiGraphics graphics, Element element);
}
