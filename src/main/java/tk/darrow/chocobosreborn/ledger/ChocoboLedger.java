package tk.darrow.chocobosreborn.ledger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;

/**
 * Server-wide book of every tamed chocobo: written on tame, hatch, save and
 * death, read by the Chocobo Almanac so a player can look up birds that are
 * stabled in unloaded chunks. Stored with the overworld.
 */
public final class ChocoboLedger extends SavedData {
	private static final String NAME = "chocobosreborn_ledger";
	private static final Factory<ChocoboLedger> FACTORY = new Factory<>(ChocoboLedger::new, ChocoboLedger::load, null);

	private final Map<UUID, BirdRecord> birds = new LinkedHashMap<>();
	/** Released from the almanac while unloaded: untamed the next time they are seen. */
	private final java.util.Set<UUID> pendingRelease = new java.util.HashSet<>();

	public static ChocoboLedger get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
	}

	public static ChocoboLedger get(ServerLevel level) {
		return get(level.getServer());
	}

	private static ChocoboLedger load(CompoundTag tag, HolderLookup.Provider registries) {
		ChocoboLedger ledger = new ChocoboLedger();
		for (Tag t : tag.getList("Birds", Tag.TAG_COMPOUND)) {
			BirdRecord r = BirdRecord.load((CompoundTag) t);
			ledger.birds.put(r.id(), r);
		}
		for (net.minecraft.nbt.Tag t : tag.getList("PendingRelease", Tag.TAG_INT_ARRAY)) {
			ledger.pendingRelease.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
		}
		return ledger;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag pending = new ListTag();
		for (UUID id : pendingRelease) {
			pending.add(net.minecraft.nbt.NbtUtils.createUUID(id));
		}
		tag.put("PendingRelease", pending);
		ListTag list = new ListTag();
		for (BirdRecord r : birds.values()) {
			list.add(r.save());
		}
		tag.put("Birds", list);
		return tag;
	}

	/** Bring a tamed bird's entry up to date (creates it on first tame). */
	public void update(ChocoboEntity bird) {
		if (applyPendingRelease(bird)) {
			return;
		}
		if (!bird.isTame() || bird.getOwnerUUID() == null || bird.raceNpc()) {
			return;
		}
		BirdRecord old = birds.get(bird.getUUID());
		BirdRecord now = old == null
				? BirdRecord.of(bird, null, null, -1, -1, 0, day(bird.level().getGameTime()), true)
				: old.refreshed(bird);
		birds.put(bird.getUUID(), now);
		setDirty();
	}

	/** A chick hatched from two parents (called before it is added to the world). */
	public void hatched(ChocoboEntity chick, ChocoboEntity a, ChocoboEntity b, int nut, long gameTime) {
		BirdRecord r = BirdRecord.of(chick, a.getUUID(), b.getUUID(), a.color().getId(), b.color().getId(), nut,
				day(gameTime), true);
		birds.put(chick.getUUID(), r);
		setDirty();
	}

	/** Rename one of the player's birds: the live entity if loaded, and the record either way. */
	public void rename(net.minecraft.server.level.ServerPlayer player, UUID id, String name) {
		BirdRecord r = birds.get(id);
		if (r == null || !player.getUUID().equals(r.owner())) {
			return;
		}
		String clean = name.strip();
		if (clean.length() > 24) {
			clean = clean.substring(0, 24);
		}
		boolean found = false;
		for (ServerLevel level : player.getServer().getAllLevels()) {
			if (level.getEntity(id) instanceof ChocoboEntity bird) {
				bird.setCustomName(clean.isEmpty() ? null : net.minecraft.network.chat.Component.literal(clean));
				bird.setCustomNameVisible(!clean.isEmpty());
				found = true;
			}
		}
		String pending = found ? "" : (clean.isEmpty() ? BirdRecord.PENDING_CLEAR : clean);
		birds.put(id, new BirdRecord(r.id(), r.owner(), clean, r.color(), r.bornGrade(), r.grade(), r.male(), r.raceClass(),
				r.wins(), r.classWins(), r.trSpeed(), r.trStamina(), r.trIntel(), r.trCoop(), r.parentA(), r.parentB(),
				r.parentColorA(), r.parentColorB(), r.nut(), r.bornDay(), r.alive(), pending));
		setDirty();
	}

	/**
	 * Release a living bird from the almanac: it goes wild again (saddle and bags
	 * dropped) wherever it is, or the moment it is next loaded, and leaves the ledger.
	 */
	public void release(net.minecraft.server.level.ServerPlayer player, UUID id) {
		BirdRecord r = birds.get(id);
		if (r == null || !player.getUUID().equals(r.owner())) {
			return;
		}
		if (tk.darrow.chocobosreborn.race.RaceManager.isActiveRacer(id)
				|| tk.darrow.chocobosreborn.race.HeatSchedule.hasBird(id)) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
					"chocobosreborn.almanac.d.release_racing"), true);
			return;
		}
		boolean done = false;
		for (ServerLevel level : player.getServer().getAllLevels()) {
			if (level.getEntity(id) instanceof ChocoboEntity bird) {
				if (bird.racing()) {
					player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
							"chocobosreborn.almanac.d.release_racing"), true);
					return;
				}
				setWild(bird);
				done = true;
			}
		}
		if (!done) {
			pendingRelease.add(id);
		}
		birds.remove(id);
		setDirty();
		player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
				"chocobosreborn.almanac.d.released"), false);
		tk.darrow.chocobosreborn.item.ChocoboAlmanacItem.send(player);
	}

	/** Drop the record of a bird that has passed (Ahmi: "remove passed ones"). */
	public void forget(net.minecraft.server.level.ServerPlayer player, UUID id) {
		BirdRecord r = birds.get(id);
		if (r == null || !player.getUUID().equals(r.owner()) || r.alive()) {
			return;
		}
		birds.remove(id);
		setDirty();
		tk.darrow.chocobosreborn.item.ChocoboAlmanacItem.send(player);
	}

	/** A released bird that was unloaded at the time: make it wild now. Returns true when it was pending. */
	public boolean applyPendingRelease(ChocoboEntity bird) {
		if (!pendingRelease.remove(bird.getUUID())) {
			return false;
		}
		setWild(bird);
		birds.remove(bird.getUUID());
		setDirty();
		return true;
	}

	private static void setWild(ChocoboEntity bird) {
		bird.dropEquipment();
		bird.setOrderedToSit(false);
		bird.resetLove();
		bird.clearNut();
		bird.setOwnerUUID(null);
		bird.setTame(false, false);
		bird.setCustomNameVisible(false);
		bird.setCustomName(null);
		bird.ejectPassengers();
	}

	public void died(UUID id) {
		BirdRecord r = birds.get(id);
		if (r != null && r.alive()) {
			birds.put(id, r.dead());
			setDirty();
		}
	}

	@Nullable
	public BirdRecord find(UUID id) {
		return birds.get(id);
	}

	/** The player's birds, plus the parents / children of those birds for the family pages. */
	public List<BirdRecord> forOwner(UUID owner) {
		List<BirdRecord> mine = new ArrayList<>();
		for (BirdRecord r : birds.values()) {
			if (owner.equals(r.owner())) {
				mine.add(r);
			}
		}
		List<BirdRecord> out = new ArrayList<>(mine);
		for (BirdRecord r : mine) {
			addIfKnown(out, r.parentA());
			addIfKnown(out, r.parentB());
		}
		for (BirdRecord r : birds.values()) {
			if (!out.contains(r) && (isIn(mine, r.parentA()) || isIn(mine, r.parentB()))) {
				out.add(r);
			}
		}
		return out;
	}

	private void addIfKnown(List<BirdRecord> out, @Nullable UUID id) {
		BirdRecord r = id == null ? null : birds.get(id);
		if (r != null && !out.contains(r)) {
			out.add(r);
		}
	}

	private static boolean isIn(List<BirdRecord> list, @Nullable UUID id) {
		if (id == null) {
			return false;
		}
		for (BirdRecord r : list) {
			if (r.id().equals(id)) {
				return true;
			}
		}
		return false;
	}

	private static long day(long gameTime) {
		return gameTime / 24000L;
	}
}
