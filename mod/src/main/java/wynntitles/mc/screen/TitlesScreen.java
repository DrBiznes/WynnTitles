package wynntitles.mc.screen;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import wynntitles.WynnTitles;
import wynntitles.api.ApiException;
import wynntitles.api.ApiTypes.Me;
import wynntitles.catalog.Catalog;
import wynntitles.mc.Mc;
import wynntitles.mc.TitleRenderer;
import wynntitles.mc.WynnFonts;
import wynntitles.mc.screen.ui.Align;
import wynntitles.mc.screen.ui.Box;
import wynntitles.mc.screen.ui.Element;
import wynntitles.mc.screen.ui.Insets;
import wynntitles.mc.screen.ui.Size;
import wynntitles.mc.screen.ui.Tween;
import wynntitles.mc.screen.ui.UiScreen;
import wynntitles.titles.Title;
import wynntitles.titles.TitleStyle;
import wynntitles.titles.TitleWord;

/**
 * The in-game home of WynnTitles, laid out like Wynncraft's raid start screen:
 * a ribbon with the title, a stone tab bar to switch between picking a title,
 * styling it, buying styles and browsing achievements, the page, and its
 * actions along the bottom. A new page slides in from the side of its tab.
 */
public class TitlesScreen extends UiScreen {
	// The panel is one picture (PANEL_W/H in draw-ui-sprites.py), sized to fit a
	// 427x240 GUI (854x480 at GUI scale 2). The content stays inside the frame,
	// clear of the pillars.
	private static final int PANEL_WIDTH = 416;
	private static final int PANEL_HEIGHT = 229;
	private static final int SIDE_INSET = 22;
	private static final int BODY_WIDTH = PANEL_WIDTH - 2 * SIDE_INSET;
	// Leaves the footer midway between the page and the pinstripe above the ledge.
	private static final int BODY_HEIGHT = 141;
	private static final int COLUMN_WIDTH = (BODY_WIDTH - 8) / 2;
	/** How far a page slides in from, in pixels. */
	private static final int SLIDE = 18;

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
	private AchievementsPage achievements =
			new AchievementsPage(a -> WynnTitles.tracker().canDetect(a), a -> WynnTitles.tracker().progress(a));
	private final StylesPage styles = new StylesPage(this::profileSaved);
	private final ShopPage shop = new ShopPage(this::profileSaved);
	private Me me;
	private String error;
	private String selectedPrefix;
	private String selectedSuffix;
	private boolean saving;

	private Box header;
	private Box body;
	private Box footer;
	/** Rising into place on open, like a Wynncraft menu. */
	private final Tween rise = new Tween(220).restart();
	private final Tween slide = new Tween(180);
	/** Which side the last page slid in from: 1 the right, -1 the left. */
	private int slideFrom;

