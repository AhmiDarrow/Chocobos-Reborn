package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tk.darrow.chocobosreborn.breed.ChocoboColor;

/**
 * Whiskerwind's courses, kart-racer style: every class has sprints (one long lap) and
 * grands prix (three to five laps of a shorter circuit, the most laps on the shortest
 * lap), each a themed circuit on its own sky island. Sprint or grand prix is decided by
 * {@link #getLaps()} alone ({@link #isSprint()}); how many courses a class has is simply
 * how many rows of this table carry its class ({@link #ofClass}), so a new course is a
 * new row. A circuit is a {@link Shape} (a closed spline with straights, sweepers,
 * hairpins, chicanes and hills) scaled to its lap length, dressed in a {@link Theme},
 * and carrying {@link Feature}s along the way:
 * <ul>
 * <li>BOOST strips (every class): a burst of speed for whoever drives over them;</li>
 * <li>WATER, RIDGE and LAVA across the direct line: the birds that excel (river
 * birds, climbers, the Nether bird, Gold) take them at pace, everyone else takes
 * the slower detour road outside;</li>
 * <li>MUD (bog) on the direct line: a trap that slows everyone, so the detour is
 * the smart route.</li>
 * </ul>
 * Class C is open road with boosts; B adds one feature, A two plus a bog, S three or
 * more, with taller ridges. Lap progress is by nearest point on the centre line,
 * so the direct line and the detour credit the same progress.
 * <p>
 * Where a feature sits decides what a colour is worth. A detour round a feature on a
 * straight is the same length as the straight and costs only the swing out and back;
 * one round the outside of a big bend costs a tenth of a lap. So the spans below are
 * not free-hand: each sits on a level stretch of a bend that puts its detour near
 * {@link #detourTarget()} ({@link #detourCost}), spread round the lap, clear of the
 * bunched field off the grid, with the bog always the cheapest thing to go round.
 * Boost strips take what is left on the corner exits: five on a sprint's long lap,
 * three on a grand prix's short one. {@code CourseBalanceTest} holds all of that in place.
 * <p>
 * <b>Adding a course</b>: HANDOFF.md, "48 courses, phase 1" / "How to add a course".
 * Ordinals are saved (heats, the picker's answer), so a row is never removed or
 * reordered; new rows go at the END, inside their class's block.
 */
public enum RaceTrack {
	// columns: class, course index (0, 1, 2 ... within the class in table order; it places
	// the island, see slotOf), theme, shape, lap length target in blocks, laps (1 = sprint,
	// 3-5 = grand prix), features
	// ---- C: meadow, orchard, shore (0-2 grands prix, 3-5 sprints)
	C_MEADOW(RaceClass.C, 0, Theme.MEADOW, Shape.STADIUM, 305, 5, boost(0.34), boost(0.54), boost(0.86)),
	C_ORCHARD(RaceClass.C, 1, Theme.ORCHARD, Shape.ZIGZAG, 355, 4, boost(0.17), boost(0.47), boost(0.88)),
	C_SHORE(RaceClass.C, 2, Theme.SHORE, Shape.PEANUT, 460, 3, boost(0.18), boost(0.53), boost(0.88)),
	C_DOWNS(RaceClass.C, 3, Theme.MEADOW, Shape.ROVAL, 1150, 1, boost(0.11), boost(0.47), boost(0.58), boost(0.66), boost(0.93)),
	C_CIDER(RaceClass.C, 4, Theme.ORCHARD, Shape.CLOUD, 1170, 1, boost(0.42), boost(0.59), boost(0.70), boost(0.85), boost(0.93)),
	C_LAGOON(RaceClass.C, 5, Theme.SHORE, Shape.WAVE, 1190, 1, boost(0.10), boost(0.22), boost(0.46), boost(0.81), boost(0.92)),
	// ---- B: canyon, river, snow
	B_CANYON(RaceClass.B, 0, Theme.CANYON, Shape.DELTA, 340, 5, ridge(0.53, 0.66), boost(0.22), boost(0.485), boost(0.84)),
	B_FORD(RaceClass.B, 1, Theme.RIVER, Shape.KIDNEY, 395, 4, water(0.40, 0.52), boost(0.33), boost(0.82), boost(0.93)),
	B_FROST(RaceClass.B, 2, Theme.SNOW, Shape.LOLLIPOP, 500, 3, ridge(0.58, 0.62), boost(0.44), boost(0.65), boost(0.87)),
	B_MESA(RaceClass.B, 3, Theme.CANYON, Shape.SERPENT, 1280, 1, ridge(0.44, 0.48), boost(0.20), boost(0.35), boost(0.53), boost(0.83), boost(0.91)),
	B_RAPIDS(RaceClass.B, 4, Theme.RIVER, Shape.HAIRPIN, 1300, 1, water(0.51, 0.56), boost(0.22), boost(0.39), boost(0.60), boost(0.75), boost(0.92)),
	B_GLACIER(RaceClass.B, 5, Theme.SNOW, Shape.STAIRS, 1320, 1, water(0.33, 0.38), boost(0.45), boost(0.58), boost(0.66), boost(0.78), boost(0.87)),
	// ---- A: cavern, jungle, nether
	A_CRYSTAL(RaceClass.A, 0, Theme.CAVERN, Shape.DEE, 372, 5, mud(0.34, 0.44), water(0.4675, 0.5375), ridge(0.6875, 0.7375), boost(0.2725), boost(0.65), boost(0.835)),
	A_CANOPY(RaceClass.A, 1, Theme.JUNGLE, Shape.ELBOW, 440, 4, water(0.2925, 0.3225), mud(0.6575, 0.7075), ridge(0.9175, 0.9475), boost(0.2575), boost(0.595), boost(0.8825)),
	A_EMBER(RaceClass.A, 2, Theme.NETHER, Shape.TRIDENT, 590, 3, mud(0.19, 0.23), lava(0.7825, 0.8125), ridge(0.865, 0.945), boost(0.305), boost(0.51), boost(0.7325)),
	A_DEEPS(RaceClass.A, 3, Theme.CAVERN, Shape.SWITCHBACK, 1420, 1, ridge(0.13, 0.17), water(0.49, 0.54), mud(0.77, 0.80), boost(0.23), boost(0.41), boost(0.60), boost(0.68), boost(0.84)),
	A_TEMPLE(RaceClass.A, 4, Theme.JUNGLE, Shape.CASTLE, 1440, 1, water(0.15, 0.20), ridge(0.48, 0.52), mud(0.70, 0.73), boost(0.34), boost(0.57), boost(0.77), boost(0.84), boost(0.92)),
	A_INFERNO(RaceClass.A, 5, Theme.NETHER, Shape.CROWN, 1460, 1, lava(0.19, 0.24), lava(0.38, 0.43), ridge(0.68, 0.72), mud(0.86, 0.89), boost(0.11), boost(0.30), boost(0.50), boost(0.63), boost(0.79)),
	// ---- S: skyway, keep, end
	S_SKYWAY(RaceClass.S, 0, Theme.SKYWAY, Shape.BOOMERANG, 448, 5, ridge(0.15, 0.195), mud(0.43, 0.54), water(0.57, 0.615), ridge(0.65, 0.69), boost(0.115), boost(0.365), boost(0.895)),
	S_KEEP(RaceClass.S, 1, Theme.KEEP, Shape.RAMPART, 485, 4, lava(0.43, 0.475), ridge(0.51, 0.56), mud(0.69, 0.78), lava(0.89, 0.935), boost(0.125), boost(0.37), boost(0.625)),
	S_VOID(RaceClass.S, 2, Theme.END, Shape.HAMMER, 610, 3, water(0.28, 0.325), ridge(0.36, 0.405), mud(0.60, 0.67), boost(0.17), boost(0.545), boost(0.88)),
	S_STARFALL(RaceClass.S, 3, Theme.SKYWAY, Shape.SWEEPS, 1560, 1, ridge(0.09, 0.13), water(0.22, 0.28), ridge(0.49, 0.53), water(0.68, 0.72), boost(0.44), boost(0.60), boost(0.76), boost(0.81), boost(0.89)),
	S_CITADEL(RaceClass.S, 4, Theme.KEEP, Shape.HOOK, 1580, 1, lava(0.25, 0.30), ridge(0.56, 0.60), mud(0.65, 0.68), lava(0.74, 0.79), boost(0.11), boost(0.34), boost(0.51), boost(0.83), boost(0.87)),
	S_MAELSTROM(RaceClass.S, 5, Theme.END, Shape.BEE, 1600, 1, water(0.15, 0.20), ridge(0.28, 0.32), water(0.62, 0.68), ridge(0.88, 0.92), boost(0.10), boost(0.49), boost(0.54), boost(0.75), boost(0.80)),
	// ==== phase 2: new courses. Add rows ONLY between your own class's begin / end
	// markers (course indices 6-11 in order: three sprints, then three grands prix), one
	// row per line, every row ending in a comma. ====
	// ---- new C courses (phase 2) begin ----
	C_HARVEST(RaceClass.C, 6, Theme.FARMLAND, Shape.SCYTHE, 1200, 1, boost(0.16), boost(0.30), boost(0.57), boost(0.66), boost(0.78)),
	C_HONEYCOMB(RaceClass.C, 7, Theme.ORCHARD, Shape.HONEYCOMB, 1160, 1, boost(0.14), boost(0.31), boost(0.48), boost(0.64), boost(0.81)),
	C_SCALLOP(RaceClass.C, 8, Theme.SHORE, Shape.SCALLOP, 1240, 1, boost(0.17), boost(0.32), boost(0.43), boost(0.61), boost(0.76)),
	C_HEARTFIELD(RaceClass.C, 9, Theme.MEADOW, Shape.HEART, 298, 5, boost(0.30), boost(0.63), boost(0.88)),
	C_KITE_HILL(RaceClass.C, 10, Theme.MEADOW, Shape.KITE, 388, 4, boost(0.16), boost(0.54), boost(0.88)),
	C_HORSESHOE(RaceClass.C, 11, Theme.FARMLAND, Shape.HORSESHOE, 480, 3, boost(0.15), boost(0.53), boost(0.88)),
	// ---- new C courses (phase 2) end ----

	// ---- new B courses (phase 2) begin ----
	// sprints: a watering hole at the tip of a savanna tusk, a ridge in the notch of a canyon
	// arrowhead, a ford across a river's oxbow; grands prix: a savanna lozenge with a waterhole,
	// a kopje ridge on a savanna shield, a snow ridge across a mitten's thumb
	B_ACACIA(RaceClass.B, 6, Theme.SAVANNA, Shape.TUSK, 1340, 1, water(0.51, 0.54), boost(0.14), boost(0.36), boost(0.58), boost(0.69), boost(0.80)),
	B_GULCH(RaceClass.B, 7, Theme.CANYON, Shape.ARROWHEAD, 1320, 1, ridge(0.66, 0.70), boost(0.14), boost(0.33), boost(0.50), boost(0.73), boost(0.82)),
	B_OXBOW(RaceClass.B, 8, Theme.RIVER, Shape.OXBOW, 1360, 1, water(0.20, 0.25), boost(0.36), boost(0.52), boost(0.65), boost(0.79), boost(0.87)),
	B_BAOBAB(RaceClass.B, 9, Theme.SAVANNA, Shape.LOZENGE, 345, 5, water(0.41, 0.53), boost(0.155), boost(0.655), boost(0.84)),
	B_KOPJE(RaceClass.B, 10, Theme.SAVANNA, Shape.HEATER, 405, 4, ridge(0.53, 0.65), boost(0.30), boost(0.48), boost(0.82)),
	B_SNOWCAP(RaceClass.B, 11, Theme.SNOW, Shape.MITTEN, 515, 3, ridge(0.56, 0.60), boost(0.44), boost(0.74), boost(0.89)),
	// ---- new B courses (phase 2) end ----

