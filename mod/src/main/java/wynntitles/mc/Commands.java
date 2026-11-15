package wynntitles.mc;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.mojang.brigadier.context.CommandContext;
import java.net.URI;
import me.shedaniel.autoconfig.AutoConfigClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import wynntitles.WynnTitles;
import wynntitles.achievements.WynntilsDetectors;
import wynntitles.api.ApiException;
import wynntitles.config.WynnTitlesConfig;
import wynntitles.mc.screen.TitlesScreen;

/** /titles opens the title screen and /achievements its Achievements tab; the other subcommands are settings and troubleshooting. */
public final class Commands {
	/** Command and what it does, in the order /titles help lists them. */
	private static final String[][] HELP = {
		{"/titles", "Opens WynnTitles"},
		{"/titles achievements", "Opens it straight to the Achievements tab"},
		{"/achievements", "Same as /titles achievements"},
		{"/titles config", "Opens the settings (needs Mod Menu)"},
		{"/titles status", "Shows which server the mod is using and how it's doing"},
		{"/titles login", "Tries logging in again"},
		{"/titles reload", "Reloads your titles and achievements from the server"},
		{"/titles rescan", "Reads this character's discoveries from the Content Book again"},
		{"/titles help", "Shows this list"},
	};

	private static final String MOD_MENU_URL = "https://modrinth.com/mod/modmenu";

	private Commands() {}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(literal("achievements").executes(Commands::openAchievements));
			dispatcher.register(literal("titles")
				.executes(Commands::open)
				.then(literal("achievements").executes(Commands::openAchievements))
				.then(literal("config").executes(Commands::config))
				.then(literal("login").executes(Commands::login))
				.then(literal("reload").executes(Commands::reload))
				.then(literal("rescan").executes(Commands::rescan))
				.then(literal("status").executes(Commands::status))
				.then(literal("help").executes(Commands::help)));
		});
	}

	private static int help(CommandContext<FabricClientCommandSource> ctx) {
		MutableComponent text = Component.literal("Commands").withStyle(ChatFormatting.GOLD);
		for (String[] line : HELP) {
			text.append(Component.literal("\n" + line[0]).withStyle(ChatFormatting.AQUA))
					.append(Component.literal(" - " + line[1]).withStyle(ChatFormatting.GRAY));
		}
		ctx.getSource().sendFeedback(Mc.tagged(text));
		return 1;
	}

	private static int open(CommandContext<FabricClientCommandSource> ctx) {
		Mc.openScreen(new TitlesScreen());
		return 1;
	}

	private static int openAchievements(CommandContext<FabricClientCommandSource> ctx) {
		Mc.openScreen(TitlesScreen.achievements());
		return 1;
	}

	/** Opens the same screen Mod Menu's config button does, or points to Mod Menu when it isn't installed. */
	private static int config(CommandContext<FabricClientCommandSource> ctx) {
		if (FabricLoader.getInstance().isModLoaded("modmenu")) {
			Mc.openScreen(AutoConfigClient.getConfigScreen(WynnTitlesConfig.class, null).get());
			return 1;
		}
		ctx.getSource().sendFeedback(Mc.tagged(Component.literal("Install ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal("Mod Menu").withStyle(style -> style
						.withColor(ChatFormatting.AQUA)
						.withUnderlined(true)
						.withClickEvent(new ClickEvent.OpenUrl(URI.create(MOD_MENU_URL)))))
				.append(Component.literal(" to change WynnTitles' settings.").withStyle(ChatFormatting.GRAY))));
		return 1;
	}

	private static int login(CommandContext<FabricClientCommandSource> ctx) {
		Mc.chat(Mc.tagged(Component.literal("Logging in...").withStyle(ChatFormatting.GRAY)));
		WynnTitles.account().relogin().whenComplete((me, t) -> Mc.chat(Mc.tagged(t == null
				? Component.literal("Logged in as " + me.username()).withStyle(ChatFormatting.GREEN)
				: Component.literal("Login failed: " + ApiException.describe(t)).withStyle(ChatFormatting.RED))));
		return 1;
	}

	private static int reload(CommandContext<FabricClientCommandSource> ctx) {
		WynnTitles.titles().clear();
		WynnTitles.catalogs().refresh()
				.thenCompose(catalog -> WynnTitles.account().refresh())
				.whenComplete((me, t) -> Mc.chat(Mc.tagged(t == null
						? Component.literal("Reloaded titles and achievements.").withStyle(ChatFormatting.GREEN)
						: Component.literal("Reload failed: " + ApiException.describe(t)).withStyle(ChatFormatting.RED))));
		return 1;
	}

	private static int rescan(CommandContext<FabricClientCommandSource> ctx) {
		ctx.getSource().sendFeedback(Mc.tagged(WynntilsDetectors.rescanDiscoveries()
				? Component.literal("Rescanning this character's discoveries.").withStyle(ChatFormatting.GREEN)
				: Component.literal("Join a world on a character first.").withStyle(ChatFormatting.RED)));
		return 1;
	}

	private static int status(CommandContext<FabricClientCommandSource> ctx) {
		var me = WynnTitles.account().me();
		var catalog = WynnTitles.catalogs().catalog();
		ctx.getSource().sendFeedback(Mc.tagged(Component.literal(String.join("\n",
				"Server: " + WynnTitlesConfig.resolvedServerUrl(),
				"Account: " + (me == null ? "not loaded" : me.username() + " (" + me.unlocked().size() + " unlocked)"),
				"Catalog: " + (catalog == null ? "not loaded" : catalog.version())))));
		return 1;
	}
}
