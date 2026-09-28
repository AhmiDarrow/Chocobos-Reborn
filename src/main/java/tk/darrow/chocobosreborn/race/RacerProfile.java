package tk.darrow.chocobosreborn.race;

/**
 * How a Square AI racer drives, per class. Pure numbers so the ladder can be
 * unit-tested (and simulated, see {@code RaceSimTest}): every axis gets harder
 * from C to S, and the named rivals (Teiyo, Jolo) drive at S discipline whatever
 * the class.
 *
 * <p>An AI bird spends the same stamina bar as a rider's bird: the same pool
 * ({@link RaceScoring#maxStamina} + stamina training), one point a dash tick
 * (intelligence skips some), one point back every three ticks off the dash, and
 * the same lock when the bar runs dry. The profile only decides <em>when</em> it
 * dashes and how hard that dash is.
 *
 * <ul>
 * <li>{@code cruise}: multiplier on the bird's own grade x speed-training stack.</li>
 * <li>{@code dash}: extra multiplier while dashing (a rider's is {@link RaceScoring#dashMul()}).</li>
 * <li>{@code reserve}: share of the bar kept back until the final push.</li>
 * <li>{@code reactionMin/Max}: ticks of hesitation after GO.</li>
 * <li>{@code wobble}: lane noise in blocks (sloppy driving).</li>
 * <li>{@code stumbleChancePerLap}: expected stumbles (a 0.55x hiccup for 20 ticks) per lap.</li>
 * <li>{@code lineHold}: how tightly it holds the inside racing line (0..1).</li>
 * <li>{@code brake}: the most it lifts for a hairpin (0.2 = 20 % off).</li>
 * <li>{@code boostAim}: chance it steers for a boost strip's lane.</li>
 * </ul>
 *
 * <p>No component looks at the player. The field holds its own pace.
 */
public record RacerProfile(double cruise, double dash, double reserve, int reactionMin, int reactionMax,
                           double wobble, double stumbleChancePerLap, double lineHold, double brake, double boostAim) {

	public enum Role { FIELD, TEIYO, JOLO }

	/** Per-racer, per-heat form: each bird runs at base x (1 +- VARIANCE), rivals too. */
	public static final double VARIANCE = 0.05D;
	/** Never dash the bar below this: an empty bar locks the dash until it is back at {@link RaceScoring#DASH_READY}. */
	public static final double FLOOR = 0.03D;
	/** After a burst, the bar climbs this far over the floor before the next one (no one-tick flicker). */
	public static final double BURST = 0.12D;
	/** A full bar recovers nothing, so a full bird dashes wherever it is. */
	public static final double FULL = 0.97D;
	/** Lift below this is a real corner: a bird does not burn stamina into it. */
	public static final double CORNER = 0.92D;
	/** Final push: the last lap of a grand prix, the last {@value} of a sprint. */
	public static final double SPRINT_PUSH = 0.50D;

	public static RacerProfile of(RaceClass raceClass, Role role) {
		RacerProfile base = switch (raceClass) {
			// cruise sits on the bird's own grade x speed training (and the class land speed,
			// RaceScoring.fieldLandSpeed), so a class bird is not a flat attribute.
			// Reaction is from GO: the riders see the same countdown, so no field jumps the lights.
			case C -> new RacerProfile(0.885D, 1.24D, 0.05D, 14, 26, 0.90D, 0.60D, 0.35D, 0.26D, 0.45D);
			case B -> new RacerProfile(0.885D, 1.32D, 0.10D, 11, 19, 0.60D, 0.40D, 0.60D, 0.21D, 0.65D);
			case A -> new RacerProfile(0.945D, 1.40D, 0.15D, 9, 14, 0.35D, 0.20D, 0.80D, 0.16D, 0.85D);
			case S -> new RacerProfile(0.99D, 1.48D, 0.20D, 7, 11, 0.15D, 0.05D, 0.95D, 0.11D, 0.95D);
		};
		return switch (role) {
			case FIELD -> base;
			// the rivals' pace comes from RaceScoring.rivalPace (paced off the rider's bird);
			// the profile gives them S discipline on top
			case TEIYO, JOLO -> rival(base);
		};
	}

	/** A named rival: S-class discipline in every class. Its pace is {@link RaceScoring#rivalPace}. */
	private static RacerProfile rival(RacerProfile base) {
		RacerProfile s = of(RaceClass.S, Role.FIELD);
		return new RacerProfile(base.cruise, s.dash, s.reserve, s.reactionMin, s.reactionMax, s.wobble,
				s.stumbleChancePerLap, s.lineHold, s.brake, s.boostAim);
	}

	/**
	 * This bird's temper for one heat, -1..1: a front-runner (+) keeps less back and
	 * pushes late, a closer (-) banks more and pushes from further out. It moves
	 * when a bird spends, not how much, so the field looks different without any
	 * bird getting a free lunch.
	 */
	public RacerProfile withTemper(double temper) {
		double tt = Math.max(-1.0D, Math.min(1.0D, temper));
		double r = Math.max(FLOOR, Math.min(0.5D, reserve - 0.08D * tt));
		return new RacerProfile(cruise, dash, r, reactionMin, reactionMax, wobble, stumbleChancePerLap,
				lineHold, brake, boostAim);
	}

	/** Chance the racer knows a bog when it sees one (45% + 55% × lineHold). */
	public static double bogSavvyChance(double lineHold) {
		return 0.45D + 0.55D * lineHold;
	}

	/**
	 * Speed factor for the bend ahead: {@code turn} is the heading change in radians
	 * over the next 18 blocks ({@link RaceTrack#turnAhead}). Sweepers are free, a
	 * hairpin costs the full {@link #brake}.
	 */
	public double cornerLift(double turn) {
		return 1.0D - brake * Math.min(1.0D, Math.max(0.0D, (turn - 0.35D) / 1.2D));
	}

	/** Final push: from here on the reserve is spent. {@code remainingLaps} = laps still to run (fraction). */
	public static boolean finalPush(double remainingLaps, int totalLaps) {
		return remainingLaps <= (totalLaps > 1 ? 1.0D : SPRINT_PUSH);
	}

	/**
	 * Whether to dash this tick. {@code energy} = bar 0..1, {@code dashingNow} = was
	 * dashing last tick, {@code straight} = a straight ahead, {@code lift} = this
	 * tick's {@link #cornerLift}, {@code push} = {@link #finalPush}.
	 * <ul>
	 * <li>never runs the bar dry: an empty bar locks the dash for 150 ticks;</li>
	 * <li>a bar with {@link #PLENTY} over what it keeps back is spent anywhere, bends
	 * included: a bar that sits high recovers nothing (a smart bird on a big pool
	 * dashes more than the straights hold);</li>
	 * <li>otherwise straights only, never into a braking corner, in bursts down to
	 * {@link #reserve}; the push spends the reserve.</li>
	 * </ul>
	 */
	public boolean wantsDash(double energy, boolean dashingNow, boolean straight, double lift, boolean push) {
		if (energy <= FLOOR) {
			return false;
		}
		double floor = push ? FLOOR : Math.max(reserve, FLOOR);
		if (energy >= Math.min(FULL, floor + PLENTY)) {
			return true;
		}
		if (lift < CORNER) {
			return false;
		}
		if (push) {
			return true;
		}
		if (!straight) {
			return false;
		}
		return dashingNow ? energy > floor : energy >= floor + BURST;
	}

	/** Bar over the keep-back that is spent anywhere, bends included. */
	public static final double PLENTY = 0.25D;
}