	// ---- new A courses (phase 2) begin ----
	A_TOADSTOOL(RaceClass.A, 6, Theme.MUSHROOM, Shape.TOADSTOOL, 1480, 1, ridge(0.29, 0.33), mud(0.47, 0.51), water(0.66, 0.70), boost(0.11), boost(0.22), boost(0.36), boost(0.73), boost(0.86)),
	A_AMMONITE(RaceClass.A, 7, Theme.CAVERN, Shape.AMMONITE, 1440, 1, ridge(0.36, 0.41), ridge(0.49, 0.52), mud(0.83, 0.86), boost(0.10), boost(0.22), boost(0.30), boost(0.55), boost(0.72)),
	A_FORGE(RaceClass.A, 8, Theme.NETHER, Shape.ANVILHORN, 1500, 1, lava(0.30, 0.33), mud(0.46, 0.49), lava(0.64, 0.67), boost(0.14), boost(0.25), boost(0.37), boost(0.70), boost(0.86)),
	A_GROTTO(RaceClass.A, 9, Theme.CAVERN, Shape.BLINDFISH, 405, 5, water(0.3175, 0.3675), mud(0.41, 0.49), water(0.6875, 0.7275), boost(0.5875), boost(0.6275), boost(0.8725)),
	A_MOONSHELF(RaceClass.A, 10, Theme.MUSHROOM, Shape.SHELFCAP, 455, 4, ridge(0.465, 0.495), lava(0.5275, 0.5575), mud(0.6875, 0.7275), boost(0.3), boost(0.615), boost(0.92)),
	A_MACHETE(RaceClass.A, 11, Theme.JUNGLE, Shape.MACHETE, 570, 3, water(0.4125, 0.4525), mud(0.48, 0.53), ridge(0.5825, 0.6225), boost(0.2625), boost(0.7225), boost(0.9325)),
	// ---- new A courses (phase 2) end ----

	// ---- new S courses (phase 2) begin ----
	S_ABYSS(RaceClass.S, 6, Theme.DEEP_DARK, Shape.NAUTILUS, 1620, 1, water(0.20, 0.27), ridge(0.34, 0.41), water(0.46, 0.51), mud(0.87, 0.92), boost(0.12), boost(0.30), boost(0.62), boost(0.71), boost(0.80)),
	S_ZENITH(RaceClass.S, 7, Theme.END, Shape.COMET, 1600, 1, ridge(0.28, 0.35), water(0.40, 0.47), mud(0.56, 0.61), ridge(0.84, 0.90), boost(0.12), boost(0.20), boost(0.51), boost(0.66), boost(0.75)),
	S_BASTION(RaceClass.S, 8, Theme.KEEP, Shape.STARFORT, 1580, 1, water(0.27, 0.30), ridge(0.47, 0.50), mud(0.57, 0.63), water(0.67, 0.70), boost(0.12), boost(0.33), boost(0.53), boost(0.73), boost(0.92)),
	S_ORBIT(RaceClass.S, 9, Theme.SKYWAY, Shape.SATURN, 450, 5, water(0.12, 0.17), mud(0.38, 0.48), water(0.71, 0.755), boost(0.275), boost(0.555), boost(0.63)),
	S_ECLIPSE(RaceClass.S, 10, Theme.SKYWAY, Shape.CRESCENT_MOON, 485, 4, mud(0.36, 0.465), ridge(0.49, 0.535), ridge(0.89, 0.935), boost(0.105), boost(0.30), boost(0.56)),
	S_RIFT(RaceClass.S, 11, Theme.DEEP_DARK, Shape.FISSURE, 610, 3, ridge(0.21, 0.255), ridge(0.32, 0.365), mud(0.52, 0.62), water(0.72, 0.765), boost(0.15), boost(0.645), boost(0.87)),
	// ---- new S courses (phase 2) end ----
	;

	/** A stretch of the direct line: terrain (with a detour road around it) or a boost strip. */
	public record Feature(Type type, double start, double end) {
		public enum Type { WATER, RIDGE, LAVA, MUD, BOOST }

		public boolean covers(double t) {
			return t >= start && t <= end;
		}

		/** Terrain features have a detour; boost strips are just part of the road. */
		public boolean terrain() {
			return type != Type.BOOST;
		}

		/** Birds that take the direct line at full pace (nobody suits a bog). */
		public boolean suits(ChocoboColor c) {
			return switch (type) {
				case WATER -> c.waterWalk();
				case RIDGE -> c.climb();
				case LAVA -> c.lavaWalk();
				case MUD -> false;
				case BOOST -> true;
			};
		}
	}

	/**
	 * Visual dressing of a course: road, kerbs (corner stripes), rail, wall / trim, margin
	 * ground, island rock, posts, lamps. Four per class: the first three are shared by the
	 * original sprint / grand prix pairs, the fourth (FARMLAND, SAVANNA, MUSHROOM, DEEP_DARK)
	 * arrived with the 48-course plan. A theme's scenery lives in {@code RaceCourseLayout}
	 * ({@code decorate}, {@code verge}, {@code themeLandmark}); its music in
	 * {@link RaceScoring#raceLoopKey}; its picker name in lang {@code chocobosreborn.select.theme.<name>}.
	 */
	public enum Theme {
		MEADOW("dirt_path", "red_concrete", "white_concrete", "oak_fence", "mossy_stone_bricks", "grass_block", "dirt", "oak_log", "lantern[hanging=false]"),
		ORCHARD("packed_mud", "orange_concrete", "white_concrete", "spruce_fence", "mud_bricks", "grass_block", "dirt", "stripped_spruce_log", "lantern[hanging=false]"),
		SHORE("smooth_sandstone", "red_concrete", "white_concrete", "bamboo_fence", "cut_sandstone", "sand", "sandstone", "stripped_jungle_log", "sea_lantern"),
		CANYON("terracotta", "red_terracotta", "white_terracotta", "dark_oak_fence", "orange_terracotta", "red_sand", "terracotta", "dark_oak_log", "lantern[hanging=false]"),
		RIVER("gravel", "red_concrete", "white_concrete", "spruce_fence", "cobblestone", "grass_block", "stone", "spruce_log", "lantern[hanging=false]"),
		SNOW("snow_block", "red_concrete", "light_blue_concrete", "spruce_fence", "packed_ice", "snow_block", "snow_block", "spruce_log", "sea_lantern"),
		CAVERN("polished_deepslate", "red_concrete", "white_concrete", "deepslate_tile_wall", "deepslate_bricks", "deepslate", "deepslate", "polished_basalt", "sea_lantern"),
		JUNGLE("mud_bricks", "red_concrete", "lime_concrete", "jungle_fence", "mossy_cobblestone", "moss_block", "moss_block", "jungle_log", "lantern[hanging=false]"),
		NETHER("basalt", "red_nether_bricks", "nether_bricks", "nether_brick_fence", "polished_blackstone", "netherrack", "netherrack", "polished_blackstone", "glowstone"),
		SKYWAY("white_concrete", "magenta_stained_glass", "cyan_stained_glass", "air", "quartz_block", "quartz_block", "quartz_block", "quartz_pillar", "sea_lantern"),
		KEEP("polished_blackstone", "red_concrete", "black_concrete", "polished_blackstone_wall", "polished_blackstone_bricks", "blackstone", "blackstone", "crying_obsidian", "shroomlight"),
		END("end_stone_bricks", "purple_concrete", "white_concrete", "end_stone_brick_wall", "purpur_block", "end_stone", "end_stone", "purpur_pillar", "end_rod"),
		// the 48-course themes, one per class (open road / water or ridge / all four / all four)
		/** C: wheat fields, hay bales, scarecrows and birch fences; open road. */
		FARMLAND("coarse_dirt", "lime_concrete", "white_concrete", "birch_fence", "bricks", "grass_block", "dirt", "stripped_birch_log", "lantern[hanging=false]"),
		/** B: acacia and coarse dirt under terracotta mesas; a watering hole (water) or a kopje (ridge). */
		SAVANNA("smooth_red_sandstone", "orange_concrete", "black_concrete", "acacia_fence", "stripped_acacia_wood", "coarse_dirt", "red_sandstone", "stripped_acacia_log", "lantern[hanging=false]"),
		/** A: mycelium and giant mushrooms, mushroom-block kerbs, froglights; every feature. */
		MUSHROOM("podzol", "red_mushroom_block", "mushroom_stem", "mangrove_fence", "tuff_bricks", "mycelium", "dirt", "mushroom_stem", "pearlescent_froglight"),
		/** S: the ancient city: sculk, deepslate tiles, soul lanterns, a warden's frame; every feature. */
		DEEP_DARK("deepslate_tiles", "cyan_concrete", "light_gray_concrete", "cobbled_deepslate_wall", "polished_deepslate", "sculk", "cobbled_deepslate", "chiseled_deepslate", "soul_lantern[hanging=false]"),
		// ---- new C themes (phase 2, only if a course truly needs one) begin ----
		// ---- new C themes (phase 2) end ----

		// ---- new B themes (phase 2) begin ----
		// ---- new B themes (phase 2) end ----

		// ---- new A themes (phase 2) begin ----
		// ---- new A themes (phase 2) end ----

		// ---- new S themes (phase 2) begin ----
		// ---- new S themes (phase 2) end ----
		;

		public final String road, kerbA, kerbB, rail, wall, ground, base, post, lamp;

		Theme(String road, String kerbA, String kerbB, String rail, String wall, String ground, String base, String post, String lamp) {
			this.road = road;
			this.kerbA = kerbA;
			this.kerbB = kerbB;
			this.rail = rail;
			this.wall = wall;
			this.ground = ground;
			this.base = base;
			this.post = post;
			this.lamp = lamp;
		}
	}

