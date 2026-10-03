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
	/**
	 * A racing climber goes up its ridge at 1.2 blocks a tick (vanilla's ladder was 0.118: the
	 * ridge cost more than its detour saved) and its last push only just clears the top, so it
	 * lands there running instead of flying over the ridge.
	 */
	@Test
	void aClimberBoundsUpTheRidgeAndLandsOnTop() {
		assertTrue(RaceScoring.ridgeClimbRate() > 1.0D, "an S ridge (5) in five ticks");
		assertTrue(RaceScoring.ridgeLift(4.0D) == RaceScoring.RIDGE_CLIMB_LIFT);
		assertTrue(RaceScoring.ridgeLift(Double.NaN) == RaceScoring.RIDGE_CLIMB_LIFT, "no known course: full lift");
		assertTrue(RaceScoring.ridgeLift(-1.0D) == RaceScoring.RIDGE_CLIMB_LIFT, "still on a face over the reckoned top");
		for (double toTop : new double[]{0.05D, 0.3D, 0.9D}) {
			double rise = (RaceScoring.ridgeLift(toTop) - 0.08D) * 0.98D;
			assertTrue(rise >= toTop && rise <= toTop + 0.11D, toTop + " under the top rises " + rise);
		}
	}

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
	void aRidersBirdStepsFromItsFootingNotALeftOverFlag() {
		assertFalse(RaceScoring.riderStepGround(true, true, false),
				"left 'on ground' by a step that ended over the bog: no zero-height slide the server cannot replay");
		assertTrue(RaceScoring.riderStepGround(true, false, true),
				"the server's replay climbed onto the top: standing on it, it steps like the client");
		assertTrue(RaceScoring.riderStepGround(true, true, true));
		assertFalse(RaceScoring.riderStepGround(true, false, false));
		assertTrue(RaceScoring.riderStepGround(false, true, false), "an AI bird keeps vanilla's flag");
		assertFalse(RaceScoring.riderStepGround(false, false, true), "an AI bird keeps vanilla's flag");
		assertTrue(RaceScoring.FOOTING_PROBE > 0.0D && RaceScoring.FOOTING_PROBE < 1.0D / 16.0D,
				"a hair under the feet, never the depth of a carpet or a snow layer");
	}

	@Test
	void theRidgeBandIsTheRidgeFeatureAcrossTheRoad() {
		RaceTrack track = RaceTrack.A_CRYSTAL;
		RaceTrack.Feature ridge = track.terrainFeatures().stream()
				.filter(f -> f.type() == RaceTrack.Feature.Type.RIDGE).findFirst().orElseThrow();
		double mid = (ridge.start() + ridge.end()) / 2.0D, lap = track.lapLength();
		assertTrue(track.ridgeBandAt(mid, 0.0D));
		assertTrue(track.ridgeBandAt(ridge.start() - 1.0D / lap, 3.0D), "a bird's nose meets the face before its centre does");
		assertTrue(track.ridgeBandAt(mid, -(RaceTrack.ROAD_HALF + 1.0D)), "pressed on its flank from the connector side");
		assertTrue(track.ridgeBandAt(mid, RaceTrack.ROAD_HALF + 1.0D), "pressed on its flank from the infield");
		assertFalse(track.ridgeBandAt(mid, RaceTrack.RIDGE_BAND_HALF + 0.25D), "clear of the ridge");
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

	/**
	 * Walk a bird up to every ridge in every racing lane: where its nose first meets a block of
	 * the ridge, it must already be allowed to climb. On a diagonal leg the face reaches the outer
	 * lanes before the centre line's start (B_CANYON, lane -4.1: 2.2 blocks early), which a
	 * 2-block pad missed and pinned a Green bird on the face every lap.
	 */
	@Test
	void everyRidgeFaceIsClimbableFromEveryLane() {
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout layout = RaceCourseLayout.of(track);
			double lap = track.lapLength();
			for (RaceTrack.Feature ridge : track.terrainFeatures()) {
				if (ridge.type() != RaceTrack.Feature.Type.RIDGE) continue;
				for (double lane = -RacerLine.LANE_LIMIT; lane <= RacerLine.LANE_LIMIT + 1.0E-9D; lane += 0.5D) {
					for (double t = ridge.start() - 12.0D / lap; t < ridge.start() + 4.0D / lap; t += 0.25D / lap) {
						RacePoint at = track.pointAtLane(t, lane);
						double[] tg = track.tangent(t);
						// the nose: a bird is about 2 blocks long, the box half-width 0.875
						double nx = at.x() + tg[0] * 1.0D, nz = at.z() + tg[1] * 1.0D;
						int bx = (int) Math.floor(nx), bz = (int) Math.floor(nz), by = (int) Math.floor(at.y()) + 1;
						String block = layout.blocks().get(new RaceCourseLayout.Cell(bx, by, bz));
						if (block == null || block.contains("water") || block.contains("carpet") || block.contains("boost")) continue;
						assertTrue(track.ridgeBandAt(t, lane), track + " lane " + lane + ": nose meets " + block + " at "
								+ String.format(java.util.Locale.ROOT, "%.4f", t) + " (" + Math.round((ridge.start() - t) * lap * 10) / 10.0D
								+ " blocks before the ridge) outside the climb band");
						break;
					}
				}
			}
		}
	}

	/**
	 * Wherever a bird's body touches a ridge, it may climb it: the band reaches every lane from
	 * which the ridge's outermost column is within a bird's half width. "Serah" and a Black bird in
	 * the field GameTest sat wedged in a notch of S_SKYWAY's first ridge at lane -6.6 (the ridge's
	 * columns reach 6.2 out on that diagonal leg) with the band ending at 5.5; the Green harness bot
	 * on B_KOPJE slid off the face to -6.5 the same way, every lap. No ridge column reaches a lane
	 * the detour's road would put a bird in.
	 */
	@Test
	void aBirdTouchingARidgeIsOnItsClimbBand() {
		double widest = 0.0D;
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout layout = RaceCourseLayout.of(track);
			for (var e : layout.blocks().entrySet()) {
				var c = e.getKey();
				double cx = c.x() + 0.5D, cz = c.z() + 0.5D;
				double t = track.progressAt(cx, cz);
				RaceTrack.Feature ft = track.terrainAt(t);
				if (ft == null || ft.type() != RaceTrack.Feature.Type.RIDGE || c.y() != (int) track.groundY(t)
						|| !layout.surfaceCells().contains(c)) {
					continue;
				}
				double lane = Math.abs(track.laneAt(t, cx, cz));
				if (lane > RaceTrack.DETOUR_INNER) {
					continue;   // another leg of the lap
				}
				widest = Math.max(widest, lane);
				// the column's far corner, and a bird's half width past it
				double reach = lane + Math.sqrt(0.5D) + 0.875D;
				assertTrue(reach <= RaceTrack.RIDGE_BAND_HALF + 0.35D, track + ": a ridge column " + lane + " out");
				assertTrue(track.ridgeBandAt(t, lane + 0.875D), track + ": touching the column at " + lane);
			}
		}
		assertTrue(widest > RaceTrack.ROAD_HALF, "the ridge's columns reach past the road's edge: " + widest);
		assertTrue(RaceTrack.RIDGE_BAND_HALF + 0.875D < RaceTrack.DETOUR_INNER + 0.5D, "a bird on the detour's road never climbs");
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
