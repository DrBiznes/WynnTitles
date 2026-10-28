package wynntitles.mc.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import wynntitles.WynnTitles;
import wynntitles.api.ApiException;
import wynntitles.api.ApiTypes.Me;
import wynntitles.api.ApiTypes.Unlock;
import wynntitles.catalog.Catalog;
import wynntitles.mc.Mc;
import wynntitles.mc.TitleRenderer;
import wynntitles.titles.Title;
import wynntitles.titles.TitleWord;

/** The in-game home of WynnTitles: pick a title, and browse achievements. */
public class TitlesScreen extends BaseOwoScreen<FlowLayout> {
	private static final Identifier PANEL = sprite("panel");
	private static final Identifier LIST_BG = sprite("list_bg");
	private static final Identifier ITEM_HOVER = sprite("list_item_hover");
	private static final Identifier ITEM_SELECTED = sprite("list_item_selected");

	// Light text: both the panel and the list backgrounds are dark brown.
	private static final int GOLD = 0xE8C050;
	private static final int TEXT = 0xF3E6C8;
	private static final int TEXT_DIM = 0xA88E66;
	private static final int ERROR = 0xFF6B6B;

	private enum Tab { TITLES, ACHIEVEMENTS }

	private Tab tab = Tab.TITLES;
	private Me me;
	private String error;
	private String selectedPrefix;
	private String selectedSuffix;
	private boolean saving;

	private FlowLayout body;
	private LabelComponent status;

	public TitlesScreen() {
		super(Component.literal("WynnTitles"));
	}

	/** Opens straight to the achievements tab. */
	public static TitlesScreen achievements() {
		TitlesScreen screen = new TitlesScreen();
		screen.tab = Tab.ACHIEVEMENTS;
		return screen;
	}

	@Override
	protected OwoUIAdapter<FlowLayout> createAdapter() {
		return OwoUIAdapter.create(this, UIContainers::verticalFlow);
	}

