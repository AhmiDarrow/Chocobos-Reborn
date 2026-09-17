package tk.darrow.chocobosreborn.race;

import net.minecraft.server.level.ServerLevel;

/**
 * Whiskerwind's buildings, race-town style: stone-brick footings, dark timber
 * frames with braces, cream infill, shuttered windows with flower boxes, steep
 * slate or shingle roofs with dormers and smoking chimneys, gold trim and tribe
 * banners. Every builder takes the centre of its footprint at ground level
 * ({@link VillagePlan#Y}) and faces south (+z), the door on the south face.
 */
final class VillageBuildings {
	private static final int Y = VillagePlan.Y;
	static final String FRAME = "dark_oak_log", FRAME_X = "dark_oak_log[axis=x]", FRAME_Z = "dark_oak_log[axis=z]";
	static final String INFILL = "white_terracotta", INFILL_ALT = "mud_bricks";
	static final String FOOT = "stone_bricks", TRIM = "yellow_glazed_terracotta";

	private VillageBuildings() {
	}

	private static void set(ServerLevel l, int x, int y, int z, String id) {
		SquareBuilder.set(l, x, y, z, id);
	}

	private static void fill(ServerLevel l, int x0, int y0, int z0, int x1, int y1, int z1, String id) {
		SquareBuilder.fill(l, x0, y0, z0, x1, y1, z1, id);
	}

	// --------------------------------------------------------------- pieces

	/** A framed wall box: stone footing, log corners and bands, infill, hollow inside. */
	static void frame(ServerLevel l, int x0, int z0, int x1, int z1, int floors, String infill) {
		int top = Y + floors * 4;
		fill(l, x0, Y, z0, x1, Y, z1, FOOT);
		fill(l, x0, Y + 1, z0, x1, top, z1, infill);
		fill(l, x0 + 1, Y + 1, z0 + 1, x1 - 1, top, z1 - 1, "air");
		for (int x : new int[]{x0, x1}) {
			for (int z : new int[]{z0, z1}) {
				fill(l, x, Y + 1, z, x, top, z, FRAME);
			}
		}
		for (int f = 1; f <= floors; f++) {
			int band = Y + f * 4;
			fill(l, x0, band, z0, x1, band, z0, FRAME_X);
			fill(l, x0, band, z1, x1, band, z1, FRAME_X);
			fill(l, x0, band, z0, x0, band, z1, FRAME_Z);
			fill(l, x1, band, z0, x1, band, z1, FRAME_Z);
			if (f > 1) {
				fill(l, x0 + 1, band - 4, z0 + 1, x1 - 1, band - 4, z1 - 1, "spruce_planks");   // floor
			}
			// braces: stairs in the frame corners of the south and north faces
			set(l, x0 + 1, band - 1, z1, "dark_oak_stairs[facing=west,half=top]");
			set(l, x1 - 1, band - 1, z1, "dark_oak_stairs[facing=east,half=top]");
			set(l, x0 + 1, band - 1, z0, "dark_oak_stairs[facing=west,half=top]");
			set(l, x1 - 1, band - 1, z0, "dark_oak_stairs[facing=east,half=top]");
		}
	}

	/** A shuttered window with a flower box below, in a wall on the given face ("south", "north", "east", "west"). */
	static void window(ServerLevel l, int x, int y, int z, String face) {
		set(l, x, y, z, "glass_pane");
		set(l, x, y + 1, z, "glass_pane");
		int ox = face.equals("east") ? 1 : face.equals("west") ? -1 : 0;
		int oz = face.equals("south") ? 1 : face.equals("north") ? -1 : 0;
		String left = face.equals("south") ? "west" : face.equals("north") ? "east" : face.equals("east") ? "south" : "north";
		String right = face.equals("south") ? "east" : face.equals("north") ? "west" : face.equals("east") ? "north" : "south";
		int lx = face.equals("south") || face.equals("north") ? x - 1 : x, lz = face.equals("east") || face.equals("west") ? z - 1 : z;
		int rx = face.equals("south") || face.equals("north") ? x + 1 : x, rz = face.equals("east") || face.equals("west") ? z + 1 : z;
		set(l, lx + ox, y, lz + oz, "spruce_trapdoor[facing=" + left + ",open=true]");
		set(l, lx + ox, y + 1, lz + oz, "spruce_trapdoor[facing=" + left + ",open=true]");
		set(l, rx + ox, y, rz + oz, "spruce_trapdoor[facing=" + right + ",open=true]");
		set(l, rx + ox, y + 1, rz + oz, "spruce_trapdoor[facing=" + right + ",open=true]");
		set(l, x + ox, y - 1, z + oz, "spruce_trapdoor[facing=" + face + ",half=top,open=false]");
		set(l, x + ox, y, z + oz, (x + z) % 2 == 0 ? "potted_red_tulip" : "potted_dandelion");
	}

