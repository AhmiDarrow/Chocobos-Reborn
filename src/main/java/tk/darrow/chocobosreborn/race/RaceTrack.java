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
	C_MEADOW(RaceClass.C, 0, Theme.MEADOW, Shape.STADIUM, 600, 5, boost(0.10), boost(0.56), boost(0.93)),
	C_ORCHARD(RaceClass.C, 1, Theme.ORCHARD, Shape.ZIGZAG, 660, 4, boost(0.18), boost(0.36), boost(0.93)),
	C_SHORE(RaceClass.C, 2, Theme.SHORE, Shape.PEANUT, 760, 3, boost(0.19), boost(0.80), boost(0.93)),
	C_DOWNS(RaceClass.C, 3, Theme.MEADOW, Shape.ROVAL, 1150, 1, boost(0.11), boost(0.47), boost(0.58), boost(0.66), boost(0.93)),
	C_CIDER(RaceClass.C, 4, Theme.ORCHARD, Shape.CLOUD, 1170, 1, boost(0.42), boost(0.59), boost(0.70), boost(0.85), boost(0.93)),
	C_LAGOON(RaceClass.C, 5, Theme.SHORE, Shape.WAVE, 1190, 1, boost(0.10), boost(0.22), boost(0.46), boost(0.81), boost(0.92)),
	// ---- B: canyon, river, snow
	B_CANYON(RaceClass.B, 0, Theme.CANYON, Shape.DELTA, 650, 5, ridge(0.50, 0.54), boost(0.31), boost(0.71), boost(0.93)),
	B_FORD(RaceClass.B, 1, Theme.RIVER, Shape.LOLLIPOP, 715, 4, water(0.59, 0.64), boost(0.14), boost(0.68), boost(0.93)),
	B_FROST(RaceClass.B, 2, Theme.SNOW, Shape.KIDNEY, 825, 3, ridge(0.55, 0.59), boost(0.41), boost(0.77), boost(0.92)),
	B_MESA(RaceClass.B, 3, Theme.CANYON, Shape.SERPENT, 1280, 1, ridge(0.44, 0.48), boost(0.20), boost(0.35), boost(0.53), boost(0.83), boost(0.91)),
	B_RAPIDS(RaceClass.B, 4, Theme.RIVER, Shape.HAIRPIN, 1300, 1, water(0.51, 0.56), boost(0.22), boost(0.39), boost(0.60), boost(0.75), boost(0.92)),
	B_GLACIER(RaceClass.B, 5, Theme.SNOW, Shape.STAIRS, 1320, 1, water(0.33, 0.38), boost(0.45), boost(0.58), boost(0.66), boost(0.78), boost(0.87)),
	// ---- A: cavern, jungle, nether
	A_CRYSTAL(RaceClass.A, 0, Theme.CAVERN, Shape.DEE, 700, 5, ridge(0.12, 0.16), water(0.51, 0.56), mud(0.80, 0.83), boost(0.46), boost(0.72), boost(0.93)),
	A_CANOPY(RaceClass.A, 1, Theme.JUNGLE, Shape.ELBOW, 770, 4, water(0.34, 0.39), ridge(0.46, 0.50), mud(0.78, 0.81), boost(0.10), boost(0.68), boost(0.92)),
	A_EMBER(RaceClass.A, 2, Theme.NETHER, Shape.TRIDENT, 890, 3, lava(0.27, 0.32), ridge(0.54, 0.58), mud(0.84, 0.87), boost(0.22), boost(0.73), boost(0.93)),
	A_DEEPS(RaceClass.A, 3, Theme.CAVERN, Shape.SWITCHBACK, 1420, 1, ridge(0.13, 0.17), water(0.49, 0.54), mud(0.77, 0.80), boost(0.23), boost(0.41), boost(0.60), boost(0.68), boost(0.84)),
	A_TEMPLE(RaceClass.A, 4, Theme.JUNGLE, Shape.CASTLE, 1440, 1, water(0.15, 0.20), ridge(0.48, 0.52), mud(0.70, 0.73), boost(0.34), boost(0.57), boost(0.77), boost(0.84), boost(0.92)),
	A_INFERNO(RaceClass.A, 5, Theme.NETHER, Shape.CROWN, 1460, 1, lava(0.19, 0.24), lava(0.38, 0.43), ridge(0.68, 0.72), mud(0.86, 0.89), boost(0.11), boost(0.30), boost(0.50), boost(0.63), boost(0.79)),
	// ---- S: skyway, keep, end
	S_SKYWAY(RaceClass.S, 0, Theme.SKYWAY, Shape.BOOMERANG, 750, 5, ridge(0.23, 0.27), water(0.67, 0.73), ridge(0.82, 0.86), boost(0.11), boost(0.41), boost(0.50)),
	S_KEEP(RaceClass.S, 1, Theme.KEEP, Shape.RAMPART, 825, 4, lava(0.09, 0.14), ridge(0.36, 0.40), mud(0.61, 0.64), lava(0.80, 0.84), boost(0.52), boost(0.73), boost(0.93)),
	S_VOID(RaceClass.S, 2, Theme.END, Shape.HAMMER, 950, 3, water(0.20, 0.25), ridge(0.67, 0.71), mud(0.87, 0.90), boost(0.37), boost(0.47), boost(0.58)),
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
	C_HEARTFIELD(RaceClass.C, 9, Theme.MEADOW, Shape.HEART, 630, 5, boost(0.36), boost(0.71), boost(0.93)),
	C_KITE_HILL(RaceClass.C, 10, Theme.MEADOW, Shape.KITE, 700, 4, boost(0.21), boost(0.46), boost(0.93)),
	C_HORSESHOE(RaceClass.C, 11, Theme.FARMLAND, Shape.HORSESHOE, 800, 3, boost(0.15), boost(0.36), boost(0.76)),
	// ---- new C courses (phase 2) end ----

	// ---- new B courses (phase 2) begin ----
	// sprints: a watering hole at the tip of a savanna tusk, a ridge in the notch of a canyon
	// arrowhead, a ford across a river's oxbow; grands prix: a savanna lozenge with a waterhole,
	// a kopje ridge on a savanna shield, a snow ridge across a mitten's thumb
	B_ACACIA(RaceClass.B, 6, Theme.SAVANNA, Shape.TUSK, 1340, 1, water(0.51, 0.54), boost(0.14), boost(0.36), boost(0.58), boost(0.69), boost(0.80)),
	B_GULCH(RaceClass.B, 7, Theme.CANYON, Shape.ARROWHEAD, 1320, 1, ridge(0.66, 0.70), boost(0.14), boost(0.33), boost(0.50), boost(0.73), boost(0.82)),
	B_OXBOW(RaceClass.B, 8, Theme.RIVER, Shape.OXBOW, 1360, 1, water(0.20, 0.25), boost(0.36), boost(0.52), boost(0.65), boost(0.79), boost(0.87)),
	B_BAOBAB(RaceClass.B, 9, Theme.SAVANNA, Shape.LOZENGE, 680, 5, water(0.78, 0.825), boost(0.21), boost(0.44), boost(0.69)),
	B_KOPJE(RaceClass.B, 10, Theme.SAVANNA, Shape.HEATER, 760, 4, ridge(0.70, 0.745), boost(0.25), boost(0.59), boost(0.93)),
	B_SNOWCAP(RaceClass.B, 11, Theme.SNOW, Shape.MITTEN, 860, 3, ridge(0.40, 0.435), boost(0.21), boost(0.65), boost(0.93)),
	// ---- new B courses (phase 2) end ----

	// ---- new A courses (phase 2) begin ----
	A_TOADSTOOL(RaceClass.A, 6, Theme.MUSHROOM, Shape.TOADSTOOL, 1480, 1, ridge(0.29, 0.33), mud(0.47, 0.51), water(0.66, 0.70), boost(0.11), boost(0.22), boost(0.36), boost(0.73), boost(0.86)),
	A_AMMONITE(RaceClass.A, 7, Theme.CAVERN, Shape.AMMONITE, 1440, 1, ridge(0.36, 0.41), ridge(0.49, 0.52), mud(0.83, 0.86), boost(0.10), boost(0.22), boost(0.30), boost(0.55), boost(0.72)),
	A_FORGE(RaceClass.A, 8, Theme.NETHER, Shape.ANVILHORN, 1500, 1, lava(0.30, 0.33), mud(0.46, 0.49), lava(0.64, 0.67), boost(0.14), boost(0.25), boost(0.37), boost(0.70), boost(0.86)),
	A_GROTTO(RaceClass.A, 9, Theme.CAVERN, Shape.BLINDFISH, 760, 5, mud(0.15, 0.18), water(0.38, 0.41), water(0.66, 0.69), boost(0.30), boost(0.53), boost(0.93)),
	A_MOONSHELF(RaceClass.A, 10, Theme.MUSHROOM, Shape.SHELFCAP, 800, 4, ridge(0.18, 0.22), lava(0.34, 0.38), mud(0.58, 0.63), boost(0.53), boost(0.77), boost(0.92)),
	A_MACHETE(RaceClass.A, 11, Theme.JUNGLE, Shape.MACHETE, 950, 3, ridge(0.19, 0.22), mud(0.44, 0.47), water(0.64, 0.67), boost(0.27), boost(0.53), boost(0.76)),
	// ---- new A courses (phase 2) end ----

	// ---- new S courses (phase 2) begin ----
	S_ABYSS(RaceClass.S, 6, Theme.DEEP_DARK, Shape.NAUTILUS, 1620, 1, water(0.20, 0.27), ridge(0.34, 0.41), water(0.46, 0.51), mud(0.87, 0.92), boost(0.12), boost(0.30), boost(0.62), boost(0.71), boost(0.80)),
	S_ZENITH(RaceClass.S, 7, Theme.END, Shape.COMET, 1600, 1, ridge(0.28, 0.35), water(0.40, 0.47), mud(0.56, 0.61), ridge(0.84, 0.90), boost(0.12), boost(0.20), boost(0.51), boost(0.66), boost(0.75)),
	S_BASTION(RaceClass.S, 8, Theme.KEEP, Shape.STARFORT, 1580, 1, water(0.27, 0.30), ridge(0.47, 0.50), mud(0.57, 0.63), water(0.67, 0.70), boost(0.12), boost(0.33), boost(0.53), boost(0.73), boost(0.92)),
	S_ORBIT(RaceClass.S, 9, Theme.SKYWAY, Shape.SATURN, 790, 5, water(0.10, 0.15), water(0.56, 0.61), mud(0.80, 0.83), boost(0.23), boost(0.47), boost(0.70)),
	S_ECLIPSE(RaceClass.S, 10, Theme.SKYWAY, Shape.CRESCENT_MOON, 880, 4, ridge(0.13, 0.19), mud(0.45, 0.49), ridge(0.79, 0.85), boost(0.37), boost(0.56), boost(0.88)),
	S_RIFT(RaceClass.S, 11, Theme.DEEP_DARK, Shape.FISSURE, 1000, 3, ridge(0.10, 0.16), water(0.49, 0.53), ridge(0.74, 0.78), mud(0.86, 0.90), boost(0.22), boost(0.37), boost(0.60)),
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
		/** A flat-out stadium oval with a chicane on the back straight. */
		STADIUM(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 0, 1), p(178, 10, 2), p(200, 40, 3), p(178, 70, 2), p(140, 80, 1), p(110, 80, 0), p(96, 68, 0), p(80, 80, 0), p(40, 80, 0), p(0, 80, 0), p(-40, 70, 0), p(-60, 40, 0), p(-40, 10, 0), p(-30, 0, 0)),
		/** Lightning-bolt switchbacks with a climb through the middle. */
		ZIGZAG(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(90, 0, 0), p(112, 12, 1), p(104, 40, 2), p(64, 52, 3), p(30, 62, 4), p(20, 86, 5), p(40, 106, 5), p(80, 112, 4), p(112, 122, 3), p(118, 148, 2), p(92, 166, 1), p(50, 168, 0), p(14, 162, 0), p(-10, 140, 0), p(-20, 108, 0), p(-24, 70, 0), p(-28, 36, 0), p(-30, 0, 0)),
		/** Two round lobes joined through a narrow waist. */
		PEANUT(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(90, 0, 0), p(112, 14, 1), p(118, 44, 2), p(104, 70, 2), p(80, 86, 1), p(68, 110, 1), p(78, 134, 2), p(102, 152, 3), p(108, 180, 3), p(88, 206, 2), p(50, 214, 1), p(14, 202, 0), p(-6, 174, 0), p(0, 146, 0), p(20, 128, 0), p(26, 104, 0), p(10, 84, 0), p(-6, 58, 0), p(-10, 30, 0), p(-30, 0, 0)),
		/** Three long straights and three tight corners: a triangle. */
		DELTA(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 0, 0), p(180, 0, 0), p(212, 6, 1), p(220, 28, 2), p(200, 54, 3), p(160, 90, 4), p(120, 126, 4), p(80, 160, 3), p(46, 180, 2), p(16, 176, 1), p(0, 156, 0), p(-4, 120, 0), p(-8, 80, 0), p(-14, 40, 0), p(-30, 0, 0)),
		/** A drag strip out to a round loop and the same strip back. */
		LOLLIPOP(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 0, 0), p(180, 0, 0), p(214, 10, 1), p(236, 40, 2), p(236, 80, 3), p(214, 110, 3), p(180, 120, 2), p(146, 110, 1), p(126, 80, 1), p(110, 50, 1), p(80, 46, 1), p(40, 46, 1), p(0, 46, 0), p(-24, 38, 0), p(-30, 16, 0), p(-30, 0, 0)),
		/** A bean: one long sweeper and a concave inner bend. */
		KIDNEY(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 4, 1), p(176, 20, 2), p(196, 52, 3), p(184, 84, 3), p(150, 96, 2), p(110, 90, 2), p(80, 100, 2), p(60, 124, 1), p(30, 140, 1), p(0, 132, 0), p(-20, 104, 0), p(-26, 70, 0), p(-30, 30, 0), p(-30, 0, 0)),
		/** A D: one long straight and one enormous arc. */
		DEE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 0, 0), p(176, 10, 1), p(200, 40, 2), p(206, 80, 3), p(196, 120, 4), p(170, 150, 4), p(130, 164, 3), p(90, 166, 2), p(50, 166, 1), p(10, 166, 0), p(-16, 150, 0), p(-24, 110, 0), p(-26, 70, 0), p(-30, 30, 0), p(-30, 0, 0)),
		/** A long L: two straights joined by a fast corner, a hairpin at each end. */
		ELBOW(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 0, 0), p(174, 8, 1), p(184, 36, 2), p(170, 64, 2), p(140, 72, 2), p(110, 74, 3), p(92, 94, 4), p(90, 130, 4), p(80, 162, 3), p(50, 174, 2), p(20, 164, 1), p(10, 134, 0), p(8, 100, 0), p(2, 60, 0), p(-12, 30, 0), p(-30, 0, 0)),
		/** Three parallel fingers joined by hairpins, an E on its back. */
		TRIDENT(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(130, 0, 0), p(150, 12, 1), p(150, 40, 2), p(130, 50, 3), p(100, 50, 3), p(80, 60, 4), p(80, 100, 5), p(100, 110, 5), p(130, 110, 4), p(150, 122, 3), p(150, 150, 2), p(130, 160, 1), p(50, 160, 0), p(20, 150, 0), p(10, 120, 0), p(10, 80, 0), p(0, 60, 0), p(-10, 40, 0), p(-30, 0, 0)),
		/** A V: two long diagonal arms and a hairpin at the tip. */
		BOOMERANG(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 26, 1), p(180, 66, 2), p(214, 104, 3), p(230, 140, 4), p(218, 168, 3), p(188, 172, 2), p(162, 150, 2), p(132, 120, 2), p(102, 90, 2), p(72, 66, 1), p(40, 60, 1), p(10, 80, 0), p(-16, 110, 0), p(-40, 130, 0), p(-60, 120, 0), p(-64, 90, 0), p(-50, 60, 0), p(-40, 30, 0), p(-30, 0, 0)),
		/** Right angles and crenellations, castle walls. */
		RAMPART(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 0, 0), p(150, 10, 1), p(150, 50, 3), p(140, 60, 4), p(110, 60, 4), p(100, 70, 5), p(100, 110, 5), p(110, 120, 4), p(150, 120, 3), p(160, 130, 2), p(160, 170, 1), p(150, 180, 0), p(20, 180, 0), p(0, 170, 0), p(-14, 130, 0), p(-26, 90, 0), p(-30, 40, 0), p(-30, 0, 0)),
		/** A T: a handle straight into a wide crossbar with a hairpin at each end. */
		HAMMER(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(90, 0, 0), p(110, 12, 1), p(110, 50, 2), p(140, 60, 3), p(190, 60, 3), p(210, 76, 4), p(210, 110, 4), p(190, 124, 3), p(-70, 124, 0), p(-90, 110, 0), p(-90, 76, 0), p(-70, 60, 1), p(-40, 60, 1), p(-30, 40, 0), p(-30, 0, 0)),
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
		/** A kite: a diamond climbing to its tip, and its tail of bows swinging back to the line. */
		KITE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(110, 0, 1), p(160, 2, 2), p(186, 28, 3), p(198, 90, 4), p(210, 150, 5), p(222, 190, 5), p(212, 214, 5), p(190, 222, 5), p(150, 210, 4), p(90, 198, 3), p(28, 186, 2), p(2, 160, 1), p(-14, 126, 1), p(10, 94, 0), p(-18, 62, 0), p(-40, 30, 0), p(-30, 0, 0)),
		/** A scallop shell: the hinge and its two ears along the bottom, a ribbed rim rolling over the top. */
		SCALLOP(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(132, 6, 1), p(146, 30, 1), p(124, 52, 2), p(150, 84, 2), p(196, 110, 3), p(224, 150, 4), p(206, 176, 4), p(196, 214, 5), p(164, 234, 5), p(140, 262, 6), p(100, 266, 6), p(70, 282, 6), p(30, 282, 6), p(0, 266, 5), p(-40, 262, 5), p(-64, 234, 4), p(-96, 214, 4), p(-106, 176, 3), p(-124, 150, 3), p(-96, 110, 2), p(-50, 84, 1), p(-24, 52, 1), p(-46, 30, 0), p(-40, 6, 0), p(-30, 0, 0)),
		/** A heart: the point on the start line, two round lobes and the dip between them. */
		HEART(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 0, 1), p(190, 10, 2), p(224, 44, 3), p(232, 90, 4), p(214, 132, 4), p(170, 150, 3), p(150, 170, 3), p(132, 214, 4), p(90, 232, 4), p(44, 224, 3), p(10, 190, 2), p(-4, 140, 1), p(-12, 100, 0), p(-22, 60, 0), p(-44, 20, 0), p(-30, 0, 0)),
		/** Three honeycomb cells: twelve short sides and gentle corners, climbing cell to cell. */
		HONEYCOMB(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(90, 0, 0), p(120, 0, 0), p(135, 52, 0), p(150, 104, 1), p(210, 104, 2), p(270, 104, 3), p(300, 156, 3), p(330, 208, 4), p(300, 260, 4), p(270, 312, 5), p(210, 312, 5), p(150, 312, 5), p(120, 364, 6), p(90, 416, 6), p(30, 416, 6), p(-30, 416, 5), p(-60, 364, 5), p(-90, 312, 4), p(-60, 260, 3), p(-30, 208, 3), p(-60, 156, 2), p(-90, 104, 1), p(-60, 52, 0), p(-30, 0, 0)),
		/** A horseshoe, heels up: round the toe, over one heel, down the inside and over the other. */
		HORSESHOE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(98, 8, 1), p(122, 34, 2), p(130, 70, 3), p(130, 110, 4), p(136, 146, 5), p(126, 170, 5), p(100, 174, 5), p(84, 150, 4), p(82, 112, 3), p(78, 80, 2), p(60, 56, 1), p(35, 48, 1), p(10, 56, 1), p(-8, 80, 2), p(-12, 112, 3), p(-14, 150, 4), p(-30, 174, 4), p(-56, 170, 4), p(-66, 146, 3), p(-60, 110, 2), p(-60, 70, 1), p(-50, 30, 0), p(-30, 0, 0)),
		// ---- new C shapes (phase 2) end ----

		// ---- new B shapes (phase 2) begin ----
		/** A crescent tusk: a long climbing outer sweep, a hairpin at the tip, a concave run home. */
		TUSK(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 1), p(150, 10, 2), p(200, 40, 3), p(236, 90, 3), p(250, 150, 3), p(236, 210, 3), p(200, 254, 2), p(160, 272, 1), p(126, 266, 0), p(120, 240, 0), p(160, 216, 0), p(186, 180, 1), p(192, 140, 2), p(180, 104, 3), p(150, 76, 3), p(110, 58, 2), p(60, 54, 1), p(16, 56, 0), p(-16, 44, 0), p(-30, 20, 0), p(-30, 0, 0)),
		/** An arrowhead: two long flanks out to a sharp point, and a notched tail back by the line. */
		ARROWHEAD(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(150, 0, 1), p(200, 14, 2), p(250, 44, 3), p(296, 82, 4), p(310, 104, 5), p(296, 126, 5), p(250, 164, 4), p(200, 194, 3), p(150, 208, 2), p(100, 212, 1), p(60, 212, 0), p(20, 210, 0), p(-20, 204, 0), p(-40, 186, 0), p(-30, 160, 0), p(10, 124, 0), p(26, 106, 0), p(10, 86, 0), p(-30, 50, 0), p(-44, 22, 0), p(-30, 0, 0)),
		/** A meandering river: a pinched oxbow loop out, a long falling diagonal home. */
		OXBOW(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 0, 0), p(174, 14, 0), p(190, 44, 0), p(180, 76, 0), p(150, 90, 0), p(130, 110, 0), p(136, 140, 0), p(166, 150, 0), p(200, 160, 1), p(214, 196, 2), p(196, 230, 3), p(156, 242, 4), p(110, 236, 5), p(80, 212, 5), p(70, 180, 4), p(60, 150, 3), p(40, 120, 2), p(20, 100, 1), p(-10, 90, 0), p(-36, 70, 0), p(-40, 34, 0), p(-30, 0, 0)),
		/** A lozenge: four straights leaning one way, two sharp corners and two open ones. */
		LOZENGE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(130, 4, 1), p(150, 22, 2), p(170, 60, 3), p(186, 96, 3), p(176, 116, 2), p(146, 124, 1), p(100, 124, 0), p(60, 124, 0), p(30, 118, 0), p(10, 96, 0), p(-10, 60, 0), p(-30, 24, 0), p(-30, 0, 0)),
		/** A heater shield: a flat top, and two long curving flanks down to a rounded point. */
		HEATER(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 0, 0), p(170, 10, 1), p(180, 40, 2), p(176, 80, 3), p(160, 120, 3), p(130, 156, 2), p(96, 180, 1), p(70, 186, 0), p(44, 176, 0), p(10, 148, 0), p(-16, 110, 0), p(-30, 70, 0), p(-34, 30, 0), p(-30, 0, 0)),
		/** A mitten: a round hand and a thumb poking out the side. */
		MITTEN(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(130, 6, 1), p(146, 30, 2), p(146, 70, 3), p(140, 110, 4), p(120, 150, 4), p(84, 170, 3), p(48, 166, 2), p(24, 144, 1), p(20, 116, 1), p(-6, 104, 0), p(-40, 110, 0), p(-62, 96, 0), p(-58, 70, 0), p(-34, 54, 0), p(-24, 30, 0), p(-30, 0, 0)),
		// ---- new B shapes (phase 2) end ----

		// ---- new A shapes (phase 2) begin ----
		/** A toadstool: a bulb foot, a narrow stem climbing to a great domed cap, and down the far rim. */
		TOADSTOOL(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(96, 4, 0), p(118, 24, 1), p(112, 50, 2), p(90, 70, 3), p(84, 100, 4), p(92, 126, 5), p(130, 134, 6), p(176, 136, 6), p(210, 152, 6), p(218, 184, 5), p(196, 218, 4), p(150, 244, 3), p(90, 256, 2), p(30, 252, 1), p(-26, 232, 1), p(-70, 204, 0), p(-90, 172, 0), p(-78, 146, 0), p(-40, 136, 0), p(-4, 132, 0), p(12, 110, 0), p(10, 80, 0), p(-6, 56, 0), p(-34, 36, 0), p(-48, 14, 0), p(-30, 0, 0)),
		/** An ammonite: one long coil winding into a hairpin at its heart and back out alongside itself. */
		AMMONITE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(90, 0, 0), p(130, 0, 0), p(180, 12, 1), p(225, 35, 2), p(286, 110, 4), p(300, 200, 5), p(269, 280, 6), p(205, 330, 6), p(130, 340, 6), p(65, 313, 5), p(26, 260, 4), p(20, 200, 2), p(43, 150, 0), p(68, 126, 0), p(102, 140, 0), p(94, 176, 0), p(80, 204, 0), p(80, 232, 0), p(95, 261, 0), p(130, 280, 0), p(175, 278, 0), p(217, 250, 0), p(240, 200, 0), p(234, 140, 0), p(195, 87, 0), p(165, 70, 0), p(130, 60, 0), p(100, 60, 0), p(70, 60, 0), p(40, 60, 0), p(10, 60, 0), p(-20, 60, 0), p(-67, 30, 0), p(-30, 0, 0)),
		/** An anvil: a broad foot, a pinched waist, a flat face and a long tapering horn. */
		ANVILHORN(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(110, 0, 0), p(150, 0, 0), p(176, 8, 0), p(184, 30, 1), p(166, 46, 2), p(126, 52, 3), p(108, 70, 4), p(106, 100, 5), p(120, 122, 6), p(170, 128, 6), p(214, 134, 6), p(234, 156, 5), p(226, 182, 4), p(190, 194, 3), p(120, 196, 2), p(40, 196, 1), p(-30, 196, 0), p(-90, 190, 0), p(-150, 176, 0), p(-178, 160, 0), p(-160, 142, 0), p(-110, 132, 0), p(-50, 122, 0), p(-6, 110, 0), p(0, 80, 0), p(-10, 56, 0), p(-46, 48, 0), p(-66, 34, 0), p(-66, 12, 0), p(-50, 0, 0), p(-30, 0, 0)),
		/** A cave fish: a round head and belly, a narrow tail stock and two fat tail lobes either side of a notch. */
		BLINDFISH(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 6, 0), p(136, 30, 1), p(162, 10, 2), p(192, -16, 2), p(226, -14, 2), p(240, 14, 2), p(224, 40, 2), p(196, 62, 2), p(224, 86, 3), p(240, 114, 3), p(226, 142, 3), p(192, 144, 3), p(162, 118, 3), p(136, 94, 3), p(100, 112, 2), p(60, 120, 1), p(20, 118, 0), p(-20, 104, 0), p(-50, 78, 0), p(-58, 46, 0), p(-46, 16, 0), p(-30, 0, 0)),
		/** A shelf fungus in profile: one great outer arc, a deep concave inner arc and a horn at each end. */
		SHELFCAP(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 6, 1), p(136, 28, 2), p(160, 64, 3), p(168, 104, 3), p(156, 146, 2), p(124, 180, 1), p(80, 198, 0), p(36, 198, 0), p(2, 184, 0), p(-4, 160, 0), p(22, 148, 0), p(62, 140, 0), p(92, 118, 0), p(102, 92, 0), p(94, 66, 0), p(70, 50, 0), p(36, 48, 0), p(0, 50, 0), p(-34, 42, 0), p(-50, 20, 0), p(-30, 0, 0)),
		/** A machete: a long straight edge sweeping up to the point, a flat spine and a narrow handle with a pommel. */
		MACHETE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(120, 0, 0), p(170, 4, 1), p(214, 20, 2), p(244, 50, 3), p(258, 86, 3), p(240, 104, 3), p(200, 104, 3), p(140, 104, 2), p(80, 104, 2), p(20, 104, 1), p(-30, 106, 1), p(-60, 100, 0), p(-120, 100, 0), p(-170, 100, 0), p(-196, 112, 0), p(-216, 98, 0), p(-216, 58, 0), p(-196, 42, 0), p(-150, 46, 0), p(-100, 48, 0), p(-60, 48, 0), p(-40, 38, 0), p(-36, 18, 0), p(-30, 0, 0)),
		// ---- new A shapes (phase 2) end ----

		// ---- new S shapes (phase 2) begin ----
		/** A nautilus shell: one coil spiralling down into a hairpin at the heart and back out between its own turns. */
		NAUTILUS(p(0, 0, 8), p(30, 0, 8), p(60, 0, 8), p(90, 0, 8), p(120, 0, 8), p(211, 33, 8), p(270, 103, 7), p(285, 190, 6), p(256, 268, 5), p(194, 318, 4), p(120, 330, 3), p(54, 304, 2), p(13, 252, 1), p(5, 190, 0), p(28, 137, 0), p(61, 109, 0), p(106, 116, 0), p(91, 150, 0), p(67, 166, 0), p(54, 197, 1), p(59, 234, 2), p(86, 266, 3), p(130, 281, 4), p(179, 271, 5), p(219, 234, 6), p(236, 178, 7), p(221, 117, 8), p(174, 68, 8), p(120, 50, 8), p(40, 50, 8), p(0, 50, 8), p(-30, 50, 8), p(-55, 25, 8), p(-30, 0, 8)),
		/** A comet: one huge round head and a long tail narrowing to a hairpin at its tip. */
		COMET(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(110, 0, 0), p(170, 0, 1), p(230, 0, 2), p(300, 6, 4), p(355, 35, 6), p(392, 90, 8), p(390, 150, 9), p(355, 195, 9), p(300, 214, 8), p(240, 204, 7), p(170, 176, 5), p(100, 142, 3), p(30, 104, 1), p(-30, 70, 0), p(-70, 40, 0), p(-72, 12, 0), p(-30, 0, 0)),
		/** A star fort: five arrowhead bastions jutting from straight curtain walls. */
		STARFORT(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(107, 0, 0), p(142, -14, 1), p(177, -28, 2), p(175, 10, 2), p(172, 48, 2), p(184, 82, 3), p(195, 117, 3), p(206, 152, 4), p(218, 187, 4), p(242, 216, 5), p(266, 246, 5), p(230, 255, 5), p(193, 264, 5), p(163, 286, 5), p(133, 307, 6), p(104, 329, 6), p(74, 351, 6), p(54, 383, 7), p(33, 415, 7), p(13, 383, 7), p(-7, 351, 6), p(-37, 329, 6), p(-66, 307, 5), p(-96, 286, 5), p(-126, 264, 4), p(-163, 255, 4), p(-200, 246, 4), p(-175, 216, 3), p(-151, 187, 3), p(-139, 152, 2), p(-128, 117, 2), p(-117, 82, 1), p(-105, 48, 1), p(-108, 10, 1), p(-111, -28, 1), p(-75, -14, 0), p(-30, 0, 0)),
		/** A ringed planet: a round body with its ring poking out either side as two long hairpin fingers. */
		SATURN(p(0, 0, 2), p(30, 0, 2), p(60, 0, 2), p(100, 0, 2), p(140, 0, 2), p(175, -30, 1), p(204, -60, 0), p(250, -72, 0), p(296, -60, 0), p(325, -30, 1), p(360, 0, 2), p(400, 0, 2), p(440, 0, 2), p(480, 5, 2), p(505, 38, 2), p(480, 71, 2), p(440, 76, 2), p(400, 76, 2), p(360, 76, 2), p(330, 106, 3), p(300, 138, 5), p(250, 150, 6), p(200, 138, 5), p(170, 106, 3), p(140, 76, 2), p(100, 76, 2), p(60, 76, 2), p(20, 76, 2), p(-20, 71, 2), p(-45, 38, 2), p(-30, 0, 2)),
		/** A crescent moon: a fat outer arc, a concave inner one and two blunt horns. */
		CRESCENT_MOON(p(0, 0, 2), p(30, 0, 2), p(60, 0, 2), p(90, 0, 2), p(151, 18, 3), p(176, 34, 3), p(198, 54, 4), p(216, 77, 4), p(230, 103, 5), p(240, 132, 5), p(243, 149, 5), p(218, 178, 5), p(185, 144, 4), p(165, 123, 3), p(141, 107, 2), p(114, 95, 1), p(85, 90, 0), p(55, 91, 0), p(27, 98, 1), p(1, 110, 2), p(-23, 128, 3), p(-64, 180, 4), p(-95, 149, 4), p(-89, 120, 4), p(-78, 93, 3), p(-62, 67, 3), p(-45, 38, 2), p(-38, 14, 2), p(-30, 0, 2)),
		/** A block of ground split by a jagged fissure that zigzags down into it and back out. */
		FISSURE(p(0, 0, 8), p(30, 0, 8), p(60, 0, 8), p(100, 0, 8), p(140, 0, 8), p(185, 6, 8), p(222, 30, 8), p(242, 70, 8), p(246, 120, 8), p(238, 170, 8), p(212, 205, 8), p(180, 214, 8), p(170, 190, 7), p(188, 150, 5), p(156, 110, 3), p(172, 72, 1), p(138, 48, 0), p(104, 64, 1), p(106, 108, 3), p(88, 148, 5), p(100, 190, 7), p(84, 214, 8), p(50, 220, 8), p(15, 210, 8), p(-12, 185, 8), p(-24, 140, 8), p(-26, 90, 8), p(-30, 40, 8), p(-30, 0, 8)),
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
	public static final double MAX_ISLAND_RADIUS = 385.0D;
	/** The most courses a class can hold on its row today (six sprints and six grands prix). */
	public static final int MAX_COURSES_PER_CLASS = 12;

	private final RaceClass raceClass;
	private final int course;
	private final Theme theme;
	private final Shape shape;
	private final int laps;
	private final List<Feature> features;
	private final TrackSpline spline;
	private final double offsetX, offsetZ;

	RaceTrack(RaceClass raceClass, int course, Theme theme, Shape shape, double lapTarget, int laps, Feature... features) {
		this.raceClass = raceClass;
		this.course = course;
		this.theme = theme;
		this.shape = shape;
		this.laps = laps;
		this.features = List.of(features);
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

	public static RaceTrack byId(int id) {
		RaceTrack[] values = values();
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
		List<Feature> out = new ArrayList<>();
		for (Feature f : features) {
			if (f.terrain()) {
				out.add(f);
			}
		}
		return out;
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
