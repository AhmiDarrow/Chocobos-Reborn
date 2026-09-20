package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * What a colour is worth has to be about the same on every course. A feature laid on
 * a straight gives a detour that costs almost nothing, so the bird that can cross it
 * gains nothing for the ability; one laid on the outside of a big bend can cost a
 * tenth of a lap. Features are placed against {@link RaceTrack#detourTarget()} to
 * keep that even, and the boosts go on corner exits.
 */
class CourseBalanceTest {
	@Test
	void everyColourFeatureIsWorthAboutTheSame() {
		for (RaceTrack track : RaceTrack.values()) {
			double target = track.detourTarget();
			for (RaceTrack.Feature f : track.terrainFeatures()) {
				double cost = track.detourCost(f);
				if (f.type() == RaceTrack.Feature.Type.MUD) {
					continue;
				}
				assertTrue(cost >= 12.0D, track.name() + " " + f.type() + " detour is free (" + Math.round(cost)
						+ " blocks): the bird that can cross gains nothing");
				// the band is wider than it looks: a feature has to sit level on the hill
				// profile, on a bend, clear of the other features and of the boost strips.
				// S_STARFALL is the floor case (hills), S_KEEP the ceiling (four features)
				assertTrue(cost >= 0.55D * target && cost <= 1.3D * target, track.name() + " " + f.type() + " detour "
						+ Math.round(cost) + " blocks is out of step with this course's " + Math.round(target));
			}
		}
	}

	@Test
	void theBogIsTheCheapestThingToGoRound() {
		for (RaceTrack track : RaceTrack.values()) {
			RaceTrack.Feature bog = null;
			for (RaceTrack.Feature f : track.terrainFeatures()) {
				if (f.type() == RaceTrack.Feature.Type.MUD) {
					bog = f;
				}
			}
			if (bog == null) {
				continue;
			}
			// nobody suits a bog, so its detour is everyone's route: it must not be a toll
			double cost = track.detourCost(bog);
			for (RaceTrack.Feature f : track.terrainFeatures()) {
				if (f.type() != RaceTrack.Feature.Type.MUD) {
					assertTrue(cost < track.detourCost(f), track.name() + " bog detour (" + Math.round(cost)
							+ ") costs more than going round its " + f.type());
				}
			}
		}
	}

	@Test
	void boostsSitOnCornerExits() {
		for (RaceTrack track : RaceTrack.values()) {
			int boosts = 0;
			for (RaceTrack.Feature f : track.features()) {
				if (f.type() != RaceTrack.Feature.Type.BOOST) {
					continue;
				}
				boosts++;
				assertTrue(track.turnAhead(f.start(), 45.0D) < 0.7D, track.name() + " has a boost strip at "
						+ f.start() + " pointing into a corner");
				for (RaceTrack.Feature g : track.terrainFeatures()) {
					assertTrue(f.end() < g.start() - 0.012D || f.start() > g.end() + 0.012D, track.name()
							+ " has a boost strip inside the " + g.type() + " detour connector");
				}
			}
			assertEquals(track.getLaps() == 1 ? 3 : 5, boosts, track.name() + " boost count");
		}
	}
}
