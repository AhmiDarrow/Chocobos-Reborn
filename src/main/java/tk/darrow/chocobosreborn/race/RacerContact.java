package tk.darrow.chocobosreborn.race;

import java.util.List;

/**
 * Racer-to-racer contact ("kart bumps"), kept free of Minecraft so it can be
 * unit-tested and run the same way on both sides.
 *
 * <p>Each bird is resolved by whoever simulates it: the server for an AI bird, the
 * driving client for a rider's own bird. That side passes in its own body and what
 * it knows of the other solid racers near it, and applies the result to itself
 * only. Nobody moves another racer, so a rider's bird never gets a server
 * correction for a bump.
 *
 * <ul>
 * <li><b>Rear-end</b> (the other bird is in the cone ahead): the rear bird takes out
 * all of the closing speed, bounces back {@link #RESTITUTION} of it, and loses
 * {@link #LOSS_BASE} + {@link #LOSS_PER_SPEED} x closing speed of its pace (capped at
 * {@link #MAX_LOSS}), fading out over {@link Slow#TICKS}.</li>
 * <li><b>Front</b> (the other bird is in the cone behind): only a small nudge forward,
 * along its own heading ({@link #FRONT_NUDGE_MAX}); it keeps its line and pace.</li>
 * <li><b>Side</b>: both push themselves apart square to their own heading, so both
 * keep their pace ({@link #SIDE_BASE} + {@link #SIDE_PER_SPEED} x closing speed).</li>
 * <li><b>Head-on</b>: the other bird is ahead of both, so both take the rear-end.</li>
 * </ul>
 *
 * <p>Weight: {@link #BASE_WEIGHT} for every bird, plus {@link #DASH_WEIGHT} while
 * dashing and {@link #BOOST_WEIGHT} on a boost pad. A heavier bird shoves harder and
 * is shoved less (every effect scales with {@code 2 x other / (self + other)}, 1 at
 * equal weight). Class, grade and stats do not enter: their speed already shows in
 * the closing speed.
 *
 * <p>A shove never carries a bird past {@link RacerLine#LANE_LIMIT} (half a block of
 * air before the rail), and off the road band (a detour, a set-back) there is no
 * sideways shove at all. Contact is flat: birds more than {@link #HEIGHT} apart in
 * height do not touch.
 */
public final class RacerContact {
	/** Contact when two bird centres are closer than this (the bird box is 1.75 wide). */
	public static final double REACH = 1.6D;
	/** Height difference above which two birds pass over / under each other. */
	public static final double HEIGHT = 1.5D;
	public static final double BASE_WEIGHT = 1.0D;
	public static final double DASH_WEIGHT = 0.5D;
	public static final double BOOST_WEIGHT = 0.5D;
	/** cos of the half-angle of the ahead / behind cones (~50 degrees). */
	static final double CONE = 0.64D;
	/** Bounce: share of the closing speed returned. */
	static final double RESTITUTION = 0.3D;
	/** Closing slower than this (blocks a tick) is a rub, not a bump. */
	static final double MIN_CLOSING = 0.03D;
	public static final double LOSS_BASE = 0.10D;
	public static final double LOSS_PER_SPEED = 0.5D;
	public static final double MAX_LOSS = 0.25D;
	static final double FRONT_SHARE = 0.25D;
	public static final double FRONT_NUDGE_MAX = 0.06D;
	public static final double SIDE_BASE = 0.12D;
	public static final double SIDE_PER_SPEED = 0.6D;
	/** Overlap push-out per tick, and its cap. */
	static final double SEPARATION = 0.15D;
	static final double SEPARATION_MAX = 0.10D;
	/** Most velocity one tick of contact can add. */
	public static final double MAX_IMPULSE = 0.45D;
	/** Ground slip (0.6) x air drag (0.91): what a tick of ground travel keeps of the velocity. */
	static final double GROUND_KEEP = 0.546D;
	/** How far an impulse carries a bird on the ground before friction eats it: dv / (1 - keep). */
	static final double DRIFT = 1.0D / (1.0D - GROUND_KEEP);
	/** Lerp lag of a remote bird on a client (vanilla 3-step lerp, one packet a tick). */
	static final int LERP_TICKS = 2;
	static final int MAX_LEAD_TICKS = 10;
	/** A bird set back on the road stays a ghost this long after its hold, and until it is clear of everyone. */
	public static final int RESCUE_GHOST_TICKS = 40;

