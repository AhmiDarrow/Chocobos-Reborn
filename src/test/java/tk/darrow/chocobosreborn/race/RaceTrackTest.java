package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import tk.darrow.chocobosreborn.breed.ChocoboColor;

class RaceTrackTest {
	/** Blocks per second a class-appropriate bird averages; the length gates below assume it. */
	private static final double PACE = 9.0D;

	@Test
	void progressFollowsTheCentreLine() {
		for (RaceTrack track : RaceTrack.values()) {
			double tol = 2.5D / track.lapLength();
			for (int i = 0; i < 200; i++) {
				double t = i / 200.0;
				var p = track.pointAt(t);
				double back = track.progressAt(p.x(), p.z());
				double d = Math.abs(back - t);
				d = Math.min(d, 1.0D - d);
				assertTrue(d <= tol, track.name() + " t=" + t + " -> " + back);
				// a lane off the line still reads the same progress
				var lane = track.pointAtLane(t, -4.0D);
				double bl = track.progressAt(lane.x(), lane.z());
				double dl = Math.abs(bl - t);
				dl = Math.min(dl, 1.0D - dl);
				assertTrue(dl <= 6.0D / track.lapLength(), track.name() + " lane t=" + t + " -> " + bl);
			}
		}
	}

	@Test
	void gridSitsOnAStraightAcrossTheRoad() {
		for (RaceTrack track : RaceTrack.values()) {
			// the line and the grid sit on straight road: under 8 degrees of drift over the first 40 blocks
			assertTrue(track.turnAhead(0.0D, 40.0D) < 0.14D, track.name() + " start straight turns " + track.turnAhead(0.0D, 40.0D));
			assertTrue(track.turnAhead(0.0D, 20.0D) < 0.05D, track.name() + " grid straight turns " + track.turnAhead(0.0D, 20.0D));
			var delta = track.stallPos(5, 6).subtract(track.stallPos(0, 6));
			double[] tg = track.tangent(0.02D);
			assertEquals(0.0D, delta.x() * tg[0] + delta.z() * tg[1], 1.0E-6, track.name() + " grid perpendicular");
			assertEquals(track.stallPos(0, 6).y(), track.stallPos(5, 6).y(), 1.0E-9);
		}
	}

	@Test
	void sixCoursesPerClassThreeSprintsThreeGrandsPrix() {
		for (RaceClass rc : RaceClass.values()) {
			var tracks = RaceTrack.ofClass(rc);
			assertEquals(6, tracks.size(), rc.name());
			for (int i = 0; i < 6; i++) {
				assertEquals(i, tracks.get(i).getCourse());
				assertEquals(i < 3, tracks.get(i).isShort());
				assertEquals(i < 3 ? 1 : 3, tracks.get(i).getLaps());
				assertEquals(tracks.get(i), RaceTrack.forClass(rc, i));
			}
			// the three themes of a class each get a sprint and a grand prix, on six different silhouettes
			for (int i = 0; i < 3; i++) {
				assertEquals(tracks.get(i).theme(), tracks.get(i + 3).theme(), rc.name() + " theme pairs");
			}
			assertEquals(6, tracks.stream().map(RaceTrack::shape).distinct().count(), rc.name() + " shapes all differ");
		}
		assertEquals(24, RaceTrack.values().length);
		// every course has its own silhouette
		assertEquals(24, java.util.Arrays.stream(RaceTrack.values()).map(RaceTrack::shape).distinct().count(), "24 different shapes");
	}

	@Test
	void sprintsLastAMinuteAndGrandPrixLapsTwoMinutesLongerUpTheLadder() {
		double[] shortMin = {0, 0, 0, 0};
		double[] lapMin = {0, 0, 0, 0};
		for (RaceTrack t : RaceTrack.values()) {
			double seconds = t.lapLength() / PACE;
			int c = t.getRaceClass().getId();
			if (t.isShort()) {
				assertTrue(seconds >= 60.0D, t.name() + " sprint " + seconds + " s");
				shortMin[c] = shortMin[c] == 0 ? seconds : Math.min(shortMin[c], seconds);
			} else {
				assertTrue(seconds >= 120.0D, t.name() + " lap " + seconds + " s");
				lapMin[c] = lapMin[c] == 0 ? seconds : Math.min(lapMin[c], seconds);
			}
		}
		for (int c = 1; c < 4; c++) {
			assertTrue(shortMin[c] > shortMin[c - 1], "sprints get longer up the ladder");
			assertTrue(lapMin[c] > lapMin[c - 1], "grand prix laps get longer up the ladder");
		}
	}

