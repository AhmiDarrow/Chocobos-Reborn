package tk.darrow.chocobosreborn.race;

/**
 * Pure FF7 class and lap rules. Safe to unit-test without a Minecraft server.
 */
public final class RaceScoring {
	private RaceScoring() {
	}

	public record Promotion(RaceClass raceClass, int classWins, boolean promoted) {
	}

	public static boolean finishGraceExpired(int raceTicks, int firstFinishTick) {
		return firstFinishTick >= 0 && raceTicks - firstFinishTick > 40;
	}

	public static Promotion afterFirstPlace(RaceClass current, int classWins) {
		if (current == RaceClass.S) {
			return new Promotion(RaceClass.S, RaceClass.WINS_TO_PROMOTE, false);
		}
		int wins = classWins + 1;
		if (wins >= RaceClass.WINS_TO_PROMOTE) {
			return new Promotion(current.next(), 0, true);
		}
		return new Promotion(current, wins, false);
	}

	public static boolean wrappedPastStart(double lastProgress, double nowProgress) {
		return lastProgress > 0.75D && nowProgress < 0.25D;
	}

	public static boolean inMidcourseBand(double progress) {
		return progress > 0.40D && progress < 0.80D;
	}

	public static boolean passedMidcourse(double lastProgress, double nowProgress) {
		return inMidcourseBand(nowProgress) && nowProgress >= lastProgress;
	}

	public static boolean countsLap(boolean armed, double lastProgress, double nowProgress) {
		return armed && wrappedPastStart(lastProgress, nowProgress);
	}

	public static boolean awardsFirstPlace(boolean completedCourse, int place) {
		return completedCourse && place == 1;
	}

	public static boolean awardsRankedWin(boolean ranked, boolean completedCourse, int place) {
		return ranked && awardsFirstPlace(completedCourse, place);
	}

	public static boolean onWaterStretch(boolean inWater, boolean waterAtFeet) {
		return onWaterStretch(inWater, waterAtFeet, false);
	}

	public static boolean onWaterStretch(boolean inWater, boolean waterAtFeet, boolean waterBelow) {
		return inWater || waterAtFeet || waterBelow;
	}

	public static int displayLap(int completedLaps, int totalLaps) {
		if (totalLaps < 1) {
			return 1;
		}
		return Math.min(completedLaps + 1, totalLaps);
	}

	public static int displayPlace(int place) {
		return Math.max(1, place);
	}

	public static boolean finished(int completedLaps, int totalLaps) {
		return completedLaps >= totalLaps;
	}

	public static int maxStamina(int gradeRank, int classId, boolean teioh) {
		int base = 80 + Math.max(0, gradeRank) * 18 + Math.max(0, classId) * 16;
		if (teioh) {
			return Math.round(base * 1.25F);
		}
		return base;
	}

	public static double gradeSpeedMul(int gradeRank) {
		return switch (Math.max(0, Math.min(4, gradeRank))) {
			case 0 -> 0.86D;
			case 1 -> 0.94D;
			case 2 -> 1.00D;
			case 3 -> 1.10D;
			default -> 1.18D;
		};
	}

	public static double mountedCruise(double landSpeed, double waterSpeed, boolean onWater, boolean racing, int gradeRank) {
		double base = (!racing && onWater) ? waterSpeed : landSpeed;
		return base * gradeSpeedMul(gradeRank);
	}

	public static double dashMul() {
		return 1.62D;
	}

	public static double recoverMul() {
		return 0.42D;
	}

	public static double emptyStaminaMul() {
		return 0.62D;
	}

	public static double sortKey(boolean finished, int finishIndex, int laps, double progress) {
		if (finished) {
			return 1000.0D - finishIndex;
		}
		return laps + progress;
	}

	public static int placeOf(int finishIndex, int finishCount) {
		if (finishIndex < 0) {
			return Math.max(1, finishCount);
		}
		return finishIndex + 1;
	}

	public static boolean canEnterSquare(boolean baby, boolean saddled, boolean ownedOrCreative) {
		return !baby && saddled && ownedOrCreative;
	}

	public static boolean courseOccupied(int liveSessions) {
		return liveSessions > 0;
	}

	public static boolean sameCourseIndex(int offerCourse, int tappedCourse) {
		return offerCourse == tappedCourse;
	}

	public static boolean stayParksWithNoAi(boolean raceNpc, boolean mounted, boolean standstill) {
		return !raceNpc && !mounted && standstill;
	}

	public static boolean saddleBagStillValid(boolean alive, boolean racingOrHold, boolean inRange) {
		return alive && !racingOrHold && inRange;
	}

	public static boolean gateSkipsDefaultItemUse(boolean consumesAction, boolean fail) {
		return consumesAction || fail;
	}

	public static boolean forfeitOnDismount(boolean raceHasStarted) {
		return raceHasStarted;
	}

	public static boolean cancelPassengerDismount(boolean racingOrHold, boolean passengerSneaking,
	                                             boolean inWater, boolean onGround, boolean passengerAlive) {
		if (!passengerAlive) {
			return false;
		}
		if (racingOrHold) {
			return true;
		}
		return passengerSneaking && !inWater && !onGround;
	}

	public static boolean clientConsumesGysahlOnBird(boolean holdingGysahl) {
		return holdingGysahl;
	}

