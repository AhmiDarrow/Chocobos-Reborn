package tk.darrow.chocobosreborn.race;

import java.util.Locale;

/**
 * Gold Saucer / Chocobo Square classes from Final Fantasy VII.
 * Ranked wins earn points by the heat's length ({@link RaceScoring#winPoints});
 * {@link #pointsToPromote()} of them promote a bird; class never drops.
 */
public enum RaceClass {
	C(0),
	B(1),
	A(2),
	S(3);

	/** Class C bar (nine sprint wins). B and A scale up from here. */
	public static final int POINTS_TO_PROMOTE = 36;
	/** Class B bar: 1.5× Class C. */
	public static final int POINTS_B = 54;
	/** Class A bar (and Class S display): 2× Class C. */
	public static final int POINTS_A = 72;
	/**
	 * Almanac / bird-record mark that class points are already on the per-class ladder
	 * (format 4). Older saves used {@link #POINTS_TO_PROMOTE} (36) for the uniform ladder.
	 */
	public static final int LADDER_MARK = POINTS_A;

	private final int id;

	RaceClass(int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}

	/**
	 * Points needed to leave this class. Class S is the top: the almanac shows the
	 * Class A bar and never promotes further.
	 */
	public int pointsToPromote() {
		return switch (this) {
			case C -> POINTS_TO_PROMOTE;
			case B -> POINTS_B;
			case A, S -> POINTS_A;
		};
	}

	/**
	 * Named rivals can take two stalls on a ranked card: Ahmi and Risika in Class C,
	 * Teiyo and Jolo from Class B up. Whether they run is rolled per heat
	 * ({@link #rollRivals}), not fixed by the class.
	 */
	public boolean includesTeioh() {
		return true;
	}

	/** The share of ranked heats the named pair runs in (Ahmi: two in three; half was too rare). */
	public static final double RIVAL_CHANCE = 2.0D / 3.0D;

	/** One heat's roll ({@code roll} uniform in [0, 1)): do this class's named rivals run it? */
	public boolean rollRivals(double roll) {
		return includesTeioh() && roll < RIVAL_CHANCE;
	}

	/** Class C's named pair (Ahmi / Risika) instead of Teiyo / Jolo. */
	public boolean cClassRivals() {
		return this == C;
	}

	public RaceClass next() {
		return byId(Math.min(S.id, this.id + 1));
	}

	private static final RaceClass[] VALUES = values();

	public static RaceClass byId(int id) {
		RaceClass[] values = VALUES;
		if (id < 0) {
			return C;
		}
		if (id >= values.length) {
			return S;
		}
		return values[id];
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}
}
