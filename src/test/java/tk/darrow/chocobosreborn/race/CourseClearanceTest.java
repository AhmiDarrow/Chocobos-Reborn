package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import tk.darrow.chocobosreborn.entity.ChocoboEntity;

/**
 * Nothing solid stands in a racing bird's way. A bird held to {@link RacerLine#LANE_LIMIT}
 * is 1.75 wide, so its body reaches {@code LANE_LIMIT + 0.875} from the centre line; any
 * rail, rim, kerb wall, post or piece of scenery rasterised inside that at body height is a
 * wall in the racing line. The AI jumps off it ({@link RacerGoal}), but a rider (or the
 * harness bot) is pinned there: B_FORD's pool wall stepped a block into the band on a
 * bend and held a rider for 50 s.
 *
 * <p>The lap is sampled every half block, the lane every eighth of a block across the
 * band (and across the detour road and its connectors around a feature), and each sample's
 * column is checked from the surface up through the bird's height ({@link ChocoboEntity#ADULT_H}).
 * The road's own surface one step up a hill, and a ridge feature itself, are the course,
 * not an intrusion; water, lava, carpets, banners, plants and boost pads are not solid.
 */
class CourseClearanceTest {
	/** A bird's half width plus a hair, beyond the lane limit. */
	static final double BODY = 0.875D + 0.1D;
	/** How far across the band a bird's body reaches. */
	static final double REACH = RacerLine.LANE_LIMIT + BODY;
	/** The detour road's own lane limit: its half width less a block, like the band's. */
	static final double DETOUR_CENTRE = -(RaceTrack.DETOUR_INNER + RaceTrack.DETOUR_OUTER) / 2.0D;
	static final double DETOUR_REACH = (RaceTrack.DETOUR_OUTER - RaceTrack.DETOUR_INNER) / 2.0D - 1.0D + BODY;
	/** Blocks of body above the surface a bird stands on. */
	static final int HEIGHT = (int) Math.ceil(ChocoboEntity.ADULT_H);

	/** A block a bird's box collides with (the plan's ids, block state stripped). */
	static boolean solid(String block) {
		if (block == null) {
			return false;
		}
		String id = block.contains("[") ? block.substring(0, block.indexOf('[')) : block;
		if (id.equals("air") || id.equals("water") || id.equals("lava") || id.endsWith("boost_pad")) {
			return false;
		}
		if (id.equals("snow")) {
			return block.contains("layers=") && !block.contains("layers=1]") && !block.contains("layers=1,");
		}
		if (id.endsWith("_carpet") || id.endsWith("_banner") || id.endsWith("_sign")) {
			return false;
		}
		return !OPEN.contains(id);
	}

	/** Plants, flames and the like the plan uses: no collision box. */
	private static final Set<String> OPEN = Set.of("short_grass", "tall_grass", "fern", "large_fern", "dead_bush",
			"sweet_berry_bush", "pink_petals", "crimson_roots", "warped_roots", "glow_lichen", "sculk_vein", "fire",
			"soul_fire", "vine", "poppy", "dandelion", "azure_bluet", "blue_orchid", "cornflower", "oxeye_daisy",
			"torch", "wall_torch", "brown_mushroom", "red_mushroom", "sugar_cane", "seagrass", "kelp");

	private static boolean liquid(String block) {
		return "water".equals(block) || "lava".equals(block);
	}

	private static int surf(RaceTrack track, double t) {
		return (int) track.groundY(t) - 1;
	}

	private static int floor(double v) {
		return (int) Math.floor(v);
	}

	private static boolean near(RaceTrack track, double t) {
		for (RaceTrack.Feature f : track.terrainFeatures()) {
			if (t >= f.start() - RaceTrack.DETOUR_CONNECT && t <= f.end() + RaceTrack.DETOUR_CONNECT) {
				return true;
			}
		}
		return false;
	}

	/** How far round a connector is toward the detour lane (0 at the fork, 1 alongside the feature). */
	private static double blend(RaceTrack track, double t) {
		for (RaceTrack.Feature f : track.terrainFeatures()) {
			double from = f.start() - RaceTrack.DETOUR_CONNECT, to = f.end() + RaceTrack.DETOUR_CONNECT;
			if (t >= from && t <= to) {
				return t < f.start() ? (t - from) / RaceTrack.DETOUR_CONNECT
						: t > f.end() ? (to - t) / RaceTrack.DETOUR_CONNECT : 1.0D;
			}
		}
		return 0.0D;
	}

	/** Every solid cell in a racing bird's body, one line per cell: course, progress, lane, cell, block. */
	static List<String> intrusions(RaceTrack track) {
		return intrusions(track, RaceCourseLayout.of(track));
	}

