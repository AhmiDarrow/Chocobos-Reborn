package tk.darrow.chocobosreborn.race;

import java.util.List;

/**
 * Whiskerwind island geometry. Minecraft-free so JUnit can assert that every
 * footprint sits on the island and clear of its neighbours without loading
 * {@link VillagePlan}.
 *
 * <p>Village v13 (Ahmi: "increase its size by about 50%, make it feel more
 * alive"): the island is 1.5x the v12 one around the same centre. The old
 * landmarks keep their look and spread out with it; the new outer ring holds
 * the ranch, the nest barn and nursery, the jockey lounge, the pond, the
 * orchard and four more cottages. North is -z: the return gate is north, the
 * race arch and the overlook toward the courses are south.
 */
final class VillageLayout {
	static final int CX = 0, CZ = -72, RADIUS = 69;
	/** Plaza centre = the arrival medallion (Square.ARRIVAL). */
	static final int PX = 0, PZ = -52, PLAZA_R = 16;
	static final int FOUNTAIN_Z = -84;
	/** Basin water radius, and the paved ring's outer radius (a gold saucer sits at the statue's feet). */
	static final int FOUNTAIN_R = 7, FOUNTAIN_PAVE = 12;
	/** The race arch (south) and the return gate (north), the two ends of the avenue. */
	static final int ARCH_Z = -24, GATE_Z = -126;
	/** Cross streets. */
	static final int PLAZA_ST_Z = -52, NORTH_ST_Z = -104;
	/** Gysahl bed west of Sage Wynn's stall. */
	static final int GYSAHL_CX = -29, GYSAHL_CZ = -45;

	// building centres (each builder takes the centre of its footprint, door south)
	static final int HALL_X = 39, HALL_Z = -51;
	static final int INN_X = -39, INN_Z = -75;
	static final int STABLE_X = 42, STABLE_Z = -87;
	static final int MILL_X = -47, MILL_Z = -95;
	static final int NEST_X = 44, NEST_Z = -105;
	static final int LOUNGE_X = -22, LOUNGE_Z = -31;
	/** Ranch paddock fence (inclusive), the 1-wide gap on its west side at RANCH_GAP_Z. */
	static final int RANCH_X0 = 54, RANCH_Z0 = -76, RANCH_X1 = 67, RANCH_Z1 = -50, RANCH_GAP_Z = -63;
	/** Nursery pen fence (inclusive), closed: the chicks stay in, the minder works over the rail. */
	static final int NURSERY_X0 = 27, NURSERY_Z0 = -107, NURSERY_X1 = 35, NURSERY_Z1 = -101;
	static final int POND_X = -42, POND_Z = -38;
	static final int ORCHARD_X0 = -25, ORCHARD_Z0 = -137, ORCHARD_X1 = -9, ORCHARD_Z1 = -127;
	/** The winners' board: a wall along z = BOARD_Z from x BOARD_X0 to BOARD_X1, signs on its north face. */
	static final int BOARD_X0 = 5, BOARD_X1 = 13, BOARD_Z = -30;
	/** Shrine islet off the orchard corner, reached by a bridge. */
	static final int SHRINE_X = -38, SHRINE_Z = -158, SHRINE_FROM_X = -24, SHRINE_FROM_Z = -137;
	/** Decorative market stalls tended by residents: fruit on the fountain street, fish by the hall. */
	static final int FRUIT_X = -33, FRUIT_Z = -89, FISH_X = 33, FISH_Z = -70;

	/** The eight cottages, by centre. */
	static final int[][] COTTAGES = {
			{-45, -113}, {-58, -60}, {-12, -118}, {45, -37},
			{12, -118}, {28, -30}, {-45, -54}, {26, -121}
	};

	/** A named footprint (inclusive block bounds, margins included) that nothing else may overlap. */
	record Plot(String name, int x0, int z0, int x1, int z1) {
		boolean overlaps(Plot o) {
			return x0 <= o.x1 && o.x0 <= x1 && z0 <= o.z1 && o.z0 <= z1;
		}

		boolean contains(int x, int z) {
			return x >= x0 && x <= x1 && z >= z0 && z <= z1;
		}
	}

