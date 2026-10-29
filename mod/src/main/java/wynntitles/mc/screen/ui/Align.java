package wynntitles.mc.screen.ui;

/** Where content sits along an axis when it has room to spare. */
public enum Align {
	START,
	CENTER,
	END;

	/** How far to move content to sit here, given the room to spare (negative when it overflows). */
	int offset(int free) {
		return switch (this) {
			case START -> 0;
			case CENTER -> free / 2;
			case END -> free;
		};
	}
}
