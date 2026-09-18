package tk.darrow.chocobosreborn.race;

/**
 * How a Square AI racer drives, per class. Pure numbers so the ladder can be
 * unit-tested: every axis gets harder from C to S, and the named rivals
 * (Teiyo, Jolo) drive at S discipline whatever the class.
 *
 * <ul>
 * <li>{@code cruise}: multiplier on the bird's movement speed when not dashing.</li>
 * <li>{@code dash}: extra multiplier while dashing (the player's is 1.62).</li>
 * <li>{@code energyDrain} / {@code energyRecover}: per-tick change of the dash
 * budget (0..1) while dashing / cruising.</li>
 * <li>{@code dashThreshold}: energy needed before the racer will dash at all.</li>
 * <li>{@code saveForLastLap}: share of energy kept in reserve until the final lap.</li>
 * <li>{@code reactionMin/Max}: ticks of hesitation after the start.</li>
 * <li>{@code wobble}: lane noise in blocks (sloppy driving).</li>
 * <li>{@code stumbleChancePerLap}: expected stumbles (a 0.55x speed hiccup for 20
 * ticks) per lap.</li>
 * <li>{@code rubberBand}: how much the racer speeds up when the player is well
 * ahead / slows when well behind (0 = honest pace).</li>
 * <li>{@code lineHold}: how tightly it holds the inside racing line (0..1).</li>
 * </ul>
 */
public record RacerProfile(double cruise, double dash, double energyDrain, double energyRecover,
                           double dashThreshold, double saveForLastLap, int reactionMin, int reactionMax,
                           double wobble, double stumbleChancePerLap, double rubberBand, double lineHold) {

	public enum Role { FIELD, TEIYO, JOLO }

	/** Per-racer, per-heat form: each bird runs at base x (1 +- VARIANCE), rivals too. */
	public static final double VARIANCE = 0.05D;

	public static RacerProfile of(RaceClass raceClass, Role role) {
		RacerProfile base = switch (raceClass) {
			// Cruise is discipline on top of the bird's own speedMul (grade x speed training).
			// Divided by fieldTraining so a typical NPC of this class still sits near the old
			// pace; a rival at 100 training then actually pulls away on stats.
			// Reaction is measured from GO (the riders see the same countdown), so no field jumps the lights.
			case C -> new RacerProfile(0.845D, 1.22D, 1.0D / 110.0D, 1.0D / 420.0D, 0.10D, 0.00D, 16, 30, 0.90D, 1.00D, 0.10D, 0.35D);
			case B -> new RacerProfile(0.882D, 1.27D, 1.0D / 140.0D, 1.0D / 360.0D, 0.25D, 0.20D, 12, 20, 0.60D, 0.50D, 0.06D, 0.60D);
			case A -> new RacerProfile(0.887D, 1.32D, 1.0D / 170.0D, 1.0D / 300.0D, 0.30D, 0.35D, 10, 14, 0.35D, 0.20D, 0.03D, 0.80D);
			case S -> new RacerProfile(0.923D, 1.36D, 1.0D / 200.0D, 1.0D / 260.0D, 0.30D, 0.45D, 8, 11, 0.15D, 0.05D, 0.00D, 0.95D);
		};
		return switch (role) {
			case FIELD -> base;
			// the field eased 9% at Ahmi's ask; the named rivals got 5% of it back
			case TEIYO -> rival(base, 1.17D);
			case JOLO -> rival(base, 1.13D);
		};
	}

	/** A named rival: faster, and always at S-class discipline. */
	private static RacerProfile rival(RacerProfile base, double boost) {
		RacerProfile s = of(RaceClass.S, Role.FIELD);
		return new RacerProfile(base.cruise * boost, Math.max(base.dash, s.dash), Math.min(base.energyDrain, s.energyDrain),
				Math.max(base.energyRecover, s.energyRecover), s.dashThreshold, s.saveForLastLap, s.reactionMin, s.reactionMax,
				s.wobble, s.stumbleChancePerLap, 0.0D, s.lineHold);
	}

	/** Chance the racer knows a bog when it sees one (45% + 55% × lineHold). */
	public static double bogSavvyChance(double lineHold) {
		return 0.45D + 0.55D * lineHold;
	}

	/** Average speed multiplier over a lap if the racer dashes whenever it can. */
	public double averageSpeed() {
		double dashShare = energyRecover / (energyDrain + energyRecover);
		return cruise * (1.0D + (dash - 1.0D) * dashShare);
	}

	/**
	 * Whether to dash now. {@code straight} = on the flat part of the oval,
	 * {@code lastLap} = final lap, {@code gap} = laps the player is ahead (negative
	 * when the racer leads), {@code energy} = current budget 0..1.
	 */
	public boolean wantsDash(double energy, boolean straight, boolean lastLap, double gap) {
		if (energy <= 0.0D) {
			return false;
		}
		if (lastLap) {
			return energy > 0.02D;
		}
		if (energy < dashThreshold) {
			return false;
		}
		if (energy <= saveForLastLap) {
			// reserve: only spend it to answer a player who is pulling away
			return gap > 0.08D;
		}
		return straight || gap > 0.04D;
	}

	/** Speed factor from rubber-banding against the player's gap (laps ahead). */
	public double bandFactor(double gap) {
		if (rubberBand <= 0.0D) {
			return 1.0D;
		}
		double g = Math.max(-0.25D, Math.min(0.25D, gap));
		return 1.0D + rubberBand * (g / 0.25D);
	}
}
