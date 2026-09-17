package tk.darrow.chocobosreborn.net;

import java.util.function.Consumer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import tk.darrow.chocobosreborn.ChocobosReborn;

/**
 * Server -> client: the player's ledger entries, sent when the Chocobo Almanac
 * is opened. The client mod installs {@link #CLIENT_OPENER}; the server never
 * touches client classes.
 */
public record AlmanacPayload(CompoundTag data) implements CustomPacketPayload {
	public static final Type<AlmanacPayload> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "almanac"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AlmanacPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.COMPOUND_TAG, AlmanacPayload::data, AlmanacPayload::new);

	@Nullable
	public static Consumer<CompoundTag> CLIENT_OPENER;

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(AlmanacPayload payload, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (CLIENT_OPENER != null) {
				CLIENT_OPENER.accept(payload.data());
			}
		});
	}
}
