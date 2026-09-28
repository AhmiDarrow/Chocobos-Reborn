package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;

import org.junit.jupiter.api.Test;

class RacerProfileTest {
	private static final RaceClass[] LADDER = {RaceClass.C, RaceClass.B, RaceClass.A, RaceClass.S};

	@Test
	void everyAxisGetsHarderUpTheLadder() {
		for (int i = 1; i < LADDER.length; i++) {
			RacerProfile lo = RacerProfile.of(LADDER[i - 1], RacerProfile.Role.FIELD);
			RacerProfile hi = RacerProfile.of(LADDER[i], RacerProfile.Role.FIELD);
			String step = LADDER[i - 1] + "->" + LADDER[i];
			assertTrue(hi.cruise() >= lo.cruise(), "cruise " + step);
			assertTrue(hi.dash() > lo.dash(), "dash " + step);
			assertTrue(hi.reactionMax() < lo.reactionMax(), "reaction " + step);
			assertTrue(hi.wobble() < lo.wobble(), "wobble " + step);
			assertTrue(hi.stumbleChancePerLap() < lo.stumbleChancePerLap(), "stumbles " + step);
			assertTrue(hi.lineHold() > lo.lineHold(), "line " + step);
			assertTrue(hi.brake() < lo.brake(), "corner lift " + step);
			assertTrue(hi.boostAim() > lo.boostAim(), "boost aim " + step);
			assertTrue(hi.reserve() >= lo.reserve(), "reserve " + step);
		}
	}

	@Test
	void bogSavvyFollowsLineHoldNotTheCDefault() {
		double c = RacerProfile.bogSavvyChance(RacerProfile.of(RaceClass.C, RacerProfile.Role.FIELD).lineHold());
		double s = RacerProfile.bogSavvyChance(RacerProfile.of(RaceClass.S, RacerProfile.Role.FIELD).lineHold());
		assertEquals(0.45D + 0.55D * 0.35D, c, 1.0E-9);
		assertTrue(s > c);
		assertTrue(s > 0.9D);
	}

	@Test
	void sClassReactsFromGoNotBeforeIt() {
		RacerProfile s = RacerProfile.of(RaceClass.S, RacerProfile.Role.FIELD);
		// reaction runs from GO after the visual countdown: S is sharpest but never jumps the lights
		assertTrue(s.reactionMax() <= 12 && s.reactionMin() >= 5);
	}

	@Test
	void nothingInTheProfileLooksAtThePlayer() {
		// the old rubberBand / bandFactor and the player-gap dash trigger are gone for good
		for (RecordComponent rc : RacerProfile.class.getRecordComponents()) {
			String n = rc.getName().toLowerCase();
			assertFalse(n.contains("band") || n.contains("gap") || n.contains("player"), rc.getName());
		}
		for (var m : RacerProfile.class.getDeclaredMethods()) {
			String n = m.getName().toLowerCase();
			assertFalse(n.contains("band") || n.contains("gap"), m.getName());
		}
		// the dead per-profile energy budget is gone: the AI spends its bird's own stamina bar
		for (RecordComponent rc : RacerProfile.class.getRecordComponents()) {
			assertFalse(rc.getName().startsWith("energy"), rc.getName());
		}
	}

	@Test
	void rivalsDriveAtSDiscipline() {
		RacerProfile s = RacerProfile.of(RaceClass.S, RacerProfile.Role.FIELD);
		for (RaceClass rc : LADDER) {
			for (RacerProfile.Role role : new RacerProfile.Role[]{RacerProfile.Role.TEIYO, RacerProfile.Role.JOLO}) {
				RacerProfile r = RacerProfile.of(rc, role);
				assertEquals(s.wobble(), r.wobble(), 1.0E-9);
				assertEquals(s.reactionMax(), r.reactionMax());
				assertEquals(s.dash(), r.dash(), 1.0E-9);
				assertEquals(s.brake(), r.brake(), 1.0E-9);
			}
		}
	}

	@Test
	void dashStrategy() {
		RacerProfile s = RacerProfile.of(RaceClass.S, RacerProfile.Role.FIELD);
		double r = s.reserve();
		// a straight with bar to spare: dash; keep the reserve back until the push
		assertTrue(s.wantsDash(0.90D, false, true, 1.0D, false), "dashes the straights when flush");
		assertFalse(s.wantsDash(r - 0.01D, true, true, 1.0D, false), "reserve held");
		assertTrue(s.wantsDash(r - 0.01D, false, true, 1.0D, true), "spends it in the push");
		// a burst runs down to the reserve, then the bar refills a little before the next one
		assertTrue(s.wantsDash(r + 0.05D, true, true, 1.0D, false), "a burst continues");
		assertFalse(s.wantsDash(r + 0.05D, false, true, 1.0D, false), "no one-tick flicker at the reserve");
		// corners: cruise them, and never burn a short bar into a hairpin
		assertFalse(s.wantsDash(r + 0.20D, false, false, 1.0D, false), "cruises the bends");
		assertFalse(s.wantsDash(r + 0.20D, true, true, 0.85D, false), "no dash into a braking corner");
		assertFalse(s.wantsDash(0.20D, true, true, 0.85D, true), "not even in the push");
		// but a bar that sits high recovers nothing, so plenty is spent anywhere
		assertTrue(s.wantsDash(1.0D, false, false, 0.85D, false), "a full bar is spent anywhere");
		assertTrue(s.wantsDash(r + RacerProfile.PLENTY, false, false, 0.85D, false), "so is plenty over the reserve");
		// never runs the bar dry into the lock
		assertFalse(s.wantsDash(RacerProfile.FLOOR, true, true, 1.0D, true), "stops above empty");
		assertFalse(s.wantsDash(0.0D, true, true, 1.0D, true), "nothing left");
	}

	@Test
	void theSprintPushIsTheLastStretchNotTheWholeRace() {
		// 1.0.18 treated a one-lap sprint as "the last lap" from GO: the whole bar went off the grid and into the first corner
		assertFalse(RacerProfile.finalPush(1.0D, 1), "not at the start of a sprint");
		assertFalse(RacerProfile.finalPush(0.6D, 1));
		assertTrue(RacerProfile.finalPush(RacerProfile.SPRINT_PUSH, 1));
		assertFalse(RacerProfile.finalPush(1.5D, 3), "not before the last lap of a grand prix");
		assertTrue(RacerProfile.finalPush(1.0D, 3));
	}

	@Test
	void temperMovesTheReserveOnly() {
		RacerProfile b = RacerProfile.of(RaceClass.B, RacerProfile.Role.FIELD);
		RacerProfile front = b.withTemper(1.0D);
		RacerProfile closer = b.withTemper(-1.0D);
		assertTrue(front.reserve() < b.reserve() && b.reserve() < closer.reserve());
		assertTrue(front.reserve() >= RacerProfile.FLOOR);
		assertEquals(b.cruise(), front.cruise(), 1.0E-12);
		assertEquals(b.dash(), closer.dash(), 1.0E-12);
	}

	@Test
	void cornerLiftIsFreeOnSweepersAndCappedOnHairpins() {
		for (RaceClass rc : LADDER) {
			RacerProfile p = RacerProfile.of(rc, RacerProfile.Role.FIELD);
			assertEquals(1.0D, p.cornerLift(0.2D), 1.0E-12);
			assertEquals(1.0D - p.brake(), p.cornerLift(3.0D), 1.0E-12);
			assertTrue(p.cornerLift(1.0D) < 1.0D && p.cornerLift(1.0D) > 1.0D - p.brake());
		}
	}
}
