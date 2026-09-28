package tk.darrow.chocobosreborn.race;

/**
 * Pure lane rules for {@link RacerGoal}, kept free of Minecraft so they can be
 * unit-tested: where the racing line sits, how far a racer may swing before it
 * meets the rail, which side it passes on, where a finished bird parks, the
 * per-lap stumble roll and the boost-strip coin.
 * Lanes are blocks from the centre line, positive = inside the loop
 * ({@link RaceTrack#pointAtLane}).
 */
public final class RacerLine {
	/** The inside racing line. */
	static final double INSIDE_LINE = 1.0D;
	/** Sideways swing when overtaking. */
	public static final double PASS_OFFSET = 2.4D;
	/**
	 * Widest lane a racer aims for on the road band. The outside rail (and a
	 * corner's inside rail) stands on the kerb at ROAD_HALF + 1 and a bird is 1.75
	 * wide, so this keeps half a block of air between feathers and fence.
	 */
	public static final double LANE_LIMIT = RaceTrack.ROAD_HALF - 1.0D;
	/** A finished bird coasts here, out of the racing line. */
	static final double PARK_LANE = -3.5D;
	/** Boost-strip lanes the AI can aim at: outside / centre / inside (see {@link RaceCourseLayout#boostLane}). */
	static final double[] BOOST_LANES = {-3.0D, 0.0D, 3.0D};

	private RacerLine() {
	}

	/** Keep an aimed lane on the road band, clear of the rails. */
	public static double clampLane(double lane) {
		return Math.max(-LANE_LIMIT, Math.min(LANE_LIMIT, lane));
	}

	/**
	 * Which way to go round a bird at {@code otherLane}: +1 = inside, -1 = outside.
	 * Away from it, unless that side has no room left before the rail.
	 */
	public static double passSide(double myLane, double otherLane) {
		double away = otherLane >= myLane ? -1.0D : 1.0D;
		double room = away < 0 ? myLane + LANE_LIMIT : LANE_LIMIT - myLane;
		return room >= PASS_OFFSET * 0.75D ? away : -away;
	}

	/** Chance of a stumble this tick: {@code perLap} spread over the blocks of a lap. */
	static double stumbleChance(double perLap, double movedBlocks, double lapLength) {
		if (perLap <= 0.0D || movedBlocks <= 0.0D) {
			return 0.0D;
		}
		return perLap * movedBlocks / Math.max(1.0D, lapLength);
	}

	/** Whether a bird goes for strip {@code index} on lap {@code lap}: repeatable per heat, {@code aim} of the time. */
	static boolean aimsForBoost(double aim, long seed, int index, int lap) {
		long h = seed ^ (index * 0x9E3779B97F4A7C15L) ^ (lap * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53 < aim;
	}

	// ------------------------------------------------------------ traffic
	// "skill" below is the profile's lineHold: C 0.35, B 0.60, A 0.80, S and the rivals 0.95.

	/** Most a leader drifts across to cover the inside from a chaser (x skill). */
	static final double DEFEND_MAX = 1.0D;
	/** A chaser this far inside of the leader (blocks) is lining up a pass; further in, it is already past. */
	static final double DEFEND_WINDOW = 3.5D;

	/** Signed blocks from progress {@code t} to {@code other} along the lap, the short way round (+ = ahead). */
	static double blocksAhead(double t, double other, double lapLength) {
		double d = other - t;
		d -= Math.floor(d + 0.5D);
		return d * lapLength;
	}

	/** Clearance kept from a bird alongside: the contact reach and a margin that grows with skill. */
	static double sideClear(double skill) {
		return RacerContact.REACH + 0.1D + 0.3D * skill;
	}

	/**
	 * How far ahead (blocks) a racer reacts to a bird in its lane: late for a C bird,
	 * early for an S one, and earlier the faster it is closing.
	 */
	static double trafficLook(double skill, double closing) {
		return 2.0D + 4.0D * skill + Math.max(0.0D, closing) * 25.0D * skill;
	}

	/** Chance a racer does not see the bird ahead this time and drives into its tail (C ~1 in 3, S 1 in 40). */
	static double trafficMiss(double skill) {
		return 0.5D * (1.0D - Math.max(0.0D, Math.min(1.0D, skill)));
	}

	/** An aimed lane runs into a bird at {@code otherLane}. */
	static boolean laneTaken(double aimLane, double otherLane, double clear) {
		return Math.abs(aimLane - otherLane) < clear;
	}

	/**
	 * Keep an aimed lane clear of a bird alongside at {@code otherLane}: never steer
	 * nearer to it than {@code clear} on its side of {@code myLane}.
	 */
	static double keepClear(double lane, double myLane, double otherLane, double clear) {
		if (Math.abs(otherLane - myLane) >= clear + 1.0D) {
			return lane;   // alongside but a lane over: nothing to squeeze
		}
		return otherLane >= myLane ? Math.min(lane, otherLane - clear) : Math.max(lane, otherLane + clear);
	}

	/**
	 * Pace scale when stuck behind a slower bird with no way round: its speed, a touch
	 * under when about to touch. Never a stop, never faster than the bird's own pace.
	 */
	static double followScale(double mySpeed, double otherSpeed, double blocksAhead) {
		if (mySpeed <= 1.0E-6D) {
			return 1.0D;
		}
		double r = otherSpeed / mySpeed * (blocksAhead < RacerContact.REACH + 0.6D ? 0.9D : 1.0D);
		return Math.max(0.5D, Math.min(1.0D, r));
	}

	/**
	 * Lane a leader takes to cover the inside from a chaser at {@code chaserLane}:
	 * only a chaser lining up on the inside (from {@link RacerContact#REACH} to
	 * {@link #DEFEND_WINDOW} blocks in), and at most {@link #DEFEND_MAX} x skill across.
	 * The caller holds it for a while and does not defend again straight away, so it
	 * is one move, never a weave.
	 */
	static double defendLane(double lane, double chaserLane, double skill) {
		double d = chaserLane - lane;
		if (d < RacerContact.REACH || d > DEFEND_WINDOW) {
			return lane;
		}
		return clampLane(lane + Math.min(DEFEND_MAX * skill, d));
	}
	// ------------------------------------------------------------ walls

	/** Ticks pressed on a wall without getting anywhere before a racer recovers, and how long it recovers. */
	public static final int STUCK_TICKS = 10, RECOVER_TICKS = 20;
	/** Horizontal blocks a tick below which a racer pressed on a wall counts as not moving. */
	public static final double STUCK_MOVE = 0.05D;

	/**
	 * One tick of the wall check the AI ({@link RacerGoal}) and the harness bot share: pressed
	 * into something (horizontal collision) on the ground and barely moving. Returns the new
	 * count; at {@link #STUCK_TICKS} the racer recovers (jumps, backs off, steers for the line).
	 * Sliding along a rail at speed, a jump and a clear road reset it.
	 */
	public static int stuckStep(int stuck, boolean horizontalCollision, boolean onGround, double moved) {
		return horizontalCollision && onGround && moved < STUCK_MOVE ? stuck + 1 : 0;
	}
}
