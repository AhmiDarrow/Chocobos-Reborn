package tk.darrow.chocobosreborn.race;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Whiskerwind's outer ring (village v13): the streets and lanes between the
 * districts, the ranch where the town flock grazes, the chick nursery, the pond
 * with its dock, the orchard, the winners' board, signposts, two market stalls
 * the townsfolk keep, and trees wherever the ground is open. Positions come from
 * {@link VillageLayout}; every piece sits on its own plot.
 */
final class VillageDistrict {
	private static final int Y = VillagePlan.Y;

	/**
	 * Streets and lanes as polylines. A lane is three wide and never paves over a
	 * plot, the avenue or the plaza, so a lane that runs into a building stops at
	 * its wall; they are laid first and everything else is built over them.
	 */
	static final int[][][] LANES = {
			{{-37, -52}, {-4, -52}}, {{4, -52}, {26, -52}},                               // plaza street
			{{-47, -86}, {-47, -84}, {-4, -84}}, {{4, -84}, {32, -84}, {32, -78}, {45, -78}}, // fountain street, the stable gates
			{{-46, -104}, {-4, -104}}, {{4, -104}, {24, -104}},                           // north street
			{{24, -104}, {24, -98}, {44, -98}},                                           // to the nest barn
			{{-39, -68}, {-36, -68}, {-36, -52}},                                         // the inn door
			{{-58, -53}, {-58, -46}, {-36, -46}},                                         // west lane: the pond, two cottages
			{{-45, -106}, {-45, -104}}, {{-12, -111}, {-12, -104}}, {{12, -111}, {12, -104}},
			{{26, -114}, {20, -114}, {20, -104}},
			{{26, -52}, {28, -52}, {28, -66}, {52, -66}, {52, -63}, {53, -63}},           // out to the ranch gap
			{{-4, -31}, {-15, -31}},                                                      // the jockey lounge
			{{45, -30}, {36, -30}, {36, -42}, {26, -42}, {26, -52}}, {{28, -23}, {36, -23}, {36, -30}},
			{{-5, -124}, {-17, -124}, {-17, -125}},                                       // the orchard
	};

	private VillageDistrict() {
	}

	private static void set(ServerLevel l, int x, int y, int z, String id) {
		SquareBuilder.set(l, x, y, z, id);
	}

	private static void fill(ServerLevel l, int x0, int y0, int z0, int x1, int y1, int z1, String id) {
		SquareBuilder.fill(l, x0, y0, z0, x1, y1, z1, id);
	}

	// -------------------------------------------------------------- lanes

