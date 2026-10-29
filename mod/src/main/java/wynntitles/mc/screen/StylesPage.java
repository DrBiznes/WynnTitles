package wynntitles.mc.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import wynntitles.WynnTitles;
import wynntitles.api.ApiException;
import wynntitles.api.ApiTypes.Me;
import wynntitles.catalog.Catalog;
import wynntitles.catalog.Catalog.ShopItem;
import wynntitles.mc.Mc;
import wynntitles.mc.TitleRenderer;
import wynntitles.mc.WynnFonts;
import wynntitles.mc.screen.ui.Align;
import wynntitles.mc.screen.ui.Box;
import wynntitles.mc.screen.ui.Insets;
import wynntitles.mc.screen.ui.ScrollView;
import wynntitles.mc.screen.ui.Size;
import wynntitles.titles.Title;
import wynntitles.titles.TitleStyle;
import wynntitles.titles.TitleWord;

/**
 * The styles tab: dress each title slot in pieces you own, or leave it in the
 * word's native style. A shop style replaces the native one completely.
 * Buying happens on the shop tab.
 */
final class StylesPage {
	private static final int SLOT_WIDTH = 104;

	private enum Slot {
		PREFIX("Prefix"),
		SUFFIX("Suffix");

		final String label;

		Slot(String label) {
			this.label = label;
		}
	}

	private final Consumer<Me> onSaved;
	private Slot slot = Slot.PREFIX;
	/** The style picked for each slot; null is the word's native style. */
	private final TitleStyle[] drafts = new TitleStyle[2];
	private boolean saving;
	private String error;

	private Catalog catalog;
	private Me me;
	private TitleWord prefixWord;
	private TitleWord suffixWord;
	private Box slots;
	private Box footer;
	private ScrollView ownedList;
	private Box ownedContent;
	private String adjustment;

	StylesPage(Consumer<Me> onSaved) {
		this.onSaved = onSaved;
	}

	/** Goes back to what is worn, e.g. after saving or when the profile first loads. */
	void reset(Me me) {
		drafts[0] = me.styles().prefix();
		drafts[1] = me.styles().suffix();
	}

	/** The words are the ones picked on the titles tab, equipped or not. */
	Box build(Catalog catalog, Me me, TitleWord prefixWord, TitleWord suffixWord, int width, int height) {
		this.catalog = catalog;
		this.me = me;
		this.prefixWord = prefixWord;
		this.suffixWord = suffixWord;
		footer = Box.row(Size.content(), Size.content());
		footer.gap(8);
		footer.alignY(Align.CENTER);

		Box page = Box.column(Size.fixed(width), Size.fixed(height));
		page.gap(5);
		page.child(StyleChips.nameplate(this::preview));

		Box columns = Box.row(Size.fill(), Size.expand());
		columns.gap(8);
		Box left = Box.column(Size.fixed(SLOT_WIDTH), Size.fill());
		left.gap(3);
		left.child(WynnUi.heading("Slot").margins(Insets.left(2)));
		slots = Box.column(Size.fill(), Size.content());
		left.child(WynnUi.well(Size.fill(), Size.expand(), slots));
		columns.child(left);

		Box right = Box.column(Size.expand(), Size.fill());
		right.gap(3);
		right.child(WynnUi.heading("Your styles").margins(Insets.left(2)));
		// Wearing rebuilds the page; stay where the player was in the list.
		double offset = ownedList == null ? 0 : ownedList.offset();
		ownedContent = Box.column(Size.fill(), Size.content());
		ownedList = WynnUi.well(Size.fill(), Size.expand(), ownedContent).scrollTo(offset);
		right.child(ownedList);
		columns.child(right);
		page.child(columns);

		refresh();
		return page;
	}

	/** The actions for the panel's footer, from the last build. */
	Box footer() {
		return footer;
	}

