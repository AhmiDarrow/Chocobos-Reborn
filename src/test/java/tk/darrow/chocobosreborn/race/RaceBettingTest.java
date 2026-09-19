package tk.darrow.chocobosreborn.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceBettingTest {
	@Test
	void stakeClampsToSixteenAndRejectsEmptyHands() {
		assertEquals(0, RaceScoring.clampStake(0));
		assertEquals(0, RaceScoring.clampStake(-4));
		assertEquals(1, RaceScoring.clampStake(1));
		assertEquals(16, RaceScoring.clampStake(16));
		assertEquals(16, RaceScoring.clampStake(64));
		assertEquals(0, RaceScoring.clampDuelStake(-4));
		assertEquals(32, RaceScoring.clampDuelStake(32));
		assertEquals(32, RaceScoring.clampDuelStake(64));
	}

	@Test
	void booksOnlyWhileIdleOrInCountdownAndOncePerHeat() {
		assertTrue(RaceScoring.mayPlaceBet(true, false, 4));
		assertFalse(RaceScoring.mayPlaceBet(false, false, 4));
		assertFalse(RaceScoring.mayPlaceBet(true, true, 4));
		assertFalse(RaceScoring.mayPlaceBet(true, false, 0));
		assertTrue(RaceScoring.booksOpen(true, false));
		assertFalse(RaceScoring.booksOpen(true, true));
		assertFalse(RaceScoring.booksOpen(false, false));
	}

	@Test
	void selfOddsClimbWithClassAndNamedBirdsHaveFixedPrices() {
		assertEquals(2, RaceScoring.selfOdds(RaceClass.C.getId()));
		assertEquals(3, RaceScoring.selfOdds(RaceClass.B.getId()));
		assertEquals(4, RaceScoring.selfOdds(RaceClass.A.getId()));
		assertEquals(5, RaceScoring.selfOdds(RaceClass.S.getId()));
		assertEquals(3, RaceScoring.odds(RaceScoring.BetPick.JOE, 0, 3));
		assertEquals(2, RaceScoring.odds(RaceScoring.BetPick.TEIOH, 3, 3));
		assertEquals(6, RaceScoring.odds(RaceScoring.BetPick.FIELD, 0, 1));
		assertEquals(2, RaceScoring.odds(RaceScoring.BetPick.OPPONENT, 1, 3));
		// FIELD is priced by how many AI birds it covers: five of six is close to evens
		assertEquals(1, RaceScoring.odds(RaceScoring.BetPick.FIELD, 0, RaceScoring.expectedFieldBirds(1, false)));
		assertEquals(2, RaceScoring.odds(RaceScoring.BetPick.FIELD, 0, 4));
		assertEquals(2, RaceScoring.odds(RaceScoring.BetPick.FIELD, 3, RaceScoring.expectedFieldBirds(1, true)));
	}

	@Test
	void payoutIsStakeTimesOddsOnlyWhenThePickWins() {
		assertEquals(12, RaceScoring.payout(4, 3, true));
		assertEquals(0, RaceScoring.payout(4, 3, false));
		assertEquals(0, RaceScoring.payout(0, 5, true));
		assertEquals(0, RaceScoring.payout(4, 0, true));
	}

	@Test
	void rankedPicksPayFirstPlaceOnly() {
		assertTrue(RaceScoring.pickWon(RaceScoring.BetPick.SELF, true, true, false, false, false, false));
		assertTrue(RaceScoring.pickWon(RaceScoring.BetPick.JOE, true, false, true, false, false, false));
		assertTrue(RaceScoring.pickWon(RaceScoring.BetPick.TEIOH, true, false, false, true, false, false));
		assertTrue(RaceScoring.pickWon(RaceScoring.BetPick.FIELD, true, false, false, false, true, false));
		assertFalse(RaceScoring.pickWon(RaceScoring.BetPick.JOE, true, true, false, false, false, false));
		assertFalse(RaceScoring.pickWon(RaceScoring.BetPick.FIELD, false, false, false, false, true, false));
		assertTrue(RaceScoring.pickWon(RaceScoring.BetPick.OPPONENT, false, false, false, false, false, true));
		assertFalse(RaceScoring.pickWon(RaceScoring.BetPick.OPPONENT, true, false, false, false, false, true));
	}

	@Test
	void sneakCyclesTheLegalBoard() {
		assertEquals(RaceScoring.BetPick.FIELD, RaceScoring.nextPick(RaceScoring.BetPick.SELF, true, false));
		assertEquals(RaceScoring.BetPick.SELF, RaceScoring.nextPick(RaceScoring.BetPick.FIELD, true, false));
		assertEquals(RaceScoring.BetPick.JOE, RaceScoring.nextPick(RaceScoring.BetPick.SELF, true, true));
		assertEquals(RaceScoring.BetPick.TEIOH, RaceScoring.nextPick(RaceScoring.BetPick.JOE, true, true));
		assertEquals(RaceScoring.BetPick.FIELD, RaceScoring.nextPick(RaceScoring.BetPick.TEIOH, true, true));
		assertEquals(RaceScoring.BetPick.OPPONENT, RaceScoring.nextPick(RaceScoring.BetPick.SELF, false, true));
		assertEquals(RaceScoring.BetPick.SELF, RaceScoring.nextPick(RaceScoring.BetPick.OPPONENT, false, true));
	}

	@Test
	void aDnfAfterGoKeepsTheStakeAndAScratchRefundsSpectators() {
		assertFalse(RaceScoring.refundBookieOnForfeit(true));
		assertTrue(RaceScoring.refundBookieOnForfeit(false));
		assertTrue(RaceScoring.scratchRefundsLeftoverBets(false));
		assertFalse(RaceScoring.scratchRefundsLeftoverBets(true));
		assertTrue(RaceScoring.spectatorMayBetOnFun(true, false));
		assertTrue(RaceScoring.spectatorMayBetOnFun(false, true));
		assertFalse(RaceScoring.spectatorMayBetOnFun(false, false));
	}

	@Test
	void pendingPicksRemapWhenTheHeatDoesNotHaveThatBird() {
		assertEquals(RaceScoring.BetPick.OPPONENT, RaceScoring.legalize(RaceScoring.BetPick.JOE, false, true));
		assertEquals(RaceScoring.BetPick.SELF, RaceScoring.legalize(RaceScoring.BetPick.SELF, false, true));
		assertEquals(RaceScoring.BetPick.FIELD, RaceScoring.legalize(RaceScoring.BetPick.OPPONENT, true, true));
		assertEquals(RaceScoring.BetPick.FIELD, RaceScoring.legalize(RaceScoring.BetPick.JOE, true, false));
		assertEquals(RaceScoring.BetPick.JOE, RaceScoring.legalize(RaceScoring.BetPick.JOE, true, true));
	}

	@Test
	void spectatorSelfBecomesTheFieldAndCycleSkipsMissingNamedBirds() {
		assertEquals(RaceScoring.BetPick.FIELD, RaceScoring.legalizeBettor(
				RaceScoring.BetPick.SELF, true, false, true, true, false));
		assertEquals(RaceScoring.BetPick.SELF, RaceScoring.legalizeBettor(
				RaceScoring.BetPick.SELF, true, true, true, true, false));
		assertEquals(RaceScoring.BetPick.FIELD, RaceScoring.legalizeBettor(
				RaceScoring.BetPick.JOE, true, false, false, true, false));
		// a racer may only back themselves
		assertEquals(RaceScoring.BetPick.SELF, RaceScoring.legalizeBettor(
				RaceScoring.BetPick.FIELD, true, true, true, true, false));
		assertEquals(RaceScoring.BetPick.SELF, RaceScoring.legalizeBettor(
				RaceScoring.BetPick.OPPONENT, false, true, false, false, true));
		assertEquals(RaceScoring.BetPick.SELF, RaceScoring.legalizeBettor(
				RaceScoring.BetPick.OPPONENT, false, true, false, false, false));
		assertEquals(RaceScoring.BetPick.JOE, RaceScoring.nextLivePick(
				RaceScoring.BetPick.SELF, true, false, true, true, false));
		assertEquals(RaceScoring.BetPick.FIELD, RaceScoring.nextLivePick(
				RaceScoring.BetPick.JOE, true, false, true, false, false));
		assertEquals(RaceScoring.BetPick.SELF, RaceScoring.nextLivePick(
				RaceScoring.BetPick.SELF, true, true, true, true, false));
		assertEquals(RaceScoring.BetPick.SELF, RaceScoring.nextLivePick(
				RaceScoring.BetPick.SELF, false, true, false, false, false));
	}

	@Test
	void tackAndTreatsSellTheStaplesForGp() {
		assertTrue(RaceShops.catalog().stream().anyMatch(line ->
				line.role().equals("tack") && line.resultId().equals("chocobosreborn:chocobo_saddle") && line.cost() == 12));
		assertTrue(RaceShops.catalog().stream().anyMatch(line ->
				line.role().equals("treats") && line.resultId().equals("chocobosreborn:zeio_nut") && line.cost() == 128));
		// nothing dearer than two GP stacks: the merchant screen has two cost slots of 64
		assertTrue(RaceShops.catalog().stream().allMatch(line -> line.cost() <= 128));
		assertTrue(RaceShops.catalog().stream().anyMatch(line ->
				line.role().equals("greens") && line.resultId().equals("chocobosreborn:gysahl_green") && line.resultCount() == 8));
	}

	@Test
	void payoutsOverAStackAreSplit() {
		assertEquals(0, RaceCurrency.stacks(0, 64).length);
		assertArrayEquals(new int[]{16}, RaceCurrency.stacks(16, 64));
		assertArrayEquals(new int[]{64, 32}, RaceCurrency.stacks(96, 64));
		assertArrayEquals(new int[]{16, 16}, RaceCurrency.stacks(32, 16));
	}

	@Test
	void westStallKeepersStandInsideNotOnTheEastFence() {
		var greens = TownPosts.keeperPosts().stream()
				.filter(post -> post.role() == TownRole.GREENS)
				.findFirst().orElseThrow();
		var fair = TownPosts.keeperPosts().stream()
				.filter(post -> post.role() == TownRole.FAIR)
				.findFirst().orElseThrow();
		assertEquals(-17.5D, greens.x(), 1.0E-9);
		assertEquals(-17.5D, fair.x(), 1.0E-9);
		assertTrue(Math.abs(greens.x() + 16.0D) > 0.4D);
		assertTrue(Math.abs(fair.x() + 16.0D) > 0.4D);
	}
}
