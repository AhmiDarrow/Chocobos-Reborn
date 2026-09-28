package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Every theme is ready to race before a course wears it: the four 48-course themes
 * (FARMLAND, SAVANNA, MUSHROOM, DEEP_DARK) arrived in phase 1 with no course of their
 * own, so here each is laid over existing courses of its class that carry every terrain
 * feature the class uses ({@code RaceCourseLayout.dressedAs}) and must build a sealed,
 * dressed island with its own set piece. Every theme and course also has its lang keys.
 */
class CourseThemeTest {
	/** Theme -> courses of its class to dress in it (between them, every feature the class has) and its set piece's blocks. */
	private static final Map<RaceTrack.Theme, List<RaceTrack>> PREVIEWS = Map.of(
			RaceTrack.Theme.FARMLAND, List.of(RaceTrack.C_DOWNS, RaceTrack.C_ORCHARD),
			RaceTrack.Theme.SAVANNA, List.of(RaceTrack.B_RAPIDS, RaceTrack.B_MESA),
			RaceTrack.Theme.MUSHROOM, List.of(RaceTrack.A_DEEPS, RaceTrack.A_INFERNO),
			RaceTrack.Theme.DEEP_DARK, List.of(RaceTrack.S_MAELSTROM, RaceTrack.S_CITADEL));

	private static final Map<RaceTrack.Theme, List<String>> SET_PIECE = Map.of(
			RaceTrack.Theme.FARMLAND, List.of("carved_pumpkin[facing=north]", "hay_block[axis=x]"),   // scarecrow
			RaceTrack.Theme.SAVANNA, List.of("acacia_wood", "packed_mud"),                              // great acacia
			RaceTrack.Theme.MUSHROOM, List.of("ochre_froglight", "red_mushroom_block"),                 // giant mushroom
			RaceTrack.Theme.DEEP_DARK, List.of("reinforced_deepslate", "sculk_catalyst"));              // warden frame

	@Test
	void sixteenThemesFourPerClass() {
		assertEquals(16, RaceTrack.Theme.values().length);
		assertEquals(16, java.util.Arrays.stream(RaceTrack.Theme.values()).map(t -> t.road + t.kerbA + t.ground + t.post).distinct().count(),
				"every theme has its own palette");
	}

	@Test
	void theNewThemesLayASealedDressedIsland() {
		for (Map.Entry<RaceTrack.Theme, List<RaceTrack>> e : PREVIEWS.entrySet()) {
			RaceTrack.Theme theme = e.getKey();
			Set<RaceTrack.Feature.Type> covered = new HashSet<>();
			for (RaceTrack track : e.getValue()) {
				String what = theme + " on " + track.name();
				RaceCourseLayout lay = RaceCourseLayout.dressedAs(track, theme);
				Set<String> laid = new HashSet<>(lay.blocks().values());
				assertTrue(lay.blocks().size() > 20_000, what + " dressed");
				for (String b : List.of(theme.road, theme.kerbA, theme.kerbB, theme.wall, theme.ground, theme.base, theme.post, theme.lamp)) {
					assertTrue(laid.contains(b), what + " lays no " + b);
				}
				for (String b : SET_PIECE.get(theme)) {
					assertTrue(laid.contains(b), what + " is missing its set piece: no " + b);
				}
				List<String> leaks = CourseLiquidTest.leaks(track, lay);
				assertTrue(leaks.isEmpty(), what + ": " + leaks.size() + " road cells get wet; first: " + (leaks.isEmpty() ? "" : leaks.get(0)));
				// the road plan is the course's, whatever it wears
				assertEquals(RaceCourseLayout.of(track).road(), lay.road(), what + " road");
				assertEquals(RaceCourseLayout.of(track).fanPosts().size(), lay.fanPosts().size(), what + " crowd");
				for (RaceTrack.Feature f : track.terrainFeatures()) {
					covered.add(f.type());
				}
			}
			Set<RaceTrack.Feature.Type> classUses = new HashSet<>();
			for (RaceTrack t : RaceTrack.ofClass(e.getValue().get(0).getRaceClass())) {
				for (RaceTrack.Feature f : t.terrainFeatures()) {
					classUses.add(f.type());
				}
			}
			assertEquals(classUses, covered, theme + " is previewed with every terrain feature its class races");
		}
		assertEquals(4, PREVIEWS.size());
	}

	@Test
	void everyThemeHasMusicAndEveryCourseAndThemeHasItsName() throws IOException {
		String lang = Files.readString(Path.of("src/main/resources/assets/chocobosreborn/lang/en_us.json"));
		for (RaceTrack.Theme theme : RaceTrack.Theme.values()) {
			assertNotNull(RaceScoring.raceLoopKey(theme), theme + " music");
			assertTrue(lang.contains("\"chocobosreborn.select.theme." + theme.name().toLowerCase(Locale.ROOT) + "\""), theme + " picker name");
		}
		for (RaceTrack track : RaceTrack.values()) {
			assertTrue(lang.contains("\"chocobosreborn.track." + track.id() + "\""), track + " name");
		}
		for (String cls : List.of("c", "b", "a", "s")) {
			assertTrue(lang.contains("\"_anchor.track." + cls + ".begin\""), "lang anchor for new " + cls + " course names");
		}
	}
}
