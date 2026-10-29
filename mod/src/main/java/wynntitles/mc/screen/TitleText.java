package wynntitles.mc.screen;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import wynntitles.mc.Mc;

/**
 * Draws a Component that is rebuilt every frame, so animated title effects
 * move. owo's LabelComponent re-lays out on every text change, which this avoids.
 */
public class TitleText extends BaseUIComponent {
	private final Supplier<Component> text;
	private final float scale;
	private final int color;
	private boolean centered;

	public TitleText(Supplier<Component> text, float scale, int color) {
		this.text = text;
		this.scale = scale;
		this.color = color;
		this.sizing(Sizing.content(), Sizing.content());
	}

	/**
	 * Fills the parent's width and centres the text in it every frame. A
	 * content-sized one keeps the width it was laid out with, so it drifts off
	 * centre when the text changes length.
	 */
	public TitleText centered() {
		this.centered = true;
		this.sizing(Sizing.fill(), Sizing.content());
		return this;
	}

	@Override
	protected int determineHorizontalContentSize(Sizing sizing) {
		return Math.round(font().width(text.get()) * scale);
	}

	@Override
	protected int determineVerticalContentSize(Sizing sizing) {
		return Math.round(font().lineHeight * scale);
	}

	@Override
	public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
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
