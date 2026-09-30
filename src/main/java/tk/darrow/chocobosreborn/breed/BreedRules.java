package tk.darrow.chocobosreborn.breed;

import tk.darrow.chocobosreborn.race.RaceClass;

/**
 * Hatch-color helpers that sit on top of {@link Ff7Line} without Minecraft entities.
 */
public final class BreedRules {
	private BreedRules() {
	}

	public static ChocoboColor withoutUnseededGold(ChocoboColor color, ChocoboColor inherit, ChocoboNut nut) {
		if (color != ChocoboColor.GOLD || Ff7Line.allowsGold(nut.getStrength())) {
			return color;
		}
		return inherit == ChocoboColor.GOLD ? ChocoboColor.YELLOW : inherit;
	}

	/**
	 * The class whose first places a pairing counts ({@link BreedingOdds}): Zeio (Gold) is
	 * Class A, Carob on Green + Blue (Black) is Class B, any other Carob pairing (Yellows to
	 * Green / Blue) is Class C.
	 */
	public static RaceClass stageClass(ChocoboColor a, ChocoboColor b, ChocoboNut nut) {
		if (nut == ChocoboNut.ZEIO) {
			return RaceClass.A;
		}
		boolean greenBlue = (a == ChocoboColor.GREEN && b == ChocoboColor.BLUE)
				|| (a == ChocoboColor.BLUE && b == ChocoboColor.GREEN);
		return greenBlue ? RaceClass.B : RaceClass.C;
	}

	/**
	 * The stage a single bird fed {@code nut} is heading for: a Green or Blue on Carob is
	 * after Black (Class B), so it is paired with the other colour.
	 */
	public static RaceClass stageClass(ChocoboColor bird, ChocoboNut nut) {
		ChocoboColor mate = bird == ChocoboColor.GREEN ? ChocoboColor.BLUE
				: bird == ChocoboColor.BLUE ? ChocoboColor.GREEN : bird;
		return stageClass(bird, mate, nut);
	}

	/** End and Nether birds are not on the Yellow farm line; they still pass their own colour. */
	public static ChocoboColor asBreedingColor(ChocoboColor c) {
		return c;
	}

	public static boolean randomClauseMatches(String random, int randColor) {
		if (random == null || random.equals("none")) {
			return true;
		}
		String[] parts = random.split(" ");
		if (parts.length < 2) {
			return false;
		}
		int threshold;
		try {
			threshold = Integer.parseInt(parts[1]);
		} catch (NumberFormatException ignored) {
			return false;
		}
		if (parts[0].equals("above")) {
			return randColor > threshold;
		}
		if (parts[0].equals("under")) {
			return randColor < threshold;
		}
		return true;
	}

	public static ChocoboColor resolve(ChocoboColor first, ChocoboColor second,
	                                   ChocoboGrade firstGrade, ChocoboGrade secondGrade,
	                                   ChocoboNut nut, boolean winsQualify,
	                                   boolean mutationHits, boolean pickGreen,
	                                   ChocoboColor inherit) {
		inherit = asBreedingColor(inherit);
		Ff7Line.Result result = Ff7Line.resolve(
				first.toLine(), second.toLine(),
				firstGrade.getRank(), secondGrade.getRank(),
				nut.getStrength(), winsQualify, mutationHits, pickGreen);
		ChocoboColor color = switch (result) {
			case GOLD -> ChocoboColor.GOLD;
			case BLACK -> ChocoboColor.BLACK;
			case WHITE -> ChocoboColor.WHITE;
			case GREEN -> ChocoboColor.GREEN;
			case BLUE -> ChocoboColor.BLUE;
			case INHERIT -> inherit;
			case NONE -> inherit;
		};
		return withoutUnseededGold(color, inherit, nut);
	}
}
