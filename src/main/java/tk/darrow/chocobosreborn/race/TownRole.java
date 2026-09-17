package tk.darrow.chocobosreborn.race;

import java.util.Locale;

/**
 * The Square keepers are Tribal Power kin: each role wears one of the four kin
 * skins (elder / drummer / hunter / weaver) and a tribe cloak with that tribe's
 * colour, matching the Nine Tribes canon shared with Tribal Power.
 */
public enum TownRole {
	STEWARD("elder", "spindle", 0x62d1c9),   // Esther: Loom-stitcher elder
	BOOKIE("hunter", "swarm", 0xd7b23c),     // Rook: Colony-keeper, quick eyes
	TACK("drummer", "claw", 0xb8512f),       // Tack: Edge-walker
	GREENS("weaver", "sprout", 0x4f9a5a),    // Sage Wynn: Rootbinder
	FAIR("weaver", "clock", 0x4a7fb5),       // Fair: Pattern-weaver
	TREATS("drummer", "soil", 0x8b5a2b),     // Bilo: Pad-keeper
	FARMHAND("hunter", "soil", 0x8b5a2b),    // farm gate kin: takes riders to the Square (one per farm, unnamed)
	STABLEHAND("weaver", "sprout", 0x4f9a5a), // farm stall: gysahl, seeds, plain nuts
	EXCHANGE("elder", "clock", 0x4a7fb5),    // Marl: the GP Exchange, trades GP for everyday goods
	DUEL("hunter", "claw", 0xb8512f),        // Sable: the duel master, two riders, one course, a side bet
	BROKER("elder", "soil", 0x8b5a2b),       // Pell: the broker, swaps and gifts birds between players
	FAN_SWARM("hunter", "swarm", 0xd7b23c),  // race fans in the stands: one per tribe look
	FAN_CLOCK("weaver", "clock", 0x4a7fb5),
	FAN_SPROUT("drummer", "sprout", 0x4f9a5a),
	FAN_CLAW("elder", "claw", 0xb8512f),
	JOCKEY_SWARM("hunter", "swarm", 0xd7b23c),   // kin jockeys riding the AI racers, one per tribe look
	JOCKEY_CLOCK("weaver", "clock", 0x4a7fb5),
	JOCKEY_SPINDLE("elder", "spindle", 0x62d1c9),
	JOCKEY_SOIL("drummer", "soil", 0x8b5a2b),
	JOCKEY_CLAW("hunter", "claw", 0xb8512f),
	JOCKEY_TEIYO("elder", "spindle", 0x62d1c9),  // the named rivals' jockeys
	JOCKEY_JOLO("drummer", "swarm", 0xd7b23c);

	private final String skin;
	private final String tribe;
	private final int colour;

	TownRole(String skin, String tribe, int colour) {
		this.skin = skin;
		this.tribe = tribe;
		this.colour = colour;
	}

	public boolean shops() {
		return this == TACK || this == GREENS || this == FAIR || this == TREATS || this == STABLEHAND || this == EXCHANGE;
	}

	/** Riders of the AI racers: never trade or teleport anyone. */
	public boolean jockey() {
		return this == JOCKEY_SWARM || this == JOCKEY_CLOCK || this == JOCKEY_SPINDLE || this == JOCKEY_SOIL
				|| this == JOCKEY_CLAW || this == JOCKEY_TEIYO || this == JOCKEY_JOLO;
	}

	/** Spectators: cheer, never trade or teleport anyone. */
	public boolean fans() {
		return this == FAN_SWARM || this == FAN_CLOCK || this == FAN_SPROUT || this == FAN_CLAW;
	}

	/** Behaves like Esther: sends a saddled rider to Chocobo Square, or home again. */
	public boolean stewards() {
		return this == STEWARD || this == FARMHAND;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	/** Kin skin id: elder, drummer, hunter or weaver. */
	public String skin() {
		return skin;
	}

	/** Tribe id for the cloak overlay (kin_cloak_&lt;tribe&gt;.png). */
	public String tribe() {
		return tribe;
	}

	/** Vanilla banner colour closest to the tribe colour, for the stall banners. */
	public String banner() {
		return switch (tribe) {
			case "spindle" -> "cyan";
			case "swarm" -> "yellow";
			case "claw" -> "orange";
			case "sprout" -> "green";
			case "clock" -> "blue";
			default -> "brown";
		};
	}

	/** Tribe colour that tints the cloak overlay. */
	public int colour() {
		return colour;
	}

	public static TownRole byId(int id) {
		TownRole[] values = values();
		if (id < 0 || id >= values.length) {
			return STEWARD;
		}
		return values[id];
	}
}
