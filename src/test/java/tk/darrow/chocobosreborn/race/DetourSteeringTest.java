package tk.darrow.chocobosreborn.race;

import java.util.List;

import org.junit.jupiter.api.Test;
import tk.darrow.chocobosreborn.breed.ChocoboColor;
import static org.junit.jupiter.api.Assertions.*;

class DetourSteeringTest {
    /** The colours a field bird can be, Jolo's Gold included (FieldRoster, RaceScoring.joloColor). */
    private static final List<ChocoboColor> RACING = List.of(ChocoboColor.YELLOW, ChocoboColor.GREEN, ChocoboColor.BLUE,
            ChocoboColor.BLACK, ChocoboColor.WHITE, ChocoboColor.GOLD);

    @Test void glacierDoesNotAimThroughTheRailBeforeTheEntrance() {
        var track = RaceTrack.B_GLACIER;
        double current = .301;
        assertNotNull(track.terrainAt(current + .03)); // previous early-swerve trigger
        var oldTarget = track.pointAtLane(current + .012, -12.5);
        var layout = RaceCourseLayout.of(track);
        assertFalse(layout.onCourse(oldTarget.x(), oldTarget.z()));
        double lane = track.steerLaneAt(current + .012, 1, ChocoboColor.YELLOW, true);
        assertTrue(lane >= -RacerLine.LANE_LIMIT - 1e-9, "still on the band before the opening: " + lane);
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
     * The line an AI bird steers for round every detour its colour takes, from every lane it
     * can come in on, is road all the way and clear of anything solid across the bird's whole
     * body: waiting on the band's outside lane while the rail stands, across the connector, along
     * the detour, back out and onto its line again. The old line blended from the racing line
     * straight to the detour's middle over a connector 4-7 blocks long, a 13.5-block swing that no
     * bird makes at A or S pace: it met the ridge face, or ran wide past the rejoin outside the rail
     * ("Vivi", "Vincent", "Cait" on A_CRYSTAL).
     */
    @Test void theDetourLineIsRoadAndClearForEveryColour() {
        for (var track : RaceTrack.values()) {
            if (track.terrainFeatures().isEmpty()) continue;
            var layout = RaceCourseLayout.of(track);
            double lap = track.lapLength(), c = RaceTrack.DETOUR_CONNECT * lap;
            for (var colour : RACING) for (boolean savvy : new boolean[]{false, true}) for (var f : track.terrainFeatures()) {
                if (!RaceTrack.takesDetour(f, colour, savvy)) continue;
                double len = (f.end() - f.start()) * lap;
                for (double direct : new double[]{-RacerLine.LANE_LIMIT, RacerLine.INSIDE_LINE, RacerLine.LANE_LIMIT}) {
                    for (double x = -(c + RaceTrack.DETOUR_PREP + 2.0); x <= len + c + RaceTrack.DETOUR_SETTLE + 2.0; x += 0.25) {
                        double t = f.start() + x / lap;
                        double lane = track.steerLaneAt(t, direct, colour, savvy);
                        String at = track.name() + " " + colour + (savvy ? " savvy" : "") + " " + f.type() + " x=" + x + " from lane " + direct;
                        var p = track.pointAtLane(t, lane);
                        assertTrue(layout.onCourse(p.x(), p.z()), at + ": lane " + lane + " is off the road");
                        if (x >= 0.0 && x <= len) {
                            assertTrue(lane <= -(RaceTrack.DETOUR_INNER + 0.875), at + ": body on the feature side of the detour, lane " + lane);
                        }
                        if (!track.inOpening(t)) {
                            assertTrue(Math.abs(lane) <= RacerLine.LANE_LIMIT + 1e-9, at + ": aims past the band's rail, lane " + lane);
                        }
                        assertBodyClear(track, layout, t, lane, at);
                    }
                }
            }
        }
    }

    private static void assertBodyClear(RaceTrack track, RaceCourseLayout layout, double t, double lane, String at) {
        int s = (int) track.groundY(t) - 1;
        for (double o = lane - 0.875; o <= lane + 0.875 + 1e-9; o += 0.25) {
            var q = track.pointAtLane(t, o);
            int x = (int) Math.floor(q.x()), z = (int) Math.floor(q.z());
            for (int y = s + 1; y <= s + 2; y++) {
                var cell = new RaceCourseLayout.Cell(x, y, z);
                String block = layout.blocks().get(cell);
                assertFalse(CourseClearanceTest.solid(block) && !layout.surfaceCells().contains(cell),
                        at + ": lane " + lane + " runs the body into " + block + " at " + x + "," + y + "," + z);
            }
        }
    }

    /**
     * Two openings a few blocks apart are one ({@link RaceTrack#DETOUR_MERGE}): on A_CRYSTAL the
     * bog's rejoin and the pool's fork left a 1.2-block rail stub in the line of every bird that
     * goes round both, and it held Yellow and Green birds there in the sweep.
     */
    @Test void openingsAFewBlocksApartAreLaidAsOne() {
        var crystal = RaceTrack.A_CRYSTAL;
        var bog = crystal.terrainFeatures().get(0);
        var pool = crystal.terrainFeatures().get(1);
        double gapFrom = bog.end() + RaceTrack.DETOUR_CONNECT, gapTo = pool.start() - RaceTrack.DETOUR_CONNECT;
        assertTrue((gapTo - gapFrom) * crystal.lapLength() < RaceTrack.DETOUR_MERGE);
        var layout = RaceCourseLayout.of(crystal);
        String rail = crystal.theme().rail;
        for (double t = gapFrom; t <= gapTo; t += 0.25 / crystal.lapLength()) {
            assertTrue(crystal.inOpening(t), "gap is opening at " + t);
            var road = crystal.pointAtLane(t, -12.5);
            assertTrue(layout.onCourse(road.x(), road.z()), "detour road runs through the gap at " + t);
            var kerb = crystal.pointAtLane(t, -(RaceTrack.ROAD_HALF + 1.0));
            int s = (int) crystal.groundY(t) - 1;
            String above = layout.blocks().get(new RaceCourseLayout.Cell((int) Math.floor(kerb.x()), s + 1, (int) Math.floor(kerb.z())));
            assertNotEquals(rail, above, "no rail stub in the gap at " + t);
        }
        // a bird that goes round both stays out on the detour between them
        for (double t = bog.end(); t <= pool.start(); t += 0.5 / crystal.lapLength()) {
            assertEquals(RaceTrack.DETOUR_HOLD, crystal.steerLaneAt(t, 1.0, ChocoboColor.YELLOW, true), 1e-9);
        }
        // S_RIFT's two ridges are 25 blocks of band apart: the rail stands between them
        var rift = RaceTrack.S_RIFT;
        double mid = (rift.terrainFeatures().get(0).end() + rift.terrainFeatures().get(1).start()) / 2.0;
        assertFalse(rift.inOpening(mid));
    }

    /**
     * "Vivi" stood 40 s against A_CRYSTAL's ridge face at 0.69 (a non-climber, pressing on
     * toward a detour lane it could not reach through the ridge; jumping does not clear four
     * blocks). A stalled AI bird ({@link RacerRecovery}) first re-aims a block and a half ahead on
     * the detour lane (a slide sideways along the face), then backs off six blocks to the lane it
     * wants there: both points are road, clear of the ridge.
     */
    @Test void aBirdPinnedOnARidgeFaceRecoversOntoClearRoad() {
        for (var track : RaceTrack.values()) for (var ridge : track.terrainFeatures()) {
            if (ridge.type() != RaceTrack.Feature.Type.RIDGE) continue;
            var layout = RaceCourseLayout.of(track);
            double lap = track.lapLength(), pinned = ridge.start() - 0.9 / lap;
            for (double direct = -RacerLine.LANE_LIMIT; direct <= RacerLine.LANE_LIMIT; direct += 1.5) {
                double back = pinned - RacerRecovery.BACK_BLOCKS / lap;
                double backLane = track.steerLaneAt(back, direct, ChocoboColor.YELLOW, true);
                double aside = pinned + RacerRecovery.REAIM_BLOCKS / lap;
                double asideLane = track.steerLaneAt(pinned + 4.0 / lap, 0.0, ChocoboColor.YELLOW, true);
                assertTrue(back < ridge.start(), track.name());
                assertTrue(asideLane <= -(RaceTrack.DETOUR_INNER + 0.875), track.name() + " re-aims onto the detour: " + asideLane);
                for (double[] p : new double[][]{{back, backLane}, {aside, asideLane}}) {
                    var target = track.pointAtLane(p[0], p[1]);
                    assertTrue(layout.onCourse(target.x(), target.z()), track.name() + " lane " + direct);
                    assertBodyClear(track, layout, p[0], p[1], track.name() + " recovery from lane " + direct);
                }
            }
        }
    }

    /**
     * Carried wide out of a rejoin and past the end of the opening, a bird stands outside the
     * band's rail ("Vincent", A_CRYSTAL 0 + 0.75, 50 s): it steers back into the opening it came
     * out of, never at the rail post ahead.
     */
    @Test void aBirdOutsideTheRailPastARejoinSteersBackIntoTheOpening() {
        for (var track : RaceTrack.values()) for (var f : track.terrainFeatures()) {
            double lap = track.lapLength();
            double past = f.end() + RaceTrack.DETOUR_CONNECT + 1.1 / lap;
            if (track.inOpening(past)) continue;   // merged with the next opening: there is no rail here
            double back = track.openingBehind(past, -7.25, RacerRecovery.BACK_BLOCKS);
            assertFalse(Double.isNaN(back), track.name() + " " + f.type());
            assertTrue(back < past && track.inOpening(back), track.name());
            var target = track.pointAtLane(back, RaceTrack.DETOUR_WAIT);
            assertTrue(RaceCourseLayout.of(track).onCourse(target.x(), target.z()), track.name());
            // on the band, or in the opening, nothing to steer back to
            assertTrue(Double.isNaN(track.openingBehind(past, -3.0, RacerRecovery.BACK_BLOCKS)));
            assertTrue(Double.isNaN(track.openingBehind(f.end(), -12.5, RacerRecovery.BACK_BLOCKS)));
        }
    }

    @Test void aShortLapsConnectorIsBrakedForALongLapsIsNot() {
        // A_CRYSTAL's 4.5-block connector at A pace (1.4 blocks a tick) went by in three ticks
        double crystal = RaceTrack.DETOUR_CONNECT * RaceTrack.A_CRYSTAL.lapLength();
        double pace = RacerLine.connectorPace(crystal, 1.4);
        assertTrue(pace < 0.65 && pace > 0.5, "braked to " + pace);
        assertEquals(RacerLine.CONNECTOR_TICKS, crystal / (1.4 * pace), 1e-9);
        // a sprint's 17-19 blocks at S pace cost nothing
        assertEquals(1.0, RacerLine.connectorPace(RaceTrack.DETOUR_CONNECT * RaceTrack.S_ABYSS.lapLength(), 2.5), 1e-9);
        assertEquals(1.0, RacerLine.connectorPace(crystal, 0.5), 1e-9, "a C-pace bird is slow enough already");
        // only for a detour the colour takes, and only on or just before a connector
        var crystalTrack = RaceTrack.A_CRYSTAL;
        var ridge = crystalTrack.terrainFeatures().get(2);
        assertFalse(Double.isNaN(crystalTrack.connectorAhead(ridge.start() - 2.0 / crystalTrack.lapLength(), ChocoboColor.BLUE, true, 6.0)));
        assertTrue(Double.isNaN(crystalTrack.connectorAhead(ridge.start() - 2.0 / crystalTrack.lapLength(), ChocoboColor.GREEN, true, 6.0)));
        double middle = (ridge.start() + ridge.end()) / 2.0;
        assertTrue(Double.isNaN(crystalTrack.connectorAhead(middle, ChocoboColor.BLUE, true, 6.0)), "free along the detour");
    }

    @Test void abilitiesAndBogKnowledgeStillChooseTheDirectRoute() {
        for (var track : RaceTrack.values()) for (var feature : track.terrainFeatures()) {
            double middle = (feature.start() + feature.end()) / 2;
            if (feature.type() == RaceTrack.Feature.Type.MUD) {
                assertEquals(1, track.detourLaneAt(middle, 1, ChocoboColor.GOLD, false));
                assertEquals(-12.5, track.detourLaneAt(middle, 1, ChocoboColor.GOLD, true));
                assertEquals(1, track.steerLaneAt(middle, 1, ChocoboColor.GOLD, false));
                assertEquals(RaceTrack.DETOUR_HOLD, track.steerLaneAt(middle, 1, ChocoboColor.GOLD, true));
            } else {
                assertEquals(1, track.detourLaneAt(middle, 1, ChocoboColor.GOLD, true));
                assertEquals(1, track.steerLaneAt(middle, 1, ChocoboColor.GOLD, true));
            }
        }
    }
}
