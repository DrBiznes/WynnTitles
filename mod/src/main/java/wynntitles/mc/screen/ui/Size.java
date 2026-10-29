package wynntitles.mc.screen.ui;

/**
 * How an element is sized along one axis: a fixed number of pixels, just big
 * enough for its content, all the space its parent offers (fill), or, along a
 * row's or column's main axis, whatever its siblings leave over (expand).
 */
public record Size(Kind kind, int pixels) {
	public enum Kind {
		FIXED,
		CONTENT,
		FILL,
		EXPAND
	}

	private static final Size CONTENT = new Size(Kind.CONTENT, 0);
	private static final Size FILL = new Size(Kind.FILL, 0);
	private static final Size EXPAND = new Size(Kind.EXPAND, 0);

	public static Size fixed(int pixels) {
		return new Size(Kind.FIXED, pixels);
	}

	public static Size content() {
		return CONTENT;
	}

	public static Size fill() {
		return FILL;
	}

	public static Size expand() {
		return EXPAND;
	}

	public boolean isContent() {
		return kind == Kind.CONTENT;
	}

	public boolean isExpand() {
		return kind == Kind.EXPAND;
	}

	/** Fixed sizes stay put; fill and expand take all of the space offered. Not for content sizes. */
	int resolve(int space) {
		return kind == Kind.FIXED ? pixels : Math.max(0, space);
	}
}
