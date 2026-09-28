package tk.darrow.chocobosreborn.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;

/**
 * A server correction of the bird this client drives. Vanilla moves the vehicle to the
 * server's position (absMoveTo, not a lerp: {@code ChocoboEntity#lerpTo} is not involved)
 * and echoes it back, but leaves its velocity and on-ground flag: the next tick then
 * replays the rejected move from the same spot. Runs after vanilla, on the main thread
 * only (the network-thread pass returns early through {@code ensureRunningOnSameThread}).
 */
@Mixin(ClientPacketListener.class)
public abstract class ChocoboVehicleCorrectionMixin {
	@Inject(method = "handleMoveVehicle", at = @At("TAIL"))
	private void chocobosreborn$adoptCorrection(ClientboundMoveVehiclePacket packet, CallbackInfo ci) {
		var player = Minecraft.getInstance().player;
		if (player != null && player.getRootVehicle() instanceof ChocoboEntity bird && bird.isControlledByLocalInstance()) {
			bird.adoptVehicleCorrection();
		}
	}
}
