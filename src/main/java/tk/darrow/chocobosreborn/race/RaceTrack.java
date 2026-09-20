package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tk.darrow.chocobosreborn.breed.ChocoboColor;

/**
 * Whiskerwind's courses, kart-racer style: six per class (three sprints of one
 * lap, three grands prix of three laps), each a themed circuit on its own sky
 * island. A circuit is a {@link Shape} (a closed spline with straights, sweepers,
 * hairpins, chicanes and hills) scaled to the class's lap length, dressed in a
 * {@link Theme}, and carrying {@link Feature}s along the way:
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
 * Boost strips take what is left on the corner exits — three on a sprint, five on a
 * grand prix. {@code CourseBalanceTest} holds all of that in place.
 */
public enum RaceTrack {
	// ---- C: meadow, orchard, shore
	C_MEADOW(RaceClass.C, 0, Theme.MEADOW, Shape.STADIUM, 600, 1, boost(0.10), boost(0.56), boost(0.93)),
	C_ORCHARD(RaceClass.C, 1, Theme.ORCHARD, Shape.ZIGZAG, 612, 1, boost(0.18), boost(0.36), boost(0.93)),
	C_SHORE(RaceClass.C, 2, Theme.SHORE, Shape.PEANUT, 624, 1, boost(0.19), boost(0.80), boost(0.93)),
	C_DOWNS(RaceClass.C, 3, Theme.MEADOW, Shape.ROVAL, 1150, 3, boost(0.11), boost(0.47), boost(0.58), boost(0.66), boost(0.93)),
	C_CIDER(RaceClass.C, 4, Theme.ORCHARD, Shape.CLOUD, 1170, 3, boost(0.42), boost(0.59), boost(0.70), boost(0.85), boost(0.93)),
	C_LAGOON(RaceClass.C, 5, Theme.SHORE, Shape.WAVE, 1190, 3, boost(0.10), boost(0.22), boost(0.46), boost(0.81), boost(0.92)),
	// ---- B: canyon, river, snow
	B_CANYON(RaceClass.B, 0, Theme.CANYON, Shape.DELTA, 650, 1, ridge(0.50, 0.54), boost(0.31), boost(0.71), boost(0.93)),
	B_FORD(RaceClass.B, 1, Theme.RIVER, Shape.LOLLIPOP, 662, 1, water(0.59, 0.64), boost(0.14), boost(0.68), boost(0.93)),
	B_FROST(RaceClass.B, 2, Theme.SNOW, Shape.KIDNEY, 674, 1, ridge(0.47, 0.51), boost(0.41), boost(0.77), boost(0.92)),
	B_MESA(RaceClass.B, 3, Theme.CANYON, Shape.SERPENT, 1280, 3, ridge(0.44, 0.48), boost(0.20), boost(0.35), boost(0.53), boost(0.83), boost(0.91)),
	B_RAPIDS(RaceClass.B, 4, Theme.RIVER, Shape.HAIRPIN, 1300, 3, water(0.51, 0.56), boost(0.22), boost(0.39), boost(0.60), boost(0.75), boost(0.92)),
	B_GLACIER(RaceClass.B, 5, Theme.SNOW, Shape.STAIRS, 1320, 3, water(0.33, 0.38), boost(0.45), boost(0.58), boost(0.66), boost(0.78), boost(0.87)),
	// ---- A: cavern, jungle, nether
	A_CRYSTAL(RaceClass.A, 0, Theme.CAVERN, Shape.DEE, 700, 1, ridge(0.12, 0.16), water(0.51, 0.56), mud(0.80, 0.83), boost(0.46), boost(0.72), boost(0.93)),
	A_CANOPY(RaceClass.A, 1, Theme.JUNGLE, Shape.ELBOW, 712, 1, water(0.34, 0.39), ridge(0.46, 0.50), mud(0.78, 0.81), boost(0.10), boost(0.68), boost(0.92)),
	A_EMBER(RaceClass.A, 2, Theme.NETHER, Shape.TRIDENT, 724, 1, lava(0.31, 0.36), ridge(0.54, 0.58), mud(0.84, 0.87), boost(0.22), boost(0.73), boost(0.93)),
	A_DEEPS(RaceClass.A, 3, Theme.CAVERN, Shape.SWITCHBACK, 1420, 3, ridge(0.13, 0.17), water(0.49, 0.54), mud(0.77, 0.80), boost(0.23), boost(0.41), boost(0.60), boost(0.68), boost(0.84)),
	A_TEMPLE(RaceClass.A, 4, Theme.JUNGLE, Shape.CASTLE, 1440, 3, water(0.15, 0.20), ridge(0.48, 0.52), mud(0.70, 0.73), boost(0.34), boost(0.57), boost(0.77), boost(0.84), boost(0.92)),
	A_INFERNO(RaceClass.A, 5, Theme.NETHER, Shape.CROWN, 1460, 3, lava(0.19, 0.24), lava(0.38, 0.43), ridge(0.68, 0.72), mud(0.86, 0.89), boost(0.11), boost(0.30), boost(0.50), boost(0.63), boost(0.79)),
	// ---- S: skyway, keep, end
	S_SKYWAY(RaceClass.S, 0, Theme.SKYWAY, Shape.BOOMERANG, 750, 1, ridge(0.23, 0.27), water(0.67, 0.73), ridge(0.82, 0.86), boost(0.11), boost(0.41), boost(0.50)),
	S_KEEP(RaceClass.S, 1, Theme.KEEP, Shape.RAMPART, 762, 1, lava(0.09, 0.14), ridge(0.36, 0.40), mud(0.61, 0.64), lava(0.80, 0.84), boost(0.52), boost(0.73), boost(0.93)),
	S_VOID(RaceClass.S, 2, Theme.END, Shape.HAMMER, 774, 1, water(0.20, 0.25), ridge(0.67, 0.71), mud(0.87, 0.90), boost(0.37), boost(0.47), boost(0.58)),
	S_STARFALL(RaceClass.S, 3, Theme.SKYWAY, Shape.SWEEPS, 1560, 3, ridge(0.09, 0.13), water(0.22, 0.28), ridge(0.49, 0.53), water(0.68, 0.72), boost(0.44), boost(0.60), boost(0.76), boost(0.81), boost(0.89)),
	S_CITADEL(RaceClass.S, 4, Theme.KEEP, Shape.HOOK, 1580, 3, lava(0.25, 0.30), ridge(0.56, 0.60), mud(0.65, 0.68), lava(0.74, 0.79), boost(0.11), boost(0.34), boost(0.51), boost(0.83), boost(0.87)),
	S_MAELSTROM(RaceClass.S, 5, Theme.END, Shape.BEE, 1600, 3, water(0.15, 0.20), ridge(0.28, 0.32), water(0.62, 0.68), ridge(0.88, 0.92), boost(0.10), boost(0.49), boost(0.54), boost(0.75), boost(0.80));

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

