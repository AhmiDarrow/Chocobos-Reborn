package tk.darrow.chocobosreborn.race;

/** GP stack maths for payouts (Chocobos Reborn is standalone: GP is its only money). */
public final class RaceCurrency {
	private RaceCurrency() {
	}

	/** Field 6x on 16 GP is 96, over a 64 stack. */
	public static int[] stacks(int count, int maxStack) {
		if (count < 1) {
			return new int[0];
		}
		int max = Math.max(1, maxStack);
		int[] out = new int[(count + max - 1) / max];
		for (int i = 0; i < out.length; i++) {
			out[i] = Math.min(max, count);
			count -= out[i];
		}
		return out;
	}
}