	/** Gable roof over x0..x1 (with overhang) ridged along x, rows of stairs from z0-1 to z1+1, dormer optional. */
	static void gable(ServerLevel l, int x0, int z0, int x1, int z1, int base, String stairs, String slab, String infill, boolean dormer) {
		int half = (z1 - z0) / 2 + 1;
		for (int step = 0; step <= half; step++) {
			int y = base + step;
			int zs = z0 - 1 + step, zn = z1 + 1 - step;
			if (zs >= zn) {
				fill(l, x0 - 1, y, zs, x1 + 1, y, zs, slab);
				break;
			}
			fill(l, x0 - 1, y, zs, x1 + 1, y, zs, stairs + "[facing=south]");
			fill(l, x0 - 1, y, zn, x1 + 1, y, zn, stairs + "[facing=north]");
			if (step > 0) {
				fill(l, x0, y, zs + 1, x1, y, zn - 1, infill);
				fill(l, x0 + 1, y, zs + 1, x1 - 1, y, zn - 1, "air");
				set(l, x0, y, zs + 1, FRAME);
				set(l, x1, y, zs + 1, FRAME);
			}
		}
		// eave trims
		fill(l, x0 - 1, base - 1, z0 - 1, x1 + 1, base - 1, z0 - 1, "spruce_trapdoor[facing=north,half=top,open=false]");
		fill(l, x0 - 1, base - 1, z1 + 1, x1 + 1, base - 1, z1 + 1, "spruce_trapdoor[facing=south,half=top,open=false]");
		if (dormer) {
			int dx = (x0 + x1) / 2;
			fill(l, dx - 1, base, z1 - 1, dx + 1, base + 2, z1 + 1, infill);
			set(l, dx, base, z1 + 1, "glass_pane");
			set(l, dx, base + 1, z1 + 1, "glass_pane");
			fill(l, dx - 1, base + 3, z1 - 1, dx + 1, base + 3, z1 + 1, slab);
			set(l, dx - 2, base + 2, z1 + 1, stairs + "[facing=west]");
			set(l, dx + 2, base + 2, z1 + 1, stairs + "[facing=east]");
		}
	}

	/** A brick chimney with a campfire in its throat: it smokes. */
	static void chimney(ServerLevel l, int x, int z, int top) {
		fill(l, x, Y + 1, z, x, top + 2, z, "bricks");
		set(l, x, top + 3, z, "campfire[lit=true]");
		set(l, x - 1, top + 3, z, "brick_wall");
		set(l, x + 1, top + 3, z, "brick_wall");
		set(l, x, top + 3, z - 1, "brick_wall");
		set(l, x, top + 3, z + 1, "brick_wall");
	}

	/** Door with a step, lanterns and a gold-trimmed lintel on the south face. */
	static void door(ServerLevel l, int x, int z, String wood) {
		set(l, x, Y + 1, z, wood + "_door[facing=north,half=lower]");
		set(l, x, Y + 2, z, wood + "_door[facing=north,half=upper]");
		set(l, x, Y + 3, z, TRIM);
		set(l, x, Y, z + 1, "stone_brick_slab[type=bottom]");
		set(l, x - 1, Y + 3, z + 1, "lantern[hanging=true]");
		set(l, x + 1, Y + 3, z + 1, "lantern[hanging=true]");
		fill(l, x - 1, Y + 4, z + 1, x + 1, Y + 4, z + 1, "spruce_slab[type=bottom]");
	}

