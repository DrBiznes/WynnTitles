package wynntitles.titles;

import java.util.List;

/**
 * A per-character color effect on a run. Mirrors catalog.Effect on the server.
 * Animation is how an animated effect moves (see {@link TitleAnimation}); null
 * means flow. A cycleMs of 0 uses the animation's own beat.
 */
public record TitleEffect(String type, List<String> colors, String palette, boolean animated, int cycleMs, String animation) {
	public TitleEffect {
		colors = colors == null ? List.of() : List.copyOf(colors);
		palette = palette == null || palette.isBlank() ? null : palette;
		cycleMs = Math.max(cycleMs, 0);
		animation = animation == null || animation.isBlank() ? null : animation;
	}
}
