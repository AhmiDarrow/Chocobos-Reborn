package tk.darrow.chocobosreborn.race;

import java.util.Locale;

/**
 * Gold Saucer / Chocobo Square classes from Final Fantasy VII.
 * Ranked wins earn points by the heat's length ({@link RaceScoring#winPoints});
 * {@link #POINTS_TO_PROMOTE} of them promote a bird; class never drops.
 */
public enum RaceClass {
	C(0),
	B(1),
	A(2),
	S(3);

	/**
	 * Points to leave a class: nine sprint wins (4 each), or fewer grand prix wins (each
	 * worth more, by the heat's length). See {@link RaceScoring#winPoints}.
	 */
	public static final int POINTS_TO_PROMOTE = 36;

	private final int id;

	RaceClass(int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}

	public boolean includesTeioh() {
		return this != C;
	}

	public RaceClass next() {
		return byId(Math.min(S.id, this.id + 1));
	}

	public static RaceClass byId(int id) {
		RaceClass[] values = values();
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
