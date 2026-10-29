package wynntitles.mc.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
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
import wynntitles.WynnTitles;
import wynntitles.api.ApiException;
import wynntitles.api.ApiTypes.Me;
import wynntitles.catalog.Catalog;
import wynntitles.mc.Mc;
import wynntitles.mc.TitleRenderer;
import wynntitles.mc.WynnFonts;
import wynntitles.titles.Title;
import wynntitles.titles.TitleStyle;
import wynntitles.titles.TitleWord;

/**
 * The in-game home of WynnTitles, laid out like Wynncraft's raid start screen:
 * a ribbon with the title, a stone tab bar to switch between picking a title,
 * styling it, buying styles and browsing achievements, the page, and its
 * actions along the bottom.
 */
public class TitlesScreen extends BaseOwoScreen<FlowLayout> {
	// The panel is one picture (PANEL_W/H in draw-ui-sprites.py), sized to fit a
	// 427x240 GUI (854x480 at GUI scale 2). The content stays inside the frame,
	// clear of the pillars.
	private static final int PANEL_WIDTH = 416;
	private static final int PANEL_HEIGHT = 229;
	private static final int SIDE_INSET = 22;
	private static final int BODY_WIDTH = PANEL_WIDTH - 2 * SIDE_INSET;
	// Leaves the footer midway between the page and the pinstripe above the ledge.
	private static final int BODY_HEIGHT = 141;
	/** The tab bar centres the eye between two equal halves, whatever the tabs' widths. */
	private static final int EYE_WIDTH = 14;
	private static final int TAB_GAP = 10;
	private static final int TAB_SIDE = (BODY_WIDTH - EYE_WIDTH - 2 * TAB_GAP) / 2;
	private static final int COLUMN_WIDTH = (BODY_WIDTH - 8) / 2;
	private enum Tab {
		TITLES("Titles"),
		STYLES("Styles"),
		SHOP("Shop"),
		ACHIEVEMENTS("Achievements");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private Tab tab = Tab.TITLES;
	private AchievementsPage achievements = new AchievementsPage(a -> WynnTitles.tracker().canDetect(a));
	private final StylesPage styles = new StylesPage(this::profileSaved);
	private final ShopPage shop = new ShopPage(this::profileSaved);
	private Me me;
	private String error;
	private String selectedPrefix;
	private String selectedSuffix;
	private boolean saving;

	private FlowLayout header;
	private FlowLayout tabBar;
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

	/** Opens straight to the styles tab. */
	public static TitlesScreen styles() {
		TitlesScreen screen = new TitlesScreen();
		screen.tab = Tab.STYLES;
		return screen;
	}

	/** Opens straight to the shop tab. */
	public static TitlesScreen shop() {
		TitlesScreen screen = new TitlesScreen();
		screen.tab = Tab.SHOP;
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

		FlowLayout panel = UIContainers.verticalFlow(Sizing.fixed(PANEL_WIDTH), Sizing.fixed(PANEL_HEIGHT));
		panel.gap(4);
		panel.surface(WynnUi.surface(WynnUi.PANEL))
				.padding(Insets.of(0, 0, SIDE_INSET, SIDE_INSET))
				.horizontalAlignment(HorizontalAlignment.CENTER);
		// The ribbon sits across the top edge of the field.
		header = UIContainers.horizontalFlow(Sizing.content(), Sizing.fixed(20));
		tabBar = UIContainers.horizontalFlow(Sizing.fill(), Sizing.fixed(18));
		tabBar.gap(TAB_GAP);
		tabBar.surface(WynnUi.surface(WynnUi.BAR))
				.horizontalAlignment(HorizontalAlignment.CENTER)
				.verticalAlignment(VerticalAlignment.CENTER);
		body = UIContainers.verticalFlow(Sizing.fill(), Sizing.fixed(BODY_HEIGHT));
		body.gap(5);
		body.horizontalAlignment(HorizontalAlignment.CENTER).verticalAlignment(VerticalAlignment.TOP);
		footer = UIContainers.horizontalFlow(Sizing.fill(), Sizing.fixed(WynnUi.BUTTON_HEIGHT));
		footer.gap(8);
		footer.horizontalAlignment(HorizontalAlignment.CENTER).verticalAlignment(VerticalAlignment.CENTER);
		panel.child(header).child(tabBar).child(body).child(footer);

		// Rise into place, like a Wynncraft menu opening.
		panel.positioning(Positioning.relative(50, 58));
		panel.positioning().animate(220, Easing.CUBIC, Positioning.relative(50, 50)).forwards();
		root.child(panel);

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
			styles.reset(me);
		}
	}

	/** After wearing, buying or refunding: a refund also takes styles off. */
	private void profileSaved(Me updated) {
		me = updated;
		styles.reset(updated);
		rebuild();
	}

