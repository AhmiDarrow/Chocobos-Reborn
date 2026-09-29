package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A bigger field lines up behind the front row in the same lanes; a six-bird grid is exactly as it was. */
class GridRowsTest {
	private static double distance(RacePoint a, RacePoint b) {
		return Math.hypot(a.x() - b.x(), a.z() - b.z());
	}

	@Test
	void sixBirdsStandInOneRowJustPastTheLineAsBefore() {
		for (RaceTrack track : RaceTrack.values()) {
			for (int i = 0; i < 6; i++) {
				RacePoint expected = track.pointAtLane(0.02D, (i - 2.5D) * RaceTrack.STALL_SPACING);
				RacePoint stall = track.stallPos(i, 6);
				assertEquals(0.0D, distance(expected, stall), 1.0E-9, track + " stall " + i);
				assertEquals(expected.y(), stall.y(), 1.0E-9);
			}
		}
	}

	@Test
	void twentyBirdsLineUpInRowsOfSixBehindTheFrontRow() {
		assertEquals(4, RaceTrack.gridRows(20));
		assertEquals(1, RaceTrack.gridRows(6));
		for (int i = 0; i < 20; i++) {
			assertEquals(RaceTrack.stallOffset(i % 6, 6), RaceTrack.stallOffset(i, 20), 1.0E-9, "stall " + i + " keeps a normal lane");
		}
		for (RaceTrack track : RaceTrack.values()) {
			// the back row stands where a six-bird grid stands; each row in front is one row spacing ahead
			assertEquals(0.0D, distance(track.stallPos(18, 20), track.pointAtLane(0.02D, RaceTrack.stallOffset(0, 6))), 1.0E-9,
					track + ": the back row is just past the line");
			for (int row = 0; row < 3; row++) {
				RacePoint front = track.stallPos(row * 6 + 2, 20);
				RacePoint behind = track.stallPos((row + 1) * 6 + 2, 20);
				double gap = distance(front, behind);
				assertTrue(gap > RaceTrack.GRID_ROW_SPACING * 0.75D && gap < RaceTrack.GRID_ROW_SPACING * 1.25D,
						track + ": row " + row + " stands about one row spacing ahead of the next, gap " + gap);
				double tf = track.progressAt(front.x(), front.z());
				double tb = track.progressAt(behind.x(), behind.z());
				assertTrue(tf > tb, track + ": row " + row + " is in front (" + tf + " vs " + tb + ")");
			}
		}
	}
}
