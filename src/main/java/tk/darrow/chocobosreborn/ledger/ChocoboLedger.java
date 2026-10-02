package tk.darrow.chocobosreborn.ledger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
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
	/** Last saved dimension and block, so a whistle can load a bird whose chunk is not in memory. */
	private final Map<UUID, Where> where = new LinkedHashMap<>();
	/** Released from the almanac while unloaded: untamed the next time they are seen. */
	private final java.util.Set<UUID> pendingRelease = new java.util.HashSet<>();

	/** Dimension location string plus the block the bird last saved at. */
	public record Where(String dim, int x, int y, int z) {
	}

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
		for (Tag t : tag.getList("Where", Tag.TAG_COMPOUND)) {
			if (!(t instanceof CompoundTag compound) || !compound.hasUUID("Id")) {
				continue;
			}
			ledger.where.put(compound.getUUID("Id"), new Where(compound.getString("Dim"),
					compound.getInt("X"), compound.getInt("Y"), compound.getInt("Z")));
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
		ListTag places = new ListTag();
		for (Map.Entry<UUID, Where> entry : where.entrySet()) {
			CompoundTag compound = new CompoundTag();
			compound.putUUID("Id", entry.getKey());
			compound.putString("Dim", entry.getValue().dim());
			compound.putInt("X", entry.getValue().x());
			compound.putInt("Y", entry.getValue().y());
			compound.putInt("Z", entry.getValue().z());
			places.add(compound);
		}
		tag.put("Where", places);
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
		noteWhere(bird);
		BirdRecord old = birds.get(bird.getUUID());
		BirdRecord now = old == null
				? BirdRecord.of(bird, null, null, -1, -1, 0, day(bird.level().getGameTime()), true)
				: old.refreshed(bird);
		if (now.equals(old)) {
			return;   // runs on every chunk save of every tame bird: rewrite the ledger only on a change
		}
		birds.put(bird.getUUID(), now);
		setDirty();
	}

	/** Whereabouts are written even when the stats already match. Dirty only when the block changed. */
	private void noteWhere(ChocoboEntity bird) {
		if (!(bird.level() instanceof ServerLevel level)) {
			return;
		}
		BlockPos pos = bird.blockPosition();
		rememberWhere(bird.getUUID(), level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
	}

	private void rememberWhere(UUID id, String dim, int x, int y, int z) {
		Where next = new Where(dim, x, y, z);
		if (next.equals(where.get(id))) {
			return;
		}
		where.put(id, next);
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
		String clean = cleanName(name);
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
				r.wins(), r.classWins(), r.winsC(), r.winsB(), r.winsA(), r.trSpeed(), r.trStamina(), r.trIntel(), r.trCoop(),
				r.geneSpeed(), r.geneStamina(), r.geneIntel(), r.geneCoop(), r.spark(), r.parentA(), r.parentB(),
				r.parentColorA(), r.parentColorB(), r.nut(), r.bornDay(), r.alive(), pending));
		setDirty();
	}

	/**
	 * A name as the anvil would take it: no section signs or control characters (a raw
	 * NUL would read back as {@link BirdRecord#PENDING_CLEAR}), trimmed, at most 24 long.
	 */
	public static String cleanName(String raw) {
		String clean = net.minecraft.util.StringUtil.filterText(raw).strip();
		return clean.length() > 24 ? clean.substring(0, 24).strip() : clean;
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
		where.remove(id);
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
		where.remove(id);
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
		where.remove(bird.getUUID());
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

	@Nullable
	public Where findWhere(UUID id) {
		return where.get(id);
	}

	/** This player's own birds, in ledger order. Family pages stay on {@link #forOwner}. */
	public List<BirdRecord> owned(UUID owner) {
		List<BirdRecord> mine = new ArrayList<>();
		for (BirdRecord r : birds.values()) {
			if (owner.equals(r.owner())) {
				mine.add(r);
			}
		}
		return mine;
	}

	/**
	 * The player's birds, plus the parents / children of those birds for the family pages.
	 * Membership goes by bird id in sets: a list scan per record made every almanac open
	 * quadratic in a ledger that only grows.
	 */
	public List<BirdRecord> forOwner(UUID owner) {
		List<BirdRecord> mine = new ArrayList<>();
		java.util.Set<UUID> mineIds = new java.util.HashSet<>();
		for (BirdRecord r : birds.values()) {
			if (owner.equals(r.owner())) {
				mine.add(r);
				mineIds.add(r.id());
			}
		}
		List<BirdRecord> out = new ArrayList<>(mine);
		java.util.Set<UUID> outIds = new java.util.HashSet<>(mineIds);
		for (BirdRecord r : mine) {
			addIfKnown(out, outIds, r.parentA());
			addIfKnown(out, outIds, r.parentB());
		}
		for (BirdRecord r : birds.values()) {
			if (!outIds.contains(r.id()) && (isIn(mineIds, r.parentA()) || isIn(mineIds, r.parentB()))) {
				out.add(r);
				outIds.add(r.id());
			}
		}
		return out;
	}

	private void addIfKnown(List<BirdRecord> out, java.util.Set<UUID> outIds, @Nullable UUID id) {
		BirdRecord r = id == null ? null : birds.get(id);
		if (r != null && outIds.add(r.id())) {
			out.add(r);
		}
	}

	private static boolean isIn(java.util.Set<UUID> ids, @Nullable UUID id) {
		return id != null && ids.contains(id);
	}

	private static long day(long gameTime) {
		return gameTime / 24000L;
	}
}