	public TitlesScreen() {
		super(Component.literal("WynnTitles"), PANEL_WIDTH, PANEL_HEIGHT);
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
	public static TitlesScreen achievements(
			Predicate<Catalog.Achievement> canDetect, Function<Catalog.Achievement, String> progress) {
		TitlesScreen screen = achievements();
		screen.achievements = new AchievementsPage(canDetect, progress);
		return screen;
	}

	@Override
	protected Element build() {
		Box panel = Box.column(Size.fixed(PANEL_WIDTH), Size.fixed(PANEL_HEIGHT));
		panel.gap(4);
		panel.background(WynnUi.surface(WynnUi.PANEL))
				.padding(Insets.of(0, 0, SIDE_INSET, SIDE_INSET))
				.alignX(Align.CENTER);
		// The ribbon sits across the top edge of the field.
		header = Box.row(Size.content(), Size.fixed(20));
		// What you wear on one side of the eye, how you earn and spend points on the other.
		TabBar tabBar = new TabBar(Arrays.stream(Tab.values()).map(t -> t.label).toList(), 2,
				() -> tab.ordinal(), i -> switchTab(Tab.values()[i]));
		// The page and its actions are clipped, so they can slide in under the frame.
		body = Box.column(Size.fill(), Size.fixed(BODY_HEIGHT));
		body.gap(5);
		body.alignX(Align.CENTER).alignY(Align.START).clip(true);
		footer = Box.row(Size.fill(), Size.fixed(WynnUi.BUTTON_HEIGHT));
		footer.gap(8);
		footer.alignX(Align.CENTER).alignY(Align.CENTER).clip(true);
		panel.child(header).child(tabBar).child(body).child(footer);

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
		return panel;
	}

	@Override
	protected float drop() {
		return 0.08f * (1 - rise.value());
	}

	@Override
	protected void animate() {
		int shift = slide.running() ? Math.round(slideFrom * SLIDE * (1 - slide.value())) : 0;
		body.shift(shift);
		footer.shift(shift);
	}

	private void switchTab(Tab next) {
		if (tab != next) {
			if (tab == Tab.TITLES) {
				// Picks are only kept by equipping them; elsewhere the preview shows what you wear.
				dropTitlePicks();
			}
			slideFrom = next.ordinal() > tab.ordinal() ? 1 : -1;
			slide.restart();
			tab = next;
			WynnUi.playPageTurn();
			rebuild();
		}
	}

	/** Number keys 1 to 4 open the tabs in order. */
	@Override
	public boolean keyPressed(KeyEvent event) {
		int index = event.key() - GLFW.GLFW_KEY_1;
		if (index >= 0 && index < Tab.values().length) {
			switchTab(Tab.values()[index]);
			return true;
		}
		return super.keyPressed(event);
	}

	private void resetSelection() {
		if (me != null) {
			dropTitlePicks();
			styles.reset(me);
		}
	}

	private void dropTitlePicks() {
		if (me != null) {
			selectedPrefix = me.equipped().prefix();
			selectedSuffix = me.equipped().suffix();
		}
	}

	/** After wearing or buying. */
	private void profileSaved(Me updated) {
		me = updated;
		styles.reset(updated);
		rebuild();
	}

	private void rebuild() {
		header.clearChildren();
		header.child(WynnUi.ribbon(tab.label));
		body.clearChildren();
		footer.clearChildren();

		Catalog catalog = WynnTitles.catalogs().catalog();
		if (me == null || (tab != Tab.TITLES && catalog == null)) {
			body.alignY(Align.CENTER);
			body.child(me == null && error != null
					? WynnUi.label(WynnFonts.body(error), WynnUi.ERROR).maxWidth(PANEL_WIDTH - 40)
					: WynnUi.label(WynnFonts.body(me == null ? "Logging in..." : "Loading achievements..."), WynnUi.TEXT_DIM));
			return;
		}
		body.alignY(Align.START);
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

	// ── Titles ─────────────────────────────────────────────────────────────

	private void buildTitles() {
		// How the title will look over your head.
		body.child(StyleChips.nameplate(this::preview));

		Box columns = Box.row(Size.fill(), Size.expand());
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
	private Box wordColumn(String heading, List<TitleWord> words,
			Supplier<String> selected, Consumer<String> select) {
		Box column = Box.column(Size.fixed(COLUMN_WIDTH), Size.fill());
		column.gap(3);
		column.child(WynnUi.heading(heading).margins(Insets.left(2)));

		Box rows = Box.column(Size.fill(), Size.content());
		rows.child(wordRow(null, 0, selected, select));
		for (int i = 0; i < words.size(); i++) {
			rows.child(wordRow(words.get(i), i + 1, selected, select));
		}
		column.child(WynnUi.well(Size.fill(), Size.expand(), rows));
		return column;
	}

	/**
	 * Rows restyle themselves as the selection changes, so picking one keeps the
	 * list's scroll. Every other row is a shade lighter, so long lists are easy to follow.
	 */
	private Box wordRow(TitleWord word, int index, Supplier<String> selected, Consumer<String> select) {
		String text = word == null ? null : word.text();
		Box row = Box.row(Size.fill(), Size.fixed(13));
		row.padding(Insets.of(2, 0, 5, 0)).alignY(Align.CENTER);
		row.child(word == null
				? WynnUi.label(WynnFonts.body("None"), WynnUi.TEXT_DIM)
				: new TitleText(() -> TitleRenderer.render(word), 1f, WynnUi.TEXT));
		StyleChips.clickable(row, index % 2 == 1 ? WynnUi.STRIPE : null, () -> Objects.equals(selected.get(), text),
				WynnUi.ROW_SELECTED, WynnUi.ROW_HOVER, () -> {
					if (!Objects.equals(selected.get(), text)) {
						select.accept(text);
						WynnUi.playClick();
						titlesFooter();
					}
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
