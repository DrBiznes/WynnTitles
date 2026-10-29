package wynntitles.titles;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import wynntitles.catalog.Catalog;

class TitleFontsTest {
	private Catalog catalog() throws Exception {
		List<Catalog.ShopItem> shop = new Gson().fromJson(
				Files.readString(Path.of("../server/catalog/shop.json")),
				new TypeToken<List<Catalog.ShopItem>>() {}.getType());
		return new Catalog("test", List.of(), shop);
	}

	@Test
	void fontSwitchKeepsExactlyTheSupportedPieces() throws Exception {
		Catalog catalog = catalog();
		TitleWord nativeWord = new TitleWord("Greg", List.of(
				new TitleRun("Greg", "dark_gray", false, false, false, false, true, null)));
		for (Catalog.ShopItem font : catalog.shop()) {
			if (!Catalog.ShopItem.FONT.equals(font.kind())) continue;
			for (String source : List.of("color_red", "palette_ice", "animated_ice", "rainbow")) {
				TitleStyle before = new TitleStyle(source, List.of("bold"));
				TitleStyle after = catalog.selectFont(before, font);
				TitleRun run = catalog.restyle(nativeWord, after).runs().get(0);
				assertEquals(font.font(), run.font());
				assertFalse(run.obfuscated(), "shop styles explicitly replace native styles");
				assertEquals(!font.banner(), run.bold());
				if (font.banner() && !source.equals("color_red")) {
					assertNull(after.source());
					assertNull(run.effect());
					assertEquals(source.equals("rainbow") ? EffectColors.firstColor(
							catalog.restyle(nativeWord, before).runs().get(0).effect()) : 0xC4F1FF,
							EffectColors.colorValue(run.color()));
				} else {
					assertEquals(source, after.source());
				}
			}
		}
	}

	@Test
	void coverageAndAsciiCaseFoldingAreExplicit() throws Exception {
		assertEquals(0, TitleFonts.bannerCharacter("Wynn Hero 123"));
		assertEquals('\'', TitleFonts.bannerCharacter("Queen's"));
		assertEquals('é', TitleFonts.bannerCharacter("Café"));
		assertEquals('ß', TitleFonts.bannerCharacter("ß"));
		assertEquals(0xE016, TitleFonts.glyph('w'));
		assertEquals("GREG", TitleFonts.text("minecraft:language/five", "Greg"));
		assertEquals("Greg", TitleFonts.text("minecraft:language/old_fruman", "Greg"));
		Catalog catalog = catalog();
		TitleWord word = new TitleWord("Queen's", null);
		assertEquals(word, catalog.restyle(word, new TitleStyle(null, List.of(), "font_pill", null, null)));
	}
}
