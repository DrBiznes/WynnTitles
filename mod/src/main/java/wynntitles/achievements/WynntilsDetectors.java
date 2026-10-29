package wynntitles.achievements;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wynntils.core.components.Models;
import com.wynntils.core.text.StyledText;
import com.wynntils.models.activities.type.ActivityStatus;
import com.wynntils.models.character.type.CharacterGamemode;
import com.wynntils.models.character.type.ClassType;
import com.wynntils.models.guild.type.GuildRank;
import com.wynntils.models.items.properties.GearTierItemProperty;
import com.wynntils.models.items.properties.ShinyItemProperty;
import com.wynntils.models.players.type.wynnplayer.CharacterData;
import com.wynntils.models.players.type.wynnplayer.ContentCompletedData;
import com.wynntils.models.players.type.wynnplayer.GlobalData;
import com.wynntils.models.players.type.wynnplayer.PlayerGuildInfo;
import com.wynntils.models.players.type.wynnplayer.WynnPlayerInfo;
import com.wynntils.models.profession.type.ProfessionType;
import com.wynntils.models.raid.event.RaidEndedEvent;
import com.wynntils.models.territories.profile.TerritoryProfile;
import com.wynntils.services.leaderboard.type.LeaderboardType;
import com.wynntils.utils.wynn.WynnUtils;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import wynntitles.catalog.Catalog.Trigger;
import wynntitles.mc.Mc;

/**
 * Detectors for every trigger type in the catalog, built on Wynntils models.
 * A catalog entry this mod cannot check is skipped, and the achievements
 * screen asks the player to update.
 */
public final class WynntilsDetectors {
	private static final PlayerProfile PROFILE = new PlayerProfile();
	private static final RaidDetector RAIDS = new RaidDetector();

	private WynntilsDetectors() {}

	/** Detectors that listen for Wynntils events; register it once Wynntils has started. */
	public static Object eventListener() {
		return RAIDS;
	}

	/** Visited territories for territorySet triggers are kept in visitedFile. */
	public static Map<String, Detector> create(Path visitedFile) {
		Map<String, Detector> detectors = new HashMap<>();
		detectors.put("combatLevel", WynntilsDetectors::combatLevel);
		detectors.put("combatLevelAnyClass", WynntilsDetectors::combatLevelAnyClass);
		detectors.put("totalProfessionLevel", WynntilsDetectors::totalProfessionLevel);
		detectors.put("professionLevel", new ProfessionLevelDetector());
		detectors.put("professionLevelAll", new ProfessionLevelDetector());
		detectors.put("location", WynntilsDetectors::location);
		detectors.put("territorySet", new TerritorySetDetector(new VisitedTerritories(visitedFile)));
		detectors.put("coordinates", trigger -> nearby(trigger, new JsonObject()));
		detectors.put("itemAtLocation", WynntilsDetectors::itemAtLocation);
		detectors.put("inventoryItem", WynntilsDetectors::inventoryItem);
		detectors.put("itemSet", WynntilsDetectors::itemSet);
		detectors.put("itemRarityCount", WynntilsDetectors::itemRarityCount);
		detectors.put("shinyItem", WynntilsDetectors::shinyItem);
		detectors.put("statistic", new StatisticDetector());
		detectors.put("quest", new QuestDetector());
		detectors.put("raid", RAIDS);
		detectors.put("dungeon", WynntilsDetectors::dungeon);
		detectors.put("dungeonSet", WynntilsDetectors::dungeonSet);
		detectors.put("characterRoster", WynntilsDetectors::characterRoster);
		detectors.put("gamemodeLevel", WynntilsDetectors::gamemodeLevel);
		detectors.put("playtime", WynntilsDetectors::playtime);
		detectors.put("accountAge", WynntilsDetectors::accountAge);
		detectors.put("guild", new GuildDetector());
		detectors.put("leaderboard", WynntilsDetectors::leaderboard);
		return detectors;
	}

	private static JsonObject combatLevel(Trigger trigger) {
		if (!Models.Character.hasCharacter()) {
			return null;
		}
		String className = Models.Character.getClassType().getName();
		int level = Models.CombatXp.getCombatLevel().current();
		if (!className.equalsIgnoreCase(trigger.className()) || level < trigger.level()) {
			return null;
		}
		JsonObject evidence = new JsonObject();
		evidence.addProperty("className", className);
		evidence.addProperty("level", level);
		return evidence;
	}

