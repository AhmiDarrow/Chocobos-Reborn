package tk.darrow.chocobosreborn.race;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.world.entity.ai.goal.Goal;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;

/**
 * Square AI racer. Drives a {@link RacerProfile} on top of the bird's own
 * training: speed, stamina, intelligence and cooperation (handling) use the
 * same formulas as a rider, and the dash spends the bird's own stamina bar.
 * The profile is discipline (when to dash, corners, line, wobble, stumbles).
 * The session sets {@link #speed} (this heat's form), {@link #paceScale},
 * {@link #lapsDone} and toggles {@link #running}. Nothing here looks at the
 * player: the field holds its own pace.
 */
public class RacerGoal extends Goal {
	private static final int STUMBLE_TICKS = 20;
	/** Look this far ahead for a boost strip to line up with. */
	private static final double BOOST_LOOK = 24.0D;

	private final ChocoboEntity bird;
	private final RaceTrack track;
	private final RaceCourseLayout layout;
	private final double startLane;
	/** This heat's form, 1 +- {@link RacerProfile#VARIANCE}. */
	public double speed = 1.0D;
	/**
	 * Scales this bird's own stack onto the pace it should run: the class land
	 * speed for the field ({@link RaceScoring#fieldLandSpeed}), the rider's bird for
	 * Teiyo / Jolo ({@link RaceScoring#rivalPaceAbs}).
	 */
	public double paceScale = 1.0D;
	public boolean running;
	public RacerProfile profile;
	public int lapsDone;
	public int totalLaps = 3;

	private final double noiseSeed;
	private final double lineSpread;
	/** Knows a bog when it sees one (sloppy classes sometimes drive straight in). */
	private final boolean bogSavvy;
	private final long boostSeed;
	private double energy = 1.0D;
	private int reaction = -1;
	private int ticks;
	private int stumble;
	private int passTicks;
	private double passSide;
	private boolean dashing;
	/** Boost strip being lined up for (its feature index), and whether this bird goes for it. */
	private int boostStrip = -1;
	private boolean boostAim;
	/** Last progress on this lap, so the next tick searches that section. */
	private double along = -1.0D;
	private double lastX = Double.NaN, lastZ;
	private List<ChocoboEntity> nearby = List.of();

	public RacerGoal(ChocoboEntity bird, RaceTrack track, double lane, RacerProfile profile) {
		this.bird = bird;
		this.track = track;
		this.startLane = lane;
		this.layout = RaceCourseLayout.of(track);
		// temper: front-runner or closer for this heat (RacerProfile#withTemper)
		this.profile = profile.withTemper(bird.getRandom().nextDouble() * 2.0D - 1.0D);
		this.noiseSeed = bird.getRandom().nextDouble() * Math.PI * 2.0D;
		this.lineSpread = (bird.getRandom().nextDouble() - 0.5D) * 1.2D;
		this.bogSavvy = bird.getRandom().nextDouble() < RacerProfile.bogSavvyChance(profile.lineHold());
		this.boostSeed = bird.getRandom().nextLong();
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		return running;
	}

