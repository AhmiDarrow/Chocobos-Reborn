package tk.darrow.chocobosreborn.race;

import tk.darrow.chocobosreborn.breed.ChocoboColor;

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

	/**
	 * A win on a heat as long as the class's shortest sprint is worth this many points;
	 * every other heat scales from it by length ({@link #winPoints}).
	 */
	static final int REFERENCE_POINTS = 4;

	/** Promotion on the nine-point ladder (save formats 1 and 2); an older first-place mark was 3 of its points. */
	static final int OLD_PROMOTE = 9;
	private static final int OLD_MARK_POINTS = 3;

	private static final java.util.Map<RaceClass, Double> REFERENCE = new java.util.EnumMap<>(RaceClass.class);

	/**
	 * Points for a ranked first place on {@code track}: 4 x heat length / the class's
	 * shortest sprint, rounded. Every sprint is 4 (a class's sprints are within 12.5 % of
	 * each other, so pinning them is only a guard); a grand prix scores by its heat, e.g.
	 * a heat 2.5 times the shortest sprint is 10. {@link RaceClass#POINTS_TO_PROMOTE} (36)
	 * is nine sprint wins. HANDOFF.md "Points by distance" has the table.
	 */
	public static int winPoints(RaceTrack track) {
		if (track.isSprint()) {
			return REFERENCE_POINTS;
		}
		return winPoints(track.raceLength(), referenceLength(track.getRaceClass()));
	}

	/** {@link #winPoints(RaceTrack)} for a heat of {@code heatLength} against a class reference. */
	static int winPoints(double heatLength, double reference) {
		return Math.max(REFERENCE_POINTS, (int) Math.round(REFERENCE_POINTS * heatLength / reference));
	}

	/** The class's yardstick: its shortest sprint (one lap), from the course table. */
	public static synchronized double referenceLength(RaceClass raceClass) {
		return REFERENCE.computeIfAbsent(raceClass, rc -> {
			double min = Double.MAX_VALUE;
			for (RaceTrack t : RaceTrack.sprintsOf(rc)) {
				min = Math.min(min, t.raceLength());
			}
			if (min == Double.MAX_VALUE) {
				// no sprint in the class: its longest lap stands in
				min = 1.0D;
				for (RaceTrack t : RaceTrack.ofClass(rc)) {
					min = Math.max(min, t.lapLength());
				}
			}
			return min;
		});
	}

	/**
	 * A ranked first place worth {@code earned} points ({@link #winPoints}).
	 * {@link RaceClass#POINTS_TO_PROMOTE} promote; spare points do not carry over.
	 * Class S is the top: it shows the full ladder and earns nothing more.
	 */
	public static Promotion afterFirstPlace(RaceClass current, int classPoints, int earned) {
		if (current == RaceClass.S) {
			return new Promotion(RaceClass.S, RaceClass.POINTS_TO_PROMOTE, false);
		}
		int points = Math.max(0, classPoints) + Math.max(0, earned);
		if (points >= RaceClass.POINTS_TO_PROMOTE) {
			return new Promotion(current.next(), 0, true);
		}
		return new Promotion(current, points, false);
	}

	/**
	 * Save format 1 -> 2. A bird saved before the points ladder carried first-place marks
	 * toward the old three-win promotion. Convert them once, keeping the same share of the
	 * way up: 1 or 2 marks become 3 or 6 points of 9, and a Class S bird shows the full 9.
	 * Anything else is already points (a bird cannot hold 3 marks below S).
	 */
	public static int migratedClassPoints(int classId, int stored) {
		if (classId >= RaceClass.S.getId()) {
			return OLD_PROMOTE;
		}
		if (stored >= 1 && stored <= 2) {
			return stored * OLD_MARK_POINTS;
		}
		return Math.max(0, stored);
	}

	/**
	 * Save format 2 -> 3. Points of 9 become points of 36 (x4, the same share of the way
	 * up: a sprint was 1 of 9 and is 4 of 36). Class S shows the full 36.
	 */
	public static int rescaledClassPoints(int classId, int stored) {
		if (classId >= RaceClass.S.getId()) {
			return RaceClass.POINTS_TO_PROMOTE;
		}
		int scale = RaceClass.POINTS_TO_PROMOTE / OLD_PROMOTE;
		return Math.min(RaceClass.POINTS_TO_PROMOTE - 1, Math.max(0, stored) * scale);
	}

	/** A bird's class points saved in {@code format}, brought up to the current ladder, one step at a time. */
	public static int convertedClassPoints(int format, int classId, int stored) {
		int points = stored;
		if (format < 2) {
			points = migratedClassPoints(classId, points);
		}
		if (format < 3) {
			points = rescaledClassPoints(classId, points);
		}
		return points;
	}

	/** Points still needed in this class. Class S is the top of the ladder. */
	public static int winsUntilPromote(RaceClass current, int classWins) {
		if (current == RaceClass.S) {
			return 0;
		}
		return Math.max(0, RaceClass.POINTS_TO_PROMOTE - classWins);
	}

	/** A sprint's first-place GP in the class. */
	public static int basePurse(RaceClass raceClass) {
		return switch (raceClass) {
			case C -> 6;
			case B -> 12;
			case A -> 24;
			case S -> 48;
		};
	}

	/**
	 * First-place GP on {@code track}: the class base scaled like the points
	 * (base x winPoints / 4, rounded), so a sprint pays the base and a grand prix
	 * pays by its length (10 points: 2.5 times the base).
	 */
	public static int purse(RaceTrack track) {
		return purse(track.getRaceClass(), winPoints(track));
	}

	static int purse(RaceClass raceClass, int winPoints) {
		return (int) Math.round(basePurse(raceClass) * winPoints / (double) REFERENCE_POINTS);
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

	/**
	 * How many ticks a remote chocobo may take to reach a newly received position.
	 * A steady gallop is about 1.35 blocks a tick, so an on-time packet keeps its own
	 * step count. A late packet glides instead of popping. A jump past 24 blocks is a
	 * real teleport and keeps the packet's steps.
	 */
	public static int remoteGlideSteps(int packetSteps, double distanceSquared) {
		int steps = Math.max(1, packetSteps);
		if (!Double.isFinite(distanceSquared) || distanceSquared > 24.0D * 24.0D) return steps;
		int glide = (int) Math.ceil(Math.sqrt(distanceSquared) / 1.35D);
		return Math.min(8, Math.max(steps, glide));
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

	/** Vanilla's downward contact epsilon must not put an ascending landing below its step. */
	public static double vehicleValidationY(double replayDeltaY) {
		return replayDeltaY > 0.0D ? replayDeltaY + 1.0E-6D : replayDeltaY;
	}

	/**
	 * Vanilla compares displacement from the server tick's first position with this
	 * allowance. Replace only that comparison with displacement from the last accepted
	 * packet: an ordered backlog is many small steps, not one huge movement. This can
	 * be negative when a packet doubles back; it is NOT a physical velocity squared.
	 * The unchanged comparison still rejects a single step beyond vanilla's limit.
	 */
	public static double vehiclePacketAllowance(double velocitySquared, double tickDistanceSquared, double stepDistanceSquared) {
		if (!Double.isFinite(tickDistanceSquared) || !Double.isFinite(stepDistanceSquared)) return velocitySquared;
		return velocitySquared + tickDistanceSquared - stepDistanceSquared;
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
	/**
	 * What a class field bird actually cruises at, before its +-5 % form: the
	 * profile's discipline x the grade it races at (born grade plus one step per
	 * 120 training points) x its speed training. The same stack a rider gets.
	 */
	public static double fieldPace(RaceClass raceClass) {
		int train = fieldTraining(raceClass.getId(), false);
		int grade = Math.min(4, Math.min(4, raceClass.getId() + 1) + (train * 4) / 120);
		return RacerProfile.of(raceClass, RacerProfile.Role.FIELD).cruise() * gradeSpeedMul(grade) * speedTrainingMul(train);
	}

	/**
	 * FF7's Teioh runs off the player's own bird. Teiyo cruises at a share of the
	 * best rider's cruise (B 0.92, A 1.00, S 1.08; callers pass it with land speed in,
	 * {@link #rivalPaceAbs}), never under {@link #rivalFloor} x the field. His 100-point
	 * stamina and intelligence and S discipline do the rest (RaceSimTest). Jolo runs 4 % under
	 * Teiyo. Set once at the grid, so a gap during the heat changes nothing.
	 */
	public static double rivalPace(RaceClass raceClass, boolean jolo, double riderPace, double fieldPace) {
		double share = switch (raceClass) {
			case C, B -> 0.92D;
			case A -> 1.00D;
			case S -> 1.08D;
		};
		double teiyo = Math.max(fieldPace * rivalFloor(raceClass), share * riderPace);
		return jolo ? teiyo * 0.96D : teiyo;
	}

	/** Jolo rides his class's best bird: Blue in B, White in A, Gold only in S. */
	public static ChocoboColor joloColor(RaceClass rc) {
		return switch (rc) {
			case C, B -> ChocoboColor.BLUE;
			case A -> ChocoboColor.WHITE;
			case S -> ChocoboColor.GOLD;
		};
	}

	public static int fieldTraining(int classId, boolean rival) {
		if (rival) {
			return 100;
		}
		return switch (Math.max(0, Math.min(3, classId))) {
			case 0 -> 34;
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

	/**
	 * Fun gates: the short gate runs the class's first sprint (course 3, one long lap), the
	 * long gate its first grand prix (course 0, five laps). Since the 48-course swap courses
	 * 0-2 of every class are grands prix and 3-5 sprints.
	 */
	public static int funGateCourse(boolean longCourse) {
		return longCourse ? 0 : 3;
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
	/**
	 * The Nether bird (Flame) and the End bird (Purple) do not race (Ahmi). Every
	 * other breed may enter a heat or a duel.
	 */
	public static boolean mayRace(tk.darrow.chocobosreborn.breed.ChocoboColor color) {
		return color != tk.darrow.chocobosreborn.breed.ChocoboColor.FLAME
				&& color != tk.darrow.chocobosreborn.breed.ChocoboColor.PURPLE;
	}

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

	/** One outbound trip at GO and one inbound trip at the finish, each bounded independently. */
	public static double lagCreditTicks(int startLatencyMs, int finishLatencyMs) {
		return (lagCreditTicks(startLatencyMs) + lagCreditTicks(finishLatencyMs)) * 0.5D;
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

	/** How far before a shortcut's fork a rider hears about it, in blocks. */
	public static final double SHORTCUT_WARN_BLOCKS = 45.0D;

	/** Blocks from progress {@code at} forward round the lap to {@code target}; negative just past it (within half a lap). */
	public static double blocksAhead(double at, double target, double lapLength) {
		double d = target - at;
		d -= Math.floor(d);
		if (d > 0.5D) {
			d -= 1.0D;
		}
		return d * lapLength;
	}

	/** A rescued bird waits this long on the road before it may go again. */
	public static final int RESCUE_HOLD_TICKS = 20;
	/** Off the road and this far from the last point on it: put back. */
	public static final double STRAY_BLOCKS = 24.0D;

	public static boolean strayedTooFar(double dx, double dz) {
		return dx * dx + dz * dz > STRAY_BLOCKS * STRAY_BLOCKS;
	}

	/** Whole seconds left on the off-road countdown, for the warning (never 0 while it shows). */
	public static int offRoadSecondsLeft(int offTicks) {
		return Math.max(1, (RaceLapProgress.OFF_LIMIT_TICKS - offTicks + 19) / 20);
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

	/**
	 * Whether a climbing colour pressed into a wall climbs it. In a race only a ridge
	 * feature is climbable: a climber that went up a rail, a pool wall, a stand or a post
	 * beside the road left the course, and the server's replay of the rider's vehicle moves
	 * (which does not climb) reset it every tick ("moved wrongly!") and froze it there.
	 */
	public static boolean mayClimb(boolean colourClimbs, boolean againstWall, boolean racing, boolean onRidge) {
		return colourClimbs && againstWall && (!racing || onRidge);
	}

	/** Highest step in a race: the road's hill steps are one block; a rail stands 1.5, a pool wall one above the road. */
	public static final float RACE_STEP = 1.0F;

	/**
	 * Step height a bird has: its colour's (2 for the climbing colours), capped at
	 * {@link #RACE_STEP} in a race so no bird walks up onto the rail and off the course.
	 */
	public static float stepHeight(boolean racing, float colourStep) {
		return racing ? Math.min(colourStep, RACE_STEP) : colourStep;
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
		RaceTrack track = null;
		for (RaceTrack t : RaceTrack.values()) {
			if (t.id().equals(trackName)) {
				track = t;
				break;
			}
		}
		return track == null ? "chocobo_dash" : raceLoopKey(track.theme());
	}

	/** The course loop by theme, so every course of a theme (a new one too) gets the same music. */
	public static String raceLoopKey(RaceTrack.Theme theme) {
		return switch (theme) {
			case SHORE -> "chocobo_race_gallop";
			case RIVER, SAVANNA -> "rune_dash";
			case CANYON, SNOW -> "gallop_of_adventure";
			case CAVERN, JUNGLE, NETHER, END, MUSHROOM -> "gallop_of_heroes";
			case SKYWAY, KEEP, DEEP_DARK -> "speed_of_the_dragon";
			case MEADOW, ORCHARD, FARMLAND -> "chocobo_dash";
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

	// ---- AI pass (2026-09-27): pace in movement-speed units, colour included.
	// A bird's land speed (Yellow 0.20 .. Gold 0.50) multiplies everything else, for a
	// rider and for the AI alike, so pace has to be compared with it in.

	/**
	 * The land speed a class field is paced round: the colour a rider usually brings
	 * to that class (C Yellow, B Green / Blue, A between White and Black, S a notch
	 * over Black: the legends). Gold is the S reward, so it is not the yardstick.
	 */
	public static double classLandSpeed(RaceClass rc) {
		return switch (rc) {
			case C -> 0.20D;
			case B -> 0.27D;
			case A -> 0.375D;
			case S -> 0.42D;
		};
	}

	/** Share of a field bird's colour edge (or deficit) over {@link #classLandSpeed} it keeps. */
	public static final double FIELD_COLOUR_SHARE = 0.25D;

	/**
	 * What a field bird of {@code color} runs at in this class instead of its raw land
	 * speed. Without this a Yellow in S ran at half a Black's pace and was never in the
	 * race; now the colours are the class favourites and outsiders by a few percent.
	 */
	public static double fieldLandSpeed(ChocoboColor color, RaceClass rc) {
		double ref = classLandSpeed(rc);
		return ref + FIELD_COLOUR_SHARE * (color.landSpeed() - ref);
	}

	/** A bird's cruise in movement-speed units: land speed x grade x speed training (a rider's stack). */
	public static double absolutePace(double landSpeed, int gradeRank, int speedStat) {
		return landSpeed * gradeSpeedMul(gradeRank) * speedTrainingMul(speedStat);
	}

	/** The class field's cruise in movement-speed units, before its +-5 % form. */
	public static double fieldPaceAbs(RaceClass rc) {
		return classLandSpeed(rc) * fieldPace(rc);
	}

	/**
	 * Teiyo's / Jolo's cruise in movement-speed units: {@link #rivalPace} fed with the
	 * best rider's own land speed x grade x training, so a rival paces a Yellow and a
	 * Gold alike instead of running a Black's land speed on a Yellow's multiplier.
	 */
	public static double rivalPaceAbs(RaceClass rc, boolean jolo, double riderPaceAbs) {
		return rivalPace(rc, jolo, riderPaceAbs, fieldPaceAbs(rc));
	}

	/**
	 * Teiyo's slowest cruise as a share of the field's. Under 1 low down: his
	 * 100-point stamina and intelligence are worth more against a B field (48 points)
	 * than an S one (92), and on this floor he should sit just ahead of the field's
	 * best bird, not a lap clear of it.
	 */
	public static double rivalFloor(RaceClass rc) {
		return switch (rc) {
			case C, B -> 0.86D;
			case A -> 0.98D;
			case S -> 1.03D;
		};
	}
}
