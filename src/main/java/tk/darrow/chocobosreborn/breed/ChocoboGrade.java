package tk.darrow.chocobosreborn.breed;

import java.util.Locale;

/**
 * Quality ranks used by the Ninjacat Skies breeding line.
 * Mapped from Final Fantasy VII's Poor / Average / Good / Great / Wonderful grades.
 */
public enum ChocoboGrade {
	POOR(0),
	AVERAGE(1),
	GOOD(2),
	GREAT(3),
	WONDERFUL(4);

	private final int rank;

	ChocoboGrade(int rank) {
		this.rank = rank;
	}

	public int getRank() {
		return rank;
	}

	public boolean atLeast(ChocoboGrade other) {
		return this.rank >= other.rank;
	}

	public ChocoboGrade raise() {
		int next = Math.min(WONDERFUL.rank, this.rank + 1);
		return byRank(next);
	}

	public static ChocoboGrade byRank(int rank) {
		ChocoboGrade[] values = values();
		if (rank < 0) {
			return POOR;
		}
		if (rank >= values.length) {
			return WONDERFUL;
		}
		return values[rank];
	}

	public static ChocoboGrade byId(int id) {
		return byRank(id);
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}
}
