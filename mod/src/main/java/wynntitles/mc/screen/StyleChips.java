package wynntitles.mc.screen;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import wynntitles.catalog.Catalog;
import wynntitles.catalog.Catalog.ShopItem;
import wynntitles.mc.TitleRenderer;
import wynntitles.mc.WynnFonts;
import wynntitles.mc.screen.ui.Align;
import wynntitles.mc.screen.ui.Box;
import wynntitles.mc.screen.ui.Element;
import wynntitles.mc.screen.ui.Insets;
import wynntitles.mc.screen.ui.Painter;
import wynntitles.mc.screen.ui.Size;
import wynntitles.titles.EffectColors;
import wynntitles.titles.TitleAnimation;
import wynntitles.titles.TitleFonts;
import wynntitles.titles.TitleStyle;
import wynntitles.titles.TitleWord;

/** Shop items as small tiles in groups, shared by the styles and shop tabs. */
final class StyleChips {
	private static final int CHIP_HEIGHT = 15;
	private static final int PLAQUE_HEIGHT = 11;
	/** How long the prismatic rim takes to go once round the rainbow. */
	private static final int PRISM_CYCLE_MS = 4000;
	private static final Identifier CHIP = WynnUi.sprite("chip");
	private static final Identifier CHIP_HOVER = WynnUi.sprite("chip_hover");
	private static final Identifier CHIP_SELECTED = WynnUi.sprite("chip_selected");
	private static final Identifier OWNED = WynnUi.sprite("owned");
	private static final int OWNED_SIZE = 9;

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
				case ShopItem.FONT -> GOLD;
				case ShopItem.ANIMATED_PALETTE -> GOLD;
				case ShopItem.RAINBOW -> PRISMATIC;
				default -> STONE;
			};
		}

		/** Draws one of this tier's sprites, tinted if it is prismatic. */
		void draw(GuiGraphics graphics, Identifier sprite, Element c) {
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
			new String[] {ShopItem.FORMAT, "Formats"},
			new String[] {ShopItem.FONT, "Fonts"});

	private StyleChips() {}

	/**
	 * A heading and a wrapping row of chips for every group that has items, in
	 * catalog order. Kinds this mod does not know are left out. With prices,
	 * each heading carries a plaque in its tier's metal with what the group costs.
	 */
	static void addGroups(Box into, Catalog catalog, Predicate<ShopItem> include,
			Function<ShopItem, Box> chip, boolean prices) {
		for (String[] group : GROUPS) {
			List<ShopItem> items = catalog.shop().stream()
					.filter(i -> i.kind().equals(group[0]) && i.supported() && include.test(i))
					.toList();
			if (items.isEmpty()) {
				continue;
			}
			Box heading = Box.row(Size.content(), Size.content());
			heading.gap(4).alignY(Align.CENTER).margins(Insets.top(2));
			heading.child(WynnUi.heading(group[1]));
			if (prices) {
				heading.child(plaque(Tier.of(group[0]), items));
			}
			into.child(heading);
			Box chips = row();
			for (ShopItem item : items) {
				chips.child(chip.apply(item));
			}
			into.child(chips);
		}
	}

	static void addGroups(Box into, Catalog catalog, Predicate<ShopItem> include, Function<ShopItem, Box> chip) {
		addGroups(into, catalog, include, chip, false);
	}

	/** A metal tag with the group's price, or its cheapest if prices differ. */
	private static Box plaque(Tier tier, List<ShopItem> items) {
		int min = items.stream().mapToInt(ShopItem::price).min().orElse(0);
		boolean same = items.stream().allMatch(i -> i.price() == min);
		Box plaque = Box.row(Size.content(), Size.fixed(PLAQUE_HEIGHT));
		plaque.padding(Insets.of(2, 0, 4, 4)).alignY(Align.CENTER);
		plaque.background((graphics, c) -> tier.draw(graphics, tier.plaque, c));
		plaque.child(WynnUi.label(WynnFonts.small((same ? "" : "from ") + points(min)), WynnUi.INK));
		return plaque;
	}

	static Box row() {
		Box chips = Box.wrap(Size.fill(), Size.content());
		chips.gap(2);
		return chips;
	}

	/**
	 * The item's name drawn in its own style, so animated items animate right on
	 * the chip; colors get a swatch, since dark text would vanish into the well.
	 */
	static Element label(Catalog catalog, ShopItem item) {
		if (ShopItem.COLOR.equals(item.kind())) {
			Box swatch = Box.row(Size.content(), Size.content());
			swatch.gap(3);
			swatch.alignY(Align.CENTER);
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
			case ShopItem.FONT -> item.banner() ? "Solid banner and contrasting letters; no formats" : "Wynncraft font, with colors and formats";
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
		if (ShopItem.FONT.equals(item.kind())) return new TitleStyle(null, List.of(), item.id(), null, null);
		return item.isSource() ? new TitleStyle(item.id(), List.of()) : new TitleStyle(null, List.of(item.id()));
	}

	/** Missing packs and unsupported characters are explained before selection. */
	static String fontProblem(ShopItem item, TitleWord... words) {
		if (!ShopItem.FONT.equals(item.kind())) return null;
		if (!WynnFonts.available(item.font())) return "Needs the Wynncraft resource pack";
		if (item.banner()) for (TitleWord word : words) {
			if (word == null) continue;
			int c = TitleFonts.bannerCharacter(word.text());
			if (c != 0) return item.name() + ": no banner glyph for “" + new String(Character.toChars(c)) + "”";
		}
		return null;
	}

	static Box disabled(ShopItem item, String why, Element badge) {
		Box chip = chip(Tier.STONE, WynnUi.label(WynnFonts.body(item.name()), WynnUi.TEXT_DIM), badge,
				() -> false, tooltip(item.name(), why, ChatFormatting.GRAY), null);
		return chip;
	}

	/** A tile with a label and an optional badge (the owned tick, say), gold-rimmed while selected. */
	static Box chip(Element label, Element badge, BooleanSupplier selected, Component tooltip, Runnable onClick) {
		return chip(Tier.STONE, label, badge, selected, tooltip, onClick);
	}

	/** A chip for a shop item, rimmed in its tier's metal. */
	static Box chip(ShopItem item, Element label, Element badge, BooleanSupplier selected,
			Component tooltip, Runnable onClick) {
		return chip(Tier.of(item.kind()), label, badge, selected, tooltip, onClick);
	}

	private static Box chip(Tier tier, Element label, Element badge, BooleanSupplier selected,
			Component tooltip, Runnable onClick) {
		Box chip = Box.row(Size.content(), Size.fixed(CHIP_HEIGHT));
		chip.gap(4);
		chip.padding(Insets.of(2, 0, 5, 5)).alignY(Align.CENTER);
		chip.child(label);
		if (badge != null) {
			chip.child(badge);
		}
		if (tooltip != null) {
			chip.tooltip(tooltip);
		}
		Painter rim = tier.rim == null ? null : (graphics, c) -> tier.draw(graphics, tier.rim, c);
		clickable(chip, WynnUi.surface(CHIP), selected, CHIP_SELECTED, CHIP_HOVER, rim, onClick);
		return chip;
	}

	/** An optional base, selected and hover sprites, and a click handler (which brings the hand cursor). */
	static void clickable(Box layout, Painter base, BooleanSupplier selected, Identifier selectedSprite,
			Identifier hoverSprite, Runnable onClick) {
		clickable(layout, base, selected, selectedSprite, hoverSprite, null, onClick);
	}

	/** As above, with an overlay drawn over the base and hover, but not while selected (a tier rim, say). */
	static void clickable(Box layout, Painter base, BooleanSupplier selected, Identifier selectedSprite,
			Identifier hoverSprite, Painter overlay, Runnable onClick) {
		Painter selectedSurface = WynnUi.surface(selectedSprite);
		Painter hoverSurface = WynnUi.surface(hoverSprite);
		layout.background((graphics, c) -> {
			if (base != null) {
				base.paint(graphics, c);
			}
			if (selected.getAsBoolean()) {
				selectedSurface.paint(graphics, c);
				return;
			}
			if (c.hovered()) {
				hoverSurface.paint(graphics, c);
			}
			if (overlay != null) {
				overlay.paint(graphics, c);
			}
		});
		layout.onClick(onClick);
	}

	/** The nameplate the tabs preview the title on. */
	static Box nameplate(Supplier<Component> title) {
		Box nameplate = Box.column(Size.fill(), Size.fixed(24));
		nameplate.background(WynnUi.surface(WynnUi.NAMEPLATE))
				.alignX(Align.CENTER)
				.alignY(Align.CENTER);
		nameplate.child(new TitleText(title, 1.5f, WynnUi.TEXT).centered());
		return nameplate;
	}

	/** The balance, for a footer. */
	static Box balance(int balance) {
		Box row = Box.row(Size.content(), Size.content());
		row.gap(6);
		row.alignY(Align.CENTER);
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

	/** A green seal with a tick, for the chip of a piece you own. */
	static Element ownedMark() {
		return new Element() {
			{
				sizing(Size.fixed(OWNED_SIZE), Size.fixed(OWNED_SIZE));
			}

			@Override
			public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
				WynnUi.blit(graphics, OWNED, x, y, width, height);
			}
		};
	}

	/** A small square of a solid color with a light rim, so black still shows. */
	private static final class Swatch extends Element {
		private final int color;

		Swatch(int color) {
			this.color = color;
			sizing(Size.fixed(7), Size.fixed(7));
		}

		@Override
		public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
			graphics.fill(x, y, x + width, y + height, 0xFFB3A9A4);
			graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF000000 | color);
		}
	}
}
