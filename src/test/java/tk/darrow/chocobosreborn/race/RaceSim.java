package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;
import java.util.List;

import tk.darrow.chocobosreborn.breed.ChocoboColor;
import tk.darrow.chocobosreborn.breed.ChocoboGreen;

/**
 * A pure-Java heat simulator for the ladder: no Minecraft classes, one bird at a
 * time over the real course geometry ({@link RaceTrack}), with the real stamina
 * rules ({@link RaceScoring}: pool, one point a dash tick, intelligence skips, one
 * point back every three ticks, the lock at empty, dash / empty multipliers, boost
 * strips) and the AI's real decisions ({@link RacerProfile#wantsDash},
 * {@link RacerProfile#cornerLift}).
 *
 * <p>Model, kept simple on purpose:
 * <ul>
 * <li>speed in blocks a tick = {@link #K} x movement speed x multipliers (vanilla ground
 * physics: acceleration / (1 - 0.546) on 0.6-friction blocks);</li>
 * <li>corners: the AI lifts by its profile's {@code cornerLift}; a rider by
 * {@link #RIDER_BRAKE} (a human line through a hairpin is not perfect either);</li>
 * <li>a bird that does not suit a feature takes its detour ({@link RaceTrack#detourCost}
 * extra blocks over the connector span); a climber pays {@link #CLIMB_TICKS_PER_BLOCK}
 * per block of ridge; bogs are gone round by everyone;</li>
 * <li>stumbles are an expected-value drag; boost strips are hit by a rider always and by
 * the AI at its {@code boostAim} (a fixed pattern, so a run is repeatable).</li>
 * </ul>
 */
final class RaceSim {
	static final double K = 2.2D;
	static final int RIDER_REACTION = 6;
	static final double RIDER_BRAKE = 0.10D;
	static final double CLIMB_TICKS_PER_BLOCK = 5.0D;

	enum Drive {
		/** The AI's own rules. */
		AI,
		/** A rider who dashes the straights, never runs dry into the lock, empties the bar by the line. */
		SMART,
		/** A rider who never dashes (but still steers over the boost strips). */
		CRUISE
	}

	/** One lap of a course sampled per block. */
	static final class Course {
		final RaceTrack track;
		final int laps;
		final double lap;
		final int n;
		final double[] turn;
		final boolean[] straight;

		Course(RaceTrack track) {
			this.track = track;
			this.laps = track.getLaps();
			this.lap = track.lapLength();
			this.n = (int) Math.ceil(lap);
			this.turn = new double[n];
			this.straight = new boolean[n];
			for (int i = 0; i < n; i++) {
				double t = i / (double) n;
				turn[i] = track.turnAhead(t, 18.0D);
				straight[i] = track.isStraight(t);
			}
		}

		private final java.util.Map<RaceTrack.Feature, Double> costs = new java.util.HashMap<>();

		double cost(RaceTrack.Feature f) {
			return costs.computeIfAbsent(f, track::detourCost);
		}

		int idx(double t) {
			return Math.floorMod((int) (t * n), n);
		}
	}

	/** A bird: colour, born grade and training. */
	record Bird(ChocoboColor color, int bornRank, int speed, int stamina, int intel, int coop) {
		int grade() {
			return ChocoboGreen.gradeFromTraining(bornRank, speed + stamina + intel + coop);
		}

		int pool(RaceClass rc) {
			return RaceScoring.maxStamina(grade(), rc.getId(), false) + stamina;
		}

		double pace(double land) {
			return RaceScoring.absolutePace(land, grade(), speed);
		}
	}

	/** One entrant: the bird, how it is driven, and its cruise in movement-speed units. */
	record Entrant(Bird bird, Drive drive, RacerProfile profile, double cruiseAbs, int reaction, Legacy legacy) {
		Entrant(Bird bird, Drive drive, RacerProfile profile, double cruiseAbs, int reaction) {
			this(bird, drive, profile, cruiseAbs, reaction, null);
		}

		static Entrant rider(Bird b, Drive d) {
			return new Entrant(b, d, null, b.pace(b.color().landSpeed()), RIDER_REACTION);
		}

		/** A field bird of this class and colour at {@code form} (-0.05..0.05). */
		static Entrant field(RaceClass rc, ChocoboColor color, double form) {
			int train = RaceScoring.fieldTraining(rc.getId(), false);
			Bird b = new Bird(color, Math.min(4, rc.getId() + 1), train, train, train, train);
			RacerProfile p = RacerProfile.of(rc, RacerProfile.Role.FIELD);
			double land = RaceScoring.fieldLandSpeed(color, rc);
			return new Entrant(b, Drive.AI, p, b.pace(land) * p.cruise() * (1.0D + form),
					(p.reactionMin() + p.reactionMax()) / 2);
		}

