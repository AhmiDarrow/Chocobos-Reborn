package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import tk.darrow.chocobosreborn.breed.BreedGenes;

/** Birds saved by older versions are brought onto the current rules once (save format 2). */
class OldSaveConversionTest {
	@Test
	void oldMarksKeepTheirShareOfThePromotion() {
		// two of the old three becomes six of nine, not two of nine
		assertEquals(3, RaceScoring.migratedClassPoints(RaceClass.C.getId(), 1));
		assertEquals(6, RaceScoring.migratedClassPoints(RaceClass.B.getId(), 2));
		assertEquals(0, RaceScoring.migratedClassPoints(RaceClass.A.getId(), 0));
		assertEquals(3, RaceScoring.winsUntilPromote(RaceClass.B, RaceScoring.migratedClassPoints(RaceClass.B.getId(), 2)));
	}

	@Test
	void pointsAlreadyOnTheNewLadderStay() {
		assertEquals(4, RaceScoring.migratedClassPoints(RaceClass.C.getId(), 4));
		assertEquals(8, RaceScoring.migratedClassPoints(RaceClass.A.getId(), 8));
	}

	@Test
	void aClassSBirdShowsTheFullLadder() {
		assertEquals(RaceClass.POINTS_TO_PROMOTE, RaceScoring.migratedClassPoints(RaceClass.S.getId(), 3));
		assertEquals(0, RaceScoring.winsUntilPromote(RaceClass.S, RaceScoring.migratedClassPoints(RaceClass.S.getId(), 3)));
	}

	@Test
	void onlyABirdWithNoBornStatsRollsABloodline() {
		assertTrue(BreedGenes.blankLine(0, 0, 0, 0));
		assertFalse(BreedGenes.blankLine(0, 0, 0, 1));
		assertFalse(BreedGenes.blankLine(12, 3, 5, 7));
	}
}
