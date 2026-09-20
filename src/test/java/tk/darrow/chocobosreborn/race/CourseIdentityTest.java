package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Two courses share a theme — the sprint and the grand prix — so the landmark is what
 * tells them apart from the saddle. Each of the twenty-four has its own.
 */
class CourseIdentityTest {
	private static List<String> signatureOf(RaceTrack track) {
		return switch (track) {
			// sprints: the set piece each theme has always had
			case C_MEADOW -> List.of("oak_log");
			case C_ORCHARD -> List.of("cherry_log");
			case C_SHORE -> List.of("white_concrete", "sea_lantern");
			case B_CANYON -> List.of("yellow_terracotta");
			case B_FORD -> List.of("mossy_cobblestone");
			case B_FROST -> List.of("packed_ice");
			case A_CRYSTAL -> List.of("amethyst_block");
			case A_CANOPY -> List.of("mossy_cobblestone");
			case A_EMBER -> List.of("nether_bricks");
			case S_SKYWAY -> List.of("magenta_stained_glass");
			case S_KEEP -> List.of("soul_lantern[hanging=true]");
			case S_VOID -> List.of("obsidian", "bedrock");
			// grand prix: a second set piece per theme, so no two courses look alike
			case C_DOWNS -> List.of("white_wool", "cobblestone");            // windmill
			case C_CIDER -> List.of("barrel[facing=up]", "hay_block[axis=y]");  // cider barn
			case C_LAGOON -> List.of("oak_log[axis=y]", "white_wool");        // shipwreck
			case B_MESA -> List.of("yellow_terracotta", "brown_terracotta");  // arch
			case B_RAPIDS -> List.of("stripped_spruce_log[axis=x]");          // mill wheel
			case B_GLACIER -> List.of("blue_ice", "snow[layers=4]");          // frozen fall
			case A_DEEPS -> List.of("dripstone_block", "amethyst_cluster[facing=up]");
			case A_TEMPLE -> List.of("chiseled_stone_bricks", "moss_block");  // idol
			case A_INFERNO -> List.of("bone_block[axis=y]", "soul_fire");     // bone arch
			case S_STARFALL -> List.of("cyan_stained_glass", "quartz_pillar[axis=y]");   // halo
			case S_CITADEL -> List.of("red_wool", "chiseled_polished_blackstone");       // gatehouse
			case S_MAELSTROM -> List.of("iron_bars", "magenta_stained_glass");           // caged crystal
		};
	}

	@Test
	void everyCourseHasItsOwnLandmark() {
		for (RaceTrack track : RaceTrack.values()) {
			Set<String> laid = new HashSet<>(RaceCourseLayout.of(track).blocks().values());
			for (String block : signatureOf(track)) {
				assertTrue(laid.contains(block),
						track.name() + " is missing its landmark: no " + block + " on the island");
			}
		}
	}

	@Test
	void theSprintAndTheGrandPrixOfAThemeAreNotTheSameCourse() {
		for (RaceTrack sprint : RaceTrack.values()) {
			if (!sprint.isShort()) {
				continue;
			}
			for (RaceTrack grand : RaceTrack.values()) {
				if (grand.isShort() || grand.theme() != sprint.theme()) {
					continue;
				}
				assertTrue(sprint.shape() != grand.shape(), sprint + " and " + grand + " share a shape");
				Set<String> a = new HashSet<>(RaceCourseLayout.of(sprint).blocks().values());
				Set<String> b = new HashSet<>(RaceCourseLayout.of(grand).blocks().values());
				assertTrue(!a.equals(b), sprint + " and " + grand + " are built from the same blocks");
			}
		}
	}
}
