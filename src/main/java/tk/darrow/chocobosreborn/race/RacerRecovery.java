package tk.darrow.chocobosreborn.race;

/**
 * An AI racer's way out of a stall, kept free of Minecraft so it can be unit-tested
 * ({@code RacerRecoveryTest}). It watches forward progress along the lap, not position:
 * a bird that rocks back and forth against a rail, or backs off and drives into the same
 * wall again, has made none.
 *
 * <ol>
 * <li>{@link Mode#RACE}: normal driving, until {@link #NO_PROGRESS_TICKS} go by without
 * {@link #PROGRESS_BLOCKS} of new ground.</li>
 * <li>{@link Mode#REAIM}: steer for the lane it should be on (the detour lane or the
 * road) just ahead of where it is, so it slides along the wall instead of into it, and
 * jump if it is pressed on something.</li>
 * <li>{@link Mode#BACK_OFF}: aim {@link #BACK_BLOCKS} back along the lap on that lane, to
 * come at the opening again from the road.</li>
 * <li>Then a fresh {@link #NO_PROGRESS_TICKS} of racing. After {@link #GIVE_UP_CYCLES}
 * cycles with no new ground it asks to be set back on the road ({@link #takeSetBack}),
 * the same set-back an off-road bird gets: it never rocks against a wall for good.</li>
 * </ol>
 * Harness evidence (A_CRYSTAL): "Vincent" (Blue) stood 50 s outside the rail just past the
 * ridge detour's rejoin, "Cait" (Yellow) past the water's; the old 3-block back-off led it
 * straight back into the same rail post.
 */
public final class RacerRecovery {
	public enum Mode { RACE, REAIM, BACK_OFF }

	/** Ticks without new ground before a racer tries something else (two seconds). */
	public static final int NO_PROGRESS_TICKS = 40;
	/** New ground, in blocks along the lap, that counts as progress. */
	public static final double PROGRESS_BLOCKS = 1.5D;
	public static final int REAIM_TICKS = 25;
	public static final int BACK_TICKS = 25;
	/** How far back a backing-off racer aims, blocks along the lap. */
	public static final double BACK_BLOCKS = 6.0D;
	/** How far ahead a re-aiming racer aims, blocks along the lap: a sideways move, not a run at the wall. */
	public static final double REAIM_BLOCKS = 1.5D;
	/** Stall cycles before the racer asks to be set back on the road. */
	public static final int GIVE_UP_CYCLES = 2;

	private double travelled;
	private double best;
	private int since;
	private int cycles;
	private Mode mode = Mode.RACE;
	private int modeTicks;
	private boolean setBack;

	/**
	 * One tick: {@code forwardBlocks} of lap progress since the last (negative going
	 * backwards). Returns what to do this tick.
	 */
	public Mode step(double forwardBlocks) {
		return step(forwardBlocks, false);
	}

	/**
	 * One tick, {@code climbing} when the bird is going up a ridge face: that is ground made
	 * too (an S ridge is five blocks, 40-odd ticks of climbing with no progress along the lap;
	 * turning it aside at the top dropped it back down the face).
	 */
	public Mode step(double forwardBlocks, boolean climbing) {
		return step(forwardBlocks, climbing, false);
	}

	/**
	 * One tick, {@code inTraffic} when the bird is held up by another racer (racers are solid to
	 * each other): that is not being stuck on the road, and backing off a wall would not help. The
	 * caller stops passing it on after a while, so two birds can never pin each other for good.
	 */
	public Mode step(double forwardBlocks, boolean climbing, boolean inTraffic) {
		travelled += forwardBlocks;
		if (climbing || inTraffic) {
			since = 0;
			mode = Mode.RACE;
			modeTicks = 0;
			return mode;
		}
		if (travelled > best + PROGRESS_BLOCKS) {
			best = travelled;
			since = 0;
			cycles = 0;
			mode = Mode.RACE;
			modeTicks = 0;
			return mode;
		}
		since++;
		switch (mode) {
			case RACE -> {
				if (since >= NO_PROGRESS_TICKS) {
					mode = Mode.REAIM;
					modeTicks = REAIM_TICKS;
				}
			}
			case REAIM -> {
				if (--modeTicks <= 0) {
					mode = Mode.BACK_OFF;
					modeTicks = BACK_TICKS;
				}
			}
			case BACK_OFF -> {
				if (--modeTicks <= 0) {
					mode = Mode.RACE;
					since = 0;
					if (++cycles >= GIVE_UP_CYCLES) {
						setBack = true;
						cycles = 0;
					}
				}
			}
		}
		return mode;
	}

	public Mode mode() {
		return mode;
	}

	/** Ticks since the last new ground. */
	public int stalledTicks() {
		return since;
	}

	/** True once when the racer has given up getting out on its own; the session sets it back. */
	public boolean takeSetBack() {
		boolean s = setBack;
		setBack = false;
		return s;
	}

	/** Set back on the road (or held on the grid): start counting afresh from here. */
	public void reset() {
		travelled = 0.0D;
		best = 0.0D;
		since = 0;
		cycles = 0;
		mode = Mode.RACE;
		modeTicks = 0;
		setBack = false;
	}
}