	// ------------------------------------------------------------ buildings

	/** A cottage: 9 x 7 timber frame, shuttered windows, dormer, chimney, garden path. */
	static void cottage(ServerLevel l, int cx, int cz, String infill, String roofStairs, String roofSlab) {
		int x0 = cx - 4, x1 = cx + 4, z0 = cz - 3, z1 = cz + 3;
		frame(l, x0, z0, x1, z1, 1, infill);
		door(l, cx, z1, "spruce");
		window(l, cx - 3, Y + 2, z1, "south");
		window(l, cx + 3, Y + 2, z1, "south");
		window(l, cx, Y + 2, z0, "north");
		window(l, x0, Y + 2, cz, "west");
		window(l, x1, Y + 2, cz, "east");
		gable(l, x0, z0, x1, z1, Y + 5, roofStairs, roofSlab, infill, true);
		fill(l, x0 + 1, Y + 5, z0 + 1, x1 - 1, Y + 5, z1 - 1, "spruce_planks");   // attic floor: the lantern hangs from it
		chimney(l, x1 - 1, z0 + 1, Y + 7);
		// inside
		set(l, x0 + 1, Y + 1, z0 + 1, "red_bed[facing=east,part=foot]");
		set(l, x0 + 2, Y + 1, z0 + 1, "red_bed[facing=east,part=head]");
		set(l, x1 - 1, Y + 1, z0 + 1, "bookshelf");
		set(l, x1 - 1, Y + 2, z0 + 1, "bookshelf");
		set(l, x1 - 2, Y + 1, z0 + 1, "crafting_table");
		set(l, cx, Y + 1, cz, "dark_oak_fence");
		set(l, cx, Y + 2, cz, "dark_oak_pressure_plate");
		set(l, cx, Y + 4, cz, "lantern[hanging=true]");
		set(l, x1 - 1, Y + 1, z0 + 2, "campfire[lit=true]");
		set(l, x1 - 1, Y + 2, z0 + 2, "iron_bars");
		// garden path and hedge corners
		fill(l, cx, Y, z1 + 2, cx, Y, z1 + 3, "stone_brick_slab[type=bottom]");
		for (int x : new int[]{x0 - 1, x1 + 1}) {
			for (int z : new int[]{z0 - 1, z1 + 1}) {
				set(l, x, Y + 1, z, "oak_leaves[persistent=true]");
			}
		}
	}

