package wynntitles.mc.screen.ui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Text in one color, wrapped onto more lines if it is wider than its maximum width. */
public class Label extends Element {
	private static final int LINE_SPACING = 2;

	private Component text;
	private final int color;
	private int maxWidth = Integer.MAX_VALUE;
	private List<FormattedCharSequence> lines = List.of();

	/** The color is RGB; the text's own colors win over it. */
	public Label(Component text, int color) {
		this.text = text;
		this.color = color;
	}

	public Label text(Component text) {
		this.text = text;
		return this;
	}

	public Label maxWidth(int maxWidth) {
		this.maxWidth = maxWidth;
		return this;
	}

	@Override
	public Label margins(Insets margins) {
		super.margins(margins);
		return this;
	}

	@Override
	protected int contentWidth() {
		lines = font().split(text, maxWidth);
		int widest = 0;
		for (FormattedCharSequence line : lines) {
			widest = Math.max(widest, font().width(line));
		}
		return widest;
	}

	@Override
	protected int contentHeight() {
		return Math.max(0, lines.size() * (font().lineHeight + LINE_SPACING) - LINE_SPACING);
	}

	@Override
	protected void measure(int spaceWidth, int spaceHeight) {
		if (!widthSize().isContent()) {
			// Wrap to the width the label was given rather than its own.
			lines = font().split(text, Math.min(maxWidth, widthSize().resolve(spaceWidth - margins().horizontal())));
		}
		super.measure(spaceWidth, spaceHeight);
	}

	@Override
	public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
		// One device pixel down sits text a little better in its box.
		graphics.pose().pushMatrix();
		graphics.pose().translate(0, UiScreen.devicePixel());
		for (int i = 0; i < lines.size(); i++) {
			// Since 1.21.6 text colors are ARGB; without an alpha byte the text is invisible.
			graphics.drawString(font(), lines.get(i), x, y + i * (font().lineHeight + LINE_SPACING),
					0xFF000000 | color, false);
		}
		graphics.pose().popMatrix();
	}

	private static Font font() {
		return Minecraft.getInstance().font;
	}
}
