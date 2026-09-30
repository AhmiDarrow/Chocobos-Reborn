package tk.darrow.chocobosreborn.breed;

/**
 * Final Fantasy VII farm-line color resolution, independent of Minecraft entities.
 */
public final class Ff7Line {
	public enum Color {
		YELLOW, GREEN, BLUE, WHITE, BLACK, GOLD, OTHER
	}

	public enum Result {
		GOLD, BLACK, WHITE, GREEN, BLUE, INHERIT, NONE
	}

	public static final int GOOD = 2;
	public static final int GREAT = 3;
	public static final int WONDERFUL = 4;
	public static final int CAROB = 1;
	public static final int ZEIO = 2;

	private Ff7Line() {
	}

	/**
	 * {@code winsQualify}: both parents have the first-place finishes this pairing needs
	 * in its class ({@link BreedingOdds#qualifies}). Without them the line cannot change
	 * at all, so a Green + Blue pair on Carob hatches a parent's colour instead of White.
	 * The same goes for grade: Good Yellows for Green / Blue, a Great Green and a Great
	 * Blue for Black, a Wonderful Black and a Wonderful Yellow for Gold.
	 */
	public static Result resolve(Color first, Color second, int firstGrade, int secondGrade,
	                             int nutStrength, boolean winsQualify, boolean mutationHits, boolean pickGreen) {
		if (nutStrength >= ZEIO) {
			if (isWonderfulBlackAndYellow(first, second, firstGrade, secondGrade) && mutationHits) {
				return Result.GOLD;
			}
			return Result.INHERIT;
		}
		if (nutStrength >= CAROB) {
			if (isPair(first, second, Color.GREEN, Color.BLUE)) {
				if (!winsQualify || firstGrade < GREAT || secondGrade < GREAT) {
					return Result.NONE;
				}
				return mutationHits ? Result.BLACK : Result.WHITE;
			}
			if (bothAtLeast(first, second, Color.YELLOW, firstGrade, secondGrade, GOOD) && mutationHits) {
				return pickGreen ? Result.GREEN : Result.BLUE;
			}
		}
		return Result.NONE;
	}

	public static boolean goldPair(Color first, Color second, int firstGrade, int secondGrade) {
		return isWonderfulBlackAndYellow(first, second, firstGrade, secondGrade);
	}

	public static boolean allowsGold(int nutStrength) {
		return nutStrength >= ZEIO;
	}

	public static Color stripGoldWithoutZeio(Color child, int nutStrength) {
		if (child == Color.GOLD && !allowsGold(nutStrength)) {
			return Color.YELLOW;
		}
		return child;
	}

	private static boolean isWonderfulBlackAndYellow(Color first, Color second, int firstGrade, int secondGrade) {
		return isPair(first, second, Color.BLACK, Color.YELLOW) && firstGrade >= WONDERFUL && secondGrade >= WONDERFUL;
	}

	private static boolean bothAtLeast(Color first, Color second, Color color, int firstGrade, int secondGrade, int minGrade) {
		return first == color && second == color && firstGrade >= minGrade && secondGrade >= minGrade;
	}

	private static boolean isPair(Color a, Color b, Color one, Color two) {
		return (a == one && b == two) || (a == two && b == one);
	}
}