	/** The inn: three storeys, arched ground floor, balcony, slate roof with dormers, bell tower with a copper cap. */
	static void inn(ServerLevel l, int cx, int cz) {
		int x0 = cx - 6, x1 = cx + 6, z0 = cz - 5, z1 = cz + 5;
		frame(l, x0, z0, x1, z1, 2, INFILL);
		// arched stone ground floor
		fill(l, x0, Y + 1, z0, x1, Y + 3, z1, FOOT);
		fill(l, x0 + 1, Y + 1, z0 + 1, x1 - 1, Y + 3, z1 - 1, "air");
		for (int x = x0 + 2; x < x1; x += 4) {
			set(l, x, Y + 2, z1, "glass_pane");
			set(l, x, Y + 3, z1, "stone_brick_stairs[facing=south,half=top]");
			set(l, x, Y + 2, z0, "glass_pane");
			set(l, x, Y + 3, z0, "stone_brick_stairs[facing=north,half=top]");
		}
		door(l, cx, z1, "dark_oak");
		// first floor windows and the balcony over the door
		window(l, cx - 4, Y + 6, z1, "south");
		window(l, cx + 4, Y + 6, z1, "south");
		window(l, cx - 3, Y + 6, z0, "north");
		window(l, cx + 3, Y + 6, z0, "north");
		fill(l, cx - 2, Y + 5, z1 + 1, cx + 2, Y + 5, z1 + 2, "dark_oak_slab[type=top]");
		fill(l, cx - 2, Y + 6, z1 + 2, cx + 2, Y + 6, z1 + 2, "dark_oak_fence");
		set(l, cx - 2, Y + 6, z1 + 1, "dark_oak_fence");
		set(l, cx + 2, Y + 6, z1 + 1, "dark_oak_fence");
		set(l, cx, Y + 5, z1, "dark_oak_door[facing=north,half=lower]");
		set(l, cx, Y + 6, z1, "dark_oak_door[facing=north,half=upper]");
		gable(l, x0, z0, x1, z1, Y + 9, "deepslate_tile_stairs", "deepslate_tile_slab[type=bottom]", INFILL, true);
		fill(l, x0 + 1, Y + 8, z0 + 1, x1 - 1, Y + 8, z1 - 1, "spruce_planks");   // attic floor
		chimney(l, x0 + 1, z0 + 1, Y + 12);
		// stairs up, taproom, beds
		for (int i = 0; i < 4; i++) {
			set(l, x0 + 1, Y + 1 + i, z1 - 1 - i, "spruce_stairs[facing=north]");
			fill(l, x0 + 1, Y + 1, z1 - 1 - i, x0 + 1, Y + i, z1 - 1 - i, "spruce_planks");
		}
		set(l, x0 + 1, Y + 4, z1 - 5, "air");
		fill(l, x1 - 2, Y + 1, z0 + 1, x1 - 2, Y + 1, z1 - 3, "dark_oak_slab[type=top]");
		fill(l, x1 - 1, Y + 1, z0 + 1, x1 - 1, Y + 2, z1 - 3, "barrel[facing=up]");
		for (int x = x0 + 3; x <= x0 + 7; x += 2) {
			set(l, x, Y + 1, z0 + 3, "dark_oak_fence");
			set(l, x, Y + 2, z0 + 3, "dark_oak_pressure_plate");
			set(l, x - 1, Y + 1, z0 + 3, "spruce_stairs[facing=east]");
		}
		set(l, cx, Y + 3, cz, "lantern[hanging=true]");
		set(l, cx, Y + 7, cz, "lantern[hanging=true]");
		for (int x = x0 + 2; x < x1 - 1; x += 3) {
			set(l, x, Y + 5, z0 + 1, "red_bed[facing=south,part=head]");
			set(l, x, Y + 5, z0 + 2, "red_bed[facing=south,part=foot]");
		}
		// bell tower on the east side with a copper cap
		int tx = x1 + 3;
		fill(l, tx - 1, Y, cz - 1, tx + 1, Y + 12, cz + 1, FOOT);
		for (int y = Y + 2; y <= Y + 10; y += 4) {
			set(l, tx, y, cz + 1, "glass_pane");
			set(l, tx + 1, y, cz, "glass_pane");
		}
		fill(l, tx - 1, Y + 13, cz - 1, tx + 1, Y + 15, cz + 1, "air");
		for (int x : new int[]{tx - 1, tx + 1}) {
			for (int z : new int[]{cz - 1, cz + 1}) {
				fill(l, x, Y + 13, z, x, Y + 15, z, FRAME);
			}
		}
		set(l, tx, Y + 15, cz, "bell[attachment=ceiling]");
		fill(l, tx - 2, Y + 16, cz - 2, tx + 2, Y + 16, cz + 2, "waxed_oxidized_cut_copper_stairs[facing=south]");
		fill(l, tx - 1, Y + 17, cz - 1, tx + 1, Y + 17, cz + 1, "waxed_oxidized_cut_copper");
		set(l, tx, Y + 18, cz, "waxed_oxidized_copper");
		set(l, tx, Y + 19, cz, "lightning_rod");
		fill(l, x1 + 1, Y + 1, cz, x1 + 1, Y + 3, cz, "air");   // passage from the inn to the tower
	}

