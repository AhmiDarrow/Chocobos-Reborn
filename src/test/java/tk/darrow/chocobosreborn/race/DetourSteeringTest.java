package tk.darrow.chocobosreborn.race;

import org.junit.jupiter.api.Test;
import tk.darrow.chocobosreborn.breed.ChocoboColor;
import static org.junit.jupiter.api.Assertions.*;

class DetourSteeringTest {
    @Test void glacierDoesNotAimThroughTheRailBeforeTheEntrance() {
        var track = RaceTrack.B_GLACIER;
        double current = .301;
        assertNotNull(track.terrainAt(current + .03)); // previous early-swerve trigger
        var oldTarget = track.pointAtLane(current + .012, -12.5);
        var layout = RaceCourseLayout.of(track);
        assertFalse(layout.onCourse(oldTarget.x(), oldTarget.z()));
        double lane = track.detourLaneAt(current + .012, 1, ChocoboColor.YELLOW, true);
        assertEquals(1, lane);
        var target = track.pointAtLane(current + .012, lane);
        assertTrue(layout.onCourse(target.x(), target.z()));
    }

    @Test void allCoursesUseTheirBuiltConnectorIntervals() {
        for (var track : RaceTrack.values()) for (var feature : track.terrainFeatures()) {
            double before = feature.start() - RaceTrack.DETOUR_CONNECT;
            double after = feature.end() + RaceTrack.DETOUR_CONNECT;
            assertEquals(1, track.detourLaneAt(before - 1e-6, 1, ChocoboColor.YELLOW, true), 1e-9, track.name());
            assertEquals(1, track.detourLaneAt(after + 1e-6, 1, ChocoboColor.YELLOW, true), 1e-9, track.name());
            assertEquals(1, track.detourLaneAt(before, 1, ChocoboColor.YELLOW, true), 1e-9, track.name());
            assertEquals(1, track.detourLaneAt(after, 1, ChocoboColor.YELLOW, true), 1e-9, track.name());
            assertEquals(-12.5, track.detourLaneAt(feature.start(), 1, ChocoboColor.YELLOW, true), 1e-9, track.name());
            assertEquals(-12.5, track.detourLaneAt(feature.end(), 1, ChocoboColor.YELLOW, true), 1e-9, track.name());
            for (int i = 0; i <= 20; i++) {
                double at = before + (after - before) * i / 20.0;
                double lane = track.detourLaneAt(at, 1, ChocoboColor.YELLOW, true);
                var point = track.pointAtLane(at, lane);
                assertTrue(RaceCourseLayout.of(track).onCourse(point.x(), point.z()), track.name() + " " + at);
            }
        }
    }

    /**
     * "Vivi" stood 40 s against A_CRYSTAL's ridge face at 0.69 (a non-climber, pressing on
     * toward a detour lane it could not reach through the ridge; jumping does not clear four
     * blocks). A stuck AI bird now backs off along the lap to the lane it wants there: that
     * point is on the road, before the face, and clear of the ridge.
     */
    @Test void aBirdPinnedOnARidgeFaceBacksOffOntoClearRoad() {
        for (var track : RaceTrack.values()) for (var ridge : track.terrainFeatures()) {
            if (ridge.type() != RaceTrack.Feature.Type.RIDGE) continue;
            var layout = RaceCourseLayout.of(track);
            double lap = track.lapLength(), pinned = ridge.start() - 0.9 / lap;
            for (double direct = -RacerLine.LANE_LIMIT; direct <= RacerLine.LANE_LIMIT; direct += 1.5) {
                double back = pinned - 3.0 / lap;
                double lane = track.detourLaneAt(back, direct, ChocoboColor.YELLOW, true);
                var target = track.pointAtLane(back, lane);
                assertTrue(layout.onCourse(target.x(), target.z()), track.name() + " lane " + direct);
                assertTrue(back < ridge.start(), track.name());
                int x = (int) Math.floor(target.x()), z = (int) Math.floor(target.z()), s = (int) track.groundY(back) - 1;
                for (int y = s + 1; y <= s + 3; y++) {
                    var cell = new RaceCourseLayout.Cell(x, y, z);
                    String block = layout.blocks().get(cell);
                    // the road one step up a hill is the road, not the ridge
                    assertFalse(CourseClearanceTest.solid(block) && !layout.surfaceCells().contains(cell),
                            track.name() + " back-off target in " + block);
                }
            }
        }
    }

    @Test void abilitiesAndBogKnowledgeStillChooseTheDirectRoute() {
        for (var track : RaceTrack.values()) for (var feature : track.terrainFeatures()) {
            double middle = (feature.start() + feature.end()) / 2;
            if (feature.type() == RaceTrack.Feature.Type.MUD) {
                assertEquals(1, track.detourLaneAt(middle, 1, ChocoboColor.GOLD, false));
                assertEquals(-12.5, track.detourLaneAt(middle, 1, ChocoboColor.GOLD, true));
            } else {
                assertEquals(1, track.detourLaneAt(middle, 1, ChocoboColor.GOLD, true));
            }
        }
    }
}
