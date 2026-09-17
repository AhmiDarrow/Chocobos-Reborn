package tk.darrow.chocobosreborn.breed;

import java.util.Locale;

/**
 * Plumage and movement, FF7's terrain rules: Yellow crosses nothing special, Green
 * crosses mountains (climbs), Blue crosses rivers (walks on shallow water), Black
 * does both, Gold does everything: mountains, any water, and it flies (Ahmi:
 * "Gold has all abilities and can fly"). White (a missed Black roll), Purple (the
 * End bird: climbs, any water, rider floats down; only Gold flies) and Flame (Nether:
 * fire-immune, walks on lava) are this mod's own colours. Pink / Red were removed.
 * Columns: id (= ordinal), land speed, water speed, air speed, max health, step
 * height, water walk, climb, fly, fire immune, rider water breathing.
 */
public enum ChocoboColor {
	YELLOW(0, 0.20D, 0.10D, 0.00D, 30.0D, 1.0F, false, false, false, false, false),
	GREEN(1, 0.27D, 0.10D, 0.00D, 30.0D, 2.0F, false, true, false, false, false),
	BLUE(2, 0.27D, 0.50D, 0.00D, 30.0D, 1.0F, true, false, false, false, true),
	WHITE(3, 0.35D, 0.45D, 0.00D, 40.0D, 2.0F, true, true, false, false, false),
	BLACK(4, 0.40D, 0.20D, 0.00D, 40.0D, 2.0F, true, true, false, false, false),
	GOLD(5, 0.50D, 0.45D, 0.80D, 50.0D, 2.0F, true, true, true, true, true),
	PURPLE(6, 0.45D, 0.40D, 0.00D, 50.0D, 2.0F, true, true, false, false, true),
	FLAME(7, 0.40D, 0.10D, 0.00D, 50.0D, 1.0F, false, false, false, true, false);

	private final int id;
	private final double landSpeed;
	private final double waterSpeed;
	private final double airSpeed;
	private final double maxHealth;
	private final float stepHeight;
	private final boolean waterWalk;
	private final boolean climb;
	private final boolean fly;
	private final boolean fireImmune;
	private final boolean waterBreathing;

	ChocoboColor(int id, double landSpeed, double waterSpeed, double airSpeed, double maxHealth,
	             float stepHeight, boolean waterWalk, boolean climb, boolean fly,
	             boolean fireImmune, boolean waterBreathing) {
		this.id = id;
		this.landSpeed = landSpeed;
		this.waterSpeed = waterSpeed;
		this.airSpeed = airSpeed;
		this.maxHealth = maxHealth;
		this.stepHeight = stepHeight;
		this.waterWalk = waterWalk;
		this.climb = climb;
		this.fly = fly;
		this.fireImmune = fireImmune;
		this.waterBreathing = waterBreathing;
	}

	public int getId() {
		return id;
	}

	public double landSpeed() {
		return landSpeed;
	}

	public double waterSpeed() {
		return waterSpeed;
	}

	public double airSpeed() {
		return airSpeed;
	}

	public double maxHealth() {
		return maxHealth;
	}

	public float stepHeight() {
		return stepHeight;
	}

	public boolean waterWalk() {
		return waterWalk;
	}

	public boolean climb() {
		return climb;
	}

	public boolean fly() {
		return fly;
	}

	public boolean fireImmune() {
		return fireImmune;
	}

	public boolean waterBreathing() {
		return waterBreathing;
	}

	public boolean nightVision() {
		return this == BLACK || this == GOLD;
	}

	/** FF7 Gold crosses the ocean: any water depth. Blue / White / Black only rivers (shallow). */
	public boolean deepWater() {
		return this == GOLD || this == PURPLE;
	}

	/** The End bird: its rider drifts down instead of falling. */
	public boolean riderSlowFalling() {
		return this == PURPLE;
	}

	/** Flame is the Nether bird: it walks on lava (its "river"). */
	/** The Nether bird crosses lava; Gold has every ability. */
	public boolean lavaWalk() {
		return this == FLAME || this == GOLD;
	}

	public boolean riderFireResist() {
		return this == FLAME;
	}

	public boolean spaceBird() {
		return this == GREEN || this == BLACK || this == GOLD || this == WHITE || this == PURPLE;
	}

	public boolean waterBird() {
		return waterWalk;
	}

	public static ChocoboColor byId(int id) {
		ChocoboColor[] values = values();
		if (id < 0 || id >= values.length) {
			return YELLOW;
		}
		return values[id];
	}

	public Ff7Line.Color toLine() {
		return switch (this) {
			case YELLOW -> Ff7Line.Color.YELLOW;
			case GREEN -> Ff7Line.Color.GREEN;
			case BLUE -> Ff7Line.Color.BLUE;
			case WHITE -> Ff7Line.Color.WHITE;
			case BLACK -> Ff7Line.Color.BLACK;
			case GOLD -> Ff7Line.Color.GOLD;
			// the End and Nether birds breed as plain Yellows (their colour never passes on)
			case PURPLE, FLAME -> Ff7Line.Color.YELLOW;
		};
	}

	public static ChocoboColor fromLine(Ff7Line.Color color) {
		return switch (color) {
			case GREEN -> GREEN;
			case BLUE -> BLUE;
			case WHITE -> WHITE;
			case BLACK -> BLACK;
			case GOLD -> GOLD;
			default -> YELLOW;
		};
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}
}
