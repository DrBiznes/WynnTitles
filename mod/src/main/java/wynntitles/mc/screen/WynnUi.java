package wynntitles.mc.screen;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import wynntitles.WynnTitles;
import wynntitles.mc.Mc;
import wynntitles.mc.WynnFonts;

/**
 * The look of WynnTitles' screens, after Wynncraft's own menus: a wood frame
 * with gold fittings, parchment plaques and gold buttons. The sprites come from
 * scripts/draw-ui-sprites.py; sizes noted here must match it.
 */
final class WynnUi {
	// Light text: the panel and wells are dark wood.
	static final int TEXT = 0xF3E6C8;
	static final int TEXT_DIM = 0xA8916C;
	static final int GOLD = 0xF2CF4A;
	/** Dark text on parchment and gold. */
	static final int INK = 0x3F2812;
	static final int INK_DISABLED = 0x2A231C;
	static final int ERROR = 0xFF6B6B;
	static final int NEEDS_UPDATE = 0xE89A50;
	static final int UNLOCKED = 0x8FD86B;

	static final Identifier PANEL = sprite("panel");
	static final Identifier WELL = sprite("well");
	static final Identifier NAMEPLATE = sprite("nameplate");
	static final Identifier ROW_HOVER = sprite("row_hover");
	static final Identifier ROW_SELECTED = sprite("row_selected");

	static final int BUTTON_HEIGHT = 18;
	static final int SCROLLBAR_WIDTH = 5;
	/** How much narrower a well's content is than the well. */
	static final int WELL_INSET = 3 + 2 + SCROLLBAR_WIDTH + 1;
	private static final Identifier PLAQUE = sprite("plaque");
	private static final Identifier BUTTON = sprite("button");
	private static final Identifier BUTTON_HOVER = sprite("button_hover");
	private static final Identifier BUTTON_DISABLED = sprite("button_disabled");
	private static final Identifier SCROLL_TRACK = sprite("scroll_track");
	private static final Identifier SCROLL_THUMB = sprite("scroll_thumb");
	private static final Identifier GEM = sprite("gem");

	private WynnUi() {}

	static Identifier sprite(String name) {
		return Identifier.fromNamespaceAndPath(WynnTitles.MOD_ID, "widget/" + name);
	}

	static Surface surface(Identifier sprite) {
		return (graphics, component) -> blit(graphics, sprite,
				component.x(), component.y(), component.width(), component.height());
	}

	static void blit(GuiGraphics graphics, Identifier sprite, int x, int y, int width, int height) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
	}

	static LabelComponent label(Component text, int color) {
		LabelComponent label = UIComponents.label(text);
		label.color(Color.ofRgb(color)).shadow(false);
		return label;
	}

	/** A parchment nameboard with a heading in small capitals. */
	static FlowLayout plaque(String text) {
		FlowLayout plaque = UIContainers.horizontalFlow(Sizing.content(), Sizing.fixed(15));
		plaque.padding(Insets.of(4, 0, 10, 10)).surface(surface(PLAQUE));
		plaque.child(label(WynnFonts.small(text), INK));
		return plaque;
	}

	/** A small-caps label such as a column heading. */
	static LabelComponent heading(String text) {
		return label(WynnFonts.small(text), GOLD);
	}

	/** A gold button with dark lettering, like Wynncraft's "Start!". */
	static ButtonComponent button(String text, int width, boolean active, Runnable onPress) {
		ButtonComponent button = UIComponents.button(
				WynnFonts.small(text).withColor(active ? INK : INK_DISABLED), b -> onPress.run());
		button.renderer((graphics, b, delta) -> blit(graphics,
						!b.active ? BUTTON_DISABLED : b.isHovered() ? BUTTON_HOVER : BUTTON,
						b.getX(), b.getY(), b.getWidth(), b.getHeight()))
				.textShadow(false)
				.sizing(Sizing.fixed(width), Sizing.fixed(BUTTON_HEIGHT));
		button.active(active);
		return button;
	}

	/**
	 * Scrolls its content inside an engraved well, with a gold handle. owo draws
	 * the scrollbar over the content's right edge, so the content is padded clear of it.
	 */
	static ScrollContainer<UIComponent> well(Sizing width, Sizing height, UIComponent content) {
		FlowLayout clear = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		clear.child(content);
		clear.padding(Insets.right(SCROLLBAR_WIDTH + 1));
		ScrollContainer<UIComponent> scroll = UIContainers.verticalScroll(width, height, clear);
		scroll.scrollbar((graphics, x, y, w, h, trackX, trackY, trackW, trackH, lastInteract, direction, active) -> {
					if (!active) {
						return;
					}
					blit(graphics, SCROLL_TRACK, trackX, trackY, trackW, trackH);
					blit(graphics, SCROLL_THUMB, x, y, w, h);
				})
				.scrollbarThiccness(SCROLLBAR_WIDTH)
				.scrollStep(14)
				.padding(Insets.of(3, 3, 3, 2))
				.surface(surface(WELL));
		return scroll;
	}

	/** A thin gold rule with a gem in the middle. */
	static UIComponent divider(Sizing width) {
		return UIContainers.horizontalFlow(width, Sizing.fixed(5))
				.surface((graphics, c) -> {
					int mid = c.y() + 2;
					graphics.fill(c.x(), mid, c.x() + c.width(), mid + 1, 0xFF000000 | 0xB0801C);
					graphics.fill(c.x(), mid + 1, c.x() + c.width(), mid + 2, 0xFF000000 | 0x140D08);
					blit(graphics, GEM, c.x() + (c.width() - 7) / 2, c.y(), 7, 5);
				});
	}

	static void playClick() {
		play(SoundEvents.UI_BUTTON_CLICK.value(), 1f);
	}

	static void playPageTurn() {
		play(SoundEvents.BOOK_PAGE_TURN, 1f);
	}

	private static void play(SoundEvent sound, float pitch) {
		Mc.client().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
	}
}
