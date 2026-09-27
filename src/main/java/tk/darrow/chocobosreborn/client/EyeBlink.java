package tk.darrow.chocobosreborn.client;

/** Client-local blink timing: short blinks, staggered across a flock. */
final class EyeBlink {
	private EyeBlink() {}
	static boolean closed(int ticks, int entityId) {
		if (ticks < 20) return false;
		int period = 110 + (int) Math.floorMod((long) entityId * 31, 71);
		return Math.floorMod((long) ticks + (long) entityId * 37, period) < 3;
	}
}
