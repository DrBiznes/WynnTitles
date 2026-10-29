package wynntitles.mc.screen;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import wynntitles.mc.Mc;
import wynntitles.mc.WynnFonts;
import wynntitles.mc.screen.ui.Element;
import wynntitles.mc.screen.ui.Size;
import wynntitles.mc.screen.ui.Tween;
import wynntitles.mc.screen.ui.UiScreen;

/**
 * The stone tab bar: tab names in frames carved into the bar, split by the
 * Wynncraft eye, which sits in the middle whatever the tabs' widths. The open
 * tab's gold-rimmed frame glides over to whichever tab is picked next.
 */
final class TabBar extends Element {
	private static final int HEIGHT = 18;
	private static final int TAB_HEIGHT = 14;
	private static final int EYE_WIDTH = 14;
	private static final int EYE_HEIGHT = 8;
	private static final int GAP = 10;
	/** Room either side of a tab's name, as in the frame sprite. */
	private static final int TAB_LEFT = 7;
	private static final int TAB_RIGHT = 6;
	private static final int TEXT_TOP = 3;

	private final List<Component> labels;
	private final int leftCount;
	private final IntSupplier selected;
	private final IntConsumer onSelect;
	private final int[] tabX;
	private final int[] tabWidth;
	private final Tween glide = new Tween(160);
	private int eyeX;
	private int glideFrom = -1;
	private int shown = -1;

	/** The first leftCount tabs go left of the eye, the rest right of it. */
	TabBar(List<String> names, int leftCount, IntSupplier selected, IntConsumer onSelect) {
		this.labels = names.stream().map(n -> (Component) WynnFonts.small(n)).toList();
		this.leftCount = leftCount;
		this.selected = selected;
		this.onSelect = onSelect;
		this.tabX = new int[names.size()];
		this.tabWidth = new int[names.size()];
		sizing(Size.fill(), Size.fixed(HEIGHT));
	}

	@Override
	protected void place(int x, int y) {
		super.place(x, y);
		Font font = Mc.client().font;
		for (int i = 0; i < labels.size(); i++) {
			tabWidth[i] = TAB_LEFT + font.width(labels.get(i)) + TAB_RIGHT;
		}
		int side = (width - EYE_WIDTH - 2 * GAP) / 2;
		eyeX = x + side + GAP;
		// Left tabs end at the left half's edge; right tabs start at the right half's.
		int cursor = x + side;
		for (int i = leftCount - 1; i >= 0; i--) {
			cursor -= tabWidth[i];
			tabX[i] = cursor;
			cursor -= GAP;
		}
		cursor = eyeX + EYE_WIDTH + GAP;
		for (int i = leftCount; i < labels.size(); i++) {
			tabX[i] = cursor;
			cursor += tabWidth[i] + GAP;
		}
	}

	@Override
	public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
		int current = selected.getAsInt();
		if (current != shown) {
			// The first frame has nowhere to glide from.
			glideFrom = shown;
			shown = current;
			if (glideFrom >= 0) {
				glide.restart();
			}
		}
		int tabY = y + (HEIGHT - TAB_HEIGHT) / 2;
		WynnUi.blit(graphics, WynnUi.BAR, x, y, width, height);
		WynnUi.blit(graphics, WynnUi.EYE, eyeX, y + (HEIGHT - EYE_HEIGHT) / 2, EYE_WIDTH, EYE_HEIGHT);
		int hoveredTab = hovered() ? tabAt(mouseX, mouseY) : -1;
		for (int i = 0; i < labels.size(); i++) {
			WynnUi.blit(graphics, i == hoveredTab && i != current ? WynnUi.TAB_HOVER : WynnUi.TAB,
					tabX[i], tabY, tabWidth[i], TAB_HEIGHT);
		}

		float t = glide.running() && glideFrom >= 0 ? glide.value() : 1;
		int from = glideFrom >= 0 ? glideFrom : current;
		int frameX = Math.round(Tween.lerp(tabX[from], tabX[current], t));
		int frameWidth = Math.round(Tween.lerp(tabWidth[from], tabWidth[current], t));
		WynnUi.blit(graphics, WynnUi.TAB_SELECTED, frameX, tabY, frameWidth, TAB_HEIGHT);

		Font font = Mc.client().font;
		graphics.pose().pushMatrix();
		graphics.pose().translate(0, UiScreen.devicePixel());
		for (int i = 0; i < labels.size(); i++) {
			// The name turns gold once the frame has arrived.
			boolean gold = i == current && t > 0.5f;
			graphics.drawString(font, labels.get(i), tabX[i] + TAB_LEFT, tabY + TEXT_TOP,
					0xFF000000 | (gold ? WynnUi.GOLD : WynnUi.TEXT), false);
		}
		graphics.pose().popMatrix();
	}

	private int tabAt(double mouseX, double mouseY) {
		int tabY = y + (HEIGHT - TAB_HEIGHT) / 2;
		if (mouseY < tabY || mouseY >= tabY + TAB_HEIGHT) {
			return -1;
		}
		for (int i = 0; i < labels.size(); i++) {
			if (mouseX >= tabX[i] && mouseX < tabX[i] + tabWidth[i]) {
				return i;
			}
		}
		return -1;
	}

	@Override
	protected boolean click(double mouseX, double mouseY) {
		int tab = tabAt(mouseX, mouseY);
		if (tab < 0) {
			return false;
		}
		onSelect.accept(tab);
		return true;
	}

	@Override
	protected boolean clickable(double mouseX, double mouseY) {
		return tabAt(mouseX, mouseY) >= 0;
	}
}