	static List<Plot> plots() {
		java.util.ArrayList<Plot> p = new java.util.ArrayList<>(List.of(
				new Plot("plaza", PX - PLAZA_R, PZ - PLAZA_R, PX + PLAZA_R, PZ + PLAZA_R),
				new Plot("fountain", -FOUNTAIN_PAVE, FOUNTAIN_Z - FOUNTAIN_PAVE, FOUNTAIN_PAVE, FOUNTAIN_Z + FOUNTAIN_PAVE),
				new Plot("board", BOARD_X0, BOARD_Z - 2, BOARD_X1, BOARD_Z + 1),
				new Plot("greens", -25, -48, -21, -42), new Plot("fair", -25, -64, -21, -58),
				new Plot("tack", 20, -48, 24, -42), new Plot("treats", 20, -64, 24, -58),
				new Plot("gysahl", GYSAHL_CX - 2, GYSAHL_CZ - 3, GYSAHL_CX + 2, GYSAHL_CZ + 3),
				new Plot("exchange", -18, -101, -14, -95), new Plot("bookie", 14, -101, 18, -95),
				new Plot("duel", -27, -113, -23, -109), new Plot("broker", 23, -113, 27, -109),
				new Plot("hall", HALL_X - 9, HALL_Z - 5, HALL_X + 11, HALL_Z + 8),
				new Plot("inn", INN_X - 7, INN_Z - 6, INN_X + 11, INN_Z + 8),
				new Plot("stable", STABLE_X - 9, STABLE_Z - 7, STABLE_X + 9, STABLE_Z + 7),
				new Plot("windmill", MILL_X - 8, MILL_Z - 7, MILL_X + 8, MILL_Z + 7),
				new Plot("ranch", RANCH_X0 - 1, RANCH_Z0 - 1, RANCH_X1 + 1, RANCH_Z1 + 1),
				new Plot("nest", NEST_X - 6, NEST_Z - 5, NEST_X + 7, NEST_Z + 5),
				new Plot("nursery", NURSERY_X0 - 1, NURSERY_Z0 - 1, NURSERY_X1 + 1, NURSERY_Z1 + 1),
				new Plot("lounge", LOUNGE_X - 6, LOUNGE_Z - 4, LOUNGE_X + 6, LOUNGE_Z + 5),
				new Plot("pond", POND_X - 8, POND_Z - 6, POND_X + 8, POND_Z + 6),
				new Plot("orchard", ORCHARD_X0 - 1, ORCHARD_Z0 - 1, ORCHARD_X1 + 1, ORCHARD_Z1 + 1),
				new Plot("gate", -4, GATE_Z - 3, 4, GATE_Z + 2),
				new Plot("fruit", FRUIT_X - 3, FRUIT_Z - 3, FRUIT_X + 3, FRUIT_Z + 3),
				new Plot("fish", FISH_X - 3, FISH_Z - 3, FISH_X + 3, FISH_Z + 3)));
		for (int i = 0; i < COTTAGES.length; i++) {
			int[] c = COTTAGES[i];
			p.add(new Plot("cottage" + (i + 1), c[0] - 5, c[1] - 4, c[0] + 5, c[1] + 6));
		}
		return p;
	}

	/** True where a tree or garden may go: on the island, off every plot, the streets and the avenue. */
	static boolean openGround(int x, int z) {
		if (!onIsland(x, z) || Math.abs(x) <= 6 || Math.abs(z - PLAZA_ST_Z) <= 3 || Math.abs(z - FOUNTAIN_Z) <= 3
				|| Math.abs(z - NORTH_ST_Z) <= 3 || rimInset(x, z) < 4.0D) {
			return false;
		}
		for (Plot p : plots()) {
			if (x >= p.x0() - 3 && x <= p.x1() + 3 && z >= p.z0() - 3 && z <= p.z1() + 3) {
				return false;
			}
		}
		return true;
	}

	private VillageLayout() {
	}

	/** Rim radius at angle theta: 69 +- a few blocks so the island is not a disc. */
	static double rim(double theta) {
		return RADIUS + 6.0D * Math.sin(3.0D * theta + 0.7D) + 3.0D * Math.sin(7.0D * theta);
	}

	static boolean onIsland(int x, int z) {
		return rimInset(x, z) >= 0.0D;
	}

	/** Blocks from (x, z) in to the rim; negative off the island. */
	static double rimInset(double x, double z) {
		double dx = x - CX, dz = z - CZ;
		return rim(Math.atan2(dz, dx)) - Math.hypot(dx, dz);
	}

	// ------------------------------------------------------------ townsfolk

	/** A resident's day: where it sleeps, works, idles in the afternoon, and drinks in the evening. */
	record Resident(TownRole role, double homeX, double homeZ, double workX, double workZ) {
	}

