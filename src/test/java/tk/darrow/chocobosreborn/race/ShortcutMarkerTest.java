package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Every terrain shortcut is marked where the road forks (Ahmi: "I have no idea where any are"). */
class ShortcutMarkerTest {
	@Test
	void everyTerrainFeatureHasAGantrySignAndAStripe() {
		int features = 0;
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout layout = RaceCourseLayout.of(track);
			List<RaceTrack.Feature> terrain = track.terrainFeatures();
			features += terrain.size();
			assertEquals(terrain.size(), layout.shortcutSigns().size(), track.name() + " one sign per shortcut");
			for (RaceTrack.Feature f : terrain) {
				String stripe = RaceCourseLayout.shortcutColour(f.type()) + "_concrete";
				double t = f.start() - RaceTrack.DETOUR_CONNECT - 10.0D / track.lapLength();
				t -= Math.floor(t);
				boolean found = false;
				for (int back = 0; back < 8 && !found; back++) {
					double tt = t - back * 0.5D / track.lapLength();
					RacePoint q = track.pointAt(tt - Math.floor(tt));
					int y = (int) track.groundY(tt - Math.floor(tt)) - 1;
					found = stripe.equals(layout.blocks().get(new RaceCourseLayout.Cell((int) Math.floor(q.x()), y, (int) Math.floor(q.z()))));
				}
				assertTrue(found, track.name() + " " + f.type() + " stripe on the approach");
			}
		}
		assertTrue(features > 20, "the ladder has shortcuts to mark: " + features);
	}

	@Test
	void bannersNameTheBreedsThatTakeItStraight() {
		for (RaceTrack track : RaceTrack.values()) {
			for (RaceTrack.Feature f : track.terrainFeatures()) {
				List<String> b = RaceCourseLayout.shortcutBanners(f);
				switch (f.type()) {
					case WATER -> assertTrue(b.contains("blue") && b.contains("yellow") && !b.contains("green"), b.toString());
					case RIDGE -> assertTrue(b.contains("green") && b.contains("yellow") && !b.contains("blue"), b.toString());
					case LAVA -> assertEquals(List.of("yellow", "red"), b);
					case MUD -> assertEquals(List.of("brown"), b);
					default -> {
					}
				}
			}
		}
	}

	@Test
	void theHeadsUpComesBeforeTheFork() {
		assertEquals(20.0D, RaceScoring.blocksAhead(0.10D, 0.12D, 1000.0D), 1.0E-9);
		assertEquals(10.0D, RaceScoring.blocksAhead(0.995D, 0.005D, 1000.0D), 1.0E-9, "across the line");
		assertTrue(RaceScoring.blocksAhead(0.13D, 0.12D, 1000.0D) < 0.0D, "just past is behind, not a lap ahead");
	}
}
