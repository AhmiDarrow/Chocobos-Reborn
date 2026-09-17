package tk.darrow.chocobosreborn.breed;

/**
 * Where wild yellows may appear. Cold pads must accept snow and ice so Wonderful grades can roll.
 */
public final class WildSpawn {
	private WildSpawn() {
	}

	public static boolean overworldGround(boolean animalsSpawnable, boolean snowOrIce, boolean coldBiome, boolean bright) {
		if (!bright) {
			return false;
		}
		if (animalsSpawnable) {
			return true;
		}
		return coldBiome && snowOrIce;
	}

	public static int packMin(int min, int max) {
		int low = Math.max(0, min);
		int high = Math.max(0, max);
		return Math.min(low, high);
	}

	public static int packMax(int min, int max) {
		int low = Math.max(0, min);
		int high = Math.max(0, max);
		return Math.max(low, high);
	}

	/**
	 * Ambient kweh delay in ticks. A 0-tick delay would play every tick.
	 */
	public static int kwehIntervalTicks(int limit, double unitRandom) {
		int span = Math.max(1, limit);
		double u = unitRandom;
		if (u < 0.0D) {
			u = 0.0D;
		} else if (u >= 1.0D) {
			u = 0.999999D;
		}
		return 24 * (1 + (int) (u * span));
	}
}