	/** Ten residents, one per role, each with a home in a cottage (or the inn) and a job somewhere in town. */
	static List<Resident> residents() {
		return List.of(
				new Resident(TownRole.RESIDENT_RANCHER, home(3), homeZ(3), 57.5D, -62.5D),
				new Resident(TownRole.RESIDENT_NURSE, home(7), homeZ(7), 31.5D, -99.5D),
				new Resident(TownRole.RESIDENT_GARDENER, home(2), homeZ(2), -17.5D, -131.5D),
				new Resident(TownRole.RESIDENT_FISHER, home(6), homeZ(6), POND_X + 0.5D, POND_Z - 3.5D),
				new Resident(TownRole.RESIDENT_MILLER, home(0), homeZ(0), MILL_X + 0.5D, MILL_Z + 9.5D),
				new Resident(TownRole.RESIDENT_GROCER, home(1), homeZ(1), FRUIT_X + 0.5D, FRUIT_Z + 0.5D),
				new Resident(TownRole.RESIDENT_FISHMONGER, home(4), homeZ(4), FISH_X + 0.5D, FISH_Z + 0.5D),
				new Resident(TownRole.RESIDENT_JOCKEY_A, INN_X - 2.5D, INN_Z + 3.5D, LOUNGE_X - 1.5D, LOUNGE_Z + 0.5D),
				new Resident(TownRole.RESIDENT_JOCKEY_B, INN_X + 2.5D, INN_Z + 3.5D, LOUNGE_X + 2.5D, LOUNGE_Z - 0.5D),
				new Resident(TownRole.RESIDENT_CLERK, home(5), homeZ(5), HALL_X + 0.5D, HALL_Z - 1.5D));
	}

	/** Which resident this role is (index into {@link #residents}), or -1. */
	static int indexOf(TownRole role) {
		List<Resident> all = residents();
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).role() == role) {
				return i;
			}
		}
		return -1;
	}

	/** Stand spot inside cottage i: beside the bed, clear of the table and the stove. */
	private static double home(int i) {
		return COTTAGES[i][0] - 1.5D;
	}

	private static double homeZ(int i) {
		return COTTAGES[i][1] + 1.5D;
	}

	/** Afternoon spots: round the fountain and the plaza. */
	static final double[][] SOCIAL = {
			{-6.5D, FOUNTAIN_Z + 7.5D}, {6.5D, FOUNTAIN_Z + 7.5D}, {-7.5D, FOUNTAIN_Z - 6.5D}, {7.5D, FOUNTAIN_Z - 6.5D},
			{-10.5D, PZ - 8.5D}, {10.5D, PZ - 8.5D}, {-11.5D, PZ + 9.5D}, {11.5D, PZ + 9.5D},
			{-4.5D, FOUNTAIN_Z + 9.5D}, {4.5D, FOUNTAIN_Z - 9.5D}
	};
	/** Evening: the inn's taproom tables. */
	static final double[][] EVENING = {
			{INN_X - 3.5D, INN_Z - 1.5D}, {INN_X - 1.5D, INN_Z - 1.5D}, {INN_X + 0.5D, INN_Z - 1.5D},
			{INN_X - 3.5D, INN_Z + 0.5D}, {INN_X - 1.5D, INN_Z + 0.5D}, {INN_X + 0.5D, INN_Z + 0.5D},
			{INN_X + 2.5D, INN_Z + 1.5D}, {INN_X - 2.5D, INN_Z + 2.5D}, {INN_X + 1.5D, INN_Z + 2.5D},
			{INN_X - 0.5D, INN_Z + 3.5D}
	};

	/** Heat watchers line the overlook rail, spread along the south arc. */
	static double[] watchSpot(int i) {
		double a = Math.toRadians(-60.0D + 120.0D * (i % 10) / 9.0D);
		double r = OVERLOOK_R - 1.8D;
		return new double[]{0.5D + r * Math.sin(a), OVERLOOK_Z + 0.5D + r * Math.cos(a)};
	}

	/** The overlook terrace beyond the arch: centre and radius. */
	static final int OVERLOOK_Z = ARCH_Z + 7, OVERLOOK_R = 12;

	enum Activity { HOME, WORK, SOCIAL, EVENING, WATCH }

	/**
	 * Where a resident should be at this time of day. A heat being called (or
	 * running) pulls everyone awake to the overlook; at night they stay in.
	 */
	static Activity activity(long dayTime, boolean heatLive) {
		long t = Math.floorMod(dayTime, 24000L);
		boolean night = t >= 12500L && t < 23500L;
		if (heatLive && !night) {
			return Activity.WATCH;
		}
		if (t < 1000L || night) {
			return t < 1000L && !night ? Activity.SOCIAL : Activity.HOME;
		}
		if (t < 8000L) {
			return Activity.WORK;
		}
		if (t < 10500L) {
			return Activity.SOCIAL;
		}
		return Activity.EVENING;
	}

	/** Target block centre (x, z) for resident index i doing this activity. */
	static double[] spot(int i, Activity a) {
		Resident r = residents().get(i);
		return switch (a) {
			case HOME -> new double[]{r.homeX(), r.homeZ()};
			case WORK -> new double[]{r.workX(), r.workZ()};
			case SOCIAL -> SOCIAL[i % SOCIAL.length];
			case EVENING -> EVENING[i % EVENING.length];
			case WATCH -> watchSpot(i);
		};
	}
}
