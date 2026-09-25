package tk.darrow.chocobosreborn.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.race.RaceScoring;

/**
 * A ridden chocobo's server velocity is cleared before packets run, so a guest
 * burst is judged against a zero step and rejected once it passes ten blocks.
 * Later packets in the same burst still see the position from the start of the
 * tick, so the allowance has to be applied on every packet. Course teleports
 * stay on the vanilla reject.
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

	@Inject(method = "handleMoveVehicle", at = @At("HEAD"))
	private void chocobosreborn$slackChocoboBurst(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
		if (this.player == null || !(this.player.getRootVehicle() instanceof ChocoboEntity bird)
				|| bird.getControllingPassenger() != this.player) {
			return;
		}
		double dx = packet.getX() - this.vehicleFirstGoodX;
		double dy = packet.getY() - this.vehicleFirstGoodY;
		double dz = packet.getZ() - this.vehicleFirstGoodZ;
		if (RaceScoring.vehicleMoveWithinSlack(dx, dy, dz)) {
			bird.setDeltaMovement(dx, dy, dz);
		}
	}
}
