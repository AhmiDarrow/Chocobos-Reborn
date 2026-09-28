package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The AI's way out of a stall ({@link RacerRecovery}). Hub harness, A_CRYSTAL: "Vincent"
 * stood outside the rail past the ridge detour's rejoin and "Cait" past the water's for the
 * whole race; the old wall back-off (3 blocks, only while pressed on a wall on the ground)
 * drove them straight back into the same rail post, or never fired at all.
 */
class RacerRecoveryTest {
	private static RacerRecovery.Mode run(RacerRecovery r, int ticks, double perTick) {
		RacerRecovery.Mode m = RacerRecovery.Mode.RACE;
		for (int i = 0; i < ticks; i++) {
			m = r.step(perTick);
		}
		return m;
	}

	@Test
	void aRacerMakingGroundNeverRecovers() {
		RacerRecovery r = new RacerRecovery();
		// a slow bird in a bog: a tenth of a block a tick is still ground made
		assertEquals(RacerRecovery.Mode.RACE, run(r, 2000, 0.1D));
		assertFalse(r.takeSetBack());
	}

	@Test
	void aPinnedRacerSlidesAsideThenBacksOffThenIsSetBack() {
		RacerRecovery r = new RacerRecovery();
		assertEquals(RacerRecovery.Mode.RACE, run(r, RacerRecovery.NO_PROGRESS_TICKS - 1, 0.0D));
		assertEquals(RacerRecovery.Mode.REAIM, run(r, 1, 0.0D), "after two seconds with no ground it re-aims");
		assertEquals(RacerRecovery.Mode.BACK_OFF, run(r, RacerRecovery.REAIM_TICKS, 0.0D), "then backs off");
		assertEquals(RacerRecovery.Mode.RACE, run(r, RacerRecovery.BACK_TICKS, 0.0D), "then has another go");
		assertFalse(r.takeSetBack(), "one cycle is not yet giving up");
		int cycle = RacerRecovery.NO_PROGRESS_TICKS + RacerRecovery.REAIM_TICKS + RacerRecovery.BACK_TICKS;
		run(r, cycle * (RacerRecovery.GIVE_UP_CYCLES - 1), 0.0D);
		assertTrue(r.takeSetBack(), "no ground through two whole recoveries: set it back on the road");
		assertFalse(r.takeSetBack(), "once");
	}

	@Test
	void rockingBackAndForthIsNoGround() {
		// backing off and driving into the same post again: moving every tick, no new ground
		RacerRecovery r = new RacerRecovery();
		boolean setBack = false;
		for (int i = 0; i < 400 && !setBack; i++) {
			r.step((i / 10) % 2 == 0 ? 0.4D : -0.4D);
			setBack = r.takeSetBack();
		}
		assertTrue(setBack, "it never rocks on a wall for good");
	}

	@Test
	void newGroundEndsARecovery() {
		RacerRecovery r = new RacerRecovery();
		run(r, RacerRecovery.NO_PROGRESS_TICKS + 5, 0.0D);
		assertEquals(RacerRecovery.Mode.REAIM, r.mode());
		// back off 3 blocks and come forward again: only past the old best does it count
		run(r, 10, -0.3D);
		assertTrue(r.mode() != RacerRecovery.Mode.RACE, "backing away is no ground");
		run(r, 10, 0.3D);
		assertTrue(r.mode() != RacerRecovery.Mode.RACE, "back where it was stuck: nothing new yet");
		assertEquals(RacerRecovery.Mode.RACE, run(r, 6, 0.5D), "1.5 blocks past the old best: racing again");
		assertTrue(r.stalledTicks() < 5, "counting afresh from the new ground: " + r.stalledTicks());
	}

	@Test
	void climbingARidgeIsGround() {
		// an S ridge is five blocks: 40-odd ticks going up with no progress along the lap;
		// turning aside near the top dropped the climber back down the face
		RacerRecovery r = new RacerRecovery();
		for (int i = 0; i < 60; i++) {
			assertEquals(RacerRecovery.Mode.RACE, r.step(0.0D, true));
		}
		assertEquals(RacerRecovery.Mode.RACE, run(r, RacerRecovery.NO_PROGRESS_TICKS - 1, 0.0D));
	}

	@Test
	void aSetBackStartsAfresh() {
		RacerRecovery r = new RacerRecovery();
		run(r, 170, 0.0D);
		r.reset();
		assertEquals(RacerRecovery.Mode.RACE, r.mode());
		assertEquals(RacerRecovery.Mode.RACE, run(r, RacerRecovery.NO_PROGRESS_TICKS - 1, 0.0D));
		assertFalse(r.takeSetBack());
	}
}
