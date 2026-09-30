package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The duel picker never offers a stake over the class's biggest purse, which is the server's cap. */
class DuelStakeTest {
	@Test
	void pickerStopsAtTheBiggestPurseOfTheClass() {
		for (RaceClass rc : RaceClass.values()) {
			int maxPurse = DuelStakes.biggestPurse(rc);
			int[] stakes = DuelStakes.upTo(maxPurse);
			assertEquals(0, stakes[0], rc + " starts with no side bet");
			int top = stakes[stakes.length - 1];
			assertEquals(RaceScoring.clampDuelStake(maxPurse), top, rc + " tops out at the purse the server allows");
			for (int i = 1; i < stakes.length; i++) {
				assertTrue(stakes[i] > stakes[i - 1], rc + " stakes climb");
			}
			for (RaceTrack t : RaceTrack.ofClass(rc)) {
				assertTrue(RaceScoring.purse(t) <= maxPurse, t.id() + " purse within the class's biggest");
			}
		}
	}

	@Test
	void stepsBetweenTheFixedStakesEndOnThePurse() {
		assertArrayEquals(new int[]{0, 4, 8, 9}, DuelStakes.upTo(9));
		assertArrayEquals(new int[]{0, 4, 8}, DuelStakes.upTo(8));
		assertArrayEquals(new int[]{0, 4, 8, 16, 32}, DuelStakes.upTo(72));
	}
}
