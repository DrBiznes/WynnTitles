package wynntitles.mc.screen;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import wynntitles.catalog.Catalog;
import wynntitles.catalog.Catalog.ShopItem;
import wynntitles.mc.TitleRenderer;
import wynntitles.mc.WynnFonts;
import wynntitles.titles.TitleStyle;
import wynntitles.titles.TitleWord;

/** Shop items as small tiles in groups, shared by the styles and shop tabs. */
final class StyleChips {
	private static final int CHIP_HEIGHT = 13;
	private static final Identifier CHIP = WynnUi.sprite("chip");
	private static final Identifier CHIP_HOVER = WynnUi.sprite("chip_hover");
	private static final Identifier CHIP_SELECTED = WynnUi.sprite("chip_selected");

	/** Shop groups in the order they are shown, by item kind. */
	private static final List<String[]> GROUPS = List.of(
			new String[] {ShopItem.COLOR, "Colors"},
			new String[] {ShopItem.PALETTE, "Palettes"},
			new String[] {ShopItem.ANIMATED_PALETTE, "Animated palettes"},
			new String[] {ShopItem.RAINBOW, "Rainbow"},
			new String[] {ShopItem.FORMAT, "Formats"});

	private StyleChips() {}

	/**
	 * A heading and a wrapping row of chips for every group that has items, in
	 * catalog order. Kinds this mod does not know are left out.
	 */
	static void addGroups(FlowLayout into, Catalog catalog, Predicate<ShopItem> include, Function<ShopItem, FlowLayout> chip) {
		for (String[] group : GROUPS) {
			List<ShopItem> items = catalog.shop().stream()
					.filter(i -> i.kind().equals(group[0]) && i.supported() && include.test(i))
					.toList();
			if (items.isEmpty()) {
				continue;
			}
			into.child(WynnUi.heading(group[1]).margins(Insets.top(2)));
			FlowLayout chips = row();
			for (ShopItem item : items) {
				chips.child(chip.apply(item));
			}
			into.child(chips);
		}
	}

	static FlowLayout row() {
		FlowLayout chips = UIContainers.ltrTextFlow(Sizing.fill(), Sizing.content());
		chips.gap(2);
		return chips;
	}

	/** The item's name drawn in its own style; colors get a swatch, since dark text would vanish into the well. */
	static UIComponent label(Catalog catalog, ShopItem item) {
		if (ShopItem.COLOR.equals(item.kind())) {
			FlowLayout swatch = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
			swatch.gap(3);
			swatch.verticalAlignment(VerticalAlignment.CENTER);
			swatch.child(new Swatch(colorValue(item.color())));
			swatch.child(WynnUi.label(WynnFonts.body(item.name()), WynnUi.TEXT));
			return swatch;
		}
		TitleWord sample = catalog.restyle(new TitleWord(item.name(), null), alone(item));
		return new TitleText(() -> TitleRenderer.render(sample), 1f, WynnUi.TEXT);
	}

	/** A style made of just this item. */
	static TitleStyle alone(ShopItem item) {
		return item.isSource() ? new TitleStyle(item.id(), List.of()) : new TitleStyle(null, List.of(item.id()));
	}

	/** A tile with a label and an optional badge (a price, say), gold-rimmed while selected. */
	static FlowLayout chip(UIComponent label, Component badge, BooleanSupplier selected, Component tooltip, Runnable onClick) {
		FlowLayout chip = UIContainers.horizontalFlow(Sizing.content(), Sizing.fixed(CHIP_HEIGHT));
		chip.gap(4);
		chip.padding(Insets.of(2, 0, 4, 4)).verticalAlignment(VerticalAlignment.CENTER);
		chip.child(label);
		if (badge != null) {
			chip.child(WynnUi.label(badge, WynnUi.GOLD));
		}
		if (tooltip != null) {
			chip.tooltip(tooltip);
		}
		clickable(chip, WynnUi.surface(CHIP), selected, CHIP_SELECTED, CHIP_HOVER, onClick);
		return chip;
	}

	/** An optional base surface, selected and hover surfaces, a hand cursor and a click handler. */
	static void clickable(FlowLayout layout, Surface base, BooleanSupplier selected, Identifier selectedSprite,
			Identifier hoverSprite, Runnable onClick) {
		boolean[] hovered = {false};
		Surface selectedSurface = WynnUi.surface(selectedSprite);
		Surface hoverSurface = WynnUi.surface(hoverSprite);
		layout.surface((graphics, c) -> {
			if (base != null) {
				base.draw(graphics, c);
			}
			if (selected.getAsBoolean()) {
				selectedSurface.draw(graphics, c);
			} else if (hovered[0]) {
				hoverSurface.draw(graphics, c);
			}
		});
		layout.cursorStyle(CursorStyle.HAND);
		layout.mouseEnter().subscribe(() -> hovered[0] = true);
		layout.mouseLeave().subscribe(() -> hovered[0] = false);
		layout.mouseDown().subscribe((click, doubled) -> {
			onClick.run();
			return true;
		});
	}

	/** The nameplate both tabs preview the title on: the title, then the player's name. */
	static FlowLayout nameplate(Supplier<Component> title, String username) {
		FlowLayout nameplate = UIContainers.verticalFlow(Sizing.fill(), Sizing.fixed(36));
		nameplate.gap(2);
		nameplate.surface(WynnUi.surface(WynnUi.NAMEPLATE))
				.horizontalAlignment(HorizontalAlignment.CENTER)
				.verticalAlignment(VerticalAlignment.CENTER);
		nameplate.child(new TitleText(title, 1.5f, WynnUi.TEXT));
		nameplate.child(WynnUi.label(Component.literal(username), 0xFFFFFF));
		return nameplate;
	}

	/** The balance, for a footer. */
	static FlowLayout balance(int balance) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		row.gap(6);
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.child(WynnUi.heading("Balance"));
		row.child(WynnUi.label(WynnFonts.body(points(balance)), WynnUi.TEXT));
		return row;
	}

	static String points(int n) {
		return n + (n == 1 ? " pt" : " pts");
	}

	static Component tooltip(String title, String detail, ChatFormatting color) {
		return WynnFonts.body(title).append(Component.literal("\n" + detail).withStyle(color));
	}

	private static int colorValue(String color) {
		ChatFormatting named = ChatFormatting.getByName(color);
		if (named != null && named.getColor() != null) {
			return named.getColor();
		}
		try {
			return Integer.parseInt(color.substring(1), 16);
		} catch (RuntimeException e) {
			return 0xFFFFFF;
		}
	}

	/** A small square of a solid color with a light rim, so black still shows. */
	private static final class Swatch extends BaseUIComponent {
		private final int color;

		Swatch(int color) {
			this.color = color;
			sizing(Sizing.fixed(7), Sizing.fixed(7));
		}

		@Override
		public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
			graphics.fill(x, y, x + width, y + height, 0xFFB3A9A4);
			graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF000000 | color);
		}
	}
}
