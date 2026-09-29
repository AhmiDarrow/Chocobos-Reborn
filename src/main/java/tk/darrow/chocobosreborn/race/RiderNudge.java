package tk.darrow.chocobosreborn.race;

/**
 * The server as referee between racers that clients drive.
 *
 * <p>A rider's client moves its own bird, and sees every rival a little in the past (the
 * played-back field, plus the trip to the server). Two riders can each be clear of the other
 * on their own screens and still overlap on the server. The server does not move a rider's
 * bird (that would be a snap); it tells the rider how hard to push away, as velocity the
 * client adds to its bird, and the bird slides clear over the next few ticks.
 *
 * <p>Each bird of an overlapping pair takes half of the way out, along the shallower overlap
 * (the way out is shortest there), plus {@link #MARGIN}. A push takes a round trip to show on
 * the server, so a bird is not pushed again for {@link #cooldown} ticks unless it was driven
 * further in meanwhile.
 *
 * <p>Pure arithmetic, no Minecraft types: unit-tested in {@code RiderNudgeTest}.
 */
public final class RiderNudge {
	/** Clear space aimed for past touching, in blocks. */
	public static final double MARGIN = 0.05D;
	/** Most velocity one push adds, blocks a tick (it carries about twice that before friction stops it). */
	public static final double MAX = 0.3D;
	/** How far an impulse carries a bird on the ground: 1 / (1 - ground slip x air drag). */
	public static final double DRIFT = 1.0D / (1.0D - 0.546D);
	/** Overlaps shallower than this are left to the clients' own collision. */
	public static final double MIN_DEPTH = 0.02D;
	/** A bird driven this much further in is pushed again before its cooldown is out. */
	public static final double DEEPER = 0.1D;

	private RiderNudge() {
	}

	/**
	 * The push for the bird with box {@code a} (min/max x and z) off the bird with box
	 * {@code b}, as {dvx, dvz, depth}; null when they do not overlap. Centres decide the side.
	 */
	public static double[] push(double aMinX, double aMaxX, double aMinZ, double aMaxZ,
			double bMinX, double bMaxX, double bMinZ, double bMaxZ) {
		double ox = Math.min(aMaxX, bMaxX) - Math.max(aMinX, bMinX);
		double oz = Math.min(aMaxZ, bMaxZ) - Math.max(aMinZ, bMinZ);
		if (ox <= MIN_DEPTH || oz <= MIN_DEPTH) {
			return null;
		}
		double depth = Math.min(ox, oz);
		double dv = Math.min(MAX, (depth * 0.5D + MARGIN) / DRIFT);
		if (ox < oz) {
			double side = (aMinX + aMaxX) >= (bMinX + bMaxX) ? 1.0D : -1.0D;
			return new double[] { side * dv, 0.0D, depth };
		}
		double side = (aMinZ + aMaxZ) >= (bMinZ + bMaxZ) ? 1.0D : -1.0D;
		return new double[] { 0.0D, side * dv, depth };
	}

	/** Ticks before a pushed rider is pushed again: its round trip, and a tick for the move to land. */
	public static int cooldown(int rttMs) {
		return 2 + Math.round(Math.max(0, Math.min(1000, rttMs)) / 50.0F);
	}

	/** Whether to push now: the cooldown is out, or the bird was driven further in since the last push. */
	public static boolean due(long now, long lastPush, int cooldown, double lastDepth, double depth) {
		return now - lastPush >= cooldown || depth > lastDepth + DEEPER;
	}
}
