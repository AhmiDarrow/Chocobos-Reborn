package tk.darrow.chocobosreborn.breed;

import tk.darrow.chocobosreborn.race.RaceClass;

/**
 * FF7 farm-line odds. Combined race wins scale the mutation chance up to a guarantee.
 * Each stage of the line counts only the first places won in its own class
 * ({@link BreedRules#stageClass}): Green / Blue in Class C, Black in Class B, Gold in Class A.
 */
public final class BreedingOdds {
	private BreedingOdds() {
	}

	/**
	 * Class C wins each parent needs before a Carob can turn two Yellows. Higher classes
	 * scale this by their promotion bar against {@link #REFERENCE_BAR}, rounded up:
	 * C 5, B 8, A 10 ({@link #minWinsEach}).
	 */
	public static final int BASE_WINS_EACH = 5;
	/**
	 * Class C wins per parent that make the roll certain: one sprint short of promotion.
	 * Scaled like {@link #BASE_WINS_EACH} and doubled for the pair: C 16, B 24, A 32
	 * ({@link #guaranteeWins}).
	 */
	public static final int BASE_GUARANTEE_EACH = 8;
	/** The bar the base numbers are set against: Class C's promotion, 36 points. */
	public static final int REFERENCE_BAR = RaceClass.POINTS_TO_PROMOTE;

	/** First places in {@code raceClass} each parent needs: ceil(5 x bar / 36). */
	public static int minWinsEach(RaceClass raceClass) {
		return scaled(BASE_WINS_EACH, raceClass);
	}

	/** Combined first places in {@code raceClass} that make the roll certain: 2 x ceil(8 x bar / 36). */
	public static int guaranteeWins(RaceClass raceClass) {
		return 2 * scaled(BASE_GUARANTEE_EACH, raceClass);
	}

	private static int scaled(int base, RaceClass raceClass) {
		return Math.ceilDiv(base * raceClass.pointsToPromote(), REFERENCE_BAR);
	}

	/**
	 * FF7: colour breeding needs racers. Each parent must have at least
	 * {@code minEach} first-place finishes in the stage's class or the line cannot
	 * change at all; from there the chance climbs from 25 % to a guarantee at
	 * {@code winsForGuarantee} combined wins in that class.
	 */
	public static double chance(int winsA, int winsB, int minEach, int winsForGuarantee) {
		if (!qualifies(winsA, winsB, minEach)) {
			return 0.0D;
		}
		int combined = winsA + winsB;
		int floor = 2 * minEach;
		if (winsForGuarantee <= floor || combined >= winsForGuarantee) {
			return 1.0D;
		}
		return 0.25D + 0.75D * ((combined - floor) / (double) (winsForGuarantee - floor));
	}

	/** Both parents have at least {@code minEach} first-place finishes, so the line may change. */
	public static boolean qualifies(int winsA, int winsB, int minEach) {
		return winsA >= minEach && winsB >= minEach;
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
