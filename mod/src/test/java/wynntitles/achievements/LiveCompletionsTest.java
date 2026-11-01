package wynntitles.achievements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The messages are captured from the game, padding glyphs included. */
class LiveCompletionsTest {
	private static final String PADDING = new String(Character.toChars(0xD0074));
	private static final String NAME_PADDING = new String(Character.toChars(0xD006F));
	private static final String CORRUPTED_WIN = "§6Great job! You've completed the CorruptedÀÀÀDecrepitÀÀÀSewers Dungeon!";

	@Test
	void dungeonWinCountsWithSpacesInItsName() {
		LiveCompletions live = new LiveCompletions();
		live.onMessage("§6Great job! You've completed the DecrepitÀÀÀSewers Dungeon!", 0);

		assertEquals(1, live.dungeonClears("Decrepit Sewers"));
		assertEquals(0, live.dungeonClears("Corrupted Decrepit Sewers"));
	}

	@Test
	void corruptedWinKeepsItsOwnName() {
		LiveCompletions live = new LiveCompletions();
		live.onMessage(CORRUPTED_WIN, 0);

		assertEquals(1, live.dungeonClears("Corrupted Decrepit Sewers"));
		assertEquals(0, live.dungeonClears("Decrepit Sewers"));
	}

	@Test
	void repeatedDungeonMessageCountsOnce() {
		LiveCompletions live = new LiveCompletions();
		live.onMessage(CORRUPTED_WIN, 0);
		live.onMessage(CORRUPTED_WIN, 200);
		live.onMessage(CORRUPTED_WIN, 9_000);

		assertEquals(1, live.dungeonClears("Corrupted Decrepit Sewers"));
	}

	@Test
	void laterClearCountsAgain() {
		LiveCompletions live = new LiveCompletions();
		live.onMessage(CORRUPTED_WIN, 0);
		live.onMessage(CORRUPTED_WIN, 60_000);

		assertEquals(2, live.dungeonClears("Corrupted Decrepit Sewers"));
	}

	@Test
	void questNameIsTheMessageAfterTheHeader() {
		LiveCompletions live = new LiveCompletions();
		live.onMessage("", 0);
		live.onMessage(PADDING + "§6[Quest Completed]", 0);
		live.onMessage(NAME_PADDING + "§e§lDearly Departed", 0);

		assertTrue(live.questCompleted("Dearly Departed"));
	}

	@Test
	void questNameOnItsOwnCountsForNothing() {
		LiveCompletions live = new LiveCompletions();
		live.onMessage(NAME_PADDING + "§e§lDearly Departed", 0);

		assertFalse(live.questCompleted("Dearly Departed"));
	}

	@Test
	void onlyTheMessageAfterTheHeaderIsAName() {
		LiveCompletions live = new LiveCompletions();
		live.onMessage(PADDING + "§6[Quest Completed]", 0);
		live.onMessage("§7[You are now leaving Ragni]", 0);
		live.onMessage(NAME_PADDING + "§e§lDearly Departed", 0);

		assertFalse(live.questCompleted("Dearly Departed"));
	}

	@Test
	void otherMessagesRecordNothing() {
		LiveCompletions live = new LiveCompletions();
		live.onMessage("§7Complete the dungeon for this run to count towards the Forgery Chest.", 0);
		live.onMessage("§b[1/8] §7Dungeon Runs complete.", 0);
		live.onMessage("Great job! You've completed the Decrepit Sewers Dungeon!", 0);
		live.onMessage("someone: §6Great job! You've completed the Decrepit Sewers Dungeon!", 0);
		live.onMessage("[Quest Completed]", 0);

		assertEquals(0, live.dungeonClears("Decrepit Sewers"));
		assertFalse(live.questCompleted("Dearly Departed"));
	}

	@Test
	void countAddsSessionClearsToTheFirstApiCount() {
		LiveCompletions live = new LiveCompletions();
		assertEquals(9, live.dungeonCount("Galleon's Graveyard", 9));
		live.onMessage("§6Great job! You've completed the Galleon'sÀÀÀGraveyard Dungeon!", 0);

		assertEquals(10, live.dungeonCount("Galleon's Graveyard", 9), "the API is behind");
		assertEquals(10, live.dungeonCount("Galleon's Graveyard", 10), "the API caught up");
		assertEquals(12, live.dungeonCount("Galleon's Graveyard", 12), "the API is ahead");
	}

	@Test
	void countIsSessionClearsWithoutAProfile() {
		LiveCompletions live = new LiveCompletions();
		live.onMessage(CORRUPTED_WIN, 0);

		assertEquals(1, live.dungeonCount("Corrupted Decrepit Sewers", null));
	}
}
