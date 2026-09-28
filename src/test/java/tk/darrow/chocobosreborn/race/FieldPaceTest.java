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
	void rivalsRunOffTheRidersBirdAndNeverTrailTheField() {
		double field = RaceScoring.fieldPace(RaceClass.B);
		// a slow rider: the rival still beats the field
		assertEquals(field * 1.06D, RaceScoring.rivalPace(RaceClass.B, false, 0.5D, field), 1.0E-9);
		// a strong rider: S Teiyo is 10 % over the rider's stack, Jolo 4 % under Teiyo
		double s = RaceScoring.fieldPace(RaceClass.S);
		assertEquals(1.10D * 2.0D, RaceScoring.rivalPace(RaceClass.S, false, 2.0D, s), 1.0E-9);
		assertEquals(1.10D * 2.0D * 0.96D, RaceScoring.rivalPace(RaceClass.S, true, 2.0D, s), 1.0E-9);
		assertTrue(RaceScoring.rivalPace(RaceClass.A, false, 1.6D, 0.0D) < RaceScoring.rivalPace(RaceClass.S, false, 1.6D, 0.0D));
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