	/** Redraws what depends on the drafts. The chips restyle themselves, so the list keeps its scroll. */
	private void refresh() {
		slots.clearChildren();
		slots.child(slotRow(Slot.PREFIX, prefixWord));
		slots.child(slotRow(Slot.SUFFIX, suffixWord));
		ownedContent.clearChildren();
		ownedContent.child(owned());
		refreshFooter();
	}

	private Component preview() {
		Title title = Title.of(catalog.restyle(prefixWord, drafts[0]), catalog.restyle(suffixWord, drafts[1]));
		return title == null
				? WynnFonts.body("Pick a title on the Titles tab").withColor(WynnUi.TEXT_DIM)
				: TitleRenderer.render(title);
	}

	private Box slotRow(Slot s, TitleWord word) {
		Box row = Box.column(Size.fill(), Size.content());
		row.gap(2);
		row.padding(Insets.of(3, 3, 5, 2));
		Box heading = Box.row(Size.content(), Size.content());
		heading.gap(4);
		heading.child(WynnUi.label(WynnFonts.small(s.label), s == slot ? WynnUi.GOLD : WynnUi.TEXT_DIM));
		if (drafts[s.ordinal()] == null) {
			heading.child(WynnUi.label(WynnFonts.small("native"), WynnUi.TEXT_DIM));
		}
		row.child(heading);
		row.child(word == null
				? WynnUi.label(WynnFonts.body("None"), WynnUi.TEXT_DIM)
				: new TitleText(() -> TitleRenderer.render(catalog.restyle(word, drafts[s.ordinal()])), 1f, WynnUi.TEXT));

		StyleChips.clickable(row, null, () -> s == slot, WynnUi.ROW_SELECTED, WynnUi.ROW_HOVER, () -> {
			if (slot != s) {
				slot = s;
				WynnUi.playClick();
				refresh();
			}
		});
		return row;
	}

	private Box owned() {
		Box list = Box.column(Size.fill(), Size.content());
		list.gap(3);
		list.padding(Insets.of(2, 2, 3, 2));

		list.child(WynnUi.heading("Look"));
		Box look = StyleChips.row();
		look.child(StyleChips.chip(WynnUi.label(WynnFonts.body("Native"), WynnUi.TEXT), null,
				() -> draft() == null,
				StyleChips.tooltip("Native", "The word's own style from its achievement", ChatFormatting.GRAY),
				() -> setDraft(null)));
		list.child(look);

		StyleChips.addGroups(list, catalog, i -> me.owns(i.id()), item -> {
			String problem = problem(item);
			return problem != null ? StyleChips.disabled(item, problem, null) : StyleChips.chip(item,
					StyleChips.label(catalog, item), null, () -> wears(item),
					StyleChips.tooltip(item.name(), StyleChips.describe(catalog, item), ChatFormatting.GRAY),
					() -> toggle(item));
		});
		ShopItem font = draft() == null ? null : catalog.shopItem(draft().font()).orElse(null);
		if (font != null && font.banner()) {
			list.child(WynnUi.heading("Letter color"));
			Box colors = StyleChips.row();
			colors.child(StyleChips.chip(WynnUi.label(WynnFonts.body("Auto contrast"), WynnUi.TEXT), null,
					() -> draft().letterColor() == null, Component.literal("White or black for best contrast"),
					() -> setDraft(new TitleStyle(draft().source(), draft().formats(), draft().font(), draft().color(), null))));
			for (ShopItem color : catalog.shop()) if (ShopItem.COLOR.equals(color.kind())) {
				colors.child(StyleChips.chip(StyleChips.label(catalog, color), null,
						() -> color.color().equals(draft().letterColor()), Component.literal(color.name()),
						() -> setDraft(new TitleStyle(draft().source(), draft().formats(), draft().font(), draft().color(), color.color()))));
			}
			list.child(colors);
		}
		if (adjustment != null) list.child(WynnUi.label(WynnFonts.body(adjustment), WynnUi.TEXT_DIM).maxWidth(235));
		if (me.owned().isEmpty()) {
			list.child(WynnUi.label(WynnFonts.body("Buy colors, palettes and formats on the Shop tab."), WynnUi.TEXT_DIM)
					.maxWidth(220)
					.margins(Insets.top(4)));
		}
		return list;
	}

