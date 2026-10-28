package wynntitles.titles;

import java.util.List;

/** One equippable prefix or suffix. Mirrors catalog.Word on the server. */
public record TitleWord(String text, List<TitleRun> runs) {
	public TitleWord {
		runs = runs == null || runs.isEmpty() ? List.of(TitleRun.plain(text)) : List.copyOf(runs);
	}
}
