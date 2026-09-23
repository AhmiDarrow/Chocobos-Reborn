package tk.darrow.chocobosreborn.race;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No course liquid reaches the road. The islands are laid without block updates, so a
 * leak sits still until something touches it mid-heat, then runs onto the road and
 * washes the boost pads out as items. This floods every water and lava source of every
 * plan (the plan is the whole island; everything else is sky) the way vanilla fluid
 * spreads: straight down while it can fall, otherwise sideways up to 7 blocks (lava 3),
 * into anything that is not a full block.
 */
class CourseLiquidTest {
	/** Blocks fluid flows into or through: air, and the plan's non-full and waterloggable pieces. */
	private static boolean open(String block) {
		if (block == null || block.equals("air")) {
			return true;
		}
		String id = block.contains("[") ? block.substring(0, block.indexOf('[')) : block;
		if (id.equals("water") || id.equals("lava")) {
			return false;   // already liquid: the source's own business
		}
		for (String part : new String[]{"fence", "_wall", "slab", "stairs", "pane", "bars", "carpet", "banner", "torch",
				"lantern", "poppy", "dandelion", "daisy", "bush", "pickle", "campfire", "cluster", "pointed_dripstone",
				"rail", "button", "sign", "sapling", "grass", "fern", "vine", "coral", "leaves", "chain", "ladder",
				"lever", "boost_pad", "flower", "tulip", "orchid", "allium", "bluet", "cornflower", "lily", "kelp",
				"seagrass", "candle", "pressure_plate", "trapdoor", "door", "gate", "composter", "scaffolding"}) {
			if (id.contains(part)) {
				return true;
			}
		}
		return id.equals("snow") || id.equals("sugar_cane") || id.equals("cactus");
	}

	private static boolean liquid(String block) {
		return block != null && (block.equals("water") || block.equals("lava") || block.contains("waterlogged=true"));
	}

	/** Where each course's liquid would get to that it must not: road tiles outside its own sources. */
	static List<String> leaks(RaceTrack track) {
		RaceCourseLayout layout = RaceCourseLayout.of(track);
		Map<RaceCourseLayout.Cell, String> plan = layout.blocks();
		Set<RaceCourseLayout.Tile> road = layout.road();
		Map<RaceCourseLayout.Cell, Integer> reach = new HashMap<>();
		ArrayDeque<Object[]> queue = new ArrayDeque<>();
		for (Map.Entry<RaceCourseLayout.Cell, String> e : plan.entrySet()) {
			if (liquid(e.getValue())) {
				int spread = e.getValue().equals("lava") ? 3 : 7;
				reach.put(e.getKey(), spread + 1);
				queue.add(new Object[]{e.getKey(), spread, spread, e.getKey()});
			}
		}
		List<String> out = new ArrayList<>();
		int minY = plan.keySet().stream().mapToInt(RaceCourseLayout.Cell::y).min().orElse(0) - 1;
		while (!queue.isEmpty()) {
			Object[] q = queue.poll();
			RaceCourseLayout.Cell c = (RaceCourseLayout.Cell) q[0];
			int left = (int) q[1], full = (int) q[2];
			RaceCourseLayout.Cell below = new RaceCourseLayout.Cell(c.x(), c.y() - 1, c.z());
			if (below.y() < minY) {
				continue;   // off the island into the void
			}
			List<RaceCourseLayout.Cell> next = new ArrayList<>();
			if (open(plan.get(below))) {
				next.add(below);
				left = full;   // a falling column lands as a fresh spread
			} else if (left > 0) {
				left--;
				next.add(new RaceCourseLayout.Cell(c.x() + 1, c.y(), c.z()));
				next.add(new RaceCourseLayout.Cell(c.x() - 1, c.y(), c.z()));
				next.add(new RaceCourseLayout.Cell(c.x(), c.y(), c.z() + 1));
				next.add(new RaceCourseLayout.Cell(c.x(), c.y(), c.z() - 1));
			}
			for (RaceCourseLayout.Cell n : next) {
				if (!open(plan.get(n))) {
					continue;
				}
				Integer seen = reach.get(n);
				if (seen != null && seen >= left + 1) {
					continue;
				}
				reach.put(n, left + 1);
				queue.add(new Object[]{n, left, full, q[3]});
				if (road.contains(new RaceCourseLayout.Tile(n.x(), n.z()))) {
					RaceCourseLayout.Cell src = (RaceCourseLayout.Cell) q[3];
					out.add(String.format("%s: %s from %d %d %d (t=%.3f) reaches the road at %d %d %d", track.name(),
							plan.get(src), src.x(), src.y(), src.z(), track.progressAt(src.x() + 0.5D, src.z() + 0.5D),
							n.x(), n.y(), n.z()));
				}
			}
		}
		return out;
	}

	@Test
	void noCourseLiquidReachesTheRoad() {
		List<String> all = new ArrayList<>();
		for (RaceTrack track : RaceTrack.values()) {
			List<String> l = leaks(track);
			if (!l.isEmpty()) {
				System.out.println(track.name() + ": " + l.size() + " road cells wet");
				l.stream().map(x -> x.substring(0, x.indexOf(" reaches"))).distinct().limit(12).forEach(x -> System.out.println("  " + x));
			}
			all.addAll(l);
		}
		assertTrue(all.isEmpty(), all.size() + " road cells get wet; first: " + (all.isEmpty() ? "" : all.get(0)));
	}

	/**
	 * The relay's clear ({@link RaceCourseLayout#clearChunks}) covers the whole new island
	 * and never reaches another island's blocks or the village the paddock rebuild scrubs.
	 */
	@Test
	void islandClearStaysOnItsOwnIsland() {
		Map<Long, RaceTrack> owner = new HashMap<>();
		for (RaceTrack track : RaceTrack.values()) {
			for (long key : RaceCourseLayout.of(track).chunks()) {
				RaceTrack prev = owner.put(key, track);
				assertTrue(prev == null, "chunk shared by " + prev + " and " + track);
			}
		}
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout layout = RaceCourseLayout.of(track);
			Set<Long> clear = layout.clearChunks();
			for (long key : clear) {
				RaceTrack o = owner.get(key);
				assertTrue(o == null || o == track, track + " would clear a chunk of " + o);
				assertTrue(!RaceCourseLayout.nearVillage((int) (key >> 32), (int) key), track + " would clear near the village");
			}
			for (long key : layout.chunks()) {
				assertTrue(clear.contains(key), track + " leaves a chunk of its own island uncleared: "
						+ (key >> 32) + ", " + (int) key);
			}
			assertTrue(clear.size() > layout.chunks().size(), track + " clears a ring for an older, wider plan");
		}
	}
}
