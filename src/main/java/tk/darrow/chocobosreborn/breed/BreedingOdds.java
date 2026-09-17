package tk.darrow.chocobosreborn.breed;

/**
 * FF7 farm-line odds. Combined race wins scale the mutation chance up to a guarantee.
 */
public final class BreedingOdds {
	private BreedingOdds() {
	}

	/**
	 * FF7: colour breeding needs racers. Each parent must have at least
	 * {@code minEach} first-place finishes or the line cannot change at all; from
	 * there the chance climbs from 25 % to a guarantee at {@code winsForGuarantee}
	 * combined wins.
	 */
	public static double chance(int winsA, int winsB, int minEach, int winsForGuarantee) {
		if (winsA < minEach || winsB < minEach) {
			return 0.0D;
		}
		int combined = winsA + winsB;
		int floor = 2 * minEach;
		if (winsForGuarantee <= floor || combined >= winsForGuarantee) {
			return 1.0D;
		}
		return 0.25D + 0.75D * ((combined - floor) / (double) (winsForGuarantee - floor));
	}

	public static double chanceFromWins(int combinedWins, int winsForGuarantee) {
		if (winsForGuarantee <= 0) {
			return 1.0D;
		}
		if (combinedWins >= winsForGuarantee) {
			return 1.0D;
		}
		if (combinedWins <= 0) {
			return 0.10D;
		}
		return 0.10D + 0.90D * (combinedWins / (double) winsForGuarantee);
	}
}
