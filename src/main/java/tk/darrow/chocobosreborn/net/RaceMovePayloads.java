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

	/** Where a racing bird stood on server tick {@code tick}: one entry of {@link Frames}. */
	public record Frame(int entityId, int tick, double x, double y, double z, float yRot, float bodyRot, boolean onGround) {}

	/**
	 * Server to one player: every racing bird near them on server tick {@code tick}, in one packet.
	 * One payload per bird repeated the channel name (longer than the frame itself) for each of
	 * them every tick; batched, a six-bird heat is about a third of the bytes. Y is a float (a
	 * course sits at y 65) and the yaws are sixteen-bit angles; x and z stay doubles, since a course
	 * island can be thousands of blocks out.
	 */
	public record Frames(int tick, java.util.List<Frame> frames) implements CustomPacketPayload {
		/** Called on the network thread, once per frame, with the arrival time from {@link System#nanoTime()}. */
		public static java.util.function.BiConsumer<Frame, Long> NET_HANDLER;
		public static final Type<Frames> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "race_frames"));
		private static final float ANGLE = 65536.0F / 360.0F;
		public static final StreamCodec<RegistryFriendlyByteBuf, Frames> CODEC = StreamCodec.of((b, p) -> {
			b.writeVarInt(p.tick);
			b.writeVarInt(p.frames.size());
			for (Frame f : p.frames) {
				b.writeVarInt(f.entityId());
				b.writeDouble(f.x()); b.writeFloat((float) f.y()); b.writeDouble(f.z());
				b.writeShort(Math.round(f.yRot() * ANGLE)); b.writeShort(Math.round(f.bodyRot() * ANGLE));
				b.writeBoolean(f.onGround());
			}
		}, b -> {
			int tick = b.readVarInt();
			int n = b.readVarInt();
			java.util.List<Frame> frames = new java.util.ArrayList<>(Math.min(n, 256));
			for (int i = 0; i < n; i++) {
				frames.add(new Frame(b.readVarInt(), tick, b.readDouble(), b.readFloat(), b.readDouble(),
						b.readShort() / ANGLE, b.readShort() / ANGLE, b.readBoolean()));
			}
			return new Frames(tick, frames);
		});
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
		public static void handle(Frames p, IPayloadContext ctx) {
			long arrived = System.nanoTime();
			if (NET_HANDLER != null) {
				for (Frame f : p.frames) NET_HANDLER.accept(f, arrived);
			}
		}
	}
}
