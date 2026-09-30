package tk.darrow.chocobosreborn.breed;

import org.junit.jupiter.api.Test;
import tk.darrow.chocobosreborn.race.RaceClass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreedingOddsTest {
	@Test
	void greenBlueNeedsFourWinsForGuarantee() {
		assertEquals(0.10D, BreedingOdds.chanceFromWins(0, 4), 1.0E-9);
		assertEquals(0.10D + 0.90D * 0.5D, BreedingOdds.chanceFromWins(2, 4), 1.0E-9);
		assertEquals(1.0D, BreedingOdds.chanceFromWins(4, 4), 1.0E-9);
		assertEquals(1.0D, BreedingOdds.chanceFromWins(12, 4), 1.0E-9);
	}

	@Test
	void blackNeedsNineAndGoldNeedsTwelve() {
		assertEquals(1.0D, BreedingOdds.chanceFromWins(9, 9), 1.0E-9);
		assertTrue(BreedingOdds.chanceFromWins(8, 9) < 1.0D);
		assertEquals(1.0D, BreedingOdds.chanceFromWins(12, 12), 1.0E-9);
		assertTrue(BreedingOdds.chanceFromWins(6, 12) < 0.6D);
	}

	@Test
	void colourBreedingNeedsWinsOnBothParents() {
		// FF7: a parent that has never won cannot change the line
		assertEquals(0.0D, BreedingOdds.chance(0, 4, 1, 4), 1.0E-9);
		assertEquals(0.0D, BreedingOdds.chance(1, 0, 1, 4), 1.0E-9);
		// at the minimum on both: 25 %; at the guarantee: certain
		assertEquals(0.25D, BreedingOdds.chance(1, 1, 1, 4), 1.0E-9);
		assertEquals(0.625D, BreedingOdds.chance(2, 1, 1, 4), 1.0E-9);
		assertEquals(1.0D, BreedingOdds.chance(2, 2, 1, 4), 1.0E-9);
		assertEquals(0.25D, BreedingOdds.chance(3, 3, 3, 12), 1.0E-9);
		assertEquals(1.0D, BreedingOdds.chance(6, 6, 3, 12), 1.0E-9);
		assertEquals(1.0D, BreedingOdds.chance(9, 3, 3, 12), 1.0E-9);
	}

	@Test
	void stageWinsScaleByTheClassBar() {
		// per parent: ceil(5 x bar / 36); combined guarantee: 2 x ceil(8 x bar / 36)
		assertEquals(5, BreedingOdds.minWinsEach(RaceClass.C));
		assertEquals(8, BreedingOdds.minWinsEach(RaceClass.B));
		assertEquals(10, BreedingOdds.minWinsEach(RaceClass.A));
		assertEquals(16, BreedingOdds.guaranteeWins(RaceClass.C));
		assertEquals(24, BreedingOdds.guaranteeWins(RaceClass.B));
		assertEquals(32, BreedingOdds.guaranteeWins(RaceClass.A));
		// eight Class C wins a parent is one sprint short of promotion
		assertEquals(RaceClass.POINTS_TO_PROMOTE - 4, 4 * BreedingOdds.guaranteeWins(RaceClass.C) / 2);
	}

	@Test
	void stageChanceRunsFromAQuarterToCertain() {
		for (RaceClass rc : new RaceClass[]{RaceClass.C, RaceClass.B, RaceClass.A}) {
			int min = BreedingOdds.minWinsEach(rc);
			int sure = BreedingOdds.guaranteeWins(rc);
			// one parent short: no roll at all, however many the other has
			assertEquals(0.0D, BreedingOdds.chance(min - 1, sure, min, sure), 1.0E-9, rc.id());
			assertEquals(0.25D, BreedingOdds.chance(min, min, min, sure), 1.0E-9, rc.id());
			assertTrue(BreedingOdds.chance(min, sure - min - 1, min, sure) < 1.0D, rc.id());
			assertEquals(1.0D, BreedingOdds.chance(sure / 2, sure / 2, min, sure), 1.0E-9, rc.id());
			assertEquals(1.0D, BreedingOdds.chance(min, sure - min, min, sure), 1.0E-9, rc.id());
		}
		// halfway from 10 to 16 combined in Class C: 25 % + 75 % x 3 / 6
		assertEquals(0.625D, BreedingOdds.chance(6, 7, 5, 16), 1.0E-9);
	}

	@Test
	void eachStageCountsItsOwnClass() {
		assertEquals(RaceClass.C, BreedRules.stageClass(ChocoboColor.YELLOW, ChocoboColor.YELLOW, ChocoboNut.CAROB));
		assertEquals(RaceClass.B, BreedRules.stageClass(ChocoboColor.GREEN, ChocoboColor.BLUE, ChocoboNut.CAROB));
		assertEquals(RaceClass.B, BreedRules.stageClass(ChocoboColor.BLUE, ChocoboColor.GREEN, ChocoboNut.CAROB));
		assertEquals(RaceClass.A, BreedRules.stageClass(ChocoboColor.BLACK, ChocoboColor.YELLOW, ChocoboNut.ZEIO));
		// one bird fed a nut: the stage it is heading for
		assertEquals(RaceClass.C, BreedRules.stageClass(ChocoboColor.YELLOW, ChocoboNut.CAROB));
		assertEquals(RaceClass.B, BreedRules.stageClass(ChocoboColor.GREEN, ChocoboNut.CAROB));
		assertEquals(RaceClass.B, BreedRules.stageClass(ChocoboColor.BLUE, ChocoboNut.CAROB));
		assertEquals(RaceClass.A, BreedRules.stageClass(ChocoboColor.YELLOW, ChocoboNut.ZEIO));
		assertEquals(RaceClass.A, BreedRules.stageClass(ChocoboColor.BLACK, ChocoboNut.ZEIO));
	}

	@Test
	void zeioOutranksCarob() {
		assertEquals(ChocoboNut.ZEIO, ChocoboNut.stronger(ChocoboNut.CAROB, ChocoboNut.ZEIO));
		assertEquals(ChocoboNut.CAROB, ChocoboNut.stronger(ChocoboNut.NONE, ChocoboNut.CAROB));
		assertEquals(ChocoboNut.NONE, ChocoboNut.byId(-1));
	}

	@Test
	void wonderfulOutranksGreat() {
		assertTrue(ChocoboGrade.WONDERFUL.atLeast(ChocoboGrade.GREAT));
		assertTrue(ChocoboGrade.GREAT.atLeast(ChocoboGrade.GREAT));
		assertEquals(ChocoboGrade.WONDERFUL, ChocoboGrade.GREAT.raise());
		assertEquals(ChocoboGrade.WONDERFUL, ChocoboGrade.WONDERFUL.raise());
		assertEquals(ChocoboGrade.POOR, ChocoboGrade.byRank(-3));
	}

	@Test
	void goldParentsWithoutZeioDoNotHatchGold() {
		assertEquals(ChocoboColor.YELLOW, BreedRules.withoutUnseededGold(
				ChocoboColor.GOLD, ChocoboColor.GOLD, ChocoboNut.CAROB));
		assertEquals(ChocoboColor.GREEN, BreedRules.withoutUnseededGold(
				ChocoboColor.GOLD, ChocoboColor.GREEN, ChocoboNut.NONE));
		assertEquals(ChocoboColor.GOLD, BreedRules.withoutUnseededGold(
				ChocoboColor.GOLD, ChocoboColor.GOLD, ChocoboNut.ZEIO));
	}

	@Test
	void malformedRandomClausesAreSkipped() {
		assertTrue(BreedRules.randomClauseMatches("none", 50));
		assertTrue(BreedRules.randomClauseMatches("above 40", 50));
		assertFalse(BreedRules.randomClauseMatches("above 50", 50));
		assertTrue(BreedRules.randomClauseMatches("under 10", 5));
		assertFalse(BreedRules.randomClauseMatches("above", 50));
		assertFalse(BreedRules.randomClauseMatches("under nope", 50));
	}

	@Test
	void emptyGuaranteeAndNegativeWinsStaySane() {
		assertEquals(1.0D, BreedingOdds.chanceFromWins(0, 0), 1.0E-9);
		assertEquals(0.10D, BreedingOdds.chanceFromWins(-2, 4), 1.0E-9);
		assertEquals(0.10D, BreedingOdds.chanceFromWins(0, 12), 1.0E-9);
	}

	@Test
	void endAndNetherParentsBreedTrueEvenOnACarobHit() {
		assertEquals(ChocoboColor.PURPLE, BreedRules.resolve(
				ChocoboColor.PURPLE, ChocoboColor.PURPLE,
				ChocoboGrade.GOOD, ChocoboGrade.GOOD,
				ChocoboNut.PEPIO, false, true, true, ChocoboColor.PURPLE));
		assertEquals(ChocoboColor.PURPLE, BreedRules.resolve(
				ChocoboColor.PURPLE, ChocoboColor.PURPLE,
				ChocoboGrade.GOOD, ChocoboGrade.GOOD,
				ChocoboNut.CAROB, true, true, true, ChocoboColor.PURPLE));
		assertEquals(ChocoboColor.FLAME, BreedRules.resolve(
				ChocoboColor.FLAME, ChocoboColor.FLAME,
				ChocoboGrade.GOOD, ChocoboGrade.GOOD,
				ChocoboNut.CAROB, true, true, false, ChocoboColor.FLAME));
		assertEquals(ChocoboColor.GREEN, BreedRules.resolve(
				ChocoboColor.YELLOW, ChocoboColor.YELLOW,
				ChocoboGrade.GOOD, ChocoboGrade.GOOD,
				ChocoboNut.CAROB, true, true, true, ChocoboColor.YELLOW));
	}

	@Test
	void greenBlueCarobShortOfWinsHatchesAParentColour() {
		int minEach = BreedingOdds.minWinsEach(RaceClass.B);
		assertFalse(BreedingOdds.qualifies(0, 0, minEach));
		assertFalse(BreedingOdds.qualifies(20, 7, minEach));
		assertTrue(BreedingOdds.qualifies(8, 8, minEach));
		for (ChocoboColor inherit : new ChocoboColor[]{ChocoboColor.GREEN, ChocoboColor.BLUE}) {
			assertEquals(inherit, BreedRules.resolve(
					ChocoboColor.GREEN, ChocoboColor.BLUE,
					ChocoboGrade.GREAT, ChocoboGrade.GREAT,
					ChocoboNut.CAROB, BreedingOdds.qualifies(20, 7, minEach), false, true, inherit));
		}
		// with the wins, a missed Black roll is still White
		assertEquals(ChocoboColor.WHITE, BreedRules.resolve(
				ChocoboColor.GREEN, ChocoboColor.BLUE,
				ChocoboGrade.GREAT, ChocoboGrade.GREAT,
				ChocoboNut.CAROB, BreedingOdds.qualifies(8, 8, minEach), false, true, ChocoboColor.GREEN));
	}

	@Test
	void trainingBonusMustNotBeWrittenBackAsBornGrade() {
		int born = ChocoboGrade.AVERAGE.getRank();
		int trained = ChocoboGreen.gradeFromTraining(born, 120);
		assertEquals(ChocoboGrade.GOOD.getRank(), trained);
		assertEquals(trained, ChocoboGreen.gradeFromTraining(born, 120));
		assertEquals(ChocoboGrade.GREAT.getRank(), ChocoboGreen.gradeFromTraining(trained, 120));
	}

	@Test
	void trainingGreensWaitFiveMinutesBetweenFeeds() {
		assertEquals(6000, ChocoboGreen.TRAIN_COOLDOWN_TICKS);
		assertTrue(ChocoboGreen.trainReady(0L, 100L, false));
		assertTrue(ChocoboGreen.trainReady(1000L, 1001L, true));
		assertFalse(ChocoboGreen.trainReady(1000L, 1001L, false));
		assertEquals(5999, ChocoboGreen.trainWaitTicks(1000L, 1001L));
		assertEquals(0, ChocoboGreen.trainWaitTicks(1000L, 7000L));
		assertTrue(ChocoboGreen.trainReady(1000L, 7000L, false));
		assertEquals(ChocoboGreen.TRAIN_COOLDOWN_TICKS, ChocoboGreen.trainWaitTicks(5000L, 1000L));
		assertEquals("5:00", ChocoboGreen.trainWaitClock(6000));
		assertEquals("0:01", ChocoboGreen.trainWaitClock(1));
		assertEquals("2:30", ChocoboGreen.trainWaitClock(3000));
	}
}
