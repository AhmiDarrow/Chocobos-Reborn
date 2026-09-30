package tk.darrow.chocobosreborn.ledger;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.race.RaceClass;
import tk.darrow.chocobosreborn.race.RaceScoring;

/**
 * One tamed chocobo as the almanac remembers it (works while the bird's chunk is unloaded).
 * {@code wins} is every ranked first place; {@code winsC} / {@code winsB} / {@code winsA} are
 * the ones won in each class, which the farm line counts.
 */
public record BirdRecord(UUID id, UUID owner, String name, int color, int bornGrade, int grade, boolean male,
                         int raceClass, int wins, int classWins, int winsC, int winsB, int winsA, int trSpeed, int trStamina, int trIntel, int trCoop,
                         int geneSpeed, int geneStamina, int geneIntel, int geneCoop, int spark,
                         @Nullable UUID parentA, @Nullable UUID parentB, int parentColorA, int parentColorB,
                         int nut, long bornDay, boolean alive, String pendingName) {

	public static BirdRecord of(ChocoboEntity b, @Nullable UUID parentA, @Nullable UUID parentB,
	                            int parentColorA, int parentColorB, int nut, long bornDay, boolean alive) {
		UUID owner = b.getOwnerUUID() == null ? new UUID(0L, 0L) : b.getOwnerUUID();
		String name = b.hasCustomName() ? b.getCustomName().getString() : "";
		return new BirdRecord(b.getUUID(), owner, name, b.color().getId(), b.bornGrade().getRank(), b.grade().getRank(),
				b.male(), b.raceClass().getId(), b.raceWins(), b.classWins(), b.winsInClass(RaceClass.C),
				b.winsInClass(RaceClass.B), b.winsInClass(RaceClass.A), b.trainedSpeed(), b.trainedStamina(),
				b.trainedIntelligence(), b.trainedCooperation(), b.geneSpeed(), b.geneStamina(),
				b.geneIntelligence(), b.geneCooperation(), b.spark(), parentA, parentB, parentColorA, parentColorB, nut,
				bornDay, alive, "");
	}

	/** Ranked first places won in {@code raceClass}; Class S wins count toward no stage. */
	public int winsIn(RaceClass raceClass) {
		return switch (raceClass) {
			case C -> winsC;
			case B -> winsB;
			case A -> winsA;
			case S -> 0;
		};
	}

	/** Stored when the almanac clears a name on an unloaded bird (empty pending used to mean "none"). */
	public static final String PENDING_CLEAR = "\0";

	/**
	 * Fresh stats from a live bird, keeping the lineage fields of the old record. A
	 * pending rename (made from the almanac while the bird was unloaded) is applied
	 * to the entity now.
	 */

	public BirdRecord refreshed(ChocoboEntity b) {
		if (pendingName != null && !pendingName.isEmpty()) {
			if (PENDING_CLEAR.equals(pendingName)) {
				b.setCustomName(null);
				b.setCustomNameVisible(false);
			} else if (!pendingName.equals(b.hasCustomName() ? b.getCustomName().getString() : "")) {
				b.setCustomName(net.minecraft.network.chat.Component.literal(pendingName));
				b.setCustomNameVisible(true);
			}
		}
		return of(b, parentA, parentB, parentColorA, parentColorB, nut, bornDay, b.isAlive());
	}

	public BirdRecord dead() {
		return new BirdRecord(id, owner, name, color, bornGrade, grade, male, raceClass, wins, classWins, winsC, winsB, winsA, trSpeed,
				trStamina, trIntel, trCoop, geneSpeed, geneStamina, geneIntel, geneCoop, spark, parentA, parentB,
				parentColorA, parentColorB, nut, bornDay, false, pendingName);
	}

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putUUID("Id", id);
		t.putUUID("Owner", owner);
		t.putString("Name", name);
		t.putInt("Color", color);
		t.putInt("BornGrade", bornGrade);
		t.putInt("Grade", grade);
		t.putBoolean("Male", male);
		t.putInt("Class", raceClass);
		t.putInt("Wins", wins);
		t.putInt("ClassWins", classWins);
		t.putIntArray("WinsByClass", new int[]{winsC, winsB, winsA});
		t.putInt("Ladder", tk.darrow.chocobosreborn.race.RaceClass.LADDER_MARK);
		t.putInt("TrSpeed", trSpeed);
		t.putInt("TrStamina", trStamina);
		t.putInt("TrIntel", trIntel);
		t.putInt("TrCoop", trCoop);
		t.putInt("GeneSpeed", geneSpeed);
		t.putInt("GeneStamina", geneStamina);
		t.putInt("GeneIntel", geneIntel);
		t.putInt("GeneCoop", geneCoop);
		t.putInt("Spark", spark);
		if (parentA != null) {
			t.putUUID("ParentA", parentA);
		}
		if (parentB != null) {
			t.putUUID("ParentB", parentB);
		}
		t.putInt("ParentColorA", parentColorA);
		t.putInt("ParentColorB", parentColorB);
		t.putInt("Nut", nut);
		t.putLong("BornDay", bornDay);
		t.putBoolean("Alive", alive);
		t.putString("Pending", pendingName == null ? "" : pendingName);
		return t;
	}

	/**
	 * Class points as the almanac shows them. Older ladder marks are brought onto the
	 * current per-class bars (C 36 / B 54 / A 72) the same way a loaded bird is.
	 */
	static int ladderPoints(CompoundTag t) {
		int stored = t.getInt("ClassWins");
		int ladder = t.getInt("Ladder");
		if (ladder == tk.darrow.chocobosreborn.race.RaceClass.LADDER_MARK) {
			return stored;
		}
		if (ladder == tk.darrow.chocobosreborn.race.RaceClass.POINTS_TO_PROMOTE) {
			return tk.darrow.chocobosreborn.race.RaceScoring.scaledToPerClass(t.getInt("Class"), stored);
		}
		return tk.darrow.chocobosreborn.race.RaceScoring.convertedClassPoints(2, t.getInt("Class"), stored);
	}

	/**
	 * First places per class, {C, B, A}. A record written before they were kept shares its
	 * lifetime wins out the same way a loaded bird does ({@link RaceScoring#migratedWinsByClass}).
	 */
	static int[] winsByClass(CompoundTag t) {
		int[] stored = t.getIntArray("WinsByClass");
		if (t.contains("WinsByClass") && stored.length == 3) {
			return stored;
		}
		return RaceScoring.migratedWinsByClass(t.getInt("Class"), t.getInt("Wins"));
	}

	public static BirdRecord load(CompoundTag t) {
		int[] byClass = winsByClass(t);
		return new BirdRecord(t.getUUID("Id"), t.getUUID("Owner"), t.getString("Name"), t.getInt("Color"),
				t.getInt("BornGrade"), t.getInt("Grade"), t.getBoolean("Male"), t.getInt("Class"), t.getInt("Wins"),
				ladderPoints(t), byClass[0], byClass[1], byClass[2], t.getInt("TrSpeed"), t.getInt("TrStamina"), t.getInt("TrIntel"), t.getInt("TrCoop"),
				t.getInt("GeneSpeed"), t.getInt("GeneStamina"), t.getInt("GeneIntel"), t.getInt("GeneCoop"),
				t.contains("Spark") ? t.getInt("Spark") : -1,
				t.hasUUID("ParentA") ? t.getUUID("ParentA") : null, t.hasUUID("ParentB") ? t.getUUID("ParentB") : null,
				t.getInt("ParentColorA"), t.getInt("ParentColorB"), t.getInt("Nut"), t.getLong("BornDay"),
				!t.contains("Alive") || t.getBoolean("Alive"), t.getString("Pending"));
	}
}
