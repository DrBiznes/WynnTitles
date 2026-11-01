package wynntitles.achievements;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import wynntitles.achievements.RaidLastStand.Member;

class RaidLastStandTest {
	private static final String RAID = "The Wartorn Palace";
	private static final Member SELF = new Member("Me", true, true);
	private static final Member DEAD_A = new Member("Alice", true, false);
	private static final Member DEAD_B = new Member("Bob", true, false);
	private static final Member ALIVE = new Member("Cleo", true, true);
	private static final Member OFFLINE = new Member("Dana", false, false);

	/** The fallen count from a win straight after this party list. */
	private static int win(int partySize, List<Member> rows) {
		RaidLastStand stand = new RaidLastStand();
		stand.onParty("Me", partySize, rows, 1000);
		stand.onCompleted(RAID, 1000);
		return stand.fallen(RAID);
	}

	@Test
	void everyoneElseDeadCountsWhetherOrNotThePlayerIsListed() {
		assertEquals(2, win(3, List.of(SELF, DEAD_A, DEAD_B)));
		assertEquals(2, win(3, List.of(DEAD_A, DEAD_B)));
		assertEquals(1, win(2, List.of(new Member("ME", true, true), DEAD_A)));
	}

	@Test
	void aSurvivorOrDyingYourselfDoesNot() {
		assertEquals(0, win(3, List.of(SELF, DEAD_A, ALIVE)));
		assertEquals(0, win(2, List.of(new Member("Me", true, false), ALIVE)));
		assertEquals(0, win(3, List.of(new Member("Me", true, false), DEAD_A, DEAD_B)));
	}

	@Test
	void soloAndDisconnectsAreNotLastStands() {
		assertEquals(0, win(0, List.of()));
		assertEquals(0, win(1, List.of(SELF)));
		assertEquals(0, win(2, List.of(SELF, OFFLINE)));
		assertEquals(1, win(3, List.of(SELF, DEAD_A, OFFLINE)));
	}

	@Test
	void anUnreadableRowCouldBeASurvivor() {
		assertEquals(0, win(4, List.of(SELF, DEAD_A, DEAD_B)));
		assertEquals(0, win(4, List.of(DEAD_A, DEAD_B)));
	}

	@Test
	void aPartyNotListedYetDoesNotBlockIt() {
		assertEquals(1, win(0, List.of(SELF, DEAD_A)));
	}

	@Test
	void theWinMayArriveJustAfterTheRowsChange() {
		RaidLastStand stand = new RaidLastStand();
		stand.onParty("Me", 2, List.of(SELF, DEAD_A), 1000);
		stand.onParty("Me", 2, List.of(SELF, new Member("Alice", true, true)), 3000);
		stand.onCompleted(RAID, 4000);
		assertEquals(1, stand.fallen(RAID));
	}

	@Test
	void anOldStandDoesNotCount() {
		RaidLastStand stand = new RaidLastStand();
		stand.onParty("Me", 2, List.of(SELF, DEAD_A), 1000);
		stand.onCompleted(RAID, 11_001);
		assertEquals(0, stand.fallen(RAID));
	}

	@Test
	void aStandEndsWithItsRaidAndCountsForThatRaidOnly() {
		RaidLastStand stand = new RaidLastStand();
		stand.onParty("Me", 2, List.of(SELF, DEAD_A), 1000);
		stand.onCompleted("The Canyon Colossus", 1000);
		stand.onCompleted(RAID, 1000);
		assertEquals(1, stand.fallen("the canyon colossus"));
		assertEquals(0, stand.fallen(RAID));
	}
}
