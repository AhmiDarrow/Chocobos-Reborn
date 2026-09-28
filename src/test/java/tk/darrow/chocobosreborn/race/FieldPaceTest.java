package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import tk.darrow.chocobosreborn.breed.ChocoboColor;
import tk.darrow.chocobosreborn.breed.ChocoboGreen;

/** Who a rider actually races against, class by class. */
class FieldPaceTest {
	private static final RaceClass[] LADDER = {RaceClass.C, RaceClass.B, RaceClass.A, RaceClass.S};

	@Test
	void theFieldGetsFasterUpTheLadder() {
		for (int i = 1; i < LADDER.length; i++) {
			assertTrue(RaceScoring.fieldPace(LADDER[i]) > RaceScoring.fieldPace(LADDER[i - 1]), LADDER[i].name());
		}
	}

	@Test
	void aClassCFieldRacesAtGoodGradeWhateverItsTrainingRoll() {
		int lowest = RaceScoring.fieldTraining(0, false) - 4;
		// born Average + one training step = Good, even on the lowest +-4 roll
		assertEquals(2, ChocoboGreen.gradeFromTraining(1, lowest * 4));
	}

	@Test
	void aFreshGoodBirdHasAContestInClassC() {
		// a Good bird with no training cruises at 1.00; the C field sits just under it
		double c = RaceScoring.fieldPace(RaceClass.C);
		assertTrue(c > 0.95D && c < 1.0D, "C field " + c);
	}

	@Test
	void rivalsRunOffTheRidersBirdAndNeverTrailTheirFloor() {
		// AI pass 2026-09-27: floor and shares re-set from RaceSimTest (the old floor 1.06 and
		// shares 1.00-1.10 made B Teiyo faster than a maxed Green once colour was counted)
		double field = RaceScoring.fieldPaceAbs(RaceClass.B);
		// a slow rider: the rival sits on his floor
		assertEquals(field * RaceScoring.rivalFloor(RaceClass.B), RaceScoring.rivalPaceAbs(RaceClass.B, false, 0.05D), 1.0E-9);
		// a strong rider: S Teiyo is 8 % over the rider's cruise, Jolo 4 % under Teiyo
		assertEquals(1.08D * 2.0D, RaceScoring.rivalPaceAbs(RaceClass.S, false, 2.0D), 1.0E-9);
		assertEquals(1.08D * 2.0D * 0.96D, RaceScoring.rivalPaceAbs(RaceClass.S, true, 2.0D), 1.0E-9);
		assertTrue(RaceScoring.rivalPace(RaceClass.A, false, 1.6D, 0.0D) < RaceScoring.rivalPace(RaceClass.S, false, 1.6D, 0.0D));
	}

	@Test
	void rivalsPaceTheRidersLandSpeedToo() {
		// 1.0.18 fed the rider's grade x training only and ran it on Teiyo's Black land speed:
		// a Green rider in B met a Teiyo faster than a maxed Green. The pace now has land speed in.
		double yellow = RaceScoring.absolutePace(ChocoboColor.YELLOW.landSpeed(), 4, 100);
		double gold = RaceScoring.absolutePace(ChocoboColor.GOLD.landSpeed(), 4, 100);
		assertEquals(1.08D * gold, RaceScoring.rivalPaceAbs(RaceClass.S, false, gold), 1e-9);
		assertTrue(RaceScoring.rivalPaceAbs(RaceClass.S, false, gold) > RaceScoring.rivalPaceAbs(RaceClass.S, false, yellow));
	}

	@Test
	void fieldBirdsKeepAQuarterOfTheirColourEdge() {
		// a Yellow in S used to run at half a Black's pace and was never in the race
		double ref = RaceScoring.classLandSpeed(RaceClass.S);
		assertEquals(ref + 0.25D * (ChocoboColor.YELLOW.landSpeed() - ref),
				RaceScoring.fieldLandSpeed(ChocoboColor.YELLOW, RaceClass.S), 1e-12);
		for (RaceClass rc : LADDER) {
			double lo = Double.MAX_VALUE, hi = 0.0D;
			for (FieldRoster.Entry e : FieldRoster.home(rc)) {
				double v = RaceScoring.fieldLandSpeed(e.color(), rc);
				lo = Math.min(lo, v);
				hi = Math.max(hi, v);
			}
			assertTrue(hi / lo < 1.20D, rc + " field spread " + hi / lo);
		}
		// the colours still order the field: a Black is the S favourite over a Yellow
		assertTrue(RaceScoring.fieldLandSpeed(ChocoboColor.BLACK, RaceClass.S) > RaceScoring.fieldLandSpeed(ChocoboColor.YELLOW, RaceClass.S));
	}

	@Test
	void goldRacesOnlyInClassS() {
		assertEquals(ChocoboColor.GOLD, RaceScoring.joloColor(RaceClass.S));
		assertNotEquals(ChocoboColor.GOLD, RaceScoring.joloColor(RaceClass.B));
		assertNotEquals(ChocoboColor.GOLD, RaceScoring.joloColor(RaceClass.A));
		for (FieldRoster.Entry e : FieldRoster.all()) {
			assertNotEquals(ChocoboColor.GOLD, e.color(), e.name());
		}
	}

	@Test
	void classBRunsYellowGreenAndBlue() {
		for (FieldRoster.Entry e : FieldRoster.home(RaceClass.B)) {
			ChocoboColor c = e.color();
			assertTrue(c == ChocoboColor.YELLOW || c == ChocoboColor.GREEN || c == ChocoboColor.BLUE, e.name() + " " + c);
		}
	}
}