	/** The race hall: sandstone and quartz, gold trim, tribe banners, trophy pedestals, the notice board. */
	static void raceHall(ServerLevel l, int cx, int cz) {
		int x0 = cx - 7, x1 = cx + 7, z0 = cz - 4, z1 = cz + 4;
		fill(l, x0 - 1, Y, z0 - 1, x1 + 1, Y, z1 + 1, "smooth_sandstone");
		fill(l, x0, Y + 1, z0, x1, Y + 6, z1, "cut_sandstone");
		fill(l, x0 + 1, Y + 1, z0 + 1, x1 - 1, Y + 6, z1 - 1, "air");
		for (int x : new int[]{x0, x1}) {
			for (int z : new int[]{z0, z1}) {
				fill(l, x, Y + 1, z, x, Y + 7, z, "quartz_pillar");
				set(l, x, Y + 8, z, "gold_block");
			}
		}
		fill(l, x0, Y + 7, z0, x1, Y + 7, z1, "smooth_quartz");
		fill(l, x0 + 1, Y + 7, z0 + 1, x1 - 1, Y + 7, z1 - 1, "air");
		fill(l, x0 + 1, Y + 8, z0 + 1, x1 - 1, Y + 8, z1 - 1, "smooth_quartz_slab[type=bottom]");
		fill(l, x0, Y + 8, z0, x1, Y + 8, z0, "smooth_quartz_stairs[facing=south]");
		fill(l, x0, Y + 8, z1, x1, Y + 8, z1, "smooth_quartz_stairs[facing=north]");
		fill(l, x0 + 1, Y + 3, z0, x1 - 1, Y + 3, z0, TRIM);
		fill(l, x0 + 1, Y + 3, z1, x1 - 1, Y + 3, z1, TRIM);
		// double doors under a gold lintel, tall windows
		fill(l, cx - 1, Y + 1, z1, cx, Y + 2, z1, "air");
		set(l, cx - 1, Y + 1, z1, "oak_door[facing=north,half=lower,hinge=left]");
		set(l, cx - 1, Y + 2, z1, "oak_door[facing=north,half=upper,hinge=left]");
		set(l, cx, Y + 1, z1, "oak_door[facing=north,half=lower,hinge=right]");
		set(l, cx, Y + 2, z1, "oak_door[facing=north,half=upper,hinge=right]");
		fill(l, cx - 2, Y + 3, z1, cx + 1, Y + 3, z1, "gold_block");
		for (int x : new int[]{cx - 5, cx - 3, cx + 3, cx + 5}) {
			fill(l, x, Y + 2, z1, x, Y + 5, z1, "glass_pane");
			fill(l, x, Y + 2, z0, x, Y + 5, z0, "glass_pane");
		}
		// tribe banners between the windows
		String[] colours = {"yellow", "blue", "green", "orange"};
		int i = 0;
		for (int x : new int[]{cx - 6, cx - 4, cx + 4, cx + 6}) {
			set(l, x, Y + 5, z1 + 1, colours[i++ % 4] + "_wall_banner[facing=south]");
		}
		// inside: trophy pedestals down the sides, a dais and lectern at the back, lanterns
		for (int x = x0 + 2; x <= x1 - 2; x += 3) {
			set(l, x, Y + 1, z0 + 1, "chiseled_quartz_block");
			set(l, x, Y + 2, z0 + 1, (x / 3) % 2 == 0 ? "gold_block" : "diamond_block");
			set(l, x, Y + 3, z0 + 1, "glass");
		}
		fill(l, cx - 2, Y + 1, z0 + 2, cx + 2, Y + 1, z0 + 3, "smooth_quartz_slab[type=bottom]");
		set(l, cx, Y + 2, z0 + 2, "lectern[facing=south]");
		fill(l, cx - 4, Y + 6, cz, cx + 4, Y + 6, cz, "chain");
		for (int x : new int[]{cx - 4, cx, cx + 4}) {
			set(l, x, Y + 5, cz, "lantern[hanging=true]");
		}
		// the notice board outside: what the town does
		fill(l, x1 + 3, Y + 1, z1 - 2, x1 + 3, Y + 3, z1 + 2, "dark_oak_planks");
		fill(l, x1 + 3, Y + 4, z1 - 3, x1 + 3, Y + 4, z1 + 3, "dark_oak_slab[type=bottom]");
		SquareBuilder.sign(l, x1 + 4, Y + 3, z1 - 1, "east", "chocobosreborn.sign.board.0", "chocobosreborn.sign.board.1", "chocobosreborn.sign.board.2", "chocobosreborn.sign.board.3");
		SquareBuilder.sign(l, x1 + 4, Y + 3, z1 + 1, "east", "chocobosreborn.sign.board.4", "chocobosreborn.sign.board.5", "chocobosreborn.sign.board.6", "chocobosreborn.sign.board.7");
		SquareBuilder.sign(l, x1 + 4, Y + 2, z1, "east", "chocobosreborn.sign.board.8", "chocobosreborn.sign.board.9", "chocobosreborn.sign.board.10", "chocobosreborn.sign.board.11");
	}

