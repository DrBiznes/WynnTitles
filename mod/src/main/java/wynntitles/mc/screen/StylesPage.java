package wynntitles.mc.screen;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
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
	private FlowLayout slots;
	private FlowLayout footer;

	StylesPage(Consumer<Me> onSaved) {
		this.onSaved = onSaved;
	}

	/** Goes back to what is worn, e.g. after saving or when the profile first loads. */
	void reset(Me me) {
		drafts[0] = me.styles().prefix();
		drafts[1] = me.styles().suffix();
	}

	/** The words are the ones picked on the titles tab, equipped or not. */
	FlowLayout build(Catalog catalog, Me me, TitleWord prefixWord, TitleWord suffixWord, int width, int height) {
		this.catalog = catalog;
		this.me = me;
		this.prefixWord = prefixWord;
		this.suffixWord = suffixWord;
		// Built fresh each time: owo does not draw a layout again once its parent has removed it.
		footer = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		footer.gap(8);
		footer.verticalAlignment(VerticalAlignment.CENTER);

		FlowLayout page = UIContainers.verticalFlow(Sizing.fixed(width), Sizing.fixed(height));
		page.gap(5);
		page.child(StyleChips.nameplate(this::preview, me.username()));

		FlowLayout columns = UIContainers.horizontalFlow(Sizing.fill(), Sizing.expand());
		columns.gap(8);
		FlowLayout left = UIContainers.verticalFlow(Sizing.fixed(SLOT_WIDTH), Sizing.fill());
		left.gap(3);
		left.child(WynnUi.heading("Slot").margins(Insets.left(2)));
		slots = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		left.child(WynnUi.well(Sizing.fill(), Sizing.expand(), slots));
		columns.child(left);

		FlowLayout right = UIContainers.verticalFlow(Sizing.expand(), Sizing.fill());
		right.gap(3);
		right.child(WynnUi.heading("Your styles").margins(Insets.left(2)));
		right.child(WynnUi.well(Sizing.fill(), Sizing.expand(), owned()));
		columns.child(right);
		page.child(columns);

		refresh();
		return page;
	}

	/** The actions for the panel's footer, from the last build. */
	FlowLayout footer() {
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

	private FlowLayout slotRow(Slot s, TitleWord word) {
		FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		row.gap(2);
		row.padding(Insets.of(3, 3, 5, 2));
		FlowLayout heading = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
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

	private FlowLayout owned() {
		FlowLayout list = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		list.gap(3);
		list.padding(Insets.of(2, 2, 3, 2));

		list.child(WynnUi.heading("Look"));
		FlowLayout look = StyleChips.row();
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