	private static JsonObject combatLevelAnyClass(Trigger trigger) {
		if (!Models.Character.hasCharacter()) {
			return null;
		}
		int level = Models.CombatXp.getCombatLevel().current();
		if (level < trigger.level()) {
			return null;
		}
		JsonObject evidence = new JsonObject();
		evidence.addProperty("className", Models.Character.getClassType().getName());
		evidence.addProperty("level", level);
		return evidence;
	}

	private static JsonObject totalProfessionLevel(Trigger trigger) {
		if (!Models.Character.hasCharacter()) {
			return null;
		}
		int total = 0;
		for (ProfessionType type : ProfessionType.values()) {
			total += Models.Profession.getLevel(type);
		}
		if (total < trigger.level()) {
			return null;
		}
		JsonObject evidence = new JsonObject();
		evidence.addProperty("totalLevel", total);
		return evidence;
	}

	/** One profession (professionName) or every listed one (professionNames) at the level, on this character. */
	private static final class ProfessionLevelDetector implements Detector {
		@Override
		public boolean supports(Trigger trigger) {
			return professions(trigger) != null;
		}

		@Override
		public JsonObject check(Trigger trigger) {
			List<ProfessionType> types = professions(trigger);
			if (types == null || !Models.Character.hasCharacter()) {
				return null;
			}
			JsonObject evidence = new JsonObject();
			for (ProfessionType type : types) {
				int level = Models.Profession.getLevel(type);
				if (level < trigger.level()) {
					return null;
				}
				evidence.addProperty(type.getDisplayName(), level);
			}
			return evidence;
		}

		private static List<ProfessionType> professions(Trigger trigger) {
			List<String> names = trigger.professionNames() != null
					? trigger.professionNames()
					: trigger.professionName() == null ? List.of() : List.of(trigger.professionName());
			List<ProfessionType> types = new ArrayList<>();
			for (String name : names) {
				try {
					types.add(ProfessionType.fromString(name));
				} catch (IllegalArgumentException e) {
					return null;
				}
			}
			return types.isEmpty() ? null : types;
		}
	}

	private static TerritoryProfile territory() {
		if (Mc.client().player == null) {
			return null;
		}
		return Models.Territory.getTerritoryProfileForPosition(Mc.client().player.position());
	}

	private static JsonObject location(Trigger trigger) {
		TerritoryProfile territory = territory();
		if (territory == null
				|| !(territory.getName().equalsIgnoreCase(trigger.territoryName())
						|| territory.getFriendlyName().equalsIgnoreCase(trigger.territoryName()))) {
			return null;
		}
		Vec3 pos = Mc.client().player.position();
		JsonObject evidence = new JsonObject();
		evidence.addProperty("territory", territory.getName());
		evidence.addProperty("x", (int) pos.x);
		evidence.addProperty("y", (int) pos.y);
		evidence.addProperty("z", (int) pos.z);
		return evidence;
	}

	/** Records the territory the player stands in, then checks whether every listed one has been visited. */
	private static final class TerritorySetDetector implements Detector {
		private final VisitedTerritories visited;

		TerritorySetDetector(VisitedTerritories visited) {
			this.visited = visited;
		}

		@Override
		public JsonObject check(Trigger trigger) {
			TerritoryProfile territory = territory();
			if (territory == null) {
				return null;
			}
			String player = Mc.client().player.getStringUUID();
			visited.add(player, territory.getName());
			for (String name : trigger.territoryNames()) {
				if (!visited.contains(player, name)) {
					return null;
				}
			}
			JsonObject evidence = new JsonObject();
			evidence.addProperty("territories", trigger.territoryNames().size());
			evidence.addProperty("last", territory.getName());
			return evidence;
		}
	}

	/** Adds the player's position to evidence if they are within the trigger's circle, otherwise null. */
	private static JsonObject nearby(Trigger trigger, JsonObject evidence) {
		if (Mc.client().player == null) {
			return null;
		}
		Vec3 pos = Mc.client().player.position();
		double dx = pos.x - trigger.x();
		double dz = pos.z - trigger.z();
		if (dx * dx + dz * dz > (double) trigger.radius() * trigger.radius()) {
			return null;
		}
		evidence.addProperty("x", (int) pos.x);
		evidence.addProperty("y", (int) pos.y);
		evidence.addProperty("z", (int) pos.z);
		return evidence;
	}

