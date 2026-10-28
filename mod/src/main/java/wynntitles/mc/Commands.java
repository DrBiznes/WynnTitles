package wynntitles.mc;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import wynntitles.WynnTitles;
import wynntitles.api.ApiException;
import wynntitles.config.WynnTitlesConfig;
import wynntitles.mc.screen.TitlesScreen;

/** /wt opens the title screen; the subcommands are for troubleshooting. */
public final class Commands {
	private Commands() {}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal("wt")
				.executes(Commands::open)
				.then(literal("titles").executes(Commands::open))
				.then(literal("achievements").executes(ctx -> {
					Mc.openScreen(TitlesScreen.achievements());
					return 1;
				}))
				.then(literal("login").executes(Commands::login))
				.then(literal("reload").executes(Commands::reload))
				.then(literal("status").executes(Commands::status))));
	}

	private static int open(CommandContext<FabricClientCommandSource> ctx) {
		Mc.openScreen(new TitlesScreen());
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
