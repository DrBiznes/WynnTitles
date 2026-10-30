package wynntitles.mc.screen.ui;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Scrolls one child up and down inside its padding. The wheel glides there
 * rather than jumping; dragging the scrollbar, or clicking its track, moves
 * straight there. The scrollbar is drawn over the right edge of the padding
 * area, so the child should leave room for it.
 */
public class ScrollView extends Element {
	/** Pixels per wheel notch. */
	public static final int NOTCH = 15;
	/** How quickly a glide closes in on its target, in seconds; about two thirds of the way after this long. */
	private static final double GLIDE_SECONDS = 0.07;

	@FunctionalInterface
	public interface Scrollbar {
		void paint(GuiGraphics graphics, int thumbX, int thumbY, int thumbWidth, int thumbHeight,
				int trackX, int trackY, int trackWidth, int trackHeight);
	}

	private final Element child;
	private Insets padding = Insets.NONE;
	private Painter background;
	private Scrollbar scrollbar;
	private int thickness = 3;
	private double target;
	private double current;
	private int maxScroll;
	private long lastFrameNanos;
	private boolean dragging;

	public ScrollView(Size width, Size height, Element child) {
		this.child = child;
		sizing(width, height);
	}

	Element child() {
		return child;
	}

	public ScrollView padding(Insets padding) {
		this.padding = padding;
		return this;
	}

	public ScrollView background(Painter background) {
		this.background = background;
		return this;
	}

	public ScrollView scrollbar(Scrollbar scrollbar, int thickness) {
		this.scrollbar = scrollbar;
		this.thickness = thickness;
		return this;
	}

	/** How far down the view is, in pixels, once any glide settles. */
	public double offset() {
		return target;
	}

	/**
	 * Jumps straight to an offset, such as the one an earlier view of the same
	 * list had, so rebuilding a page keeps its place. Kept in range once laid out.
	 */
	public ScrollView scrollTo(double offset) {
		target = current = Math.max(0, offset);
		return this;
	}

	@Override
	protected void measure(int spaceWidth, int spaceHeight) {
		super.measure(spaceWidth, spaceHeight);
		child.measure(width - padding.horizontal(), height - padding.vertical());
		maxScroll = Math.max(0, child.fullHeight() - viewHeight());
		target = Math.clamp(target, 0, maxScroll);
		current = Math.clamp(current, 0, maxScroll);
	}

	@Override
	protected void place(int x, int y) {
		super.place(x, y);
		long now = System.nanoTime();
		double seconds = lastFrameNanos == 0 ? 0 : (now - lastFrameNanos) / 1e9;
		lastFrameNanos = now;
		current += (target - current) * (1 - Math.exp(-seconds / GLIDE_SECONDS));
		if (Math.abs(target - current) < 0.3) {
			current = target;
		}
		child.place(x + padding.left() + child.margins().left(),
				y + padding.top() + child.margins().top() - (int) Math.round(current));
	}

	@Override
	public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
		if (background != null) {
			background.paint(graphics, this);
		}
		graphics.enableScissor(x + padding.left(), y + padding.top(), x + width - padding.right(), y + height - padding.bottom());
		child.draw(graphics, mouseX, mouseY);
		graphics.disableScissor();
		if (scrollbar != null && maxScroll > 0) {
			int thumb = thumbLength();
			int thumbY = trackY() + (int) Math.round(current / maxScroll * (viewHeight() - thumb));
			scrollbar.paint(graphics, trackX(), thumbY, thickness, thumb, trackX(), trackY(), thickness, viewHeight());
		}
	}

	@Override
	protected void collect(double mouseX, double mouseY, List<Element> path) {
		path.add(this);
		if (!inScrollbar(mouseX) && inView(mouseX, mouseY) && child.contains(mouseX, mouseY)) {
			child.collect(mouseX, mouseY, path);
		}
	}

	/** Moves by wheel notches, positive toward the top. */
	boolean scroll(double notches) {
		if (maxScroll == 0) {
			return false;
		}
		target = Math.clamp(target - notches * NOTCH, 0, maxScroll);
		return true;
	}

	/** Starts a scrollbar drag if the point is on the scrollbar; a click on the track centres the thumb there. */
	boolean startDrag(double mouseX, double mouseY) {
		if (maxScroll == 0 || !inScrollbar(mouseX)) {
			return false;
		}
		int thumb = thumbLength();
		int thumbY = trackY() + (int) Math.round(current / maxScroll * (viewHeight() - thumb));
		if (mouseY < thumbY || mouseY >= thumbY + thumb) {
			jumpTo((mouseY - trackY() - thumb / 2.0) / (viewHeight() - thumb) * maxScroll);
		}
		dragging = true;
		return true;
	}

	void drag(double dy) {
		if (dragging) {
			jumpTo(current + dy * maxScroll / Math.max(1, viewHeight() - thumbLength()));
		}
	}

	void stopDrag() {
		dragging = false;
	}

	private void jumpTo(double offset) {
		target = current = Math.clamp(offset, 0, maxScroll);
	}

	private int viewHeight() {
		return height - padding.vertical();
	}

	private int trackX() {
		return x + width - padding.right() - thickness;
	}

	private int trackY() {
		return y + padding.top();
	}

	private int thumbLength() {
		int view = viewHeight();
		return Math.max(thickness, Math.min(view, view * view / Math.max(1, child.fullHeight())));
	}

	private boolean inScrollbar(double mouseX) {
		return scrollbar != null && maxScroll > 0 && mouseX >= trackX();
	}

	private boolean inView(double mouseX, double mouseY) {
		return mouseX >= x + padding.left() && mouseX < x + width - padding.right()
				&& mouseY >= y + padding.top() && mouseY < y + height - padding.bottom();
	}
}
