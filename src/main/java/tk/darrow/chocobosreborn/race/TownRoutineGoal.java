package tk.darrow.chocobosreborn.race;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import tk.darrow.chocobosreborn.entity.KinStewardEntity;

/**
 * A resident's day ({@link VillageLayout#activity}): home at night, work in the
 * morning, the fountain in the afternoon, the inn in the evening, and the overlook
 * rail whenever a heat is called. Thinks once a second; paths only when the
 * target changes or a walk ends short; potters within two blocks once there.
 */
public class TownRoutineGoal extends Goal {
	private static final double SPEED = 0.55D;
	private static final int STUCK_THINKS = 45;

	private final KinStewardEntity kin;
	private final int index;
	private VillageLayout.Activity activity;
	private double tx, tz;
	private int think;
	private int idle;
	private int stuck;
	private boolean arrived;

	public TownRoutineGoal(KinStewardEntity kin, int index) {
		this.kin = kin;
		this.index = index;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	/** Which resident this role is (index into {@link VillageLayout#residents}), or -1. */
	public static int indexOf(TownRole role) {
		return VillageLayout.indexOf(role);
	}

	@Override
	public boolean canUse() {
		return kin.level() instanceof ServerLevel sl && Square.isSquare(sl) && !kin.isPassenger();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void stop() {
		kin.setCheering(false);
	}

	@Override
	public void tick() {
		ServerLevel level = (ServerLevel) kin.level();
		if (--think <= 0) {
			think = 10;   // goals tick every other game tick: about once a second
			VillageLayout.Activity a = VillageLayout.activity(level.getDayTime(), TownLife.heatLive(level));
			if (a != activity) {
				activity = a;
				double[] spot = VillageLayout.spot(index, a);
				tx = spot[0];
				tz = spot[1];
				arrived = false;
				stuck = 0;
				walk(tx, tz);
			} else if (!arrived) {
				if (kin.distanceToSqr(tx, kin.getY(), tz) <= 2.25D) {
					arrived = true;
					idle = 40 + kin.getRandom().nextInt(80);
					kin.getNavigation().stop();
				} else if (kin.getNavigation().isDone()) {
					if (++stuck > STUCK_THINKS && level.getNearestPlayer(kin, 20.0D) == null) {
						// a walk that never gets there, out of sight: step onto the spot
						kin.moveTo(tx, SquareBuilder.GROUND_Y + 1, tz, kin.getYRot(), 0.0F);
						arrived = true;
					} else {
						walk(tx, tz);
					}
				}
			}
			kin.setCheering(activity == VillageLayout.Activity.WATCH && arrived && TownLife.cheering(level));
		}
		if (arrived && activity != VillageLayout.Activity.HOME && activity != VillageLayout.Activity.WATCH && --idle <= 0) {
			idle = 60 + kin.getRandom().nextInt(100);
			double ox = (kin.getRandom().nextDouble() - 0.5D) * 4.0D, oz = (kin.getRandom().nextDouble() - 0.5D) * 4.0D;
			kin.getNavigation().moveTo(tx + ox, SquareBuilder.GROUND_Y + 1, tz + oz, SPEED * 0.7D);
		}
	}

	private void walk(double x, double z) {
		if (kin.getNavigation().moveTo(x, SquareBuilder.GROUND_Y + 1, z, SPEED)) {
			return;
		}
		Vec3 step = DefaultRandomPos.getPosTowards(kin, 16, 7, new Vec3(x, SquareBuilder.GROUND_Y + 1, z), Math.PI / 2.0D);
		if (step != null) {
			kin.getNavigation().moveTo(step.x, step.y, step.z, SPEED);
		}
	}
}
