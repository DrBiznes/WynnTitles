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
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import wynntitles.catalog.Catalog;
import wynntitles.catalog.Catalog.ShopItem;
import wynntitles.mc.TitleRenderer;
import wynntitles.mc.WynnFonts;
import wynntitles.titles.EffectColors;
import wynntitles.titles.TitleAnimation;
import wynntitles.titles.TitleStyle;
import wynntitles.titles.TitleWord;

/** Shop items as small tiles in groups, shared by the styles and shop tabs. */
final class StyleChips {
	private static final int CHIP_HEIGHT = 13;
	private static final int PLAQUE_HEIGHT = 11;
	/** How long the prismatic rim takes to go once round the rainbow. */
	private static final int PRISM_CYCLE_MS = 4000;
	private static final Identifier CHIP = WynnUi.sprite("chip");
	private static final Identifier CHIP_HOVER = WynnUi.sprite("chip_hover");
	private static final Identifier CHIP_SELECTED = WynnUi.sprite("chip_selected");

	/**
	 * How grand an item's chip looks, by kind: pricier kinds get a metal rim and
	 * a matching price plaque on their group's heading. Prismatic sprites are
	 * drawn in greys and tinted with the rainbow every frame.
	 */
	enum Tier {
		STONE(null, "plaque_stone"),
		SILVER("chip_rim_silver", "plaque_silver"),
		GOLD("chip_rim_gold", "plaque_gold"),
		PRISMATIC("chip_rim_prismatic", "plaque_prismatic");

		final Identifier rim;
		final Identifier plaque;

		Tier(String rim, String plaque) {
			this.rim = rim == null ? null : WynnUi.sprite(rim);
			this.plaque = WynnUi.sprite(plaque);
		}

		static Tier of(String kind) {
			return switch (kind) {
				case ShopItem.PALETTE -> SILVER;
				case ShopItem.ANIMATED_PALETTE -> GOLD;
				case ShopItem.RAINBOW -> PRISMATIC;
				default -> STONE;
			};
		}

		/** Draws one of this tier's sprites, tinted if it is prismatic. */
		void draw(OwoUIGraphics graphics, Identifier sprite, UIComponent c) {
			if (this == PRISMATIC) {
				float t = (System.currentTimeMillis() % PRISM_CYCLE_MS) / (float) PRISM_CYCLE_MS;
				WynnUi.blit(graphics, sprite, c.x(), c.y(), c.width(), c.height(), EffectColors.rainbow(t));
			} else {
				WynnUi.blit(graphics, sprite, c.x(), c.y(), c.width(), c.height());
			}
		}
	}

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
	 * catalog order. Kinds this mod does not know are left out. With prices,
	 * each heading carries a plaque in its tier's metal with what the group costs.
	 */
	static void addGroups(FlowLayout into, Catalog catalog, Predicate<ShopItem> include,
			Function<ShopItem, FlowLayout> chip, boolean prices) {
		for (String[] group : GROUPS) {
			List<ShopItem> items = catalog.shop().stream()
					.filter(i -> i.kind().equals(group[0]) && i.supported() && include.test(i))
					.toList();
			if (items.isEmpty()) {
				continue;
			}
			FlowLayout heading = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
			heading.gap(4).verticalAlignment(VerticalAlignment.CENTER).margins(Insets.top(2));
			heading.child(WynnUi.heading(group[1]));
			if (prices) {
				heading.child(plaque(Tier.of(group[0]), items));
			}
			into.child(heading);
			FlowLayout chips = row();
			for (ShopItem item : items) {
				chips.child(chip.apply(item));
			}
			into.child(chips);
		}
	}

	static void addGroups(FlowLayout into, Catalog catalog, Predicate<ShopItem> include, Function<ShopItem, FlowLayout> chip) {
		addGroups(into, catalog, include, chip, false);
	}

	/** A metal tag with the group's price, or its cheapest if prices differ. */
	private static FlowLayout plaque(Tier tier, List<ShopItem> items) {
		int min = items.stream().mapToInt(ShopItem::price).min().orElse(0);
		boolean same = items.stream().allMatch(i -> i.price() == min);
		FlowLayout plaque = UIContainers.horizontalFlow(Sizing.content(), Sizing.fixed(PLAQUE_HEIGHT));
		plaque.padding(Insets.of(2, 0, 4, 4)).verticalAlignment(VerticalAlignment.CENTER);
		plaque.surface((graphics, c) -> tier.draw(graphics, tier.plaque, c));
		plaque.child(WynnUi.label(WynnFonts.small((same ? "" : "from ") + points(min)), WynnUi.INK));
		return plaque;
	}

	static FlowLayout row() {
		FlowLayout chips = UIContainers.ltrTextFlow(Sizing.fill(), Sizing.content());
		chips.gap(2);
		return chips;
	}

	/**
	 * The item's name drawn in its own style, so animated items animate right on
	 * the chip; colors get a swatch, since dark text would vanish into the well.
	 */
	static UIComponent label(Catalog catalog, ShopItem item) {
		if (ShopItem.COLOR.equals(item.kind())) {
			FlowLayout swatch = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
			swatch.gap(3);
			swatch.verticalAlignment(VerticalAlignment.CENTER);
			swatch.child(new Swatch(EffectColors.colorValue(item.color())));
			swatch.child(WynnUi.label(WynnFonts.body(item.name()), WynnUi.TEXT));
			return swatch;
		}
		TitleWord sample = catalog.restyle(new TitleWord(item.name(), null), alone(item));
		return new TitleText(() -> TitleRenderer.render(sample), 1f, WynnUi.TEXT);
	}

