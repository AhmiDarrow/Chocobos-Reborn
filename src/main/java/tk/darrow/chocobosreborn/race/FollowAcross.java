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
	private static final int RETRY_TICKS = 40;

	private FollowAcross() {
	}

	/** Server stop: retry waits belong to that server's tick count. */
	static void reset() {
		WAIT.clear();
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
		if (waiting(bird, level.getServer().getTickCount())) {
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
		if (waiting(bird, now)) {
			return bird;
		}
		for (Entity passenger : bird.getPassengers()) {
			if (passenger instanceof net.minecraft.world.entity.player.Player p && !p.getUUID().equals(owner.getUUID())) {
				return bird;   // someone else is riding it (a /ride, another mod): not thrown off, as the whistle does
			}
		}
		Vec3 spot = landing(owner, bird);
		if (spot == null) {
			// nowhere safe near the owner (flying, in a two-high tunnel, over lava): try again later
			WAIT.put(bird.getUUID(), now + RETRY_TICKS);
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
		// The owner's chunk ticket may not cover the landing yet. Load it the way a portal does,
		// or the copy is created and then dropped with the unloaded chunk.
		BlockPos at = BlockPos.containing(spot);
		ServerLevel destination = owner.serverLevel();
		destination.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(at), 3, at);
		destination.getChunk(at.getX() >> 4, at.getZ() >> 4);
		Entity moved = Square.teleport(bird, destination, spot, owner.getYRot());
		WAIT.remove(bird.getUUID());
		if (moved == null) {
			WAIT.put(bird.getUUID(), now + RETRY_TICKS);
			return null;
		}
		moved.setPortalCooldown(300);
		moved.fallDistance = 0.0F;
		return moved;
	}

	/**
	 * A failed trip waits {@link #RETRY_TICKS} before the next try. A wait further off than that
	 * was set by an earlier server in this JVM (singleplayer reopening a world restarts the tick
	 * count) and would hold the bird back for as long as that server ran: dropped.
	 */
	private static boolean waiting(ChocoboEntity bird, int now) {
		Integer wait = WAIT.get(bird.getUUID());
		if (wait == null) {
			return false;
		}
		if (now < wait && wait - now <= RETRY_TICKS) {
			return true;
		}
		WAIT.remove(bird.getUUID());
		return false;
	}

	/** Open ground a couple of blocks behind the owner, so a portal does not immediately send the bird back. */
	@org.jetbrains.annotations.Nullable
	static Vec3 landing(ServerPlayer owner, ChocoboEntity bird) {
		return landingNear(owner, bird, owner.getYRot(), 2.5D);
	}

	/**
	 * Safe ground {@code distance} blocks behind the owner along {@code yawDeg}: within three blocks across and from
	 * three above to six below that point, else straight down that column. Null if there is none: a bird is never
	 * put in mid-air (an owner flying or on elytra) or inside the rock.
	 */
	@org.jetbrains.annotations.Nullable
	public static Vec3 landingNear(ServerPlayer owner, ChocoboEntity bird, float yawDeg, double distance) {
		ServerLevel level = owner.serverLevel();
		float yaw = yawDeg * (Mth.PI / 180.0F);
		double x = owner.getX() + Math.sin(yaw) * distance;
		double z = owner.getZ() - Math.cos(yaw) * distance;
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
						if (standable(level, cursor, tall, bird.fireImmune())) {
							return new Vec3(cursor.getX() + 0.5D, cursor.getY(), cursor.getZ() + 0.5D);
						}
					}
				}
			}
		}
		// the owner is high above any floor: the first safe ground below, if the drop is not into the void
		for (int y = base.getY() - 7; y > level.getMinBuildHeight(); y--) {
			cursor.set(base.getX(), y, base.getZ());
			if (standable(level, cursor, tall, bird.fireImmune())) {
				return new Vec3(cursor.getX() + 0.5D, cursor.getY(), cursor.getZ() + 0.5D);
			}
		}
		return null;
	}

	/** Sturdy ground, room for the bird's height, and nothing that burns or traps it (lava, fire, powder snow). */
	static boolean standable(ServerLevel level, BlockPos feet, int tall, boolean fireImmune) {
		if (!level.getWorldBorder().isWithinBounds(feet) || !level.isInWorldBounds(feet)) {
			return false;
		}
		BlockPos ground = feet.below();
		BlockState floor = level.getBlockState(ground);
		if (!floor.isFaceSturdy(level, ground, Direction.UP)) {
			return false;
		}
		if (!fireImmune && floor.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) {
			return false;
		}
		for (int i = 0; i < tall; i++) {
			BlockPos at = feet.above(i);
			BlockState st = level.getBlockState(at);
			if (!st.getCollisionShape(level, at).isEmpty() || hazard(st, fireImmune)) {
				return false;
			}
		}
		return true;
	}

	/** No collision box, but not somewhere to stand: lava and fire (unless it does not burn), powder snow, berry bushes. */
	private static boolean hazard(BlockState st, boolean fireImmune) {
		if (st.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW) || st.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
				|| st.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE) || st.is(net.minecraft.world.level.block.Blocks.COBWEB)) {
			return true;
		}
		return !fireImmune && (st.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
				|| st.is(net.minecraft.tags.BlockTags.FIRE));
	}
}
