package tk.darrow.chocobosreborn.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceScoringTest {
	@Test
	void finishGraceStartsAtFinishAndDoesNotExpireDuringTheRace() {
		assertFalse(RaceScoring.finishGraceExpired(3000, -1));
		assertFalse(RaceScoring.finishGraceExpired(2400, 2400));
		assertFalse(RaceScoring.finishGraceExpired(2440, 2400));
		assertTrue(RaceScoring.finishGraceExpired(2441, 2400));
	}

	@Test
	void threeFirstPlacesPromoteAndNeverDrop() {
		RaceScoring.Promotion first = RaceScoring.afterFirstPlace(RaceClass.C, 0);
		assertEquals(RaceClass.C, first.raceClass());
		assertEquals(1, first.classWins());
		assertFalse(first.promoted());

		RaceScoring.Promotion second = RaceScoring.afterFirstPlace(RaceClass.C, 1);
		assertEquals(2, second.classWins());
		assertFalse(second.promoted());

		RaceScoring.Promotion third = RaceScoring.afterFirstPlace(RaceClass.C, 2);
		assertEquals(RaceClass.B, third.raceClass());
		assertEquals(0, third.classWins());
		assertTrue(third.promoted());
	}

	@Test
	void sClassNeverDrops() {
		RaceScoring.Promotion stay = RaceScoring.afterFirstPlace(RaceClass.S, 3);
		assertEquals(RaceClass.S, stay.raceClass());
		assertFalse(stay.promoted());
	}

	@Test
	void classLadderIsCBAThenS() {
		assertEquals(RaceClass.B, RaceClass.C.next());
		assertEquals(RaceClass.A, RaceClass.B.next());
		assertEquals(RaceClass.S, RaceClass.A.next());
		assertEquals(RaceClass.S, RaceClass.S.next());
		assertTrue(RaceClass.B.includesTeioh());
		assertFalse(RaceClass.C.includesTeioh());
	}

	@Test
	void lapWrapAndDisplay() {
		assertTrue(RaceScoring.wrappedPastStart(0.98D, 0.02D));
		assertFalse(RaceScoring.wrappedPastStart(0.10D, 0.20D));
		assertFalse(RaceScoring.wrappedPastStart(0.20D, 0.80D));
		assertEquals(1, RaceScoring.displayLap(0, 3));
		assertEquals(3, RaceScoring.displayLap(2, 3));
		assertEquals(3, RaceScoring.displayLap(3, 3));
		assertEquals(1, RaceScoring.displayLap(0, 0));
		assertEquals(1, RaceScoring.displayPlace(0));
		assertEquals(1, RaceScoring.displayPlace(1));
		assertEquals(6, RaceScoring.displayPlace(6));
		assertTrue(RaceScoring.finished(3, 3));
		assertFalse(RaceScoring.finished(2, 3));
	}

	@Test
	void blueWaterSpeedAppliesOffTheOvalNotDuringARace() {
		assertEquals(0.50D, RaceScoring.mountedCruise(0.27D, 0.50D, true, false, 2), 1.0E-9);
		assertEquals(0.27D, RaceScoring.mountedCruise(0.27D, 0.50D, true, true, 2), 1.0E-9);
		assertEquals(0.27D, RaceScoring.mountedCruise(0.27D, 0.50D, false, false, 2), 1.0E-9);
		assertTrue(RaceScoring.mountedCruise(0.27D, 0.50D, true, false, 4)
				> RaceScoring.mountedCruise(0.27D, 0.50D, true, false, 0));
	}

	@Test
	void wonderfulBirdsOutrunPoorOnes() {
		assertTrue(RaceScoring.gradeSpeedMul(4) > RaceScoring.gradeSpeedMul(0));
		assertEquals(1.00D, RaceScoring.gradeSpeedMul(2), 1.0E-9);
		assertTrue(RaceScoring.dashMul() > 1.5D);
		assertTrue(RaceScoring.emptyStaminaMul() < 0.7D);
	}

	@Test
	void teiohHasTwentyFivePercentMoreStamina() {
		int ordinary = RaceScoring.maxStamina(3, 1, false);
		int teioh = RaceScoring.maxStamina(3, 1, true);
		assertEquals(Math.round(ordinary * 1.25F), teioh);
		assertTrue(RaceScoring.maxStamina(4, 3, false) > ordinary);
	}

	@Test
	void finishedBirdsRankAheadInFinishOrder() {
		double first = RaceScoring.sortKey(true, 0, 3, 0.01D);
		double second = RaceScoring.sortKey(true, 1, 3, 0.99D);
		double stillRacing = RaceScoring.sortKey(false, -1, 2, 0.99D);
		assertTrue(first > second);
		assertTrue(second > stillRacing);
		assertEquals(1, RaceScoring.placeOf(0, 1));
		assertEquals(2, RaceScoring.placeOf(1, 2));
		assertEquals(1, RaceScoring.placeOf(-1, 0));
		assertEquals(3, RaceScoring.placeOf(-1, 3));
	}

	@Test
	void squareEntryNeedsAdultSaddleAndOwner() {
		assertTrue(RaceScoring.canEnterSquare(false, true, true));
		assertFalse(RaceScoring.canEnterSquare(true, true, true));
		assertFalse(RaceScoring.canEnterSquare(false, false, true));
		assertFalse(RaceScoring.canEnterSquare(false, true, false));
	}

	@Test
	void countdownRemountsInsteadOfForfeit() {
		assertFalse(RaceScoring.forfeitOnDismount(false));
		assertTrue(RaceScoring.forfeitOnDismount(true));
		assertFalse(RaceScoring.driftedFromStall(0.1D, 0.0D, 0.1D));
		assertTrue(RaceScoring.driftedFromStall(1.0D, 0.0D, 0.0D));
	}

	@Test
	void spaceAndWaterChangeSpeed() {
		assertEquals(1.28D, RaceScoring.terrainMultiplier(true, false, true, false), 1.0E-9);
		assertEquals(0.88D, RaceScoring.terrainMultiplier(true, false, false, false), 1.0E-9);
		assertEquals(1.26D, RaceScoring.terrainMultiplier(false, true, false, true), 1.0E-9);
		assertEquals(0.55D, RaceScoring.terrainMultiplier(false, true, false, false), 1.0E-9);
		assertTrue(RaceScoring.spaceBird(true, false, false, false));
		assertFalse(RaceScoring.spaceBird(false, false, false, false));
	}

	@Test
	void reversingAtTheStartDoesNotCountALap() {
		assertTrue(RaceScoring.wrappedPastStart(0.98D, 0.02D));
		assertFalse(RaceScoring.countsLap(false, 0.98D, 0.02D));
		assertTrue(RaceScoring.countsLap(true, 0.98D, 0.02D));
		assertTrue(RaceScoring.passedMidcourse(0.45D, 0.50D));
		assertFalse(RaceScoring.passedMidcourse(0.02D, 0.02D));
		assertFalse(RaceScoring.passedMidcourse(0.81D, 0.50D));
	}

	@Test
	void firstPlaceRequiresFinishingTheCourse() {
		assertTrue(RaceScoring.awardsFirstPlace(true, 1));
		assertFalse(RaceScoring.awardsFirstPlace(false, 1));
		assertFalse(RaceScoring.awardsFirstPlace(true, 2));
		assertFalse(RaceScoring.awardsRankedWin(true, false, 1));
	}

	@Test
	void funDuelsNeverAwardClassWins() {
		assertTrue(RaceScoring.awardsRankedWin(true, true, 1));
		assertFalse(RaceScoring.awardsRankedWin(false, true, 1));
		assertFalse(RaceScoring.awardsRankedWin(true, false, 1));
	}

	@Test
	void funJoinMatchesTheGateNotTheClassMap() {
		assertTrue(RaceScoring.sameCourseIndex(0, 0));
		assertTrue(RaceScoring.sameCourseIndex(1, 1));
		assertFalse(RaceScoring.sameCourseIndex(0, 1));
	}

	@Test
	void waterWalkersStillCountAsOnWater() {
		assertTrue(RaceScoring.onWaterStretch(false, true));
		assertTrue(RaceScoring.onWaterStretch(true, false));
		assertFalse(RaceScoring.onWaterStretch(false, false));
		assertEquals(1.26D, RaceScoring.terrainMultiplier(false,
				RaceScoring.onWaterStretch(false, false, true), false, true));
	}

	@Test
	void goldCannotFlyTheCourse() {
		assertFalse(RaceScoring.mayFlyDuringRace(true, true));
		assertTrue(RaceScoring.mayFlyDuringRace(false, true));
		assertFalse(RaceScoring.mayFlyDuringRace(false, false));
	}

	@Test
	void oneLiveSessionOccupiesTheOval() {
		assertFalse(RaceScoring.courseOccupied(0));
		assertTrue(RaceScoring.courseOccupied(1));
	}

	@Test
	void sneakDismountCancelsDuringRaceButAirTeleportDoesNot() {
		assertTrue(RaceScoring.cancelPassengerDismount(true, false, false, false, true));
		assertTrue(RaceScoring.cancelPassengerDismount(true, true, false, true, true));
		assertTrue(RaceScoring.cancelPassengerDismount(false, true, false, false, true));
		assertFalse(RaceScoring.cancelPassengerDismount(false, false, false, false, true));
		assertFalse(RaceScoring.cancelPassengerDismount(false, true, true, false, true));
		assertFalse(RaceScoring.cancelPassengerDismount(false, true, false, true, true));
		assertFalse(RaceScoring.cancelPassengerDismount(true, false, false, false, false));
	}

	@Test
	void gysahlClickOnABirdDoesNotStartEating() {
		assertTrue(RaceScoring.clientConsumesGysahlOnBird(true));
		assertFalse(RaceScoring.clientConsumesGysahlOnBird(false));
	}

	@Test
	void saddleBagsCloseWhenTheRaceLocksTheSaddle() {
		assertTrue(RaceScoring.saddleBagStillValid(true, false, true));
		assertFalse(RaceScoring.saddleBagStillValid(true, true, true));
		assertFalse(RaceScoring.saddleBagStillValid(false, false, true));
		assertFalse(RaceScoring.saddleBagStillValid(true, false, false));
	}

	@Test
	void busyOrAlreadyRacingGatesDoNotPlaceTheHeldBlock() {
		assertTrue(RaceScoring.gateSkipsDefaultItemUse(true, false));
		assertTrue(RaceScoring.gateSkipsDefaultItemUse(false, true));
		assertFalse(RaceScoring.gateSkipsDefaultItemUse(false, false));
	}

	@Test
	void stayWhistleDoesNotFreezeARiddenBird() {
		assertTrue(RaceScoring.stayParksWithNoAi(false, false, true));
		assertFalse(RaceScoring.stayParksWithNoAi(false, true, true));
		assertFalse(RaceScoring.stayParksWithNoAi(true, false, true));
		assertFalse(RaceScoring.stayParksWithNoAi(false, false, false));
	}

	@Test
	void squareVoidRescueSkipsBirdsStillOnTheCourse() {
		assertTrue(RaceScoring.squareFallRescue(10.0D, false));
		assertFalse(RaceScoring.squareFallRescue(10.0D, true));
		assertFalse(RaceScoring.squareFallRescue(65.0D, false));
		assertFalse(RaceScoring.squareFallRescue(50.0D, false));
	}

	@Test
	void opponentLeaveDoesNotReplayVictoryAfterAFinish() {
		assertTrue(RaceScoring.playVictoryOnOpponentLeave(false));
		assertFalse(RaceScoring.playVictoryOnOpponentLeave(true));
	}

	@Test
	void neoforgeBagMenuIncludesTheSeparateSaddleSlot() {
		assertEquals(46, RaceScoring.saddleBagMenuSlots(45, true));
		assertEquals(46, RaceScoring.saddleBagMenuSlots(46, false));
		assertEquals(0, RaceScoring.saddleBagMenuSlots(-3, false));
	}

	@Test
	void eachMapHasALoopCueAndSReusesAClassAnthems() {
		assertEquals("chocobo_dash", RaceScoring.raceLoopKey("c_meadow"));
		assertEquals("chocobo_race_gallop", RaceScoring.raceLoopKey("c_shore"));
		assertEquals("gallop_of_adventure", RaceScoring.raceLoopKey("b_canyon"));
		assertEquals("rune_dash", RaceScoring.raceLoopKey("b_ford"));
		assertEquals("gallop_of_heroes", RaceScoring.raceLoopKey("a_crystal"));
		assertEquals("speed_of_the_dragon", RaceScoring.raceLoopKey("s_skyway"));
		assertEquals("gallop_of_heroes", RaceScoring.raceLoopKey("s_void"));
		assertEquals("speed_of_the_dragon", RaceScoring.raceLoopKey("s_keep"));
		assertEquals("chocobo_dash", RaceScoring.raceLoopKey(null));
		assertTrue(RaceScoring.raceLoopShouldPlay(true, true, false));
		assertFalse(RaceScoring.raceLoopShouldPlay(true, true, true));
		assertFalse(RaceScoring.raceLoopShouldPlay(false, true, false));
		assertFalse(RaceScoring.raceLoopShouldPlay(true, false, false));
	}

	@Test
	void classBPromotesToAThenS() {
		RaceScoring.Promotion toA = RaceScoring.afterFirstPlace(RaceClass.B, 2);
		assertEquals(RaceClass.A, toA.raceClass());
		assertTrue(toA.promoted());
		RaceScoring.Promotion toS = RaceScoring.afterFirstPlace(RaceClass.A, 2);
		assertEquals(RaceClass.S, toS.raceClass());
		assertTrue(toS.promoted());
	}
}
