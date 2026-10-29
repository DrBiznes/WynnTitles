package wynntitles.gametest;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import wynntitles.catalog.Catalog;
import wynntitles.mc.Icons;
import wynntitles.mc.screen.AchievementIcon;

/** Test-only contact sheets: actual framed icons beside their vanilla fallbacks. */
final class IconGalleryScreen extends Screen {
	static final int PAGE_SIZE = 32;
	private final List<Catalog.Achievement> entries;
	private final String variant;
	private final int page;

	IconGalleryScreen(List<Catalog.Achievement> entries, String variant, int page) {
		super(Component.literal("Achievement icon audit"));
		this.entries = entries;
		this.variant = variant;
		this.page = page;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xFF201A25);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.drawString(font, "Achievement icons / " + variant + " / page " + page
				+ "    Framed: catalog icon    Small: fallback item", 12, 12, 0xFFF7C948, false);
		int cellWidth = (width - 24) / 4;
		int cellHeight = (height - 40) / 8;
		for (int i = 0; i < entries.size(); i++) {
			Catalog.Achievement entry = entries.get(i);
			int x = 12 + i % 4 * cellWidth;
			int y = 32 + i / 4 * cellHeight;
			graphics.fill(x, y, x + cellWidth - 4, y + cellHeight - 4, 0xFF322B36);
			Catalog.Achievement framed = variant.equals("fallbacks")
					? new Catalog.Achievement(entry.id(), entry.category(), entry.name(), entry.description(),
							entry.tier(), entry.points(), new Catalog.Icon(null, entry.icon().fallback()),
							entry.trigger(), entry.rewards())
					: entry;
			AchievementIcon.draw(graphics, framed, true, x + 4, y + 4);
			graphics.renderItem(Icons.stack(new Catalog.Icon(null, entry.icon().fallback())), x + 38, y + 11);
			graphics.drawString(font, font.plainSubstrByWidth(entry.id(), cellWidth - 64),
					x + 60, y + 8, 0xFFFFF4DC, false);
			graphics.drawString(font, font.plainSubstrByWidth(entry.name(), cellWidth - 64),
					x + 60, y + 21, 0xFFB3A9A4, false);
			String model = entry.icon().model();
			graphics.drawString(font, font.plainSubstrByWidth(model == null ? "Vanilla item" : model,
					cellWidth - 12), x + 6, y + 40, 0xFFB3A9A4, false);
		}
	}
}
