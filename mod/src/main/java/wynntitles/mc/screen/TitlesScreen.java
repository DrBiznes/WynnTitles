package wynntitles.mc.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.Easing;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import wynntitles.WynnTitles;
import wynntitles.api.ApiException;
import wynntitles.api.ApiTypes.Me;
import wynntitles.catalog.Catalog;
import wynntitles.mc.Icons;
import wynntitles.mc.Mc;
import wynntitles.mc.TitleRenderer;
import wynntitles.mc.WynnFonts;
import wynntitles.titles.Title;
import wynntitles.titles.TitleWord;

/**
 * The in-game home of WynnTitles, styled like a Wynncraft menu: a framed book
 * with bookmark tabs for picking a title and browsing achievements.
 */
public class TitlesScreen extends BaseOwoScreen<FlowLayout> {
	// Sized to fit a 427x240 GUI (854x480 at GUI scale 2) with the tabs.
	private static final int PANEL_WIDTH = 372;
	private static final int BODY_WIDTH = PANEL_WIDTH - 20;
	private static final int BODY_HEIGHT = 150;
	private static final int COLUMN_WIDTH = (BODY_WIDTH - 8) / 2;
	private static final Identifier TAB = WynnUi.sprite("tab");
	private static final Identifier TAB_SELECTED = WynnUi.sprite("tab_selected");

	/** Tab icons name Wynncraft models, like achievement icons; the gametest checks they resolve. */
	public static final Catalog.Icon TITLES_ICON = new Catalog.Icon("wynn/skin/hat/jagged_crown", "minecraft:name_tag");
	public static final Catalog.Icon ACHIEVEMENTS_ICON = new Catalog.Icon("wynn/player/content_book", "minecraft:writable_book");

	private enum Tab {
		TITLES("Titles", TITLES_ICON),
		ACHIEVEMENTS("Achievements", ACHIEVEMENTS_ICON);

		final String label;
		final Catalog.Icon icon;

		Tab(String label, Catalog.Icon icon) {
			this.label = label;
			this.icon = icon;
		}
	}

	private Tab tab = Tab.TITLES;
	private AchievementsPage achievements = new AchievementsPage(a -> WynnTitles.tracker().canDetect(a));
	private Me me;
	private String error;
	private String selectedPrefix;
	private String selectedSuffix;
	private boolean saving;

	private FlowLayout tabs;
	private FlowLayout header;
	private FlowLayout body;
	private FlowLayout footer;

	public TitlesScreen() {
		super(Component.literal("WynnTitles"));
	}

	/** Opens straight to the achievements tab. */
	public static TitlesScreen achievements() {
		TitlesScreen screen = new TitlesScreen();
		screen.tab = Tab.ACHIEVEMENTS;
		return screen;
	}

	/** The achievements tab with a stand-in for the tracker, for tests. */
	public static TitlesScreen achievements(Predicate<Catalog.Achievement> canDetect) {
		TitlesScreen screen = achievements();
		screen.achievements = new AchievementsPage(canDetect);
		return screen;
	}

	@Override
	protected OwoUIAdapter<FlowLayout> createAdapter() {
		return OwoUIAdapter.create(this, UIContainers::verticalFlow);
	}

