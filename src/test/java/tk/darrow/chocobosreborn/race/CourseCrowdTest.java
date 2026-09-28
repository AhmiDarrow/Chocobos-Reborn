package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The stands and the crowd: more of both up the classes, stands on both sides of every
 * course, and never a stand block near the road — a circuit folds back on itself, so
 * every road tile of the lap counts, not just the section the stand faces.
 */
class CourseCrowdTest {
	private static final int[][] FAN_RANGE = {{30, 50}, {65, 95}, {120, 160}, {190, 250}};

	@Test
	void standsAndFansGrowWithTheClass() {
		Map<RaceClass, int[]> minMax = new EnumMap<>(RaceClass.class);
		StringBuilder table = new StringBuilder();
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout lay = RaceCourseLayout.of(track);
			List<CourseStands.Stand> stands = lay.stands();
			int fans = lay.fanPosts().size();
			RaceClass rc = track.getRaceClass();
			assertEquals(1 + CourseStands.extraStands(rc), stands.size(), track + " stands");
			int[] range = FAN_RANGE[rc.getId()];
			assertTrue(fans >= range[0] && fans <= range[1], track + " seats " + fans + " fans");
			int[] mm = minMax.computeIfAbsent(rc, c -> new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE});
			mm[0] = Math.min(mm[0], stands.size());
			mm[1] = Math.max(mm[1], stands.size());
			mm[2] = Math.min(mm[2], fans);
			mm[3] = Math.max(mm[3], fans);
			int inside = 0, outside = 0;
			for (CourseStands.Stand s : stands) {
				if (s.side() > 0) {
					inside++;
				} else {
					outside++;
				}
			}
			table.append(track).append(' ').append(stands.size()).append(" stands (").append(inside).append(" in, ")
					.append(outside).append(" out) ").append(fans).append(" fans\n");
			// every course has a stand on each side of the road
			assertTrue(inside >= 1 && outside >= 1, track + " has " + inside + " stands inside and " + outside + " outside");
			assertTrue(Math.abs(inside - outside) <= 2 + (stands.size() > 6 ? 1 : 0), track + " lopsided: " + inside + " / " + outside);
		}
		System.out.print(table);
		// strictly more stands and more fans every class up: the least popular S course beats the busiest A
		RaceClass[] order = RaceClass.values();
		for (int i = 1; i < order.length; i++) {
			int[] lo = minMax.get(order[i - 1]), hi = minMax.get(order[i]);
			assertTrue(hi[0] > lo[1], order[i] + " stands " + hi[0] + " vs " + lo[1]);
			assertTrue(hi[2] > lo[3], order[i] + " fans " + hi[2] + " vs " + lo[3]);
		}
	}

	@Test
	void noStandBlockNearAnyRoadTile() {
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout lay = RaceCourseLayout.of(track);
			Set<Long> road = new HashSet<>();
			for (RaceCourseLayout.Tile t : lay.road()) {
				road.add(RaceCourseLayout.roadKey(t.x(), t.z()));
			}
			assertFalse(lay.standTiles().isEmpty(), track + " built no stand");
			for (RaceCourseLayout.Tile t : lay.standTiles()) {
				for (int dx = -3; dx <= 3; dx++) {
					for (int dz = -3; dz <= 3; dz++) {
						if (dx * dx + dz * dz <= CourseStands.CLEAR2) {
							assertFalse(road.contains(RaceCourseLayout.roadKey(t.x() + dx, t.z() + dz)),
									track + " stand block at " + t + " is within 3 of road (" + (t.x() + dx) + ", " + (t.z() + dz) + ")");
						}
					}
				}
			}
		}
	}

	@Test
	void everyFanStandsOnASeatFacingTheRoadInsideItsStandBox() {
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout lay = RaceCourseLayout.of(track);
			List<CourseStands.Stand> stands = lay.stands();
			List<RaceCourseLayout.FanPost> fans = lay.fanPosts();
			int covered = 0;
			for (CourseStands.Stand s : stands) {
				assertEquals(covered, s.firstFan(), track + " fan ranges are contiguous");
				assertTrue(s.fanCount() >= 3, track + " stand " + s.index() + " has " + s.fanCount() + " fans");
				covered += s.fanCount();
				for (int i = s.firstFan(); i < s.firstFan() + s.fanCount(); i++) {
					RaceCourseLayout.FanPost f = fans.get(i);
					assertEquals(s.index(), f.stand());
					assertTrue(f.x() >= s.minX() && f.x() <= s.maxX() && f.z() >= s.minZ() && f.z() <= s.maxZ()
							&& f.y() >= s.minY() && f.y() <= s.maxY(), track + " fan outside its stand box " + f);
					var seat = new RaceCourseLayout.Cell((int) Math.floor(f.x()), (int) Math.floor(f.y()), (int) Math.floor(f.z()));
					String block = lay.blocks().get(seat);
					assertTrue(block != null && block.contains("_stairs[facing="), track + " fan not on a seat: " + block + " at " + f);
					assertFalse(lay.onCourse(f.x(), f.z()), track + " fan on the road");
					// facing the track: a step toward where the fan looks brings it nearer the centre line
					double r = Math.toRadians(f.yaw());
					double lx = f.x() - Math.sin(r) * 4.0D, lz = f.z() + Math.cos(r) * 4.0D;
					double here = distToLine(track, f.x(), f.z()), there = distToLine(track, lx, lz);
					assertTrue(there < here, track + " fan faces away from the road " + f);
				}
			}
			assertEquals(fans.size(), covered, track + " every fan belongs to a stand");
			// the client helper and the layout agree
			assertEquals(fans, CourseStands.of(track).fanPosts(), track + " client crowd");
		}
	}

	@Test
	void standsStandOnTheIsland() {
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout lay = RaceCourseLayout.of(track);
			Set<Long> columns = new HashSet<>();
			int minY = Integer.MAX_VALUE;
			for (RaceCourseLayout.Cell c : lay.blocks().keySet()) {
				columns.add(RaceCourseLayout.roadKey(c.x(), c.z()));
			}
			for (CourseStands.Plan p : CourseStands.of(track).plans()) {
				for (CourseStands.Cell c : CourseStands.of(track).cells(p.index())) {
					// rock right under every seat column, so no stand floats over the void
					assertTrue(lay.blocks().containsKey(new RaceCourseLayout.Cell(c.x(), c.ground() - 1, c.z())),
							track + " stand " + p.index() + " has no island under (" + c.x() + ", " + c.z() + ")");
					minY = Math.min(minY, c.ground());
				}
			}
			assertTrue(minY > 50, track + " stand ground " + minY);
			assertFalse(columns.isEmpty());
		}
	}

	private static double distToLine(RaceTrack track, double x, double z) {
		double t = track.progressAt(x, z);
		RacePoint p = track.pointAt(t);
		return Math.hypot(p.x() - x, p.z() - z);
	}
}