	private static JsonObject itemAtLocation(Trigger trigger) {
		if (!Models.Character.hasCharacter() || Models.Inventory.getAmountInInventory(trigger.itemName()) < 1) {
			return null;
		}
		JsonObject evidence = new JsonObject();
		evidence.addProperty("item", trigger.itemName());
		return nearby(trigger, evidence);
	}

	private static JsonObject inventoryItem(Trigger trigger) {
		if (!Models.Character.hasCharacter()) {
			return null;
		}
		int amount = Models.Inventory.getAmountInInventory(trigger.itemName());
		if (amount < trigger.amount()) {
			return null;
		}
		JsonObject evidence = new JsonObject();
		evidence.addProperty("amount", amount);
		return evidence;
	}

	/** Item names the way Wynntils matches them in getAmountInInventory, worn items included. */
	private static List<String> carriedNames() {
		List<String> names = new ArrayList<>();
		for (ItemStack stack : Mc.inventory()) {
			if (!stack.isEmpty()) {
				String name = StyledText.fromComponent(stack.getHoverName()).getNormalized().trim().getString();
				names.add(WynnUtils.stripItemNameMarkers(name, false));
			}
		}
		return names;
	}

	private static JsonObject itemSet(Trigger trigger) {
		if (!Models.Character.hasCharacter()) {
			return null;
		}
		List<String> carried = carriedNames();
		for (List<String> set : trigger.itemSets()) {
			boolean complete = set.stream().allMatch(item -> carried.stream().anyMatch(name -> name.endsWith(item)));
			if (complete) {
				JsonObject evidence = new JsonObject();
				JsonArray items = new JsonArray();
				set.forEach(items::add);
				evidence.add("items", items);
				return evidence;
			}
		}
		return null;
	}

	private static JsonObject itemRarityCount(Trigger trigger) {
		if (!Models.Character.hasCharacter()) {
			return null;
		}
		int count = 0;
		for (ItemStack stack : Mc.inventory()) {
			boolean matches = Models.Item.asWynnItemProperty(stack, GearTierItemProperty.class)
					.map(item -> item.getGearTier().name().equalsIgnoreCase(trigger.rarity()))
					.orElse(false);
			if (matches) {
				count++;
			}
		}
		if (count < trigger.amount()) {
			return null;
		}
		JsonObject evidence = new JsonObject();
		evidence.addProperty(trigger.rarity(), count);
		return evidence;
	}

	private static JsonObject shinyItem(Trigger trigger) {
		if (!Models.Character.hasCharacter()) {
			return null;
		}
		List<String> shinies = new ArrayList<>();
		for (ItemStack stack : Mc.inventory()) {
			boolean shiny = Models.Item.asWynnItemProperty(stack, ShinyItemProperty.class)
					.flatMap(ShinyItemProperty::getShinyStat)
					.isPresent();
			if (shiny) {
				shinies.add(stack.getHoverName().getString());
			}
		}
		if (shinies.size() < trigger.amount()) {
			return null;
		}
		JsonObject evidence = new JsonObject();
		JsonArray items = new JsonArray();
		shinies.forEach(items::add);
		evidence.add("items", items);
		return evidence;
	}

	/** Lifetime statistics from the Wynncraft API, by catalog name. */
	private static final class StatisticDetector implements Detector {
		private static final Map<String, ToLongFunction<GlobalData>> STATISTICS = Map.of(
				"mobsKilled", GlobalData::mobsKilled,
				"chestsFound", GlobalData::chestsFound,
				"wars", GlobalData::wars,
				"dungeonsCompleted", global -> total(global.dungeons()),
				"raidsCompleted", global -> total(global.raids()),
				"lootruns", GlobalData::lootruns,
				"caves", GlobalData::caves,
				"worldEvents", GlobalData::worldEvents,
				"completedQuests", GlobalData::completedQuests);

		@Override
		public boolean supports(Trigger trigger) {
			return STATISTICS.containsKey(trigger.statistic());
		}

