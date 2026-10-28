package wynntitles.achievements;

import com.google.gson.JsonObject;
import com.wynntils.core.components.Models;
import com.wynntils.models.activities.type.ActivityStatus;
import com.wynntils.models.players.type.wynnplayer.CharacterData;
import com.wynntils.models.players.type.wynnplayer.GlobalData;
import com.wynntils.models.players.type.wynnplayer.WynnPlayerInfo;
import com.wynntils.models.profession.type.ProfessionType;
import com.wynntils.models.territories.profile.TerritoryProfile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;
import net.minecraft.world.phys.Vec3;
import wynntitles.catalog.Catalog.Trigger;
import wynntitles.mc.Mc;

/**
 * Detectors for every trigger type in the catalog, built on Wynntils models.
 * A catalog entry this mod cannot check is skipped, and the achievements
 * screen asks the player to update.
 */
public final class WynntilsDetectors {
	private WynntilsDetectors() {}

	public static Map<String, Detector> create() {
		Map<String, Detector> detectors = new HashMap<>();
		detectors.put("combatLevel", WynntilsDetectors::combatLevel);
		detectors.put("combatLevelAnyClass", WynntilsDetectors::combatLevelAnyClass);
		detectors.put("totalProfessionLevel", WynntilsDetectors::totalProfessionLevel);
		detectors.put("location", WynntilsDetectors::location);
		detectors.put("inventoryItem", WynntilsDetectors::inventoryItem);
		detectors.put("statistic", new StatisticDetector());
		detectors.put("quest", new QuestDetector());
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
}
