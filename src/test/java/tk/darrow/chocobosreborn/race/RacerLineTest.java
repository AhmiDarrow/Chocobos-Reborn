package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import tk.darrow.chocobosreborn.breed.ChocoboColor;

/** The AI's line: rails, passing, boost strips, parking, stumbles, and which birds take which shortcut. */
class RacerLineTest {
	private static final double BIRD_HALF = 0.875D;

	@Test
	void anAimedLaneNeverTouchesTheRail() {
		// outside rail (and a corner's inside rail) stands on the kerb at ROAD_HALF + 1
		assertTrue(RacerLine.LANE_LIMIT + BIRD_HALF < RaceTrack.ROAD_HALF + 1.0D - 0.25D);
		assertEquals(RacerLine.LANE_LIMIT, RacerLine.clampLane(9.0D), 1e-12);
		assertEquals(-RacerLine.LANE_LIMIT, RacerLine.clampLane(-9.0D), 1e-12);
		assertEquals(1.2D, RacerLine.clampLane(1.2D), 1e-12);
		// 1.0.18: an outer C bird (stall -4.5, wobble ~1) swinging 2.2 outward to pass aimed at -7.7, through the fence
		assertTrue(RacerLine.clampLane(-4.5D - 1.0D - RacerLine.PASS_OFFSET) >= -RacerLine.LANE_LIMIT);
	}

	@Test
	void passingGoesRoundTheSlowBirdOnTheSideWithRoom() {
		assertEquals(1.0D, RacerLine.passSide(0.0D, -1.0D), "slow bird outside: go inside");
		assertEquals(-1.0D, RacerLine.passSide(0.0D, 1.0D), "slow bird inside: go outside");
		// hard against the outside with the slow bird inside of us: no room outside, so go inside
		assertEquals(1.0D, RacerLine.passSide(-RacerLine.LANE_LIMIT + 0.5D, -RacerLine.LANE_LIMIT + 1.0D));
		assertEquals(-1.0D, RacerLine.passSide(RacerLine.LANE_LIMIT - 0.5D, RacerLine.LANE_LIMIT - 1.0D));
	}

	@Test
	void aFinishedBirdParksOffTheRacingLine() {
		assertTrue(Math.abs(RacerLine.PARK_LANE - RacerLine.INSIDE_LINE) >= 3.0D);
		assertTrue(Math.abs(RacerLine.PARK_LANE) <= RacerLine.LANE_LIMIT);
	}

	@Test
	void stumblesArePerLapWhateverTheLapLength() {
		// 1.0.18 rolled perLap / 900 each tick: a 1600-block lap at C pace (~3600 ticks) got four times the rate
		for (double lap : new double[]{600.0D, 1600.0D}) {
			for (double perTick : new double[]{0.4D, 1.3D}) {
				double sum = 0.0D;
				for (double run = 0.0D; run < lap; run += perTick) {
					sum += RacerLine.stumbleChance(0.6D, perTick, lap);
				}
				assertEquals(0.6D, sum, 0.01D, lap + " / " + perTick);
			}
		}
		assertEquals(0.0D, RacerLine.stumbleChance(0.6D, 0.0D, 600.0D), 1e-12, "a bird that is not moving does not stumble");
	}

	@Test
	void boostAimHitsItsRateAndIsRepeatable() {
		int hits = 0, n = 0;
		for (long seed = 1; seed <= 400; seed++) {
			for (int strip = 0; strip < 5; strip++) {
				if (RacerLine.aimsForBoost(0.65D, seed * 7919L, strip, 1)) hits++;
				n++;
			}
		}
		assertEquals(0.65D, hits / (double) n, 0.05D);
		assertEquals(RacerLine.aimsForBoost(0.5D, 42L, 3, 2), RacerLine.aimsForBoost(0.5D, 42L, 3, 2));
		assertTrue(RacerLine.aimsForBoost(1.0D, 42L, 3, 2));
		assertFalse(RacerLine.aimsForBoost(0.0D, 42L, 3, 2));
	}

	@Test
	void everyBoostStripHasALaneTheAiCanAimFor() {
		// strips cover one 3-block lane each; the AI aims at -3 / 0 / +3 and must land inside the pads
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout layout = RaceCourseLayout.of(track);
			for (RaceTrack.Feature f : track.features()) {
				if (f.type() != RaceTrack.Feature.Type.BOOST) continue;
				int lanes = 0;
				for (double o : new double[]{-3.0D, 0.0D, 3.0D}) {
					if (layout.boostLane(f, o)) {
						lanes++;
						assertTrue(Math.abs(o) <= RacerLine.LANE_LIMIT);
						// the whole footprint sits on pads
						assertTrue(layout.boostLane(f, o - BIRD_HALF + 0.3D) && layout.boostLane(f, o + BIRD_HALF - 0.3D), track + " " + f);
					}
				}
				assertEquals(1, lanes, track + " " + f);
			}
		}
	}

	@Test
	void eachColourTakesTheShortcutsItIsEntitledToAndOnlyThose() {
		for (RaceTrack track : RaceTrack.values()) {
			for (RaceTrack.Feature f : track.terrainFeatures()) {
				double mid = (f.start() + f.end()) / 2.0D;
				for (ChocoboColor c : ChocoboColor.values()) {
					boolean entitled = switch (f.type()) {
						case WATER -> c == ChocoboColor.BLUE || c == ChocoboColor.WHITE || c == ChocoboColor.BLACK
								|| c == ChocoboColor.GOLD || c == ChocoboColor.PURPLE;
						case RIDGE -> c == ChocoboColor.GREEN || c == ChocoboColor.WHITE || c == ChocoboColor.BLACK
								|| c == ChocoboColor.GOLD || c == ChocoboColor.PURPLE;
						case LAVA -> c == ChocoboColor.FLAME || c == ChocoboColor.GOLD;
						case MUD, BOOST -> false;
					};
					double lane = track.detourLaneAt(mid, RacerLine.INSIDE_LINE, c, true);
					String what = track + " " + f.type() + " " + c;
					if (entitled) {
						assertEquals(RacerLine.INSIDE_LINE, lane, 1e-9, what + " should go straight through");
					} else {
						assertTrue(lane < -RaceTrack.DETOUR_INNER, what + " should take the detour");
					}
				}
			}
		}
	}

	@Test
	void theRivalsColoursUseTheirShortcuts() {
		// Teiyo rides Black (ridges, water), Jolo Blue / White / Gold by class
		assertTrue(new RaceTrack.Feature(RaceTrack.Feature.Type.RIDGE, 0.1, 0.2).suits(ChocoboColor.BLACK));
		assertTrue(new RaceTrack.Feature(RaceTrack.Feature.Type.WATER, 0.1, 0.2).suits(RaceScoring.joloColor(RaceClass.B)));
		assertTrue(new RaceTrack.Feature(RaceTrack.Feature.Type.RIDGE, 0.1, 0.2).suits(RaceScoring.joloColor(RaceClass.A)));
		assertTrue(new RaceTrack.Feature(RaceTrack.Feature.Type.LAVA, 0.1, 0.2).suits(RaceScoring.joloColor(RaceClass.S)));
		assertFalse(new RaceTrack.Feature(RaceTrack.Feature.Type.LAVA, 0.1, 0.2).suits(ChocoboColor.BLACK));
	}
}
