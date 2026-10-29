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
		ownedList = WynnUi.well(Size.fill(), Size.expand(), owned()).scrollTo(offset);
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

		StyleChips.addGroups(list, catalog, i -> me.owns(i.id()), item -> StyleChips.chip(item,
				StyleChips.label(catalog, item), null, () -> wears(item),
				StyleChips.tooltip(item.name(), StyleChips.describe(catalog, item), ChatFormatting.GRAY),
				() -> toggle(item)));
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
		return draft != null && (item.isSource() ? item.id().equals(draft.source()) : draft.formats().contains(item.id()));
	}

	/** A source replaces the current one and a format toggles; picking what is already worn takes it off. */
	private void toggle(ShopItem item) {
		TitleStyle draft = draft() == null ? new TitleStyle(null, List.of()) : draft();
		if (item.isSource()) {
			setDraft(new TitleStyle(item.id().equals(draft.source()) ? null : item.id(), draft.formats()));
		} else {
			List<String> formats = new ArrayList<>(draft.formats());
			if (!formats.remove(item.id())) {
				formats.add(item.id());
			}
			setDraft(new TitleStyle(draft.source(), formats));
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
