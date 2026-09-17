package tk.darrow.chocobosreborn.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceLapProgressTest {
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
				if (lap.update(progress, RaceCourseLayout.of(track).onCourse(point.x(), point.z()))) {
					count++;
				}
			}
			assertEquals(3, count, track.name());
		}
	}

	@Test
	void cuttingTheInfieldForfeitsTheLapButAllowsTheNextFullLap() {
		var lap = new RaceLapProgress(.02);
		int count = 0;
		for (int i = 21; i <= 2020; i++) {
			if (lap.update((i % 1000) / 1000.0, i < 300 || i > 700)) {
				count++;
			}
		}
		assertEquals(1, count);
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
		assertFalse(lap.update(.5, true));
		assertFalse(lap.update(.98, true));
		assertFalse(lap.update(.01, true));
	}
}