	static List<String> intrusions(RaceTrack track, RaceCourseLayout layout) {
		Map<RaceCourseLayout.Cell, String> plan = layout.blocks();
		Set<RaceCourseLayout.Cell> surface = layout.surfaceCells();
		Map<RaceCourseLayout.Cell, String> hits = new LinkedHashMap<>();
		int steps = (int) Math.ceil(track.lapLength() * 2.0D);
		for (int i = 0; i < steps; i++) {
			double t = i / (double) steps;
			int s = surf(track, t);
			RaceTrack.Feature ft = track.terrainAt(t);
			boolean ridge = ft != null && ft.type() == RaceTrack.Feature.Type.RIDGE;
			boolean liquid = ft != null && (ft.type() == RaceTrack.Feature.Type.WATER || ft.type() == RaceTrack.Feature.Type.LAVA);
			// lanes a bird can hold here: the band, and round a feature the detour and the swing to it
			List<double[]> spans = new ArrayList<>();
			// feet on the surface; across water or lava a wading bird's feet are on the pool floor
			spans.add(new double[]{-REACH, REACH, ridge ? track.ridgeHeight() + 1 : liquid ? -1 : 1, 0});
			if (near(track, t)) {
				if (ft != null) {
					spans.add(new double[]{DETOUR_CENTRE - DETOUR_REACH, DETOUR_CENTRE + DETOUR_REACH, 1, 1});
				} else {
					// the connector: every lane between a lane of the band and a lane of the detour,
					// blended the way detourLaneAt swings a bird out and back
					double b = blend(track, t);
					double lo = (1.0D - b) * -RacerLine.LANE_LIMIT + b * (DETOUR_CENTRE - DETOUR_REACH + BODY) - BODY;
					double hi = (1.0D - b) * RacerLine.LANE_LIMIT + b * (DETOUR_CENTRE + DETOUR_REACH - BODY) + BODY;
					if (lo < -REACH) {
						spans.add(new double[]{lo, Math.min(hi, -REACH), 1, 2});
					}
				}
			}
			for (double[] span : spans) {
				int from = s + (int) span[2];
				for (double o = span[0]; o <= span[1] + 1.0E-9D; o += 0.125D) {
					RacePoint q = track.pointAtLane(t, o);
					int x = floor(q.x()), z = floor(q.z());
					for (int y = from; y < from + HEIGHT; y++) {
						RaceCourseLayout.Cell c = new RaceCourseLayout.Cell(x, y, z);
						String block = plan.get(c);
						if (!solid(block) || surface.contains(c) || hits.containsKey(c)) {
							continue;
						}
						if (y < s && !liquid(plan.get(new RaceCourseLayout.Cell(x, s, z)))) {
							continue;   // under the road a pool ends against: the step out of the pool, not a rim
						}
						String where = span[3] == 0 ? (ft == null ? "road" : ft.type().name().toLowerCase()) : span[3] == 1 ? "detour" : "connector";
						hits.put(c, String.format("%s t=%.4f %s lane=%+.3f %d %d %d (surf %d) %s", track.name(), t, where, o,
								x, y, z, s, block));
					}
				}
			}
		}
		return new ArrayList<>(hits.values());
	}

	/**
	 * The harness rider B_FORD pinned for 50 s: a Blue bird at progress 0.4633, 4.38 outside
	 * the line, in the ford (0.40-0.52), its west face against a pool wall that had stepped a
	 * block into the band on the bend. Its box there is clear now.
	 */
	@Test
	void theFordNoLongerPinsARider() {
		RaceCourseLayout layout = RaceCourseLayout.of(RaceTrack.B_FORD);
		double x = -1226.125D, y = 68.5D, z = 1618.72D, half = 0.875D + 0.05D;   // and what it pressed against
		for (int bx = floor(x - half); bx <= floor(x + half); bx++) {
			for (int bz = floor(z - half); bz <= floor(z + half); bz++) {
				for (int by = floor(y); by <= floor(y + ChocoboEntity.ADULT_H); by++) {
					String block = layout.blocks().get(new RaceCourseLayout.Cell(bx, by, bz));
					assertTrue(!solid(block), "B_FORD still has " + block + " at " + bx + " " + by + " " + bz);
				}
			}
		}
	}

	@Test
	void nothingSolidStandsInTheRacingLanes() {
		List<String> all = new ArrayList<>();
		for (RaceTrack track : RaceTrack.values()) {
			List<String> hits = intrusions(track);
			if (!hits.isEmpty()) {
				System.out.println(track.name() + ": " + hits.size() + " solid cells in the racing lanes");
				hits.stream().limit(20).forEach(h -> System.out.println("  " + h));
			}
			all.addAll(hits);
		}
		System.out.println("CLEARANCE total intrusions: " + all.size());
		try {
			java.nio.file.Files.write(java.nio.file.Path.of("build", "course-clearance.txt"), all);
		} catch (java.io.IOException ignored) {
			// the report is a convenience; the assertion below is the test
		}
		assertTrue(all.isEmpty(), all.size() + " solid cells in the racing lanes; first: " + (all.isEmpty() ? "" : all.get(0)));
	}
}
