package wynntitles.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

/** Player-facing options, edited through Mod Menu with a Cloth Config screen. */
@Config(name = "wynntitles")
public class WynnTitlesConfig implements ConfigData {
	public static final String DEFAULT_SERVER_URL = "https://wynntitles.jamino.me";

	@ConfigEntry.Gui.Tooltip
	public boolean showTitles = true;

	@ConfigEntry.Gui.Tooltip
	public boolean announceUnlocks = true;

	@ConfigEntry.Category("advanced")
	@ConfigEntry.Gui.Tooltip
	public String serverUrl = "";

	@ConfigEntry.Category("advanced")
	@ConfigEntry.Gui.Tooltip
	public boolean previewOwnTitleOnEveryone = false;

	public static void register() {
		AutoConfig.register(WynnTitlesConfig.class, GsonConfigSerializer::new);
	}

	public static WynnTitlesConfig get() {
		return AutoConfig.getConfigHolder(WynnTitlesConfig.class).getConfig();
	}

	/** The config value wins, then the dev-run system property, then the built-in default. */
	public static String resolvedServerUrl() {
		String configured = get().serverUrl;
		if (configured != null && !configured.isBlank()) {
			return configured.trim();
		}
		return System.getProperty("wynntitles.serverUrl", DEFAULT_SERVER_URL);
	}
}
