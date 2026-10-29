package wynntitles.titles;

import java.util.ArrayList;
import java.util.List;

/**
 * How a player restyles one title slot, by shop item id: at most one color
 * source and any formats. It replaces the word's native style completely.
 * Mirrors catalog.Style on the server.
 */
public record TitleStyle(String source, List<String> formats) {
	public TitleStyle {
		source = source == null || source.isBlank() ? null : source;
		formats = formats == null ? List.of() : List.copyOf(formats);
	}

	/** Every shop item the style uses. */
	public List<String> items() {
		List<String> items = new ArrayList<>(formats);
		if (source != null) {
			items.add(source);
		}
		return items;
	}

	/** The server refuses an empty style; it means "native" on the screen. */
	public boolean isEmpty() {
		return source == null && formats.isEmpty();
	}
}
