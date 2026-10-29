package wynntitles.mc.screen.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Lays its children out in a row, a column, or left to right wrapping onto new
 * lines like text, with a gap between them. Alignment moves the children as a
 * group along the main axis and each child on its own across it.
 */
public class Box extends Element {
	public enum Direction {
		ROW,
		COLUMN,
		WRAP
	}

	private final Direction direction;
	private final List<Element> children = new ArrayList<>();
	private int gap;
	private Insets padding = Insets.NONE;
	private Align alignX = Align.START;
	private Align alignY = Align.START;
	private Painter background;
	private boolean clip;
	private int shiftX;
	private int contentWidth;
	private int contentHeight;
	/** Where WRAP put each child, relative to the padding's corner. */
	private int[] wrapX = new int[0];
	private int[] wrapY = new int[0];

	public Box(Direction direction, Size width, Size height) {
		this.direction = direction;
		sizing(width, height);
	}

	public static Box row(Size width, Size height) {
		return new Box(Direction.ROW, width, height);
	}

	public static Box column(Size width, Size height) {
		return new Box(Direction.COLUMN, width, height);
	}

	/** Wraps like text; needs a width that does not depend on its content. */
	public static Box wrap(Size width, Size height) {
		return new Box(Direction.WRAP, width, height);
	}

	public Box child(Element child) {
		children.add(child);
		return this;
	}

	public Box clearChildren() {
		children.clear();
		return this;
	}

	public List<Element> children() {
		return Collections.unmodifiableList(children);
	}

	public Box gap(int gap) {
		this.gap = gap;
		return this;
	}

	public Box padding(Insets padding) {
		this.padding = padding;
		return this;
	}

	public Box alignX(Align align) {
		this.alignX = align;
		return this;
	}

	public Box alignY(Align align) {
		this.alignY = align;
		return this;
	}

	public Box background(Painter background) {
		this.background = background;
		return this;
	}

	/** Hides whatever of the children falls outside the padding, from the eye and the mouse. */
	public Box clip(boolean clip) {
		this.clip = clip;
		return this;
	}

	/** Moves the children sideways without changing the layout, for a page sliding in. */
	public Box shift(int dx) {
		this.shiftX = dx;
		return this;
	}

	@Override
	public Box sizing(Size width, Size height) {
		super.sizing(width, height);
		return this;
	}

	@Override
	public Box margins(Insets margins) {
		super.margins(margins);
		return this;
	}

	@Override
	public Box tooltip(Component tooltip) {
		super.tooltip(tooltip);
		return this;
	}

	@Override
	public Box onClick(Runnable onClick) {
		super.onClick(onClick);
		return this;
	}

	@Override
	protected void measure(int spaceWidth, int spaceHeight) {
		int availWidth = spaceWidth - margins().horizontal();
		int availHeight = spaceHeight - margins().vertical();
		boolean contentSizedX = widthSize().isContent();
		boolean contentSizedY = heightSize().isContent();
		int ownWidth = contentSizedX ? 0 : widthSize().resolve(availWidth);
		int ownHeight = contentSizedY ? 0 : heightSize().resolve(availHeight);
		// A content-sized box offers its children what it was offered.
		int innerWidth = (contentSizedX ? availWidth : ownWidth) - padding.horizontal();
		int innerHeight = (contentSizedY ? availHeight : ownHeight) - padding.vertical();
		if (direction == Direction.WRAP) {
			measureWrap(innerWidth, innerHeight);
		} else {
			measureLine(innerWidth, innerHeight);
		}
		width = contentSizedX ? contentWidth + padding.horizontal() : ownWidth;
		height = contentSizedY ? contentHeight + padding.vertical() : ownHeight;
	}

