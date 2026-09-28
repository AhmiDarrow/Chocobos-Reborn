package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Courses share themes (a grand prix and a sprint on each original theme, more once the
 * 48-course plan lands), so the landmark is what tells them apart from the saddle. Each
 * course has its own set piece ({@code RaceCourseLayout.landmarkC} .. {@code landmarkS})
 * and lays its own signature blocks, no two courses have the same signature, and no two
 * courses of a theme share a silhouette or a block palette.
 */
class CourseIdentityTest {
	/**
	 * Blocks only this course's set piece lays among the courses of its theme. A new course
	 * adds its case in its class's region (the switch has to cover every course, so a
	 * course without one does not compile).
	 */
	static List<String> signatureOf(RaceTrack track) {
		return switch (track) {
			// ---- C
			case C_MEADOW -> List.of("oak_log");
			case C_ORCHARD -> List.of("cherry_log");
			case C_SHORE -> List.of("white_concrete", "sea_lantern");
			case C_DOWNS -> List.of("white_wool", "cobblestone");            // windmill
			case C_CIDER -> List.of("barrel[facing=up]", "hay_block[axis=y]");  // cider barn
			case C_LAGOON -> List.of("oak_log[axis=y]", "white_wool");        // shipwreck
			// ---- new C signatures (phase 2) begin ----
			// ---- new C signatures (phase 2) end ----
			// ---- B
			case B_CANYON -> List.of("yellow_terracotta", "red_terracotta");  // hoodoo
			case B_FORD -> List.of("mossy_cobblestone", "water");            // cairn with a spring
			case B_FROST -> List.of("packed_ice");
			case B_MESA -> List.of("yellow_terracotta", "brown_terracotta");  // arch
			case B_RAPIDS -> List.of("stripped_spruce_log[axis=x]");          // mill wheel
			case B_GLACIER -> List.of("blue_ice", "snow[layers=4]");          // frozen fall
			// ---- new B signatures (phase 2) begin ----
			case B_ACACIA -> List.of("acacia_wood", "packed_mud");                       // great acacia (the SAVANNA piece)
			case B_GULCH -> List.of("cut_red_sandstone", "chiseled_red_sandstone");      // balanced rock
			case B_OXBOW -> List.of("stripped_oak_log[axis=y]", "lantern[hanging=true]"); // stilt hut
			case B_BAOBAB -> List.of("stripped_jungle_wood", "jungle_leaves[persistent=true]");
			case B_KOPJE -> List.of("granite", "polished_granite");
			case B_SNOWCAP -> List.of("coal_block", "black_wool");                       // snowman
			// ---- new B signatures (phase 2) end ----
			// ---- A
			case A_CRYSTAL -> List.of("amethyst_block");
			case A_CANOPY -> List.of("mossy_stone_bricks", "gold_block");   // step pyramid
			case A_EMBER -> List.of("nether_bricks");
			case A_DEEPS -> List.of("dripstone_block", "amethyst_cluster[facing=up]");
			case A_TEMPLE -> List.of("chiseled_stone_bricks", "moss_block");  // idol
			case A_INFERNO -> List.of("bone_block[axis=y]", "soul_fire");     // bone arch
			// ---- new A signatures (phase 2) begin ----
			// ---- new A signatures (phase 2) end ----
			// ---- S
			case S_SKYWAY -> List.of("magenta_stained_glass");
			case S_KEEP -> List.of("soul_lantern[hanging=true]");
			case S_VOID -> List.of("obsidian", "bedrock");
			case S_STARFALL -> List.of("cyan_stained_glass", "quartz_pillar[axis=y]");   // halo
			case S_CITADEL -> List.of("red_wool", "chiseled_polished_blackstone");       // gatehouse
			case S_MAELSTROM -> List.of("iron_bars", "magenta_stained_glass");           // caged crystal
			// ---- new S signatures (phase 2) begin ----
			// ---- new S signatures (phase 2) end ----
		};
	}

	@Test
	void everyCourseHasItsOwnLandmark() {
		for (RaceTrack track : RaceTrack.values()) {
			Set<String> piece = RaceCourseLayout.of(track).landmarkBlocks();
			for (String block : signatureOf(track)) {
				assertTrue(piece.contains(block),
						track.name() + " is missing its landmark: its set piece lays no " + block + " (it lays " + piece + ")");
			}
		}
	}

	/** No two courses share a signature or build the same set piece (same blocks in the same places). */
	@Test
	void noTwoCoursesShareALandmark() {
		List<String> bad = new java.util.ArrayList<>();
		RaceTrack[] all = RaceTrack.values();
		for (int i = 0; i < all.length; i++) {
			assertFalse(signatureOf(all[i]).isEmpty(), all[i] + " has a signature");
			for (int j = i + 1; j < all.length; j++) {
				if (new HashSet<>(signatureOf(all[i])).equals(new HashSet<>(signatureOf(all[j])))) {
					bad.add(all[i] + " and " + all[j] + " have the same landmark signature");
				}
				if (RaceCourseLayout.of(all[i]).landmarkShape().equals(RaceCourseLayout.of(all[j]).landmarkShape())) {
					bad.add(all[i] + " and " + all[j] + " build the same set piece");
				}
			}
		}
		assertTrue(bad.isEmpty(), String.join("; ", bad));
	}

	/** Two courses of one theme: different silhouettes and a different block palette on the island. */
	@Test
	void coursesOfAThemeAreNotTheSameCourse() {
		RaceTrack[] all = RaceTrack.values();
		for (int i = 0; i < all.length; i++) {
			for (int j = i + 1; j < all.length; j++) {
				RaceTrack a = all[i], b = all[j];
				if (a.theme() != b.theme()) {
					continue;
				}
				assertTrue(a.shape() != b.shape(), a + " and " + b + " share a shape");
				Set<String> la = new HashSet<>(RaceCourseLayout.of(a).blocks().values());
				Set<String> lb = new HashSet<>(RaceCourseLayout.of(b).blocks().values());
				assertFalse(la.equals(lb), a + " and " + b + " are built from the same blocks");
			}
		}
	}
}
