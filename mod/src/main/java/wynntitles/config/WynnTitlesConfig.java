package wynntitles.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.minecraft.world.InteractionResult;

/** Player-facing options, edited through Mod Menu with a Cloth Config screen. */
@Config(name = "wynntitles")
public class WynnTitlesConfig implements ConfigData {
	public static final String DEFAULT_SERVER_URL = "https://wynntitles.jamino.me";

	@ConfigEntry.Gui.Tooltip
	public boolean showTitles = true;

	@ConfigEntry.Gui.Tooltip
	public boolean hideTitlesInRaids = true;

	/** Nametag title size, in percent of the name's. */
	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.BoundedDiscrete(min = 50, max = 150)
	public int titleScale = 80;

	@ConfigEntry.Gui.Tooltip
	public boolean animateTitles = true;

	/** The chat line only; kept under its old name so existing configs carry over. */
	@ConfigEntry.Gui.Tooltip
	public boolean announceUnlocks = true;

	@ConfigEntry.Gui.Tooltip
	public boolean unlockToasts = true;

	@ConfigEntry.Category("advanced")
	@ConfigEntry.Gui.Tooltip
	public String serverUrl = "";

	@ConfigEntry.Category("advanced")
	@ConfigEntry.Gui.Tooltip
	public boolean previewOwnTitleOnEveryone = false;

	private static String lastServerUrl;

	/** {@code onServerChange} runs when a save points the mod at a different server. */
	public static void register(Runnable onServerChange) {
		AutoConfig.register(WynnTitlesConfig.class, GsonConfigSerializer::new);
		lastServerUrl = resolvedServerUrl();
		AutoConfig.getConfigHolder(WynnTitlesConfig.class).registerSaveListener((holder, config) -> {
			String url = resolve(config.serverUrl);
			if (!url.equals(lastServerUrl)) {
				lastServerUrl = url;
				onServerChange.run();
			}
			return InteractionResult.PASS;
		});
	}

	public static WynnTitlesConfig get() {
		return AutoConfig.getConfigHolder(WynnTitlesConfig.class).getConfig();
	}

	public static String resolvedServerUrl() {
		return resolve(get().serverUrl);
	}

	/** The config value wins, then the dev-run system property, then the built-in default. */
	private static String resolve(String configured) {
		if (configured != null && !configured.isBlank()) {
			return configured.trim();
		}
		return System.getProperty("wynntitles.serverUrl", DEFAULT_SERVER_URL);
	}

	@Override
	public void validatePostLoad() {
		titleScale = Math.clamp(titleScale, 50, 150);
	}
}
