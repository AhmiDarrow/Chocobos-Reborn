package tk.darrow.chocobosreborn.race;

import java.util.Locale;

/**
 * Gold Saucer / Chocobo Square classes from Final Fantasy VII.
 * Three first-place finishes promote a bird; class never drops.
 */
public enum RaceClass {
	C(0),
	B(1),
	A(2),
	S(3);

	/** Points to leave a class. A sprint win is 1, a grand prix win is 3. */
	public static final int POINTS_TO_PROMOTE = 9;
	public static final int SPRINT_POINTS = 1;
	public static final int GRAND_PRIX_POINTS = 3;

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
