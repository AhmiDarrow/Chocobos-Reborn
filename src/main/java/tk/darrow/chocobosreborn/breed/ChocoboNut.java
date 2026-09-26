package tk.darrow.chocobosreborn.breed;

import java.util.Locale;

/**
 * The eight Final Fantasy VII breeding nuts. Feeding a nut to two adults mates
 * them, as in FF7. Only Carob and Zeio change the farm line
 * (Green / Blue / Black and Gold); the others pick the chick's talent.
 *
 * Ordinals 0..2 are NONE / CAROB / ZEIO so saved "Nut" values from earlier
 * builds (which stored the strength) still read back correctly.
 */
public enum ChocoboNut {
	NONE(0, 0),
	CAROB(1, 7),
	ZEIO(2, 8),
	PEPIO(0, 1),
	LUCHILE(0, 2),
	SARAHA(0, 3),
	LASAN(0, 4),
	PRAM(0, 5),
	POROV(0, 6);

	private final int strength;
	private final int tier;

	ChocoboNut(int strength, int tier) {
		this.strength = strength;
		this.tier = tier;
	}

	/** Farm-line power: 0 plain, 1 Carob, 2 Zeio. */
	public int getStrength() {
		return strength;
	}

	/** Chick talent: a small born gift, 1 Pepio through 8 Zeio. */
	public int getTier() {
		return tier;
	}

	public static ChocoboNut stronger(ChocoboNut a, ChocoboNut b) {
		if (a.strength != b.strength) {
			return a.strength > b.strength ? a : b;
		}
		return a.tier >= b.tier ? a : b;
	}

	public static ChocoboNut byId(int id) {
		ChocoboNut[] values = values();
		if (id < 0 || id >= values.length) {
			return NONE;
		}
		return values[id];
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}
}