	private void rebuild() {
		header.clearChildren();
		header.child(WynnUi.ribbon(tab.label));
		tabBar.clearChildren();
		// What you wear on one side of the eye, how you earn and spend points on the other.
		tabBar.child(tabSide(HorizontalAlignment.RIGHT, Tab.TITLES, Tab.STYLES));
		tabBar.child(UIContainers.horizontalFlow(Sizing.fixed(EYE_WIDTH), Sizing.fixed(8)).surface(WynnUi.surface(WynnUi.EYE)));
		tabBar.child(tabSide(HorizontalAlignment.LEFT, Tab.SHOP, Tab.ACHIEVEMENTS));
		body.clearChildren();
		footer.clearChildren();

		Catalog catalog = WynnTitles.catalogs().catalog();
		if (me == null || (tab != Tab.TITLES && catalog == null)) {
			body.verticalAlignment(VerticalAlignment.CENTER);
			body.child(me == null && error != null
					? WynnUi.label(WynnFonts.body(error), WynnUi.ERROR).maxWidth(PANEL_WIDTH - 40)
					: WynnUi.label(WynnFonts.body(me == null ? "Logging in..." : "Loading achievements..."), WynnUi.TEXT_DIM));
			return;
		}
		body.verticalAlignment(VerticalAlignment.TOP);
		if (tab == Tab.TITLES) {
			buildTitles();
		} else if (tab == Tab.STYLES) {
			body.child(styles.build(catalog, me, find(me.words().prefixes(), selectedPrefix),
					find(me.words().suffixes(), selectedSuffix), BODY_WIDTH, BODY_HEIGHT));
			footer.child(styles.footer());
		} else if (tab == Tab.SHOP) {
			body.child(shop.build(catalog, me, find(me.words().prefixes(), selectedPrefix),
					find(me.words().suffixes(), selectedSuffix), BODY_WIDTH, BODY_HEIGHT));
			footer.child(shop.footer());
		} else {
			body.child(achievements.build(catalog, me, WynnTitles.stats().stats(), BODY_WIDTH, BODY_HEIGHT));
			footer.child(achievements.footer(me));
			if (error != null) {
				footer.child(WynnUi.label(WynnFonts.body(error), WynnUi.ERROR));
			}
		}
	}

	private FlowLayout tabSide(HorizontalAlignment toward, Tab... tabs) {
		FlowLayout side = UIContainers.horizontalFlow(Sizing.fixed(TAB_SIDE), Sizing.fixed(14));
		side.gap(TAB_GAP);
		side.horizontalAlignment(toward).verticalAlignment(VerticalAlignment.CENTER);
		for (Tab t : tabs) {
			side.child(tabButton(t));
		}
		return side;
	}

	private FlowLayout tabButton(Tab t) {
		// The name in a frame carved into the bar, so it reads as a button; the
		// open tab's frame is inset with a gold rim.
		boolean selected = t == tab;
		boolean[] hovered = {false};
		FlowLayout button = UIContainers.horizontalFlow(Sizing.content(), Sizing.fixed(14));
		button.padding(Insets.of(3, 0, 7, 6)).verticalAlignment(VerticalAlignment.TOP);
		button.surface((graphics, c) -> WynnUi.surface(selected ? WynnUi.TAB_SELECTED
				: hovered[0] ? WynnUi.TAB_HOVER : WynnUi.TAB).draw(graphics, c));
		button.child(WynnUi.label(WynnFonts.small(t.label), selected ? WynnUi.GOLD : WynnUi.TEXT));
		button.cursorStyle(CursorStyle.HAND);
		button.mouseEnter().subscribe(() -> hovered[0] = true);
		button.mouseLeave().subscribe(() -> hovered[0] = false);
		button.mouseDown().subscribe((click, doubled) -> {
			switchTab(t);
			return true;
		});
		return button;
	}

	// ── Titles ─────────────────────────────────────────────────────────────

	private void buildTitles() {
		// How the title will look over your head: the title, then your name.
		body.child(StyleChips.nameplate(this::preview, me.username()));

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
		Title title = Title.of(
				styled(find(me.words().prefixes(), selectedPrefix), me.styles().prefix()),
				styled(find(me.words().suffixes(), selectedSuffix), me.styles().suffix()));
		return title == null
				? WynnFonts.body("No title").withColor(WynnUi.TEXT_DIM)
				: TitleRenderer.render(title);
	}

	/** The word as it would be worn: in the slot's shop style, or its native one. */
	private static TitleWord styled(TitleWord word, TitleStyle style) {
		Catalog catalog = WynnTitles.catalogs().catalog();
		return catalog == null ? word : catalog.restyle(word, style);
	}

	/** Lists words in their native styles; only the preview shows the slot's shop style. */
	private FlowLayout wordColumn(String heading, List<TitleWord> words,
			Supplier<String> selected, Consumer<String> select) {
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
