package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RiderBudgetTest {
	private static final double CAP = RiderBudget.PER_TICK * RiderBudget.BANK_TICKS;

	@Test
	void aFreshRiderStartsWithAFullBank() {
		assertEquals(CAP, RiderBudget.refill(0.0D, Long.MIN_VALUE, 100L), 1.0E-9);
	}

	@Test
	void theBankRefillsEachTickUpToItsCap() {
		assertEquals(RiderBudget.PER_TICK, RiderBudget.refill(0.0D, 10L, 11L), 1.0E-9);
		assertEquals(CAP, RiderBudget.refill(0.0D, 10L, 10_000L), 1.0E-9, "a long pause banks only the cap");
		assertEquals(5.0D, RiderBudget.refill(5.0D, 10L, 10L), 1.0E-9, "packets within one tick share its budget");
		assertEquals(5.0D, RiderBudget.refill(5.0D, 10L, 9L), 1.0E-9, "time never runs backwards into credit");
	}

	@Test
	void theFastestLegitimateBirdsFitWithRoomToSpare() {
		// harness races: 99.9th percentile 4.6 blocks a tick; the budget must never be the reason a real rider is snapped
		assertTrue(RiderBudget.PER_TICK >= 4.6D * 1.25D);
		assertTrue(RiderBudget.STEP_CAP >= RiderBudget.PER_TICK, "a single packet can always use a tick's budget");
		// a one-second stall's backlog (twenty ticks of top speed) still catches up in one go
		assertTrue(CAP >= 20 * 4.6D);
	}

	@Test
	void contactLeadsTheShownFieldByItsDelayAndTheRoundTrip() {
		assertEquals(RacerContact.leadTicks(100), RacerContact.leadTicks(100, -1.0D), "no frames: vanilla's lerp lead");
		assertEquals(4, RacerContact.leadTicks(100, 2.0D), "2 ticks of playout delay + a 100 ms round trip");
		assertEquals(RacerContact.MAX_LEAD_TICKS, RacerContact.leadTicks(1000, 6.0D), "capped");
	}
}
