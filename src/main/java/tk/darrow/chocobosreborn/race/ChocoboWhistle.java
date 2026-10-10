package tk.darrow.chocobosreborn.race;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.ledger.BirdRecord;
import tk.darrow.chocobosreborn.ledger.ChocoboLedger;
import tk.darrow.chocobosreborn.sound.ModSounds;

/**
 * A blown whistle calls the player's tame birds from any distance and any dimension.
 * Passive follow is unchanged: this is a deliberate summon, Stay and Wander included.
 */
public final class ChocoboWhistle {
	private static final int PENDING_TICKS = 100;
	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	private static final Map<Integer, Blow> BLOWS = new HashMap<>();
	private static int nextBlow = 1;

	private ChocoboWhistle() {
	}

	/** Server stop: a pending call belongs to that server's tick count. */
	public static void reset() {
		PENDING.clear();
		BLOWS.clear();
	}

	/**
	 * @return true when at least one bird was summoned or a chunk load was left pending
	 */
	public static boolean blow(ServerPlayer owner) {
		if (!owner.isAlive() || owner.isSpectator()) {
			return false;
		}
		MinecraftServer server = owner.server;
		ChocoboLedger ledger = ChocoboLedger.get(server);
		UUID me = owner.getUUID();
		int now = server.getTickCount();
		int token = nextBlow++;
		Blow blow = new Blow(me);
		int here = 0;
		int racing = 0;
		int busy = 0;
		int lost = 0;
		int parked = 0;
		int qualified = 0;
		int accepted = 0;
		for (BirdRecord record : ledger.owned(me)) {
			if (!record.alive()) {
				continue;
			}
			UUID id = record.id();
			if (RaceManager.isActiveRacer(id) || HeatSchedule.hasBird(id)) {
				racing++;
				continue;
			}
			ChocoboEntity bird = findLoaded(server, id);
			if (bird == null) {
				ChocoboLedger.Where at = ledger.findWhere(id);
				ServerLevel source = at == null ? null : whereLevel(server, at);
				if (at == null || source == null) {
					lost++;
					continue;
				}
				if (at.command() != ChocoboLedger.UNKNOWN_COMMAND && at.command() != WhistleRules.FOLLOW) {
					parked++;          // left on Stay or Wander: not worth loading its chunk to leave it there
					continue;
				}
				if (accepted >= WhistleRules.CAP) {
					qualified++;
					continue;
				}
				bird = forceLoad(source, id, at);
				if (bird == null) {
					qualified++;
					file(blow, token, id, me, now);
					accepted++;
					continue;
				}
			}
			detach(id);
			WhistleRules.Reason reason = WhistleRules.reason(facts(bird, owner, record));
			switch (reason) {
				case CALL -> {
					qualified++;
					if (accepted >= WhistleRules.CAP) {
						break;
					}
					if (summon(bird, owner, blow)) {
						blow.arrived++;
						accepted++;
					}
				}
				case HERE, WhistleRules.Reason.MOUNTED -> here++;
				case RACING -> racing++;
				case BUSY -> busy++;
				case PARKED -> parked++;
				default -> {
				}
			}
		}
		int coming = blow.arrived + blow.waiting;
		// "eight came" only when eight did: a teleport another mod blocked does not count
		boolean capped = qualified > WhistleRules.CAP && coming >= WhistleRules.CAP;
		String key = WhistleRules.noticeKey(coming, capped, here, racing, busy, lost, parked);
		if (coming > 0) {
			if ("chocobosreborn.whistle.called_many".equals(key)) {
				owner.displayClientMessage(Component.translatable(key, coming), false);
			} else {
				owner.displayClientMessage(Component.translatable(key), false);
			}
			if (WhistleRules.alsoRacing(coming, racing)) {
				owner.displayClientMessage(Component.translatable("chocobosreborn.whistle.racing"), true);
			}
			owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), ModSounds.KWEH_FOLLOW.get(),
					SoundSource.NEUTRAL, 0.8F, 1.0F);
		} else {
			owner.displayClientMessage(Component.translatable(key), true);
		}
		if (blow.waiting == 0) {
			BLOWS.remove(token);
		}
		return coming > 0;
	}

	/** A chunk that was unloaded at the blow has finished loading this bird. */
	public static void onAdded(ChocoboEntity bird) {
		if (!(bird.level() instanceof ServerLevel level)) {
			return;
		}
		Pending pending = PENDING.remove(bird.getUUID());
		if (pending == null) {
			return;
		}
		Blow blow = BLOWS.get(pending.blow);
		if (blow != null && blow.waiting > 0) {
			blow.waiting--;
		}
		MinecraftServer server = level.getServer();
		ServerPlayer owner = server.getPlayerList().getPlayer(pending.owner);
		if (owner != null && owner.isAlive() && !owner.isSpectator()) {
			BirdRecord record = ChocoboLedger.get(server).find(bird.getUUID());
			// a saved lead is reattached on the bird's first tick, after this: isLeashed() still reads false here
			boolean tied = bird.getLeashData() != null;
			if (record != null && !tied && WhistleRules.reason(facts(bird, owner, record)) == WhistleRules.Reason.CALL) {
				if (blow == null) {
					blow = new Blow(owner.getUUID());
				}
				if (summon(bird, owner, blow)) {
					blow.arrived++;
				}
			}
		}
		finish(blow, pending.blow, server, false);
	}

	/** Drop calls whose chunk never loaded, and say so once when none of that blow arrived. */
	public static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Pending> entry = it.next();
			Pending pending = entry.getValue();
			// A wait further out than one blow was stamped by an earlier server in this JVM.
			if (now < pending.expiry && pending.expiry - now <= PENDING_TICKS) {
				continue;
			}
			it.remove();
			Blow blow = BLOWS.get(pending.blow);
			if (blow != null && blow.waiting > 0) {
				blow.waiting--;
			}
			finish(blow, pending.blow, server, true);
		}
	}

	private static void finish(@Nullable Blow blow, int token, MinecraftServer server, boolean tellIfEmpty) {
		if (blow == null || blow.waiting > 0) {
			return;
		}
		BLOWS.remove(token);
		if (tellIfEmpty && blow.arrived == 0) {
			ServerPlayer owner = server.getPlayerList().getPlayer(blow.owner);
			if (owner != null) {
				owner.displayClientMessage(Component.translatable("chocobosreborn.whistle.lost"), true);
			}
		}
	}

	private static void file(Blow blow, int token, UUID bird, UUID owner, int now) {
		detach(bird);
		PENDING.put(bird, new Pending(owner, now + PENDING_TICKS, token));
		blow.waiting++;
		BLOWS.put(token, blow);
	}

	/** The bird is in the world now, so an older pending call must not summon it twice. */
	private static void detach(UUID bird) {
		Pending old = PENDING.remove(bird);
		if (old == null) {
			return;
		}
		Blow previous = BLOWS.get(old.blow);
		if (previous == null) {
			return;
		}
		if (previous.waiting > 0) {
			previous.waiting--;
		}
		if (previous.waiting == 0) {
			BLOWS.remove(old.blow);
		}
	}

	private static @Nullable ChocoboEntity findLoaded(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof ChocoboEntity bird && bird.isAlive() && !bird.isRemoved()) {
				return bird;
			}
		}
		return null;
	}

	private static @Nullable ServerLevel whereLevel(MinecraftServer server, ChocoboLedger.Where at) {
		try {
			ResourceLocation loc = ResourceLocation.parse(at.dim());
			ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, loc);
			return server.getLevel(key);
		} catch (IllegalArgumentException bad) {
			return null;
		}
	}

	/** Portal ticket on the bird's own chunk, then a synchronous get, the way a gate loads a landing. */
	private static @Nullable ChocoboEntity forceLoad(ServerLevel level, UUID id, ChocoboLedger.Where at) {
		BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
		level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(pos), 3, pos);
		level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
		if (level.getEntity(id) instanceof ChocoboEntity bird && bird.isAlive() && !bird.isRemoved()) {
			return bird;
		}
		return null;
	}

	private static WhistleRules.Facts facts(ChocoboEntity bird, ServerPlayer owner, BirdRecord record) {
		boolean same = owner.level() == bird.level();
		double distance = same ? owner.distanceTo(bird) : 0.0D;
		boolean ownerRiding = false;
		boolean otherRider = false;
		for (Entity passenger : bird.getPassengers()) {
			if (passenger instanceof Player player) {
				if (owner.getUUID().equals(player.getUUID())) {
					ownerRiding = true;
				} else {
					otherRider = true;
				}
			}
		}
		return new WhistleRules.Facts(owner.getUUID().equals(record.owner()) && owner.getUUID().equals(bird.getOwnerUUID()),
				record.alive(), bird.isTame(), !bird.isAlive() || bird.isRemoved(), bird.raceNpc(), bird.townBird(),
				bird.racing(), RaceManager.isActiveRacer(bird.getUUID()), HeatSchedule.hasBird(bird.getUUID()),
				bird.isLeashed(), otherRider, ownerRiding, same, distance, bird.command().ordinal(), bird.isBaby());
	}

	private static boolean summon(ChocoboEntity bird, ServerPlayer owner, Blow blow) {
		for (Entity passenger : bird.getPassengers()) {
			if (passenger instanceof Player player && !owner.getUUID().equals(player.getUUID())) {
				return false;
			}
		}
		// later birds land on a spun yaw, a step further behind, so eight do not share one block
		Vec3 spot = FollowAcross.landingNear(owner, bird, owner.getYRot() + blow.slot * 40.0F, 2.5D + blow.slot * 1.5D);
		if (spot == null) {
			return false;   // nowhere safe to land it: it stays where it is
		}
		if (bird.isVehicle()) {
			RaceManager.RELEASING.add(bird.getUUID());
			try {
				bird.ejectPassengers();
			} finally {
				RaceManager.RELEASING.remove(bird.getUUID());
			}
		}
		// Load the landing the way a portal does, or a cross-dimension copy is dropped with the chunk.
		BlockPos at = BlockPos.containing(spot);
		ServerLevel destination = owner.serverLevel();
		destination.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(at), 3, at);
		destination.getChunk(at.getX() >> 4, at.getZ() >> 4);
		Entity moved = Square.teleport(bird, destination, spot, owner.getYRot());
		if (!(moved instanceof ChocoboEntity live)) {
			return false;
		}
		blow.slot++;
		live.setPortalCooldown(300);
		live.fallDistance = 0.0F;
		if (live.getNavigation() != null) {
			live.getNavigation().stop();
		}
		live.giveCommand(ChocoboEntity.Command.FOLLOW, null);
		if (live.level() instanceof ServerLevel level) {
			ChocoboLedger.get(level).update(live);
		}
		return true;
	}

	private static final class Blow {
		final UUID owner;
		int waiting;
		int arrived;
		int slot;

		Blow(UUID owner) {
			this.owner = owner;
		}
	}

	private static final class Pending {
		final UUID owner;
		final int expiry;
		final int blow;

		Pending(UUID owner, int expiry, int blow) {
			this.owner = owner;
			this.expiry = expiry;
			this.blow = blow;
		}
	}
}
