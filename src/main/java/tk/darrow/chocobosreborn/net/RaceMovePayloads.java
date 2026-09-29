package tk.darrow.chocobosreborn.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import tk.darrow.chocobosreborn.ChocobosReborn;

/**
 * Race movement on its own wire, beside vanilla's vehicle packets.
 *
 * <p>{@link Teleport} / {@link TeleportAck}: vanilla has a handshake for a player's own
 * teleports but none for a vehicle. When the server moved a rider's bird (a set-back, the
 * transfer to a course) the client, which drives that bird and ignores the server's entity
 * teleport for it, kept sending moves from where it had been; every one of them was refused
 * with a correction, and each correction arrived after the client had moved on, so the bird
 * snapped back again and again for a whole round trip. Now the server numbers each move it
 * makes, the client applies it and answers, and moves sent before that answer are dropped
 * silently. See {@link tk.darrow.chocobosreborn.race.RiderAuthority}.
 *
 * <p>{@link Frame}: every racing bird's position, stamped with the server tick it was taken
 * on. Vanilla's entity packets carry no time, so a client can only lerp toward whatever
 * arrived last and every burst or gap in delivery shows as a stutter. With the tick a client
 * plays the field back from a short buffer instead ({@link PlayoutClock}, {@link FrameBuffer}).
 * Frames are read on the network thread so their arrival time is exact.
 */
public final class RaceMovePayloads {
	private RaceMovePayloads() {}

	/** Server to the rider: the server moved your bird; take it from here and answer with {@code id}. */
	public record Teleport(int entityId, int id, double x, double y, double z, float yRot, float xRot) implements CustomPacketPayload {
		public static java.util.function.Consumer<Teleport> CLIENT_HANDLER;
		public static final Type<Teleport> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "rider_teleport"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Teleport> CODEC = StreamCodec.of((b, p) -> {
			b.writeVarInt(p.entityId); b.writeVarInt(p.id);
			b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
			b.writeFloat(p.yRot); b.writeFloat(p.xRot);
		}, b -> new Teleport(b.readVarInt(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat()));
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
		public static void handle(Teleport p, IPayloadContext ctx) {
			ctx.enqueueWork(() -> { if (CLIENT_HANDLER != null) CLIENT_HANDLER.accept(p); });
		}
	}

	/** Server to the rider: your bird overlaps another racer; add this velocity to slide it clear ({@code race/RiderNudge}). */
	public record Nudge(int entityId, float dvx, float dvz) implements CustomPacketPayload {
		public static java.util.function.Consumer<Nudge> CLIENT_HANDLER;
		public static final Type<Nudge> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "rider_nudge"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Nudge> CODEC = StreamCodec.of((b, p) -> {
			b.writeVarInt(p.entityId); b.writeFloat(p.dvx); b.writeFloat(p.dvz);
		}, b -> new Nudge(b.readVarInt(), b.readFloat(), b.readFloat()));
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
		public static void handle(Nudge p, IPayloadContext ctx) {
			ctx.enqueueWork(() -> { if (CLIENT_HANDLER != null) CLIENT_HANDLER.accept(p); });
		}
	}

	/** Rider to server: the move numbered {@code id} is applied; everything sent from now on starts there. */
	public record TeleportAck(int entityId, int id) implements CustomPacketPayload {
		public static final Type<TeleportAck> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "rider_teleport_ack"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TeleportAck> CODEC = StreamCodec.of((b, p) -> {
			b.writeVarInt(p.entityId); b.writeVarInt(p.id);
		}, b -> new TeleportAck(b.readVarInt(), b.readVarInt()));
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
		public static void handle(TeleportAck p, IPayloadContext ctx) {
			ctx.enqueueWork(() -> {
				if (ctx.player() instanceof ServerPlayer player) {
					tk.darrow.chocobosreborn.race.RiderAuthority.ack(player, p.entityId, p.id);
				}
			});
		}
	}

	/** Server to everyone near: where a racing bird stood on server tick {@code tick}. */
	public record Frame(int entityId, int tick, double x, double y, double z, float yRot, float bodyRot, boolean onGround)
			implements CustomPacketPayload {
		/** Called on the network thread with the arrival time from {@link System#nanoTime()}. */
		public static java.util.function.BiConsumer<Frame, Long> NET_HANDLER;
		public static final Type<Frame> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "race_frame"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Frame> CODEC = StreamCodec.of((b, p) -> {
			b.writeVarInt(p.entityId); b.writeVarInt(p.tick);
			b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
			b.writeFloat(p.yRot); b.writeFloat(p.bodyRot); b.writeBoolean(p.onGround);
		}, b -> new Frame(b.readVarInt(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(),
				b.readFloat(), b.readFloat(), b.readBoolean()));
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
		public static void handle(Frame p, IPayloadContext ctx) {
			long arrived = System.nanoTime();
			if (NET_HANDLER != null) NET_HANDLER.accept(p, arrived);
		}
	}
}
