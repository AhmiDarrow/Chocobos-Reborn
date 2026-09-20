package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The course islands must be solid under everything a racer can be on. A gap in the
 * stamp is a hole clean through the island into the void, and
 * {@link RaceScoring#squareFallRescue} cannot save a rider who drops through one
 * while {@link RaceCourseLayout#onCourse} still reads true above it: they fall out
 * of the world, the client unloads the course and only the sky is left.
 */
class CourseIslandTest {
	private static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	/** Every (x, z) the plan puts a block in, at any height. */
	private static Set<Long> columns(RaceCourseLayout layout) {
		Set<Long> out = new HashSet<>();
		for (RaceCourseLayout.Cell c : layout.blocks().keySet()) {
			out.add(key(c.x(), c.z()));
		}
		return out;
	}

	@Test
	void noVoidColumnUnderTheRacingArea() {
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout layout = RaceCourseLayout.of(track);
			Set<Long> filled = columns(layout);
			int holes = 0;
			String first = null;
			for (RaceCourseLayout.Tile tile : layout.road()) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						int x = tile.x() + dx, z = tile.z() + dz;
						if (filled.contains(key(x, z))) {
							continue;
						}
						holes++;
						if (first == null) {
							first = " first at (" + x + ", " + z + ")";
						}
					}
				}
			}
			assertEquals(0, holes, track.name() + " has " + holes + " void columns under the road," + first);
		}
	}

	@Test
	void everyLaneOfTheRoadHasAFloor() {
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout layout = RaceCourseLayout.of(track);
			Set<Long> filled = columns(layout);
			int steps = (int) Math.ceil(track.lapLength() * 4.0D);
			for (int i = 0; i < steps; i++) {
				double t = i / (double) steps;
				for (double o = -RaceTrack.ROAD_HALF; o <= RaceTrack.ROAD_HALF; o += 0.25D) {
					RacePoint p = track.pointAtLane(t, o);
					int x = (int) Math.floor(p.x()), z = (int) Math.floor(p.z());
					assertTrue(filled.contains(key(x, z)),
							track.name() + " has no floor at lane " + o + " of t=" + t + " (" + x + ", " + z + ")");
				}
			}
		}
	}


	/** Every block in the plan, by cell. */
	private static Map<Long, String> blocks(RaceCourseLayout layout) {
		Map<Long, String> out = new HashMap<>();
		for (Map.Entry<RaceCourseLayout.Cell, String> e : layout.blocks().entrySet()) {
			RaceCourseLayout.Cell c = e.getKey();
			out.put(cellKey(c.x(), c.y(), c.z()), e.getValue());
		}
		return out;
	}

	private static long cellKey(int x, int y, int z) {
		return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
	}


	/** Level of the racing surface in each road column (kerbs and features count as road). */
	private static Map<Long, Integer> roadSurface(RaceCourseLayout layout, RaceTrack track) {
		List<String> surfaces = List.of(track.theme().road, track.theme().kerbA, track.theme().kerbB,
				track.theme().wall, "water", "lava", "mud", "yellow_concrete");
		Map<Long, Integer> out = new HashMap<>();
		for (Map.Entry<RaceCourseLayout.Cell, String> e : layout.blocks().entrySet()) {
			RaceCourseLayout.Cell c = e.getKey();
			if (!layout.road().contains(new RaceCourseLayout.Tile(c.x(), c.z()))) {
				continue;
			}
			if (!surfaces.contains(e.getValue()) && !e.getValue().endsWith("_concrete")) {
				continue;
			}
			long k = key(c.x(), c.z());
			Integer had = out.get(k);
			if (had == null || c.y() > had) {
				out.put(k, c.y());
			}
		}
		return out;
	}

	/**
	 * A pool is stamped as source blocks with no block update, so it sits still until
	 * something disturbs it — and then it drains over the island through any open face.
	 * Lava doing that on the lap below is a course-ruining surprise, so every face of
	 * every pool must be walled.
	 */
	@Test
	void poolsCannotDrainOverTheIsland() {
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout layout = RaceCourseLayout.of(track);
			Map<Long, String> at = blocks(layout);
			Map<Long, Integer> top = new HashMap<>();
			for (RaceCourseLayout.Cell c : layout.blocks().keySet()) {
				top.merge(key(c.x(), c.z()), c.y(), Math::max);
			}
			for (RaceCourseLayout.Tile tile : layout.road()) {
				Integer surf = top.get(key(tile.x(), tile.z()));
				if (surf == null) {
					continue;
				}
				String here = at.get(cellKey(tile.x(), surf, tile.z()));
				if (!"water".equals(here) && !"lava".equals(here)) {
					continue;
				}
				for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					for (int y : new int[]{surf, surf - 1}) {
						assertNotNull(at.get(cellKey(tile.x() + d[0], y, tile.z() + d[1])),
								track.name() + ": " + here + " at (" + tile.x() + ", " + surf + ", " + tile.z()
										+ ") is open to (" + (tile.x() + d[0]) + ", " + y + ", " + (tile.z() + d[1]) + ")");
					}
				}
			}
		}
	}

	/**
	 * A circuit folds back on itself, so the plan is stamped in layers (ground, road,
	 * kerbs, scenery) and the road wins every overlap. Laid in one pass, a rail, a
	 * cactus or a camp fire from the leg next door ends up standing in the racing line.
	 */
	@Test
	void nothingIsStandingInTheRacingLine() {
		List<String> scenery = List.of("fence", "wall", "leaves", "cactus", "fire", "poppy", "dandelion", "fern",
				"grass", "dead_bush", "chorus", "end_rod", "lantern", "bamboo", "sapling", "mushroom", "snow[",
				"campfire", "torch", "sugar_cane", "vine", "log");
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout layout = RaceCourseLayout.of(track);
			Map<Long, Integer> surface = roadSurface(layout, track);
			for (Map.Entry<RaceCourseLayout.Cell, String> e : layout.blocks().entrySet()) {
				RaceCourseLayout.Cell c = e.getKey();
				Integer surf = surface.get(key(c.x(), c.z()));
				// only what a rider would ride into: the start gantry passes high overhead
				if (surf == null || c.y() <= surf || c.y() > surf + 3) {
					continue;
				}
				for (String bad : scenery) {
					assertFalse(e.getValue().contains(bad), track.name() + " has " + e.getValue() + " on the road at ("
							+ c.x() + ", " + c.y() + ", " + c.z() + ")");
				}
			}
		}
	}

	/** Water, lava and bog belong to their own stretch of the lap, nowhere else. */
	@Test
	void noStrayTerrainOnTheRacingLine() {
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout layout = RaceCourseLayout.of(track);
			Map<Long, Integer> top = new HashMap<>();
			for (RaceCourseLayout.Cell c : layout.blocks().keySet()) {
				top.merge(key(c.x(), c.z()), c.y(), Math::max);
			}
			Map<Long, String> at = blocks(layout);
			for (RaceCourseLayout.Tile tile : layout.road()) {
				Integer surf = top.get(key(tile.x(), tile.z()));
				String here = surf == null ? null : at.get(cellKey(tile.x(), surf, tile.z()));
				if (here == null) {
					continue;
				}
				RaceTrack.Feature.Type want = switch (here) {
					case "water" -> RaceTrack.Feature.Type.WATER;
					case "lava" -> RaceTrack.Feature.Type.LAVA;
					case "mud" -> RaceTrack.Feature.Type.MUD;
					default -> null;
				};
				if (want == null) {
					continue;
				}
				double p = track.progressAt(tile.x() + 0.5D, tile.z() + 0.5D);
				// a block sits across a span of t, so allow a block either side of the feature's ends
				double slack = 1.5D / track.lapLength();
				RaceTrack.Feature f = track.terrainAt(p);
				if (f == null) {
					f = track.terrainAt(p + slack);
				}
				if (f == null) {
					f = track.terrainAt(p - slack);
				}
				assertTrue(f != null && f.type() == want, track.name() + " has " + here + " at (" + tile.x() + ", "
						+ tile.z() + "), t=" + String.format("%.3f", p) + ", where the feature is "
						+ (f == null ? "none" : f.type()));
			}
		}
	}

	@Test
	void everyStallStandsOnSomething() {
		for (RaceTrack track : RaceTrack.values()) {
			Set<Long> filled = columns(RaceCourseLayout.of(track));
			for (int stall = 0; stall < RaceSession.FIELD; stall++) {
				RacePoint p = track.stallPos(stall, RaceSession.FIELD);
				assertTrue(filled.contains(key((int) Math.floor(p.x()), (int) Math.floor(p.z()))),
						track.name() + " stall " + stall + " stands over the void");
			}
		}
	}
}
