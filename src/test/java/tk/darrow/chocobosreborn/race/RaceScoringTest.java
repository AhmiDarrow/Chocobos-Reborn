package tk.darrow.chocobosreborn.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceScoringTest {
	@Test
	void persistHeatStartKeepsAFutureMarkAndBumpsADueOne() {
		assertEquals(12000L, RaceScoring.persistHeatStart(12000L, 8000L, 6000, 200));
		long now = 15000L;
		long next = RaceScoring.persistHeatStart(12000L, now, 6000, 200);
		assertTrue(next > now);
		assertEquals(RaceScoring.nextHeatMark(now, 6000, 200), next);
		assertEquals(RaceScoring.nextHeatMark(12000L, 6000, 200),
				RaceScoring.persistHeatStart(12000L, 12000L, 6000, 200));
	}

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
		assertEquals(0, RaceScoring.winsUntilPromote(RaceClass.S, stay.classWins()));
		assertEquals(3, RaceScoring.winsUntilPromote(RaceClass.C, 0));
		assertEquals(1, RaceScoring.winsUntilPromote(RaceClass.C, 2));
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
	void aFastDashSamplesTheBoostStripItWouldSkip() {
		assertEquals(1, RaceScoring.boostPadSamples(0.0D, 0.0D));
		assertEquals(1, RaceScoring.boostPadSamples(0.1D, 0.0D));
		assertTrue(RaceScoring.boostPadSamples(1.3D, 0.0D) >= 3);
		assertTrue(RaceScoring.boostPadSamples(1.3D, 0.0D) <= 8);
	}

	@Test
	void wonderfulBirdsOutrunPoorOnes() {
		assertTrue(RaceScoring.gradeSpeedMul(4) > RaceScoring.gradeSpeedMul(0));
		assertEquals(1.00D, RaceScoring.gradeSpeedMul(2), 1.0E-9);
		assertTrue(RaceScoring.dashMul() > 1.5D);
		assertTrue(RaceScoring.emptyStaminaMul() < 0.7D);
		assertEquals(1.0D, RaceScoring.speedTrainingMul(0), 1.0E-9);
		assertEquals(1.35D, RaceScoring.speedTrainingMul(100), 1.0E-9);
		assertEquals(1.0D, RaceScoring.speedTrainingMul(-8), 1.0E-9);
	}

	@Test
	void intelligenceSkipsTheSameDashDrainForRiderAndAi() {
		assertFalse(RaceScoring.intelSkipsDashDrain(0, 4, 0));
		assertFalse(RaceScoring.intelSkipsDashDrain(100, 5, 0));
		assertTrue(RaceScoring.intelSkipsDashDrain(100, 4, 0));
		assertTrue(RaceScoring.intelSkipsDashDrain(50, 8, 49));
		assertFalse(RaceScoring.intelSkipsDashDrain(50, 8, 50));
		assertFalse(RaceScoring.dashEnds(1), "an intel skip that keeps the last point must not kill the dash");
		assertTrue(RaceScoring.dashEnds(0));
	}

	@Test
	void cooperationIsHandlingForRiderAndField() {
		assertEquals(0.35F, RaceScoring.turnCatchup(0), 1.0E-5F);
		assertEquals(1.0F, RaceScoring.turnCatchup(100), 1.0E-5F);
		assertTrue(RaceScoring.turnCatchup(80) > RaceScoring.turnCatchup(20));
		assertEquals(18.0F, RaceScoring.turnMaxDegrees(0), 1.0E-5F);
		assertEquals(60.0F, RaceScoring.turnMaxDegrees(100), 1.0E-5F);
		assertEquals(0.25F, RaceScoring.strafeMul(0), 1.0E-5F);
		assertEquals(0.50F, RaceScoring.strafeMul(100), 1.0E-5F);
		assertTrue(RaceScoring.handlingWobbleMul(0) > RaceScoring.handlingWobbleMul(100));
		assertTrue(RaceScoring.handlingLineMul(100) > RaceScoring.handlingLineMul(0));
		assertEquals(1.0D, RaceScoring.handlingLineMul(100), 1.0E-9);
	}

	@Test
	void fieldNpcsGetClassTrainingAndRivalsMaxOut() {
		assertEquals(22, RaceScoring.fieldTraining(0, false));
		assertEquals(48, RaceScoring.fieldTraining(1, false));
		assertEquals(72, RaceScoring.fieldTraining(2, false));
		assertEquals(92, RaceScoring.fieldTraining(3, false));
		assertEquals(100, RaceScoring.fieldTraining(0, true));
		assertEquals(100, RaceScoring.fieldTraining(3, true));
		assertTrue(RaceScoring.speedTrainingMul(RaceScoring.fieldTraining(3, false))
				> RaceScoring.speedTrainingMul(RaceScoring.fieldTraining(0, false)));
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
		assertTrue(RaceScoring.mayEnterCourse(0, 0));
		assertTrue(RaceScoring.mayEnterCourse(2, 0));
		assertFalse(RaceScoring.mayEnterCourse(0, 2));
	}

	@Test
	void countdownRemountsInsteadOfForfeit() {
		assertFalse(RaceScoring.forfeitOnDismount(false));
		assertTrue(RaceScoring.forfeitOnDismount(true));
		assertTrue(RaceScoring.stillOnCourse(false, -1));
		assertFalse(RaceScoring.stillOnCourse(true, -1));
		assertFalse(RaceScoring.stillOnCourse(false, 0));
		assertFalse(RaceScoring.stillOnCourse(true, 0));
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
		assertEquals(0, RaceScoring.funGateCourse(false));
		assertEquals(3, RaceScoring.funGateCourse(true));
	}

	@Test
	void startArrowMaskIsAThreeWideShaftAndAFortyFiveDegreeHead() {
		int half = RaceScoring.startArrowHalf();
		StringBuilder got = new StringBuilder();
		for (int along = 0; along < RaceScoring.startArrowLength(); along++) {
			for (int across = -half; across <= half; across++) {
				if (RaceScoring.startArrowTip(along, across)) {
					got.append('G');
				} else if (RaceScoring.startArrowCell(along, across)) {
					got.append('#');
				} else {
					got.append(' ');
				}
			}
			if (along + 1 < RaceScoring.startArrowLength()) {
				got.append('\n');
			}
		}
		assertEquals(String.join("\n", RaceScoring.START_ARROW_MASK), got.toString());
		int shaft = 0, barbs = 0, tipRow = 0;
		for (int across = -half; across <= half; across++) {
			if (RaceScoring.startArrowCell(0, across)) {
				shaft++;
			}
			if (RaceScoring.startArrowCell(6, across)) {
				barbs++;
			}
			if (RaceScoring.startArrowCell(RaceScoring.startArrowLength() - 1, across)) {
				tipRow++;
			}
		}
		assertEquals(3, shaft);
		assertEquals(11, barbs);
		assertEquals(1, tipRow);
		assertTrue(RaceScoring.startArrowTip(RaceScoring.startArrowLength() - 1, 0));
		assertFalse(RaceScoring.startArrowTip(RaceScoring.startArrowLength() - 2, 0));
		assertArrayEquals(new int[]{1, 0}, RaceScoring.arrowForward(0.9D, 0.1D));
		assertArrayEquals(new int[]{0, 1}, RaceScoring.arrowRight(1, 0));
	}

	@Test
	void holdSeatsJockeysBeforeTheScrubSoTheyAreNotDiscarded() {
		assertFalse(RaceScoring.seatJockeysOnHoldTick(1));
		assertTrue(RaceScoring.seatJockeysOnHoldTick(2));
		assertFalse(RaceScoring.seatJockeysOnHoldTick(40));
		assertFalse(RaceScoring.scrubCourseOnHoldTick(1));
		assertFalse(RaceScoring.scrubCourseOnHoldTick(2));
		assertTrue(RaceScoring.scrubCourseOnHoldTick(40));
	}

	@Test
	void aPlacedRiderKeepsTheirPlaceIfLaterMarkedDnf() {
		assertEquals(1, RaceScoring.resultPlace(0, true, 1, 6, 6));
		assertEquals(2, RaceScoring.resultPlace(1, false, 2, 6, 2));
		assertEquals(6, RaceScoring.resultPlace(-1, true, 1, 6, 3));
		assertEquals(3, RaceScoring.resultPlace(-1, false, 1, 6, 3));
	}

	@Test
	void duelOpponentPaysTheOtherHumanNotAPaceBird() {
		assertTrue(RaceScoring.pickWon(RaceScoring.BetPick.OPPONENT, false, false, false, false, true, true));
		assertFalse(RaceScoring.pickWon(RaceScoring.BetPick.OPPONENT, false, true, false, false, true, false));
		assertFalse(RaceScoring.pickWon(RaceScoring.BetPick.OPPONENT, true, false, false, false, true, true));
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
	void offCourseDismountLockIsOnlyForFliersAndWaterWalkers() {
		assertTrue(RaceScoring.lockOffCourseDismount(true, false, false, false));
		assertFalse(RaceScoring.lockOffCourseDismount(true, true, false, false));
		assertTrue(RaceScoring.lockOffCourseDismount(false, false, true, true));
		assertFalse(RaceScoring.lockOffCourseDismount(false, false, false, true));
		assertFalse(RaceScoring.lockOffCourseDismount(false, true, false, false));
	}

	@Test
	void gysahlClickOnABirdDoesNotStartEating() {
		assertTrue(RaceScoring.clientConsumesGysahlOnBird(true));
		assertFalse(RaceScoring.clientConsumesGysahlOnBird(false));
	}

	@Test
	void squareBirdsAndLiveRacersAreProtected() {
		assertTrue(RaceScoring.squareNpcProtected(true, false, false));
		assertTrue(RaceScoring.squareNpcProtected(false, true, false));
		assertTrue(RaceScoring.squareNpcProtected(false, false, true));
		assertFalse(RaceScoring.squareNpcProtected(false, false, false));
		assertTrue(RaceScoring.squareNpcProtected(false, false, false, true));
		assertFalse(RaceScoring.squareNpcProtected(false, false, false, false));
	}

	@Test
	void aFullHeatIsSkippedUnlessThisRiderIsAlreadyIn() {
		assertTrue(RaceScoring.joinSkipsFullHeat(3, 6, false));
		assertFalse(RaceScoring.joinSkipsFullHeat(6, 6, false));
		assertTrue(RaceScoring.joinSkipsFullHeat(6, 6, true));
	}

	@Test
	void spectatorBetsAttachToExactlyOneHoldHeat() {
		assertTrue(RaceScoring.attachSpectatorBet(true, 0));
		assertTrue(RaceScoring.attachSpectatorBet(false, 1));
		assertFalse(RaceScoring.attachSpectatorBet(false, 0));
		assertFalse(RaceScoring.attachSpectatorBet(false, 2));
		assertFalse(RaceScoring.attachSpectatorBet(false, true, 1));
		assertTrue(RaceScoring.attachSpectatorBet(true, true, 1));
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
	void whiskerwindProtectsTheRiderAsWellAsTheBird() {
		assertTrue(RaceScoring.squareRiderProtected(true, false));
		assertFalse(RaceScoring.squareRiderProtected(false, false));   // only in the Square
		assertFalse(RaceScoring.squareRiderProtected(true, true));     // /kill still works
		assertTrue(RaceScoring.squareVisitorFallRescue(10.0D, false));
		assertFalse(RaceScoring.squareVisitorFallRescue(65.0D, false));
		// a rider in the saddle is the session's to rescue, bird and all
		assertFalse(RaceScoring.squareVisitorFallRescue(10.0D, true));
	}

	@Test
	void squareVoidRescueCatchesEveryFall() {
		assertTrue(RaceScoring.squareFallRescue(10.0D));
		// a drop through a gap in the road is still a drop: the old on-course exception
		// left the rider falling out of the world
		assertTrue(RaceScoring.squareFallRescue(-20.0D));
		assertFalse(RaceScoring.squareFallRescue(65.0D));
		assertFalse(RaceScoring.squareFallRescue(50.0D));
		assertTrue(RaceScoring.squarePetFallRescue(10.0D, false));
		assertFalse(RaceScoring.squarePetFallRescue(10.0D, true));
		assertFalse(RaceScoring.squarePetFallRescue(65.0D, false));
		assertTrue(RaceScoring.forfeitDropsCourseAssign());
	}

	@Test
	void aPostedDuelStakeIsConsumedNotRefundedWhenTheHeatStarts() {
		assertEquals(0, RaceScoring.postedDuelReturnedAtStart(10));
		assertEquals(0, RaceScoring.postedDuelReturnedAtStart(0));
		assertEquals(20, RaceScoring.duelPotGp(10, 2));
		assertEquals(0, RaceScoring.duelPotGp(0, 2));
	}

	@Test
	void sneakReplacesAFedNutEvenWhileInLove() {
		assertTrue(RaceScoring.sneakReplacesFedNut(true, true));
		assertFalse(RaceScoring.sneakReplacesFedNut(true, false));
		assertFalse(RaceScoring.sneakReplacesFedNut(false, true));
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
		assertTrue(RaceScoring.raceLoopShouldPlay(true, false, false, true));
		assertFalse(RaceScoring.villageLoopShouldPlay(true, false, true));
		assertTrue(RaceScoring.villageLoopShouldPlay(true, false, false));
		assertTrue(RaceScoring.onCourseIsland(10.0D, 10.0D, 40.0D, 40.0D, 8.0D));
		assertFalse(RaceScoring.onCourseIsland(80.0D, 0.0D, 40.0D, 40.0D, 8.0D));
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

	/** A host (~0 ms), a guest (120 ms) and an AI bird that all cross together finish together. */
	@Test
	void lagCreditPutsHostGuestAndFieldOnOneClock() {
		assertEquals(0.0D, RaceScoring.lagCreditTicks(0), 1e-9);
		assertEquals(2.4D, RaceScoring.lagCreditTicks(120), 1e-9);
		assertEquals(RaceScoring.MAX_LAG_CREDIT_TICKS, RaceScoring.lagCreditTicks(5000), 1e-9);
		assertEquals(0.0D, RaceScoring.lagCreditTicks(-5), 1e-9);
		// the guest's crossing reaches the server 2.4 ticks after the host's; credited, they tie
		double host = 100 - 1 + 0.5 - RaceScoring.lagCreditTicks(0);
		double guest = 102.4 - 1 + 0.5 - RaceScoring.lagCreditTicks(120);
		assertEquals(host, guest, 1e-9);
	}

	@Test
	void crossFractionTimesTheLineInsideATick() {
		assertEquals(0.5D, RaceScoring.crossFraction(0.99D, 0.01D), 1e-9);
		assertEquals(0.25D, RaceScoring.crossFraction(0.995D, 0.015D), 1e-9);
		assertEquals(1.0D, RaceScoring.crossFraction(0.99D, 0.99D), 1e-9);
		// two birds over the line in one tick: the one nearer it at the start of the tick is first
		assertTrue(RaceScoring.crossFraction(0.998D, 0.004D) < RaceScoring.crossFraction(0.995D, 0.004D));
	}

	@Test
	void aFinishWaitsUntilNoLaterReportCanBeatIt() {
		int wait = RaceScoring.MAX_LAG_CREDIT_TICKS;
		assertFalse(RaceScoring.finishSettled(100.0D, 100 + wait));
		assertTrue(RaceScoring.finishSettled(100.0D, 101 + wait));
		// the earliest time a crossing reported next tick can carry is now - wait
		int now = 200;
		double earliestNext = (now + 1) - 1 + 0.0D - RaceScoring.lagCreditTicks(RaceScoring.MAX_LAG_CREDIT_MS);
		assertTrue(RaceScoring.finishSettled(earliestNext - 1e-6, now));
		assertFalse(RaceScoring.finishSettled(earliestNext, now));
	}
}
