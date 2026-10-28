package wynntitles.titles;

import java.util.ArrayList;
import java.util.List;

/** A full display title (prefix + suffix) as the server builds it. */
public record Title(String plain, List<TitleRun> runs) {
	public Title {
		runs = runs == null ? List.of() : List.copyOf(runs);
	}

	/** Builds a preview the same way the server does: prefix, a space, suffix. */
	public static Title of(TitleWord prefix, TitleWord suffix) {
		List<TitleRun> runs = new ArrayList<>();
		StringBuilder plain = new StringBuilder();
		if (prefix != null) {
			runs.addAll(prefix.runs());
			plain.append(prefix.text());
		}
		if (prefix != null && suffix != null) {
			runs.add(TitleRun.plain(" "));
			plain.append(' ');
		}
		if (suffix != null) {
			runs.addAll(suffix.runs());
			plain.append(suffix.text());
		}
		return runs.isEmpty() ? null : new Title(plain.toString(), runs);
	}
}
