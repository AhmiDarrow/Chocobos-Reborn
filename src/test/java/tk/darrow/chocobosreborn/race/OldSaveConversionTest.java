package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import tk.darrow.chocobosreborn.breed.BreedGenes;

/**
 * Birds saved by older versions are brought onto the current rules once, one step at a
 * time: format 1 -> 2 (old marks to points of nine), 2 -> 3 (points of nine to points of 36).
 */
class OldSaveConversionTest {
	@Test
	void oldMarksKeepTheirShareOfThePromotion() {
		// format 1 -> 2: two of the old three becomes six of nine, not two of nine
		assertEquals(3, RaceScoring.migratedClassPoints(RaceClass.C.getId(), 1));
		assertEquals(6, RaceScoring.migratedClassPoints(RaceClass.B.getId(), 2));
		assertEquals(0, RaceScoring.migratedClassPoints(RaceClass.A.getId(), 0));
	}

	@Test
	void pointsOnTheNineLadderStayThroughStepOne() {
		assertEquals(4, RaceScoring.migratedClassPoints(RaceClass.C.getId(), 4));
		assertEquals(8, RaceScoring.migratedClassPoints(RaceClass.A.getId(), 8));
	}

	@Test
	void stepOneShowsClassSAsTheFullNine() {
		assertEquals(9, RaceScoring.migratedClassPoints(RaceClass.S.getId(), 3));
	}

	@Test
	void pointsOfNineBecomePointsOfThirtySix() {
		// format 2 -> 3: x4, the same share of the way up
		assertEquals(0, RaceScoring.rescaledClassPoints(RaceClass.C.getId(), 0));
		assertEquals(4, RaceScoring.rescaledClassPoints(RaceClass.C.getId(), 1));
		assertEquals(20, RaceScoring.rescaledClassPoints(RaceClass.B.getId(), 5));
		assertEquals(32, RaceScoring.rescaledClassPoints(RaceClass.A.getId(), 8));
		assertEquals(RaceClass.POINTS_TO_PROMOTE, RaceScoring.rescaledClassPoints(RaceClass.S.getId(), 9));
		for (int old = 0; old < 9; old++) {
			// a bird k sprint wins short of promotion is still k sprint wins short
			int now = RaceScoring.rescaledClassPoints(RaceClass.C.getId(), old);
			assertEquals((9 - old) * 4, RaceScoring.winsUntilPromote(RaceClass.C, now));
		}
	}

	@Test
	void aFormatTwoBirdTakesOnlyTheSecondStep() {
		assertEquals(20, RaceScoring.convertedClassPoints(2, RaceClass.B.getId(), 5));
		// 1 or 2 on the nine ladder is points there, not old marks: 1 sprint win stays 1 sprint win
		assertEquals(4, RaceScoring.convertedClassPoints(2, RaceClass.C.getId(), 1));
		assertEquals(8, RaceScoring.convertedClassPoints(2, RaceClass.C.getId(), 2));
		assertEquals(36, RaceScoring.convertedClassPoints(2, RaceClass.S.getId(), 9));
	}

	@Test
	void aFormatOneBirdTakesBothStepsInOrder() {
		// 1 mark -> 3 of 9 -> 12 of 36; 2 marks -> 6 of 9 -> 24 of 36
		assertEquals(12, RaceScoring.convertedClassPoints(1, RaceClass.C.getId(), 1));
		assertEquals(24, RaceScoring.convertedClassPoints(0, RaceClass.B.getId(), 2));
		assertEquals(12, RaceScoring.winsUntilPromote(RaceClass.B, RaceScoring.convertedClassPoints(0, RaceClass.B.getId(), 2)));
		assertEquals(0, RaceScoring.convertedClassPoints(1, RaceClass.A.getId(), 0));
		assertEquals(RaceClass.POINTS_TO_PROMOTE, RaceScoring.convertedClassPoints(1, RaceClass.S.getId(), 3));
		assertEquals(0, RaceScoring.winsUntilPromote(RaceClass.S, RaceScoring.convertedClassPoints(1, RaceClass.S.getId(), 3)));
	}

	@Test
	void aCurrentBirdIsLeftAlone() {
		assertEquals(26, RaceScoring.convertedClassPoints(3, RaceClass.C.getId(), 26));
		assertEquals(2, RaceScoring.convertedClassPoints(3, RaceClass.C.getId(), 2));
	}

	@Test
	void onlyABirdWithNoBornStatsRollsABloodline() {
		assertTrue(BreedGenes.blankLine(0, 0, 0, 0));
		assertFalse(BreedGenes.blankLine(0, 0, 0, 1));
		assertFalse(BreedGenes.blankLine(12, 3, 5, 7));
	}
}
