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

	public static Result resolve(Color first, Color second, int firstGrade, int secondGrade,
	                             int nutStrength, int combinedWins, boolean mutationHits, boolean pickGreen) {
		if (nutStrength >= ZEIO) {
			if (isBlackAndWonderfulYellow(first, second, firstGrade, secondGrade) && mutationHits) {
				return Result.GOLD;
			}
			return Result.INHERIT;
		}
		if (nutStrength >= CAROB) {
			if (isPair(first, second, Color.GREEN, Color.BLUE)) {
				return mutationHits ? Result.BLACK : Result.WHITE;
			}
			if (bothAtLeast(first, second, Color.YELLOW, firstGrade, secondGrade, GOOD) && mutationHits) {
				return pickGreen ? Result.GREEN : Result.BLUE;
			}
		}
		return Result.NONE;
	}

	public static boolean goldPair(Color first, Color second, int firstGrade, int secondGrade) {
		return isBlackAndWonderfulYellow(first, second, firstGrade, secondGrade);
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

	private static boolean isBlackAndWonderfulYellow(Color first, Color second, int firstGrade, int secondGrade) {
		return (first == Color.BLACK && second == Color.YELLOW && secondGrade == WONDERFUL)
				|| (second == Color.BLACK && first == Color.YELLOW && firstGrade == WONDERFUL);
	}

	private static boolean bothAtLeast(Color first, Color second, Color color, int firstGrade, int secondGrade, int minGrade) {
		return first == color && second == color && firstGrade >= minGrade && secondGrade >= minGrade;
	}

	private static boolean isPair(Color a, Color b, Color one, Color two) {
		return (a == one && b == two) || (a == two && b == one);
	}
}
