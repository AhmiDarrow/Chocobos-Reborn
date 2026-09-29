package tk.darrow.chocobosreborn.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.race.RaceScoring;
import tk.darrow.chocobosreborn.race.RiderAuthority;

/**
 * A player's chocobo moves by {@link RiderAuthority}, not by vanilla's replay-and-refuse.
 *
 * <p>Runs on the game thread (after vanilla's hand-off from the network thread), and only for a
 * chocobo this player drives; every other vehicle keeps vanilla's handling. Invalid numbers are
 * left to vanilla, which disconnects the sender.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChocoboVehicleMoveMixin {
	@Shadow public ServerPlayer player;
	@Shadow private Entity lastVehicle;
	@Shadow private double vehicleLastGoodX;
	@Shadow private double vehicleLastGoodY;
	@Shadow private double vehicleLastGoodZ;
	@Shadow private boolean clientVehicleIsFloating;

	@Shadow
	private boolean updateAwaitingTeleport() {
		throw new AssertionError();
	}

	@Shadow
	private void resyncPlayerWithVehicle(Entity vehicle) {
		throw new AssertionError();
	}

	@Shadow
	private boolean noBlocksAround(Entity entity) {
		throw new AssertionError();
	}

	@Inject(method = "handleMoveVehicle", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
	private void chocobosreborn$riderDrives(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
		Entity root = this.player.getRootVehicle();
		if (!(root instanceof ChocoboEntity bird) || bird.getControllingPassenger() != this.player || root != this.lastVehicle) {
			return;
		}
		if (!Double.isFinite(packet.getX()) || !Double.isFinite(packet.getY()) || !Double.isFinite(packet.getZ())
				|| !Float.isFinite(packet.getYRot()) || !Float.isFinite(packet.getXRot())) {
			return;
		}
		ci.cancel();
		if (this.updateAwaitingTeleport() || RiderAuthority.awaiting(this.player, bird)) {
			// sent before the client had its own teleport or the bird's: dropped, never answered with a correction
			return;
		}
		ServerLevel level = this.player.serverLevel();
		double tx = Mth.clamp(packet.getX(), -3.0E7D, 3.0E7D);
		double ty = Mth.clamp(packet.getY(), -2.0E7D, 2.0E7D);
		double tz = Mth.clamp(packet.getZ(), -3.0E7D, 3.0E7D);
		float yRot = Mth.wrapDegrees(packet.getYRot());
		float xRot = Mth.wrapDegrees(packet.getXRot());
		double fx = bird.getX(), fy = bird.getY(), fz = bird.getZ();
		String refused = RiderAuthority.refuse(level, this.player, bird, fx, fy, fz, tx, ty, tz);
		if (refused != null) {
			refuse(bird, refused, fx, fy, fz, tx, ty, tz);
			return;
		}
		boolean wasClear = level.noCollision(bird, bird.getBoundingBox().deflate(0.0625D));
		boolean landedBelow = bird.verticalCollisionBelow;
		if (bird.onClimbable()) {
			bird.resetFallDistance();
		}
		double dy = ty - fy - 1.0E-6D;
		// Replayed for its side effects (fall damage, pressure plates, portals); the client's result is kept.
		bird.move(MoverType.PLAYER, new Vec3(tx - fx, RaceScoring.vehicleValidationY(dy), tz - fz));
		bird.absMoveTo(tx, ty, tz, yRot, xRot);
		this.resyncPlayerWithVehicle(bird);
		if (wasClear && !level.noCollision(bird, bird.getBoundingBox().deflate(0.0625D))) {
			bird.absMoveTo(fx, fy, fz, yRot, xRot);
			this.resyncPlayerWithVehicle(bird);
			refuse(bird, "inside a block", fx, fy, fz, tx, ty, tz);
			return;
		}
		level.getChunkSource().move(this.player);
		Vec3 moved = new Vec3(bird.getX() - fx, bird.getY() - fy, bird.getZ() - fz);
		this.player.setKnownMovement(moved);
		this.player.checkMovementStatistics(moved.x, moved.y, moved.z);
		this.player.checkRidingStatistics(moved.x, moved.y, moved.z);
		this.clientVehicleIsFloating = dy >= -0.03125D && !landedBelow && !this.player.server.isFlightAllowed()
				&& !bird.isNoGravity() && this.noBlocksAround(bird);
		this.vehicleLastGoodX = bird.getX();
		this.vehicleLastGoodY = bird.getY();
		this.vehicleLastGoodZ = bird.getZ();
	}

	private void refuse(ChocoboEntity bird, String why, double fx, double fy, double fz, double tx, double ty, double tz) {
		int count = RiderAuthority.refused(this.player);
		ChocobosReborn.LOGGER.warn("{} (ridden by {}) move refused #{}: {} ({}, {}, {} -> {}, {}, {})",
				bird.getName().getString(), this.player.getName().getString(), count, why,
				String.format(java.util.Locale.ROOT, "%.2f", fx), String.format(java.util.Locale.ROOT, "%.2f", fy),
				String.format(java.util.Locale.ROOT, "%.2f", fz), String.format(java.util.Locale.ROOT, "%.2f", tx),
				String.format(java.util.Locale.ROOT, "%.2f", ty), String.format(java.util.Locale.ROOT, "%.2f", tz));
		RiderAuthority.teleport(this.player, bird);
	}
}