	// ── Drafts ─────────────────────────────────────────────────────────────

	private TitleStyle draft() {
		return drafts[slot.ordinal()];
	}

	private void setDraft(TitleStyle style) {
		TitleStyle normalized = style == null || style.isEmpty() ? null : style;
		if (!Objects.equals(drafts[slot.ordinal()], normalized)) {
			drafts[slot.ordinal()] = normalized;
			WynnUi.playClick();
			refresh();
		}
	}

	private boolean wears(ShopItem item) {
		TitleStyle draft = draft();
		if (draft == null) return false;
		if (ShopItem.FONT.equals(item.kind())) return item.id().equals(draft.font());
		return item.isSource() ? item.id().equals(draft.source()) : draft.formats().contains(item.id());
	}


	private String problem(ShopItem item) {
		String missing = StyleChips.fontProblem(item, slot == Slot.PREFIX ? prefixWord : suffixWord);
		if (missing != null) return missing;
		ShopItem font = draft() == null ? null : catalog.shopItem(draft().font()).orElse(null);
		if (font != null && !ShopItem.FONT.equals(item.kind()) && !font.allows(item))
			return font.name() + (ShopItem.FORMAT.equals(item.kind()) ? ": formats unavailable" : ": solid colors only");
		return null;
	}

	private void toggle(ShopItem item) {
		if (problem(item) != null) return;
		TitleStyle d = draft() == null ? new TitleStyle(null, List.of()) : draft();
		adjustment = null;
		if (ShopItem.FONT.equals(item.kind())) {
			if (item.id().equals(d.font())) {
				setDraft(new TitleStyle(d.source(), d.formats(), null, null, null));
				return;
			}
			boolean dropsEffect = d.source() != null && !item.allows(catalog.shopItem(d.source()).orElseThrow());
			boolean dropsFormats = !Boolean.TRUE.equals(item.formats()) && !d.formats().isEmpty();
			if (dropsEffect || dropsFormats) {
				adjustment = dropsEffect ? "On Wear: keep the first palette color; remove the effect" : "On Wear: remove formats";
				adjustment += dropsEffect && dropsFormats ? " and formats." : ".";
			}
			setDraft(catalog.selectFont(d, item));
		} else if (item.isSource()) {
			setDraft(new TitleStyle(item.id().equals(d.source()) ? null : item.id(), d.formats(), d.font(), null, d.letterColor()));
		} else {
			List<String> formats = new ArrayList<>(d.formats());
			if (!formats.remove(item.id())) formats.add(item.id());
			setDraft(new TitleStyle(d.source(), formats, d.font(), d.color(), d.letterColor()));
		}
	}

	private boolean changed() {
		return !Objects.equals(drafts[0], me.styles().prefix()) || !Objects.equals(drafts[1], me.styles().suffix());
	}

	// ── Footer ─────────────────────────────────────────────────────────────

	private void refreshFooter() {
		footer.clearChildren();
		footer.child(WynnUi.button(saving ? "Saving..." : "Wear", 80, changed() && !saving, this::wear));
		if (error != null) {
			footer.child(WynnUi.label(WynnFonts.body(error), WynnUi.ERROR));
		}
	}

	private void wear() {
		saving = true;
		refreshFooter();
		WynnTitles.account().setStyle(drafts[0], drafts[1]).whenComplete((updated, t) -> Mc.run(() -> {
			saving = false;
			if (t != null) {
				error = "Could not wear: " + ApiException.describe(t);
				refreshFooter();
				return;
			}
			error = null;
			reset(updated);
			onSaved.accept(updated);
		}));
	}
}