	/** What the item does, in a few words: "Ocean palette, rippling letter by letter". */
	static String describe(Catalog catalog, ShopItem item) {
		return switch (item.kind()) {
			case ShopItem.COLOR -> "Colors the whole word " + item.name().toLowerCase(Locale.ROOT);
			case ShopItem.PALETTE -> PALETTE_BLURBS.getOrDefault(item.palette(), "A gradient across the word");
			case ShopItem.ANIMATED_PALETTE -> {
				TitleAnimation motion = item.motion();
				String palette = catalog.shop().stream()
						.filter(i -> ShopItem.PALETTE.equals(i.kind()) && i.palette().equals(item.palette()))
						.map(ShopItem::name)
						.findFirst()
						.orElse(item.name());
				yield palette + " palette, " + (motion == null ? "animated" : motion.blurb);
			}
			case ShopItem.RAINBOW -> "Every color, flowing along the word";
			case ShopItem.FORMAT -> FORMAT_BLURBS.getOrDefault(item.format(), "A format") + ", over any look";
			default -> "";
		};
	}

	/** The colors of each palette, as {@link EffectColors} lays them along a word. */
	private static final Map<String, String> PALETTE_BLURBS = Map.ofEntries(
			Map.entry("red", "Scarlet fading to hot coral"),
			Map.entry("red_purple", "Red turning through rose to violet"),
			Map.entry("flame", "White-hot gold through orange to red"),
			Map.entry("ember", "Copper coals glowing amber"),
			Map.entry("sunset", "Violet sky to pink, orange and gold"),
			Map.entry("ocean", "Deep blue to teal and seafoam"),
			Map.entry("ice", "Pale, frosty blues"),
			Map.entry("toxic", "Acid lime to neon green"),
			Map.entry("forest", "Leaf and moss greens to bark brown"),
			Map.entry("royal", "Regal purple with a band of gold"),
			Map.entry("void", "Cold periwinkle and violet starlight"));

	private static final Map<String, String> FORMAT_BLURBS = Map.of(
			"bold", "Thicker letters",
			"italic", "Slanted letters",
			"underlined", "A line under the word",
			"strikethrough", "A line through the word");

	/** A style made of just this item. */
	static TitleStyle alone(ShopItem item) {
		return item.isSource() ? new TitleStyle(item.id(), List.of()) : new TitleStyle(null, List.of(item.id()));
	}

	/** A tile with a label and an optional badge ("owned", say), gold-rimmed while selected. */
	static FlowLayout chip(UIComponent label, Component badge, BooleanSupplier selected, Component tooltip, Runnable onClick) {
		return chip(Tier.STONE, label, badge, selected, tooltip, onClick);
	}

	/** A chip for a shop item, rimmed in its tier's metal. */
	static FlowLayout chip(ShopItem item, UIComponent label, Component badge, BooleanSupplier selected,
			Component tooltip, Runnable onClick) {
		return chip(Tier.of(item.kind()), label, badge, selected, tooltip, onClick);
	}

	private static FlowLayout chip(Tier tier, UIComponent label, Component badge, BooleanSupplier selected,
			Component tooltip, Runnable onClick) {
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
		Surface rim = tier.rim == null ? null : (graphics, c) -> tier.draw(graphics, tier.rim, c);
		clickable(chip, WynnUi.surface(CHIP), selected, CHIP_SELECTED, CHIP_HOVER, rim, onClick);
		return chip;
	}

	/** An optional base surface, selected and hover surfaces, a hand cursor and a click handler. */
	static void clickable(FlowLayout layout, Surface base, BooleanSupplier selected, Identifier selectedSprite,
			Identifier hoverSprite, Runnable onClick) {
		clickable(layout, base, selected, selectedSprite, hoverSprite, null, onClick);
	}

	/** As above, with an overlay drawn over the base and hover, but not while selected (a tier rim, say). */
	static void clickable(FlowLayout layout, Surface base, BooleanSupplier selected, Identifier selectedSprite,
			Identifier hoverSprite, Surface overlay, Runnable onClick) {
		boolean[] hovered = {false};
		Surface selectedSurface = WynnUi.surface(selectedSprite);
		Surface hoverSurface = WynnUi.surface(hoverSprite);
		layout.surface((graphics, c) -> {
			if (base != null) {
				base.draw(graphics, c);
			}
			if (selected.getAsBoolean()) {
				selectedSurface.draw(graphics, c);
				return;
			}
			if (hovered[0]) {
				hoverSurface.draw(graphics, c);
			}
			if (overlay != null) {
				overlay.draw(graphics, c);
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
		nameplate.child(new TitleText(title, 1.5f, WynnUi.TEXT).centered());
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

	static MutableComponent tooltip(String title, String detail, ChatFormatting color) {
		return WynnFonts.body(title).append(Component.literal("\n" + detail).withStyle(color));
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