	/** The stable: a log barn with a hay loft over a fenced yard, troughs and a tack wall. */
	static void stable(ServerLevel l, int cx, int cz) {
		int hx = 9, hz = 7;
		int x0 = cx - hx, x1 = cx + hx, z0 = cz - hz, z1 = cz + hz;
		fill(l, x0, Y, z0, x1, Y, z1, "coarse_dirt");
		for (int x = x0; x <= x1; x += 3) {
			fill(l, x, Y, z0 + 5, x, Y, z1, "dirt_path");
		}
		for (int x = x0; x <= x1; x++) {
			if (Math.abs(x - cx) > 2) {
				set(l, x, Y + 1, z1, "spruce_fence");
			}
		}
		for (int z = z0; z <= z1; z++) {
			set(l, x0, Y + 1, z, "spruce_fence");
			set(l, x1, Y + 1, z, "spruce_fence");
		}
		set(l, cx - 3, Y + 1, z1, "spruce_fence_gate[facing=south,open=true]");
		set(l, cx + 3, Y + 1, z1, "spruce_fence_gate[facing=south,open=true]");
		for (int x : new int[]{cx - 3, cx + 3}) {
			set(l, x, Y + 2, z1, "lantern[hanging=false]");
		}
		// barn: log frame, plank walls, open front, loft and a steep shingle roof
		int bz1 = z0 + 5;
		fill(l, x0, Y, z0, x1, Y, bz1, "spruce_planks");
		fill(l, x0, Y + 1, z0, x1, Y + 5, z0, "spruce_planks");
		for (int x = x0; x <= x1; x += 4) {
			fill(l, x, Y + 1, z0, x, Y + 5, z0, FRAME);
			fill(l, x, Y + 1, bz1, x, Y + 5, bz1, FRAME);
		}
		fill(l, x0, Y + 1, z0, x0, Y + 5, bz1, "spruce_planks");
		fill(l, x1, Y + 1, z0, x1, Y + 5, bz1, "spruce_planks");
		fill(l, x0 + 1, Y + 4, z0 + 1, x1 - 1, Y + 4, bz1 - 1, "spruce_planks");   // loft
		fill(l, cx - 1, Y + 4, bz1 - 2, cx + 1, Y + 4, bz1 - 1, "air");             // loft hatch
		fill(l, x0 + 1, Y + 1, z0 + 1, x0 + 1, Y + 3, z0 + 1, "ladder[facing=south]");   // on the back wall
		for (int x = x0 + 2; x < x1; x += 3) {
			set(l, x, Y + 5, z0 + 1, "hay_block");
			set(l, x, Y + 5, z0 + 2, "hay_block[axis=x]");
		}
		gable(l, x0, z0, x1, bz1, Y + 6, "spruce_stairs", "spruce_slab[type=bottom]", "spruce_planks", false);
		fill(l, x0 + 2, Y + 1, z0 + 1, x0 + 2, Y + 1, z0 + 1, "hay_block");
		fill(l, x1 - 2, Y + 1, z0 + 1, x1 - 2, Y + 2, z0 + 1, "hay_block");
		set(l, x0 + 4, Y + 1, z0 + 1, "water_cauldron[level=3]");
		set(l, x1 - 4, Y + 1, z0 + 1, "water_cauldron[level=3]");
		set(l, cx, Y + 3, bz1 - 3, "lantern[hanging=true]");
		// tack wall: fences with hanging lanterns, a chest and a barrel
		set(l, x0 + 1, Y + 1, z0 + 3, "chest[facing=east]");
		set(l, x0 + 1, Y + 1, z0 + 4, "barrel[facing=up]");
		fill(l, x0 + 4, Y + 1, z1 - 2, x1 - 4, Y + 1, z1 - 2, "spruce_fence");   // hitching rail
		set(l, x1 - 3, Y + 1, z1 - 3, "hay_block");
		set(l, x1 - 3, Y + 2, z1 - 3, "hay_block[axis=z]");
	}

