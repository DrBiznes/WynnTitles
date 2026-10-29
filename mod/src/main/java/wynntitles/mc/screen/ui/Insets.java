package wynntitles.mc.screen.ui;

/** Space around an element (margins) or inside a container (padding). */
public record Insets(int top, int bottom, int left, int right) {
	public static final Insets NONE = new Insets(0, 0, 0, 0);

	public static Insets of(int all) {
		return new Insets(all, all, all, all);
	}

	public static Insets of(int top, int bottom, int left, int right) {
		return new Insets(top, bottom, left, right);
	}

	public static Insets top(int top) {
		return new Insets(top, 0, 0, 0);
	}

	public static Insets left(int left) {
		return new Insets(0, 0, left, 0);
	}

	public static Insets right(int right) {
		return new Insets(0, 0, 0, right);
	}

	public int horizontal() {
		return left + right;
	}

	public int vertical() {
		return top + bottom;
	}
}
