package wynntitles.gametest;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import wynntitles.catalog.Catalog;
import wynntitles.mc.TitleRenderer;
import wynntitles.titles.TitleStyle;
import wynntitles.titles.TitleWord;

/** Test-only font contact sheet, using the actual title rendering path. */
final class FontGalleryScreen extends Screen {
	private final Catalog catalog;
	FontGalleryScreen(Catalog catalog) { super(Component.literal("Font audit")); this.catalog = catalog; }
	@Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xFF201A25);
	}
	@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		int y = 10;
		for (Catalog.ShopItem item : catalog.shop()) {
			if (!Catalog.ShopItem.FONT.equals(item.kind())) continue;
			graphics.drawString(font, item.name(), 10, y, 0xFFFFF4DC, false);
			String[] sources = {"color_gold", item.banner() ? "color_dark_blue" : "animated_ocean"};
			for (int n = 0; n < sources.length; n++) {
				var style = new TitleStyle(sources[n], n == 1 && !item.banner() ? List.of("bold") : List.of(), item.id(), null,
						n == 1 && item.banner() ? "yellow" : null);
				Component sample = TitleRenderer.render(catalog.restyle(new TitleWord("Wynn Hero 123", null), style));
				graphics.drawString(font, sample, 118 + n * 170, y, 0xFFFFFFFF, false);
			}
			y += 19;
		}
	}
}
