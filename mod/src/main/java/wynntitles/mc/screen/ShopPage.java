package wynntitles.mc.screen;

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
import wynntitles.mc.screen.ui.Element;
import wynntitles.mc.screen.ui.Insets;
import wynntitles.mc.screen.ui.ScrollView;
import wynntitles.mc.screen.ui.Size;
import wynntitles.titles.Title;
import wynntitles.titles.TitleWord;

/**
 * The shop tab: every style piece, priced in its tooltip. Picking one previews it on
 * your title; bought pieces are worn from the styles tab. Refunding gives every
 * point back, so trying things is never a trap.
 */
final class ShopPage {
	private final Consumer<Me> onSaved;
	private ShopItem selected;
	private boolean saving;
	private boolean confirmRefund;
	private String error;

	private Catalog catalog;
	private Me me;
	private TitleWord prefixWord;
	private TitleWord suffixWord;
	private Box footer;
	private ScrollView items;

	ShopPage(Consumer<Me> onSaved) {
		this.onSaved = onSaved;
	}

	Box build(Catalog catalog, Me me, TitleWord prefixWord, TitleWord suffixWord, int width, int height) {
		this.catalog = catalog;
		this.me = me;
		this.prefixWord = prefixWord;
		this.suffixWord = suffixWord;
		if (selected != null && catalog.shopItem(selected.id()).isEmpty()) {
			selected = null;
		}
		footer = Box.row(Size.content(), Size.content());
		footer.gap(8);
		footer.alignY(Align.CENTER);

		Box page = Box.column(Size.fixed(width), Size.fixed(height));
		page.gap(5);
		page.child(StyleChips.nameplate(this::preview));
		Box heading = Box.row(Size.fill(), Size.content());
		heading.gap(6).margins(Insets.left(2));
		heading.child(WynnUi.heading("Shop"));
		// What the picked piece does, so the animations have names.
		heading.child(new TitleText(this::caption, 1f, WynnUi.TEXT_DIM).sizing(Size.expand(), Size.content()));
		page.child(heading);

		Box list = Box.column(Size.fill(), Size.content());
		list.gap(3);
		list.padding(Insets.of(0, 2, 3, 2));
		StyleChips.addGroups(list, catalog, i -> true, this::chip, true);
		// Buying rebuilds the page; stay where the player was in the list.
		double offset = items == null ? 0 : items.offset();
		items = WynnUi.well(Size.fill(), Size.expand(), list).scrollTo(offset);
		page.child(items);

		refreshFooter();
		return page;
	}

	Box footer() {
		return footer;
	}

	/** Your title with the picked piece on both words, or the piece's name if no title is picked. */
	private Component preview() {
		if (selected == null) {
			return WynnFonts.body("Pick a style to try it on").withColor(WynnUi.TEXT_DIM);
		}
		TitleWord prefix = prefixWord, suffix = suffixWord;
		if (prefix == null && suffix == null) {
			prefix = new TitleWord(selected.name(), null);
		}
		Title title = Title.of(catalog.restyle(prefix, StyleChips.alone(selected)),
				catalog.restyle(suffix, StyleChips.alone(selected)));
		return TitleRenderer.render(title);
	}

	private Component caption() {
		return selected == null ? Component.empty() : WynnFonts.body(StyleChips.describe(catalog, selected));
	}

	private Box chip(ShopItem item) {
		boolean owned = me.owns(item.id());
		// The price is in the tooltip and on the buy button, so the chips stay short.
		Element badge = owned ? StyleChips.ownedMark() : null;
		Component tooltip = StyleChips.tooltip(item.name(), StyleChips.describe(catalog, item), ChatFormatting.GRAY)
				.append(owned
						? Component.literal("\nOwned: wear it from the Styles tab").withStyle(ChatFormatting.GREEN)
						: Component.literal("\n" + StyleChips.points(item.price())).withStyle(ChatFormatting.GOLD));
		return StyleChips.chip(item, StyleChips.label(catalog, item), badge,
				() -> selected != null && selected.id().equals(item.id()), tooltip, () -> select(item));
	}

	private void select(ShopItem item) {
		if (!Objects.equals(selected, item)) {
			selected = item;
			confirmRefund = false;
			WynnUi.playClick();
			refreshFooter();
		}
	}

	private void refreshFooter() {
		footer.clearChildren();
		footer.child(StyleChips.balance(me.balance()));
		if (saving) {
			footer.child(WynnUi.button("Saving...", 104, false, () -> {}));
		} else if (selected == null) {
			footer.child(WynnUi.button("Buy", 104, false, () -> {}));
		} else if (me.owns(selected.id())) {
			footer.child(WynnUi.button("Owned", 104, false, () -> {}));
		} else if (selected.price() > me.balance()) {
			footer.child(WynnUi.button("Need " + (selected.price() - me.balance()) + " more", 104, false, () -> {}));
		} else {
			ShopItem item = selected;
			footer.child(WynnUi.button("Buy for " + StyleChips.points(item.price()), 104, true, () -> buy(item)));
		}
		footer.child(WynnUi.button(confirmRefund ? "Refund all?" : "Refund", 72,
				!saving && !me.owned().isEmpty(), this::refund));
		if (error != null) {
			footer.child(WynnUi.label(WynnFonts.body(error), WynnUi.ERROR));
		}
	}

	private void buy(ShopItem item) {
		saving = true;
		refreshFooter();
		WynnTitles.account().buy(List.of(item.id())).whenComplete((updated, t) -> finish(updated, t, "Could not buy"));
	}

	/** Asks once, then refunds every purchase and puts both slots back to their native style. */
	private void refund() {
		if (!confirmRefund) {
			confirmRefund = true;
			refreshFooter();
			return;
		}
		saving = true;
		refreshFooter();
		WynnTitles.account().resetPurchases().whenComplete((updated, t) -> finish(updated, t, "Could not refund"));
	}

	private void finish(Me updated, Throwable t, String failure) {
		Mc.run(() -> {
			saving = false;
			confirmRefund = false;
			if (t != null) {
				error = failure + ": " + ApiException.describe(t);
				refreshFooter();
				return;
			}
			error = null;
			// Prices and the balance changed, so the screen builds the page again.
			onSaved.accept(updated);
		});
	}
}
