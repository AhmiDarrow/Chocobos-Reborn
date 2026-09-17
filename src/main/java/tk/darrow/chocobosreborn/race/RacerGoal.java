package tk.darrow.chocobosreborn.race;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.world.entity.ai.goal.Goal;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;

/**
 * Square AI racer. Drives a {@link RacerProfile}: hesitates at the start,
 * converges onto the inside racing line, dashes on the straights within an
 * energy budget (saving some for the last lap at higher classes), swings out to
 * pass a slower bird, wobbles and occasionally stumbles at low classes, and
 * rubber-bands gently toward the player at C and B. The session sets
 * {@link #speed} (base multiplier on the bird's movement speed), {@link #profile},
 * {@link #playerGap} and {@link #lapsDone} each tick, and toggles {@link #running}.
 */
public class RacerGoal extends Goal {
	private static final int STUMBLE_TICKS = 20;
	private static final double INSIDE_LINE = 1.0D;     // blocks inside the centre line
	private static final double PASS_OFFSET = 2.2D;     // blocks outward when overtaking

	private final ChocoboEntity bird;
	private final RaceTrack track;
	private final RaceCourseLayout layout;
	private final double startLane;
	public double speed = 1.0D;
	public boolean running;
	public RacerProfile profile = RacerProfile.of(RaceClass.C, RacerProfile.Role.FIELD);
	/** Laps the player is ahead of this racer (negative = this racer leads). */
	public double playerGap;
	public int lapsDone;
	public int totalLaps = 3;

	private final double noiseSeed;
	private final double lineSpread;
	/** Knows a bog when it sees one (sloppy classes sometimes drive straight in). */
	private final boolean bogSavvy;
	private double energy = 1.0D;
	private int reaction = -1;
	private int ticks;
	private int stumble;
	private int passTicks;
	private boolean dashing;
	private List<ChocoboEntity> nearby = List.of();

	public RacerGoal(ChocoboEntity bird, RaceTrack track, double lane) {
		this.bird = bird;
		this.track = track;
		this.startLane = lane;
		this.layout = RaceCourseLayout.of(track);
		this.noiseSeed = bird.getRandom().nextDouble() * Math.PI * 2.0D;
		this.lineSpread = (bird.getRandom().nextDouble() - 0.5D) * 1.2D;
		this.bogSavvy = bird.getRandom().nextDouble() < 0.45D + 0.55D * profile.lineHold();
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
		double t = track.progressAt(bird.getX(), bird.getZ());
		boolean lastLap = lapsDone >= totalLaps - 1;
		boolean straight = track.isStraight(t);

		// --- energy and dashing
		if (dashing) {
			energy = Math.max(0.0D, energy - profile.energyDrain());
		} else {
			energy = Math.min(1.0D, energy + profile.energyRecover());
		}
		dashing = profile.wantsDash(energy, straight, lastLap, playerGap);

		// --- racing line: start lane blends into the inside line over the first stretch
		double blend = Math.min(1.0D, ticks / 140.0D) * profile.lineHold();
		double wobble = profile.wobble() * Math.sin(bird.tickCount * 0.05D + noiseSeed);
		double lane = startLane + (INSIDE_LINE + lineSpread - startLane) * blend + wobble;
		// --- features: birds that excel take the direct line, the rest swing out onto the detour early
		RaceTrack.Feature feature = track.terrainAt(t + 0.03D);
		if (feature == null) {
			feature = track.terrainAt(t);
		}
		if (feature != null && !feature.suits(bird.color())
				&& (feature.type() != RaceTrack.Feature.Type.MUD || bogSavvy)) {
			lane = -(RaceTrack.DETOUR_INNER + RaceTrack.DETOUR_OUTER) / 2.0D + wobble * 0.5D;
			passTicks = 0;
		}

		// --- overtaking: a slower bird just ahead in our lane -> swing outward
		if (ticks % 5 == 0) {
			nearby = bird.level().getEntitiesOfClass(ChocoboEntity.class, bird.getBoundingBox().inflate(3.5D, 1.0D, 3.5D),
					e -> e != bird && e.racing());
		}
		if (passTicks > 0) {
			passTicks--;
		} else {
			for (ChocoboEntity other : nearby) {
				double dt = track.progressAt(other.getX(), other.getZ()) - t;
				if (dt < 0.0D) {
					dt += 1.0D;
				}
				if (dt < 0.03D && other.getDeltaMovement().horizontalDistanceSqr() < bird.getDeltaMovement().horizontalDistanceSqr()) {
					passTicks = 40;
					break;
				}
			}
		}
		if (passTicks > 0) {
			lane -= PASS_OFFSET;   // negative offset = outward
		}

		// --- stumbles (sloppy classes)
		if (stumble > 0) {
			stumble--;
		} else if (profile.stumbleChancePerLap() > 0.0D && bird.getRandom().nextDouble() < profile.stumbleChancePerLap() / 900.0D) {
			stumble = STUMBLE_TICKS;
		}

		double ahead = 0.012D + 0.006D * Math.max(0.0D, speed - 1.0D);
		RacePoint target = track.pointAtLane((t + ahead) % 1.0D, lane);
		// terrain is physical now (water slows swimmers, ridges block non-climbers); no attribute fudge
		double mul = speed * profile.cruise() * profile.bandFactor(playerGap);
		if (dashing) {
			mul *= profile.dash();
		}
		if (stumble > 0) {
			mul *= 0.55D;
		}
		// brake for the corners: hairpins and chicanes are taken slower, sweepers barely
		double turn = track.turnAhead(t, 18.0D);
		mul *= 1.0D - 0.30D * Math.min(1.0D, Math.max(0.0D, (turn - 0.35D) / 1.2D));
		if (lapsDone >= totalLaps) {
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
			RacePoint back = track.pointAtLane(t, lane);
			bird.getMoveControl().setWantedPosition(back.x(), back.y(), back.z(), mul);
		}
	}
}
