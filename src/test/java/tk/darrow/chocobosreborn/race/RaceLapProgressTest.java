package tk.darrow.chocobosreborn.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceLapProgressTest {
	private static final double ALLOW = 0.01D;

	@Test
	void everyCourseAcceptsLaneEdgesAndStartingStalls() {
		for (RaceTrack track : RaceTrack.values()) {
			var layout = RaceCourseLayout.of(track);
			for (int stall = 0; stall < 6; stall++) {
				var p = track.stallPos(stall, 6);
				assertTrue(layout.onCourse(p.x(), p.z()), track.name());
			}
			for (int i = 0; i < 360; i++) {
				for (int side : new int[]{-1, 1}) {
					var p = track.pointAtLane(i / 360.0, side * 4.0D);
					assertTrue(layout.onCourse(p.x(), p.z()), track.name() + " t=" + i / 360.0 + " side " + side);
				}
			}
		}
	}

	@Test
	void everyCourseAcceptsThreeCompleteLaps() {
		for (RaceTrack track : RaceTrack.values()) {
			var lap = new RaceLapProgress(.02);
			int count = 0;
			for (int i = 21; i <= 3020; i++) {
				double progress = (i % 1000) / 1000.0;
				var point = track.pointAt(progress);
				if (lap.step(progress, RaceCourseLayout.of(track).onCourse(point.x(), point.z()),
						RaceLapProgress.allowance(track.lapLength())) == RaceLapProgress.Step.LAP) {
					count++;
				}
			}
			assertEquals(3, count, track.name());
		}
	}

	@Test
	void aWideLineOffTheRoadKeepsTheLap() {
		// off the road for 40 ticks, back on a little further round: nothing lost
		var lap = new RaceLapProgress(.02);
		int laps = 0;
		for (int i = 21; i <= 2020; i++) {
			double p = (i % 1000) / 1000.0;
			boolean road = !(i >= 300 && i < 340);
			double fed = road ? p : 0.3;   // off the road the rider is not credited; progress is wherever
			RaceLapProgress.Step s = lap.step(road ? p : fed, road, 0.05);
			assertTrue(s != RaceLapProgress.Step.RESCUE, "no rescue at " + i);
			if (s == RaceLapProgress.Step.LAP) {
				laps++;
			}
		}
		assertEquals(2, laps);
	}

	@Test
	void aShortcutIsSetBackNotForfeited() {
		var lap = new RaceLapProgress(.02);
		for (int i = 21; i < 300; i++) {
			lap.step(i / 1000.0, true, ALLOW);
		}
		// off the road at 0.299, cuts across and rejoins at 0.6
		assertEquals(RaceLapProgress.Step.NONE, lap.step(0.45, false, ALLOW));
		assertEquals(RaceLapProgress.Step.RESCUE, lap.step(0.6, true, ALLOW));
		assertEquals(0.299, lap.lastProgress(), 1.0E-9, "the anchor is where it left the road");
		// the session puts it back at the anchor; riding on from there completes the lap
		lap.rescued();
		int laps = 0;
		for (int i = 299; i <= 1300; i++) {
			if (lap.step((i % 1000) / 1000.0, true, ALLOW) == RaceLapProgress.Step.LAP) {
				laps++;
			}
		}
		assertEquals(1, laps, "the lap in progress still counts");
	}

	@Test
	void fourSecondsOffTheRoadIsARescue() {
		var lap = new RaceLapProgress(.5);
		for (int i = 1; i < RaceLapProgress.OFF_LIMIT_TICKS; i++) {
			assertEquals(RaceLapProgress.Step.NONE, lap.step(.5, false, ALLOW));
			assertTrue(lap.offCourse());
		}
		assertEquals(RaceLapProgress.Step.RESCUE, lap.step(.5, false, ALLOW));
		assertEquals(4, RaceScoring.offRoadSecondsLeft(1));
		assertEquals(1, RaceScoring.offRoadSecondsLeft(RaceLapProgress.OFF_LIMIT_TICKS - 1));
		assertTrue(RaceScoring.strayedTooFar(25.0D, 0.0D));
		assertFalse(RaceScoring.strayedTooFar(10.0D, 10.0D));
	}

	@Test
	void theInfieldIsNotRoad() {
		for (RaceTrack track : RaceTrack.values()) {
			var infield = track.pointAtLane(0.02D, RaceTrack.ROAD_HALF + 12.0D);
			assertFalse(RaceCourseLayout.of(track).onCourse(infield.x(), infield.z()), track.name() + " infield is not road");
		}
	}

	@Test
	void reverseLapsAndTeleportsDoNotCount() {
		var lap = new RaceLapProgress(.02);
		for (int i = 3019; i >= 0; i--) {
			assertFalse(lap.update((i % 1000) / 1000.0, true));
		}
		lap = new RaceLapProgress(.02);
		assertEquals(RaceLapProgress.Step.RESCUE, lap.step(.5, true, ALLOW), "a teleport round the lap is put back");
		assertFalse(lap.update(.98, true));
		assertFalse(lap.update(.01, true));
	}
}
