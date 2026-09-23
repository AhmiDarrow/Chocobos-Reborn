package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.entity.ModEntities;

/**
 * A bird on Follow comes with its owner into the Nether, the End, the Square, and back.
 * Stay, Wander, a lead, a race, and the Square's own birds stay where they were put.
 * Only a loaded bird can make the trip; once its chunk loads, the next check brings it.
 */
public final class FollowAcross {
	private static final Map<UUID, Integer> WAIT = new HashMap<>();

	private FollowAcross() {
	}

	public static void onChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			bringLoaded(player);
		}
	}

	/** Loaded follow-birds in every other dimension. */
	public static void bringLoaded(ServerPlayer owner) {
		if (!owner.isAlive() || owner.isSpectator()) {
			return;
		}
		MinecraftServer server = owner.server;
		for (ServerLevel level : server.getAllLevels()) {
			if (level == owner.serverLevel()) {
				continue;
			}
			List<ChocoboEntity> birds = new ArrayList<>(level.getEntities(ModEntities.CHOCOBO.get(),
					bird -> wants(bird) && owner.getUUID().equals(bird.getOwnerUUID())));
			for (ChocoboEntity bird : birds) {
				bring(bird, owner);
			}
		}
	}

	/** A bird whose chunk loaded after the owner had already left. */
	public static void towardOwner(ChocoboEntity bird) {
		if (!(bird.level() instanceof ServerLevel level) || !wants(bird)) {
			return;
		}
		int now = level.getServer().getTickCount();
		Integer wait = WAIT.get(bird.getUUID());
		if (wait != null && now < wait) {
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(bird.getOwnerUUID());
		if (owner == null || owner.level() == level || !owner.isAlive() || owner.isSpectator()) {
			return;
		}
		bring(bird, owner);
	}

	public static boolean wants(ChocoboEntity bird) {
		return bird.isAlive()
				&& !bird.isRemoved()
				&& bird.isTame()
				&& bird.command() == ChocoboEntity.Command.FOLLOW
				&& !bird.isOrderedToSit()
				&& !bird.racing()
				&& !bird.raceNpc()
				&& !bird.townBird()
				&& !bird.isLeashed()
				&& bird.getOwnerUUID() != null;
	}

	/** @return the bird in the owner's level, or the same bird when it did not need to move */
	public static @Nullable Entity bring(ChocoboEntity bird, ServerPlayer owner) {
		if (!wants(bird) || owner.level() == bird.level()) {
			return bird;
		}
		int now = owner.server.getTickCount();
		Integer wait = WAIT.get(bird.getUUID());
		if (wait != null && now < wait) {
			return bird;
		}
		if (bird.isVehicle()) {
			RaceManager.RELEASING.add(bird.getUUID());
			try {
				bird.ejectPassengers();
			} finally {
				RaceManager.RELEASING.remove(bird.getUUID());
			}
		}
		Vec3 spot = landing(owner, bird);
		// The owner's chunk ticket may not cover the landing yet. Load it the way a portal does,
		// or the copy is created and then dropped with the unloaded chunk.
		BlockPos at = BlockPos.containing(spot);
		ServerLevel destination = owner.serverLevel();
		destination.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(at), 3, at);
		destination.getChunk(at.getX() >> 4, at.getZ() >> 4);
		Entity moved = Square.teleport(bird, destination, spot, owner.getYRot());
		WAIT.remove(bird.getUUID());
		if (moved == null) {
			WAIT.put(bird.getUUID(), now + 40);
			return null;
		}
		moved.setPortalCooldown(300);
		moved.fallDistance = 0.0F;
		return moved;
	}

	/** Open ground a couple of blocks behind the owner, so a portal does not immediately send the bird back. */
	static Vec3 landing(ServerPlayer owner, ChocoboEntity bird) {
		ServerLevel level = owner.serverLevel();
		float yaw = owner.getYRot() * (Mth.PI / 180.0F);
		double x = owner.getX() + Math.sin(yaw) * 2.5D;
		double z = owner.getZ() - Math.cos(yaw) * 2.5D;
		BlockPos base = BlockPos.containing(x, owner.getY(), z);
		int tall = Math.max(1, Mth.ceil(bird.getBbHeight()));
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int r = 0; r <= 3; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (r > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy = 3; dy >= -6; dy--) {
						cursor.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
						if (standable(level, cursor, tall)) {
							return new Vec3(cursor.getX() + 0.5D, cursor.getY(), cursor.getZ() + 0.5D);
						}
					}
				}
			}
		}
		return new Vec3(x, owner.getY(), z);
	}

	private static boolean standable(ServerLevel level, BlockPos feet, int tall) {
		if (!level.getWorldBorder().isWithinBounds(feet)) {
			return false;
		}
		BlockPos ground = feet.below();
		BlockState floor = level.getBlockState(ground);
		if (!floor.isFaceSturdy(level, ground, Direction.UP)) {
			return false;
		}
		for (int i = 0; i < tall; i++) {
			BlockPos at = feet.above(i);
			if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