	@Override
	protected void build(FlowLayout root) {
		root.surface(Surface.VANILLA_TRANSLUCENT);

		// The tabs come first so the panel's frame covers their inner ends.
		FlowLayout book = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		tabs = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
		tabs.gap(2).horizontalAlignment(HorizontalAlignment.RIGHT).margins(Insets.of(24, 0, 0, -6));
		book.child(tabs);

		FlowLayout panel = UIContainers.verticalFlow(Sizing.fixed(PANEL_WIDTH), Sizing.content());
		panel.gap(4);
		panel.surface(WynnUi.surface(WynnUi.PANEL))
				.padding(Insets.of(2, 9, 10, 10))
				.horizontalAlignment(HorizontalAlignment.CENTER);
		// The plaque sits over the top of the frame, like a nameboard.
		header = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		body = UIContainers.verticalFlow(Sizing.fill(), Sizing.fixed(BODY_HEIGHT));
		body.gap(5);
		body.horizontalAlignment(HorizontalAlignment.CENTER).verticalAlignment(VerticalAlignment.TOP);
		footer = UIContainers.horizontalFlow(Sizing.fill(), Sizing.fixed(WynnUi.BUTTON_HEIGHT));
		footer.gap(8);
		footer.horizontalAlignment(HorizontalAlignment.CENTER).verticalAlignment(VerticalAlignment.CENTER);
		panel.child(header).child(body).child(footer);
		book.child(panel);

		// Rise into place, like a Wynncraft menu opening.
		book.positioning(Positioning.relative(50, 58));
		book.positioning().animate(220, Easing.CUBIC, Positioning.relative(50, 50)).forwards();
		root.child(book);

		me = WynnTitles.account().me();
		resetSelection();
		rebuild();

		// Rarity is optional: without it the list shows placeholders, not an error.
		WynnTitles.stats().refresh().whenComplete((stats, t) -> Mc.run(() -> {
			if (t == null && tab == Tab.ACHIEVEMENTS && me != null) {
				rebuild();
			}
		}));

		WynnTitles.account().refresh().whenComplete((loaded, t) -> Mc.run(() -> {
			if (t != null) {
				error = ApiException.describe(t);
			} else {
				boolean firstLoad = me == null;
				me = loaded;
				error = null;
				if (firstLoad) {
					resetSelection();
				}
			}
			rebuild();
		}));
	}

	private void switchTab(Tab next) {
		if (tab != next) {
			tab = next;
			WynnUi.playPageTurn();
			rebuild();
		}
	}

	private void resetSelection() {
		if (me != null) {
			selectedPrefix = me.equipped().prefix();
			selectedSuffix = me.equipped().suffix();
		}
	}

	private void rebuild() {
		tabs.clearChildren();
		for (Tab t : Tab.values()) {
			tabs.child(tabButton(t));
		}
		header.clearChildren();
		header.child(WynnUi.plaque(tab.label));
		body.clearChildren();
		footer.clearChildren();

		Catalog catalog = WynnTitles.catalogs().catalog();
		if (me == null || (tab == Tab.ACHIEVEMENTS && catalog == null)) {
			body.verticalAlignment(VerticalAlignment.CENTER);
			body.child(me == null && error != null
					? WynnUi.label(WynnFonts.body(error), WynnUi.ERROR).maxWidth(PANEL_WIDTH - 40)
					: WynnUi.label(WynnFonts.body(me == null ? "Logging in..." : "Loading achievements..."), WynnUi.TEXT_DIM));
			return;
		}
		body.verticalAlignment(VerticalAlignment.TOP);
		if (tab == Tab.TITLES) {
			buildTitles();
		} else {
			body.child(achievements.build(catalog, me, WynnTitles.stats().stats(), BODY_WIDTH, BODY_HEIGHT));
			footer.child(achievements.footer(me));
			if (error != null) {
				footer.child(WynnUi.label(WynnFonts.body(error), WynnUi.ERROR));
			}
		}
	}

	private FlowLayout tabButton(Tab t) {
		boolean selected = t == tab;
		FlowLayout button = UIContainers.horizontalFlow(Sizing.fixed(selected ? 30 : 26), Sizing.fixed(24));
		button.surface(WynnUi.surface(selected ? TAB_SELECTED : TAB)).padding(Insets.of(4, 0, 5, 0));
		button.child(UIComponents.item(Icons.stack(t.icon)));
		button.tooltip(WynnFonts.body(t.label));
		button.cursorStyle(CursorStyle.HAND);
		button.mouseDown().subscribe((click, doubled) -> {
			switchTab(t);
			return true;
		});
		return button;
	}

	// ── Titles ─────────────────────────────────────────────────────────────

	private void buildTitles() {
		// How the title will look over your head: the title, then your name.
		FlowLayout nameplate = UIContainers.verticalFlow(Sizing.fill(), Sizing.fixed(36));
		nameplate.gap(2);
		nameplate.surface(WynnUi.surface(WynnUi.NAMEPLATE))
				.horizontalAlignment(HorizontalAlignment.CENTER)
				.verticalAlignment(VerticalAlignment.CENTER);
		nameplate.child(new TitleText(this::preview, 1.5f, WynnUi.TEXT));
		nameplate.child(WynnUi.label(Component.literal(me.username()), 0xFFFFFF));
		body.child(nameplate);

		FlowLayout columns = UIContainers.horizontalFlow(Sizing.fill(), Sizing.expand());
		columns.gap(8);
		columns.child(wordColumn("Prefix", me.words().prefixes(), () -> selectedPrefix, w -> selectedPrefix = w));
		columns.child(wordColumn("Suffix", me.words().suffixes(), () -> selectedSuffix, w -> selectedSuffix = w));
		body.child(columns);
		titlesFooter();
	}

