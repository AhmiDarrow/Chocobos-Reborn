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
