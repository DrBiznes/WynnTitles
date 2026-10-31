package wynntitles.achievements;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.Map;
import org.junit.jupiter.api.Test;
import wynntitles.catalog.Catalog;

class AchievementTrackerTest {
	private static final Detector NEVER = trigger -> null;

	@Test
	void canDetectNeedsADetectorThatSupportsTheTrigger() {
		Detector questsOnlyA = new Detector() {
			@Override
			public JsonObject check(Catalog.Trigger trigger) {
				return null;
			}

			@Override
			public boolean supports(Catalog.Trigger trigger) {
				return "A".equals(trigger.questName());
			}
		};
		// canDetect only reads the detectors, so the rest can be null.
		AchievementTracker tracker = new AchievementTracker(
				null, null, null, Map.of("quest", questsOnlyA, "location", NEVER), a -> {}, "test", () -> true);

		assertTrue(tracker.canDetect(achievement(trigger("quest", "A"))));
		assertFalse(tracker.canDetect(achievement(trigger("quest", "B"))), "detector said it cannot check B");
		assertTrue(tracker.canDetect(achievement(trigger("location", null))), "supports defaults to true");
		assertFalse(tracker.canDetect(achievement(trigger("dungeon", null))), "no detector for the type");
	}

	private static Catalog.Trigger trigger(String type, String questName) {
		return new Catalog.Trigger(type, questName, null, 0, null, null, 0, null, null,
				null, null, null, null, null, null, null, null, null, 0, 0, 0);
	}

	private static Catalog.Achievement achievement(Catalog.Trigger trigger) {
		return new Catalog.Achievement("id", "category", "Name", "", "bronze", 1, null, trigger, null);
	}
}
