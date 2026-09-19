package tk.darrow.chocobosreborn.race;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Chocobo Square bookkeeping, stored on the Square level: which course is
 * built, whether the town keepers exist, and where each visitor came from.
 */
public class SquareData extends SavedData {
	public static final String NAME = "chocobosreborn_square";

	public record ReturnPoint(ResourceKey<Level> dimension, Vec3 pos, float yaw) {
	}

	private final java.util.Set<Integer> builtTracks = new java.util.HashSet<>();
	private boolean paddockBuilt;
	private int paddockVersion;
	/** Course plan version the built islands were laid with; a newer plan relays them on next use. */
	private int courseVersion;
	private boolean keepersSpawned;
	private final Map<UUID, ReturnPoint> returns = new HashMap<>();
	private final Map<UUID, Integer> owedGp = new HashMap<>();
	private CompoundTag heats = new CompoundTag();
	/** Bookie and duel stakes held by live heats and open duel challenges; anything still here on load was lost to a crash. */
	private final Map<UUID, Integer> heldStakes = new HashMap<>();
	/** Course chunks a live heat has force-loaded; anything still here at server start is unforced. */
	private final java.util.Set<Long> forcedChunks = new java.util.HashSet<>();

	public static SquareData get(ServerLevel squareLevel) {
		return squareLevel.getDataStorage().computeIfAbsent(
				new SavedData.Factory<>(SquareData::new, SquareData::load), NAME);
	}

