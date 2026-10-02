package tk.darrow.chocobosreborn.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RivalRollTest {
	@Test
	void theNamedRivalsRunAboutTwoHeatsInThreeInEveryClass() {
		assertEquals(2.0D / 3.0D, RaceClass.RIVAL_CHANCE);
		for (RaceClass rc : RaceClass.values()) {
			assertTrue(rc.rollRivals(0.0D), rc + " can field its rivals");
			assertFalse(rc.rollRivals(0.67D), rc + " sits them out above the line");
			java.util.Random random = new java.util.Random(42L + rc.ordinal());
			int runs = 0;
			int heats = 4000;
			for (int i = 0; i < heats; i++) {
				if (rc.rollRivals(random.nextDouble())) {
					runs++;
				}
			}
			double share = runs / (double) heats;
			assertTrue(share > 0.63D && share < 0.70D, rc + " rivals ran " + share + " of heats");
		}
	}
}