	private RacerContact() {
	}

	/** A bird as the resolving side knows it: feet position, horizontal velocity (blocks a tick), weight. */
	public record Body(double x, double y, double z, double vx, double vz, double weight, boolean solid) {
		/** Where this bird will be {@code ticks} from now at its present velocity. */
		public Body ahead(int ticks) {
			return ticks <= 0 ? this : new Body(x + vx * ticks, y, z + vz * ticks, vx, vz, weight, solid);
		}
	}

	/**
	 * The resolving bird's place across the road: {@code lane} blocks from the centre
	 * line (positive = inside, like {@link RaceTrack#pointAtLane}) and the unit
	 * vector that points to +lane. {@link #NONE}: no road known, no sideways limit.
	 */
	public record Guard(double lane, double latX, double latZ) {
		public static final Guard NONE = new Guard(Double.NaN, 0.0D, 0.0D);

		boolean known() {
			return !Double.isNaN(lane);
		}
	}

	public enum Kind { NONE, REAR, FRONT, SIDE }

	/** What contact does to the resolving bird this tick: a velocity change and a pace loss (0..MAX_LOSS). */
	public record Push(double dvx, double dvz, double loss, Kind kind) {
		public static final Push NONE = new Push(0.0D, 0.0D, 0.0D, Kind.NONE);

		public boolean any() {
			return kind != Kind.NONE;
		}
	}

	/** Solid for contact: in a live heat, not held (grid countdown, set-back) and not a ghost (finished, just set back). */
	public static boolean solid(boolean racing, boolean held, boolean ghost) {
		return racing && !held && !ghost;
	}

	public static double weight(boolean dashing, boolean boosting) {
		return BASE_WEIGHT + (dashing ? DASH_WEIGHT : 0.0D) + (boosting ? BOOST_WEIGHT : 0.0D);
	}

	/**
	 * How far a driving client looks ahead of the remote birds it sees. A remote bird
	 * is drawn a lerp plus half a round trip behind the server, and the server will
	 * see this client's bird half a round trip late: leading the others by the round
	 * trip plus the lerp compares the same moment the server does. Capped, so a
	 * spike does not throw the bird's idea of the field far down the road.
	 */
	public static int leadTicks(int rttMs) {
		int rtt = Math.max(0, Math.min(1000, rttMs));
		return Math.min(MAX_LEAD_TICKS, LERP_TICKS + Math.round(rtt / 50.0F));
	}

