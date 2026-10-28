package wynntitles.titles;

import java.util.List;

/** A per-character color effect on a run. Mirrors catalog.Effect on the server. */
public record TitleEffect(String type, List<String> colors, String palette, boolean animated, int cycleMs) {
	public TitleEffect {
		colors = colors == null ? List.of() : List.copyOf(colors);
		palette = palette == null || palette.isBlank() ? null : palette;
		cycleMs = cycleMs <= 0 ? 5000 : cycleMs;
	}
}
