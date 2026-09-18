package tk.darrow.chocobosreborn.race;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;

/**
 * Move control for Square AI racers. Vanilla {@link MoveControl} feeds the
 * movement speed in as the forward input too, so a mob runs at roughly speed
 * squared: a fraction of what a rider gets from the same bird. This one steers
 * toward the wanted point and drives with full forward input at
 * {@code speedModifier x MOVEMENT_SPEED}, the same scale a rider's input uses,
 * so {@link RacerProfile} cruise/dash sit on the same scale as a rider's
 * {@code speedMul} and dash, and cooperation caps how fast the bird can turn.
 */
public class RacerMoveControl extends MoveControl {
	public RacerMoveControl(Mob mob) {
		super(mob);
	}

	@Override
	public void tick() {
		if (operation != Operation.MOVE_TO) {
			super.tick();
			return;
		}
		operation = Operation.WAIT;
		double dx = wantedX - mob.getX();
		double dz = wantedZ - mob.getZ();
		double dy = wantedY - mob.getY();
		double dist = dx * dx + dz * dz;
		if (dist < 1.0E-4D) {
			mob.setZza(0.0F);
			return;
		}
		float yaw = (float) (Mth.atan2(dz, dx) * (180.0F / (float) Math.PI)) - 90.0F;
		int coop = mob instanceof ChocoboEntity bird ? bird.trainedCooperation() : 100;
		mob.setYRot(rotlerp(mob.getYRot(), yaw, RaceScoring.turnMaxDegrees(coop)));
		mob.yBodyRot = mob.getYRot();
		float speed = (float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
		mob.setSpeed(speed);
		mob.setZza(1.0F);
		if (dy > mob.maxUpStep() && dist < Math.max(1.0D, mob.getBbWidth() * mob.getBbWidth())) {
			mob.getJumpControl().jump();
			operation = Operation.JUMPING;
		}
	}
}
