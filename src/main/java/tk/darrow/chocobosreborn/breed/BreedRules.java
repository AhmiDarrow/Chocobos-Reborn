package tk.darrow.chocobosreborn.breed;

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

	/** The End and Nether birds count as Yellow in the farm line and cannot pass their colour on. */
	public static ChocoboColor asBreedingColor(ChocoboColor c) {
		return c == ChocoboColor.PURPLE || c == ChocoboColor.FLAME ? ChocoboColor.YELLOW : c;
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
	                                   ChocoboNut nut, int combinedWins,
	                                   boolean mutationHits, boolean pickGreen,
	                                   ChocoboColor inherit) {
		inherit = asBreedingColor(inherit);
		Ff7Line.Result result = Ff7Line.resolve(
				first.toLine(), second.toLine(),
				firstGrade.getRank(), secondGrade.getRank(),
				nut.getStrength(), combinedWins, mutationHits, pickGreen);
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
