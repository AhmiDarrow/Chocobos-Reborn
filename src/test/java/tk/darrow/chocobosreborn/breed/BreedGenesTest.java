package tk.darrow.chocobosreborn.breed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BreedGenesTest {
	@Test
	void aPerfectLineTakesManyGenerations() {
		assertEquals(10, BreedGenes.blood(0, 100), "a fed bird with no blood passes a tenth");
		assertEquals(100, BreedGenes.passed(80, 40));
		int blood = 10;
		int[] firstEight = {19, 27, 34, 40, 46, 51, 55, 59};
		for (int expected : firstEight) {
			blood = climb(blood);
			assertEquals(expected, blood);
		}
		assertTrue(blood < 70, "eight perfect generations still under 70");
		for (int i = firstEight.length; i < 20; i++) {
			blood = climb(blood);
		}
		assertEquals(85, blood, "twenty perfect generations reach the mid-80s, not a legend");
	}

	@Test
	void sparkLeansTowardTheStrongerParent() {
		assertEquals(60, BreedGenes.favorLean(true, 0));
		assertEquals(80, BreedGenes.favorLean(true, 100));
		assertEquals(40, BreedGenes.favorLean(false, 0));
		assertEquals(60, BreedGenes.favorLean(false, 100));
		int plain = BreedGenes.childGene(20, 80, 0, BreedGenes.favorLean(false, 0), 0, 0, 0);
		int sparked = BreedGenes.childGene(20, 80, 0, BreedGenes.favorLean(true, 100), 0, 0, BreedGenes.SPARK);
		assertTrue(sparked > plain);
		assertTrue(sparked < 80, "a spark on a middling pair stays under the stronger parent");
		assertFalse(BreedGenes.stepsUp(sparked, 20, 80));
		assertFalse(BreedGenes.stepsUp(plain, 20, 80));
	}

	@Test
	void legendNeedsASparkOnAnElitePair() {
		assertEquals(100, BreedGenes.childGene(90, 90, 0, 50, 8, 0, BreedGenes.SPARK));
		assertTrue(BreedGenes.stepsUp(100, 90, 90));
		assertEquals(96, BreedGenes.childGene(100, 100, 8, 50, 8, 0, 0), "no spark caps at 96");
		assertEquals(93, BreedGenes.childGene(80, 80, 0, 50, 8, 0, BreedGenes.SPARK));
		assertEquals(96, BreedGenes.childGene(80, 80, 0, 50, 12, 0, BreedGenes.SPARK),
				"a lucky roll on an 80 line cannot mint a legend");
		assertEquals(98, BreedGenes.childGene(85, 85, 0, 50, 8, 0, BreedGenes.SPARK));
		assertEquals(38, BreedGenes.childGene(50, 50, 0, 50, -20, 0, 0), "wobble clamps at -12");
		assertEquals(62, BreedGenes.childGene(50, 50, 0, 50, 20, 0, 0), "wobble clamps at 12");
		assertTrue(BreedGenes.childGene(40, 40, 0, 50, -12, 0, 0) < 40);
	}

	@Test
	void nutAndGradeAreASmallStart() {
		assertEquals(1, BreedGenes.nutGift(1, true));
		assertEquals(0, BreedGenes.nutGift(1, false));
		assertEquals(8, BreedGenes.nutGift(8, true));
		assertEquals(4, BreedGenes.nutGift(8, false));
		assertEquals(3, BreedGenes.childGene(0, 0, 1, 50, -12, BreedGenes.gradeFloor(1), 0));
		assertEquals(0, BreedGenes.gradeFloor(0));
		assertEquals(2, BreedGenes.gradeFloor(1));
		assertEquals(8, BreedGenes.gradeFloor(4));
		assertEquals(8, BreedGenes.wildGene(8, 0));
		assertEquals(8, BreedGenes.wildGene(0, 8));
		assertEquals(7, BreedGenes.wildGene(0, 16));
	}

	/** Midpoint chick of two equal bloodlines, then fed until the race stat caps. */
	private static int climb(int blood) {
		int chick = BreedGenes.childGene(blood, blood, 0, 50, 0, 0, 0);
		return BreedGenes.blood(chick, ChocoboGreen.MAX_POINTS - chick);
	}
}
