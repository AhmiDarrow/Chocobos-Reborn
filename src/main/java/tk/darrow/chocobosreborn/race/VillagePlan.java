package tk.darrow.chocobosreborn.race;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import tk.darrow.chocobosreborn.block.ModBlocks;
import tk.darrow.chocobosreborn.block.SquareGateBlock;

/**
 * Whiskerwind, the village: an organic sky island with a plaza around the
 * arrival medallion, a chocobo fountain, market stalls, timber cottages, an inn
 * with a bell tower, a stable yard, a windmill on its mound, the race arch with
 * a viewing pier over the void toward the courses, waterfalls off the rim and a
 * bridge to a shrine islet. Everything is code-placed through
 * {@link SquareBuilder#set} / {@link SquareBuilder#fill}; the keepers' stalls are
 * built by {@link SquareBuilder#booth} around {@link TownPosts}.
 */
final class VillagePlan {
	static final int Y = SquareBuilder.GROUND_Y;
	/** Island centre and mean radius; the rim wobbles with two sine waves. */
	static final int CX = VillageLayout.CX, CZ = VillageLayout.CZ, RADIUS = VillageLayout.RADIUS;
	/** Plaza centre = the arrival medallion (Square.ARRIVAL). */
	static final int PX = VillageLayout.PX, PZ = VillageLayout.PZ;
	static final int FOUNTAIN_Z = VillageLayout.FOUNTAIN_Z;

	private VillagePlan() {
	}

	private static void set(ServerLevel l, int x, int y, int z, String id) {
		SquareBuilder.set(l, x, y, z, id);
	}

	private static void fill(ServerLevel l, int x0, int y0, int z0, int x1, int y1, int z1, String id) {
		SquareBuilder.fill(l, x0, y0, z0, x1, y1, z1, id);
	}

	/** Rim radius at angle theta: 46 +- a few blocks so the island is not a disc. */
	static double rim(double theta) {
		return VillageLayout.rim(theta);
	}

	static boolean onIsland(int x, int z) {
		return VillageLayout.onIsland(x, z);
	}

	// ------------------------------------------------------------- island

	/** Grass top, dirt, then rock deepening toward the centre; roots and waterfalls on the rim. */
	static void island(ServerLevel l) {
		int r = RADIUS + 8;
		for (int x = CX - r; x <= CX + r; x++) {
			for (int z = CZ - r; z <= CZ + r; z++) {
				double dx = x - CX, dz = z - CZ;
				double d = Math.hypot(dx, dz), edge = rim(Math.atan2(dz, dx));
				if (d > edge) {
					continue;
				}
				double inset = edge - d;
				int depth = (int) Math.min(16.0D, 3.0D + inset * 0.45D);
				set(l, x, Y, z, inset < 1.3D ? "chiseled_sandstone" : "grass_block");
				fill(l, x, Y - 3, z, x, Y - 1, z, inset < 1.3D ? "sandstone" : "dirt");
				for (int i = 4; i <= depth; i++) {
					String rock = i < 7 ? "stone" : i < 11 ? "deepslate" : (i + x + z) % 5 == 0 ? "tuff" : "deepslate";
					set(l, x, Y - i, z, rock);
				}
				if (inset < 1.5D && (x * 7 + z * 13) % 4 == 0) {
					set(l, x, Y - 4, z, "hanging_roots");
				}
			}
		}
		// three waterfalls spilling off the rim into the void, from a stone lip
		for (double theta : new double[]{0.6D, 2.4D, 4.3D}) {
			int x = (int) Math.round(CX + (rim(theta) - 2.0D) * Math.cos(theta));
			int z = (int) Math.round(CZ + (rim(theta) - 2.0D) * Math.sin(theta));
			fill(l, x - 1, Y, z - 1, x + 1, Y, z + 1, "stone");
			fill(l, x - 1, Y + 1, z - 1, x + 1, Y + 1, z + 1, "stone_brick_wall");
			set(l, x, Y + 1, z, "water");
			int ox = (int) Math.round(Math.cos(theta) * 2.0D), oz = (int) Math.round(Math.sin(theta) * 2.0D);
			set(l, x + ox, Y, z + oz, "air");
			set(l, x + ox, Y - 1, z + oz, "air");
			set(l, x + ox, Y + 1, z + oz, "air");
		}
		// lantern posts on the kerb, not on the floor (Ahmi)
		for (int i = 0; i < 8; i++) {
			double theta = i * Math.PI / 4.0D + 0.35D;
			double along = rim(theta) - 3.0D;
			int x = (int) Math.round(CX + along * Math.cos(theta));
			int z = (int) Math.round(CZ + along * Math.sin(theta));
			if (onIsland(x, z) && Math.hypot(x - PX, z - PZ) > 16.0D && Math.hypot(x, z - FOUNTAIN_Z) > 12.0D) {
				lamp(l, x, z);
			}
		}
	}