	@Test
	void circuitsHaveCornersHillsAndWalkableSlopes() {
		for (RaceTrack t : RaceTrack.values()) {
			double maxTurn = 0.0D, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
			int n = (int) t.lapLength();
			double prevY = t.pointAt(0.0D).y();
			for (int i = 1; i <= n; i++) {
				double tt = i / (double) n;
				maxTurn = Math.max(maxTurn, t.turnAhead(tt, 20.0D));
				double y = t.pointAt(tt).y();
				assertTrue(Math.abs(y - prevY) <= 1.0D + 1.0E-9, t.name() + " step of " + (y - prevY) + " at t=" + tt);
				prevY = y;
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
			}
			assertTrue(maxTurn > 0.6D, t.name() + " has real corners");
			assertTrue(maxY - minY >= 2.0D && maxY - minY <= 12.0D, t.name() + " hills " + (maxY - minY));
			assertEquals(RaceTrack.TRACK_Y, minY, 1.0E-9, t.name() + " lowest road is the base level");
		}
	}

	@Test
	void legsNeverRunIntoEachOther() {
		for (RaceTrack t : RaceTrack.values()) {
			// courses with detours need room for them; open-road courses just their margins
			double need = t.terrainFeatures().isEmpty() ? 2.0D * (RaceTrack.ROAD_HALF + 9.5D) : 2.0D * (RaceTrack.DETOUR_OUTER + 3.0D);
			double[] gap = t.spline().closestLegsAt(80.0D);
			assertTrue(gap[0] > need, t.name() + " legs " + gap[0] + " apart at t=" + gap[1] + " and t=" + gap[2]);
		}
	}

	@Test
	void terrainGetsCrazierUpTheLadder() {
		for (RaceTrack t : RaceTrack.ofClass(RaceClass.C)) {
			assertTrue(t.terrainFeatures().isEmpty(), t.name() + " is open road");
			assertTrue(t.features().size() >= 2, t.name() + " has boost strips");
		}
		for (RaceTrack t : RaceTrack.ofClass(RaceClass.B)) {
			assertEquals(1, t.terrainFeatures().size(), t.name());
		}
		for (RaceTrack t : RaceTrack.ofClass(RaceClass.A)) {
			assertTrue(t.terrainFeatures().size() >= 2, t.name());
			assertTrue(t.terrainFeatures().stream().anyMatch(f -> f.type() == RaceTrack.Feature.Type.MUD), t.name() + " has a bog");
		}
		for (RaceTrack t : RaceTrack.ofClass(RaceClass.S)) {
			assertTrue(t.terrainFeatures().size() >= 3, t.name());
		}
		assertTrue(RaceTrack.S_SKYWAY.ridgeHeight() > RaceTrack.B_CANYON.ridgeHeight());
		for (RaceTrack t : RaceTrack.values()) {
			List<RaceTrack.Feature> fs = t.features();
			for (int i = 0; i < fs.size(); i++) {
				RaceTrack.Feature f = fs.get(i);
				assertTrue(f.start() > 0.05D && f.end() < 0.95D && f.end() > f.start(), t.name() + " feature off the start line");
				for (int j = i + 1; j < fs.size(); j++) {
					RaceTrack.Feature g = fs.get(j);
					assertTrue(f.end() < g.start() - 0.02D || g.end() < f.start() - 0.02D, t.name() + " features overlap");
				}
				// terrain sits on level ground
				if (f.terrain()) {
					double y0 = t.pointAt(f.start()).y();
					for (double tt = f.start(); tt <= f.end(); tt += 0.002D) {
						assertEquals(y0, t.pointAt(tt).y(), 1.0E-9, t.name() + " feature not level at " + tt);
					}
				}
			}
		}
	}

