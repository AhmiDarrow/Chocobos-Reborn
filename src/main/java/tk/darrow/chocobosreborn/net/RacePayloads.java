package tk.darrow.chocobosreborn.net;

import java.util.UUID;
import java.util.function.BiConsumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.race.RaceManager;

/** Course selection (server asks the client to pick; the client answers) and bird renames. */
public final class RacePayloads {
	private RacePayloads() {
	}

	/** Server -> client: open the course picker for {@code classId}; mode 0 = ranked, 1 = duel challenge. */
	public record OpenCourseSelect(int classId, int mode) implements CustomPacketPayload {
		public static final Type<OpenCourseSelect> TYPE = new Type<>(
				ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "course_select"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenCourseSelect> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, OpenCourseSelect::classId, ByteBufCodecs.VAR_INT, OpenCourseSelect::mode,
				OpenCourseSelect::new);
		@Nullable
		public static BiConsumer<Integer, Integer> CLIENT_OPENER;

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		public static void handle(OpenCourseSelect p, IPayloadContext ctx) {
			ctx.enqueueWork(() -> {
				if (CLIENT_OPENER != null) {
					CLIENT_OPENER.accept(p.classId(), p.mode());
				}
			});
		}
	}

	/** Client -> server: the player picked a course (track ordinal), mode 0 ranked / 1 duel, GP stake for duels. */
	public record CourseChoice(int track, int mode, int stake) implements CustomPacketPayload {
		public static final Type<CourseChoice> TYPE = new Type<>(
				ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "course_choice"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CourseChoice> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, CourseChoice::track, ByteBufCodecs.VAR_INT, CourseChoice::mode,
				ByteBufCodecs.VAR_INT, CourseChoice::stake, CourseChoice::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		public static void handle(CourseChoice p, IPayloadContext ctx) {
			ctx.enqueueWork(() -> {
				if (ctx.player() instanceof ServerPlayer sp) {
					RaceManager.onCourseChosen(sp, p.track(), p.mode(), p.stake());
				}
			});
		}
	}

	/** Client -> server: rename one of the player's birds (by ledger id). */
	/** Almanac: release a living bird (forget = false) or drop a passed bird's record (forget = true). */
	public record ReleaseBird(UUID bird, boolean forget) implements CustomPacketPayload {
		public static final Type<ReleaseBird> TYPE = new Type<>(
				ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "release_bird"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ReleaseBird> CODEC = StreamCodec.composite(
				net.minecraft.core.UUIDUtil.STREAM_CODEC, ReleaseBird::bird,
				ByteBufCodecs.BOOL, ReleaseBird::forget, ReleaseBird::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		public static void handle(ReleaseBird p, IPayloadContext ctx) {
			ctx.enqueueWork(() -> {
				if (ctx.player() instanceof ServerPlayer sp) {
					var ledger = tk.darrow.chocobosreborn.ledger.ChocoboLedger.get(sp.serverLevel());
					if (p.forget()) {
						ledger.forget(sp, p.bird());
					} else {
						ledger.release(sp, p.bird());
					}
				}
			});
		}
	}

	public record RenameBird(UUID bird, String name) implements CustomPacketPayload {
		public static final Type<RenameBird> TYPE = new Type<>(
				ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "rename_bird"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RenameBird> CODEC = StreamCodec.composite(
				net.minecraft.core.UUIDUtil.STREAM_CODEC, RenameBird::bird,
				ByteBufCodecs.stringUtf8(32), RenameBird::name, RenameBird::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		public static void handle(RenameBird p, IPayloadContext ctx) {
			ctx.enqueueWork(() -> {
				if (ctx.player() instanceof ServerPlayer sp) {
					tk.darrow.chocobosreborn.ledger.ChocoboLedger.get(sp.serverLevel()).rename(sp, p.bird(), p.name());
				}
			});
		}
	}
}
