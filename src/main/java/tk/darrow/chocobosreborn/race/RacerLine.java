package tk.darrow.chocobosreborn.race;

/**
 * Pure lane rules for {@link RacerGoal}, kept free of Minecraft so they can be
 * unit-tested: where the racing line sits, how far a racer may swing before it
 * meets the rail, which side it passes on, where a finished bird parks, the
 * per-lap stumble roll and the boost-strip coin.
 * Lanes are blocks from the centre line, positive = inside the loop
 * ({@link RaceTrack#pointAtLane}).
 */
final class RacerLine {
	/** The inside racing line. */
	static final double INSIDE_LINE = 1.0D;
	/** Sideways swing when overtaking. */
	static final double PASS_OFFSET = 2.4D;
	/**
	 * Widest lane a racer aims for on the road band. The outside rail (and a
	 * corner's inside rail) stands on the kerb at ROAD_HALF + 1 and a bird is 1.75
	 * wide, so this keeps half a block of air between feathers and fence.
	 */
	static final double LANE_LIMIT = RaceTrack.ROAD_HALF - 1.0D;
	/** A finished bird coasts here, out of the racing line. */
	static final double PARK_LANE = -3.5D;
	/** Boost-strip lanes the AI can aim at: outside / centre / inside (see {@link RaceCourseLayout#boostLane}). */
	static final double[] BOOST_LANES = {-3.0D, 0.0D, 3.0D};

	private RacerLine() {
	}

	/** Keep an aimed lane on the road band, clear of the rails. */
	static double clampLane(double lane) {
		return Math.max(-LANE_LIMIT, Math.min(LANE_LIMIT, lane));
	}

	/**
	 * Which way to go round a bird at {@code otherLane}: +1 = inside, -1 = outside.
	 * Away from it, unless that side has no room left before the rail.
	 */
	static double passSide(double myLane, double otherLane) {
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
}
