package wynntitles.achievements;

import com.google.gson.JsonObject;
import com.wynntils.core.components.Models;
import com.wynntils.models.activities.type.ActivityStatus;
import com.wynntils.models.players.type.wynnplayer.CharacterData;
import com.wynntils.models.players.type.wynnplayer.GlobalData;
import com.wynntils.models.players.type.wynnplayer.WynnPlayerInfo;
import com.wynntils.models.profession.type.ProfessionType;
import com.wynntils.models.raid.event.RaidEndedEvent;
import com.wynntils.models.territories.profile.TerritoryProfile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToLongFunction;
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
	private static final RaidDetector RAIDS = new RaidDetector();

	private WynntilsDetectors() {}

	/** Detectors that listen for Wynntils events; register it once Wynntils has started. */
	public static Object eventListener() {
		return RAIDS;
	}

	public static Map<String, Detector> create() {
		Map<String, Detector> detectors = new HashMap<>();
		detectors.put("combatLevel", WynntilsDetectors::combatLevel);
		detectors.put("combatLevelAnyClass", WynntilsDetectors::combatLevelAnyClass);
		detectors.put("totalProfessionLevel", WynntilsDetectors::totalProfessionLevel);
		detectors.put("location", WynntilsDetectors::location);
		detectors.put("inventoryItem", WynntilsDetectors::inventoryItem);
		detectors.put("statistic", new StatisticDetector());
		detectors.put("quest", new QuestDetector());
		detectors.put("raid", RAIDS);
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

	private static JsonObject location(Trigger trigger) {
		if (Mc.client().player == null) {
			return null;
		}
		Vec3 pos = Mc.client().player.position();
		TerritoryProfile territory = Models.Territory.getTerritoryProfileForPosition(pos);
		if (territory == null
				|| !(territory.getName().equalsIgnoreCase(trigger.territoryName())
						|| territory.getFriendlyName().equalsIgnoreCase(trigger.territoryName()))) {
			return null;
		}
		JsonObject evidence = new JsonObject();
		evidence.addProperty("territory", territory.getName());
		evidence.addProperty("x", (int) pos.x);
		evidence.addProperty("y", (int) pos.y);
		evidence.addProperty("z", (int) pos.z);
		return evidence;
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

	/** Lifetime statistics from the Wynncraft API, by catalog name. */
	private static final class StatisticDetector implements Detector {
		private static final Map<String, ToLongFunction<GlobalData>> STATISTICS = Map.of(
				"mobsKilled", GlobalData::mobsKilled,
				"chestsFound", GlobalData::chestsFound);

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
	}

	/**
	 * Checks the quest book Wynntils keeps for the current character, and every
	 * few minutes also the Wynncraft API so quests finished on other characters
	 * count too.
	 */
	private static final class QuestDetector implements Detector {
		private static final long API_REFRESH_MS = 5 * 60_000;

		private volatile Set<String> completedOnAccount = Set.of();
		private long lastApiFetch;

		@Override
		public JsonObject check(Trigger trigger) {
			String quest = trigger.questName();
			boolean inQuestBook = Models.Quest.getQuestFromName(quest)
					.map(info -> info.status() == ActivityStatus.COMPLETED)
					.orElse(false);
			if (!inQuestBook) {
				refreshFromApi();
			}
			if (!inQuestBook && !completedOnAccount.contains(quest.toLowerCase(Locale.ROOT))) {
				return null;
			}
			JsonObject evidence = new JsonObject();
			evidence.addProperty("source", inQuestBook ? "questBook" : "wynncraftApi");
			return evidence;
		}

		private void refreshFromApi() {
			long now = System.currentTimeMillis();
			if (now - lastApiFetch < API_REFRESH_MS || Mc.client().player == null) {
				return;
			}
			lastApiFetch = now;
			Models.Player.getPlayerFullInfo(Mc.client().player.getStringUUID()).thenAccept(info -> {
				if (info == null) {
					return;
				}
				Set<String> completed = new HashSet<>();
				for (CharacterData character : info.characters().values()) {
					character.quests().forEach(q -> completed.add(q.toLowerCase(Locale.ROOT)));
				}
				completedOnAccount = completed;
			});
		}
	}

	/**
	 * Raid completions on the whole account from the Wynncraft API, refreshed
	 * every few minutes, and the raids Wynntils saw finish since the game
	 * started, so a fresh clear unlocks straight away. Wynntils also records
	 * failed raids in its history, so only its completion event counts.
	 */
	private static final class RaidDetector implements Detector {
		private static final long API_REFRESH_MS = 5 * 60_000;

		private final Map<String, Integer> completedThisSession = new ConcurrentHashMap<>();
		private volatile Map<String, Integer> completedOnAccount = Map.of();
		private long lastApiFetch;

		@SubscribeEvent
		public void onRaidCompleted(RaidEndedEvent.Completed event) {
			completedThisSession.merge(key(event.getRaid().getRaidKind().getRaidName()), 1, Integer::sum);
		}

		@Override
		public JsonObject check(Trigger trigger) {
			String raid = key(trigger.raidName());
			int thisSession = completedThisSession.getOrDefault(raid, 0);
			if (thisSession < trigger.amount()) {
				refreshFromApi();
			}
			// The API catches up with this session's clears, so adding the two
			// would count those twice.
			int onAccount = completedOnAccount.getOrDefault(raid, 0);
			int completions = Math.max(thisSession, onAccount);
			if (completions < trigger.amount()) {
				return null;
			}
			JsonObject evidence = new JsonObject();
			evidence.addProperty("source", thisSession >= onAccount ? "raidEvent" : "wynncraftApi");
			evidence.addProperty("completions", completions);
			return evidence;
		}

		private void refreshFromApi() {
			long now = System.currentTimeMillis();
			if (now - lastApiFetch < API_REFRESH_MS || Mc.client().player == null) {
				return;
			}
			lastApiFetch = now;
			Models.Player.getPlayerFullInfo(Mc.client().player.getStringUUID()).thenAccept(info -> {
				if (info == null || info.globalData().isEmpty() || info.globalData().get().raids() == null) {
					return;
				}
				Map<String, Integer> completed = new HashMap<>();
				Map<String, Integer> raids = info.globalData().get().raids().completions();
				if (raids != null) {
					raids.forEach((name, count) -> completed.merge(key(name), count, Integer::sum));
				}
				completedOnAccount = completed;
			});
		}

		private static String key(String raidName) {
			return raidName == null ? "" : raidName.toLowerCase(Locale.ROOT);
		}
	}
}
