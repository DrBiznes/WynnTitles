package wynntitles.config;

import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry;

/** Where the nametag title sits relative to the player's name. */
public enum TitlePosition implements SelectionListEntry.Translatable {
	ABOVE("text.autoconfig.wynntitles.option.titlePosition.above"),
	BELOW("text.autoconfig.wynntitles.option.titlePosition.below");

	private final String key;

	TitlePosition(String key) {
		this.key = key;
	}

	@Override
	public String getKey() {
		return key;
	}
}