	@Override
	protected void build(FlowLayout root) {
		root.surface(Surface.VANILLA_TRANSLUCENT)
				.horizontalAlignment(HorizontalAlignment.CENTER)
				.verticalAlignment(VerticalAlignment.CENTER);

		FlowLayout panel = UIContainers.verticalFlow(Sizing.fixed(320), Sizing.content());
		panel.surface(spriteSurface(PANEL))
				.padding(Insets.of(8))
				.horizontalAlignment(HorizontalAlignment.CENTER);
		panel.gap(6);

		panel.child(label(Component.literal("✦ WynnTitles ✦"), GOLD));

		FlowLayout tabs = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		tabs.gap(4);
		tabs.child(UIComponents.button(Component.literal("Titles"), b -> switchTab(Tab.TITLES)));
		tabs.child(UIComponents.button(Component.literal("Achievements"), b -> switchTab(Tab.ACHIEVEMENTS)));
		panel.child(tabs);

		body = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		body.horizontalAlignment(HorizontalAlignment.CENTER);
		body.gap(6);
		panel.child(body);

		status = label(Component.empty(), TEXT_DIM);
		panel.child(status);

		root.child(panel);

		me = WynnTitles.account().me();
		resetSelection();
		rebuildBody();

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
			rebuildBody();
		}));
	}

	private void switchTab(Tab next) {
		tab = next;
		rebuildBody();
	}

	private void resetSelection() {
		if (me != null) {
			selectedPrefix = me.equipped().prefix();
			selectedSuffix = me.equipped().suffix();
		}
	}

	private void rebuildBody() {
		body.clearChildren();
		if (me == null) {
			body.child(error == null
					? label(Component.literal("Logging in..."), TEXT_DIM)
					: label(Component.literal(error), ERROR));
			setStatus(Component.empty());
			return;
		}
		if (tab == Tab.TITLES) {
			buildTitlesTab();
		} else {
			buildAchievementsTab();
		}
		setStatus(error == null ? Component.empty() : Component.literal(error).withColor(ERROR));
	}

	// ── Titles ─────────────────────────────────────────────────────────────

	private void buildTitlesTab() {
		body.child(new TitleText(this::preview, 1.5f, TEXT).margins(Insets.vertical(4)));

		FlowLayout columns = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		columns.gap(8);
		columns.child(wordColumn("Prefix", me.words().prefixes(), selectedPrefix, w -> selectedPrefix = w));
		columns.child(wordColumn("Suffix", me.words().suffixes(), selectedSuffix, w -> selectedSuffix = w));
		body.child(columns);

		if (me.words().prefixes().isEmpty() && me.words().suffixes().isEmpty()) {
			body.child(label(Component.literal("Unlock achievements to earn title words."), TEXT_DIM));
		}

		boolean changed = !Objects.equals(selectedPrefix, me.equipped().prefix())
				|| !Objects.equals(selectedSuffix, me.equipped().suffix());
		ButtonComponent save = UIComponents.button(Component.literal(saving ? "Saving..." : "Equip"), b -> save());
		save.active(changed && !saving);
		body.child(save);
	}

	private Component preview() {
		Title title = Title.of(find(me.words().prefixes(), selectedPrefix), find(me.words().suffixes(), selectedSuffix));
		return title == null
				? Component.literal("(no title)").withColor(TEXT_DIM)
				: TitleRenderer.render(title);
	}

	private FlowLayout wordColumn(String heading, List<TitleWord> words, String selected, Consumer<String> select) {
		FlowLayout column = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
		column.gap(2);
		column.child(label(Component.literal(heading), GOLD));

		FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		rows.padding(Insets.of(1));
		rows.child(wordRow(null, selected == null, select));
		for (TitleWord word : words) {
			rows.child(wordRow(word, word.text().equals(selected), select));
		}

		var scroll = UIContainers.verticalScroll(Sizing.fixed(140), Sizing.fixed(110), rows);
		scroll.surface(spriteSurface(LIST_BG));
		column.child(scroll);
		return column;
	}

	private FlowLayout wordRow(TitleWord word, boolean selected, Consumer<String> select) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(), Sizing.fixed(12));
		row.padding(Insets.of(2, 0, 4, 0)).verticalAlignment(VerticalAlignment.CENTER);
		row.child(word == null
				? label(Component.literal("(none)"), TEXT_DIM)
				: new TitleText(() -> TitleRenderer.render(word), 1f, TEXT));

		Surface idle = selected ? spriteSurface(ITEM_SELECTED) : Surface.BLANK;
		row.surface(idle);
		row.mouseEnter().subscribe(() -> row.surface(selected ? idle : spriteSurface(ITEM_HOVER)));
		row.mouseLeave().subscribe(() -> row.surface(idle));
		row.mouseDown().subscribe((click, doubled) -> {
			select.accept(word == null ? null : word.text());
			rebuildBody();
			return true;
		});
		return row;
	}

	private void save() {
		saving = true;
		rebuildBody();
		WynnTitles.account().setTitle(selectedPrefix, selectedSuffix).whenComplete((updated, t) -> Mc.run(() -> {
			saving = false;
			if (t != null) {
				error = "Could not equip: " + ApiException.describe(t);
			} else {
				me = updated;
				error = null;
				resetSelection();
			}
			rebuildBody();
		}));
	}

	// ── Achievements ───────────────────────────────────────────────────────

	private void buildAchievementsTab() {
		Catalog catalog = WynnTitles.catalogs().catalog();
		if (catalog == null) {
			body.child(label(Component.literal("Loading achievements..."), TEXT_DIM));
			return;
		}

		Set<String> unlocked = me.unlocked().stream().map(Unlock::achievementId).collect(Collectors.toSet());
		body.child(label(Component.literal(unlocked.size() + " / " + catalog.achievements().size()
				+ " unlocked  ·  " + me.points() + (me.points() == 1 ? " point" : " points")), TEXT));

		FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		rows.padding(Insets.of(2));
		rows.gap(2);
		for (Catalog.Achievement achievement : catalog.achievements()) {
			rows.child(achievementRow(achievement, unlocked.contains(achievement.id())));
		}

		var scroll = UIContainers.verticalScroll(Sizing.fixed(296), Sizing.fixed(170), rows);
		scroll.surface(spriteSurface(LIST_BG));
		body.child(scroll);
	}

	private FlowLayout achievementRow(Catalog.Achievement achievement, boolean unlocked) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
		row.padding(Insets.of(2, 2, 3, 4)).verticalAlignment(VerticalAlignment.CENTER);
		row.gap(5);
		row.child(new AchievementIcon(achievement, unlocked));

		FlowLayout text = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
		text.gap(3);
		MutableComponent name = Component.literal(achievement.name()).withStyle(ChatFormatting.BOLD);
		text.child(label(name, unlocked ? TEXT : TEXT_DIM));
		Title reward = Title.of(achievement.rewards().prefix(), achievement.rewards().suffix());
		if (reward != null) {
			text.child(new TitleText(() -> TitleRenderer.render(reward), 1f, TEXT));
		}
		row.child(text);

		row.tooltip(List.of(
				Component.literal(achievement.name()).withStyle(ChatFormatting.GOLD),
				Component.literal(achievement.description()).withStyle(ChatFormatting.GRAY),
				Component.literal(tierLabel(achievement.tier()) + " · " + achievement.points() + " pts"
								+ (unlocked ? " · Unlocked" : ""))
						.withStyle(tierColor(achievement.tier()))));
		return row;
	}

	private static String tierLabel(String tier) {
		return tier.isEmpty() ? tier : Character.toUpperCase(tier.charAt(0)) + tier.substring(1);
	}

	private static ChatFormatting tierColor(String tier) {
		return Map.of("bronze", ChatFormatting.GOLD, "silver", ChatFormatting.GRAY, "gold", ChatFormatting.YELLOW)
				.getOrDefault(tier, ChatFormatting.WHITE);
	}

	// ── Helpers ────────────────────────────────────────────────────────────

	private void setStatus(Component text) {
		status.text(text);
	}

	private static TitleWord find(List<TitleWord> words, String text) {
		if (text == null) {
			return null;
		}
		return words.stream().filter(w -> w.text().equals(text)).findFirst().orElse(null);
	}

	private static LabelComponent label(Component text, int color) {
		LabelComponent label = UIComponents.label(text);
		label.color(Color.ofRgb(color)).shadow(false);
		return label;
	}

	private static Surface spriteSurface(Identifier sprite) {
		return (graphics, component) -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
				component.x(), component.y(), component.width(), component.height());
	}

	private static Identifier sprite(String name) {
		return Identifier.fromNamespaceAndPath(WynnTitles.MOD_ID, "widget/" + name);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
