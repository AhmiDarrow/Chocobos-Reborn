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
	/** Look this far round for traffic (blocks). */
	private static final double TRAFFIC_RANGE = 12.0D;
	/** A bird within this many blocks along the road is alongside, not ahead or behind. */
	private static final double ALONGSIDE = 2.5D;
	/** A chaser this close behind (blocks) makes a leader think about covering the inside. */
	private static final double CHASER_RANGE = 6.0D;
	private static final int PASS_TICKS = 40, MISS_TICKS = 30, DEFEND_TICKS = 40, DEFEND_COOLDOWN = 80;
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
	/** Did not see the bird ahead this time (sloppy classes): no pass, no lift, until this runs out. */
	private int missTicks;
	/** Covering the inside from a chaser: held for a while, then not again for a while (no weaving). */
	private int defendTicks, defendCooldown;
	private double defendLane;
	/** The bird last found ahead in our lane (entity id, -1 none): the miss roll is once per bird met. */
	private int lastBlocker = -1;
	private boolean dashing;
	/** Boost strip being lined up for (its feature index), and whether this bird goes for it. */
	private int boostStrip = -1;
	private boolean boostAim;
	/** Last progress on this lap, so the next tick searches that section. */
	private double along = -1.0D;
	private double lastX = Double.NaN, lastZ;
	/** Ticks pressed on a wall without moving ({@link RacerLine#stuckStep}), and ticks left of backing off it. */
	private int stuckTicks, backOff;
	/** A stuck bird aims this far back along the lap, on the lane it wants there. */
	private static final double BACK_OFF_BLOCKS = 3.0D;
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
		bird.setRaceDashFlag(dashing);   // dashing birds shove harder (RacerContact weight)
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
		// --- traffic: go round a bird ahead in our lane on the side with room, lift if boxed in,
		// keep clear of a bird alongside, cover the inside from a chaser (RacerContact bumps otherwise)
		if (ticks % 5 == 0) {
			nearby = bird.level().getEntitiesOfClass(ChocoboEntity.class,
					bird.getBoundingBox().inflate(TRAFFIC_RANGE, 2.0D, TRAFFIC_RANGE),
					e -> e != bird && e.contactSolid());
		}
		double follow = 1.0D;
		if (!finished && bird.contactSolid()) {
			follow = traffic(t, lane);
			lane = trafficLane;
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
		double aimT = t + ahead;
		lane = track.detourLaneAt(aimT, lane, bird.color(), bogSavvy);
		// pinned on a wall (a ridge face a non-climber cannot jump, a rail it was bumped
		// into): back off along the lane it wants for a second instead of pressing on
		stuckTicks = RacerLine.stuckStep(stuckTicks, bird.horizontalCollision, bird.onGround(), moved);
		if (stuckTicks >= RacerLine.STUCK_TICKS && backOff == 0) {
			backOff = RacerLine.RECOVER_TICKS;
			stuckTicks = 0;
		}
		if (backOff > 0) {
			backOff--;
			aimT = t - BACK_OFF_BLOCKS / track.lapLength();
			lane = track.detourLaneAt(aimT, directLane, bird.color(), bogSavvy);
		}
		RacePoint target = track.pointAtLane(aimT - Math.floor(aimT), lane);
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
		mul *= follow;   // boxed in behind a slower bird: match it rather than ram it
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

	/** The lane {@link #traffic} settled on. */
	private double trafficLane;

	/**
	 * Traffic for this tick, from the birds {@link #nearby}. Sets {@link #trafficLane}
	 * (from the aimed {@code lane}) and returns a pace scale (1 = none). Skill is the
	 * profile's lineHold: a C bird looks late and sometimes not at all, an S bird (and
	 * the rivals) early and nearly always ({@link RacerLine#trafficLook},
	 * {@link RacerLine#trafficMiss}).
	 */
	private double traffic(double t, double lane) {
		double skill = profile.lineHold();
		double clear = RacerLine.sideClear(skill);
		double myLane = laneOf(t, bird.getX(), bird.getZ());
		double mySpeed = bird.getDeltaMovement().horizontalDistance();
		double lap = track.lapLength();
		ChocoboEntity blocker = null;
		double blockerAhead = Double.MAX_VALUE, blockerLane = 0.0D, blockerSpeed = 0.0D;
		double chaserLane = Double.NaN;
		java.util.List<double[]> alongside = new java.util.ArrayList<>(2);
		for (ChocoboEntity other : nearby) {
			if (!other.isAlive() || !other.contactSolid()) {
				continue;
			}
			double ot = track.progressAt(other.getX(), other.getZ(), t);
			double ahead = RacerLine.blocksAhead(t, ot, lap);
			double oLane = laneOf(ot, other.getX(), other.getZ());
			double oSpeed = Math.hypot(other.contactVx(), other.contactVz());
			if (Math.abs(ahead) < ALONGSIDE && Math.abs(oLane - myLane) >= RacerContact.REACH * 0.5D) {
				alongside.add(new double[]{oLane});   // beside us, not nose to tail
			} else if (ahead > 0.0D) {
				double closing = mySpeed - oSpeed;
				boolean inLane = RacerLine.laneTaken(lane, oLane, clear)
						|| (ahead < ALONGSIDE && RacerLine.laneTaken(myLane, oLane, RacerContact.REACH));
				// slower, or already on its tail: a faster bird ahead is no obstacle
				boolean inTheWay = closing > 0.02D || ahead < RacerContact.REACH + 1.0D;
				if (inLane && inTheWay && ahead < RacerLine.trafficLook(skill, closing) && ahead < blockerAhead) {
					blocker = other;
					blockerAhead = ahead;
					blockerLane = oLane;
					blockerSpeed = oSpeed;
				}
			} else if (ahead > -CHASER_RANGE && oSpeed > mySpeed + 0.02D
					&& (Double.isNaN(chaserLane) || oLane > chaserLane)) {
				chaserLane = oLane;
			}
		}
		if (passTicks > 0) {
			passTicks--;
		}
		if (missTicks > 0) {
			missTicks--;
		}
		if (defendTicks > 0) {
			defendTicks--;
		} else if (defendCooldown > 0) {
			defendCooldown--;
		}
		double follow = 1.0D;
		int blockerId = blocker == null ? -1 : blocker.getId();
		boolean fresh = blockerId != lastBlocker;
		lastBlocker = blockerId;
		if (blocker != null && fresh && bird.getRandom().nextDouble() < RacerLine.trafficMiss(skill)) {
			missTicks = MISS_TICKS;   // did not see this one: straight on into its tail
		}
		if (blocker != null && missTicks == 0 && passTicks == 0) {
			// the side with room, unless a bird alongside already has that lane
			double side = RacerLine.passSide(lane, blockerLane);
			if (sideTaken(lane + side * RacerLine.PASS_OFFSET, alongside, clear)) {
				side = -side;
			}
			double aim = lane + side * RacerLine.PASS_OFFSET;
			if (Math.abs(aim) <= RacerLine.LANE_LIMIT + 0.5D && !sideTaken(aim, alongside, clear)) {
				passSide = side;
				passTicks = PASS_TICKS;
			}
		}
		if (passTicks > 0) {
			lane += passSide * RacerLine.PASS_OFFSET;   // a pass wins over a boost strip's lane
			defendTicks = 0;
		} else if (blocker == null && !Double.isNaN(chaserLane) && defendTicks == 0 && defendCooldown == 0
				&& Math.abs(myLane) <= RacerLine.LANE_LIMIT) {
			double covered = RacerLine.defendLane(myLane, chaserLane, skill);
			if (covered != myLane) {
				defendLane = covered;
				defendTicks = DEFEND_TICKS;
				defendCooldown = DEFEND_COOLDOWN;
			}
		}
		if (defendTicks > 0) {
			lane = defendLane;
		}
		if (missTicks == 0) {
			for (double[] a : alongside) {
				lane = RacerLine.keepClear(lane, myLane, a[0], clear);
			}
			// boxed in (no pass open) and about to touch its tail: match its pace
			if (blocker != null && passTicks == 0 && RacerLine.laneTaken(myLane, blockerLane, RacerContact.REACH)) {
				follow = RacerLine.followScale(mySpeed, blockerSpeed, blockerAhead);
			}
		}
		trafficLane = lane;
		return follow;
	}

	private static boolean sideTaken(double aim, java.util.List<double[]> alongside, double clear) {
		for (double[] a : alongside) {
			if (RacerLine.laneTaken(aim, a[0], clear)) {
				return true;
			}
		}
		return false;
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
