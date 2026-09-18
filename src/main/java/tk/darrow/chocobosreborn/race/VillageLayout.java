package tk.darrow.chocobosreborn.race;

/**
 * Whiskerwind island geometry. Minecraft-free so JUnit can assert gate and farm
 * positions without loading {@link VillagePlan}.
 */
final class VillageLayout {
	static final int CX = 0, CZ = -72, RADIUS = 46;
	/** Plaza centre = the arrival medallion (Square.ARRIVAL). */
	static final int PX = 0, PZ = -59;
	static final int FOUNTAIN_Z = -80;
	/** Practice gates, town side of the arch, flanking the avenue. */
	static final int FUN_GATE_WEST_X = -18, FUN_GATE_EAST_X = 18, FUN_GATE_Z = -42;
	/** Gysahl bed west of Sage Wynn's stall. */
	static final int GYSAHL_CX = -24, GYSAHL_CZ = -52;

	private VillageLayout() {
	}

	/** Rim radius at angle theta: 46 +- a few blocks so the island is not a disc. */
	static double rim(double theta) {
		return RADIUS + 4.0D * Math.sin(3.0D * theta + 0.7D) + 2.0D * Math.sin(7.0D * theta);
	}

	static boolean onIsland(int x, int z) {
		double dx = x - CX, dz = z - CZ;
		return Math.hypot(dx, dz) <= rim(Math.atan2(dz, dx));
	}
}
