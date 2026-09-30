package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import tk.darrow.chocobosreborn.breed.BreedGenes;

/**
 * Birds saved by older versions are brought onto the current rules once, one step at a
 * time: format 1 -> 2 (old marks to points of nine), 2 -> 3 (points of nine to points of 36),
 * 3 -> 4 (uniform 36 to per-class C 36 / B 54 / A 72), 4 -> 5 (lifetime wins shared out per class).
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
	void pointsOfThirtySixScaleToThePerClassBar() {
		// format 3 -> 4: keep the same share of the way up
		assertEquals(0, RaceScoring.scaledToPerClass(RaceClass.C.getId(), 0));
		assertEquals(18, RaceScoring.scaledToPerClass(RaceClass.C.getId(), 18));
		assertEquals(27, RaceScoring.scaledToPerClass(RaceClass.B.getId(), 18)); // half of 54
		assertEquals(36, RaceScoring.scaledToPerClass(RaceClass.A.getId(), 18)); // half of 72
		assertEquals(52, RaceScoring.scaledToPerClass(RaceClass.B.getId(), 35)); // 35 * 54 / 36
		assertEquals(70, RaceScoring.scaledToPerClass(RaceClass.A.getId(), 35)); // 35 * 72 / 36
		assertEquals(RaceClass.POINTS_A, RaceScoring.scaledToPerClass(RaceClass.S.getId(), 36));
	}

	@Test
	void aFormatThreeBirdTakesOnlyThePerClassStep() {
		assertEquals(26, RaceScoring.convertedClassPoints(3, RaceClass.C.getId(), 26));
		assertEquals(39, RaceScoring.convertedClassPoints(3, RaceClass.B.getId(), 26)); // 26 * 54 / 36
		assertEquals(52, RaceScoring.convertedClassPoints(3, RaceClass.A.getId(), 26)); // 26 * 72 / 36
		assertEquals(72, RaceScoring.convertedClassPoints(3, RaceClass.S.getId(), 36));
	}

	@Test
	void aFormatTwoBirdTakesTheLastTwoSteps() {
		// 5 of 9 -> 20 of 36 -> 30 of 54 on B
		assertEquals(30, RaceScoring.convertedClassPoints(2, RaceClass.B.getId(), 5));
		// 1 or 2 on the nine ladder is points there, not old marks: 1 sprint win stays 1 sprint win on C
		assertEquals(4, RaceScoring.convertedClassPoints(2, RaceClass.C.getId(), 1));
		assertEquals(8, RaceScoring.convertedClassPoints(2, RaceClass.C.getId(), 2));
		assertEquals(72, RaceScoring.convertedClassPoints(2, RaceClass.S.getId(), 9));
	}

	@Test
	void aFormatOneBirdTakesAllStepsInOrder() {
		// 1 mark -> 3 of 9 -> 12 of 36 (C stays 12); 2 marks -> 6 of 9 -> 24 of 36 -> 36 of 54 on B
		assertEquals(12, RaceScoring.convertedClassPoints(1, RaceClass.C.getId(), 1));
		assertEquals(36, RaceScoring.convertedClassPoints(0, RaceClass.B.getId(), 2));
		assertEquals(18, RaceScoring.winsUntilPromote(RaceClass.B, RaceScoring.convertedClassPoints(0, RaceClass.B.getId(), 2)));
		assertEquals(0, RaceScoring.convertedClassPoints(1, RaceClass.A.getId(), 0));
		assertEquals(RaceClass.POINTS_A, RaceScoring.convertedClassPoints(1, RaceClass.S.getId(), 3));
		assertEquals(0, RaceScoring.winsUntilPromote(RaceClass.S, RaceScoring.convertedClassPoints(1, RaceClass.S.getId(), 3)));
	}

	@Test
	void aCurrentBirdIsLeftAlone() {
		assertEquals(26, RaceScoring.convertedClassPoints(4, RaceClass.C.getId(), 26));
		assertEquals(40, RaceScoring.convertedClassPoints(4, RaceClass.B.getId(), 40));
		assertEquals(2, RaceScoring.convertedClassPoints(4, RaceClass.C.getId(), 2));
	}

	@Test
	void mostWinsInAClassIsItsBarInSprints() {
		assertEquals(9, RaceScoring.mostWinsInClass(RaceClass.C));
		assertEquals(14, RaceScoring.mostWinsInClass(RaceClass.B));
		assertEquals(18, RaceScoring.mostWinsInClass(RaceClass.A));
	}

	@Test
	void formatFourWinsShareOutLowestClassFirst() {
		// format 4 -> 5: lifetime wins go to the classes reached, capped, the rest to the current class
		assertArrayEquals(new int[]{0, 0, 0}, RaceScoring.migratedWinsByClass(RaceClass.C.getId(), 0));
		assertArrayEquals(new int[]{4, 0, 0}, RaceScoring.migratedWinsByClass(RaceClass.C.getId(), 4));
		// a Class C bird keeps everything in C, over the cap or not
		assertArrayEquals(new int[]{12, 0, 0}, RaceScoring.migratedWinsByClass(RaceClass.C.getId(), 12));
		assertArrayEquals(new int[]{5, 0, 0}, RaceScoring.migratedWinsByClass(RaceClass.B.getId(), 5));
		assertArrayEquals(new int[]{9, 3, 0}, RaceScoring.migratedWinsByClass(RaceClass.B.getId(), 12));
		assertArrayEquals(new int[]{9, 21, 0}, RaceScoring.migratedWinsByClass(RaceClass.B.getId(), 30));
		assertArrayEquals(new int[]{9, 14, 7}, RaceScoring.migratedWinsByClass(RaceClass.A.getId(), 30));
		assertArrayEquals(new int[]{9, 14, 27}, RaceScoring.migratedWinsByClass(RaceClass.A.getId(), 50));
		// Class S wins count toward no stage: C, B and A fill to their caps, the rest is dropped
		assertArrayEquals(new int[]{9, 14, 18}, RaceScoring.migratedWinsByClass(RaceClass.S.getId(), 60));
		assertArrayEquals(new int[]{9, 6, 0}, RaceScoring.migratedWinsByClass(RaceClass.S.getId(), 15));
		assertArrayEquals(new int[]{0, 0, 0}, RaceScoring.migratedWinsByClass(RaceClass.A.getId(), -3));
	}

	@Test
	void onlyABirdWithNoBornStatsRollsABloodline() {
		assertTrue(BreedGenes.blankLine(0, 0, 0, 0));
		assertFalse(BreedGenes.blankLine(0, 0, 0, 1));
		assertFalse(BreedGenes.blankLine(12, 3, 5, 7));
	}
}