	static void lanes(ServerLevel l) {
		for (int[][] line : LANES) {
			for (int i = 1; i < line.length; i++) {
				int x0 = line[i - 1][0], z0 = line[i - 1][1], x1 = line[i][0], z1 = line[i][1];
				int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
				for (int s = 0; s <= n; s++) {
					int x = x0 + (x1 - x0) * s / Math.max(1, n), z = z0 + (z1 - z0) * s / Math.max(1, n);
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							if (laneCell(x + dx, z + dz)) {
								int h = (x + dx) * 31 + (z + dz) * 17;
								set(l, x + dx, Y, z + dz, Math.floorMod(h, 11) == 0 ? "coarse_dirt" : Math.floorMod(h, 13) == 0 ? "gravel" : "dirt_path");
							}
						}
					}
					if (s % 11 == 5 && n > 8) {
						int side = (s / 11) % 2 == 0 ? 2 : -2;
						int lx = x1 != x0 ? x : x + side, lz = x1 != x0 ? z + side : z;
						if (VillageLayout.openGround(lx, lz)) {
							VillagePlan.lamp(l, lx, lz);
						}
					}
				}
			}
		}
	}

	private static boolean laneCell(int x, int z) {
		if (!VillageLayout.onIsland(x, z) || Math.abs(x) <= 3
				|| Math.hypot(x - VillageLayout.PX, z - VillageLayout.PZ) <= VillageLayout.PLAZA_R) {
			return false;
		}
		for (VillageLayout.Plot p : VillageLayout.plots()) {
			if (p.contains(x, z)) {
				return false;
			}
		}
		return true;
	}

	/** Distance from (x, z) to the nearest lane centreline, in blocks (Chebyshev along each segment). */
	static double laneDistance(int x, int z) {
		double best = Double.MAX_VALUE;
		for (int[][] line : LANES) {
			for (int i = 1; i < line.length; i++) {
				double ax = line[i - 1][0], az = line[i - 1][1], bx = line[i][0], bz = line[i][1];
				double vx = bx - ax, vz = bz - az, len = vx * vx + vz * vz;
				double t = len == 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, ((x - ax) * vx + (z - az) * vz) / len));
				best = Math.min(best, Math.hypot(x - (ax + t * vx), z - (az + t * vz)));
			}
		}
		return best;
	}

	// -------------------------------------------------------------- ranch

	/** The town flock's paddock: a fence with a gap too narrow for a grown bird, a trough, hay and a shelter. */
	static void ranch(ServerLevel l) {
		int x0 = VillageLayout.RANCH_X0, z0 = VillageLayout.RANCH_Z0, x1 = VillageLayout.RANCH_X1, z1 = VillageLayout.RANCH_Z1;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				int h = x * 13 + z * 7;
				set(l, x, Y, z, edge ? "coarse_dirt" : Math.floorMod(h, 9) == 0 ? "coarse_dirt" : Math.floorMod(h, 23) == 0 ? "podzol" : "grass_block");
				if (edge) {
					boolean gap = x == x0 && z == VillageLayout.RANCH_GAP_Z;
					set(l, x, Y + 1, z, gap ? "air" : "spruce_fence");
					if (!gap && (x - x0 + z - z0) % 6 == 0) {
						set(l, x, Y + 2, z, "lantern[hanging=false]");
					}
				} else if (Math.floorMod(h, 7) == 0) {
					set(l, x, Y + 1, z, "short_grass");
				}
			}
		}
		// gap posts so the opening reads as a gate
		for (int dz : new int[]{-1, 1}) {
			set(l, x0, Y + 1, VillageLayout.RANCH_GAP_Z + dz, "stripped_spruce_log");
			set(l, x0, Y + 2, VillageLayout.RANCH_GAP_Z + dz, "lantern[hanging=false]");
		}
		// trough along the west fence, hay on the north side
		for (int z = VillageLayout.RANCH_GAP_Z + 3; z <= VillageLayout.RANCH_GAP_Z + 6; z++) {
			set(l, x0 + 1, Y + 1, z, "water_cauldron[level=3]");
		}
		for (int[] h : new int[][]{{x0 + 4, z0 + 2}, {x0 + 5, z0 + 2}, {x0 + 4, z0 + 3}, {x1 - 3, z1 - 3}}) {
			set(l, h[0], Y + 1, h[1], "hay_block");
		}
		set(l, x0 + 5, Y + 2, z0 + 2, "hay_block[axis=x]");
		// shelter in the north-east corner: log posts, a plank roof, straw on the floor
		int sx0 = x1 - 6, sz0 = z0 + 1, sx1 = x1 - 1, sz1 = z0 + 5;
		for (int x : new int[]{sx0, sx1}) {
			for (int z : new int[]{sz0, sz1}) {
				fill(l, x, Y + 1, z, x, Y + 3, z, "stripped_spruce_log");
			}
		}
		fill(l, sx0, Y + 4, sz0, sx1, Y + 4, sz1, "spruce_slab[type=bottom]");
		fill(l, sx0, Y + 1, sz0, sx1, Y + 3, sz0, "spruce_planks");
		fill(l, sx0 + 1, Y, sz0 + 1, sx1 - 1, Y, sz1 - 1, "hay_block");
		set(l, (sx0 + sx1) / 2, Y + 3, sz1 - 1, "lantern[hanging=true]");
		SquareBuilder.sign(l, x0 - 1, Y + 2, VillageLayout.RANCH_GAP_Z + 2, "west", "chocobosreborn.sign.ranch.0",
				"chocobosreborn.sign.ranch.1", "chocobosreborn.sign.ranch.2", "chocobosreborn.sign.ranch.3");
		set(l, x0 - 1, Y + 1, VillageLayout.RANCH_GAP_Z + 2, "air");
		set(l, x0, Y + 2, VillageLayout.RANCH_GAP_Z + 2, "spruce_planks");   // the sign's backing on the fence
	}

	/** The chick nursery: a closed pen of straw beside the nest barn; the minder works over the rail. */
	static void nursery(ServerLevel l) {
		int x0 = VillageLayout.NURSERY_X0, z0 = VillageLayout.NURSERY_Z0, x1 = VillageLayout.NURSERY_X1, z1 = VillageLayout.NURSERY_Z1;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				set(l, x, Y, z, edge ? "stripped_birch_log" : (x + z) % 3 == 0 ? "hay_block" : "moss_block");
				set(l, x, Y + 1, z, edge ? "birch_fence" : "air");
			}
		}
		for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			set(l, c[0], Y + 1, c[1], "stripped_birch_log");
			set(l, c[0], Y + 2, c[1], "lantern[hanging=false]");
		}
		set(l, x0 + 2, Y + 1, z0 + 2, "water_cauldron[level=3]");
		set(l, x1 - 2, Y + 1, z0 + 1, "hay_block");
		set(l, x1 - 1, Y + 1, z1 - 1, "pink_petals[flower_amount=4]");
		SquareBuilder.sign(l, (x0 + x1) / 2, Y + 1, z1 + 1, "south", "chocobosreborn.sign.nursery.0",
				"chocobosreborn.sign.nursery.1", "chocobosreborn.sign.nursery.2", "chocobosreborn.sign.nursery.3");
		set(l, (x0 + x1) / 2, Y + 1, z1, "stripped_birch_log");
	}

	// --------------------------------------------------------------- pond

	/** A reedy pond with a plank dock on its north shore; the waterfall below it spills the overflow. */
	static void pond(ServerLevel l) {
		int cx = VillageLayout.POND_X, cz = VillageLayout.POND_Z;
		for (int dx = -8; dx <= 8; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				double e = (dx * dx) / 49.0D + (dz * dz) / 25.0D;
				int x = cx + dx, z = cz + dz;
				if (e <= 1.0D) {
					set(l, x, Y, z, "water");
					set(l, x, Y - 1, z, e < 0.55D ? "water" : "sand");
					set(l, x, Y - 2, z, "clay");
					if (Math.floorMod(x * 5 + z * 3, 17) == 0 && e < 0.8D) {
						set(l, x, Y + 1, z, "lily_pad");
					}
				} else if (e <= 1.45D) {
					set(l, x, Y, z, Math.floorMod(x + z, 4) == 0 ? "gravel" : "sand");
					set(l, x, Y + 1, z, "air");
					if (e <= 1.12D && Math.floorMod(x * 7 + z * 11, 4) == 0) {   // only on the waterline: cane needs water beside it
						set(l, x, Y + 1, z, "sugar_cane");
					}
				}
			}
		}
		// the dock: two planks wide from the shore out over the water, a post and lantern at the end
		fill(l, cx, Y, cz - 7, cx + 1, Y, cz - 2, "spruce_planks");
		fill(l, cx, Y - 1, cz - 2, cx, Y - 1, cz - 2, "stripped_spruce_log");
		set(l, cx - 1, Y + 1, cz - 2, "spruce_fence");
		set(l, cx - 1, Y + 2, cz - 2, "lantern[hanging=false]");
		set(l, cx + 2, Y + 1, cz - 6, "barrel[facing=up]");
		set(l, cx + 2, Y + 2, cz - 6, "spruce_trapdoor[half=bottom,open=false]");
	}

	// ------------------------------------------------------------ orchard

	/** Rows of small flowering trees with a bench, a composter and hives. */
	static void orchard(ServerLevel l) {
		int x0 = VillageLayout.ORCHARD_X0, z0 = VillageLayout.ORCHARD_Z0, x1 = VillageLayout.ORCHARD_X1, z1 = VillageLayout.ORCHARD_Z1;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				set(l, x, Y, z, (x + z) % 2 == 0 ? "grass_block" : "moss_block");
			}
		}
		for (int x = x0 + 2; x <= x1 - 1; x += 4) {
			for (int z = z0 + 2; z <= z1 - 1; z += 4) {
				fill(l, x, Y + 1, z, x, Y + 2, z, "oak_log");
				fill(l, x - 1, Y + 3, z - 1, x + 1, Y + 3, z + 1, (x + z) % 8 == 0 ? "flowering_azalea_leaves[persistent=true]" : "azalea_leaves[persistent=true]");
				set(l, x, Y + 4, z, "flowering_azalea_leaves[persistent=true]");
			}
		}
		set(l, x1 - 1, Y + 1, z1, "composter");
		set(l, x0, Y + 1, z1, "beehive[facing=south]");
		set(l, x0 + 1, Y + 1, z1, "oak_stairs[facing=north]");
		set(l, x0 + 2, Y + 1, z1, "oak_stairs[facing=north]");
	}

	// -------------------------------------------------------- winners' board

	/** A roofed plank wall between the plaza and the arch; {@link TownLife#writeBoard} fills its signs. */
	static void winnersBoard(ServerLevel l) {
		int x0 = VillageLayout.BOARD_X0, x1 = VillageLayout.BOARD_X1, z = VillageLayout.BOARD_Z;
		fill(l, x0 - 1, Y, z - 1, x1 + 1, Y, z + 1, "polished_andesite");
		fill(l, x0, Y + 1, z, x1, Y + 3, z, "dark_oak_planks");
		for (int x : new int[]{x0 - 1, x1 + 1}) {
			fill(l, x, Y + 1, z, x, Y + 4, z, "stripped_dark_oak_log");
			set(l, x, Y + 5, z, "lantern[hanging=false]");
		}
		fill(l, x0 - 1, Y + 4, z - 1, x1 + 1, Y + 4, z + 1, "dark_oak_slab[type=bottom]");
		fill(l, x0, Y + 4, z, x1, Y + 4, z, TRIM_GOLD);
		set(l, (x0 + x1) / 2, Y + 5, z, "yellow_banner[rotation=8]");
	}

	private static final String TRIM_GOLD = "gold_block";

	/** Sign spots on the board: four class boards (C, B, A, S) and the header above them. */
	static int[][] boardSigns() {
		int x0 = VillageLayout.BOARD_X0, z = VillageLayout.BOARD_Z - 1;
		return new int[][]{{x0 + 1, Y + 2, z}, {x0 + 3, Y + 2, z}, {x0 + 5, Y + 2, z}, {x0 + 7, Y + 2, z}, {x0 + 4, Y + 3, z}};
	}

	// ----------------------------------------------------------- signposts

	/**
	 * A fingerpost: a log with a sign on each face. Every face lists the same four
	 * directions from the reader's side (ahead, left, right, behind).
	 */
	static void signpost(ServerLevel l, int x, int z, String north, String south, String east, String west) {
		fill(l, x, Y + 1, z, x, Y + 3, z, "stripped_spruce_log");
		set(l, x, Y + 4, z, "lantern[hanging=false]");
		// facing south: the reader stands south looking north (ahead = north, left = west, right = east, behind = south)
		face(l, x, z + 1, "south", north, west, east, south);
		face(l, x, z - 1, "north", south, east, west, north);
		face(l, x + 1, z, "east", west, north, south, east);
		face(l, x - 1, z, "west", east, south, north, west);
	}

	private static void face(ServerLevel l, int x, int z, String facing, String ahead, String left, String right, String behind) {
		SquareBuilder.signText(l, x, Y + 2, z, facing,
				Component.literal("↑ ").append(Component.translatable(ahead)),
				Component.literal("← ").append(Component.translatable(left)),
				Component.literal("→ ").append(Component.translatable(right)),
				Component.literal("↓ ").append(Component.translatable(behind)));
	}

	// ------------------------------------------------------- market stalls

	/** A small stall with a striped awning, kept by a resident: produce on the counter, crates behind. */
	static void marketStall(ServerLevel l, int cx, int cz, int fx, int fz, String colour, String... goods) {
		int rx = -fz, rz = fx;
		for (int w = -2; w <= 2; w++) {
			for (int d = -1; d <= 1; d++) {
				int x = cx + fx * d + rx * w, z = cz + fz * d + rz * w;
				set(l, x, Y, z, "spruce_planks");
				fill(l, x, Y + 1, z, x, Y + 3, z, "air");
				set(l, x, Y + 4, z, (w & 1) == 0 ? colour + "_wool" : "white_wool");
			}
		}
		for (int w : new int[]{-2, 2}) {
			for (int d : new int[]{-1, 1}) {
				fill(l, cx + fx * d + rx * w, Y + 1, cz + fz * d + rz * w, cx + fx * d + rx * w, Y + 3, cz + fz * d + rz * w, "dark_oak_fence");
			}
		}
		// the counter in front, goods on it, crates behind the keeper
		for (int w = -1; w <= 1; w++) {
			set(l, cx + fx + rx * w, Y + 1, cz + fz + rz * w, "barrel[facing=up]");
			set(l, cx + fx + rx * w, Y + 2, cz + fz + rz * w, goods[Math.floorMod(w + 1, goods.length)]);
			set(l, cx - fx + rx * w, Y + 1, cz - fz + rz * w, (w & 1) == 0 ? "barrel[facing=up]" : "composter");
		}
		set(l, cx, Y + 3, cz, "lantern[hanging=true]");
	}

	// --------------------------------------------------------------- trees

	/** Trees and flower beds on a jittered grid wherever the ground is open and clear of the lanes. */
	static void scatter(ServerLevel l) {
		int r = VillageLayout.RADIUS;
		for (int gx = -r; gx <= r; gx += 8) {
			for (int gz = VillageLayout.CZ - r - 8; gz <= VillageLayout.CZ + r; gz += 8) {
				int h = Math.floorMod(gx * 73856093 ^ gz * 19349663, 1000);
				int x = gx + h % 5 - 2, z = gz + (h / 5) % 5 - 2;
				if (!VillageLayout.openGround(x, z) || laneDistance(x, z) < 4.0D || h % 7 == 0) {
					continue;
				}
				if (h % 3 == 0) {
					VillagePlan.cherry(l, x, z);
				} else if (h % 3 == 1) {
					oak(l, x, z);
				} else {
					VillagePlan.flowerBed(l, x, z);
				}
			}
		}
	}

	/** A small planted oak: log trunk in a moss bed, leaf crown. */
	static void oak(ServerLevel l, int x, int z) {
		set(l, x, Y, z, "moss_block");
		fill(l, x - 1, Y + 3, z - 1, x + 1, Y + 5, z + 1, "oak_leaves[persistent=true]");
		fill(l, x - 2, Y + 4, z, x + 2, Y + 4, z, "oak_leaves[persistent=true]");
		fill(l, x, Y + 4, z - 2, x, Y + 4, z + 2, "oak_leaves[persistent=true]");
		set(l, x, Y + 6, z, "oak_leaves[persistent=true]");
		fill(l, x, Y + 1, z, x, Y + 4, z, "oak_log");
	}
}
