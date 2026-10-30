package wynntitles.mc.screen.ui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * A screen drawn as one fixed-size picture, the root element, centred in the
 * window. The picture is pixel art, so it is scaled by a whole number of
 * window pixels per picture pixel: as large as the GUI scale the player chose,
 * and smaller if that would not fit, so it never spills off a small window.
 * The mouse is mapped back into the picture's pixels for hovering and clicks.
 */
public abstract class UiScreen extends Screen {
	private static float devicePixel = 1;

	private final int artWidth;
	private final int artHeight;
	private Element root;
	private List<Element> hoverPath = List.of();
	private ScrollView dragging;
	private Element pressed;
	private float scale = 1;
	private float originX;
	private float originY;

	protected UiScreen(Component title, int artWidth, int artHeight) {
		super(title);
		this.artWidth = artWidth;
		this.artHeight = artHeight;
	}

	/** One window pixel in the open screen's pixels, for nudging text by less than a whole pixel. */
	public static float devicePixel() {
		return devicePixel;
	}

	/** Called once, the first time the screen opens; returns the root, artWidth by artHeight. */
	protected abstract Element build();

	/** How far below its resting place the root is drawn, as a share of the window's height, for an opening animation. */
	protected float drop() {
		return 0;
	}

	/** Runs every frame before layout, to move whatever is animating. */
	protected void animate() {}

	@Override
	protected void init() {
		if (root == null) {
			root = build();
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Darken the world without the menu blur; the panel is the focus.
		renderTransparentBackground(graphics);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		fit();
		animate();
		root.measure(artWidth, artHeight);
		root.place(0, Math.round(drop() * height / scale));
		double artX = toArtX(mouseX);
		double artY = toArtY(mouseY);
		hover(artX, artY);

		graphics.pose().pushMatrix();
		graphics.pose().translate(originX, originY);
		graphics.pose().scale(scale, scale);
		root.draw(graphics, (int) Math.floor(artX), (int) Math.floor(artY));
		graphics.pose().popMatrix();

		for (int i = hoverPath.size() - 1; i >= 0; i--) {
			Component tooltip = hoverPath.get(i).tooltip(artX, artY);
			if (tooltip != null) {
				graphics.setTooltipForNextFrame(font, font.split(tooltip, Integer.MAX_VALUE), mouseX, mouseY);
				break;
			}
		}
		if (dragging != null || hoverPath.stream().anyMatch(e -> e.clickable(artX, artY))) {
			graphics.requestCursor(CursorTypes.POINTING_HAND);
		}
	}

	/**
	 * The middle of the first element with a tooltip of its own, not a box's, in
	 * window pixels, or null; for tests that point the mouse at it. Valid once
	 * the screen has rendered.
	 */
	public double[] tooltipTarget() {
		Element target = root == null ? null : tooltipLeaf(root);
		if (target == null) {
			return null;
		}
		int guiScale = minecraft.getWindow().getGuiScale();
		return new double[] {
				(originX + (target.x() + target.width() / 2.0) * scale) * guiScale,
				(originY + (target.y() + target.height() / 2.0) * scale) * guiScale};
	}

	private static Element tooltipLeaf(Element element) {
		if (element instanceof Box box) {
			for (Element child : box.children()) {
				Element found = tooltipLeaf(child);
				if (found != null) {
					return found;
				}
			}
			return null;
		}
		if (element instanceof ScrollView scroll) {
			return tooltipLeaf(scroll.child());
		}
		return element.tooltip(element.x(), element.y()) != null ? element : null;
	}

	/** Picks the scale and where the picture's corner goes, both on whole window pixels. */
	private void fit() {
		Window window = minecraft.getWindow();
		int guiScale = window.getGuiScale();
		float fits = Math.min(window.getWidth() / (float) artWidth, window.getHeight() / (float) artHeight);
		// Window pixels per picture pixel: whole while it fits, fractional only on a tiny window.
		float pixels = fits >= 1 ? Math.min(guiScale, (int) fits) : fits;
		scale = pixels / guiScale;
		devicePixel = 1 / pixels;
		originX = (float) Math.floor((window.getWidth() - artWidth * pixels) / 2) / guiScale;
		originY = (float) Math.floor((window.getHeight() - artHeight * pixels) / 2) / guiScale;
	}

	private double toArtX(double guiX) {
		return (guiX - originX) / scale;
	}

	private double toArtY(double guiY) {
		return (guiY - originY) / scale;
	}

	private void hover(double artX, double artY) {
		for (Element e : hoverPath) {
			e.hovered = false;
		}
		List<Element> path = new ArrayList<>();
		if (root.contains(artX, artY)) {
			root.collect(artX, artY, path);
		}
		for (Element e : path) {
			e.hovered = true;
		}
		hoverPath = path;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || root == null) {
			return super.mouseClicked(event, doubleClick);
		}
		double artX = toArtX(event.x());
		double artY = toArtY(event.y());
		hover(artX, artY);
		// Deepest first: a chip inside a scroll view gets the click before the view.
		for (int i = hoverPath.size() - 1; i >= 0; i--) {
			Element e = hoverPath.get(i);
			if (e instanceof ScrollView view && view.startDrag(artX, artY)) {
				dragging = view;
				return true;
			}
			if (e.click(artX, artY)) {
				pressed = e;
				e.pressed = true;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging != null) {
			dragging.drag(dragY / scale);
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging.stopDrag();
			dragging = null;
		}
		if (pressed != null) {
			pressed.pressed = false;
			pressed = null;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		hover(toArtX(mouseX), toArtY(mouseY));
		for (int i = hoverPath.size() - 1; i >= 0; i--) {
			if (hoverPath.get(i) instanceof ScrollView view && view.scroll(scrollY)) {
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}
}