	private void measureLine(int innerWidth, int innerHeight) {
		boolean row = direction == Direction.ROW;
		int used = 0;
		for (Element child : children) {
			if (!expands(child)) {
				child.measure(innerWidth, innerHeight);
				used += row ? child.fullWidth() : child.fullHeight();
			}
		}
		int gaps = gap * Math.max(children.size() - 1, 0);
		int remaining = Math.max(0, (row ? innerWidth : innerHeight) - used - gaps);
		int main = gaps;
		int cross = 0;
		for (Element child : children) {
			if (expands(child)) {
				child.measure(row ? remaining : innerWidth, row ? innerHeight : remaining);
			}
			main += row ? child.fullWidth() : child.fullHeight();
			cross = Math.max(cross, row ? child.fullHeight() : child.fullWidth());
		}
		contentWidth = row ? main : cross;
		contentHeight = row ? cross : main;
	}

	private static boolean expands(Element child) {
		return child.widthSize().isExpand() || child.heightSize().isExpand();
	}

	/** Lines have no gap between them; chips are drawn with room to spare. */
	private void measureWrap(int innerWidth, int innerHeight) {
		wrapX = new int[children.size()];
		wrapY = new int[children.size()];
		int lineX = 0;
		int lineY = 0;
		int widest = 0;
		int bottom = 0;
		for (int i = 0; i < children.size(); i++) {
			Element child = children.get(i);
			child.measure(innerWidth, innerHeight);
			if (lineX > 0 && lineX + child.fullWidth() > innerWidth) {
				lineX = 0;
				lineY = bottom;
			}
			wrapX[i] = lineX + child.margins().left();
			wrapY[i] = lineY + child.margins().top();
			lineX += child.fullWidth() + gap;
			bottom = Math.max(bottom, lineY + child.fullHeight());
			widest = Math.max(widest, lineX - gap);
		}
		contentWidth = widest;
		contentHeight = bottom;
	}

	@Override
	protected void place(int x, int y) {
		super.place(x, y);
		int innerX = x + padding.left() + shiftX;
		int innerY = y + padding.top();
		int innerWidth = width - padding.horizontal();
		int innerHeight = height - padding.vertical();
		switch (direction) {
			case ROW -> {
				int cursor = alignX.offset(innerWidth - contentWidth);
				for (Element child : children) {
					child.place(innerX + cursor + child.margins().left(),
							innerY + alignY.offset(innerHeight - child.fullHeight()) + child.margins().top());
					cursor += child.fullWidth() + gap;
				}
			}
			case COLUMN -> {
				int cursor = alignY.offset(innerHeight - contentHeight);
				for (Element child : children) {
					child.place(innerX + alignX.offset(innerWidth - child.fullWidth()) + child.margins().left(),
							innerY + cursor + child.margins().top());
					cursor += child.fullHeight() + gap;
				}
			}
			case WRAP -> {
				int dx = alignX.offset(innerWidth - contentWidth);
				int dy = alignY.offset(innerHeight - contentHeight);
				for (int i = 0; i < children.size(); i++) {
					children.get(i).place(innerX + dx + wrapX[i], innerY + dy + wrapY[i]);
				}
			}
		}
	}

	@Override
	public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
		if (background != null) {
			background.paint(graphics, this);
		}
		if (clip) {
			graphics.enableScissor(x + padding.left(), y + padding.top(),
					x + width - padding.right(), y + height - padding.bottom());
		}
		for (Element child : children) {
			child.draw(graphics, mouseX, mouseY);
		}
		if (clip) {
			graphics.disableScissor();
		}
	}

	@Override
	protected void collect(double mouseX, double mouseY, List<Element> path) {
		path.add(this);
		if (clip && !(mouseX >= x + padding.left() && mouseX < x + width - padding.right()
				&& mouseY >= y + padding.top() && mouseY < y + height - padding.bottom())) {
			return;
		}
		// Later children draw on top, so they get the mouse first.
		for (int i = children.size() - 1; i >= 0; i--) {
			Element child = children.get(i);
			if (child.contains(mouseX, mouseY)) {
				child.collect(mouseX, mouseY, path);
				return;
			}
		}
	}
}