	@Override
	public boolean canContinueToUse() {
		return running;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	public boolean dashing() {
		return dashing;
	}

	public double energy() {
		return energy;
	}

	@Override
	public void start() {
		reaction = profile.reactionMin() + bird.getRandom().nextInt(Math.max(1, profile.reactionMax() - profile.reactionMin() + 1));
		ticks = 0;
	}

	@Override
	public void tick() {
		ticks++;
		if (ticks <= reaction) {
			bird.getMoveControl().setWantedPosition(bird.getX(), bird.getY(), bird.getZ(), 0.0D);
			return;
		}
		double t = track.progressAt(bird.getX(), bird.getZ(), along);
		along = t;
		boolean finished = lapsDone >= totalLaps;
		double turn = track.turnAhead(t, 18.0D);
		double lift = profile.cornerLift(turn);

		// --- stamina and dashing (same pool, lockout and intel skip as a rider)
		int maxSt = Math.max(1, bird.maxStamina());
		int st = bird.stamina();
		energy = st / (double) maxSt;
		boolean locked = RaceScoring.stillDashLocked(bird.dashLocked(), st);
		if (bird.dashLocked() && !locked) {
			bird.setDashLocked(false);
		}
		boolean push = RacerProfile.finalPush(totalLaps - (lapsDone + t), totalLaps);
		dashing = !finished && !locked && st > 0
				&& profile.wantsDash(energy, dashing, track.isStraight(t), lift, push);
		if (dashing) {
			boolean skip = RaceScoring.intelSkipsDashDrain(bird.intelligenceStat(), bird.tickCount,
					bird.getRandom().nextInt(100));
			if (!skip) {
				bird.setStamina(st - 1);
				if (st - 1 <= 0) {
					bird.setDashLocked(true);
				}
			}
		} else if (st < maxSt && bird.tickCount % 3 == 0) {
			bird.setStamina(st + 1);
		}
		energy = bird.stamina() / (double) maxSt;

		// --- racing line: start lane blends into the inside line over the first stretch
		int coop = bird.cooperationStat();
		double blend = Math.min(1.0D, ticks / 140.0D) * profile.lineHold() * RaceScoring.handlingLineMul(coop);
		double wobble = profile.wobble() * RaceScoring.handlingWobbleMul(coop)
				* Math.sin(bird.tickCount * 0.05D + noiseSeed);
		double lane = startLane + (RacerLine.INSIDE_LINE + lineSpread - startLane) * blend + wobble;
		// --- boost strips cover one lane each: line up for the next one if this bird goes for it
		double boostLane = boostLane(t);
		boolean onBoostLine = !Double.isNaN(boostLane);
		if (onBoostLine) {
			lane = boostLane;
		}
		// --- overtaking: a slower bird just ahead and in our lane -> go round it, on the side with room
		if (ticks % 5 == 0) {
			nearby = bird.level().getEntitiesOfClass(ChocoboEntity.class, bird.getBoundingBox().inflate(3.5D, 1.0D, 3.5D),
					e -> e != bird && e.racing());
		}
		if (passTicks > 0) {
			passTicks--;
		} else if (!finished) {
			for (ChocoboEntity other : nearby) {
				double dt = track.progressAt(other.getX(), other.getZ(), t) - t;
				if (dt < 0.0D) {
					dt += 1.0D;
				}
				double otherLane = laneOf(t, other.getX(), other.getZ());
				if (dt < 0.03D && Math.abs(otherLane - lane) < 1.9D
						&& other.getDeltaMovement().horizontalDistanceSqr() < bird.getDeltaMovement().horizontalDistanceSqr()) {
					passSide = RacerLine.passSide(lane, otherLane);
					passTicks = 40;
					break;
				}
			}
		}
		if (passTicks > 0 && !onBoostLine) {
			lane += passSide * RacerLine.PASS_OFFSET;
		}
		if (finished) {
			lane = RacerLine.PARK_LANE;   // out of the racing line: the riders behind are still racing
		}
		lane = RacerLine.clampLane(lane);

		// --- stumbles (sloppy classes): the profile rate is per lap, so roll per block run
		double moved = Double.isNaN(lastX) ? 0.0D : Math.hypot(bird.getX() - lastX, bird.getZ() - lastZ);
		lastX = bird.getX();
		lastZ = bird.getZ();
		if (stumble > 0) {
			stumble--;
		} else if (!finished && bird.getRandom().nextDouble() < RacerLine.stumbleChance(profile.stumbleChancePerLap(), moved, track.lapLength())) {
			stumble = STUMBLE_TICKS;
		}

		// A fraction of a long lap can span an entire bend, cutting its inside
		// rail and invalidating the lap even when the bird never stops moving.
		double ahead = Math.min(0.012D + 0.006D * Math.max(0.0D, speed - 1.0D), 8.0D / track.lapLength());
		double directLane = lane;
		if (Math.abs(track.detourLaneAt(t, lane, bird.color(), bogSavvy) - lane) > 1e-6
				|| Math.abs(track.detourLaneAt(t + ahead, lane, bird.color(), bogSavvy) - lane) > 1e-6) {
			// A whole connector of lookahead cuts the diagonal's corner into the
			// rail/ridge, especially at S-class pace. Follow its local tangent.
			ahead = Math.min(ahead, 4.0D / track.lapLength());
		}
		// The old fixed early swerve aimed through the rail before the detour
		// entrance existed. Follow the same connector ramp the builder lays down.
		lane = track.detourLaneAt(t + ahead, lane, bird.color(), bogSavvy);
		RacePoint target = track.pointAtLane((t + ahead) % 1.0D, lane);
		// terrain is physical (water slows swimmers, ridges block non-climbers); no attribute fudge.
		// Same stack a rider gets: grade, then training, once each. The profile
		// cruise sits on top of that, so a class bird is not a flat attribute.
		double mul = speed * paceScale * profile.cruise()
				* RaceScoring.gradeSpeedMul(bird.grade().getRank())
				* RaceScoring.speedTrainingMul(bird.speedStat());
		if (dashing) {
			mul *= profile.dash();
		} else if (bird.stamina() <= 0) {
			mul *= RaceScoring.emptyStaminaMul();
		}
		if (stumble > 0) {
			mul *= 0.55D;
		}
		// lift for the corners: hairpins and chicanes are taken slower, sweepers barely
		mul *= lift;
		if (finished) {
			mul *= 0.6D;   // finished: coast
		}
		bird.getMoveControl().setWantedPosition(target.x(), target.y(), target.z(), mul);
		bird.getLookControl().setLookAt(target.x(), target.y() + 1.0D, target.z());
		for (net.minecraft.world.entity.Entity p : bird.getPassengers()) {
			p.setYRot(bird.getYRot());
			if (p instanceof net.minecraft.world.entity.LivingEntity le) {
				le.yBodyRot = bird.getYRot();
				le.setYHeadRot(bird.getYRot());
			}
		}
		if (bird.horizontalCollision && bird.onGround()) {
			bird.getJumpControl().jump();
		}
		if (!layout.onCourse(bird.getX(), bird.getZ()) && bird.tickCount % 10 == 0) {
			// Drifted off the road: nudge back onto the line (or the detour).
			RacePoint back = track.pointAtLane(t, track.detourLaneAt(t, directLane, bird.color(), bogSavvy));
			bird.getMoveControl().setWantedPosition(back.x(), back.y(), back.z(), mul);
		}
	}

	/**
	 * Lane of the next boost strip's pad if this bird is going for it, else NaN.
	 * Strips cover one 3-block lane each (outside / centre / inside, see
	 * {@link RaceCourseLayout#boostLane}); the inside racing line alone missed
	 * every outside one.
	 */
	private double boostLane(double t) {
		double look = BOOST_LOOK / track.lapLength();
		List<RaceTrack.Feature> features = track.features();
		for (int i = 0; i < features.size(); i++) {
			RaceTrack.Feature f = features.get(i);
			if (f.type() != RaceTrack.Feature.Type.BOOST) {
				continue;
			}
			double until = f.start() - t;
			if (until < 0.0D) {
				until += 1.0D;
			}
			boolean on = f.covers(t);
			if (!on && until > look) {
				continue;
			}
			if (boostStrip != i) {
				boostStrip = i;
				boostAim = RacerLine.aimsForBoost(profile.boostAim(), boostSeed, i, lapsDone);
			}
			if (!boostAim) {
				return Double.NaN;
			}
			for (double o : RacerLine.BOOST_LANES) {
				if (layout.boostLane(f, o)) {
					return o;
				}
			}
		}
		boostStrip = -1;   // clear of every strip: the next lap's pass rolls afresh
		return Double.NaN;
	}

	/** Signed lane of a point: positive = inside the loop, like {@link RaceTrack#pointAtLane}. */
	private double laneOf(double t, double x, double z) {
		RacePoint c = track.pointAt(t);
		RacePoint in = track.pointAtLane(t, 1.0D);
		double nx = in.x() - c.x(), nz = in.z() - c.z();
		double len = Math.max(1e-6, Math.hypot(nx, nz));
		return ((x - c.x()) * nx + (z - c.z()) * nz) / len;
	}
}
