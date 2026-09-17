package tk.darrow.chocobosreborn.breed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ff7LineTest {
	@Test
	void greatYellowsWithCarobMutateToGreenOrBlue() {
		assertEquals(Ff7Line.Result.GREEN, Ff7Line.resolve(
				Ff7Line.Color.YELLOW, Ff7Line.Color.YELLOW,
				Ff7Line.GREAT, Ff7Line.GREAT, Ff7Line.CAROB, 4, true, true));
		assertEquals(Ff7Line.Result.BLUE, Ff7Line.resolve(
				Ff7Line.Color.YELLOW, Ff7Line.Color.YELLOW,
				Ff7Line.WONDERFUL, Ff7Line.GREAT, Ff7Line.CAROB, 4, true, false));
	}

	@Test
	void missedYellowMutationFallsThrough() {
		assertEquals(Ff7Line.Result.NONE, Ff7Line.resolve(
				Ff7Line.Color.YELLOW, Ff7Line.Color.YELLOW,
				Ff7Line.GREAT, Ff7Line.GREAT, Ff7Line.CAROB, 12, false, true));
	}

	@Test
	void poorYellowsNeverMutateOnCarob() {
		assertEquals(Ff7Line.Result.NONE, Ff7Line.resolve(
				Ff7Line.Color.YELLOW, Ff7Line.Color.YELLOW,
				0, 0, Ff7Line.CAROB, 12, true, true));
	}

	@Test
	void goodYellowsWithCarobCanMakeGreenOrBlue() {
		assertEquals(Ff7Line.Result.GREEN, Ff7Line.resolve(
				Ff7Line.Color.YELLOW, Ff7Line.Color.YELLOW,
				Ff7Line.GOOD, Ff7Line.GOOD, Ff7Line.CAROB, 8, true, true));
		assertEquals(Ff7Line.Result.BLUE, Ff7Line.resolve(
				Ff7Line.Color.YELLOW, Ff7Line.Color.YELLOW,
				Ff7Line.GOOD, Ff7Line.GREAT, Ff7Line.CAROB, 8, true, false));
	}

	@Test
	void greenBlueCarobMakesBlackOrWhite() {
		assertEquals(Ff7Line.Result.BLACK, Ff7Line.resolve(
				Ff7Line.Color.GREEN, Ff7Line.Color.BLUE,
				Ff7Line.GREAT, Ff7Line.GREAT, Ff7Line.CAROB, 9, true, true));
		assertEquals(Ff7Line.Result.WHITE, Ff7Line.resolve(
				Ff7Line.Color.BLUE, Ff7Line.Color.GREEN,
				Ff7Line.GREAT, Ff7Line.GREAT, Ff7Line.CAROB, 9, false, true));
	}

	@Test
	void goldNeedsZeioBlackAndWonderfulYellow() {
		assertEquals(Ff7Line.Result.GOLD, Ff7Line.resolve(
				Ff7Line.Color.BLACK, Ff7Line.Color.YELLOW,
				Ff7Line.GREAT, Ff7Line.WONDERFUL, Ff7Line.ZEIO, 12, true, true));
		assertEquals(Ff7Line.Result.INHERIT, Ff7Line.resolve(
				Ff7Line.Color.BLACK, Ff7Line.Color.YELLOW,
				Ff7Line.GREAT, Ff7Line.WONDERFUL, Ff7Line.ZEIO, 12, false, true));
		assertEquals(Ff7Line.Result.NONE, Ff7Line.resolve(
				Ff7Line.Color.BLACK, Ff7Line.Color.YELLOW,
				Ff7Line.GREAT, Ff7Line.WONDERFUL, Ff7Line.CAROB, 12, true, true));
		assertFalse(Ff7Line.goldPair(Ff7Line.Color.BLACK, Ff7Line.Color.YELLOW, Ff7Line.GREAT, Ff7Line.GREAT));
		assertTrue(Ff7Line.goldPair(Ff7Line.Color.YELLOW, Ff7Line.Color.BLACK, Ff7Line.WONDERFUL, Ff7Line.GREAT));
	}

	@Test
	void zeioOnUnrelatedPairInherits() {
		assertEquals(Ff7Line.Result.INHERIT, Ff7Line.resolve(
				Ff7Line.Color.YELLOW, Ff7Line.Color.YELLOW,
				Ff7Line.GREAT, Ff7Line.GREAT, Ff7Line.ZEIO, 12, true, true));
	}

	@Test
	void goldNeverSticksWithoutZeio() {
		assertEquals(Ff7Line.Color.YELLOW, Ff7Line.stripGoldWithoutZeio(Ff7Line.Color.GOLD, Ff7Line.CAROB));
		assertEquals(Ff7Line.Color.GOLD, Ff7Line.stripGoldWithoutZeio(Ff7Line.Color.GOLD, Ff7Line.ZEIO));
		assertEquals(Ff7Line.Color.GREEN, Ff7Line.stripGoldWithoutZeio(Ff7Line.Color.GREEN, Ff7Line.CAROB));
		assertFalse(Ff7Line.allowsGold(Ff7Line.CAROB));
		assertTrue(Ff7Line.allowsGold(Ff7Line.ZEIO));
	}

	@Test
	void noNutLeavesJsonLine() {
		assertEquals(Ff7Line.Result.NONE, Ff7Line.resolve(
				Ff7Line.Color.GREEN, Ff7Line.Color.BLUE,
				Ff7Line.GREAT, Ff7Line.GREAT, 0, 12, true, true));
	}
}