	/** A stone windmill on a mound: tapered tower, wooden cap, four cloth sails. */
	static void windmill(ServerLevel l, int cx, int cz) {
		for (int dx = -7; dx <= 7; dx++) {
			for (int dz = -7; dz <= 7; dz++) {
				double r = Math.hypot(dx, dz);
				int h = r < 2.5D ? 3 : r < 4.5D ? 2 : r < 7.2D ? 1 : 0;
				if (h > 0 && VillagePlan.onIsland(cx + dx, cz + dz)) {
					fill(l, cx + dx, Y + 1, cz + dz, cx + dx, Y + h, cz + dz, "dirt");
					set(l, cx + dx, Y + h, cz + dz, "grass_block");
					if ((dx * 3 + dz * 5) % 7 == 0) {
						set(l, cx + dx, Y + h + 1, cz + dz, "short_grass");
					}
				}
			}
		}
		int base = Y + 4;
		fill(l, cx - 3, base, cz - 3, cx + 3, base + 3, cz + 3, "stone_bricks");
		fill(l, cx - 2, base, cz - 2, cx + 2, base + 3, cz + 2, "air");
		fill(l, cx - 2, base + 4, cz - 2, cx + 2, base + 10, cz + 2, "cobblestone");
		fill(l, cx - 1, base + 4, cz - 1, cx + 1, base + 10, cz + 1, "air");
		fill(l, cx - 3, base + 3, cz - 3, cx + 3, base + 3, cz + 3, "stone_brick_slab[type=bottom]");
		fill(l, cx - 3, base + 11, cz - 3, cx + 3, base + 11, cz + 3, "spruce_planks");
		fill(l, cx - 2, base + 12, cz - 2, cx + 2, base + 13, cz + 2, "spruce_planks");
		fill(l, cx - 1, base + 14, cz - 1, cx + 1, base + 14, cz + 1, "spruce_slab[type=bottom]");
		set(l, cx, base, cz + 3, "spruce_door[facing=north,half=lower]");
		set(l, cx, base + 1, cz + 3, "spruce_door[facing=north,half=upper]");
		set(l, cx - 1, base + 2, cz + 3, "lantern[hanging=true]");
		for (int y = base + 6; y <= base + 9; y += 3) {
			set(l, cx, y, cz + 2, "glass_pane");
			set(l, cx + 2, y, cz, "glass_pane");
		}
		int hz = cz + 3, hy = base + 9;
		set(l, cx, hy, hz, "stripped_spruce_log[axis=z]");
		set(l, cx, hy, hz + 1, "stripped_spruce_log[axis=z]");
		for (int i = 1; i <= 8; i++) {
			for (int[] d : new int[][]{{1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
				int x = cx + d[0] * i, y = hy + d[1] * i;
				set(l, x, y, hz + 1, i <= 7 ? "stripped_spruce_log" : "oak_fence");
				if (i >= 2 && i <= 7) {
					set(l, x - d[0], y, hz + 1, "white_wool");
					if (i >= 3) {
						set(l, x - d[0] * 2, y, hz + 1, i % 2 == 0 ? "white_wool" : "yellow_wool");
					}
				}
			}
		}
	}

	/** A market stall with a striped awning in the keeper's tribe colour over the counter. */
	static void awning(ServerLevel l, int px, int pz, int fx, int fz, int rx, int rz, String colour) {
		for (int w = -3; w <= 3; w++) {
			for (int d = 0; d <= 2; d++) {
				int x = px + fx * d + rx * w, z = pz + fz * d + rz * w;
				String block = (w & 1) == 0 ? colour + "_wool" : "white_wool";
				set(l, x, Y + 5, z, block);   // wool all the way: carpet over air would drop
			}
		}
		for (int w : new int[]{-3, 3}) {
			fill(l, px + fx * 2 + rx * w, Y + 1, pz + fz * 2 + rz * w, px + fx * 2 + rx * w, Y + 3, pz + fz * 2 + rz * w, "dark_oak_fence");
		}
	}
}
