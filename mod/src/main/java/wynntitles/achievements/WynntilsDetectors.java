package wynntitles.achievements;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wynntils.core.components.Models;
import com.wynntils.core.text.StyledText;
import com.wynntils.models.activities.discoveries.DiscoveryInfo;
import com.wynntils.models.activities.type.ActivityStatus;
import com.wynntils.models.activities.type.DiscoveryType;
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

	/** Each character's visited places for territorySet triggers are kept in visitedFile. */
	public static Map<String, Detector> create(Path visitedFile) {
		Map<String, Detector> detectors = new HashMap<>();
		detectors.put("combatLevel", detector(WynntilsDetectors::combatLevel, WynntilsDetectors::combatLevelProgress));
		detectors.put("combatLevelAnyClass",
				detector(WynntilsDetectors::combatLevelAnyClass, WynntilsDetectors::combatLevelAnyClassProgress));
		detectors.put("totalProfessionLevel",
				detector(WynntilsDetectors::totalProfessionLevel, WynntilsDetectors::totalProfessionLevelProgress));
		detectors.put("professionLevel", new ProfessionLevelDetector());
		detectors.put("professionLevelAll", new ProfessionLevelDetector());
		detectors.put("location", WynntilsDetectors::location);
		detectors.put("territorySet", new TerritorySetDetector(new VisitedTerritories(visitedFile)));
		detectors.put("coordinates", trigger -> nearby(trigger, new JsonObject()));
		detectors.put("itemAtLocation", WynntilsDetectors::itemAtLocation);
		detectors.put("inventoryItem", detector(WynntilsDetectors::inventoryItem, WynntilsDetectors::inventoryItemProgress));
		detectors.put("itemSet", detector(WynntilsDetectors::itemSet, WynntilsDetectors::itemSetProgress));
		detectors.put("itemRarityCount",
				detector(WynntilsDetectors::itemRarityCount, WynntilsDetectors::itemRarityCountProgress));
		detectors.put("shinyItem", detector(WynntilsDetectors::shinyItem, WynntilsDetectors::shinyItemProgress));
		detectors.put("statistic", new StatisticDetector());
		detectors.put("quest", new QuestDetector());
		detectors.put("raid", RAIDS);
		detectors.put("dungeon", detector(WynntilsDetectors::dungeon, WynntilsDetectors::dungeonProgress));
		detectors.put("dungeonSet", detector(WynntilsDetectors::dungeonSet, WynntilsDetectors::dungeonSetProgress));
		detectors.put("characterRoster",
				detector(WynntilsDetectors::characterRoster, WynntilsDetectors::characterRosterProgress));
		detectors.put("gamemodeLevel", detector(WynntilsDetectors::gamemodeLevel, WynntilsDetectors::gamemodeLevelProgress));
		detectors.put("playtime", detector(WynntilsDetectors::playtime, WynntilsDetectors::playtimeProgress));
		detectors.put("accountAge", detector(WynntilsDetectors::accountAge, WynntilsDetectors::accountAgeProgress));
		detectors.put("guild", new GuildDetector());
		detectors.put("leaderboard", detector(WynntilsDetectors::leaderboard, WynntilsDetectors::leaderboardProgress));
		return detectors;
	}

	/** A detector from a check and its progress line. */
	private static Detector detector(Detector check, Function<Trigger, String> progress) {
		return new Detector() {
			@Override
			public JsonObject check(Trigger trigger) {
				return check.check(trigger);
			}

			@Override
			public String progress(Trigger trigger) {
				return progress.apply(trigger);
			}
		};
	}

	/**
	 * "37 / 100 levels", the current value capped at the target since a met
	 * trigger may not have been claimed yet. Null for a target of 1, which has
	 * nothing to count.
	 */
	private static String count(long current, long target, String unit) {
		if (target <= 1) {
			return null;
		}
		return String.format(Locale.ROOT, "%,d / %,d %s", Math.min(current, target), target, unit);
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

	/**
	 * The best character of the class: the one being played, or any other from
	 * the API profile. Claiming still needs the class's character to be played.
	 */
	private static String combatLevelProgress(Trigger trigger) {
		boolean playing = Models.Character.hasCharacter()
				&& Models.Character.getClassType().getName().equalsIgnoreCase(trigger.className());
		if (!playing && PROFILE.get() == null) {
			return null;
		}
		int best = characters().stream()
				.filter(c -> c.classType().getName().equalsIgnoreCase(trigger.className()))
				.mapToInt(CharacterData::level)
				.max()
				.orElse(0);
		if (playing) {
			best = Math.max(best, Models.CombatXp.getCombatLevel().current());
		}
		return count(best, trigger.level(), "levels");
	}

	private static String combatLevelAnyClassProgress(Trigger trigger) {
		if (!Models.Character.hasCharacter()) {
			return null;
		}
		return count(Models.CombatXp.getCombatLevel().current(), trigger.level(), "levels");
	}

	private static int totalProfessionLevelNow() {
		int total = 0;
		for (ProfessionType type : ProfessionType.values()) {
			total += Models.Profession.getLevel(type);
		}
		return total;
	}

	private static String totalProfessionLevelProgress(Trigger trigger) {
		if (!Models.Character.hasCharacter()) {
			return null;
		}
		return count(totalProfessionLevelNow(), trigger.level(), "total profession levels");
	}

	private static JsonObject totalProfessionLevel(Trigger trigger) {
		if (!Models.Character.hasCharacter()) {
			return null;
		}
		int total = totalProfessionLevelNow();
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

		@Override
		public String progress(Trigger trigger) {
			List<ProfessionType> types = professions(trigger);
			if (types == null || !Models.Character.hasCharacter()) {
				return null;
			}
			if (types.size() == 1) {
				return count(Models.Profession.getLevel(types.get(0)), trigger.level(), "levels");
			}
			long done = types.stream().filter(type -> Models.Profession.getLevel(type) >= trigger.level()).count();
			return count(done, types.size(), "professions at level " + trigger.level());
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

	/**
	 * Every listed place visited on one character. Places come from the Content
	 * Book's discoveries, which Wynntils only reads when asked, so this asks once
	 * each time a character joins a world, and only while a set is locked. A
	 * discovery that is not a territory, like The Moon or a library, therefore
	 * counts from the next join. Territories count straight away, from where the
	 * character stands.
	 */
	private static final class TerritorySetDetector implements Detector {
		private static final long SCAN_RETRY_MS = 60_000;
		private static final int SCAN_ATTEMPTS = 3;

		private final VisitedTerritories visited;
		private String scannedWorld;
		private long scannedAt;
		private int scanAttempts;

		TerritorySetDetector(VisitedTerritories visited) {
			this.visited = visited;
		}

		@Override
		public JsonObject check(Trigger trigger) {
			String character = character();
			if (character == null) {
				return null;
			}
			scanDiscoveries(character);
			Set<String> discovered = discovered();
			visited.addAll(character, discovered);
			TerritoryProfile territory = territory();
			if (territory != null) {
				visited.add(character, territory.getName());
			}
			for (String name : trigger.territoryNames()) {
				if (!visited.contains(character, name)) {
					return null;
				}
			}
			long inContentBook = trigger.territoryNames().stream()
					.filter(name -> discovered.contains(VisitedTerritories.key(name)))
					.count();
			JsonObject evidence = new JsonObject();
			evidence.addProperty("character", character);
			evidence.addProperty("territories", trigger.territoryNames().size());
			evidence.addProperty("inContentBook", inContentBook);
			return evidence;
		}

		@Override
		public String progress(Trigger trigger) {
			String character = character();
			if (character == null) {
				return null;
			}
			long done = trigger.territoryNames().stream().filter(name -> visited.contains(character, name)).count();
			return count(done, trigger.territoryNames().size(), "places visited");
		}

		/** The current character's Wynntils ID; null until the game has reported one, when it reads "-". */
		private static String character() {
			if (!Models.WorldState.onWorld() || !Models.Character.hasCharacter()) {
				return null;
			}
			String id = Models.Character.getId();
			return "-".equals(id) ? null : id;
		}

		/** A failed scan leaves nothing to read, so that is what is retried; a query fails if a menu is open. */
		private void scanDiscoveries(String character) {
			String world = character + "@" + Models.WorldState.getCurrentWorldName();
			long now = System.currentTimeMillis();
			if (!world.equals(scannedWorld)) {
				scanAttempts = 0;
			} else if (scanAttempts >= SCAN_ATTEMPTS
					|| now - scannedAt < SCAN_RETRY_MS
					|| Models.Discovery.getAllDiscoveriesForType(DiscoveryType.TERRITORY).findAny().isPresent()) {
				return;
			}
			if (Mc.screen() == null) {
				scannedWorld = world;
				scannedAt = now;
				scanAttempts++;
				Models.Discovery.reloadDiscoveries(true, true, true);
			}
		}

		/** Discovered places on the current character, as VisitedTerritories keys. Empty until a scan finishes. */
		private static Set<String> discovered() {
			Set<String> names = new HashSet<>();
			for (DiscoveryType type : DiscoveryType.values()) {
				Models.Discovery.getAllDiscoveriesForType(type)
						.filter(DiscoveryInfo::discovered)
						.forEach(discovery -> names.add(VisitedTerritories.key(discovery.name())));
			}
			return names;
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

	private static String inventoryItemProgress(Trigger trigger) {
		if (!Models.Character.hasCharacter()) {
			return null;
		}
		return count(Models.Inventory.getAmountInInventory(trigger.itemName()), trigger.amount(), "carried");
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

	/** The set with the most pieces held or worn. */
	private static String itemSetProgress(Trigger trigger) {
		if (!Models.Character.hasCharacter()) {
			return null;
		}
		List<String> carried = carriedNames();
		long best = 0;
		int size = 0;
		for (List<String> set : trigger.itemSets()) {
			long held = set.stream().filter(item -> carried.stream().anyMatch(name -> name.endsWith(item))).count();
			if (held > best || size == 0) {
				best = held;
				size = set.size();
			}
		}
		return count(best, size, "set pieces carried");
	}

	private static int rarityCount(Trigger trigger) {
		int count = 0;
		for (ItemStack stack : Mc.inventory()) {
			boolean matches = Models.Item.asWynnItemProperty(stack, GearTierItemProperty.class)
					.map(item -> item.getGearTier().name().equalsIgnoreCase(trigger.rarity()))
					.orElse(false);
			if (matches) {
				count++;
			}
		}
		return count;
	}

	private static String itemRarityCountProgress(Trigger trigger) {
		if (!Models.Character.hasCharacter()) {
			return null;
		}
		return count(rarityCount(trigger), trigger.amount(), trigger.rarity() + " items carried");
	}

	private static JsonObject itemRarityCount(Trigger trigger) {
		if (!Models.Character.hasCharacter()) {
			return null;
		}
		int count = rarityCount(trigger);
		if (count < trigger.amount()) {
			return null;
		}
		JsonObject evidence = new JsonObject();
		evidence.addProperty(trigger.rarity(), count);
		return evidence;
	}

	private static List<String> shinies() {
		List<String> shinies = new ArrayList<>();
		for (ItemStack stack : Mc.inventory()) {
			boolean shiny = Models.Item.asWynnItemProperty(stack, ShinyItemProperty.class)
					.flatMap(ShinyItemProperty::getShinyStat)
					.isPresent();
			if (shiny) {
				shinies.add(stack.getHoverName().getString());
			}
		}
		return shinies;
	}

	private static String shinyItemProgress(Trigger trigger) {
		if (!Models.Character.hasCharacter()) {
			return null;
		}
		return count(shinies().size(), trigger.amount(), "shiny items carried");
	}

	private static JsonObject shinyItem(Trigger trigger) {
		if (!Models.Character.hasCharacter()) {
			return null;
		}
		List<String> shinies = shinies();
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
		private static final Map<String, String> UNITS = Map.of(
				"mobsKilled", "mobs killed",
				"chestsFound", "chests found",
				"wars", "wars",
				"dungeonsCompleted", "dungeons",
				"raidsCompleted", "raids",
				"lootruns", "lootruns",
				"caves", "caves",
				"worldEvents", "world events",
				"completedQuests", "quests");

		@Override
		public boolean supports(Trigger trigger) {
			return STATISTICS.containsKey(trigger.statistic());
		}

		@Override
		public JsonObject check(Trigger trigger) {
			Long value = value(trigger);
			if (value == null || value < trigger.amount()) {
				return null;
			}
			JsonObject evidence = new JsonObject();
			evidence.addProperty(trigger.statistic(), value);
			return evidence;
		}

		@Override
		public String progress(Trigger trigger) {
			Long value = value(trigger);
			return value == null ? null : count(value, trigger.amount(), UNITS.get(trigger.statistic()));
		}

		/** Null until the API profile arrives. */
		private static Long value(Trigger trigger) {
			WynnPlayerInfo info = Models.Account.getPlayerInfo();
			if (info == null || info.globalData().isEmpty()) {
				return null;
			}
			return STATISTICS.get(trigger.statistic()).applyAsLong(info.globalData().get());
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
		public String progress(Trigger trigger) {
			int thisSession = completedThisSession.getOrDefault(key(trigger.raidName()), 0);
			if (thisSession == 0 && PROFILE.get() == null) {
				return null;
			}
			int onAccount = completions(GlobalData::raids).getOrDefault(key(trigger.raidName()), 0);
			return count(Math.max(thisSession, onAccount), trigger.amount(), "clears");
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

	private static String dungeonProgress(Trigger trigger) {
		if (PROFILE.get() == null) {
			return null;
		}
		return count(completions(GlobalData::dungeons).getOrDefault(key(trigger.dungeonName()), 0), trigger.amount(), "clears");
	}

	private static String dungeonSetProgress(Trigger trigger) {
		if (PROFILE.get() == null) {
			return null;
		}
		Map<String, Integer> completed = completions(GlobalData::dungeons);
		long done = trigger.dungeonNames().stream().filter(d -> completed.getOrDefault(key(d), 0) > 0).count();
		return count(done, trigger.dungeonNames().size(), "dungeons cleared");
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

	/** Classes with a character at the level, on any characters of the account. */
	private static String characterRosterProgress(Trigger trigger) {
		if (PROFILE.get() == null) {
			return null;
		}
		List<CharacterData> characters = characters();
		int classes = 0;
		int done = 0;
		for (ClassType type : ClassType.values()) {
			if (type == ClassType.NONE) {
				continue;
			}
			classes++;
			if (characters.stream().anyMatch(c -> c.classType() == type && c.level() >= trigger.level())) {
				done++;
			}
		}
		return count(done, classes, "classes at level " + trigger.level());
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

	private static boolean hasGamemodes(CharacterData character, Trigger trigger) {
		Set<String> modes = new HashSet<>();
		for (CharacterGamemode mode : character.gamemodes()) {
			modes.add(key(mode.getApiName()));
		}
		return trigger.gamemodes().stream().allMatch(mode -> modes.contains(key(mode)));
	}

	/** The highest character with every listed gamemode. */
	private static String gamemodeLevelProgress(Trigger trigger) {
		if (PROFILE.get() == null) {
			return null;
		}
		int best = characters().stream()
				.filter(c -> hasGamemodes(c, trigger))
				.mapToInt(CharacterData::level)
				.max()
				.orElse(0);
		return count(best, trigger.level(), "levels");
	}

	/** One character with every listed gamemode at the level. A dead hardcore character loses the mode in the API. */
	private static JsonObject gamemodeLevel(Trigger trigger) {
		for (CharacterData character : characters()) {
			if (character.level() >= trigger.level() && hasGamemodes(character, trigger)) {
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

	private static String playtimeProgress(Trigger trigger) {
		WynnPlayerInfo info = PROFILE.get();
		if (info == null || info.playtime().isEmpty()) {
			return null;
		}
		return count(info.playtime().get().longValue(), trigger.amount(), "hours played");
	}

	private static String accountAgeProgress(Trigger trigger) {
		WynnPlayerInfo info = PROFILE.get();
		Instant firstJoin = info == null ? null : info.firstJoinTimestamp().orElse(null);
		if (firstJoin == null) {
			return null;
		}
		double years = Duration.between(firstJoin, Instant.now()).toDays() / 365.25;
		return String.format(Locale.ROOT, "%.1f / %d years", Math.min(years, trigger.amount()), trigger.amount());
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

		@Override
		public String progress(Trigger trigger) {
			WynnPlayerInfo info = PROFILE.get();
			if (info == null) {
				return null;
			}
			PlayerGuildInfo guild = info.guildInfo().orElse(null);
			if (guild == null || guild.guildRank() == null) {
				return "Not in a guild";
			}
			return "Rank " + rankName(guild.guildRank()) + ", needs " + rankName(rank(trigger));
		}

		private static String rankName(GuildRank rank) {
			String name = rank.name().toLowerCase(Locale.ROOT);
			return Character.toUpperCase(name.charAt(0)) + name.substring(1);
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

	/** The best placement on any leaderboard. */
	private static String leaderboardProgress(Trigger trigger) {
		WynnPlayerInfo info = PROFILE.get();
		if (info == null) {
			return null;
		}
		int best = info.leaderboardPlacements().values().stream()
				.filter(placement -> placement != null && placement > 0)
				.mapToInt(Integer::intValue)
				.min()
				.orElse(0);
		if (best == 0) {
			return "Not on a leaderboard yet";
		}
		return String.format(Locale.ROOT, "Best placement #%,d, needs top %,d", best, trigger.amount());
	}
}