	@Test
	void featuresSuitTheRightBirds() {
		var water = new RaceTrack.Feature(RaceTrack.Feature.Type.WATER, 0.1, 0.2);
		var ridge = new RaceTrack.Feature(RaceTrack.Feature.Type.RIDGE, 0.1, 0.2);
		var lava = new RaceTrack.Feature(RaceTrack.Feature.Type.LAVA, 0.1, 0.2);
		var mud = new RaceTrack.Feature(RaceTrack.Feature.Type.MUD, 0.1, 0.2);
		var boost = new RaceTrack.Feature(RaceTrack.Feature.Type.BOOST, 0.1, 0.2);
		assertTrue(water.suits(ChocoboColor.BLUE));
		assertFalse(water.suits(ChocoboColor.YELLOW));
		assertTrue(ridge.suits(ChocoboColor.GREEN));
		assertFalse(ridge.suits(ChocoboColor.BLUE));
		assertTrue(lava.suits(ChocoboColor.FLAME) && lava.suits(ChocoboColor.GOLD));
		assertFalse(lava.suits(ChocoboColor.BLACK));
		assertFalse(mud.suits(ChocoboColor.GOLD));
		assertTrue(boost.suits(ChocoboColor.YELLOW) && !boost.terrain());
		assertTrue(water.suits(ChocoboColor.GOLD) && ridge.suits(ChocoboColor.GOLD));
		assertTrue(RaceTrack.B_FORD.isWaterSection(0.52D));
		assertTrue(RaceTrack.B_CANYON.isSpaceSection(0.38D));
		assertFalse(RaceTrack.C_MEADOW.isWaterSection(0.5D));
		assertNotNull(RaceTrack.C_MEADOW.featureAt(0.305D));
		assertEquals(null, RaceTrack.C_MEADOW.terrainAt(0.305D));
	}

	@Test
	void everyCourseHasItsOwnIslandFarFromTheOthers() {
		RaceTrack[] all = RaceTrack.values();
		for (int i = 0; i < all.length; i++) {
			for (int j = i + 1; j < all.length; j++) {
				double dx = all[i].centerX() - all[j].centerX(), dz = all[i].centerZ() - all[j].centerZ();
				double need = Math.max(all[i].getRadiusX(), all[i].getRadiusZ()) + Math.max(all[j].getRadiusX(), all[j].getRadiusZ()) + 40;
				assertTrue(Math.hypot(dx, dz) > need, all[i] + " overlaps " + all[j]);
			}
			assertTrue(all[i].centerZ() - all[i].getRadiusZ() > SquareBuilder.PADDOCK_Z1 + 200, all[i] + " too near the village");
			// the loop really is centred on its island
			double cx = 0, cz = 0;
			for (int k = 0; k < 100; k++) {
				var p = all[i].pointAt(k / 100.0);
				cx = Math.max(cx, Math.abs(p.x() - all[i].centerX()));
				cz = Math.max(cz, Math.abs(p.z() - all[i].centerZ()));
			}
			assertTrue(cx <= all[i].getRadiusX() && cz <= all[i].getRadiusZ(), all[i] + " leaves its island");
		}
	}

	@Test
	void byIdClamps() {
		assertEquals(RaceTrack.C_MEADOW, RaceTrack.byId(0));
		assertEquals(RaceTrack.S_MAELSTROM, RaceTrack.byId(23));
		assertEquals(RaceTrack.C_MEADOW, RaceTrack.byId(-1));
		assertEquals(RaceTrack.C_MEADOW, RaceTrack.byId(99));
	}

	@Test
	void stallsAreSpreadAcrossTheStartLine() {
		var left = RaceTrack.C_SHORE.stallPos(0, 6);
		var right = RaceTrack.C_SHORE.stallPos(5, 6);
		assertTrue(left.distanceToSqr(right) > 4.0D);
		assertEquals(RaceTrack.TRACK_Y, left.y(), 1.0E-9);
		for (int stall = 0; stall < 6; stall++) {
			assertTrue(RaceScoring.stallFitsTrack(RaceTrack.stallOffset(stall, 6), RaceTrack.STALL_HALF_WIDTH));
			assertTrue(Math.abs(RaceTrack.stallOffset(stall, 6)) < RaceTrack.ROAD_HALF);
		}
	}

