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
     * Carried wide out of a rejoin and past the end of the opening, a bird used to stand outside
     * the band's rail ("Vincent", A_CRYSTAL 0 + 0.75, 50 s). Past every rejoin the rail now flares
     * back to the kerb ({@link RaceTrack#REJOIN_FLARE}): that spot is open road inside it. Beyond
     * the flare, outside the kerb rail (over it, say), a bird still steers back into the opening it
     * came out of, never at the rail post ahead.
     */
    @Test void aBirdOutsideTheRailPastARejoinSteersBackIntoTheOpening() {
        for (var track : RaceTrack.values()) for (var f : track.terrainFeatures()) {
            double lap = track.lapLength();
            double past = f.end() + RaceTrack.DETOUR_CONNECT + 1.1 / lap;
            if (track.inOpening(past)) continue;   // merged with the next opening: there is no rail here
            assertTrue(track.inDetourOpening(past, -7.25), track.name() + " " + f.type() + ": Vincent's spot is in the flare");
            assertTrue(Double.isNaN(track.openingBehind(past, -7.25, RacerRecovery.BACK_BLOCKS)), "nothing to steer back to in the flare");
            double beyond = f.end() + RaceTrack.DETOUR_CONNECT + (RaceTrack.REJOIN_FLARE + 1.5) / lap;
            if (track.inOpening(beyond)) continue;
            double back = track.openingBehind(beyond, -7.25, RacerRecovery.BACK_BLOCKS + RaceTrack.REJOIN_FLARE);
            assertFalse(Double.isNaN(back), track.name() + " " + f.type());
            assertTrue(back < beyond && track.inDetourOpening(back, -(RaceTrack.ROAD_HALF + 1.0)), track.name());
            var target = track.pointAtLane(back, RaceTrack.DETOUR_WAIT);
            assertTrue(RaceCourseLayout.of(track).onCourse(target.x(), target.z()), track.name());
            // on the band, or in the opening, nothing to steer back to
            assertTrue(Double.isNaN(track.openingBehind(beyond, -3.0, RacerRecovery.BACK_BLOCKS)));
            assertTrue(Double.isNaN(track.openingBehind(f.end(), -12.5, RacerRecovery.BACK_BLOCKS)));
        }
    }

    /**
     * The swing into a short connector is braked only as far as its angle needs at the bird's
     * speed ({@link RacerLine#connectorSpeed}): the lag through the turn stays inside the room
     * between the detour's lane and the feature's corner. The old rule (5.5 ticks per connector,
     * both ends) held an A bird to 0.8 blocks a tick on A_CRYSTAL and cost the field on A_CANOPY
     * and A_GROTTO a lap or two a heat.
     */
    @Test void aShortLapsConnectorIsBrakedOnlyAsMuchAsItsSwingNeeds() {
        double crystal = RaceTrack.DETOUR_CONNECT * RaceTrack.A_CRYSTAL.lapLength();
        double v = RacerLine.connectorSpeed(crystal);
        // the lag through the swing at that speed is exactly the room there is
        double sin = RacerLine.CONNECTOR_SWING / Math.hypot(RacerLine.CONNECTOR_SWING, crystal);
        assertEquals(RacerLine.CONNECTOR_ROOM, RacerLine.GROUND_LAG_TICKS * v * sin, 1e-9);
        assertTrue(v > 1.3 && v < 1.6, "A_CRYSTAL's swing at " + v);
        assertEquals(1.0, RacerLine.connectorPace(crystal, 1.35), 1e-9, "an A bird (1.3-1.5 blocks a tick) barely brakes");
        double s = RacerLine.connectorPace(crystal, 2.2);
        assertTrue(s > 0.6 && s < 0.7, "an S bird brakes to " + s);
        // a longer connector is a shallower swing: less braking
        assertTrue(RacerLine.connectorSpeed(crystal * 1.5) > v);
        // a sprint's 17-19 blocks at S pace cost nothing
        assertEquals(1.0, RacerLine.connectorPace(RaceTrack.DETOUR_CONNECT * RaceTrack.S_ABYSS.lapLength(), 2.5), 1e-9);
        assertEquals(1.0, RacerLine.connectorPace(crystal, 0.5), 1e-9, "a C-pace bird is slow enough already");
        // only for a detour the colour takes, only on or just before its fork: the way out is free
        var crystalTrack = RaceTrack.A_CRYSTAL;
        double lap = crystalTrack.lapLength();
        var ridge = crystalTrack.terrainFeatures().get(2);
        assertFalse(Double.isNaN(crystalTrack.connectorAhead(ridge.start() - 2.0 / lap, ChocoboColor.BLUE, true, 6.0)));
        assertTrue(Double.isNaN(crystalTrack.connectorAhead(ridge.start() - 2.0 / lap, ChocoboColor.GREEN, true, 6.0)));
        double middle = (ridge.start() + ridge.end()) / 2.0;
        assertTrue(Double.isNaN(crystalTrack.connectorAhead(middle, ChocoboColor.BLUE, true, 6.0)), "free along the detour");
        assertTrue(Double.isNaN(crystalTrack.connectorAhead(ridge.end() + 2.0 / lap, ChocoboColor.BLUE, true, 6.0)), "free on the way out");
    }

    /**
     * B_KOPJE (ridge 0.53-0.65), the Green harness bot: recovered at 0.5284, lane -6.5, on each of
     * three laps. A climber has no detour to swing out for there: its line is its own lane all the
     * way up to and over the ridge. It met the face in the outside lane (-4.5), slid outward along
     * the face's staircase while it climbed and dropped off the climb band at 5.5: the band now
     * reaches every lane a bird touching the ridge can be in, so from there it climbs on.
     */
    @Test void kopjeGreenGoesStraightOverTheRidgeFromEveryLane() {
        var track = RaceTrack.B_KOPJE;
        var ridge = track.terrainFeatures().get(0);
        double lap = track.lapLength();
        for (double lane = -RacerLine.LANE_LIMIT; lane <= RacerLine.LANE_LIMIT; lane += 0.5) {
            for (double t = ridge.start() - 30.0 / lap; t <= ridge.end() + 10.0 / lap; t += 0.5 / lap) {
                assertEquals(lane, track.steerLaneAt(t, lane, ChocoboColor.GREEN, true), 1e-9, "Green swings out at " + t);
                assertTrue(Double.isNaN(track.connectorAhead(t, ChocoboColor.GREEN, true, RacerLine.CONNECTOR_BRAKE_LEAD)), "and brakes at " + t);
            }
        }
        // the bot's spot: pressed on the ridge's corner, off the old band, on the new one
        double x = -4518.6044, z = 1636.875;
        double t = track.progressAt(x, z);
        double lane = track.laneAt(t, x, z);
        assertTrue(lane < -RaceTrack.ROAD_HALF, "the bot sat off the old band: " + lane);
        assertTrue(track.ridgeBandAt(t, lane), "and may climb from there now");
    }

    /**
     * A_MACHETE (pool 0.4125-0.4525, bog 0.48-0.53, ridge 0.5825-0.6225), the Black harness bots
     * (water walkers and climbers, bog-savvy): all three recovered at 0.5544, lane -7.09, four times
     * each. Black swings out for the bog only: straight over the pool, straight over the ridge. Out
     * of the bog's rejoin its line is back on the band's outside lane by the connector's end and in
     * its own lane before the ridge's gantry; what carried the bots wide was braking off the
     * throttle, which leaves a bird no grip to turn with, into a rail that started square at the
     * kerb. The flare ({@link RaceTrack#REJOIN_FLARE}) now takes a bird that runs wide there.
     */
    @Test void macheteBlackSwingsOutForTheBogOnly() {
        var track = RaceTrack.A_MACHETE;
        double lap = track.lapLength(), c = RaceTrack.DETOUR_CONNECT * lap;
        var pool = track.terrainFeatures().get(0);
        var bog = track.terrainFeatures().get(1);
        var ridge = track.terrainFeatures().get(2);
        for (double lane = -RacerLine.LANE_LIMIT; lane <= RacerLine.LANE_LIMIT; lane += 0.75) {
            // over the pool (merged with the bog's opening) and up to the bog's fork: its own lane
            double prep = bog.start() - (c + RaceTrack.DETOUR_PREP) / lap;
            for (double t = pool.start() - 20.0 / lap; t < prep; t += 0.5 / lap) {
                assertEquals(lane, track.steerLaneAt(t, lane, ChocoboColor.BLACK, true), 1e-9, "before the bog at " + t);
            }
            // the bog: out to the detour lane
            assertEquals(RaceTrack.DETOUR_HOLD, track.steerLaneAt((bog.start() + bog.end()) / 2.0, lane, ChocoboColor.BLACK, true), 1e-9);
            // out of the rejoin: on the band's outside lane by the connector's end, then its own lane
            double end = bog.end() + c / lap;
            assertEquals(RaceTrack.DETOUR_WAIT, track.steerLaneAt(end, lane, ChocoboColor.BLACK, true), 1e-9);
            for (double t = end + RaceTrack.DETOUR_SETTLE / lap; t <= ridge.end() + 10.0 / lap; t += 0.5 / lap) {
                assertEquals(lane, track.steerLaneAt(t, lane, ChocoboColor.BLACK, true), 1e-9, "after the bog at " + t);
                assertTrue(Double.isNaN(track.connectorAhead(t, ChocoboColor.BLACK, true, RacerLine.CONNECTOR_BRAKE_LEAD)));
            }
            // and nothing on the way out is braked
            for (double t = bog.end() - 6.0 / lap; t <= end; t += 0.5 / lap) {
                assertTrue(Double.isNaN(track.connectorAhead(t, ChocoboColor.BLACK, true, RacerLine.CONNECTOR_BRAKE_LEAD)), "braked at " + t);
            }
        }
        // where the bots came out, wide, is the flare's road, not the far side of a rail
        double end = bog.end() + RaceTrack.DETOUR_CONNECT;
        for (double d = 0.25; d <= 3.0; d += 0.25) {
            assertTrue(track.inDetourOpening(end + d / lap, -7.09), "open road " + d + " blocks past the rejoin");
        }
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
