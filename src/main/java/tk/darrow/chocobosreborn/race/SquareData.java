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
		return tag;
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