	private void titlesFooter() {
		footer.clearChildren();
		if (me.words().prefixes().isEmpty() && me.words().suffixes().isEmpty()) {
			footer.child(WynnUi.label(WynnFonts.body("Unlock achievements to earn title words."), WynnUi.TEXT_DIM));
		}
		boolean changed = !Objects.equals(selectedPrefix, me.equipped().prefix())
				|| !Objects.equals(selectedSuffix, me.equipped().suffix());
		footer.child(WynnUi.button(saving ? "Saving..." : "Equip", 80, changed && !saving, this::save));
		if (error != null) {
			footer.child(WynnUi.label(WynnFonts.body(error), WynnUi.ERROR));
		}
	}

	private Component preview() {
		Title title = Title.of(find(me.words().prefixes(), selectedPrefix), find(me.words().suffixes(), selectedSuffix));
		return title == null
				? WynnFonts.body("No title").withColor(WynnUi.TEXT_DIM)
				: TitleRenderer.render(title);
	}

	private FlowLayout wordColumn(String heading, List<TitleWord> words, Supplier<String> selected, Consumer<String> select) {
		FlowLayout column = UIContainers.verticalFlow(Sizing.fixed(COLUMN_WIDTH), Sizing.fill());
		column.gap(3);
		column.child(WynnUi.heading(heading).margins(Insets.left(2)));

		FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		rows.child(wordRow(null, selected, select));
		for (TitleWord word : words) {
			rows.child(wordRow(word, selected, select));
		}
		column.child(WynnUi.well(Sizing.fill(), Sizing.expand(), rows));
		return column;
	}

	/** Rows restyle themselves as the selection changes, so picking one keeps the list's scroll. */
	private FlowLayout wordRow(TitleWord word, Supplier<String> selected, Consumer<String> select) {
		String text = word == null ? null : word.text();
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(), Sizing.fixed(13));
		row.padding(Insets.of(2, 0, 5, 0)).verticalAlignment(VerticalAlignment.CENTER);
		row.child(word == null
				? WynnUi.label(WynnFonts.body("None"), WynnUi.TEXT_DIM)
				: new TitleText(() -> TitleRenderer.render(word), 1f, WynnUi.TEXT));

		boolean[] hovered = {false};
		row.surface((graphics, c) -> {
			if (Objects.equals(selected.get(), text)) {
				WynnUi.surface(WynnUi.ROW_SELECTED).draw(graphics, c);
			} else if (hovered[0]) {
				WynnUi.surface(WynnUi.ROW_HOVER).draw(graphics, c);
			}
		});
		row.cursorStyle(CursorStyle.HAND);
		row.mouseEnter().subscribe(() -> hovered[0] = true);
		row.mouseLeave().subscribe(() -> hovered[0] = false);
		row.mouseDown().subscribe((click, doubled) -> {
			if (!Objects.equals(selected.get(), text)) {
				select.accept(text);
				WynnUi.playClick();
				titlesFooter();
			}
			return true;
		});
		return row;
	}

	private void save() {
		saving = true;
		titlesFooter();
		WynnTitles.account().setTitle(selectedPrefix, selectedSuffix).whenComplete((updated, t) -> Mc.run(() -> {
			saving = false;
			if (t != null) {
				error = "Could not equip: " + ApiException.describe(t);
			} else {
				me = updated;
				error = null;
				resetSelection();
			}
			// Only the footer changes, so the word lists keep their scroll.
			if (tab == Tab.TITLES) {
				titlesFooter();
			}
		}));
	}

	private static TitleWord find(List<TitleWord> words, String text) {
		if (text == null) {
			return null;
		}
		return words.stream().filter(w -> w.text().equals(text)).findFirst().orElse(null);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
