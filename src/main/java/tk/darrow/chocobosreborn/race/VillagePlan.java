package tk.darrow.chocobosreborn.race;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import tk.darrow.chocobosreborn.block.ModBlocks;
import tk.darrow.chocobosreborn.block.SquareGateBlock;

/**
 * Whiskerwind, the village: an organic sky island with a plaza around the
 * arrival medallion, a chocobo fountain (a little gold saucer at its feet),
 * market stalls, timber cottages, an inn with a bell tower, a stable yard, a
 * windmill on its mound, the race arch with the overlook toward the courses,
 * waterfalls off the rim and a bridge to a shrine islet. The outer ring (ranch,
 * nest, pond, orchard, lounge) is {@link VillageDistrict}. Everything is
 * code-placed through {@link SquareBuilder#set} / {@link SquareBuilder#fill};
 * positions come from {@link VillageLayout}.
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

	static double rim(double theta) {
		return VillageLayout.rim(theta);
	}

	static boolean onIsland(int x, int z) {
		return VillageLayout.onIsland(x, z);
	}

	// ------------------------------------------------------------- island

	/** Grass top, dirt, then rock deepening toward the centre; roots and waterfalls on the rim. */
	static void island(ServerLevel l) {
		int r = RADIUS + 12;
		for (int x = CX - r; x <= CX + r; x++) {
			for (int z = CZ - r; z <= CZ + r; z++) {
				double inset = VillageLayout.rimInset(x, z);
				if (inset < 0.0D) {
					continue;
				}
				int depth = (int) Math.min(20.0D, 3.0D + inset * 0.35D);
				set(l, x, Y, z, inset < 1.3D ? "chiseled_sandstone" : "grass_block");
				fill(l, x, Y - 3, z, x, Y - 1, z, inset < 1.3D ? "sandstone" : "dirt");
				for (int i = 4; i <= depth; i++) {
					String rock = i < 7 ? "stone" : i < 12 ? "deepslate" : (i + x + z) % 5 == 0 ? "tuff" : "deepslate";
					set(l, x, Y - i, z, rock);
				}
				if (inset < 1.5D && (x * 7 + z * 13) % 4 == 0) {
					set(l, x, Y - 4, z, "hanging_roots");
				}
			}
		}
		// four waterfalls spilling off the rim into the void, from a stone lip (one below the pond)
		for (double theta : new double[]{0.6D, 2.45D, 3.9D, 5.2D}) {
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
		for (int i = 0; i < 14; i++) {
			double theta = i * Math.PI / 7.0D + 0.35D;
			double along = rim(theta) - 3.0D;
			int x = (int) Math.round(CX + along * Math.cos(theta));
			int z = (int) Math.round(CZ + along * Math.sin(theta));
			if (VillageLayout.openGround(x, z) || (onIsland(x, z) && VillageLayout.rimInset(x, z) < 4.0D && clearOfPlots(x, z))) {
				lamp(l, x, z);
			}
		}
	}

	private static boolean clearOfPlots(int x, int z) {
		if (Math.abs(x) <= 5) {
			return false;
		}
		for (VillageLayout.Plot p : VillageLayout.plots()) {
			if (x >= p.x0() - 1 && x <= p.x1() + 1 && z >= p.z0() - 1 && z <= p.z1() + 1) {
				return false;
			}
		}
		return true;
	}

	// -------------------------------------------------------------- plaza

	/** Paved circle around the arrival medallion with the medallion, lamps, benches and flower beds. */
	static void plaza(ServerLevel l) {
		int pr = VillageLayout.PLAZA_R;
		for (int dx = -pr; dx <= pr; dx++) {
			for (int dz = -pr; dz <= pr; dz++) {
				double r = Math.hypot(dx, dz);
				if (r > pr - 0.5D) {
					continue;
				}
				String b = r > pr - 1.5D ? "chiseled_sandstone"
						: (dx + dz) % 6 == 0 && r > 5.0D ? "cut_sandstone"
						: r > 7.5D && r < 8.5D ? "smooth_red_sandstone" : "smooth_sandstone";
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
				lamp(l, PX + sx * 11, PZ + sz * 11);
				flowerBed(l, PX + sx * 13, PZ + sz * 6);
			}
		}
		// tribe banners on the plaza rim
		String[] colours = {"yellow", "blue", "green", "orange", "red", "purple"};
		int i = 0;
		for (int[] p : new int[][]{{-15, 0}, {15, 0}, {-7, 14}, {7, 14}, {-7, -14}, {7, -14}}) {
			set(l, PX + p[0], Y + 1, PZ + p[1], "stripped_oak_log");
			set(l, PX + p[0], Y + 2, PZ + p[1], "stripped_oak_log");
			set(l, PX + p[0], Y + 3, PZ + p[1], colours[i++ % colours.length] + "_banner[rotation=8]");
		}
		// a clipped hedge round the plaza with gaps for the streets
		for (int dx = -pr - 1; dx <= pr + 1; dx++) {
			for (int dz = -pr - 1; dz <= pr + 1; dz++) {
				double r = Math.hypot(dx, dz);
				if (r > pr - 0.3D && r <= pr + 0.7D && Math.abs(dx) > 3 && Math.abs(dz) > 3 && onIsland(PX + dx, PZ + dz)) {
					set(l, PX + dx, Y + 1, PZ + dz, "oak_leaves[persistent=true]");
					if ((dx * 7 + dz * 3) % 5 == 0) {
						set(l, PX + dx, Y + 2, PZ + dz, "azalea_leaves[persistent=true]");
					}
				}
			}
		}
		// benches round the medallion: the townsfolk gather here in the afternoon
		for (int sx : new int[]{-1, 1}) {
			for (int sz : new int[]{-1, 1}) {
				String facing = sz < 0 ? "south" : "north";
				for (int k = -1; k <= 1; k++) {
					set(l, PX + sx * 7 + k, Y + 1, PZ + sz * 7, "oak_stairs[facing=" + facing + "]");
				}
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

	/** The avenue from the return gate to the arch, lamps and hedges along it. The streets are {@link VillageDistrict#lanes}. */
	static void avenue(ServerLevel l, int z0, int z1) {
		for (int z = z0 + 1; z < z1; z++) {
			for (int x = -3; x <= 3; x++) {
				set(l, x, Y, z, Math.abs(x) == 3 ? "cobblestone" : (z % 7 == 0 ? "stone_bricks" : "dirt_path"));
			}
		}
		for (int z = z0 + 6; z <= z1 - 4; z += 8) {
			if (besideAvenue(z)) {
				lamp(l, -5, z);
				lamp(l, 5, z);
			}
		}
		for (int z = z0 + 4; z < z1 - 4; z++) {
			if (besideAvenue(z) && Math.floorMod(z - z0 - 6, 8) != 0 && onIsland(4, z)) {
				set(l, -4, Y + 1, z, "oak_leaves[persistent=true]");
				set(l, 4, Y + 1, z, "oak_leaves[persistent=true]");
			}
		}
	}

	/** Beside the avenue and not on the plaza, the fountain ring, a cross street or the winners' board. */
	private static boolean besideAvenue(int z) {
		return Math.abs(z - PZ) > VillageLayout.PLAZA_R + 1
				&& Math.abs(z - FOUNTAIN_Z) > VillageLayout.FOUNTAIN_PAVE
				&& Math.abs(z - VillageLayout.NORTH_ST_Z) > 2
				&& Math.abs(z - VillageLayout.BOARD_Z) > 3
				&& z < VillageLayout.ARCH_Z - 4 && z > VillageLayout.GATE_Z + 3;
	}

	// ----------------------------------------------------------- fountain

	/** A round basin with a stylised chocobo standing in it, and a little gold saucer at its feet. */
	static void fountain(ServerLevel l, int cx, int cz) {
		int br = VillageLayout.FOUNTAIN_R;
		for (int dx = -br - 1; dx <= br + 1; dx++) {
			for (int dz = -br - 1; dz <= br + 1; dz++) {
				double r = Math.hypot(dx, dz);
				if (r > br + 0.4D) {
					continue;
				}
				set(l, cx + dx, Y, cz + dz, "smooth_sandstone");
				set(l, cx + dx, Y + 1, cz + dz, r > br - 0.6D ? "chiseled_sandstone" : "water");
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
		for (int sx : new int[]{-4, 4}) {
			for (int sz : new int[]{-4, 4}) {
				set(l, cx + sx, Y + 1, cz + sz, "sea_lantern");
			}
		}
		goldSaucer(l, cx, cz + 5);
		// a paved ring round the basin joins the avenue on both sides and the streets east and west
		int pave = VillageLayout.FOUNTAIN_PAVE;
		for (int dx = -pave; dx <= pave; dx++) {
			for (int dz = -pave; dz <= pave; dz++) {
				double r = Math.hypot(dx, dz);
				if (r > br + 0.4D && r <= pave - 0.4D) {
					set(l, cx + dx, Y, cz + dz, r > pave - 1.4D ? "stone_bricks" : (dx * dz) % 5 == 0 ? "cut_sandstone" : "dirt_path");
				}
			}
		}
		// the plaque, on the rim in front of the saucer
		SquareBuilder.sign(l, cx, Y + 1, cz + br + 1, "south", "chocobosreborn.sign.saucer.0", "chocobosreborn.sign.saucer.1",
				"chocobosreborn.sign.saucer.2", "chocobosreborn.sign.saucer.3");
	}

	/**
	 * Easter egg (Ahmi): a model of a golden saucer at the statue's feet, a nod to
	 * the one in FF7. A dark pylon with a lit ring, the golden disc, a glass dome
	 * with its glow, little domes round the rim and a spire. Our own blocks only.
	 */
	static void goldSaucer(ServerLevel l, int sx, int sz) {
		set(l, sx, Y + 1, sz, "chiseled_polished_blackstone");
		set(l, sx, Y + 2, sz, "polished_blackstone_wall");
		set(l, sx, Y + 3, sz, "polished_blackstone_wall");
		for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			set(l, sx + d[0], Y + 2, sz + d[1], "end_rod[facing=up]");   // the lights up the tower
		}
		// the saucer: a golden disc three across with a rim of four arms
		fill(l, sx - 1, Y + 4, sz - 1, sx + 1, Y + 4, sz + 1, "gold_block");
		for (int[] d : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
			set(l, sx + d[0], Y + 4, sz + d[1], "waxed_cut_copper_slab[type=top]");
			set(l, sx + d[0], Y + 5, sz + d[1], "yellow_stained_glass");   // the small domes round the park
		}
		for (int[] d : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
			set(l, sx + d[0], Y + 5, sz + d[1], "yellow_stained_glass_pane");
		}
		set(l, sx, Y + 5, sz, "glowstone");                // the big dome, lit from inside
		set(l, sx + 1, Y + 5, sz, "orange_stained_glass");
		set(l, sx - 1, Y + 5, sz, "orange_stained_glass");
		set(l, sx, Y + 5, sz + 1, "orange_stained_glass");
		set(l, sx, Y + 5, sz - 1, "orange_stained_glass");
		set(l, sx, Y + 6, sz, "end_rod[facing=up]");       // the spire
	}

	// ------------------------------------------------------ the overlook

	/** A railed terrace beyond the arch on the island's edge: the courses float out beyond it. */
	static void overlook(ServerLevel l, int archZ) {
		int cz = VillageLayout.OVERLOOK_Z, or = VillageLayout.OVERLOOK_R;
		for (int dx = -or; dx <= or; dx++) {
			for (int dz = -3; dz <= or; dz++) {
				double r = Math.hypot(dx, Math.max(0, dz));
				if (r > or + 0.4D) {
					continue;
				}
				// the terrace carries its own rock: the island rim wobbles and left benches over the void
				int depth = (int) (2.0D + (or + 0.4D - r) * 0.8D);
				fill(l, dx, Y - depth, cz + dz, dx, Y - 1, cz + dz, "stone");
				boolean edge = r > or - 0.6D && dz >= 0;
				set(l, dx, Y, cz + dz, edge ? "cut_sandstone" : (dx + dz) % 5 == 0 ? "chiseled_sandstone" : "smooth_sandstone");
				if (edge) {
					set(l, dx, Y + 1, cz + dz, "sandstone_wall");
					if ((dx + dz) % 6 == 0) {
						set(l, dx, Y + 2, cz + dz, "lantern[hanging=false]");   // on the rail, not on the ground
					}
				}
			}
		}
		fill(l, -2, Y, archZ, 2, Y, cz, "smooth_sandstone");
		for (int sx : new int[]{-1, 1}) {
			for (int k = 0; k < 3; k++) {
				set(l, sx * (7 + k) , Y + 1, cz + 2, "oak_stairs[facing=north]");
			}
		}
		fill(l, 0, Y + 1, cz + or - 1, 0, Y + 3, cz + or - 1, "stripped_oak_log");
		set(l, 0, Y + 4, cz + or - 1, "sea_lantern");
		set(l, 0, Y + 5, cz + or - 1, "yellow_banner[rotation=0]");
		SquareBuilder.sign(l, 0, Y + 2, cz + or - 2, "north", "chocobosreborn.sign.pier.0", "chocobosreborn.sign.pier.1",
				"chocobosreborn.sign.pier.2", "chocobosreborn.sign.pier.3");
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
		// bridge: a line of planks with rope rails between the two points
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

	/** The return portal at the north end of the avenue: a gold-trimmed stone frame round the gate light. */
	static void returnGate(ServerLevel l, int z) {
		fill(l, -4, Y, z - 2, 4, Y, z + 2, "polished_andesite");
		fill(l, -3, Y, z - 1, 3, Y, z + 1, "smooth_stone");
		fill(l, -2, Y + 1, z, 2, Y + 6, z, "stone_bricks");
		fill(l, -1, Y + 1, z, 1, Y + 4, z, "air");
		set(l, 0, Y + 6, z, "chiseled_stone_bricks");
		set(l, 0, Y + 7, z, "gold_block");
		set(l, -2, Y + 5, z, "stone_brick_stairs[facing=east,half=top]");
		set(l, 2, Y + 5, z, "stone_brick_stairs[facing=west,half=top]");
		set(l, -1, Y + 5, z, "stone_brick_stairs[facing=east,half=top]");
		set(l, 1, Y + 5, z, "stone_brick_stairs[facing=west,half=top]");
		for (int sx : new int[]{-3, 3}) {
			set(l, sx, Y + 1, z, "stone_brick_wall");
			set(l, sx, Y + 2, z, "stone_brick_wall");
			set(l, sx, Y + 3, z, "lantern[hanging=false]");
			set(l, sx, Y + 1, z + 2, "potted_azure_bluet");
		}
		set(l, -2, Y + 6, z + 1, "yellow_wall_banner[facing=south]");
		set(l, 2, Y + 6, z + 1, "yellow_wall_banner[facing=south]");
		// three gate blocks across the alcove, lit from above, so any path through it goes home
		for (int x = -1; x <= 1; x++) {
			l.setBlock(new BlockPos(x, Y + 1, z + 1), ModBlocks.SQUARE_GATE.get().defaultBlockState()
					.setValue(SquareGateBlock.KIND, SquareGateBlock.Kind.RETURN_GATE), 2);
			set(l, x, Y + 5, z + 1, "sea_lantern");
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
