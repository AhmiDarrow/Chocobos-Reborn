package tk.darrow.chocobosreborn.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.util.Mth;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.race.RaceScoring;

/**
 * Validate each received movement step instead of counting an ordered backlog
 * as one move from the start of the server tick. Never change physical velocity,
 * bypass collision replay, or increase the limit for a single packet.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChocoboVehicleMoveMixin {
	@Shadow
	public ServerPlayer player;
	@Shadow
	private double vehicleFirstGoodX;
	@Shadow
	private double vehicleFirstGoodY;
	@Shadow
	private double vehicleFirstGoodZ;
	@Shadow private double vehicleLastGoodX;
	@Shadow private double vehicleLastGoodY;
	@Shadow private double vehicleLastGoodZ;

	@Redirect(method = "handleMoveVehicle", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
	private void chocobosreborn$replayStep(Entity entity, MoverType type, Vec3 movement) {
		if (entity instanceof ChocoboEntity bird && bird.getControllingPassenger() == this.player) {
			movement = new Vec3(movement.x, RaceScoring.vehicleValidationY(movement.y), movement.z);
		}
		entity.move(type, movement);
	}

	@Redirect(method = "handleMoveVehicle", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/phys/Vec3;lengthSqr()D"))
	private double chocobosreborn$slackChocoboBurst(Vec3 velocity, ServerboundMoveVehiclePacket packet) {
		if (this.player == null || !(this.player.getRootVehicle() instanceof ChocoboEntity bird)
				|| bird.getControllingPassenger() != this.player) {
			return velocity.lengthSqr();
		}
		// Match the coordinates vanilla uses, including its world-bound clamps.
		double x = Mth.clamp(packet.getX(), -3.0E7, 3.0E7);
		double y = Mth.clamp(packet.getY(), -2.0E7, 2.0E7);
		double z = Mth.clamp(packet.getZ(), -3.0E7, 3.0E7);
		double dx = x - this.vehicleFirstGoodX;
		double dy = y - this.vehicleFirstGoodY;
		double dz = z - this.vehicleFirstGoodZ;
		double sx = x - this.vehicleLastGoodX;
		double sy = y - this.vehicleLastGoodY;
		double sz = z - this.vehicleLastGoodZ;
		// Runs after native thread, teleport and controlling-vehicle checks.
		return RaceScoring.vehiclePacketAllowance(velocity.lengthSqr(),
				dx * dx + dy * dy + dz * dz, sx * sx + sy * sy + sz * sz);
	}
}
