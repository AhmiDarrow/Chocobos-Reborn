package tk.darrow.chocobosreborn.net;

import java.util.ArrayDeque;

/**
 * Which server tick a client should be showing the race at, right now.
 *
 * <p>Every {@link RaceMovePayloads.Frame} says which server tick it was taken on. Its arrival
 * time says how far behind the server this client sees it: {@code serverTick - now}, in ticks
 * of wall time. The frames that arrive fastest set the reference (the largest of those
 * samples over the last {@link #WINDOW} ticks); how far typical frames arrive behind that
 * reference is the jitter. The field is shown {@link #delay()} ticks behind the fastest
 * frames, enough to ride out the jitter with a frame to spare, and never less than
 * {@link #MIN_DELAY} so there is always a frame on each side to interpolate between.
 *
 * <p>The shown tick steps exactly one per client tick, so it moves in lockstep with the
 * client's own frame interpolation (entities are drawn between their last two tick positions),
 * plus a correction toward its target of at most {@link #MAX_SLEW} ahead (5 % faster than real
 * time: not visible) or {@link #MAX_EASE} back (frames suddenly arriving later must not starve
 * the buffer). A change in the connection never shows as a jump. A target more
 * than {@link #SNAP} ticks away (the first frame, or the server stalling for seconds) is taken
 * at once.
 *
 * <p>Pure arithmetic, no Minecraft types: unit-tested in {@code PlayoutClockTest}.
 */
public final class PlayoutClock {
	/** Wall-time ticks a reference sample is remembered: long enough to span jitter, short enough to follow a slowing server. */
	public static final double WINDOW = 40.0D;
	public static final double MIN_DELAY = 1.5D;
	public static final double MAX_DELAY = 6.0D;
	public static final double MAX_SLEW = 0.05D;
	/** Easing back is allowed faster than catching up: a starved buffer (frames suddenly later) is worse than a brief slow-down. */
	public static final double MAX_EASE = 0.2D;
	public static final double SNAP = 10.0D;

	private final ArrayDeque<double[]> samples = new ArrayDeque<>();
	private double reference = Double.NaN;
	/** Smoothed lateness of a frame behind the reference: rises fast, falls slowly. */
	private double lateness = 0.5D;
	/** The server tick shown on the last client tick. */
	private double shown = Double.NaN;

	/** A frame taken on {@code serverTick} arrived at wall time {@code nowTicks}. */
	public void observe(double nowTicks, int serverTick) {
		double sample = serverTick - nowTicks;
		samples.addLast(new double[] { nowTicks, sample });
		while (!samples.isEmpty() && samples.peekFirst()[0] < nowTicks - WINDOW) {
			samples.removeFirst();
		}
		double best = Double.NEGATIVE_INFINITY;
		for (double[] s : samples) {
			best = Math.max(best, s[1]);
		}
		reference = best;
		double late = Math.max(0.0D, best - sample);
		lateness += (late - lateness) * (late > lateness ? 0.25D : 0.02D);
	}

	public boolean ready() {
		return !Double.isNaN(reference);
	}

	/** Ticks behind the fastest frames the field is shown at. */
	public double delay() {
		return Math.max(MIN_DELAY, Math.min(MAX_DELAY, 1.0D + 2.0D * lateness));
	}

	/**
	 * The server tick to show on this client tick, at wall time {@code nowTicks}. Call exactly
	 * once per client tick: the shown tick advances by one, corrected at most {@link #MAX_SLEW}
	 * toward its target.
	 */
	public double advance(double nowTicks) {
		if (!ready()) {
			return Double.NaN;
		}
		double goal = nowTicks + reference - delay();
		double next = shown + 1.0D;
		if (Double.isNaN(shown) || Math.abs(goal - next) > SNAP) {
			shown = goal;
		} else {
			double error = goal - next;
			shown = next + (error < 0.0D ? Math.max(-MAX_EASE, error) : Math.min(MAX_SLEW, error));
		}
		return shown;
	}

	/**
	 * How many ticks behind the fastest frames the field is shown at wall time {@code nowTicks}:
	 * what a rider's contact must lead the remote birds by, on top of its round trip.
	 */
	public double behind(double nowTicks) {
		return ready() && !Double.isNaN(shown) ? Math.max(0.0D, nowTicks + reference - shown) : 0.0D;
	}

	public void reset() {
		samples.clear();
		reference = Double.NaN;
		lateness = 0.5D;
		shown = Double.NaN;
	}
}
