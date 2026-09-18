package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RacerProfileTest {
	private static final RaceClass[] LADDER = {RaceClass.C, RaceClass.B, RaceClass.A, RaceClass.S};

	@Test
	void everyAxisGetsHarderUpTheLadder() {
		for (int i = 1; i < LADDER.length; i++) {
			RacerProfile lo = RacerProfile.of(LADDER[i - 1], RacerProfile.Role.FIELD);
			RacerProfile hi = RacerProfile.of(LADDER[i], RacerProfile.Role.FIELD);
			String step = LADDER[i - 1] + "->" + LADDER[i];
			assertTrue(hi.cruise() > lo.cruise(), "cruise " + step);
			assertTrue(hi.averageSpeed() > lo.averageSpeed(), "average " + step);
			assertTrue(hi.dash() >= lo.dash(), "dash " + step);
			assertTrue(hi.energyDrain() < lo.energyDrain(), "drain " + step);
			assertTrue(hi.energyRecover() > lo.energyRecover(), "recover " + step);
			assertTrue(hi.reactionMax() < lo.reactionMax(), "reaction " + step);
			assertTrue(hi.wobble() < lo.wobble(), "wobble " + step);
			assertTrue(hi.stumbleChancePerLap() < lo.stumbleChancePerLap(), "stumbles " + step);
			assertTrue(hi.rubberBand() <= lo.rubberBand(), "band " + step);
			assertTrue(hi.lineHold() > lo.lineHold(), "line " + step);
			assertTrue(hi.saveForLastLap() >= lo.saveForLastLap(), "reserve " + step);
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
	void sClassDrivesHonestly() {
		RacerProfile s = RacerProfile.of(RaceClass.S, RacerProfile.Role.FIELD);
		assertEquals(1.0D, s.bandFactor(0.25D), 1.0E-9);
		assertEquals(1.0D, s.bandFactor(-0.25D), 1.0E-9);
		// reaction runs from GO after the visual countdown: S is sharpest but never jumps the lights
		assertTrue(s.reactionMax() <= 12 && s.reactionMin() >= 8);
	}

	@Test
	void cClassRubberBandsBothWays() {
		RacerProfile c = RacerProfile.of(RaceClass.C, RacerProfile.Role.FIELD);
		assertTrue(c.bandFactor(0.25D) > 1.0D, "speeds up when the player is far ahead");
		assertTrue(c.bandFactor(-0.25D) < 1.0D, "eases off when it leads by a lot");
		assertEquals(1.0D, c.bandFactor(0.0D), 1.0E-9);
		assertEquals(c.bandFactor(0.25D), c.bandFactor(2.0D), 1.0E-9, "clamped");
	}

	@Test
	void rivalsAreFasterAndDriveAtSDiscipline() {
		for (RaceClass rc : LADDER) {
			RacerProfile field = RacerProfile.of(rc, RacerProfile.Role.FIELD);
			RacerProfile teiyo = RacerProfile.of(rc, RacerProfile.Role.TEIYO);
			RacerProfile jolo = RacerProfile.of(rc, RacerProfile.Role.JOLO);
			RacerProfile s = RacerProfile.of(RaceClass.S, RacerProfile.Role.FIELD);
			assertTrue(teiyo.cruise() > jolo.cruise() && jolo.cruise() > field.cruise(), rc.name());
			assertEquals(s.wobble(), teiyo.wobble(), 1.0E-9);
			assertEquals(s.reactionMax(), jolo.reactionMax());
			assertEquals(0.0D, teiyo.rubberBand(), 1.0E-9);
		}
	}

	@Test
	void dashStrategyFollowsTheProfile() {
		RacerProfile c = RacerProfile.of(RaceClass.C, RacerProfile.Role.FIELD);
		RacerProfile s = RacerProfile.of(RaceClass.S, RacerProfile.Role.FIELD);
		// C burns whatever it has, even in the corners, once above its low threshold
		assertTrue(c.wantsDash(0.5D, false, false, 0.0D) || c.wantsDash(0.5D, true, false, 0.0D));
		assertTrue(c.wantsDash(0.5D, true, false, 0.0D));
		// S keeps a reserve for the last lap unless the player is pulling away
		assertFalse(s.wantsDash(0.40D, true, false, 0.0D), "reserve held");
		assertTrue(s.wantsDash(0.40D, true, false, 0.20D), "answers a breakaway");
		assertTrue(s.wantsDash(0.40D, false, true, 0.0D), "spends it on the last lap");
		assertTrue(s.wantsDash(0.80D, true, false, 0.0D), "dashes the straights when flush");
		assertFalse(s.wantsDash(0.80D, false, false, 0.0D), "cruises the corners");
		assertFalse(s.wantsDash(0.0D, true, true, 1.0D), "nothing left");
	}

	@Test
	void averageSpeedIsBetweenCruiseAndFullDash() {
		for (RaceClass rc : LADDER) {
			RacerProfile p = RacerProfile.of(rc, RacerProfile.Role.FIELD);
			assertTrue(p.averageSpeed() > p.cruise());
			assertTrue(p.averageSpeed() < p.cruise() * p.dash());
		}
	}
}