	/**
	 * Circuit templates in course units (scaled per track) with a hill profile in
	 * blocks. The first three points are collinear along +x: the start straight (t = 0
	 * is the second point, so the line and the grid at t = 0.02 sit on dead-straight road). Legs stay at least 22 units apart so lanes and detours
	 * never touch another leg at any scale in use.
	 */
	public enum Shape {
		/** A short stadium oval: a crest through turn one, a bus-stop chicane kinking out of the back straight, and a long tightening final sweeper onto the line. */
		STADIUM(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(82, 0, 1), p(98, 8, 2), p(106, 24, 4), p(100, 40, 3), p(84, 46, 1), p(66, 46, 0), p(46, 46, 0), p(38, 56, 0), p(24, 56, 0), p(16, 46, 0), p(0, 46, 0), p(-16, 46, 0), p(-34, 44, 0), p(-48, 34, 0), p(-53, 20, 0), p(-46, 6, 0), p(-30, 0, 0)),
		/** A lightning bolt: esses stepping out and up the right side, a plateau across the top, and a zig back down the far side. */
		ZIGZAG(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(88, 0, 0), p(102, 10, 1), p(104, 30, 2), p(112, 46, 3), p(130, 56, 3), p(140, 74, 4), p(138, 96, 5), p(124, 106, 5), p(102, 104, 5), p(84, 98, 5), p(70, 84, 4), p(58, 66, 3), p(40, 56, 2), p(16, 54, 1), p(-10, 54, 0), p(-30, 46, 0), p(-40, 28, 0), p(-40, 10, 0), p(-30, 0, 0)),
		/** Two lobes through a narrow waist: the climb through the waist, a round top lobe at the crest, down through the waist again and a harbour chicane before the line. */
		PEANUT(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(77, 0, 0), p(94, 0, 0), p(108, 9, 1), p(115, 25, 1), p(120, 41, 1), p(113, 56, 2), p(102, 68, 2), p(94, 83, 3), p(98, 99, 3), p(107, 114, 4), p(113, 129, 4), p(106, 144, 5), p(94, 157, 5), p(82, 168, 5), p(66, 168, 5), p(53, 158, 5), p(39, 148, 4), p(30, 134, 3), p(32, 118, 1), p(37, 101, 0), p(38, 85, 0), p(28, 71, 0), p(15, 60, 0), p(-1, 61, 0), p(-17, 67, 0), p(-34, 71, 0), p(-48, 63, 0), p(-60, 51, 0), p(-63, 34, 0), p(-60, 18, 0), p(-51, 4, 0), p(-30, 0, 0)),
		/** A canyon triangle: a hairpin off the line, a long climb to a crest hairpin, a pass bowing in down the far side, a hairpin home. */
		DELTA(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(70, 0, 0), p(80, 0, 0), p(90.1, 3.1, 0), p(96.9, 11.3, 0), p(97.9, 21.8, 0), p(92.9, 31.2, 0), p(84.2, 39.9, 0.7), p(75.4, 48.7, 1.4), p(66.7, 57.4, 2.1), p(57.9, 66.2, 2.9), p(49.1, 74.9, 3.6), p(40.4, 83.7, 4.3), p(31.6, 92.5, 5), p(17.8, 97.2, 5.5), p(5.6, 89, 6), p(1.6, 82.1, 4), p(-6.2, 70.5, 4), p(-15.6, 60.1, 4), p(-26.3, 51.2, 4), p(-36.1, 44, 3.3), p(-45.9, 36.9, 2.7), p(-55.7, 29.8, 2), p(-62.4, 18.2, 1.3), p(-58.2, 5.5, 0.7), p(-46, 0, 0), p(-30, 0, 0)),
		/** An ice lolly: the stick (out along the line, back on the other side, a hairpin at its end) and a round loop swelling off it. */
		LOLLIPOP(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(69, 0, 0), p(80.7, -2.1, 0.5), p(91, -8, 1), p(101.3, -14.4, 1.2), p(113.1, -17.7, 1.5), p(125.3, -17.6, 1.8), p(136.9, -14, 2), p(147.1, -7.4, 2.2), p(155.1, 1.9, 2.5), p(160.1, 13, 2.8), p(161.8, 25.1, 3), p(160.1, 37.1, 3.2), p(155.1, 48.2, 3.5), p(147.1, 57.5, 3.8), p(136.9, 64.2, 4), p(125.3, 67.7, 4.2), p(113.1, 67.8, 4.5), p(101.3, 64.5, 4.8), p(91, 58.1, 5), p(80.7, 52.2, 4.5), p(69, 50.1, 4), p(57.3, 50.1, 3.8), p(45.7, 50.1, 3.6), p(34, 50.1, 3.3), p(22.3, 50.1, 3.1), p(10.7, 50.1, 2.9), p(-1, 50.1, 2.7), p(-12.7, 50.1, 2.4), p(-24.3, 50.1, 2.2), p(-36, 50.1, 2), p(-48.1, 46.9, 1.7), p(-57, 38, 1.3), p(-60.3, 25.8, 1), p(-60.3, 24.3, 1), p(-57, 12.1, 0.7), p(-48.1, 3.3, 0.3), p(-36, 0, 0), p(-30, 0, 0)),
		/** A kidney bean: two round lobes and a river bend bowing deep in between them along the back. */
		KIDNEY(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(73.6, 0, 0), p(85.9, 2.5, 0.2), p(96.3, 9.4, 0.5), p(103.3, 19.9, 0.8), p(105.8, 32.2, 1), p(103.8, 43.2, 2.2), p(98.2, 52.9, 2.3), p(89.7, 60, 2.5), p(79.2, 63.9, 2.7), p(68, 63.9, 2.8), p(57.5, 60, 3), p(48.9, 55, 4), p(37.4, 49.9, 4), p(25.1, 47.3, 4), p(12.5, 47.3, 4), p(0.2, 49.9, 4), p(-11.2, 55, 4), p(-19.9, 60, 3), p(-31, 64, 2.8), p(-42.7, 63.7, 2.6), p(-53.5, 59.2, 2.4), p(-62, 51.1, 2.2), p(-67.1, 40.5, 2), p(-68, 28.8, 1.8), p(-64.7, 17.6, 1.6), p(-57.5, 8.3, 1.4), p(-47.5, 2.1, 1.2), p(-36, 0, 1), p(-30, 0, 0)),
		/** A D: a long flat back (the start straight), a bowl climbing round the far end, a crest straight sagging home over the top, and a short flat side dropping back to the line. */
		DEE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(72, 0, 0), p(81.9, 2, 0), p(90.4, 7.6, 1), p(96, 16.1, 2), p(98, 26, 2), p(96, 35.9, 2), p(90.4, 44.4, 3), p(81.9, 50, 4), p(72, 52, 4), p(61.7, 52.2, 4), p(51.4, 52.9, 4), p(41.1, 54.1, 4), p(30.9, 55.7, 4), p(20.8, 57.8, 4), p(10.8, 60.3, 4), p(0.9, 63.3, 4), p(-7.6, 66, 4), p(-16.1, 68.8, 4), p(-24.6, 71.5, 4), p(-32.8, 71.7, 3), p(-39.7, 67.1, 1), p(-42.9, 59.5, 0), p(-43.9, 50.1, 0), p(-45.5, 40.8, 0), p(-47.5, 31.6, 0), p(-50, 22.5, 0), p(-51.4, 18.1, 0), p(-51.3, 9.5, 0), p(-46.2, 2.6, 0), p(-38, 0, 0), p(-30, 0, 0)),
		/** A long L, a boot: the foot is the start straight with a double-apex hairpin at the toe, the inside of the elbow one long reverse sweeper, a climb up the upright to a hairpin at the top and a gentle run down its back. */
		ELBOW(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(70, 0, 0), p(80, 0, 0), p(91, 2.9, 0), p(99.1, 11, 0), p(102, 22, 0), p(102, 30, 0), p(99.1, 41, 0), p(91, 49.1, 0), p(80, 52, 0), p(68.4, 52.3, 0), p(56.8, 53.1, 0), p(45.2, 54.4, 0), p(34.9, 57.2, 0), p(25.4, 62.3, 0), p(17.5, 69.5, 0), p(11.5, 78.3, 0), p(7.7, 88.4, 0), p(6.5, 99, 0), p(6.5, 104, 2), p(4.5, 113.9, 2), p(-1.1, 122.4, 3), p(-9.6, 128, 4), p(-19.5, 130, 4), p(-29.5, 128, 4), p(-37.9, 122.4, 4), p(-43.6, 113.9, 5), p(-45.5, 104, 5), p(-45.8, 93.1, 5), p(-46.5, 82.2, 5), p(-47.7, 71.4, 5), p(-49.3, 60.6, 5), p(-51, 51.3, 4), p(-52.6, 42, 4), p(-54.2, 32.8, 4), p(-55.9, 23.5, 3), p(-54.5, 12.1, 2), p(-47.2, 3.3, 1), p(-36.2, 0, 0), p(-30, 0, 0)),
		/** A trident head: a broad back (the start straight) and three points standing up off it, two deep notches between them, the middle point a free-standing tine over the crest. */
		TRIDENT(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(69.5, 0, 0), p(79, 0, 0), p(88.5, 0, 0), p(98, 0, 0), p(107.5, 0, 0), p(117, 0, 0), p(126.5, 0, 0), p(136.5, 2.7, 0), p(143.8, 10, 0), p(146.5, 20, 0), p(146.5, 29.4, 0), p(146.5, 38.9, 0), p(146.5, 48.3, 0), p(146.5, 57.7, 0), p(146.5, 67.1, 0), p(146.5, 76.6, 0), p(146.5, 86, 0), p(144.4, 94, 0), p(138.5, 99.9, 1), p(130.5, 102, 1), p(118.5, 102, 1), p(106.5, 102, 1), p(98, 98.5, 1), p(94.5, 90, 1), p(94.5, 80, 2), p(92.6, 70.4, 2), p(87.2, 62.3, 2), p(79.1, 56.9, 2), p(69.5, 55, 2), p(59.9, 56.9, 3), p(51.8, 62.3, 3), p(46.4, 70.4, 3), p(44.5, 80, 3), p(44.5, 82, 4), p(42.4, 91.1, 4), p(36.6, 98.4, 4), p(28.2, 102.5, 4), p(18.8, 102.5, 5), p(10.4, 98.4, 5), p(4.6, 91.1, 5), p(2.5, 82, 5), p(2.5, 80, 4), p(0.6, 70.4, 4), p(-4.8, 62.3, 4), p(-12.9, 56.9, 4), p(-22.5, 55, 4), p(-32.1, 56.9, 3), p(-40.2, 62.3, 3), p(-45.6, 70.4, 3), p(-47.5, 80, 3), p(-47.5, 90, 2), p(-51, 98.5, 2), p(-59.5, 102, 1), p(-71.5, 102, 1), p(-83.5, 102, 1), p(-91.5, 99.9, 1), p(-97.4, 94, 1), p(-99.5, 86, 1), p(-99.5, 76.6, 1), p(-99.5, 67.1, 1), p(-99.5, 57.7, 1), p(-99.5, 48.3, 1), p(-99.5, 38.9, 1), p(-99.5, 29.4, 1), p(-99.5, 20, 1), p(-96.8, 10, 1), p(-89.5, 2.7, 0), p(-79.5, 0, 0), p(-69.6, 0, 0), p(-59.7, 0, 0), p(-49.8, 0, 0), p(-39.9, 0, 0), p(-30, 0, 0)),
		/** A boomerang: an elbow off the line, up one arm to a crest hairpin at its tip, back down through the bog in the notch and along the other arm to a hairpin home. */
		BOOMERANG(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(70, 0, 0), p(79.6, 1.7, 1.5), p(88.1, 6.6, 3), p(98.1, 14.9, 3), p(108, 23.3, 3), p(118, 31.7, 3), p(127.9, 40, 3), p(134.5, 49.5, 4), p(135.5, 60.9, 5), p(130.7, 71.4, 6), p(121.2, 78, 4), p(109.8, 79, 2), p(99.3, 74.1, 0), p(94.7, 70.3, 0), p(84.5, 62.6, 0), p(73.4, 56.2, 0), p(61.6, 51.1, 0), p(49.4, 47.5, 0), p(36.8, 45.3, 0), p(24, 44.5, 0), p(10.5, 44.5, 0), p(-3, 44.5, 0), p(-16.5, 44.5, 0), p(-30, 44.5, 0), p(-41.1, 41.5, 0), p(-49.3, 33.4, 0), p(-52.3, 22.3, 0), p(-49.3, 11.1, 0), p(-41.1, 3, 0), p(-30, 0, 0)),
		/** A square keep: four right-angle corners round the curtain walls, up to the battlements and back, the west wall pinched in round its moat. */
		RAMPART(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(72, 0, 0), p(85.1, 5.4, 1), p(90.5, 18.5, 2), p(90.5, 31.3, 2), p(90.5, 44.2, 2), p(90.5, 57.1, 2), p(90.5, 70, 2), p(90.5, 82.8, 2), p(90.5, 95.7, 2), p(85.1, 108.8, 3), p(72, 114.2, 4), p(60.7, 114.2, 4), p(49.3, 114.2, 4), p(38, 114.2, 4), p(26.7, 114.2, 4), p(15.3, 114.2, 4), p(4, 114.2, 4), p(-7.3, 114.2, 4), p(-18.7, 114.2, 4), p(-30, 114.2, 4), p(-40.9, 110.5, 3.3), p(-47.5, 101.1, 2.7), p(-47.2, 89.6, 2), p(-44, 79, 2), p(-42.1, 68.1, 2), p(-41.5, 57.1, 2), p(-42.1, 46, 2), p(-44, 35.2, 2), p(-47.2, 24.6, 2), p(-47.5, 13.1, 1.3), p(-40.9, 3.6, 0.7), p(-30, 0, 0)),
		/** A hammer on its head: the head along the start line, a hairpin at each end of it and a tall handle, up one side, over the top and down a waisted side. */
		HAMMER(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(75, 0, 0), p(90, 0, 0), p(102.5, 4.1, 0), p(110.3, 14.8, 0), p(110.3, 27.9, 0), p(102.5, 38.6, 0), p(90, 42.7, 0), p(87, 42.7, 0), p(74.4, 46.1, 1), p(65.1, 55.3, 2), p(61.8, 68, 3), p(61.8, 79.8, 3), p(61.8, 91.6, 3), p(61.8, 103.4, 3), p(61.8, 115.1, 3), p(61.8, 126.9, 3), p(61.8, 138.7, 3), p(61.8, 150.5, 3), p(59, 162.8, 3.4), p(51.1, 172.6, 3.9), p(39.8, 178, 4.3), p(27.2, 178, 4.7), p(15.9, 172.6, 5.1), p(8.1, 162.8, 5.6), p(5.3, 150.5, 6), p(5.3, 145.5, 3), p(5.7, 139, 3), p(7, 132.6, 3), p(9.3, 121, 3), p(10.1, 109.2, 3), p(9.3, 97.5, 3), p(7, 85.9, 3), p(5.7, 79.5, 3), p(5.3, 73, 3), p(5.3, 68, 3), p(1.9, 55.3, 2), p(-7.4, 46.1, 1), p(-20, 42.7, 0), p(-30, 42.7, 0), p(-42.5, 38.6, 0), p(-50.3, 27.9, 0), p(-50.3, 14.8, 0), p(-42.5, 4.1, 0), p(-30, 0, 0)),
		/** A superspeedway with an infield esses section. */
		ROVAL(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 0, 0), p(180, 0, 0), p(214, 10, 1), p(230, 40, 2), p(214, 70, 2), p(180, 80, 2), p(150, 72, 3), p(130, 50, 4), p(110, 40, 4), p(90, 50, 4), p(76, 72, 3), p(60, 90, 2), p(30, 90, 1), p(0, 90, 0), p(-30, 80, 0), p(-46, 50, 0), p(-30, 20, 0), p(-30, 0, 0)),
		/** A trefoil of round lobes, a cloud seen from above. */
		CLOUD(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(130, 6, 1), p(150, 30, 2), p(146, 60, 3), p(120, 76, 3), p(126, 106, 4), p(150, 130, 5), p(146, 160, 5), p(120, 180, 4), p(90, 184, 3), p(70, 166, 2), p(50, 184, 2), p(20, 180, 1), p(-6, 160, 0), p(-10, 130, 0), p(6, 106, 0), p(0, 76, 0), p(-24, 60, 0), p(-30, 30, 0), p(-30, 0, 0)),
		/** A long straight and a rolling wave of esses on the way back. */
		WAVE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 0, 0), p(180, 0, 0), p(212, 10, 1), p(220, 40, 2), p(200, 64, 3), p(166, 60, 4), p(136, 74, 4), p(106, 96, 3), p(76, 90, 3), p(52, 70, 2), p(26, 68, 2), p(0, 84, 1), p(-20, 72, 0), p(-30, 40, 0), p(-30, 0, 0)),
		/** Winding S-bends over a rise. */
		SERPENT(p(0, 0, 0), p(30, 0, 0), p(60, 0, 1), p(90, 0, 3), p(100, 26, 5), p(84, 48, 6), p(60, 52, 5), p(40, 60, 3), p(36, 80, 2), p(50, 96, 1), p(76, 100, 0), p(100, 108, 0), p(104, 130, 0), p(84, 146, 0), p(50, 146, 0), p(16, 142, 0), p(-6, 124, 0), p(-8, 96, 0), p(2, 74, 0), p(0, 52, 0), p(-10, 30, 0), p(-30, 0, 0)),
		/** Long straight, sweeper, hill leg, hairpin at the top, long run home. */
		HAIRPIN(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(90, 0, 0), p(112, 10, 1), p(118, 32, 3), p(104, 50, 5), p(80, 54, 6), p(52, 54, 6), p(38, 62, 5), p(32, 80, 4), p(42, 96, 2), p(60, 100, 0), p(90, 100, 0), p(112, 110, 0), p(112, 130, 0), p(92, 142, 0), p(60, 142, 0), p(28, 140, 0), p(2, 126, 0), p(-8, 100, 0), p(-8, 70, 0), p(-8, 40, 0), p(-30, 0, 0)),
		/** A staircase of right-angle steps climbing away, a long run back down. */
		STAIRS(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(124, 10, 1), p(124, 50, 2), p(140, 64, 3), p(184, 64, 3), p(206, 78, 4), p(206, 118, 5), p(222, 132, 6), p(266, 132, 6), p(286, 148, 6), p(286, 190, 5), p(266, 206, 4), p(40, 206, 1), p(10, 196, 0), p(4, 160, 0), p(-10, 120, 0), p(-20, 80, 0), p(-26, 40, 0), p(-30, 0, 0)),
		/** Four parallel straights joined by hairpins: a mountain switchback. */
		SWITCHBACK(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(130, 0, 0), p(154, 14, 1), p(154, 42, 2), p(130, 56, 3), p(90, 56, 3), p(50, 56, 3), p(28, 70, 4), p(28, 98, 5), p(50, 112, 6), p(90, 112, 6), p(130, 112, 6), p(154, 126, 5), p(154, 154, 4), p(130, 168, 3), p(90, 168, 2), p(50, 168, 1), p(10, 164, 0), p(-14, 140, 0), p(-22, 100, 0), p(-26, 60, 0), p(-30, 20, 0), p(-30, 0, 0)),
		/** Ramparts and a keep: right-angle corners around an inner courtyard. */
		CASTLE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(90, 0, 0), p(100, 10, 1), p(100, 40, 3), p(90, 50, 4), p(60, 50, 4), p(50, 60, 5), p(50, 90, 3), p(60, 100, 2), p(100, 100, 1), p(110, 110, 0), p(110, 140, 0), p(100, 150, 0), p(20, 150, 0), p(-6, 140, 0), p(-24, 110, 0), p(-30, 60, 0), p(-30, 20, 0), p(-30, 0, 0)),
		/** An M: two tall humps with a deep valley between them. */
		CROWN(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 0, 0), p(180, 0, 0), p(214, 10, 1), p(226, 40, 2), p(214, 70, 3), p(190, 90, 4), p(180, 130, 5), p(196, 160, 6), p(180, 186, 6), p(140, 192, 5), p(110, 176, 4), p(100, 140, 3), p(90, 110, 2), p(70, 100, 2), p(50, 120, 3), p(40, 150, 4), p(30, 180, 5), p(0, 190, 4), p(-24, 176, 3), p(-30, 140, 2), p(-24, 100, 1), p(-28, 60, 0), p(-30, 20, 0), p(-30, 0, 0)),
		/** Huge swooping sweepers over the biggest hills. */
		SWEEPS(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 1), p(140, 10, 3), p(170, 40, 6), p(176, 80, 9), p(160, 116, 10), p(126, 130, 9), p(90, 124, 7), p(60, 140, 6), p(50, 176, 5), p(66, 206, 4), p(100, 214, 4), p(136, 208, 5), p(160, 230, 6), p(150, 260, 5), p(110, 272, 3), p(60, 268, 1), p(20, 250, 0), p(0, 220, 0), p(-4, 180, 0), p(-2, 140, 0), p(-8, 100, 0), p(-16, 60, 0), p(-26, 30, 0), p(-30, 0, 0)),
		/** A drag straight into a loop that doubles back on itself. */
		HOOK(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 0, 0), p(180, 0, 0), p(214, 12, 1), p(230, 44, 2), p(216, 76, 3), p(184, 88, 3), p(150, 80, 2), p(130, 56, 2), p(110, 40, 2), p(80, 44, 2), p(60, 64, 3), p(60, 100, 4), p(80, 120, 4), p(110, 124, 3), p(140, 140, 2), p(140, 170, 1), p(110, 190, 0), p(60, 192, 0), p(20, 180, 0), p(-10, 150, 0), p(-20, 110, 0), p(-24, 70, 0), p(-30, 30, 0), p(-30, 0, 0)),
		/** A B: a straight spine and two round bulges pinched in the middle. */
		BEE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 6, 1), p(170, 30, 2), p(176, 64, 3), p(166, 90, 3), p(150, 104, 4), p(166, 120, 4), p(178, 146, 5), p(170, 184, 4), p(140, 206, 3), p(100, 212, 2), p(60, 208, 1), p(20, 206, 0), p(-10, 190, 0), p(-20, 150, 0), p(-24, 110, 0), p(-26, 70, 0), p(-28, 36, 0), p(-30, 0, 0)),
		// ==== phase 2: new silhouettes, one per new course. Add ONLY between your own
		// class's markers, one shape per line (javadoc line above it), ending in a comma.
		// Rules: HANDOFF.md "How to add a course" (first three points collinear along +x,
		// last point (-30, 0), legs far enough apart for detours, hills 2-12 blocks). ====
		// ---- new C shapes (phase 2) begin ----
		/** A scythe: the handle is the start straight, the blade sweeps up and back over rolling hills to its tip. */
		SCYTHE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(110, 0, 1), p(170, 0, 2), p(208, 12, 3), p(230, 45, 5), p(238, 95, 6), p(226, 145, 5), p(192, 190, 7), p(140, 220, 8), p(80, 232, 6), p(20, 226, 7), p(-24, 206, 8), p(-40, 176, 8), p(-18, 150, 7), p(30, 152, 5), p(84, 150, 6), p(128, 136, 4), p(158, 108, 3), p(164, 76, 2), p(140, 56, 1), p(90, 52, 1), p(30, 52, 0), p(-20, 50, 0), p(-46, 30, 0), p(-30, 0, 0)),
		/** A kite: a long diamond whose far corner is the tip over the crest, and its tail streaming in an S down the run back to the point. */
		KITE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(76, 0, 0), p(93, 3, 0), p(104, 15, 1), p(107, 32, 1), p(108, 48, 2), p(110, 64, 3), p(111, 80, 4), p(112, 96, 4), p(103, 111, 5), p(88, 117, 6), p(73, 123, 6), p(58, 128, 6), p(42, 134, 6), p(26, 137, 6), p(11, 129, 5), p(5, 114, 4), p(1, 98, 4), p(-8, 84, 3), p(-21, 74, 2), p(-33, 63, 2), p(-44, 51, 1), p(-48, 35, 1), p(-52, 19, 0), p(-45, 4, 0), p(-30, 0, 0)),
		/** A scallop shell: the hinge and its two ears along the bottom, a ribbed rim rolling over the top. */
		SCALLOP(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(132, 6, 1), p(146, 30, 1), p(124, 52, 2), p(150, 84, 2), p(196, 110, 3), p(224, 150, 4), p(206, 176, 4), p(196, 214, 5), p(164, 234, 5), p(140, 262, 6), p(100, 266, 6), p(70, 282, 6), p(30, 282, 6), p(0, 266, 5), p(-40, 262, 5), p(-64, 234, 4), p(-96, 214, 4), p(-106, 176, 3), p(-124, 150, 3), p(-96, 110, 2), p(-50, 84, 1), p(-24, 52, 1), p(-46, 30, 0), p(-40, 6, 0), p(-30, 0, 0)),
		/** A heart: a long lobe up to the crest, a flick through the dip at the top, the second lobe and the point onto the start straight. */
		HEART(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(96, 1, 1), p(110, 10, 2), p(118, 26, 3), p(114, 44, 4), p(100, 56, 4), p(84, 60, 5), p(72, 60, 5), p(62, 70, 5), p(49, 86, 5), p(30, 96, 4), p(10, 96, 3), p(-10, 88, 2), p(-24, 72, 1), p(-32, 54, 0), p(-38, 34, 0), p(-44, 18, 0), p(-40, 6, 0), p(-30, 0, 0)),
		/** Three honeycomb cells: twelve short sides and gentle corners, climbing cell to cell. */
		HONEYCOMB(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(90, 0, 0), p(120, 0, 0), p(135, 52, 0), p(150, 104, 1), p(210, 104, 2), p(270, 104, 3), p(300, 156, 3), p(330, 208, 4), p(300, 260, 4), p(270, 312, 5), p(210, 312, 5), p(150, 312, 5), p(120, 364, 6), p(90, 416, 6), p(30, 416, 6), p(-30, 416, 5), p(-60, 364, 5), p(-90, 312, 4), p(-60, 260, 3), p(-30, 208, 3), p(-60, 156, 2), p(-90, 104, 1), p(-60, 52, 0), p(-30, 0, 0)),
		/** A horseshoe, heels up: a fast toe (the start straight), over one heel, down into the notch and a compression at its foot, up over the other heel. */
		HORSESHOE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(77, 0, 0), p(94, 2, 0), p(107, 13, 1), p(112, 29, 1), p(112, 46, 2), p(112, 63, 3), p(112, 80, 3), p(112, 97, 4), p(105, 112, 5), p(90, 118, 5), p(74, 113, 5), p(65, 99, 5), p(65, 82, 5), p(56, 68, 3), p(40, 64, 1), p(23, 64, 1), p(9, 73, 1), p(5, 89, 2), p(2, 106, 3), p(-10, 117, 4), p(-27, 118, 5), p(-43, 114, 5), p(-52, 99, 5), p(-53, 82, 4), p(-54, 65, 3), p(-56, 48, 2), p(-57, 32, 1), p(-53, 15, 0), p(-41, 4, 0), p(-30, 0, 0)),
		// ---- new C shapes (phase 2) end ----

		// ---- new B shapes (phase 2) begin ----
		/** A crescent tusk: a long climbing outer sweep, a hairpin at the tip, a concave run home. */
		TUSK(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 1), p(150, 10, 2), p(200, 40, 3), p(236, 90, 3), p(250, 150, 3), p(236, 210, 3), p(200, 254, 2), p(160, 272, 1), p(126, 266, 0), p(120, 240, 0), p(160, 216, 0), p(186, 180, 1), p(192, 140, 2), p(180, 104, 3), p(150, 76, 3), p(110, 58, 2), p(60, 54, 1), p(16, 56, 0), p(-16, 44, 0), p(-30, 20, 0), p(-30, 0, 0)),
		/** An arrowhead: two long flanks out to a sharp point, and a notched tail back by the line. */
		ARROWHEAD(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(150, 0, 1), p(200, 14, 2), p(250, 44, 3), p(296, 82, 4), p(310, 104, 5), p(296, 126, 5), p(250, 164, 4), p(200, 194, 3), p(150, 208, 2), p(100, 212, 1), p(60, 212, 0), p(20, 210, 0), p(-20, 204, 0), p(-40, 186, 0), p(-30, 160, 0), p(10, 124, 0), p(26, 106, 0), p(10, 86, 0), p(-30, 50, 0), p(-44, 22, 0), p(-30, 0, 0)),
		/** A meandering river: a pinched oxbow loop out, a long falling diagonal home. */
		OXBOW(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 0, 0), p(174, 14, 0), p(190, 44, 0), p(180, 76, 0), p(150, 90, 0), p(130, 110, 0), p(136, 140, 0), p(166, 150, 0), p(200, 160, 1), p(214, 196, 2), p(196, 230, 3), p(156, 242, 4), p(110, 236, 5), p(80, 212, 5), p(70, 180, 4), p(60, 150, 3), p(40, 120, 2), p(20, 100, 1), p(-10, 90, 0), p(-36, 70, 0), p(-40, 34, 0), p(-30, 0, 0)),
		/** A lozenge: a long leaning parallelogram, two open corners and two sharp ones, the back straight sagging round a waterhole. */
		LOZENGE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(72.6, 0, 0), p(85.3, 0, 0), p(96.3, 2.6, 0.5), p(104.9, 10, 1), p(111.7, 19.3, 1.5), p(118.4, 28.7, 2), p(125.2, 38, 2.5), p(132, 47.3, 3), p(135.5, 57.6, 3.2), p(132.5, 68, 3.5), p(124.2, 75, 3.8), p(113.4, 75.9, 4), p(103, 73.7, 4), p(92.5, 71.5, 4), p(82.1, 69.3, 4), p(69.7, 67.3, 4), p(57.2, 66.7, 4), p(44.6, 67.3, 4), p(32.2, 69.3, 4), p(21.8, 71.5, 4), p(11.3, 73.7, 4), p(0.9, 75.9, 4), p(-12.9, 74.9, 3.5), p(-23.9, 66.4, 3), p(-30.6, 57.1, 2.5), p(-37.4, 47.8, 2), p(-44.1, 38.5, 1.5), p(-50.9, 29.2, 1), p(-54.3, 16.5, 0.7), p(-48.3, 4.7, 0.3), p(-36, 0, 0), p(-30, 0, 0)),
		/** A heater shield, one flank battered in: flat top, a long domed flank to the point, a hollow flank back to a sharp corner. */
		HEATER(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(90, 0, 0), p(106, 8, 0), p(114, 26, 1), p(116, 50, 2), p(110, 76, 3), p(96, 98, 4), p(76, 114, 4), p(54, 124, 3), p(34, 126, 3), p(22, 114, 2), p(14, 96, 2), p(4, 78, 1), p(-10, 62, 1), p(-26, 54, 1), p(-44, 46, 0), p(-62, 34, 0), p(-72, 16, 0), p(-60, 2, 0), p(-30, 0, 0)),
		/** A mitten: a round hand, a thumb poking out the side, down the cuff to the line. */
		MITTEN(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(95, 0, 0), p(116, 10, 1), p(126, 32, 2), p(128, 60, 3), p(120, 88, 4), p(102, 110, 5), p(76, 124, 5), p(46, 126, 4), p(22, 116, 3), p(8, 98, 2), p(-8, 86, 2), p(-28, 84, 2), p(-52, 86, 2), p(-76, 86, 2), p(-96, 78, 2), p(-102, 60, 1), p(-92, 44, 1), p(-74, 38, 1), p(-58, 32, 1), p(-52, 18, 0), p(-46, 4, 0), p(-30, 0, 0)),
		// ---- new B shapes (phase 2) end ----

		// ---- new A shapes (phase 2) begin ----
		/** A toadstool: a bulb foot, a narrow stem climbing to a great domed cap, and down the far rim. */
		TOADSTOOL(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(96, 4, 0), p(118, 24, 1), p(112, 50, 2), p(90, 70, 3), p(84, 100, 4), p(92, 126, 5), p(130, 134, 6), p(176, 136, 6), p(210, 152, 6), p(218, 184, 5), p(196, 218, 4), p(150, 244, 3), p(90, 256, 2), p(30, 252, 1), p(-26, 232, 1), p(-70, 204, 0), p(-90, 172, 0), p(-78, 146, 0), p(-40, 136, 0), p(-4, 132, 0), p(12, 110, 0), p(10, 80, 0), p(-6, 56, 0), p(-34, 36, 0), p(-48, 14, 0), p(-30, 0, 0)),
		/** An ammonite: one long coil winding into a hairpin at its heart and back out alongside itself. */
		AMMONITE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(90, 0, 0), p(130, 0, 0), p(180, 12, 1), p(225, 35, 2), p(286, 110, 4), p(300, 200, 5), p(269, 280, 6), p(205, 330, 6), p(130, 340, 6), p(65, 313, 5), p(26, 260, 4), p(20, 200, 2), p(43, 150, 0), p(68, 126, 0), p(102, 140, 0), p(94, 176, 0), p(80, 204, 0), p(80, 232, 0), p(95, 261, 0), p(130, 280, 0), p(175, 278, 0), p(217, 250, 0), p(240, 200, 0), p(234, 140, 0), p(195, 87, 0), p(165, 70, 0), p(130, 60, 0), p(100, 60, 0), p(70, 60, 0), p(40, 60, 0), p(10, 60, 0), p(-20, 60, 0), p(-67, 30, 0), p(-30, 0, 0)),
		/** An anvil: a broad foot, a pinched waist, a flat face and a long tapering horn. */
		ANVILHORN(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(110, 0, 0), p(150, 0, 0), p(176, 8, 0), p(184, 30, 1), p(166, 46, 2), p(126, 52, 3), p(108, 70, 4), p(106, 100, 5), p(120, 122, 6), p(170, 128, 6), p(214, 134, 6), p(234, 156, 5), p(226, 182, 4), p(190, 194, 3), p(120, 196, 2), p(40, 196, 1), p(-30, 196, 0), p(-90, 190, 0), p(-150, 176, 0), p(-178, 160, 0), p(-160, 142, 0), p(-110, 132, 0), p(-50, 122, 0), p(-6, 110, 0), p(0, 80, 0), p(-10, 56, 0), p(-46, 48, 0), p(-66, 34, 0), p(-66, 12, 0), p(-50, 0, 0), p(-30, 0, 0)),
		/** A cave fish: its flat belly is the start straight, a round nose hairpin, a back sweeping down into a forked tail of two hairpin tips either side of a straight trailing edge. */
		BLINDFISH(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(72, 0, 0), p(84, 0, 0), p(94.7, 2.1, 0), p(103.8, 8.2, 0), p(109.9, 17.3, 1), p(112, 28, 1), p(109.9, 38.7, 1), p(103.8, 47.8, 2), p(94.7, 53.9, 2), p(84, 56, 2), p(73.1, 56, 2), p(62.1, 56, 2), p(51.2, 56, 2), p(40.2, 56, 2), p(30.5, 56.2, 2), p(20.8, 56.8, 2), p(11.2, 57.7, 2), p(1.6, 59, 2), p(-8, 60.7, 2), p(-17.4, 62.7, 2), p(-26.8, 65.2, 2), p(-36.1, 67.9, 2), p(-45.3, 71.1, 2), p(-57, 78.5, 2), p(-62.6, 84.2, 2), p(-71, 88.4, 2), p(-80.2, 86.9, 2), p(-86.7, 80.2, 1), p(-88, 71, 1), p(-86.6, 60.6, 1), p(-85.8, 50.2, 1), p(-85.8, 39.7, 1), p(-86.6, 29.2, 1), p(-88, 18.9, 1), p(-89.8, 8.5, 1), p(-91.7, -1.8, 1), p(-90.3, -11.1, 1), p(-83.8, -17.7, 0), p(-74.6, -19.2, 0), p(-66.3, -15, 0), p(-60.6, -9.4, 0), p(-54, -4.3, 0), p(-46.3, -1.1, 0), p(-38, 0, 0), p(-30, 0, 0)),
		/** A shelf fungus on its trunk: the trunk is the start straight, the cap climbs and rolls over a long crest to its lip, and the gilled underside sweeps back down to the trunk in one long reverse curve. */
		SHELFCAP(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(70, 0, 0), p(80, 0, 0), p(89.9, 2, 0), p(98.4, 7.6, 0), p(104, 16.1, 1), p(106, 26, 1), p(106, 34.5, 2), p(106, 42.9, 2), p(105.1, 53.3, 2), p(102.4, 63.4, 2), p(98, 72.9, 3), p(92, 81.5, 3), p(85, 89.3, 3), p(77.4, 96.5, 3), p(69.4, 103.3, 3), p(60.9, 109.4, 3), p(52.1, 115, 4), p(42.8, 119.9, 4), p(33.2, 124.1, 4), p(23.4, 127.7, 4), p(13.3, 130.6, 4), p(3.1, 132.8, 4), p(-7.4, 134.6, 4), p(-17.9, 136.5, 4), p(-28.4, 138.3, 4), p(-38.9, 140.2, 4), p(-49.4, 142, 4), p(-59.9, 143.9, 4), p(-70, 141.8, 4), p(-75.9, 133.3, 3), p(-74.4, 123.1, 3), p(-69.5, 113.9, 3), p(-65.2, 104.3, 2), p(-61.5, 94.5, 2), p(-58.5, 84.5, 2), p(-56.1, 74.3, 2), p(-54.4, 64, 2), p(-53.3, 53.6, 1), p(-53, 43.1, 1), p(-53, 31.5, 0), p(-53, 20, 0), p(-50.3, 10, 0), p(-43, 2.7, 0), p(-33, 0, 0), p(-30, 0, 0)),
		/** A machete: the edge is the start straight, sweeping up to the point, a flat spine back into a narrower handle, a round pommel, and the step down at the heel onto the edge. */
		MACHETE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(72.5, 0, 0), p(85, 0, 0), p(95.4, 0.8, 0), p(105.6, 3.1, 0), p(115.4, 6.9, 0), p(124.4, 12.2, 1), p(132.6, 18.7, 1), p(139.7, 26.4, 1), p(145.6, 35, 1), p(146.1, 47, 2), p(136.6, 54.3, 2), p(126.8, 56, 2), p(117.1, 57.7, 2), p(107.3, 59.5, 2), p(97.5, 61.2, 2), p(87.7, 62.9, 2), p(78, 64.6, 2), p(68.2, 66.4, 2), p(58.4, 68.1, 3), p(48.6, 69.8, 3), p(38.9, 71.5, 3), p(29.1, 73.3, 3), p(19.3, 75, 3), p(9.6, 76.7, 3), p(-0.2, 78.4, 3), p(-8.9, 79.6, 3), p(-17.6, 80, 3), p(-27.7, 80, 3), p(-37.9, 80, 3), p(-48.1, 80, 3), p(-58.2, 80, 3), p(-68.4, 80, 3), p(-78.5, 80, 3), p(-88.7, 80, 3), p(-98.9, 80, 3), p(-109, 80, 3), p(-119.2, 80, 3), p(-129.3, 80, 3), p(-138.5, 78.1, 3), p(-146.3, 72.9, 2), p(-151.5, 65.1, 2), p(-153.3, 56, 2), p(-153.3, 53, 2), p(-151.5, 43.8, 2), p(-146.3, 36, 2), p(-138.5, 30.8, 1), p(-129.3, 29, 1), p(-120.2, 29, 1), p(-111, 29, 1), p(-101.8, 29, 1), p(-92.7, 29, 1), p(-83.5, 29, 1), p(-74.3, 29, 1), p(-65.6, 27.2, 1), p(-58.3, 22, 0), p(-53.7, 14.5, 0), p(-49, 6.9, 0), p(-41.7, 1.8, 0), p(-33, 0, 0), p(-30, 0, 0)),
		// ---- new A shapes (phase 2) end ----

		// ---- new S shapes (phase 2) begin ----
		/** A nautilus shell: one coil spiralling down into a hairpin at the heart and back out between its own turns. */
		NAUTILUS(p(0, 0, 8), p(30, 0, 8), p(60, 0, 8), p(90, 0, 8), p(120, 0, 8), p(211, 33, 8), p(270, 103, 7), p(285, 190, 6), p(256, 268, 5), p(194, 318, 4), p(120, 330, 3), p(54, 304, 2), p(13, 252, 1), p(5, 190, 0), p(28, 137, 0), p(61, 109, 0), p(106, 116, 0), p(91, 150, 0), p(67, 166, 0), p(54, 197, 1), p(59, 234, 2), p(86, 266, 3), p(130, 281, 4), p(179, 271, 5), p(219, 234, 6), p(236, 178, 7), p(221, 117, 8), p(174, 68, 8), p(120, 50, 8), p(40, 50, 8), p(0, 50, 8), p(-30, 50, 8), p(-55, 25, 8), p(-30, 0, 8)),
		/** A comet: one huge round head and a long tail narrowing to a hairpin at its tip. */
		COMET(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(110, 0, 0), p(170, 0, 1), p(230, 0, 2), p(300, 6, 4), p(355, 35, 6), p(392, 90, 8), p(390, 150, 9), p(355, 195, 9), p(300, 214, 8), p(240, 204, 7), p(170, 176, 5), p(100, 142, 3), p(30, 104, 1), p(-30, 70, 0), p(-70, 40, 0), p(-72, 12, 0), p(-30, 0, 0)),
		/** A star fort: five arrowhead bastions jutting from straight curtain walls. */
		STARFORT(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(107, 0, 0), p(142, -14, 1), p(177, -28, 2), p(175, 10, 2), p(172, 48, 2), p(184, 82, 3), p(195, 117, 3), p(206, 152, 4), p(218, 187, 4), p(242, 216, 5), p(266, 246, 5), p(230, 255, 5), p(193, 264, 5), p(163, 286, 5), p(133, 307, 6), p(104, 329, 6), p(74, 351, 6), p(54, 383, 7), p(33, 415, 7), p(13, 383, 7), p(-7, 351, 6), p(-37, 329, 6), p(-66, 307, 5), p(-96, 286, 5), p(-126, 264, 4), p(-163, 255, 4), p(-200, 246, 4), p(-175, 216, 3), p(-151, 187, 3), p(-139, 152, 2), p(-128, 117, 2), p(-117, 82, 1), p(-105, 48, 1), p(-108, 10, 1), p(-111, -28, 1), p(-75, -14, 0), p(-30, 0, 0)),
		/** A ringed planet: the ring along the start line, a sharp tip at each end, and the planet swelling up out of it between two sweeping shoulders. */
		SATURN(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(71.7, 0, 0), p(83.3, 0, 0), p(95, 0, 0), p(106.7, 0, 0), p(118.3, 0, 0), p(130, 0, 0), p(142.4, 4.2, 0), p(149.7, 15.1, 0), p(148.8, 28.2, 0), p(140.2, 38, 0), p(129.3, 44.3, 0), p(118.5, 50.5, 0), p(107.6, 56.8, 0), p(96.9, 64.3, 0), p(87.7, 73.5, 0), p(80.2, 84.3, 0), p(74.6, 96.1, 0), p(68.5, 105.7, 0.8), p(59, 111.8, 1.7), p(47.7, 113.3, 2.5), p(36.9, 109.9, 3.3), p(28.5, 102.2, 4.2), p(24.2, 91.7, 5), p(20.5, 80.7, 3.3), p(13.9, 71.1, 1.7), p(4.7, 63.9, 0), p(-6.5, 57.4, 0), p(-17.7, 51, 0), p(-29, 44.5, 0), p(-40.2, 38, 0), p(-48.8, 28.2, 0), p(-49.7, 15.1, 0), p(-42.4, 4.2, 0), p(-30, 0, 0)),
		/** A fat crescent moon: the outer arc along the line and up to two curled horns, the hollow of the moon dipping deep between them. */
		CRESCENT_MOON(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(70, 0, 0), p(81.9, 2.6, 0), p(91.6, 10.1, 0), p(98.1, 17.8, 0), p(104.6, 25.5, 0), p(111.1, 33.3, 0), p(116.5, 43.3, 1), p(117.5, 54.7, 2), p(113.9, 65.5, 3), p(106.5, 72.9, 4), p(96.4, 75.6, 5), p(86.3, 72.9, 2.5), p(78.9, 65.5, 0), p(69.9, 54.8, 0), p(57.8, 47.8, 0), p(45.5, 44.2, 0), p(32.9, 41.9, 0), p(20.2, 41.2, 0), p(7.4, 41.9, 0), p(-5.2, 44.2, 0), p(-17.5, 47.8, 0), p(-29.6, 54.8, 0), p(-38.6, 65.5, 0), p(-49.2, 74.5, 0), p(-63.2, 74.5, 0), p(-73.9, 65.5, 0), p(-77.5, 54.7, 0), p(-76.5, 43.3, 0), p(-71.1, 33.3, 0), p(-64.6, 25.5, 0), p(-58.1, 17.8, 0), p(-51.6, 10.1, 0), p(-41.9, 2.6, 0), p(-30, 0, 0)),
		/** A split block: the line along the foot, a climb up the far wall to the lip, and a jagged fissure falling in esses down to its flooded bottom and back out. */
		FISSURE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(75, 0, 0), p(90, 0, 0), p(102.6, 3.4, 0.7), p(111.9, 12.6, 1.3), p(115.3, 25.3, 2), p(115.3, 36.7, 2), p(115.3, 48.1, 2), p(115.3, 59.5, 2), p(115.3, 70.9, 2), p(115.3, 82.3, 2), p(115.3, 93.7, 2), p(115.3, 105.1, 2), p(115.3, 116.5, 2), p(115.3, 127.9, 2), p(115.3, 139.3, 2), p(112.5, 150.8, 2.4), p(104.9, 159.7, 2.8), p(94, 164.2, 3.2), p(82.3, 163.3, 3.6), p(72.2, 157.2, 4), p(67.1, 152, 2), p(63.2, 147.2, 1), p(60.5, 141.8, 0), p(54.7, 130, -0.5), p(46.5, 119.8, -1), p(36.3, 111.6, -1.5), p(24.6, 105.8, -2), p(14.3, 99.3, -3), p(7.7, 89, -4), p(1.9, 77.2, -4), p(-6.3, 67, -4), p(-16.5, 58.8, -4), p(-28.2, 53, -4), p(-37.6, 49.6, 0), p(-43.1, 46.9, 0), p(-47.8, 43.1, 0), p(-54, 33, 0), p(-54.9, 21.3, 0), p(-50.4, 10.4, 0), p(-41.5, 2.8, 0), p(-30, 0, 0)),
		// ---- new S shapes (phase 2) end ----
		;

		final List<TrackSpline.Ctl> points;
		final double unitLength;

		Shape(TrackSpline.Ctl... points) {
			this.points = List.of(points);
			this.unitLength = new TrackSpline(this.points, 1.0, List.of()).length();
		}
	}

	private static TrackSpline.Ctl p(double x, double z, double y) {
		return new TrackSpline.Ctl(x, z, y);
	}

	private static Feature boost(double start) {
		return new Feature(Feature.Type.BOOST, start, start + 0.012);
	}

	private static Feature water(double start, double end) {
		return new Feature(Feature.Type.WATER, start, end);
	}

	private static Feature ridge(double start, double end) {
		return new Feature(Feature.Type.RIDGE, start, end);
	}

	private static Feature lava(double start, double end) {
		return new Feature(Feature.Type.LAVA, start, end);
	}

	private static Feature mud(double start, double end) {
		return new Feature(Feature.Type.MUD, start, end);
	}

	/** Standing level of the lowest road; hills rise from here. */
	public static final double TRACK_Y = 65.0D;
	public static final double PADDOCK_X = 0.5D;
	public static final double PADDOCK_Y = 65.0D;
	public static final double PADDOCK_Z = -51.5D;
	public static final double STALL_SPACING = 1.8D;
	public static final double STALL_HALF_WIDTH = 5.0D;
	/** Road band either side of the line; features replace this band, detours sit outside it. */
	public static final double ROAD_HALF = 5.5D;
	public static final double DETOUR_INNER = 9.0D;
	public static final double DETOUR_OUTER = 16.0D;
	/** Shared by the built openings, detour cost model, and AI steering. */
	public static final double DETOUR_CONNECT = 0.012D;
	/**
	 * Course islands: one row per class (z), one column per course (x); the village sits
	 * near the origin. Courses 0-5 keep the columns they were built in (x -2050 .. +2050);
	 * course 6 and up alternate west and east of them ({@link #columnOf}), so a class's
	 * row grows outward from the middle and every future index already has a place.
	 */
	private static final double ROW_Z0 = 700.0D;
	private static final double ROW_DZ = 900.0D;
	private static final double COL_DX = 820.0D;
	/**
	 * Largest island half extent ({@link #getRadiusX} / {@link #getRadiusZ}) the grid is
	 * spaced for: two neighbours of this size still keep 40 blocks of void between them
	 * (column step 820, row step 900). {@code RaceTrackTest} holds every course to it.
	 */
	/** Blocks before a ridge's start (and past its end) a climber may already climb, measured on the centre line. */
	public static final double RIDGE_BAND_PAD = 4.0D;
	/**
	 * Half width of the climb band across a ridge ({@link #ridgeBandAt}): every lane in which a
	 * bird's body can touch the ridge. The ridge is laid on the band's columns, and on a diagonal
	 * leg a column's centre reaches 6.2 blocks out (the column's corner 6.9); a bird is 0.875 wide
	 * either side of its centre. At ROAD_HALF (5.5) a climber pressed on the ridge's outer corner
	 * or flank from 5.6 out could not climb it and sat wedged in a notch of the staircase ("Serah",
	 * S_SKYWAY; the Green harness bot on B_KOPJE).
	 */
	public static final double RIDGE_BAND_HALF = ROAD_HALF + 2.25D;
	public static final double MAX_ISLAND_RADIUS = 385.0D;
	/** The most courses a class can hold on its row today (six sprints and six grands prix). */
	public static final int MAX_COURSES_PER_CLASS = 12;

	private final RaceClass raceClass;
	private final int course;
	private final Theme theme;
	private final Shape shape;
	private final int laps;
	private final List<Feature> features;
	/** Terrain features only, in table order. */
	private final List<Feature> terrain;
	/** {@link #detourSpans} per colour and bog sense, worked out on first use (the spans never change). */
	@SuppressWarnings("unchecked")
	private final List<double[]>[] spanCache = new List[ChocoboColor.values().length * 2];
	private final TrackSpline spline;
	private final double offsetX, offsetZ;

	RaceTrack(RaceClass raceClass, int course, Theme theme, Shape shape, double lapTarget, int laps, Feature... features) {
		this.raceClass = raceClass;
		this.course = course;
		this.theme = theme;
		this.shape = shape;
		this.laps = laps;
		this.features = List.of(features);
		List<Feature> terrainOnly = new ArrayList<>();
		for (Feature f : features) {
			if (f.terrain()) {
				terrainOnly.add(f);
			}
		}
		this.terrain = List.copyOf(terrainOnly);
		List<double[]> flat = new ArrayList<>();
		for (Feature f : features) {
			if (f.terrain()) {
				flat.add(new double[]{f.start(), f.end()});
			}
		}
		this.spline = new TrackSpline(shape.points, lapTarget / shape.unitLength, flat);
		this.offsetX = centerX() - (spline.minX() + spline.maxX()) / 2.0D;
		this.offsetZ = centerZ() - (spline.minZ() + spline.maxZ()) / 2.0D;
	}

	/** Course {@code course} of the class (clamped to the courses it has). */
	public static RaceTrack forClass(RaceClass raceClass, int course) {
		List<RaceTrack> all = ofClass(raceClass);
		if (all.isEmpty()) {
			return C_MEADOW;
		}
		return all.get(Math.max(0, Math.min(all.size() - 1, course)));
	}

	/** Every course of the class in table order (= course index order). */
	public static List<RaceTrack> ofClass(RaceClass raceClass) {
		List<RaceTrack> out = new ArrayList<>();
		for (RaceTrack t : values()) {
			if (t.raceClass == raceClass) {
				out.add(t);
			}
		}
		return out;
	}

	/** How many courses the class has: however many rows of the table carry it. */
	public static int courseCount(RaceClass raceClass) {
		return ofClass(raceClass).size();
	}

	/** The class's sprints (one lap), in table order. */
	public static List<RaceTrack> sprintsOf(RaceClass raceClass) {
		List<RaceTrack> out = new ArrayList<>();
		for (RaceTrack t : ofClass(raceClass)) {
			if (t.isSprint()) {
				out.add(t);
			}
		}
		return out;
	}

	/** The class's grands prix (three laps or more), in table order. */
	public static List<RaceTrack> grandsPrixOf(RaceClass raceClass) {
		List<RaceTrack> out = new ArrayList<>();
		for (RaceTrack t : ofClass(raceClass)) {
			if (t.isGrandPrix()) {
				out.add(t);
			}
		}
		return out;
	}

	/**
	 * Grid column of course index {@code course}: 0-5 are the original columns, then 6 goes
	 * west of column 0, 7 east of column 5, 8 west again and so on.
	 */
	static int columnOf(int course) {
		if (course < 6) {
			return course;
		}
		int k = course - 6;
		return (k % 2 == 0) ? -1 - k / 2 : 6 + k / 2;
	}

	/** Island centre x of course index {@code course} in any class. */
	static double slotX(int course) {
		return 0.5D + (columnOf(course) - 2.5D) * COL_DX;
	}

	/** Island centre z of the class row. */
	static double slotZ(RaceClass raceClass) {
		return 0.5D + ROW_Z0 + raceClass.getId() * ROW_DZ;
	}

	/** values() clones 48 entries on every call; byId runs every tick for every racer. */
	private static final RaceTrack[] VALUES = values();

	public static RaceTrack byId(int id) {
		RaceTrack[] values = VALUES;
		if (id < 0 || id >= values.length) {
			return C_MEADOW;
		}
		return values[id];
	}

	public RaceClass getRaceClass() {
		return raceClass;
	}

	public int getCourse() {
		return course;
	}

	/** One lap: a sprint (4 points, the base purse). Everything sprint-or-GP keys off the lap count. */
	public boolean isSprint() {
		return laps <= 1;
	}

	/** Three to five laps: a grand prix (points and purse by the heat's length, {@link #winPoints()}). */
	public boolean isGrandPrix() {
		return laps > 1;
	}

	public int getLaps() {
		return laps;
	}

	/** Blocks for the whole heat: lap length x laps. */
	public double raceLength() {
		return lapLength() * laps;
	}

	/** Points for a ranked win here ({@link RaceScoring#winPoints}): 4 a sprint, a grand prix by its length. */
	public int winPoints() {
		return RaceScoring.winPoints(this);
	}

	public Theme theme() {
		return theme;
	}

	public Shape shape() {
		return shape;
	}

	/** Half extent of the island in x (road and margins included). */
	public double getRadiusX() {
		return (spline.maxX() - spline.minX()) / 2.0D + DETOUR_OUTER + 4.0D;
	}

	public double getRadiusZ() {
		return (spline.maxZ() - spline.minZ()) / 2.0D + DETOUR_OUTER + 4.0D;
	}

	public List<Feature> features() {
		return features;
	}

	/** Terrain features only (the ones with a detour). */
	public List<Feature> terrainFeatures() {
		return terrain;
	}

	public double centerX() {
		return slotX(course);
	}

	public double centerZ() {
		return slotZ(raceClass);
	}

	/**
	 * Extra blocks a bird that takes the detour travels over one that goes straight
	 * through {@code f}, the swing out and back included. This is what a colour's
	 * ability is worth on this course, so features are placed to keep it even: see
	 * {@link #detourTarget()}.
	 */
	public double detourCost(Feature f) {
		double connect = DETOUR_CONNECT, lane = -(DETOUR_INNER + DETOUR_OUTER) / 2.0D;
		double from = f.start() - connect, to = f.end() + connect;
		int n = 400;
		double direct = 0.0D, detour = 0.0D;
		RacePoint pc = null, pd = null;
		for (int i = 0; i <= n; i++) {
			double p = from + (to - from) * i / (double) n;
			double o = p < f.start() ? lane * (p - from) / connect
					: p > f.end() ? lane * (to - p) / connect : lane;
			RacePoint c = pointAt(p);
			RacePoint d = pointAtLane(p, o);
			if (pc != null) {
				direct += Math.hypot(c.x() - pc.x(), c.z() - pc.z());
				detour += Math.hypot(d.x() - pd.x(), d.z() - pd.z());
			}
			pc = c;
			pd = d;
		}
		return detour - direct;
	}

	/**
	 * What a colour feature's detour should cost here: 2 % of a lap, floored at 13
	 * blocks so a short course still rewards the ability and capped at 28 so a long
	 * one does not decide the race on colour alone. A bog aims at 45 % of this — nobody
	 * suits a bog, so its detour is everyone's smart route and should be easy to take.
	 */
	public double detourTarget() {
		return Math.max(13.0D, Math.min(28.0D, 0.02D * lapLength()));
	}

	/** Blocks per lap along the centre line. */
	public double lapLength() {
		return spline.length();
	}

	/** Standing level of the road at t (the surface block is one below). */
	public double groundY(double t) {
		return TRACK_Y + Math.floor(spline.at(t)[2]);
	}

	public RacePoint pointAt(double t) {
		double[] p = spline.at(t);
		return new RacePoint(p[0] + offsetX, TRACK_Y + Math.floor(p[2]), p[1] + offsetZ);
	}

	public double progressAt(double x, double z) {
		return spline.nearest(x - offsetX, z - offsetZ);
	}

	/**
	 * {@link #progressAt} starting near {@code hint} (last progress, 0..1).
	 * A hint below 0 scans the whole lap.
	 */
	public double progressAt(double x, double z, double hint) {
		return spline.nearestFrom(x - offsetX, z - offsetZ, hint);
	}

	/** {@link #progressAt} between samples: for timing a line crossing inside a tick. */
	public double progressFineAt(double x, double z) {
		return spline.nearestFine(x - offsetX, z - offsetZ);
	}

	/** Fine progress beside {@code coarse}, which {@link #progressAt} already resolved. */
	public double progressFineAt(double x, double z, double coarse) {
		return spline.nearestFineFrom(x - offsetX, z - offsetZ, coarse);
	}

	public static double stallOffset(int stall, int total) {
		return (stall - (total - 1) / 2.0D) * STALL_SPACING;
	}

	public RacePoint stallPos(int stall, int total) {
		return pointAtLane(0.02D, stallOffset(stall, total));
	}

	/** Point at lane {@code offset} blocks from the centre line: positive = inside the loop, negative = outside. */
	public RacePoint pointAtLane(double t, double offset) {
		double[] p = spline.atLane(t, offset);
		return new RacePoint(p[0] + offsetX, TRACK_Y + Math.floor(p[2]), p[1] + offsetZ);
	}

	/** Direction of travel at t as a unit (x, z). */
	public double[] tangent(double t) {
		return spline.tangent(t);
	}

	/** Heading change in radians over the next {@code span} blocks; ~0 on a straight. */
	public double turnAhead(double t, double span) {
		return spline.turn(t, span);
	}

	/** Signed heading change: positive bends toward the inside. */
	public double signedTurnAhead(double t, double span) {
		return spline.signedTurn(t, span);
	}

	/** A straight: little heading change over the next 40 blocks. */
	public boolean isStraight(double t) {
		return spline.turn(t, 40.0D) < 0.25D;
	}

	TrackSpline spline() {
		return spline;
	}

	public RacePoint lookTarget() {
		return pointAt(0.08D);
	}

	public Feature featureAt(double t) {
		double w = t - Math.floor(t);
		for (Feature ft : features) {
			if (ft.covers(w)) {
				return ft;
			}
		}
		return null;
	}

	/** The terrain feature covering t, ignoring boost strips. */
	public Feature terrainAt(double t) {
		Feature f = featureAt(t);
		return f != null && f.terrain() ? f : null;
	}

	/** A detour connector's length along the lap, as a share of the lap. */
	public double detourConnect() {
		return DETOUR_CONNECT;
	}

	/** Lane at a steering target: enter and leave only through the actual detour connectors. */
	public double detourLaneAt(double t, double directLane, ChocoboColor color, boolean knowsBog) {
		double progress = t - Math.floor(t);
		for (Feature feature : features) {
			if (!feature.terrain() || feature.suits(color)
					|| (feature.type() == Feature.Type.MUD && !knowsBog)) continue;
			double from = feature.start() - DETOUR_CONNECT;
			double to = feature.end() + DETOUR_CONNECT;
			if (progress < from || progress > to) continue;
			double blend = progress < feature.start() ? (progress - from) / DETOUR_CONNECT
					: progress > feature.end() ? (to - progress) / DETOUR_CONNECT : 1.0D;
			return directLane + (-(DETOUR_INNER + DETOUR_OUTER) / 2.0D - directLane) * blend;
		}
		return directLane;
	}

	/**
	 * Where a set-back puts a bird of {@code color} down at {@code t}: its line there (the detour
	 * round a feature it does not suit, else the middle of the road), standing on what the course
	 * laid: on top of a ridge it climbs. {@code pointAtLane} is the road's own level, which on a
	 * ridge is inside it.
	 */
	public RacePoint setBackPoint(double t, ChocoboColor color) {
		double lane = detourLaneAt(t, 0.0D, color, true);
		RacePoint at = pointAtLane(t, lane);
		Feature ft = terrainAt(t);
		if (ft != null && ft.type() == Feature.Type.RIDGE && Math.abs(lane) <= ROAD_HALF) {
			return new RacePoint(at.x(), at.y() + ridgeHeight(), at.z());
		}
		return at;
	}

	/** Blocks before a detour's connector a bird that goes round starts easing out to the band's outside lane. */
	public static final double DETOUR_PREP = 14.0D;
	/** Blocks after the rejoin a bird holds the outside lane and eases back to its line. */
	public static final double DETOUR_SETTLE = 10.0D;
	/**
	 * Lane a racer holds on a detour road: a body and a margin in from its inner edge. The
	 * detour's middle (-12.5) asked for a 13.5-block swing through a connector 4-7 blocks long
	 * on a short lap; from the band's outside lane this is a 5.75-block one.
	 */
	public static final double DETOUR_HOLD = -(DETOUR_INNER + 1.25D);
	/** The band's outside lane, where a bird waits for a detour's opening: clear of the outside rail. */
	public static final double DETOUR_WAIT = -(ROAD_HALF - 1.0D);

	/**
	 * Lane a racer steers for at {@code t} ({@code directLane} when no detour is near): the
	 * racing line of the detours this colour takes. On the band before the opening it eases to
	 * the outside lane ({@link #DETOUR_WAIT}, the rail is still up), crosses the connector to
	 * {@link #DETOUR_HOLD}, holds it past the feature, crosses back to the outside lane at the
	 * rejoin and only then eases back to its line: it never aims through the rail that stands
	 * either side of the opening. Two detours close together keep it outside between them.
	 * The connectors and the road are the built ones ({@link #detourLaneAt} is their centre line).
	 */
	public double steerLaneAt(double t, double directLane, ChocoboColor color, boolean knowsBog) {
		double lap = lapLength(), c = DETOUR_CONNECT * lap;
		double out = directLane;
		for (double[] span : detourSpans(color, knowsBog)) {
			double len = (span[1] - span[0]) * lap;
			double x = wrapHalf(t - span[0]) * lap;   // blocks past the detour's start
			double lane;
			if (x < -(c + DETOUR_PREP) || x > len + c + DETOUR_SETTLE) {
				continue;
			} else if (x < -c) {
				lane = directLane + (DETOUR_WAIT - directLane) * smooth((x + c + DETOUR_PREP) / DETOUR_PREP);
			} else if (x < 0.0D) {
				lane = DETOUR_WAIT + (DETOUR_HOLD - DETOUR_WAIT) * ((x + c) / c);
			} else if (x <= len) {
				lane = DETOUR_HOLD;
			} else if (x <= len + c) {
				lane = DETOUR_HOLD + (DETOUR_WAIT - DETOUR_HOLD) * ((x - len) / c);
			} else {
				lane = DETOUR_WAIT + (directLane - DETOUR_WAIT) * smooth((x - len - c) / DETOUR_SETTLE);
			}
			out = Math.min(out, lane);
		}
		return out;
	}

	/**
	 * The detours a bird of this colour takes, as {start, end} lap spans in lap order. Two whose
	 * openings are laid as one ({@link #DETOUR_MERGE}) are one span: the bird stays out on the
	 * detour road between them instead of weaving back to the band.
	 */
	private List<double[]> detourSpans(ChocoboColor color, boolean knowsBog) {
		int key = color.ordinal() * 2 + (knowsBog ? 1 : 0);
		List<double[]> cached = spanCache[key];
		if (cached != null) {
			return cached;
		}
		List<Feature> taken = new ArrayList<>();
		for (Feature f : terrain) {
			if (takesDetour(f, color, knowsBog)) {
				taken.add(f);
			}
		}
		taken.sort(java.util.Comparator.comparingDouble(Feature::start));
		List<double[]> spans = new ArrayList<>();
		double lap = lapLength();
		for (Feature f : taken) {
			double[] last = spans.isEmpty() ? null : spans.get(spans.size() - 1);
			if (last != null && (f.start() - last[1] - 2.0D * DETOUR_CONNECT) * lap < DETOUR_MERGE) {
				last[1] = Math.max(last[1], f.end());
			} else {
				spans.add(new double[]{f.start(), f.end()});
			}
		}
		List<double[]> frozen = List.copyOf(spans);
		spanCache[key] = frozen;
		return frozen;
	}

	/** Whether a bird of this colour goes round {@code f} (a bog only if it knows one when it sees it). */
	public static boolean takesDetour(Feature f, ChocoboColor color, boolean knowsBog) {
		return f.terrain() && !f.suits(color) && (f.type() != Feature.Type.MUD || knowsBog);
	}

	/**
	 * Blocks of a detour connector this colour is on or about to cross at {@code t}: the
	 * connector's length if {@code t} is within {@code lead} blocks before it or on it (either
	 * end of a detour it takes), else NaN. {@link RacerLine#connectorPace} slows for it.
	 */
	public double connectorAhead(double t, ChocoboColor color, boolean knowsBog, double lead) {
		double lap = lapLength(), c = DETOUR_CONNECT * lap;
		for (double[] span : detourSpans(color, knowsBog)) {
			double x = wrapHalf(t - span[0]) * lap;
			// the way in only: the feature's face or rim stands at the end of that swing. The way
			// out runs into the rejoin's flare (REJOIN_FLARE), open road, so it is taken at pace.
			if (x >= -(c + lead) && x <= 0.5D) {
				return c;
			}
		}
		return Double.NaN;
	}

	/**
	 * Whether lane {@code lane} at {@code t} is open road on a detour or its connectors: inside a
	 * feature's opening (connector to connector), from the band's outside kerb out to the detour's
	 * outer edge, or inside a rejoin's flare ({@link #flareLane}). Elsewhere the band's rail stands
	 * at the kerb.
	 */
	public boolean inDetourOpening(double t, double lane) {
		if (lane > -ROAD_HALF) {
			return false;
		}
		if (inOpening(t)) {
			return lane >= -DETOUR_OUTER;
		}
		double flare = flareLane(t);
		return !Double.isNaN(flare) && lane >= flare + 1.0D;
	}

	/**
	 * Blocks past a detour opening's rejoin over which the band's outside rail comes back in: it
	 * leaves the detour's outer rail at the end of the opening and meets the kerb this far on,
	 * with road laid inside it. A bird carried wide out of a short connector (a harness bot at
	 * A_MACHETE's bog rejoin, lane -7.1 at 30 degrees to the road; "Vincent" at A_CRYSTAL) used to
	 * land outside a rail that started square at the kerb, and ran along its far side into a post.
	 * Now the rail it meets is the flare, from inside, and it slides along it back onto the band.
	 * Under {@link #DETOUR_MERGE}, so the flare always ends before the next opening.
	 */
	public static final double REJOIN_FLARE = 8.0D;

	/**
	 * The lane of the band's outside rail at {@code t} inside a rejoin's flare: from the detour's
	 * outer rail ({@code -(DETOUR_OUTER + 1)}) at the end of the opening to the kerb
	 * ({@code -(ROAD_HALF + 1)}) {@link #REJOIN_FLARE} blocks on. NaN outside every flare (and in
	 * an opening).
	 */
	public double flareLane(double t) {
		if (terrain.isEmpty() || inOpening(t)) {
			return Double.NaN;
		}
		double lap = lapLength();
		for (double end : openingEnds()) {
			double d = wrapHalf(t - end) * lap;
			if (d > 0.0D && d <= REJOIN_FLARE) {
				double u = d / REJOIN_FLARE;
				double far = DETOUR_OUTER + 1.0D, kerb = ROAD_HALF + 1.0D;
				return -(far + (kerb - far) * u);
			}
		}
		return Double.NaN;
	}

	/** Progress at which each detour opening ends (a group laid as one ends once), worked out on first use. */
	private double[] openingEnds;

	private double[] openingEnds() {
		double[] ends = openingEnds;
		if (ends == null) {
			List<Double> out = new ArrayList<>();
			double lap = lapLength();
			for (Feature f : terrain) {
				double to = f.end() + DETOUR_CONNECT;
				if (!inOpening(to + 0.25D / lap)) {
					out.add(to - Math.floor(to));
				}
			}
			ends = new double[out.size()];
			for (int i = 0; i < ends.length; i++) {
				ends[i] = out.get(i);
			}
			openingEnds = ends;
		}
		return ends;
	}

	/**
	 * Blocks of band between two detour openings below which the two are laid as one: the
	 * detour road runs on through the gap and the band's outside rail stays down. A rail
	 * stub a block or two long between two connectors (A_CRYSTAL's bog and pool, 1.2 blocks;
	 * S_ECLIPSE's bog and ridge, 0.5) stood square in the line of a bird crossing from one
	 * detour to the next.
	 */
	public static final double DETOUR_MERGE = 10.0D;

	/**
	 * Whether {@code t} is in a detour opening: a terrain feature with its connectors
	 * ({@link #DETOUR_CONNECT} either side), or a gap of under {@link #DETOUR_MERGE} blocks
	 * between two of them. There the detour road is laid and the band's outside rail is not.
	 */
	public boolean inOpening(double t) {
		double w = t - Math.floor(t), lap = lapLength();
		List<Feature> terrain = terrainFeatures();
		for (Feature f : terrain) {
			double from = f.start() - DETOUR_CONNECT, to = f.end() + DETOUR_CONNECT;
			if (w >= from && w <= to) {
				return true;
			}
			if (w > to) {
				for (Feature g : terrain) {
					double next = g.start() - DETOUR_CONNECT;
					if (g != f && next >= to && w <= next && (next - to) * lap < DETOUR_MERGE) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * A bird at {@code lane} outside the band's outside rail at {@code t}, just past a detour's
	 * rejoin (carried wide out of the connector): the progress to steer back to, a block and a
	 * half inside the opening it came out of, within {@code maxBack} blocks. NaN when it is on
	 * the band, in an opening, or nowhere near one.
	 */
	public double openingBehind(double t, double lane, double maxBack) {
		if (lane > -(ROAD_HALF + 0.75D) || lane < -DETOUR_OUTER - 1.0D || inDetourOpening(t, lane)) {
			return Double.NaN;
		}
		double lap = lapLength();
		for (double b = 0.5D; b <= maxBack; b += 0.5D) {
			double tb = t - b / lap;
			if (inDetourOpening(tb, -(ROAD_HALF + 1.0D))) {
				return tb - 1.5D / lap;
			}
		}
		return Double.NaN;
	}

	private static double wrapHalf(double d) {
		return d - Math.floor(d + 0.5D);
	}

	private static double smooth(double u) {
		u = Math.max(0.0D, Math.min(1.0D, u));
		return u * u * (3.0D - 2.0D * u);
	}

	/**
	 * On a ridge feature's band: within the road half-width of the line, from a few blocks
	 * before the ridge face to a few past its far end. A climber meets the face with its nose,
	 * not its centre, and on a diagonal leg the face reaches the outer lanes before the centre
	 * line's start (B_CANYON lane -4: 2.3 blocks; {@code RaceClimbTest.everyRidgeFaceIsClimbableFromEveryLane}).
	 * The only place a climber climbs during a race ({@link RaceScoring#mayClimb}). Across the
	 * road it reaches {@link #RIDGE_BAND_HALF}: a bird pressed on the ridge's corner or flank
	 * climbs it wherever its body touches it.
	 */
	public boolean ridgeBandAt(double t, double lane) {
		if (Math.abs(lane) > RIDGE_BAND_HALF) {
			return false;
		}
		double w = t - Math.floor(t), pad = RIDGE_BAND_PAD / lapLength();
		for (Feature f : features) {
			if (f.type() == Feature.Type.RIDGE && w >= f.start() - pad && w <= f.end() + pad) {
				return true;
			}
		}
		return false;
	}

	/** Signed lane of (x, z) at progress t: positive = inside the loop, as {@link #pointAtLane}. */
	public double laneAt(double t, double x, double z) {
		RacePoint c = pointAt(t);
		double[] tg = tangent(t);
		return (x - c.x()) * -tg[1] + (z - c.z()) * tg[0];
	}

	/** A ridge across the direct line here (climbers' shortcut). */
	public boolean isSpaceSection(double t) {
		Feature ft = featureAt(t);
		return ft != null && ft.type() == Feature.Type.RIDGE;
	}

	/** Water across the direct line here (river birds' shortcut). */
	public boolean isWaterSection(double t) {
		Feature ft = featureAt(t);
		return ft != null && ft.type() == Feature.Type.WATER;
	}

	/** Ridge height in blocks: taller up the classes. */
	public int ridgeHeight() {
		return switch (raceClass) {
			case C, B -> 3;
			case A -> 4;
			case S -> 5;
		};
	}

	public float facingYaw() {
		RacePoint from = pointAt(0.02D);
		RacePoint to = lookTarget();
		return (float) (Math.atan2(to.z() - from.z(), to.x() - from.x()) * (180.0D / Math.PI)) - 90.0F;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}
}