		@Override
		public JsonObject check(Trigger trigger) {
			WynnPlayerInfo info = Models.Account.getPlayerInfo();
			if (info == null || info.globalData().isEmpty()) {
				return null;
			}
			long value = STATISTICS.get(trigger.statistic()).applyAsLong(info.globalData().get());
			if (value < trigger.amount()) {
				return null;
			}
			JsonObject evidence = new JsonObject();
			evidence.addProperty(trigger.statistic(), value);
			return evidence;
		}

		private static long total(ContentCompletedData data) {
			return data == null ? 0 : data.total();
		}
	}

	/**
	 * Checks the quest book Wynntils keeps for the current character, and the
	 * Wynncraft API so quests finished on other characters count too.
	 */
	private static final class QuestDetector implements Detector {
		@Override
		public JsonObject check(Trigger trigger) {
			String quest = trigger.questName();
			boolean inQuestBook = Models.Quest.getQuestFromName(quest)
					.map(info -> info.status() == ActivityStatus.COMPLETED)
					.orElse(false);
			if (!inQuestBook && !completedOnAccount().contains(quest.toLowerCase(Locale.ROOT))) {
				return null;
			}
			JsonObject evidence = new JsonObject();
			evidence.addProperty("source", inQuestBook ? "questBook" : "wynncraftApi");
			return evidence;
		}

		private static Set<String> completedOnAccount() {
			WynnPlayerInfo info = PROFILE.get();
			if (info == null) {
				return Set.of();
			}
			Set<String> completed = new HashSet<>();
			for (CharacterData character : info.characters().values()) {
				character.quests().forEach(q -> completed.add(q.toLowerCase(Locale.ROOT)));
			}
			return completed;
		}
	}

	/**
	 * Raid completions on the whole account from the Wynncraft API, and the
	 * raids Wynntils saw finish since the game started, so a fresh clear
	 * unlocks straight away. Wynntils also records failed raids in its history,
	 * so only its completion event counts.
	 */
	private static final class RaidDetector implements Detector {
		private final Map<String, Integer> completedThisSession = new ConcurrentHashMap<>();

		@SubscribeEvent
		public void onRaidCompleted(RaidEndedEvent.Completed event) {
			completedThisSession.merge(key(event.getRaid().getRaidKind().getRaidName()), 1, Integer::sum);
		}

		@Override
		public JsonObject check(Trigger trigger) {
			String raid = key(trigger.raidName());
			int thisSession = completedThisSession.getOrDefault(raid, 0);
			// The API catches up with this session's clears, so adding the two
			// would count those twice.
			int onAccount = thisSession < trigger.amount() ? completions(GlobalData::raids).getOrDefault(raid, 0) : 0;
			int completions = Math.max(thisSession, onAccount);
			if (completions < trigger.amount()) {
				return null;
			}
			JsonObject evidence = new JsonObject();
			evidence.addProperty("source", thisSession >= onAccount ? "raidEvent" : "wynncraftApi");
			evidence.addProperty("completions", completions);
			return evidence;
		}
	}

	private static String key(String name) {
		return name == null ? "" : name.toLowerCase(Locale.ROOT);
	}

	/** Completions on the whole account from the API profile, by lowercase name; empty until it arrives. */
	private static Map<String, Integer> completions(Function<GlobalData, ContentCompletedData> content) {
		WynnPlayerInfo info = PROFILE.get();
		if (info == null || info.globalData().isEmpty()) {
			return Map.of();
		}
		ContentCompletedData data = content.apply(info.globalData().get());
		if (data == null || data.completions() == null) {
			return Map.of();
		}
		Map<String, Integer> completed = new HashMap<>();
		data.completions().forEach((name, count) -> completed.merge(key(name), count, Integer::sum));
		return completed;
	}

	private static JsonObject dungeon(Trigger trigger) {
		int completions = completions(GlobalData::dungeons).getOrDefault(key(trigger.dungeonName()), 0);
		if (completions < trigger.amount()) {
			return null;
		}
		JsonObject evidence = new JsonObject();
		evidence.addProperty("completions", completions);
		return evidence;
	}