		/** Teiyo (or Jolo) against a rider whose own cruise is {@code riderPaceAbs}. */
		static Entrant rival(RaceClass rc, boolean jolo, double riderPaceAbs, double form) {
			ChocoboColor color = jolo ? RaceScoring.joloColor(rc) : ChocoboColor.BLACK;
			Bird b = new Bird(color, Math.min(4, rc.getId() + 1), 100, 100, 100, 100);
			RacerProfile p = RacerProfile.of(rc, jolo ? RacerProfile.Role.JOLO : RacerProfile.Role.TEIYO);
			return new Entrant(b, Drive.AI, p, RaceScoring.rivalPaceAbs(rc, jolo, riderPaceAbs) * (1.0D + form),
					(p.reactionMin() + p.reactionMax()) / 2);
		}
	}

	/**
	 * The 1.0.18 AI, for the before column: threshold + reserve, the whole sprint
	 * treated as the last lap (dash anywhere above 2 %), 30 % hairpin brake, stumbles
	 * on a 900-tick lap, the inside line missing the outside-lane strips (1 in 3).
	 */
	record Legacy(double dash, double threshold, double save, double stumble) {
		static final double BRAKE = 0.30D;

		static Legacy of(RaceClass rc) {
			return switch (rc) {
				case C -> new Legacy(1.22D, 0.40D, 0.00D, 0.70D);
				case B -> new Legacy(1.27D, 0.42D, 0.20D, 0.50D);
				case A -> new Legacy(1.32D, 0.45D, 0.35D, 0.20D);
				case S -> new Legacy(1.36D, 0.30D, 0.45D, 0.05D);
			};
		}

		static double cruise(RaceClass rc) {
			return switch (rc) {
				case C -> 0.880D;
				case B -> 0.882D;
				case A -> 0.887D;
				case S -> 0.923D;
			};
		}

		static int reaction(RaceClass rc) {
			return switch (rc) {
				case C -> 20;
				case B -> 16;
				case A -> 12;
				case S -> 9;
			};
		}

		boolean wants(double energy, boolean straight, boolean lastLap) {
			if (energy <= 0.0D) return false;
			if (lastLap) return energy > 0.02D;
			if (energy < threshold) return false;
			if (energy <= save) return false;
			return straight;
		}

		/** Old field bird: its raw colour land speed, old cruise. */
		static Entrant field(RaceClass rc, ChocoboColor color, double form) {
			int train = RaceScoring.fieldTraining(rc.getId(), false);
			Bird b = new Bird(color, Math.min(4, rc.getId() + 1), train, train, train, train);
			return new Entrant(b, Drive.AI, null, b.pace(color.landSpeed()) * cruise(rc) * (1.0D + form), reaction(rc), of(rc));
		}

		/** Old rival: rivalPace fed the rider's grade x training only, run on the rival's own land speed. */
		static Entrant rival(RaceClass rc, boolean jolo, Bird rider, double form) {
			ChocoboColor color = jolo ? RaceScoring.joloColor(rc) : ChocoboColor.BLACK;
			Bird b = new Bird(color, Math.min(4, rc.getId() + 1), 100, 100, 100, 100);
			double riderPace = RaceScoring.gradeSpeedMul(rider.grade()) * RaceScoring.speedTrainingMul(rider.speed());
			double stack = RaceScoring.rivalPace(rc, jolo, riderPace, RaceScoring.fieldPace(rc));
			Legacy s = of(RaceClass.S);
			return new Entrant(b, Drive.AI, null, color.landSpeed() * stack * (1.0D + form), 9,
					new Legacy(Math.max(of(rc).dash, s.dash), s.threshold, s.save, s.stumble));
		}
	}

	/** Diagnostics of the last {@link #heatTicks} run: bar left at the line, share of ticks dashing. */
	static double lastLeftover, lastDashShare;

	private RaceSim() {
	}

