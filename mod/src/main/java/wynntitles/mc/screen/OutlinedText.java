package wynntitles.mc.screen;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import wynntitles.mc.Mc;

/** Text with a one-pixel outline all round, as on Wynncraft's ribbons and buttons. */
public class OutlinedText extends BaseUIComponent {
	private final Component text;
	private final int color;
	private final int outline;

	public OutlinedText(Component text, int color, int outline) {
		this.text = text;
		this.color = color;
		this.outline = outline;
		this.sizing(Sizing.content(), Sizing.content());
	}

	@Override
	protected int determineHorizontalContentSize(Sizing sizing) {
		return font().width(text) + 2;
	}

	@Override
	protected int determineVerticalContentSize(Sizing sizing) {
		return font().lineHeight + 1;
	}

	@Override
	public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
		// Colours are ARGB since 1.21.6; without an alpha byte the text is invisible.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx != 0 || dy != 0) {
					graphics.drawString(font(), text, x + 1 + dx, y + 1 + dy, 0xFF000000 | outline, false);
				}
			}
		}
		graphics.drawString(font(), text, x + 1, y + 1, 0xFF000000 | color, false);
	}

	private static Font font() {
		return Mc.client().font;
	}
}