	// -------------------------------------------------------------- plaza

	/** Paved circle around the arrival medallion with the medallion, lamps and flower beds. */
	static void plaza(ServerLevel l) {
		for (int dx = -14; dx <= 14; dx++) {
			for (int dz = -14; dz <= 14; dz++) {
				double r = Math.hypot(dx, dz);
				if (r > 13.5D) {
					continue;
				}
				String b = r > 12.5D ? "chiseled_sandstone" : (dx + dz) % 6 == 0 && r > 5.0D ? "cut_sandstone" : "smooth_sandstone";
				set(l, PX + dx, Y, PZ + dz, b);
			}
		}
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				double r = Math.hypot(dx, dz);
				if (r <= 4.3D && r > 3.2D) {
					set(l, PX + dx, Y, PZ + dz, "chiseled_sandstone");
				} else if (r <= 1.6D) {
					set(l, PX + dx, Y, PZ + dz, "yellow_terracotta");
				}
			}
		}
		set(l, PX, Y, PZ, "gold_block");
		for (int sx : new int[]{-1, 1}) {
			for (int sz : new int[]{-1, 1}) {
				lamp(l, PX + sx * 9, PZ + sz * 9);
				flowerBed(l, PX + sx * 12, PZ + sz * 5);
			}
		}
		// tribe banners on the plaza rim
		String[] colours = {"yellow", "blue", "green", "orange"};
		int i = 0;
		for (int[] p : new int[][]{{-13, 0}, {13, 0}, {-6, 13}, {6, 13}}) {
			set(l, PX + p[0], Y + 1, PZ + p[1], "stripped_oak_log");
			set(l, PX + p[0], Y + 2, PZ + p[1], "stripped_oak_log");
			set(l, PX + p[0], Y + 3, PZ + p[1], colours[i++ % 4] + "_banner[rotation=8]");
		}
		// a clipped hedge round the plaza with gaps for the streets; the four lamp posts already carry lanterns
		for (int dx = -15; dx <= 15; dx++) {
			for (int dz = -15; dz <= 15; dz++) {
				double r = Math.hypot(dx, dz);
				if (r > 14.2D && r <= 15.2D && Math.abs(dx) > 3 && Math.abs(dz) > 3 && onIsland(PX + dx, PZ + dz)) {
					set(l, PX + dx, Y + 1, PZ + dz, "oak_leaves[persistent=true]");
					if ((dx * 7 + dz * 3) % 5 == 0) {
						set(l, PX + dx, Y + 2, PZ + dz, "azalea_leaves[persistent=true]");
					}
				}
			}
		}
		for (int sx : new int[]{-1, 1}) {
			for (int sz : new int[]{-1, 1}) {
				String facing = sz < 0 ? "south" : "north";
				set(l, PX + sx * 6 - 1, Y + 1, PZ + sz * 6, "oak_stairs[facing=" + facing + "]");
				set(l, PX + sx * 6, Y + 1, PZ + sz * 6, "oak_stairs[facing=" + facing + "]");
				set(l, PX + sx * 6 + 1, Y + 1, PZ + sz * 6, "oak_stairs[facing=" + facing + "]");
			}
		}
	}

	static void lamp(ServerLevel l, int x, int z) {
		set(l, x, Y, z, "cut_sandstone");
		set(l, x, Y + 1, z, "stripped_spruce_log");
		set(l, x, Y + 2, z, "stripped_spruce_log");
		set(l, x, Y + 3, z, "oak_fence");
		set(l, x, Y + 4, z, "lantern[hanging=false]");
	}

	static void flowerBed(ServerLevel l, int cx, int cz) {
		fill(l, cx - 1, Y, cz - 1, cx + 1, Y, cz + 1, "moss_block");
		set(l, cx, Y + 1, cz, "flowering_azalea");
		set(l, cx - 1, Y + 1, cz - 1, "dandelion");
		set(l, cx + 1, Y + 1, cz + 1, "poppy");
		set(l, cx + 1, Y + 1, cz - 1, "oxeye_daisy");
		set(l, cx - 1, Y + 1, cz + 1, "cornflower");
	}

	/** Roads: the avenue from the return gate to the arch, and two cross streets. */
	static void roads(ServerLevel l, int z0, int z1) {
		for (int z = z0 + 1; z < z1; z++) {
			for (int x = -3; x <= 3; x++) {
				set(l, x, Y, z, Math.abs(x) == 3 ? "cobblestone" : (z % 7 == 0 ? "stone_bricks" : "dirt_path"));
			}
		}
		for (int z : new int[]{-62, -56, -92}) {
			for (int x = -34; x <= 34; x++) {
				if (Math.hypot(x - PX, z - PZ) > 13.5D && onIsland(x, z)) {
					set(l, x, Y, z, "dirt_path");
				}
			}
		}
		for (int z = -96; z <= -40; z += 8) {
			if (Math.hypot(0, z - PZ) > 14.0D) {
				lamp(l, -5, z);
				lamp(l, 5, z);
			}
		}
		// hedges along the avenue
		for (int z = z0 + 4; z < z1 - 4; z++) {
			boolean nearFountain = Math.hypot(4, z - FOUNTAIN_Z) < 12.5D;
			if (Math.hypot(0, z - PZ) > 15.0D && z % 8 != 0 && Math.abs(z + 92) > 2 && !nearFountain && onIsland(4, z)) {
				set(l, -4, Y + 1, z, "oak_leaves[persistent=true]");
				set(l, 4, Y + 1, z, "oak_leaves[persistent=true]");
			}
		}
	}

	// ----------------------------------------------------------- fountain

	/** A round basin with a stylised chocobo standing in it, water spouting round its feet. */
	static void fountain(ServerLevel l, int cx, int cz) {
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				double r = Math.hypot(dx, dz);
				if (r > 5.4D) {
					continue;
				}
				set(l, cx + dx, Y, cz + dz, "smooth_sandstone");
				if (r > 4.4D) {
					set(l, cx + dx, Y + 1, cz + dz, "chiseled_sandstone");
				} else {
					set(l, cx + dx, Y + 1, cz + dz, "water");
				}
			}
		}
		// pedestal and bird (facing south: head toward +z)
		fill(l, cx - 1, Y + 1, cz - 2, cx + 1, Y + 2, cz + 2, "cut_sandstone");
		fill(l, cx - 1, Y + 3, cz - 2, cx + 1, Y + 4, cz + 1, "yellow_concrete");        // body
		fill(l, cx - 2, Y + 4, cz - 1, cx + 2, Y + 4, cz, "yellow_concrete");             // wings
		set(l, cx - 2, Y + 3, cz - 1, "yellow_concrete");
		set(l, cx + 2, Y + 3, cz - 1, "yellow_concrete");
		fill(l, cx, Y + 3, cz - 3, cx, Y + 5, cz - 3, "yellow_concrete");                 // tail
		set(l, cx, Y + 6, cz - 4, "yellow_concrete");
		set(l, cx - 1, Y + 5, cz - 4, "yellow_concrete");
		set(l, cx + 1, Y + 5, cz - 4, "yellow_concrete");
		fill(l, cx, Y + 5, cz + 1, cx, Y + 7, cz + 2, "yellow_concrete");                 // neck
		fill(l, cx - 1, Y + 8, cz + 1, cx + 1, Y + 9, cz + 3, "yellow_concrete");         // head
		set(l, cx, Y + 8, cz + 4, "orange_concrete");                                     // beak
		set(l, cx, Y + 8, cz + 5, "orange_concrete");
		set(l, cx - 1, Y + 9, cz + 3, "black_concrete");                                  // eyes
		set(l, cx + 1, Y + 9, cz + 3, "black_concrete");
		set(l, cx, Y + 10, cz + 1, "yellow_concrete");                                    // crest
		set(l, cx, Y + 11, cz, "yellow_concrete");
		set(l, cx - 1, Y + 10, cz + 2, "yellow_concrete");
		set(l, cx + 1, Y + 10, cz + 2, "yellow_concrete");
		for (int sx : new int[]{-3, 3}) {
			for (int sz : new int[]{-3, 3}) {
				set(l, cx + sx, Y + 2, cz + sz, "sea_lantern");
			}
		}
		// a paved ring round the basin joins the avenue on both sides and the streets east and west
		for (int dx = -9; dx <= 9; dx++) {
			for (int dz = -9; dz <= 9; dz++) {
				double r = Math.hypot(dx, dz);
				if (r > 5.4D && r <= 8.6D) {
					set(l, cx + dx, Y, cz + dz, r > 7.6D ? "stone_bricks" : "dirt_path");
				}
			}
		}
		for (int x = -20; x <= 20; x++) {
			if (Math.abs(x) > 8 && onIsland(cx + x, cz)) {
				fill(l, cx + x, Y, cz - 1, cx + x, Y, cz + 1, Math.abs(x) % 6 == 0 ? "stone_bricks" : "dirt_path");
			}
		}
	}

	// -------------------------------------------------------- arch and pier

	/** A railed overlook behind the arch on the island's edge: the courses float out beyond it. */
	static void overlook(ServerLevel l, int z0) {
		int cz = z0 + 6;
		for (int dx = -9; dx <= 9; dx++) {
			for (int dz = 0; dz <= 9; dz++) {
				double r = Math.hypot(dx, dz);
				if (r > 9.4D) {
					continue;
				}
				// the terrace carries its own rock: the island rim wobbles and left benches over the void
				int depth = (int) (2.0D + (9.4D - r) * 0.8D);
				fill(l, dx, Y - depth, cz + dz, dx, Y - 1, cz + dz, "stone");
				set(l, dx, Y, cz + dz, r > 8.4D ? "cut_sandstone" : (dx + dz) % 5 == 0 ? "chiseled_sandstone" : "smooth_sandstone");
				if (r > 8.4D) {
					set(l, dx, Y + 1, cz + dz, "sandstone_wall");
					if ((dx + dz) % 6 == 0) {
						set(l, dx, Y + 2, cz + dz, "lantern[hanging=false]");   // on the rail, not on the ground
					}
				}
			}
		}
		fill(l, -2, Y, z0, 2, Y, cz, "smooth_sandstone");
		for (int sx : new int[]{-1, 1}) {
			set(l, sx * 7 - 1, Y + 1, cz + 3, "oak_stairs[facing=" + (sx < 0 ? "east" : "west") + "]");
			set(l, sx * 7, Y + 1, cz + 3, "oak_stairs[facing=" + (sx < 0 ? "east" : "west") + "]");
		}
		fill(l, 0, Y + 1, cz + 7, 0, Y + 3, cz + 7, "stripped_oak_log");
		set(l, 0, Y + 4, cz + 7, "sea_lantern");
		set(l, 0, Y + 5, cz + 7, "yellow_banner[rotation=0]");
		SquareBuilder.sign(l, 0, Y + 2, cz + 6, "north", "chocobosreborn.sign.pier.0", "chocobosreborn.sign.pier.1", "chocobosreborn.sign.pier.2", "chocobosreborn.sign.pier.3");
	}

	/** A railed pier of planks running north from the arch over the void, ending on a viewing deck. */
	static void pier(ServerLevel l, int z0, int z1) {
		for (int z = z0; z >= z1; z--) {
			fill(l, -2, Y, z, 2, Y, z, "spruce_planks");
			fill(l, -3, Y, z, -3, Y, z, "stripped_spruce_log");
			fill(l, 3, Y, z, 3, Y, z, "stripped_spruce_log");
			set(l, -3, Y + 1, z, (z - z1) % 6 == 0 ? "lantern[hanging=false]" : "spruce_fence");
			set(l, 3, Y + 1, z, (z - z1) % 6 == 0 ? "lantern[hanging=false]" : "spruce_fence");
			if (z % 4 == 0) {
				fill(l, -3, Y - 4, z, -3, Y - 1, z, "stripped_spruce_log");
				fill(l, 3, Y - 4, z, 3, Y - 1, z, "stripped_spruce_log");
			}
		}
		int dz = z1 - 4;
		fill(l, -6, Y, dz - 4, 6, Y, dz + 4, "spruce_planks");
		fill(l, -6, Y - 1, dz - 4, 6, Y - 1, dz + 4, "stripped_spruce_log");
		for (int x = -6; x <= 6; x++) {
			set(l, x, Y + 1, dz - 4, x % 4 == 0 ? "lantern[hanging=false]" : "spruce_fence");
		}
		for (int z = dz - 4; z <= dz + 4; z++) {
			set(l, -6, Y + 1, z, "spruce_fence");
			set(l, 6, Y + 1, z, "spruce_fence");
		}
		set(l, 0, Y + 1, dz, "campfire[lit=true]");
		set(l, -3, Y + 1, dz, "spruce_stairs[facing=east]");
		set(l, 3, Y + 1, dz, "spruce_stairs[facing=west]");
	}

	/** A small islet with a shrine (gold egg on a lectern under a gazebo), joined by a rope bridge. */
	static void shrineIslet(ServerLevel l, int cx, int cz, int fromX, int fromZ) {
		for (int dx = -7; dx <= 7; dx++) {
			for (int dz = -7; dz <= 7; dz++) {
				double r = Math.hypot(dx, dz);
				if (r > 7.2D) {
					continue;
				}
				set(l, cx + dx, Y, cz + dz, "grass_block");
				int depth = (int) (2.0D + (7.2D - r) * 0.8D);
				fill(l, cx + dx, Y - depth, cz + dz, cx + dx, Y - 1, cz + dz, "stone");
			}
		}
		fill(l, cx - 2, Y, cz - 2, cx + 2, Y, cz + 2, "smooth_sandstone");
		for (int x : new int[]{cx - 2, cx + 2}) {
			for (int z : new int[]{cz - 2, cz + 2}) {
				fill(l, x, Y + 1, z, x, Y + 3, z, "stripped_oak_log");
			}
		}
		fill(l, cx - 3, Y + 4, cz - 3, cx + 3, Y + 4, cz + 3, "cherry_slab[type=bottom]");
		fill(l, cx - 2, Y + 5, cz - 2, cx + 2, Y + 5, cz + 2, "cherry_slab[type=bottom]");
		set(l, cx, Y + 6, cz, "lantern[hanging=false]");
		set(l, cx, Y + 1, cz, "lectern[facing=south]");
		set(l, cx, Y + 2, cz, "gold_block");
		set(l, cx - 1, Y + 1, cz + 1, "cherry_sapling");
		set(l, cx + 1, Y + 1, cz - 1, "cherry_sapling");
		// bridge: straight line of planks with fence rails between the two points
		int steps = Math.max(Math.abs(cx - fromX), Math.abs(cz - fromZ));
		for (int i = 0; i <= steps; i++) {
			int x = fromX + (cx - fromX) * i / steps, z = fromZ + (cz - fromZ) * i / steps;
			for (int w = -1; w <= 1; w++) {
				int bx = Math.abs(cx - fromX) >= Math.abs(cz - fromZ) ? x : x + w;
				int bz = Math.abs(cx - fromX) >= Math.abs(cz - fromZ) ? z + w : z;
				set(l, bx, Y, bz, w == 0 ? "spruce_planks" : "stripped_spruce_log");
				if (w != 0) {
					set(l, bx, Y + 1, bz, i % 5 == 0 ? "lantern[hanging=false]" : "spruce_fence");
				}
			}
		}
	}

	/**
	 * Practice gates on the town side of the arch, flanking the avenue: a fun sprint
	 * (west, yellow) and a fun grand prix (east, blue). Ride or click to start.
	 */
	static void funGates(ServerLevel l) {
		funGate(l, VillageLayout.FUN_GATE_WEST_X, VillageLayout.FUN_GATE_Z, SquareGateBlock.Kind.SHORT_COURSE, true);
		funGate(l, VillageLayout.FUN_GATE_EAST_X, VillageLayout.FUN_GATE_Z, SquareGateBlock.Kind.LONG_COURSE, false);
	}

	private static void funGate(ServerLevel l, int x, int z, SquareGateBlock.Kind kind, boolean faceEast) {
		int dir = faceEast ? 1 : -1;
		String toward = faceEast ? "east" : "west";
		String away = faceEast ? "west" : "east";
		fill(l, x - 1, Y, z - 2, x + 1, Y, z + 2, "polished_andesite");
		fill(l, x, Y + 1, z - 2, x, Y + 5, z + 2, "stone_bricks");
		fill(l, x, Y + 1, z - 1, x, Y + 3, z + 1, "air");
		set(l, x, Y + 5, z, "chiseled_stone_bricks");
		set(l, x, Y + 4, z - 2, "stone_brick_stairs[facing=south,half=top]");
		set(l, x, Y + 4, z + 2, "stone_brick_stairs[facing=north,half=top]");
		set(l, x, Y + 1, z - 2, "stone_brick_wall");
		set(l, x, Y + 1, z + 2, "stone_brick_wall");
		set(l, x, Y + 2, z - 2, "lantern[hanging=false]");
		set(l, x, Y + 2, z + 2, "lantern[hanging=false]");
		for (int dz = -1; dz <= 1; dz++) {
			l.setBlock(new BlockPos(x + dir, Y + 1, z + dz), ModBlocks.SQUARE_GATE.get().defaultBlockState()
					.setValue(SquareGateBlock.KIND, kind), 2);
			set(l, x + dir, Y + 4, z + dz, "sea_lantern");
		}
		String shortOrLong = kind == SquareGateBlock.Kind.SHORT_COURSE ? "fun_short" : "fun_long";
		SquareBuilder.sign(l, x + dir, Y + 3, z - 2, toward, "chocobosreborn.sign." + shortOrLong + ".0",
				"chocobosreborn.sign." + shortOrLong + ".1", "chocobosreborn.sign." + shortOrLong + ".2",
				"chocobosreborn.sign." + shortOrLong + ".3");
		SquareBuilder.sign(l, x + dir, Y + 3, z + 2, toward, "chocobosreborn.sign." + shortOrLong + ".0",
				"chocobosreborn.sign." + shortOrLong + ".1", "chocobosreborn.sign." + shortOrLong + ".2",
				"chocobosreborn.sign." + shortOrLong + ".3");
		set(l, x - dir, Y + 1, z, "stone_brick_stairs[facing=" + away + "]");
	}

	/** A small farmed gysahl bed beside Sage Wynn's stall. */
	static void gysahlPatch(ServerLevel l, int cx, int cz) {
		fill(l, cx - 1, Y, cz - 1, cx + 1, Y, cz + 1, "farmland[moisture=7]");
		set(l, cx, Y, cz, "water");
		for (int dx : new int[]{-1, 0, 1}) {
			for (int dz : new int[]{-1, 0, 1}) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				set(l, cx + dx, Y + 1, cz + dz, "chocobosreborn:gysahl_green[age=4]");
			}
		}
		set(l, cx, Y + 1, cz - 2, "oak_fence");
		set(l, cx, Y + 2, cz - 2, "lantern[hanging=false]");
		set(l, cx, Y + 1, cz + 2, "stripped_oak_log");
		SquareBuilder.sign(l, cx, Y + 2, cz + 3, "south", "chocobosreborn.sign.gysahl.0",
				"chocobosreborn.sign.gysahl.1", "chocobosreborn.sign.gysahl.2", "chocobosreborn.sign.gysahl.3");
	}

	/** The return portal on the south edge: a stone frame around the gate block. */
	static void returnGate(ServerLevel l, int z) {
		fill(l, -3, Y, z - 1, 3, Y, z + 1, "polished_andesite");
		fill(l, -2, Y + 1, z, 2, Y + 5, z, "stone_bricks");
		fill(l, -1, Y + 1, z, 1, Y + 3, z, "air");
		set(l, 0, Y + 5, z, "chiseled_stone_bricks");
		set(l, -2, Y + 4, z, "stone_brick_stairs[facing=east,half=top]");
		set(l, 2, Y + 4, z, "stone_brick_stairs[facing=west,half=top]");
		set(l, -1, Y + 4, z, "stone_brick_stairs[facing=east,half=top]");
		set(l, 1, Y + 4, z, "stone_brick_stairs[facing=west,half=top]");
		set(l, -3, Y + 1, z, "stone_brick_wall");
		set(l, 3, Y + 1, z, "stone_brick_wall");
		set(l, -3, Y + 2, z, "lantern[hanging=false]");
		set(l, 3, Y + 2, z, "lantern[hanging=false]");
		// three gate blocks across the alcove, lit from above, so any path through it goes home
		for (int x = -1; x <= 1; x++) {
			l.setBlock(new BlockPos(x, Y + 1, z + 1), ModBlocks.SQUARE_GATE.get().defaultBlockState()
					.setValue(SquareGateBlock.KIND, SquareGateBlock.Kind.RETURN_GATE), 2);
			set(l, x, Y + 4, z + 1, "sea_lantern");
		}
		// the town lies south of this gate (larger z): the signs hang on the frame's south face, read from the town
		SquareBuilder.sign(l, -2, Y + 3, z + 1, "south", "chocobosreborn.sign.gate.0", "chocobosreborn.sign.gate.1", "chocobosreborn.sign.gate.2", "chocobosreborn.sign.gate.3");
		SquareBuilder.sign(l, 2, Y + 3, z + 1, "south", "chocobosreborn.sign.gate.0", "chocobosreborn.sign.gate.1", "chocobosreborn.sign.gate.2", "chocobosreborn.sign.gate.3");
	}

	/** A cherry tree: trunk, a fat pink crown, petals on the ground. */
	static void cherry(ServerLevel l, int x, int z) {
		fill(l, x, Y + 1, z, x, Y + 4, z, "cherry_log");
		fill(l, x - 2, Y + 4, z - 2, x + 2, Y + 5, z + 2, "cherry_leaves[persistent=true]");
		fill(l, x - 1, Y + 6, z - 1, x + 1, Y + 6, z + 1, "cherry_leaves[persistent=true]");
		set(l, x - 3, Y + 5, z, "cherry_leaves[persistent=true]");
		set(l, x + 3, Y + 5, z, "cherry_leaves[persistent=true]");
		set(l, x, Y + 5, z - 3, "cherry_leaves[persistent=true]");
		set(l, x, Y + 5, z + 3, "cherry_leaves[persistent=true]");
		fill(l, x, Y + 4, z, x, Y + 5, z, "cherry_log");
		set(l, x + 1, Y + 1, z + 1, "pink_petals[flower_amount=3]");
		set(l, x - 1, Y + 1, z - 1, "pink_petals[flower_amount=2]");
	}
}