	private static JsonObject dungeonSet(Trigger trigger) {
		Map<String, Integer> completed = completions(GlobalData::dungeons);
		JsonObject evidence = new JsonObject();
		for (String dungeon : trigger.dungeonNames()) {
			int completions = completed.getOrDefault(key(dungeon), 0);
			if (completions < 1) {
				return null;
			}
			evidence.addProperty(dungeon, completions);
		}
		return evidence;
	}

	/** Characters from the API profile; empty until it arrives. */
	private static List<CharacterData> characters() {
		WynnPlayerInfo info = PROFILE.get();
		return info == null ? List.of() : List.copyOf(info.characters().values());
	}

	/** A character of every class at the level, on any characters of the account. */
	private static JsonObject characterRoster(Trigger trigger) {
		List<CharacterData> characters = characters();
		JsonObject evidence = new JsonObject();
		for (ClassType type : ClassType.values()) {
			if (type == ClassType.NONE) {
				continue;
			}
			int best = characters.stream()
					.filter(c -> c.classType() == type)
					.mapToInt(CharacterData::level)
					.max()
					.orElse(0);
			if (best < trigger.level()) {
				return null;
			}
			evidence.addProperty(type.getName(), best);
		}
		return evidence;
	}

	/** One character with every listed gamemode at the level. A dead hardcore character loses the mode in the API. */
	private static JsonObject gamemodeLevel(Trigger trigger) {
		for (CharacterData character : characters()) {
			Set<String> modes = new HashSet<>();
			for (CharacterGamemode mode : character.gamemodes()) {
				modes.add(key(mode.getApiName()));
			}
			if (character.level() >= trigger.level()
					&& trigger.gamemodes().stream().allMatch(mode -> modes.contains(key(mode)))) {
				JsonObject evidence = new JsonObject();
				evidence.addProperty("className", character.classType().getName());
				evidence.addProperty("level", character.level());
				return evidence;
			}
		}
		return null;
	}

	/** Amount is hours. */
	private static JsonObject playtime(Trigger trigger) {
		WynnPlayerInfo info = PROFILE.get();
		double hours = info == null ? 0 : info.playtime().orElse(0.0);
		if (hours < trigger.amount()) {
			return null;
		}
		JsonObject evidence = new JsonObject();
		evidence.addProperty("hours", (long) hours);
		return evidence;
	}

	/** Amount is years since the account first joined Wynncraft. */
	private static JsonObject accountAge(Trigger trigger) {
		WynnPlayerInfo info = PROFILE.get();
		Instant firstJoin = info == null ? null : info.firstJoinTimestamp().orElse(null);
		if (firstJoin == null || Duration.between(firstJoin, Instant.now()).toDays() < trigger.amount() * 365.25) {
			return null;
		}
		JsonObject evidence = new JsonObject();
		evidence.addProperty("firstJoin", firstJoin.toString());
		return evidence;
	}

	/** In a guild at guildRank or higher. */
	private static final class GuildDetector implements Detector {
		@Override
		public boolean supports(Trigger trigger) {
			return rank(trigger) != null;
		}

		@Override
		public JsonObject check(Trigger trigger) {
			WynnPlayerInfo info = PROFILE.get();
			PlayerGuildInfo guild = info == null ? null : info.guildInfo().orElse(null);
			if (guild == null || guild.guildRank() == null || guild.guildRank().compareTo(rank(trigger)) < 0) {
				return null;
			}
			JsonObject evidence = new JsonObject();
			evidence.addProperty("guild", guild.guildName());
			evidence.addProperty("rank", guild.guildRank().name());
			return evidence;
		}

		private static GuildRank rank(Trigger trigger) {
			try {
				return GuildRank.valueOf(trigger.guildRank().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException | NullPointerException e) {
				return null;
			}
		}
	}

	/** Placed amount or better on any Wynncraft leaderboard. */
	private static JsonObject leaderboard(Trigger trigger) {
		WynnPlayerInfo info = PROFILE.get();
		if (info == null) {
			return null;
		}
		for (Map.Entry<LeaderboardType, Integer> placement : info.leaderboardPlacements().entrySet()) {
			if (placement.getValue() != null && placement.getValue() > 0 && placement.getValue() <= trigger.amount()) {
				JsonObject evidence = new JsonObject();
				evidence.addProperty("leaderboard", placement.getKey().name());
				evidence.addProperty("placement", placement.getValue());
				return evidence;
			}
		}
		return null;
	}
}
