package wynntitles.mc.screen;

import net.minecraft.client.gui.Font;
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
import wynntitles.mc.screen.ui.Box;
import wynntitles.mc.screen.ui.Element;
import wynntitles.mc.screen.ui.Insets;
import wynntitles.mc.screen.ui.Label;
import wynntitles.mc.screen.ui.Painter;
import wynntitles.mc.screen.ui.ScrollView;
import wynntitles.mc.screen.ui.Size;

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

	/** The lighter band on every other row of a list. */
	static final Painter STRIPE = (graphics, e) ->
			graphics.fill(e.x(), e.y(), e.x() + e.width(), e.y() + e.height(), 0x0FFFF5EB);

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

	/** The sprite stretched (or nine-sliced, per its .mcmeta) over the element. */
	static Painter surface(Identifier sprite) {
		return (graphics, e) -> blit(graphics, sprite, e.x(), e.y(), e.width(), e.height());
	}

	static void blit(GuiGraphics graphics, Identifier sprite, int x, int y, int width, int height) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
	}

	/** Like blit, with the sprite's colors multiplied by an RGB tint, for sprites drawn in greys to be tinted. */
	static void blit(GuiGraphics graphics, Identifier sprite, int x, int y, int width, int height, int tint) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height, 0xFF000000 | tint);
	}

	static Label label(Component text, int color) {
		return new Label(text, color);
	}

	/** The red ribbon across the top edge, with the screen's title in outlined white. */
	static Box ribbon(String text) {
		Box ribbon = Box.row(Size.content(), Size.fixed(20));
		ribbon.padding(Insets.of(3, 0, 13, 13)).background(surface(RIBBON));
		ribbon.child(new OutlinedText(WynnFonts.body(text), 0xFFFFFF, OUTLINE));
		return ribbon;
	}

	/** A small-caps label such as a column heading. */
	static Label heading(String text) {
		return label(WynnFonts.small(text), GOLD);
	}

	/** A stone button with dark lettering, like the raid screen's "Ready!". */
	static Element button(String text, int width, boolean active, Runnable onPress) {
		return new StoneButton(WynnFonts.small(text).withColor(active ? INK : INK_DISABLED), width, active, onPress);
	}

	/**
	 * Scrolls its content inside an engraved well, with a gold handle. The
	 * scrollbar is drawn over the content's right edge, so the content is padded clear of it.
	 */
	static ScrollView well(Size width, Size height, Element content) {
		Box clear = Box.column(Size.fill(), Size.content());
		clear.child(content);
		clear.padding(Insets.right(SCROLLBAR_WIDTH + 1));
		return new ScrollView(width, height, clear)
				.scrollbar((graphics, thumbX, thumbY, thumbW, thumbH, trackX, trackY, trackW, trackH) -> {
					blit(graphics, SCROLL_TRACK, trackX, trackY, trackW, trackH);
					blit(graphics, SCROLL_THUMB, thumbX, thumbY, thumbW, thumbH);
				}, SCROLLBAR_WIDTH)
				.padding(Insets.of(3, 3, 3, 2))
				.background(surface(WELL));
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

	/**
	 * Fires on press, like vanilla buttons, and sinks its lettering a pixel
	 * while held, so it feels pushed in.
	 */
	private static final class StoneButton extends Element {
		private final Component text;
		private final boolean active;
		private final Runnable onPress;

		StoneButton(Component text, int width, boolean active, Runnable onPress) {
			this.text = text;
			this.active = active;
			this.onPress = onPress;
			sizing(Size.fixed(width), Size.fixed(BUTTON_HEIGHT));
		}

		@Override
		protected boolean click(double mouseX, double mouseY) {
			if (!active) {
				return false;
			}
			playClick();
			onPress.run();
			return true;
		}

		@Override
		protected boolean clickable(double mouseX, double mouseY) {
			return active;
		}

		@Override
		public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
			blit(graphics, !active ? BUTTON_DISABLED : hovered() ? BUTTON_HOVER : BUTTON, x, y, width, height);
			Font font = Mc.client().font;
			int sink = active && pressed() ? 1 : 0;
			graphics.drawString(font, text, x + (width - font.width(text)) / 2, y + (height - 8) / 2 + sink,
					0xFFFFFFFF, false);
		}
	}
}