	@Test
	void layoutStampsRoadFeaturesDetoursBoostsAndTheGrandstand() {
		RaceCourseLayout l = RaceCourseLayout.of(RaceTrack.B_FORD);
		RaceTrack t = RaceTrack.B_FORD;
		var mid = t.pointAtLane(0.525D, 0.0D);
		assertEquals("water", l.blocks().get(new RaceCourseLayout.Cell((int) Math.floor(mid.x()), (int) mid.y() - 1, (int) Math.floor(mid.z()))));
		var detour = t.pointAtLane(0.525D, -(RaceTrack.DETOUR_INNER + RaceTrack.DETOUR_OUTER) / 2.0D);
		assertTrue(l.onCourse(detour.x(), detour.z()), "detour is on course");
		var inside = t.pointAtLane(0.525D, RaceTrack.DETOUR_OUTER);
		assertFalse(l.onCourse(inside.x(), inside.z()), "infield is not road");
		var plain = t.pointAtLane(0.75D, 0.0D);
		assertTrue(l.onCourse(plain.x(), plain.z()));
		assertTrue(l.chunks().size() > 50, "chunk set for force-loading");
		// ridge wall
		RaceCourseLayout r = RaceCourseLayout.of(RaceTrack.B_CANYON);
		var top = RaceTrack.B_CANYON.pointAtLane(0.38D, 0.0D);
		assertTrue(r.blocks().containsKey(new RaceCourseLayout.Cell((int) Math.floor(top.x()), (int) top.y() - 1 + RaceTrack.B_CANYON.ridgeHeight(), (int) Math.floor(top.z()))), "ridge wall");
		// boost pad lies on the road at standing level, pointing along the track
		RaceCourseLayout c = RaceCourseLayout.of(RaceTrack.C_MEADOW);
		// the first strip of a course sits on the outside lane, the second in the centre: one lane, not the road
		var pad = RaceTrack.C_MEADOW.pointAtLane(0.306D, -3.0D);
		String padBlock = c.blocks().get(new RaceCourseLayout.Cell((int) Math.floor(pad.x()), (int) pad.y(), (int) Math.floor(pad.z())));
		assertNotNull(padBlock, "boost pad");
		assertTrue(padBlock.startsWith("chocobosreborn:boost_pad[facing="), padBlock);
		var clear = RaceTrack.C_MEADOW.pointAtLane(0.306D, 3.0D);
		assertEquals(null, c.blocks().get(new RaceCourseLayout.Cell((int) Math.floor(clear.x()), (int) clear.y(), (int) Math.floor(clear.z()))), "inside lane is clear");
		var centre = RaceTrack.C_MEADOW.pointAtLane(0.626D, 0.0D);
		assertNotNull(c.blocks().get(new RaceCourseLayout.Cell((int) Math.floor(centre.x()), (int) centre.y(), (int) Math.floor(centre.z()))), "second strip in the centre");
		// bog
		RaceCourseLayout a = RaceCourseLayout.of(RaceTrack.A_CRYSTAL);
		var bog = RaceTrack.A_CRYSTAL.pointAtLane(0.835D, 0.0D);
		assertEquals("mud", a.blocks().get(new RaceCourseLayout.Cell((int) Math.floor(bog.x()), (int) bog.y() - 1, (int) Math.floor(bog.z()))));
		// lava on the Nether course
		RaceCourseLayout n = RaceCourseLayout.of(RaceTrack.A_EMBER);
		var lava = RaceTrack.A_EMBER.pointAtLane(0.375D, 0.0D);
		assertEquals("lava", n.blocks().get(new RaceCourseLayout.Cell((int) Math.floor(lava.x()), (int) lava.y() - 1, (int) Math.floor(lava.z()))));
		// a landmark stands beside every course's far side
		for (RaceTrack track : RaceTrack.values()) {
			assertTrue(RaceCourseLayout.of(track).blocks().size() > 20_000, track.name() + " dressed");
		}
		// the grandstand: eight fans, each standing on a placed block, in the infield off the road
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout lay = RaceCourseLayout.of(track);
			assertEquals(8, lay.fanPosts().size(), track.name());
			for (RaceCourseLayout.FanPost f : lay.fanPosts()) {
				var below = new RaceCourseLayout.Cell((int) Math.floor(f.x()), (int) Math.floor(f.y()) - 1, (int) Math.floor(f.z()));
				assertTrue(lay.blocks().containsKey(below), track.name() + " fan floats at " + f);
				assertFalse(lay.onCourse(f.x(), f.z()), track.name() + " fan on the road");
			}
		}
	}

	@Test
	void everyIslandStaysWithinAReasonableBlockBudget() {
		for (RaceTrack track : RaceTrack.values()) {
			int n = RaceCourseLayout.of(track).blocks().size();
			assertTrue(n < 750_000, track.name() + " has " + n + " blocks");
		}
	}

	@Test
	void courseVersionFiveRelaysTheIslands() {
		assertEquals(5, SquareBuilder.COURSE_VERSION);
	}

	@Test
	void startPaintSitsPastTheGridOnSprintAndGrandPrix() {
		for (RaceTrack track : List.of(RaceTrack.C_MEADOW, RaceTrack.S_MAELSTROM)) {
			RaceCourseLayout lay = RaceCourseLayout.of(track);
			var line = track.pointAtLane(0.0D, 0.0D);
			String chequer = lay.blocks().get(new RaceCourseLayout.Cell((int) Math.floor(line.x()),
					(int) line.y() - 1, (int) Math.floor(line.z())));
			assertTrue("white_concrete".equals(chequer) || "black_concrete".equals(chequer),
					track.name() + " start line " + chequer);
			assertNotNull(lay.courseBoard(), track.name() + " marshal board");
		}
	}

	@Test
	void startArrowIsABlockArrowOnEveryCourse() {
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout lay = RaceCourseLayout.of(track);
			double t0 = 0.02D + 4.0D / track.lapLength();
			var origin = track.pointAtLane(t0, 0.0D);
			double[] tg = track.tangent(t0);
			int[] f = RaceScoring.arrowForward(tg[0], tg[1]);
			int[] r = RaceScoring.arrowRight(f[0], f[1]);
			int ox = (int) Math.floor(origin.x()), oz = (int) Math.floor(origin.z());
			int y = (int) origin.y() - 1;
			int painted = 0;
			int half = RaceScoring.startArrowHalf();
			for (int along = 0; along < RaceScoring.startArrowLength(); along++) {
				for (int across = -half; across <= half; across++) {
					if (!RaceScoring.startArrowCell(along, across)) {
						continue;
					}
					painted++;
					String b = lay.blocks().get(new RaceCourseLayout.Cell(
							ox + f[0] * along + r[0] * across, y, oz + f[1] * along + r[1] * across));
					if (RaceScoring.startArrowTip(along, across)) {
						assertEquals("gold_block", b, track.name() + " tip");
					} else {
						assertEquals("yellow_concrete", b, track.name() + " " + along + "," + across);
					}
				}
			}
			assertTrue(painted > 20, track.name() + " arrow cells");
			String besideShaft = lay.blocks().get(new RaceCourseLayout.Cell(
					ox + r[0] * 2, y, oz + r[1] * 2));
			assertFalse("yellow_concrete".equals(besideShaft), track.name() + " shaft is 3 wide");
		}
	}

	@Test
	void warningPostsStandBeforeTerrain() {
		RaceTrack t = RaceTrack.B_FORD;
		RaceCourseLayout lay = RaceCourseLayout.of(t);
		double warn = t.terrainFeatures().get(0).start() - 8.0D / t.lapLength();
		var q = t.pointAtLane(warn, RaceTrack.ROAD_HALF + 2.0D);
		int surf = (int) t.groundY(warn) - 1;
		String banner = lay.blocks().get(new RaceCourseLayout.Cell((int) Math.floor(q.x()), surf + 3,
				(int) Math.floor(q.z())));
		assertNotNull(banner, "warning banner");
		assertTrue(banner.contains("_banner"), banner);
	}
}
