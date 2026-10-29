package wynntitles.mc.screen;

import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import wynntitles.mc.Mc;
import wynntitles.mc.screen.ui.Element;
import wynntitles.mc.screen.ui.Size;

/** Draws a Component that is rebuilt every frame, so animated title effects move. */
public class TitleText extends Element {
	private final Supplier<Component> text;
	private final float scale;
	private final int color;
	private boolean centered;

	public TitleText(Supplier<Component> text, float scale, int color) {
		this.text = text;
		this.scale = scale;
		this.color = color;
		sizing(Size.content(), Size.content());
	}

	/** Fills the parent's width and centres the text in it. */
	public TitleText centered() {
		this.centered = true;
		sizing(Size.fill(), Size.content());
		return this;
	}

	@Override
	protected int contentWidth() {
		return Math.round(font().width(text.get()) * scale);
	}

	@Override
	protected int contentHeight() {
		return Math.round(font().lineHeight * scale);
	}

	@Override
	public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
		Component current = text.get();
		int offset = centered ? Math.round((width - font().width(current) * scale) / 2) : 0;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + offset, y);
		graphics.pose().scale(scale, scale);
		// Since 1.21.6 text colors are ARGB; without an alpha byte the text is invisible.
		graphics.drawString(font(), current, 0, 0, 0xFF000000 | color, false);
		graphics.pose().popMatrix();
	}

	private static Font font() {
		return Mc.client().font;
	}
}
