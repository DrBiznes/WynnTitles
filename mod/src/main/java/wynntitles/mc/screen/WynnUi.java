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
 * The look of WynnTitles' screens, after Wynncraft's raid start screen: a regal
 * purple field between stone pillars, a ribbon for the title, a stone tab bar,
 * dark stone wells and stone buttons, with gold for highlights. The sprites come from scripts/draw-ui-sprites.py;
 * sizes noted here must match it.
 */
final class WynnUi {
	// Light text: the wells are dark stone.
	static final int TEXT = 0xFFF4DC;
	static final int TEXT_DIM = 0xB3A9A4;
	static final int GOLD = 0xF7C948;
	/** Dark text on stone buttons. */
	static final int INK = 0x2A2426;
	static final int INK_DISABLED = 0x3A3438;
	/** Outlines for white lettering, as on Wynncraft's ribbons. */
	static final int OUTLINE = 0x1C1719;
	static final int ERROR = 0xFF6B6B;
	static final int NEEDS_UPDATE = 0xE89A50;
	static final int UNLOCKED = 0x8FD86B;

	static final Identifier PANEL = sprite("panel");
	static final Identifier EYE = sprite("eye");
	static final Identifier BAR = sprite("bar");
	static final Identifier TAB = sprite("tab");
	static final Identifier TAB_HOVER = sprite("tab_hover");
	static final Identifier TAB_SELECTED = sprite("tab_selected");
	static final Identifier WELL = sprite("well");
	static final Identifier NAMEPLATE = sprite("nameplate");
	static final Identifier ROW_HOVER = sprite("row_hover");
	static final Identifier ROW_SELECTED = sprite("row_selected");

	static final int BUTTON_HEIGHT = 18;
	static final int SCROLLBAR_WIDTH = 5;
	/** How much narrower a well's content is than the well. */
	static final int WELL_INSET = 3 + 2 + SCROLLBAR_WIDTH + 1;
	private static final Identifier RIBBON = sprite("ribbon");
	private static final Identifier BUTTON = sprite("button");
	private static final Identifier BUTTON_HOVER = sprite("button_hover");
	private static final Identifier BUTTON_DISABLED = sprite("button_disabled");
	private static final Identifier SCROLL_TRACK = sprite("scroll_track");
	private static final Identifier SCROLL_THUMB = sprite("scroll_thumb");

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

	/** Like blit, with the sprite's colors multiplied by an RGB tint, for sprites drawn in greys to be tinted. */
	static void blit(GuiGraphics graphics, Identifier sprite, int x, int y, int width, int height, int tint) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height, 0xFF000000 | tint);
	}

	static LabelComponent label(Component text, int color) {
		LabelComponent label = UIComponents.label(text);
		label.color(Color.ofRgb(color)).shadow(false);
		return label;
	}

	/** The red ribbon across the top edge, with the screen's title in outlined white. */
	static FlowLayout ribbon(String text) {
		FlowLayout ribbon = UIContainers.horizontalFlow(Sizing.content(), Sizing.fixed(20));
		ribbon.padding(Insets.of(3, 0, 13, 13)).surface(surface(RIBBON));
		ribbon.child(new OutlinedText(WynnFonts.body(text), 0xFFFFFF, OUTLINE));
		return ribbon;
	}

	/** A small-caps label such as a column heading. */
	static LabelComponent heading(String text) {
		return label(WynnFonts.small(text), GOLD);
	}

	/** A stone button with dark lettering, like the raid screen's "Ready!". */
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
	 * No scroll step: owo rounds a stepped offset down to a whole step, so the
	 * last few pixels of a list could never scroll into view.
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
				.padding(Insets.of(3, 3, 3, 2))
				.surface(surface(WELL));
		return scroll;
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