	/**
	 * Resolve contact for {@code self} against {@code others} this tick.
	 * {@code hx, hz} is the bird's heading (unit; its velocity, or its facing when it is
	 * nearly still).
	 */
	public static Push resolve(Body self, double hx, double hz, List<Body> others, Guard guard) {
		if (!self.solid() || others.isEmpty()) {
			return Push.NONE;
		}
		double hl = Math.hypot(hx, hz);
		if (hl < 1.0E-9D) {
			return Push.NONE;
		}
		hx /= hl;
		hz /= hl;
		double dvx = 0.0D, dvz = 0.0D, loss = 0.0D;
		Kind kind = Kind.NONE;
		for (Body o : others) {
			if (!o.solid() || Math.abs(o.y() - self.y()) > HEIGHT) {
				continue;
			}
			double dx = self.x() - o.x(), dz = self.z() - o.z();
			double dist = Math.hypot(dx, dz);
			if (dist >= REACH) {
				continue;
			}
			// n: from the other bird to this one
			double nx, nz;
			if (dist < 1.0E-4D) {
				nx = -hx;   // right on top of each other: treat it as ahead, back off
				nz = -hz;
			} else {
				nx = dx / dist;
				nz = dz / dist;
			}
			double overlap = REACH - dist;
			double share = o.weight() / Math.max(1.0E-6D, self.weight() + o.weight());
			double ratio = 2.0D * share;
			// closing > 0: the two are coming together
			double closing = (o.vx() - self.vx()) * nx + (o.vz() - self.vz()) * nz;
			double hit = Math.max(0.0D, closing);
			double ahead = -(nx * hx + nz * hz);
			double sep = Math.min(SEPARATION_MAX, SEPARATION * overlap);
			if (ahead > CONE) {
				// rear-end (or head-on): the bird in front barely gives way, so this one takes
				// out all of the closing speed and bounces back off it, and loses pace
				double j = (1.0D + RESTITUTION * ratio) * hit + sep;
				dvx += nx * j;
				dvz += nz * j;
				if (closing > MIN_CLOSING) {
					loss = Math.max(loss, ratio * (LOSS_BASE + LOSS_PER_SPEED * hit));
				}
				kind = Kind.REAR;
			} else if (ahead < -CONE) {
				// hit from behind: a small nudge along its own line, nothing else
				double j = Math.min(FRONT_NUDGE_MAX, FRONT_SHARE * (1.0D + RESTITUTION) * hit * share);
				dvx += hx * j;
				dvz += hz * j;
				if (kind == Kind.NONE) {
					kind = Kind.FRONT;
				}
			} else {
				// side by side: push apart square to the heading, keep the pace
				double along = nx * hx + nz * hz;
				double sx = nx - along * hx, sz = nz - along * hz;
				double sl = Math.hypot(sx, sz);
				if (sl < 1.0E-6D) {
					continue;
				}
				sx /= sl;
				sz /= sl;
				double lateral = (o.vx() - self.vx()) * sx + (o.vz() - self.vz()) * sz;
				double kick = lateral > MIN_CLOSING ? ratio * (SIDE_BASE + SIDE_PER_SPEED * lateral) : 0.0D;
				dvx += sx * (kick + sep);
				dvz += sz * (kick + sep);
				if (kind != Kind.REAR) {
					kind = Kind.SIDE;
				}
			}
		}
		if (kind == Kind.NONE) {
			return Push.NONE;
		}
		double mag = Math.hypot(dvx, dvz);
		if (mag > MAX_IMPULSE) {
			dvx *= MAX_IMPULSE / mag;
			dvz *= MAX_IMPULSE / mag;
		}
		if (guard.known()) {
			double side = dvx * guard.latX() + dvz * guard.latZ();
			double allowed = guardLateral(guard.lane(), side);
			dvx += guard.latX() * (allowed - side);
			dvz += guard.latZ() * (allowed - side);
		}
		return new Push(dvx, dvz, Math.min(MAX_LOSS, loss), kind);
	}

	/**
	 * The sideways part of a shove that may stand: none off the road band (a detour,
	 * a connector, a set-back), and never so much that the bird drifts past
	 * {@link RacerLine#LANE_LIMIT}. Inward (toward the centre) is always allowed.
	 */
	static double guardLateral(double lane, double side) {
		if (Math.abs(lane) > RaceTrack.ROAD_HALF) {
			return 0.0D;
		}
		if (side == 0.0D || Math.signum(side) != Math.signum(lane)) {
			return side;
		}
		double room = Math.max(0.0D, RacerLine.LANE_LIMIT - Math.abs(lane));
		double most = room / DRIFT;
		return Math.signum(side) * Math.min(Math.abs(side), most);
	}

	/**
	 * A bump's pace loss, fading out linearly over {@link #TICKS}. A second bump
	 * while one is fading takes the larger of the two, it does not stack.
	 */
	public static final class Slow {
		public static final int TICKS = 15;
		private double loss;
		private int left;

		public void hit(double amount) {
			double now = current();
			if (amount > now) {
				loss = Math.min(MAX_LOSS, amount);
				left = TICKS;
			}
		}

		/** Pace factor for this tick, 1 when clear. */
		public double factor() {
			return 1.0D - current();
		}

		/** Advance one tick. */
		public void tick() {
			if (left > 0) {
				left--;
			}
		}

		public void clear() {
			left = 0;
			loss = 0.0D;
		}

		private double current() {
			return left <= 0 ? 0.0D : loss * left / TICKS;
		}
	}
}
