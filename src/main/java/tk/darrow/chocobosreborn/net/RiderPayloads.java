package tk.darrow.chocobosreborn.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;

/** Race input and authoritative acknowledgements; both handlers run on the game thread. */
public final class RiderPayloads {
    private RiderPayloads() {}
    public record Hud(int entityId, int lap, int laps, int place, int field) implements CustomPacketPayload {
        public static java.util.function.Consumer<Hud> CLIENT_HANDLER;
        public static final Type<Hud> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "race_hud"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hud> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.entityId); b.writeVarInt(p.lap); b.writeVarInt(p.laps); b.writeVarInt(p.place); b.writeVarInt(p.field);
        }, b -> new Hud(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static void handle(Hud p, IPayloadContext ctx) {
            ctx.enqueueWork(() -> { if (CLIENT_HANDLER != null) CLIENT_HANDLER.accept(p); });
        }
    }
    public record Input(int entityId, int epoch, int sequence, boolean dash, boolean moving) implements CustomPacketPayload {
        public static final Type<Input> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "race_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Input> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.entityId); b.writeVarInt(p.epoch); b.writeVarInt(p.sequence); b.writeBoolean(p.dash); b.writeBoolean(p.moving);
        }, b -> new Input(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static void handle(Input p, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer player && player.getVehicle() instanceof ChocoboEntity bird
                        && bird.getId() == p.entityId && bird.getControllingPassenger() == player) bird.receiveRaceInput(player, p);
            });
        }
    }
    public record Snapshot(int entityId, int epoch, int acknowledged, int stamina, boolean locked,
                           int maximum, int intelligence, long seed, boolean resend) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "race_input_ack"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.entityId); b.writeVarInt(p.epoch); b.writeVarInt(p.acknowledged); b.writeVarInt(p.stamina);
            b.writeBoolean(p.locked); b.writeVarInt(p.maximum); b.writeVarInt(p.intelligence); b.writeLong(p.seed); b.writeBoolean(p.resend);
        }, b -> new Snapshot(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(),
                b.readVarInt(), b.readVarInt(), b.readLong(), b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static void handle(Snapshot p, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (ctx.player().level().getEntity(p.entityId) instanceof ChocoboEntity bird) bird.receiveRaceSnapshot(p);
            });
        }
    }
}