	public static SquareData load(CompoundTag tag, HolderLookup.Provider registries) {
		SquareData d = new SquareData();
		for (int id : tag.getIntArray("BuiltTracks")) {
			d.builtTracks.add(id);
		}
		d.paddockBuilt = tag.getBoolean("PaddockBuilt");
		d.paddockVersion = tag.getInt("PaddockVersion");
		d.courseVersion = tag.getInt("CourseVersion");
		d.keepersSpawned = tag.getBoolean("KeepersSpawned");
		ListTag list = tag.getList("Returns", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag r = list.getCompound(i);
			ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
					ResourceLocation.parse(r.getString("Dim")));
			d.returns.put(r.getUUID("Player"), new ReturnPoint(dim,
					new Vec3(r.getDouble("X"), r.getDouble("Y"), r.getDouble("Z")), r.getFloat("Yaw")));
		}
		ListTag owed = tag.getList("OwedGp", Tag.TAG_COMPOUND);
		for (int i = 0; i < owed.size(); i++) {
			CompoundTag r = owed.getCompound(i);
			if (r.hasUUID("Player")) {
				d.owedGp.put(r.getUUID("Player"), r.getInt("Amount"));
			}
		}
		d.heats = tag.getCompound("Heats").copy();
		// stakes a heat was holding when the server went down uncleanly: hand them back
		ListTag held = tag.getList("HeldStakes", Tag.TAG_COMPOUND);
		for (int i = 0; i < held.size(); i++) {
			CompoundTag r = held.getCompound(i);
			if (r.hasUUID("Player") && r.getInt("Amount") > 0) {
				d.owedGp.merge(r.getUUID("Player"), r.getInt("Amount"), Integer::sum);
				d.setDirty();
			}
		}
		for (long key : tag.getLongArray("ForcedChunks")) {
			d.forcedChunks.add(key);
		}
		return d;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putIntArray("BuiltTracks", builtTracks.stream().mapToInt(Integer::intValue).toArray());
		tag.putBoolean("PaddockBuilt", paddockBuilt);
		tag.putInt("PaddockVersion", paddockVersion);
		tag.putInt("CourseVersion", courseVersion);
		tag.putBoolean("KeepersSpawned", keepersSpawned);
		ListTag list = new ListTag();
		returns.forEach((id, rp) -> {
			CompoundTag r = new CompoundTag();
			r.putUUID("Player", id);
			r.putString("Dim", rp.dimension().location().toString());
			r.putDouble("X", rp.pos().x);
			r.putDouble("Y", rp.pos().y);
			r.putDouble("Z", rp.pos().z);
			r.putFloat("Yaw", rp.yaw());
			list.add(r);
		});
		tag.put("Returns", list);
		ListTag owed = new ListTag();
		owedGp.forEach((id, n) -> {
			CompoundTag r = new CompoundTag();
			r.putUUID("Player", id);
			r.putInt("Amount", n);
			owed.add(r);
		});
		tag.put("OwedGp", owed);
		tag.put("Heats", heats.copy());
		ListTag held = new ListTag();
		heldStakes.forEach((id, n) -> {
			CompoundTag r = new CompoundTag();
			r.putUUID("Player", id);
			r.putInt("Amount", n);
			held.add(r);
		});
		tag.put("HeldStakes", held);
		tag.putLongArray("ForcedChunks", forcedChunks.stream().mapToLong(Long::longValue).toArray());
		return tag;
	}

	public void holdStake(UUID player, int amount) {
		if (amount > 0) {
			heldStakes.merge(player, amount, Integer::sum);
			setDirty();
		}
	}

	public void releaseStake(UUID player, int amount) {
		if (amount > 0 && heldStakes.containsKey(player)) {
			heldStakes.computeIfPresent(player, (k, n) -> n - amount > 0 ? n - amount : null);
			setDirty();
		}
	}

	public void addForcedChunks(java.util.Collection<Long> keys) {
		if (forcedChunks.addAll(keys)) {
			setDirty();
		}
	}

	public void removeForcedChunks(java.util.Collection<Long> keys) {
		if (forcedChunks.removeAll(keys)) {
			setDirty();
		}
	}

	/** Server start: chunks a crashed heat left force-loaded. Clears the record. */
	public java.util.Set<Long> takeForcedChunks() {
		java.util.Set<Long> out = new java.util.HashSet<>(forcedChunks);
		if (!out.isEmpty()) {
			forcedChunks.clear();
			setDirty();
		}
		return out;
	}

	public CompoundTag heats() {
		return heats.copy();
	}

	public void setHeats(CompoundTag tag) {
		heats = tag == null ? new CompoundTag() : tag.copy();
		setDirty();
	}

	public void oweGp(UUID player, int amount) {
		if (amount > 0) {
			owedGp.merge(player, amount, Integer::sum);
			setDirty();
		}
	}

	public int takeOwedGp(UUID player) {
		Integer n = owedGp.remove(player);
		if (n != null) {
			setDirty();
			return n;
		}
		return 0;
	}

	public boolean isBuilt(RaceTrack track) {
		return builtTracks.contains(track.ordinal());
	}

	public void setBuilt(RaceTrack track) {
		builtTracks.add(track.ordinal());
		setDirty();
	}

	/** Forget every built course (a world reset); courses are re-laid on first use. */
	public void clearBuilt() {
		builtTracks.clear();
		setDirty();
	}

	public boolean paddockBuilt() {
		return paddockBuilt;
	}

	public void setPaddockBuilt(boolean v) {
		paddockBuilt = v;
		setDirty();
	}

	/** Which {@link SquareBuilder#PADDOCK_VERSION} of the village is in the world (0 = none). */
	public int paddockVersion() {
		return paddockVersion;
	}

	public int courseVersion() {
		return courseVersion;
	}

	public void setCourseVersion(int v) {
		courseVersion = v;
		setDirty();
	}

	public void setPaddockVersion(int v) {
		paddockVersion = v;
		paddockBuilt = v > 0;
		setDirty();
	}

	public boolean keepersSpawned() {
		return keepersSpawned;
	}

	public void setKeepersSpawned(boolean v) {
		keepersSpawned = v;
		setDirty();
	}

	public void putReturn(UUID player, ReturnPoint rp) {
		returns.put(player, rp);
		setDirty();
	}

	public @Nullable ReturnPoint takeReturn(UUID player) {
		ReturnPoint rp = returns.remove(player);
		if (rp != null) {
			setDirty();
		}
		return rp;
	}

	public @Nullable ReturnPoint peekReturn(UUID player) {
		return returns.get(player);
	}
}
