package tk.darrow.chocobosreborn.race;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;

/** The Chocobo Square dimension and cross-dimension moves. */
public final class Square {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
			ResourceLocation.fromNamespaceAndPath(ChocobosReborn.MOD_ID, "square"));

	/** Where a visiting bird lands, facing the course. */
	public static final Vec3 ARRIVAL = new Vec3(0.5D, 65.0D, -58.5D);
	public static final float ARRIVAL_YAW = 180.0F;

	private Square() {
	}

	public static boolean isSquare(Level level) {
		return level.dimension() == DIMENSION;
	}

	public static @Nullable ServerLevel level(MinecraftServer server) {
		return server.getLevel(DIMENSION);
	}

	/** Move any entity, changing dimension if needed. Returns the live entity afterwards. */
	public static @Nullable Entity teleport(Entity e, ServerLevel target, Vec3 pos, float yaw) {
		// changeDimension never dismounts: a player still in a boat / minecart / on a horse
		// would arrive attached to a vehicle left behind in the old dimension
		if (e.isPassenger()) {
			Entity vehicle = e.getVehicle();
			boolean bird = vehicle instanceof ChocoboEntity;
			if (bird) {
				RaceManager.RELEASING.add(vehicle.getUUID());
			}
			try {
				e.stopRiding();
			} finally {
				if (bird) {
					RaceManager.RELEASING.remove(vehicle.getUUID());
				}
			}
		}
		if (e.level() == target) {
			e.teleportTo(pos.x, pos.y, pos.z);
			e.setYRot(yaw);
			e.setYHeadRot(yaw);
			return e;
		}
		return e.changeDimension(new DimensionTransition(target, pos, Vec3.ZERO, yaw, 0.0F,
				DimensionTransition.DO_NOTHING));
	}

	/** Move a rider and their bird together and remount. Returns the bird in the target level. */
	public static @Nullable ChocoboEntity teleportMounted(ServerPlayer player, ChocoboEntity bird,
	                                                       ServerLevel target, Vec3 pos, float yaw) {
		RaceManager.RELEASING.add(bird.getUUID());
		try {
			player.stopRiding();
		} finally {
			RaceManager.RELEASING.remove(bird.getUUID());
		}
		Entity moved = teleport(bird, target, pos, yaw);
		Entity p = teleport(player, target, pos.add(0.0D, 0.5D, 0.0D), yaw);
		ServerPlayer sp = p instanceof ServerPlayer s ? s : player;
		sp.setYRot(yaw);
		if (moved instanceof ChocoboEntity nb) {
			sp.startRiding(nb, true);
			return nb;
		}
		return null;
	}
}