	/** Visual dressing of a course: road, kerbs (corner stripes), rail, wall / trim, margin ground, island rock, posts, lamps. */
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
		END("end_stone_bricks", "purple_concrete", "white_concrete", "end_stone_brick_wall", "purpur_block", "end_stone", "end_stone", "purpur_pillar", "end_rod");

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
		BEE(p(0, 0, 0), p(30, 0, 0), p(60, 0, 0), p(100, 0, 0), p(140, 6, 1), p(170, 30, 2), p(176, 64, 3), p(166, 90, 3), p(150, 104, 4), p(166, 120, 4), p(178, 146, 5), p(170, 184, 4), p(140, 206, 3), p(100, 212, 2), p(60, 208, 1), p(20, 206, 0), p(-10, 190, 0), p(-20, 150, 0), p(-24, 110, 0), p(-26, 70, 0), p(-28, 36, 0), p(-30, 0, 0));

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
	public static final double PADDOCK_Z = -58.5D;
	public static final double STALL_SPACING = 1.8D;
	public static final double STALL_HALF_WIDTH = 5.0D;
	/** Road band either side of the line; features replace this band, detours sit outside it. */
	public static final double ROAD_HALF = 5.5D;
	public static final double DETOUR_INNER = 9.0D;
	public static final double DETOUR_OUTER = 16.0D;
	/** Course islands: one per class row (z), one per course column (x); the village sits near the origin. */
	private static final double ROW_Z0 = 700.0D;
	private static final double ROW_DZ = 900.0D;
	private static final double COL_DX = 820.0D;
	public static final int COURSES_PER_CLASS = 6;

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

	public static RaceTrack forClass(RaceClass raceClass, int course) {
		int slot = Math.max(0, Math.min(COURSES_PER_CLASS - 1, course));
		for (RaceTrack track : values()) {
			if (track.raceClass == raceClass && track.course == slot) {
				return track;
			}
		}
		return C_MEADOW;
	}

	public static List<RaceTrack> ofClass(RaceClass raceClass) {
		List<RaceTrack> out = new ArrayList<>();
		for (RaceTrack t : values()) {
			if (t.raceClass == raceClass) {
				out.add(t);
			}
		}
		return out;
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

	public boolean isShort() {
		return course < 3;
	}

	public int getLaps() {
		return laps;
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
		return 0.5D + (course - 2.5D) * COL_DX;
	}

	public double centerZ() {
		return 0.5D + ROW_Z0 + raceClass.getId() * ROW_DZ;
	}

	/**
	 * Extra blocks a bird that takes the detour travels over one that goes straight
	 * through {@code f}, the swing out and back included. This is what a colour's
	 * ability is worth on this course, so features are placed to keep it even: see
	 * {@link #detourTarget()}.
	 */
	public double detourCost(Feature f) {
		double connect = 0.012D, lane = -(DETOUR_INNER + DETOUR_OUTER) / 2.0D;
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
