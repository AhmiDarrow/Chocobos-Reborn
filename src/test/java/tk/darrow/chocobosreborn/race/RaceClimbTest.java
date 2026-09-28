package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * In a race a climber climbs the course's ridge and nothing else. On A_CRYSTAL a Black
 * bird at dash pace swerved into the rail just past the start straight, climbed it
 * (onClimbable was "climbs and pressed on something"), and the server's replay of the
 * rider's vehicle, which does not climb, reset it every tick: 600 vehicle corrections and
 * a bird frozen on the rail.
 */
class RaceClimbTest {
	@Test
	void theRuleClimbsOnlyTheRidgeInARace() {
		assertTrue(RaceScoring.mayClimb(true, true, false, false), "outside a race a climber climbs anything");
		assertTrue(RaceScoring.mayClimb(true, true, true, true), "the ridge in a race");
		assertFalse(RaceScoring.mayClimb(true, true, true, false), "a rail, a wall or a stand in a race");
		assertFalse(RaceScoring.mayClimb(false, true, false, true), "a colour that does not climb");
		assertFalse(RaceScoring.mayClimb(true, false, true, true), "nothing to climb");
	}

	@Test
	void noBirdStepsOntoTheRailInARace() {
		assertTrue(RaceScoring.stepHeight(true, 2.0F) < 1.5F, "a climbing colour steps two blocks, a rail stands 1.5");
		assertTrue(RaceScoring.stepHeight(true, 1.0F) >= 1.0F, "the road's hill steps are one block");
		assertTrue(RaceScoring.stepHeight(true, 1.0F) >= 1.0F + 1.0F / 16.0F, "from a dirt-path road up onto a concrete kerb or paint a block higher");
		assertTrue(RaceScoring.stepHeight(true, 1.0F) < 1.5F, "never onto the rail");
		assertTrue(RaceScoring.stepHeight(false, 2.0F) == 2.0F, "off the course the colour keeps its step");
	}

	@Test
	void theRidgeBandIsTheRidgeFeatureAcrossTheRoad() {
		RaceTrack track = RaceTrack.A_CRYSTAL;
		RaceTrack.Feature ridge = track.terrainFeatures().stream()
				.filter(f -> f.type() == RaceTrack.Feature.Type.RIDGE).findFirst().orElseThrow();
		double mid = (ridge.start() + ridge.end()) / 2.0D, lap = track.lapLength();
		assertTrue(track.ridgeBandAt(mid, 0.0D));
		assertTrue(track.ridgeBandAt(ridge.start() - 1.0D / lap, 3.0D), "a bird's nose meets the face before its centre does");
		assertFalse(track.ridgeBandAt(mid, RaceTrack.ROAD_HALF + 1.0D), "the rail beside the ridge");
		assertFalse(track.ridgeBandAt(mid, -12.5D), "the detour round it");
		assertFalse(track.ridgeBandAt(ridge.start() - 10.0D / lap, 0.0D), "the road before it");
		// where the Black bird went up the rail: progress 0.12-0.15, past the start straight
		double t = track.progressAt(-1985.57D, 2466.66D);
		assertFalse(track.ridgeBandAt(t, track.laneAt(t, -1985.57D, 2466.66D)), "A_CRYSTAL at t=" + t);
		for (double s = 0.10D; s <= 0.16D; s += 0.005D) {
			for (double lane = -7.0D; lane <= 7.0D; lane += 0.5D) {
				assertFalse(track.ridgeBandAt(s, lane));
			}
		}
	}

	@Test
	void laneAtReadsBackPointAtLane() {
		for (RaceTrack track : new RaceTrack[]{RaceTrack.A_CRYSTAL, RaceTrack.B_FORD, RaceTrack.S_MAELSTROM}) {
			for (double t = 0.0D; t < 1.0D; t += 0.0371D) {
				for (double lane : new double[]{-5.0D, -1.5D, 0.0D, 2.0D, 4.5D}) {
					RacePoint p = track.pointAtLane(t, lane);
					double back = track.laneAt(t, p.x(), p.z());
					assertTrue(Math.abs(back - lane) < 1.0E-6D, track + " t=" + t + " lane " + lane + " read " + back);
				}
			}
		}
	}
}