	/** Heat time in ticks. */
	static double heatTicks(Course c, Entrant e) {
		RaceClass rc = c.track.getRaceClass();
		Bird b = e.bird();
		RacerProfile p = e.profile();
		double pool = b.pool(rc);
		double stamina = pool;
		boolean locked = false, dashing = false;
		double drain = 1.0D - 0.5D * Math.min(100, b.intel()) / 100.0D;   // intelSkipsDashDrain, expected
		int boostLeft = 0;
		double boost = RaceScoring.boostPower(b.intel());
		double total = c.laps * c.lap;
		double pos = 0.0D;
		int tick = 0;
		int dashTicks = 0;
		double stall = 0.0D;
		List<RaceTrack.Feature> strips = new ArrayList<>();
		for (RaceTrack.Feature f : c.track.features()) {
			if (f.type() == RaceTrack.Feature.Type.BOOST) strips.add(f);
		}
		RaceTrack.Feature lastRidge = null;
		int lastRidgeLap = -1;
		while (pos < total && tick < 200_000) {
			tick++;
			if (tick <= e.reaction()) continue;
			if (stall > 0.0D) {
				stall -= 1.0D;
				dashing = false;
				if (stamina < pool) stamina = Math.min(pool, stamina + 1.0D / 3.0D);
				continue;
			}
			int lapIdx = (int) (pos / c.lap);
			double t = pos / c.lap - lapIdx;
			int i = c.idx(t);
			Legacy old = e.legacy();
			double lift = old != null ? 1.0D - Legacy.BRAKE * Math.min(1.0D, Math.max(0.0D, (c.turn[i] - 0.35D) / 1.2D))
					: e.drive() == Drive.AI ? p.cornerLift(c.turn[i]) : 1.0D - RIDER_BRAKE * Math.min(1.0D, Math.max(0.0D, (c.turn[i] - 0.35D) / 1.2D));
			double energy = stamina / pool;
			boolean push = RacerProfile.finalPush(c.laps - pos / c.lap, c.laps);
			if (locked && stamina >= RaceScoring.DASH_READY) locked = false;
			boolean want = switch (e.drive()) {
				case AI -> old != null ? old.wants(energy, c.straight[i], lapIdx >= c.laps - 1)
						: p.wantsDash(energy, dashing, c.straight[i], lift, push);
				case SMART -> smartDash(stamina, pool, c.straight[i], lift, push);
				case CRUISE -> false;
			};
			dashing = want && !locked && stamina >= 1.0D;
			if (dashing) dashTicks++;
			if (dashing) {
				stamina -= drain;
				if (stamina < 1.0D - 1e-9) {
					stamina = 0.0D;
					locked = true;
				}
			} else if (stamina < pool) {
				stamina = Math.min(pool, stamina + 1.0D / 3.0D);
			}
			double mul = e.cruiseAbs();
			if (dashing) {
				mul *= old != null ? old.dash() : e.drive() == Drive.AI ? p.dash() : RaceScoring.dashMul();
			} else if (stamina <= 0.0D) {
				mul *= RaceScoring.emptyStaminaMul();
			}
			if (boostLeft > 0) {
				mul *= 1.0D + boost;
				boostLeft--;
			}
			mul *= lift;
			double v = K * mul;
			if (old != null) {
				v *= 1.0D - old.stumble() / 900.0D * 20.0D * 0.45D;
			} else if (e.drive() == Drive.AI) {
				// expected stumbles: perLap x (share of a lap this tick) x 20 ticks at 0.55
				v *= 1.0D - p.stumbleChancePerLap() * 20.0D * 0.45D * v / c.lap;
			}
			// terrain
			double progress = v;
			for (RaceTrack.Feature f : c.track.terrainFeatures()) {
				boolean suits = f.suits(b.color());
				if (suits && f.type() == RaceTrack.Feature.Type.RIDGE && f.covers(t)
						&& (f != lastRidge || lastRidgeLap != lapIdx)) {
					lastRidge = f;
					lastRidgeLap = lapIdx;
					stall += c.track.ridgeHeight() * CLIMB_TICKS_PER_BLOCK;
				}
				if (suits) continue;
				double from = f.start() - RaceTrack.DETOUR_CONNECT, to = f.end() + RaceTrack.DETOUR_CONNECT;
				if (t >= from && t <= to) {
					double direct = (to - from) * c.lap;
					progress = v * direct / (direct + Math.max(0.0D, c.cost(f)));
				}
			}
			double next = pos + progress;
			for (int s = 0; s < strips.size(); s++) {
				double at = lapIdx * c.lap + strips.get(s).start() * c.lap;
				if (pos < at && next >= at && hitsStrip(e, c.track.features().indexOf(strips.get(s)), lapIdx)) {
					boostLeft = RaceScoring.boostTicks(b.intel());
				}
			}
			pos = next;
		}
		lastLeftover = stamina / pool;
		lastDashShare = dashTicks / (double) tick;
		return tick;
	}

	/** {@code slot} = the strip's index in the feature list; {@code slot % 3 == 0} is the outside lane the inside line misses. */
	private static boolean hitsStrip(Entrant e, int slot, int lap) {
		if (e.drive() != Drive.AI) return true;
		boolean onLine = slot % 3 != 0;
		if (e.legacy() != null) return onLine;
		double pattern = ((slot * 7 + lap * 3) * 0.61803398875D) % 1.0D;
		return onLine || pattern < e.profile().boostAim();
	}

	/**
	 * A good rider: holds the dash anywhere while the bar is over a quarter, the
	 * straights below that, never runs dry into the lock (the bar sits low all race,
	 * so nothing is left at the line).
	 */
	static boolean smartDash(double stamina, double pool, boolean straight, double lift, boolean push) {
		if (stamina < 2.0D) return false;
		if (stamina >= pool * RacerProfile.PLENTY) return true;
		if (lift < RacerProfile.CORNER) return false;
		return push || straight;
	}

	/** Mean heat time over every course of the class. */
	static double classTicks(RaceClass rc, java.util.function.Function<Course, Entrant> who) {
		double sum = 0.0D;
		int n = 0;
		for (RaceTrack tr : RaceTrack.ofClass(rc)) {
			Course c = course(tr);
			sum += heatTicks(c, who.apply(c));
			n++;
		}
		return sum / n;
	}

	private static final java.util.Map<RaceTrack, Course> COURSES = new java.util.EnumMap<>(RaceTrack.class);

	static synchronized Course course(RaceTrack tr) {
		return COURSES.computeIfAbsent(tr, Course::new);
	}
}
