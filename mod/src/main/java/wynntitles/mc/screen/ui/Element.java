package wynntitles.mc.screen.ui;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Something on a {@link UiScreen}: it works out its size, is placed by its
 * parent, and draws itself. Coordinates are in the screen's own pixels, which
 * the screen scales to fit the window.
 *
 * <p>The whole tree is laid out again every frame, so changing an element's
 * children or text needs no invalidation, and text that changes length (an
 * animated title, say) is always sized right.
 */
public abstract class Element {
	protected int x;
	protected int y;
	protected int width;
	protected int height;
	private Size widthSize = Size.content();
	private Size heightSize = Size.content();
	private Insets margins = Insets.NONE;
	private Component tooltip;
	private Runnable onClick;
	boolean hovered;
	boolean pressed;

	public Element sizing(Size width, Size height) {
		this.widthSize = width;
		this.heightSize = height;
		return this;
	}

	public Element margins(Insets margins) {
		this.margins = margins;
		return this;
	}

	public Element tooltip(Component tooltip) {
		this.tooltip = tooltip;
		return this;
	}

	/** Runs on a left click, and shows the hand cursor while hovered. */
	public Element onClick(Runnable onClick) {
		this.onClick = onClick;
		return this;
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public Size widthSize() {
		return widthSize;
	}

	public Size heightSize() {
		return heightSize;
	}

	public Insets margins() {
		return margins;
	}

	/** Whether the mouse is over this element, and not hidden from it by a scroll view's edge. */
	public boolean hovered() {
		return hovered;
	}

	/** Whether the left button went down on this element and is still held. */
	public boolean pressed() {
		return pressed;
	}

	int fullWidth() {
		return width + margins.horizontal();
	}

	int fullHeight() {
		return height + margins.vertical();
	}

	/** The width this element wants when content-sized. */
	protected int contentWidth() {
		return 0;
	}

	/** The height this element wants when content-sized, once its width is known. */
	protected int contentHeight() {
		return 0;
	}

	/** Works out this element's size from the space its parent offers, which includes its margins. */
	protected void measure(int spaceWidth, int spaceHeight) {
		width = widthSize.isContent() ? contentWidth() : widthSize.resolve(spaceWidth - margins.horizontal());
		height = heightSize.isContent() ? contentHeight() : heightSize.resolve(spaceHeight - margins.vertical());
	}

	/** Moves this element's top left corner, inside its margins, to x, y. */
	protected void place(int x, int y) {
		this.x = x;
		this.y = y;
	}

	/** The mouse position is in the screen's pixels. */
	public abstract void draw(GuiGraphics graphics, int mouseX, int mouseY);

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	/** Adds this element, then whichever child is under the point, which is inside this element. */
	protected void collect(double mouseX, double mouseY, List<Element> path) {
		path.add(this);
	}

	/** Handles a left click at a point inside this element; true if it used it. */
	protected boolean click(double mouseX, double mouseY) {
		if (onClick == null) {
			return false;
		}
		onClick.run();
		return true;
	}

	/** Whether a click at this point would do something, for the hand cursor. */
	protected boolean clickable(double mouseX, double mouseY) {
		return onClick != null;
	}

	protected Component tooltip(double mouseX, double mouseY) {
		return tooltip;
	}
}
