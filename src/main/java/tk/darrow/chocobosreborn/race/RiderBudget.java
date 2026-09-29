package tk.darrow.chocobosreborn.race;

/**
 * How far a rider's bird may move, in pure numbers ({@link RiderAuthority} applies them; no Minecraft
 * types, so unit tests can load it).
 *
 * <p>A rider may cover {@link #PER_TICK} blocks a server tick, above the fastest legitimate race
 * bird (about 4.6 at the 99.9th percentile of the harness races). Unused movement is banked up
 * to {@link #BANK_TICKS} ticks' worth, so the backlog a client sends after a stall catches up in
 * one go, and no single packet may step more than {@link #STEP_CAP} blocks (vanilla's limit).
 */
public final class RiderBudget {
	public static final double PER_TICK = 6.0D;
	public static final double BANK_TICKS = 20.0D;
	public static final double STEP_CAP = 10.0D;
	public static final double CAP = PER_TICK * BANK_TICKS;

	private RiderBudget() {}

	/** The bank at tick {@code now}, last spent at {@code last} (Long.MIN_VALUE: never spent). */
	public static double refill(double budget, long last, long now) {
		if (last == Long.MIN_VALUE) {
			return CAP;
		}
		return Math.min(CAP, budget + Math.max(0L, now - last) * PER_TICK);
	}
}
