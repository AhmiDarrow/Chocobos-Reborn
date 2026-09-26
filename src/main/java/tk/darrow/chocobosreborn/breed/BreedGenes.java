package tk.darrow.chocobosreborn.breed;

/**
 * Bloodline stats. Greens make this bird fast, and only a tenth of that work
 * enters the blood the next foal can inherit, so a line climbs over many
 * clutches. Most chicks land a little under that blood. One chick in eight
 * sparks one stat toward the stronger parent. A born stat above {@link #LEGEND}
 * still needs that spark, and only when both parents already offer
 * {@link #LEGEND_BLOOD} or more.
 */
public final class BreedGenes {
	/** Percent of greens that stick in the blood. The rest stays with this bird. */
	public static final int STICKS = 10;
	/** Extra born points on the sparked stat. */
	public static final int SPARK = 5;
	/** One chick in this many sparks a stat. The rest take the middle lean. */
	public static final int SPARK_ODDS = 8;
	/** Born stats above this are legendary and stay gated. */
	public static final int LEGEND = 96;
	/** Both parents must already offer this blood before a spark can pass the gate. */
	public static final int LEGEND_BLOOD = 85;

	private BreedGenes() {
	}

	/** What this bird races with: born plus greens, capped at 100. */
	public static int passed(int gene, int trained) {
		return Math.min(ChocoboGreen.MAX_POINTS, Math.max(0, gene) + Math.max(0, trained));
	}

	/** What the next foal can inherit from this stat. */
	public static int blood(int gene, int trained) {
		int born = Math.max(0, Math.min(ChocoboGreen.MAX_POINTS, gene));
		int fed = Math.max(0, Math.min(ChocoboGreen.MAX_POINTS, trained));
		return Math.min(ChocoboGreen.MAX_POINTS, born + fed * STICKS / 100);
	}

	/** A small floor from the chick's born grade. Wonderful is 8, not a head start on a perfect bird. */
	public static int gradeFloor(int bornRank) {
		int rank = Math.max(0, Math.min(ChocoboGrade.WONDERFUL.getRank(), bornRank));
		return rank * 2;
	}

	/**
	 * 0 leans to the lower parent, 100 to the higher. A sparked stat sits at
	 * 60..80. The other stats stay near the middle so the line does not forget them.
	 */
	public static int favorLean(boolean sparked, int roll0to100) {
		int roll = Math.max(0, Math.min(100, roll0to100));
		return (sparked ? 60 : 40) + roll / 5;
	}

	/** Wild blood: the grade floor plus up to 8, so a wonderful stray starts ahead of a poor one. */
	public static int wildGene(int floor, int roll) {
		return Math.min(ChocoboGreen.MAX_POINTS, Math.max(0, floor) + Math.floorMod(roll, 9));
	}

	/** Speed and stamina take the nut's tier. Intelligence and cooperation take half, rounded down. */
	public static int nutGift(int tier, boolean primary) {
		int t = Math.max(0, tier);
		return primary ? t : t / 2;
	}

	/**
	 * One born stat from the parents' blood. {@code lean0to100} picks between them.
	 * {@code wobble} is clamped to -12..12. {@code spark} is the extra on a sparked
	 * stat, or 0. The grade floor lifts a low roll, then the nut. A result above
	 * {@link #LEGEND} is cut back there unless this stat sparked and both parents
	 * already offer {@link #LEGEND_BLOOD}.
	 */
	public static int childGene(int bloodA, int bloodB, int nutGift, int lean0to100, int wobble, int floor, int spark) {
		int a = Math.max(0, bloodA);
		int b = Math.max(0, bloodB);
		int low = Math.min(a, b);
		int high = Math.max(a, b);
		int lean = Math.max(0, Math.min(100, lean0to100));
		int picked = low + (high - low) * lean / 100;
		int wob = Math.max(-12, Math.min(12, wobble));
		int born = clamp(picked + wob + Math.max(0, spark));
		int lifted = Math.max(born, Math.max(0, Math.min(ChocoboGreen.MAX_POINTS, floor)));
		int gifted = clamp(lifted + Math.max(0, nutGift));
		if (gifted > LEGEND && (spark <= 0 || Math.min(a, b) < LEGEND_BLOOD)) {
			return LEGEND;
		}
		return gifted;
	}

	/** True when the foal's born stat is past both parents' blood. */
	public static boolean stepsUp(int chickGene, int parentBloodA, int parentBloodB) {
		return chickGene > Math.max(parentBloodA, parentBloodB);
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(ChocoboGreen.MAX_POINTS, value));
	}
}