	public static boolean driftedFromStall(double dx, double dy, double dz) {
		return dx * dx + dy * dy + dz * dz > 0.36D;
	}

	public static boolean spaceBird(boolean green, boolean black, boolean gold, boolean white) {
		return green || black || gold || white;
	}

	public static double terrainMultiplier(boolean space, boolean water, boolean spaceBird, boolean waterBird) {
		double mul = 1.0D;
		if (space) {
			mul *= spaceBird ? 1.28D : 0.88D;
		}
		if (water) {
			mul *= waterBird ? 1.26D : 0.55D;
		}
		return mul;
	}

	public static boolean mayFlyDuringRace(boolean racingOrHold, boolean canFly) {
		return canFly && !racingOrHold;
	}

	public static boolean stallFitsTrack(double offset, double halfWidth) {
		return Math.abs(offset) <= halfWidth;
	}

	public static boolean squareFallRescue(double y, boolean onCourse) {
		return y < 50.0D && !onCourse;
	}

	public static boolean playVictoryOnOpponentLeave(boolean winnerAlreadyFinished) {
		return !winnerAlreadyFinished;
	}

	public static int saddleBagMenuSlots(int cargoSlots, boolean separateSaddleSlot) {
		int cargo = Math.max(0, cargoSlots);
		return separateSaddleSlot ? cargo + 1 : cargo;
	}

	public static boolean raceLoopShouldPlay(boolean inSquare, boolean racing, boolean finishedCourse) {
		return inSquare && racing && !finishedCourse;
	}

	/** The village theme plays whenever the player is in Whiskerwind and not on the course. */
	public static boolean villageLoopShouldPlay(boolean inSquare, boolean racing) {
		return inSquare && !racing;
	}

	public static String raceLoopKey(String trackName) {
		if (trackName == null) {
			return "chocobo_dash";
		}
		return switch (trackName) {
			case "c_shore", "c_lagoon" -> "chocobo_race_gallop";
			case "b_ford", "b_rapids" -> "rune_dash";
			case "b_canyon", "b_frost", "b_mesa", "b_glacier" -> "gallop_of_adventure";
			case "a_crystal", "a_canopy", "a_ember", "a_deeps", "a_temple", "a_inferno", "s_void", "s_maelstrom" -> "gallop_of_heroes";
			case "s_skyway", "s_keep", "s_starfall", "s_citadel" -> "speed_of_the_dragon";
			default -> "chocobo_dash";
		};
	}

	public static final int MAX_STAKE = 16;

	public enum BetPick {
		SELF, JOE, TEIOH, FIELD, OPPONENT
	}

	public static int clampStake(int held) {
		if (held < 1) {
			return 0;
		}
		return Math.min(MAX_STAKE, held);
	}

	public static boolean mayPlaceBet(boolean booksOpen, boolean alreadyBet, int clampedStake) {
		return booksOpen && !alreadyBet && clampedStake > 0;
	}

	public static int selfOdds(int classId) {
		return switch (Math.max(0, Math.min(3, classId))) {
			case 0 -> 2;
			case 1 -> 3;
			case 2 -> 4;
			default -> 5;
		};
	}

	public static int odds(BetPick pick, int classId) {
		if (pick == null) {
			return selfOdds(classId);
		}
		return switch (pick) {
			case SELF -> selfOdds(classId);
			case JOE -> 3;
			case TEIOH -> 2;
			case FIELD -> 6;
			case OPPONENT -> 2;
		};
	}

	public static int payout(int stake, int multiplier, boolean won) {
		if (!won || stake < 1 || multiplier < 1) {
			return 0;
		}
		return stake * multiplier;
	}

	public static boolean pickWon(BetPick pick, boolean ranked, boolean playerFirst, boolean joeFirst,
	                             boolean teiohFirst, boolean fieldFirst, boolean opponentFirst) {
		if (pick == null) {
			return false;
		}
		return switch (pick) {
			case SELF -> playerFirst;
			case JOE -> ranked && joeFirst;
			case TEIOH -> ranked && teiohFirst;
			case FIELD -> ranked && fieldFirst;
			case OPPONENT -> !ranked && opponentFirst;
		};
	}

	public static BetPick nextPick(BetPick current, boolean ranked, boolean includesTeioh) {
		BetPick now = current == null ? BetPick.SELF : current;
		if (!ranked) {
			return now == BetPick.SELF ? BetPick.OPPONENT : BetPick.SELF;
		}
		if (!includesTeioh) {
			return now == BetPick.SELF ? BetPick.FIELD : BetPick.SELF;
		}
		return switch (now) {
			case SELF -> BetPick.JOE;
			case JOE -> BetPick.TEIOH;
			case TEIOH -> BetPick.FIELD;
			default -> BetPick.SELF;
		};
	}

	public static BetPick legalize(BetPick pick, boolean ranked, boolean includesTeioh) {
		BetPick now = pick == null ? BetPick.SELF : pick;
		if (!ranked) {
			return now == BetPick.SELF ? BetPick.SELF : BetPick.OPPONENT;
		}
		if (now == BetPick.OPPONENT) {
			return BetPick.FIELD;
		}
		if (!includesTeioh && (now == BetPick.JOE || now == BetPick.TEIOH)) {
			return BetPick.FIELD;
		}
		return now;
	}

	public static boolean booksOpen(boolean inSquare, boolean running) {
		return inSquare && !running;
	}
}
