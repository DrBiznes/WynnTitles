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

	public TitleText(Supplier<Component> text, float scale, int color) {
		this.text = text;
		this.scale = scale;
		this.color = color;
		this.sizing(Sizing.content(), Sizing.content());
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
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		// Since 1.21.6 text colors are ARGB; without an alpha byte the text is invisible.
		graphics.drawString(font(), text.get(), 0, 0, 0xFF000000 | color, false);
		graphics.pose().popMatrix();
	}

	private static Font font() {
		return Mc.client().font;
	}
}
