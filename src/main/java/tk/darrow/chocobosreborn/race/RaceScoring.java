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

	/** First-places still needed in this class; Class S is the top of the ladder. */
	public static int winsUntilPromote(RaceClass current, int classWins) {
		if (current == RaceClass.S) {
			return 0;
		}
		return Math.max(0, RaceClass.WINS_TO_PROMOTE - classWins);
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

	/**
	 * How many in-between samples a boost-pad sweep needs. Fast dashes cover more
	 * than one block per tick and would skip a 1-block strip if we only tested feet.
	 */
	public static int boostPadSamples(double dx, double dz) {
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist < 0.25D) {
			return 1;
		}
		return Math.min(8, (int) Math.ceil(dist * 2.0D));
	}

	/** Speed training: +0.35% per point, +35% at 100. Same multiplier for riders and AI. */
	public static final double SPEED_PER_POINT = 0.0035D;

	public static double speedTrainingMul(int trainedSpeed) {
		return 1.0D + SPEED_PER_POINT * Math.max(0, Math.min(100, trainedSpeed));
	}

	/**
	 * Intelligence stretches a dash: on every other tick a smart bird may keep the
	 * point. At 0 nothing is skipped. At 100 half of the drain ticks are free.
	 * The same roll is used for a rider and for the race AI.
	 */
	public static boolean intelSkipsDashDrain(int intelligence, int tickCount, int roll0to99) {
		int intel = Math.max(0, Math.min(100, intelligence));
		return intel > 0 && tickCount % 2 == 0 && roll0to99 < intel;
	}

	/** After a dash empties the bar, another dash waits until stamina is back here. */
	public static final int DASH_READY = 50;

	/** The lock stays on until the bar has climbed back to {@link #DASH_READY}. */
	public static boolean stillDashLocked(boolean locked, int staminaNow) {
		return locked && staminaNow < DASH_READY;
	}

	/** Boost-pad length. 50 ticks with no intelligence, 70 at 100. */
	public static int boostTicks(int intelligence) {
		return 50 + Math.max(0, Math.min(100, intelligence)) / 5;
	}

	/** Boost-pad strength. +55% with no intelligence, +75% at 100. */
	public static double boostPower(int intelligence) {
		return 0.55D + 0.002D * Math.max(0, Math.min(100, intelligence));
	}

	/** Dash ends only when the bar is actually empty (an intel skip can keep the last point). */
	public static boolean dashEnds(int staminaNow) {
		return staminaNow <= 0;
	}

	/**
	 * The dash key spends stamina only while the rider is driving forward.
	 * {@code sprinting} is that key held this tick, not {@code LivingEntity#isSprinting}:
	 * the vanilla flag stays latched for as long as W is held. Airborne on a flier
	 * the same key is the dive, same as {@code ChocoboEntity} riding.
	 */
	public static boolean riderWantsDash(boolean sprinting, float forward, boolean flies, boolean onGround) {
		return sprinting && forward > 0.0F && !(flies && !onGround);
	}

	/**
	 * Vanilla drops a vehicle packet when displacement² − velocity² exceeds 100.
	 * A guest chocobo burst inside this many blocks is given a matching velocity
	 * so the check passes; anything farther (a course teleport) is still rejected.
	 */
	public static final double VEHICLE_SLACK_BLOCKS = 40.0D;

	public static boolean vehicleMoveWithinSlack(double dx, double dy, double dz) {
		if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)) {
			return false;
		}
		double dist2 = dx * dx + dy * dy + dz * dz;
		return dist2 > 100.0D && dist2 <= VEHICLE_SLACK_BLOCKS * VEHICLE_SLACK_BLOCKS;
	}

	/** Yaw catch-up 0..1. Zero coop is mushy; 100 is a snap to the rider. */
	public static float turnCatchup(int cooperation) {
		return 0.35F + 0.65F * Math.max(0, Math.min(100, cooperation)) / 100.0F;
	}

	/** Max degrees per tick for AI rotlerp. 18 at 0 coop, 60 (old cap) at 100. */
	public static float turnMaxDegrees(int cooperation) {
		return 18.0F + 42.0F * Math.max(0, Math.min(100, cooperation)) / 100.0F;
	}

	/** Rider strafe scale. Was a flat 0.5; low coop crab-walks. */
	public static float strafeMul(int cooperation) {
		return 0.25F + 0.25F * Math.max(0, Math.min(100, cooperation)) / 100.0F;
	}

	/** Lane wobble scale: sloppy birds wander more (1.25 at 0, 0.75 at 100). */
	public static double handlingWobbleMul(int cooperation) {
		return 1.25D - 0.50D * Math.max(0, Math.min(100, cooperation)) / 100.0D;
	}

	/** Line-hold scale from coop (0.55 at 0, 1.0 at 100), stacked on class discipline. */
	public static double handlingLineMul(int cooperation) {
		return 0.55D + 0.45D * Math.max(0, Math.min(100, cooperation)) / 100.0D;
	}

	/**
	 * Training points to stamp on a field NPC so the four stats exist. Rivals sit
	 * at 100; class birds sit below a fully greens-fed player of that ladder.
	 */
	public static int fieldTraining(int classId, boolean rival) {
		if (rival) {
			return 100;
		}
		return switch (Math.max(0, Math.min(3, classId))) {
			case 0 -> 22;
			case 1 -> 48;
			case 2 -> 72;
			default -> 92;
		};
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

	/** A placed rider keeps that place even if later marked DNF (abort / logout after the line). */
	public static int resultPlace(int finishIndex, boolean forfeited, int finishCount, int fieldSize, int runningPlace) {
		if (finishIndex >= 0) {
			return placeOf(finishIndex, finishCount);
		}
		if (forfeited) {
			return fieldSize;
		}
		return Math.max(finishCount + 1, runningPlace);
	}

	/** Fun gates: sprint is course 0, long is the first grand prix (course 3). */
	public static int funGateCourse(boolean longCourse) {
		return longCourse ? 3 : 0;
	}

	/**
	 * Start-arrow pixels, along increasing forward, across 0 at the centre.
	 * A 3-wide shaft and a 45-degree head (11 wide at the barbs, one gold tip).
	 * Stamped from one heading so it stays an arrow on the block grid.
	 */
	public static final String[] START_ARROW_MASK = {
			"    ###    ",
			"    ###    ",
			"    ###    ",
			"    ###    ",
			"    ###    ",
			"    ###    ",
			"###########",
			" ######### ",
			"  #######  ",
			"   #####   ",
			"    ###    ",
			"     G     ",
	};

	public static int startArrowLength() {
		return START_ARROW_MASK.length;
	}

	public static int startArrowHalf() {
		return START_ARROW_MASK[0].length() / 2;
	}

	public static char startArrowChar(int along, int across) {
		if (along < 0 || along >= START_ARROW_MASK.length) {
			return ' ';
		}
		String row = START_ARROW_MASK[along];
		int col = across + row.length() / 2;
		if (col < 0 || col >= row.length()) {
			return ' ';
		}
		return row.charAt(col);
	}

	public static boolean startArrowCell(int along, int across) {
		char c = startArrowChar(along, across);
		return c == '#' || c == 'G';
	}

	public static boolean startArrowTip(int along, int across) {
		return startArrowChar(along, across) == 'G';
	}

	/** Nearest compass step of a unit (x, z) tangent. */
	public static int[] arrowForward(double tx, double tz) {
		if (Math.abs(tx) >= Math.abs(tz)) {
			return new int[]{tx >= 0.0D ? 1 : -1, 0};
		}
		return new int[]{0, tz >= 0.0D ? 1 : -1};
	}

	/** Right-hand step when looking along (fx, fz) on the Minecraft XZ map. */
	public static int[] arrowRight(int fx, int fz) {
		return new int[]{-fz, fx};
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

	/** Seat AI jockeys after both entities have been sent; HOLD tick 2 is enough. */
	public static boolean seatJockeysOnHoldTick(int holdTick) {
		return holdTick == 2;
	}

	/** Scrub leftover NPCs after the field has mounted, not on the spawn tick. */
	public static boolean scrubCourseOnHoldTick(int holdTick) {
		return holdTick == 40;
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

	/** HOLD scratch hands the stake back; after GO a DNF is a settled loss. */
	public static boolean refundBookieOnForfeit(boolean raceHasStarted) {
		return !raceHasStarted;
	}

	/** Nobody placed: leftover spectator stakes scratch instead of paying as losses. */
	public static boolean scratchRefundsLeftoverBets(boolean anyHumanFinished) {
		return !anyHumanFinished;
	}

	/** Fun heats only take bets from a rider in that heat (SELF/OPPONENT). */
	public static boolean spectatorMayBetOnFun(boolean ranked, boolean racerInHeat) {
		return ranked || racerInHeat;
	}

	/** Ranked: class or below. Same rule for posting or accepting a duel. */
	public static boolean mayEnterCourse(int birdClassId, int trackClassId) {
		return trackClassId <= birdClassId;
	}

	/**
	 * Live-board legalize: drop JOE/TEIOH when that bird is not on the card, SELF when
	 * the bettor is not racing, and OPPONENT when there is no second human.
	 */
	public static BetPick legalizeBettor(BetPick pick, boolean ranked, boolean racerInHeat,
	                                    boolean hasJoe, boolean hasTeioh, boolean hasOpponent) {
		if (racerInHeat) {
			// a racer may only back themselves: anything else pays when they lose
			return BetPick.SELF;
		}
		BetPick now = legalize(pick, ranked, hasJoe || hasTeioh);
		if (ranked) {
			if (now == BetPick.JOE && !hasJoe) {
				now = BetPick.FIELD;
			}
			if (now == BetPick.TEIOH && !hasTeioh) {
				now = BetPick.FIELD;
			}
			if (now == BetPick.SELF && !racerInHeat) {
				now = BetPick.FIELD;
			}
		} else if (now == BetPick.OPPONENT && !hasOpponent) {
			now = BetPick.SELF;
		}
		return now;
	}

	/** Empty-hand cycle over picks that can actually pay on this heat. */
	public static BetPick nextLivePick(BetPick current, boolean ranked, boolean racerInHeat, boolean hasJoe,
	                                   boolean hasTeioh, boolean hasOpponent) {
		BetPick now = current == null ? BetPick.SELF : current;
		if (racerInHeat) {
			return BetPick.SELF;
		}
		if (!ranked) {
			if (!hasOpponent) {
				return BetPick.SELF;
			}
			return now == BetPick.SELF ? BetPick.OPPONENT : BetPick.SELF;
		}
		java.util.ArrayList<BetPick> cycle = new java.util.ArrayList<>();
		if (racerInHeat) {
			cycle.add(BetPick.SELF);
		}
		if (hasJoe) {
			cycle.add(BetPick.JOE);
		}
		if (hasTeioh) {
			cycle.add(BetPick.TEIOH);
		}
		cycle.add(BetPick.FIELD);
		int i = cycle.indexOf(now);
		if (i < 0) {
			return cycle.get(0);
		}
		return cycle.get((i + 1) % cycle.size());
	}

	/** Still a live racer: not already DNF and not already placed. */
	/**
	 * Most ping a rider is credited at the line (ms). The server sees a guest's bird one
	 * trip late and the guest heard GO one trip late, so without this a guest loses a
	 * whole ping to the host (whose ping is ~0) and to the AI field in every close finish.
	 */
	public static final int MAX_LAG_CREDIT_MS = 300;
	/** Ticks a finish waits before it is placed, so a later-reported but earlier crossing can still slot ahead. */
	public static final int MAX_LAG_CREDIT_TICKS = MAX_LAG_CREDIT_MS / 50;

	/** Ticks a rider with this round-trip ping is credited at the line (AI racers: 0). */
	public static double lagCreditTicks(int latencyMs) {
		return Math.max(0, Math.min(MAX_LAG_CREDIT_MS, latencyMs)) / 50.0D;
	}

	/**
	 * Share of this tick's travel that came before the start line (0..1), from lap
	 * progress before and after the tick: two birds crossing in the same tick are
	 * ordered by where they were, not by who comes first in the field list.
	 */
	public static double crossFraction(double before, double after) {
		double travelled = after - before;
		if (travelled < 0.0D) {
			travelled += 1.0D;
		}
		double toLine = 1.0D - before;
		if (!(travelled > 1.0E-9D) || toLine > travelled) {
			return 1.0D;
		}
		return Math.max(0.0D, Math.min(1.0D, toLine / travelled));
	}

	/** A finish crossing at {@code time} (ticks) is safe to place once no later report can beat it. */
	public static boolean finishSettled(double time, int nowTick) {
		return time < nowTick - MAX_LAG_CREDIT_TICKS;
	}

	public static boolean stillOnCourse(boolean forfeited, int finishIndex) {
		return !forfeited && finishIndex < 0;
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

	/** Off-course: sneak is "down" for fliers in the air and water-walkers on water, so those riders stay mounted. */
	public static boolean lockOffCourseDismount(boolean flyer, boolean onGround, boolean waterWalker, boolean inOrOnWater) {
		return (flyer && !onGround) || (waterWalker && inOrOnWater);
	}

	public static boolean clientConsumesGysahlOnBird(boolean holdingGysahl) {
		return holdingGysahl;
	}

	/** Town scenery, AI racers, and live heats: no lead, no player damage, arrows pass. */
	public static boolean squareNpcProtected(boolean townBird, boolean raceNpc, boolean racing) {
		return squareNpcProtected(townBird, raceNpc, racing, false);
	}

	public static boolean squareNpcProtected(boolean townBird, boolean raceNpc, boolean racing, boolean assignedCourse) {
		return townBird || raceNpc || racing || assignedCourse;
	}

	/**
	 * Whiskerwind is a show, not a hazard: a rider is as safe there as the birds are
	 * ({@link #squareNpcProtected}). Riding a bird that does not suit a lava or water
	 * feature should cost a place, not a life and an inventory in the void.
	 */
	public static boolean squareRiderProtected(boolean inSquare, boolean bypassesInvulnerability) {
		return inSquare && !bypassesInvulnerability;
	}

	/** A visitor below the island, racing or not, is falling: put them back in the paddock. */
	public static boolean squareVisitorFallRescue(double y, boolean ridingSomething) {
		return y < 50.0D && !ridingSomething;
	}

	/** Join a pending heat only when there is a stall, or this rider is already in it. */
	public static boolean joinSkipsFullHeat(int entrants, int field, boolean alreadyIn) {
		return alreadyIn || entrants < field;
	}

	/** Spectator GP goes on a live HOLD heat when there is exactly one. */
	public static boolean attachSpectatorBet(boolean racerInSession, int openHoldHeats) {
		return attachSpectatorBet(racerInSession, false, openHoldHeats);
	}

	/** A rider already on Esther's timetable keeps pending for their own mark. */
	public static boolean attachSpectatorBet(boolean racerInSession, boolean timetableEntered, int openHoldHeats) {
		if (racerInSession) {
			return true;
		}
		if (timetableEntered) {
			return false;
		}
		return openHoldHeats == 1;
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

	/**
	 * A racer below the island is falling, wherever it is: the lowest course rock sits
	 * at y 59, so nothing legitimate is down here (flight is blocked during a heat).
	 * This used to skip a bird that was still over the road, which meant a drop through
	 * a gap in the course was never caught at all and the rider fell out of the world.
	 */
	public static boolean squareFallRescue(double y) {
		return y < 50.0D;
	}

	/** Owned pets in Whiskerwind: catch a fall into the void, not a Gold flying between islands. */
	public static boolean squarePetFallRescue(double y, boolean flyingAirborne) {
		return y < 50.0D && !flyingAirborne;
	}

	/** Posted duel GP stays in the pot. Refunding it at start, then minting the pot at settle, doubles the purse. */
	public static int postedDuelReturnedAtStart(int postedStake) {
		return postedStake < 0 ? 0 : 0;
	}

	public static int duelPotGp(int stakePerRider, int humanCount) {
		return Math.max(0, stakePerRider) * Math.max(0, humanCount);
	}

	/** After a DNF send-home the bird is off the course, so plaza void-rescue may run. */
	public static boolean forfeitDropsCourseAssign() {
		return true;
	}

	/** Sneak-click replaces an already-fed nut even while the vanilla love timer is running. */
	public static boolean sneakReplacesFedNut(boolean alreadyFedOrInLove, boolean sneaking) {
		return alreadyFedOrInLove && sneaking;
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

	/** Keep the course loop after the line until the rider leaves the island, so the village playlist does not cover the sting. */
	public static boolean raceLoopShouldPlay(boolean inSquare, boolean racing, boolean finishedCourse, boolean onCourseIsland) {
		return inSquare && (racing || onCourseIsland) && !finishedCourse;
	}

	/** The village theme plays whenever the player is in Whiskerwind and not on the course. */
	public static boolean villageLoopShouldPlay(boolean inSquare, boolean racing) {
		return villageLoopShouldPlay(inSquare, racing, false);
	}

	public static boolean villageLoopShouldPlay(boolean inSquare, boolean racing, boolean onCourseIsland) {
		return inSquare && !racing && !onCourseIsland;
	}

	public static boolean onCourseIsland(double dx, double dz, double radiusX, double radiusZ, double pad) {
		return Math.abs(dx) <= radiusX + pad && Math.abs(dz) <= radiusZ + pad;
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

	/** Course picker offers 0, 4, 8, 16, 32; the packet is clamped to that range. */
	public static int clampDuelStake(int stake) {
		return Math.max(0, Math.min(32, stake));
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

	/** @param fieldBirds AI birds on the card other than Teiyo and Jolo, i.e. how many a FIELD bet covers */
	public static int odds(BetPick pick, int classId, int fieldBirds) {
		if (pick == null) {
			return selfOdds(classId);
		}
		return switch (pick) {
			case SELF -> selfOdds(classId);
			case JOE -> 3;
			case TEIOH -> 2;
			case FIELD -> fieldOdds(fieldBirds);
			case OPPONENT -> 2;
		};
	}

	/** Roughly fair on a six-bird card: 6x for a lone field bird, 2x for three or four, evens for five. */
	public static int fieldOdds(int fieldBirds) {
		if (fieldBirds <= 0) {
			return 6;
		}
		return Math.max(1, Math.round(6.0F / fieldBirds));
	}

	/** Field birds on a ranked card with {@code humans} riders (Teiyo and Jolo take two slots when they run). */
	public static int expectedFieldBirds(int humans, boolean includesTeioh) {
		return Math.max(0, 6 - humans - (includesTeioh ? 2 : 0));
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

	/** Next timetable mark that still leaves {@code minLead} ticks to saddle up. */
	public static long nextHeatMark(long now, int period, int minLead) {
		long mark = (now / period + 1) * period;
		if (mark - now < minLead) {
			mark += period;
		}
		return mark;
	}

	/** A saved heat mark that is already due must not fire on the first tick after reload. */
	public static long persistHeatStart(long saved, long now, int period, int minLead) {
		return saved > now ? saved : nextHeatMark(now, period, minLead);
	}
}
