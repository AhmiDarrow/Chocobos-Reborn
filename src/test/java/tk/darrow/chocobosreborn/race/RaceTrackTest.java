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

	/** Fine progress agrees with sample progress and moves smoothly between samples. */
	@Test
	void fineProgressRefinesBetweenSamples() {
		for (RaceTrack track : RaceTrack.values()) {
			double lap = track.lapLength();
			for (int i = 0; i < 200; i++) {
				double t = (i + 0.37D) / 200.0D;
				RacePoint p = track.pointAtLane(t, 1.5D);
				double fine = track.progressFineAt(p.x(), p.z());
				double coarse = track.progressAt(p.x(), p.z());
				double gap = Math.abs(fine - coarse);
				gap = Math.min(gap, 1.0D - gap);
				assertTrue(gap * lap <= 1.01D, track.name() + " fine within a sample of coarse at " + t);
				RacePoint c = track.pointAt(t);
				double onLine = track.progressFineAt(c.x(), c.z());
				double err = Math.abs(onLine - t);
				err = Math.min(err, 1.0D - err);
				assertTrue(err * lap < 0.6D, track.name() + " fine tracks t=" + t + ": " + onLine);
			}
		}
	}

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

	/**
	 * On the centre line a hint matches a full scan. Off the line it matches too
	 * whenever the full answer still sits in the hint's window; a nearer fold
	 * outside that window keeps the local leg.
	 */
	@Test
	void hintedProgressMatchesTheLocalSection() {
		for (RaceTrack track : RaceTrack.values()) {
			double lap = track.lapLength();
			double window = 48.0D / lap;
			for (int i = 0; i < 40; i++) {
				double t = i / 40.0D;
				RacePoint on = track.pointAt(t);
				double full = track.progressAt(on.x(), on.z());
				double hinted = track.progressAt(on.x(), on.z(), t);
				assertEquals(full, track.progressAt(on.x(), on.z(), -1.0D), 0.0D, track.name() + " an absent hint scans");
				double err = Math.abs(hinted - t);
				err = Math.min(err, 1.0D - err);
				assertTrue(err * lap < 1.5D, track.name() + " on the line at " + t + ": " + hinted);
				assertEquals(full, hinted, 1.0E-9, track.name() + " line " + t);
				assertEquals(track.progressFineAt(on.x(), on.z()),
						track.progressFineAt(on.x(), on.z(), hinted), 1.0E-9, track.name() + " fine " + t);

				RacePoint lane = track.pointAtLane(t, -14.0D);
				double laneFull = track.progressAt(lane.x(), lane.z());
				double laneHint = track.progressAt(lane.x(), lane.z(), t);
				double along = Math.abs(laneFull - t);
				along = Math.min(along, 1.0D - along);
				if (along <= window) {
					assertEquals(laneFull, laneHint, 1.0E-9, track.name() + " lane " + t);
				} else {
					double stay = Math.abs(laneHint - t);
					stay = Math.min(stay, 1.0D - stay);
					assertTrue(stay <= window || laneHint == laneFull,
							track.name() + " lane " + t + " hint " + laneHint + " full " + laneFull);
				}
			}
		}
	}

	/** A hint from the other side of the lap must not stick when that side is far away. */
	@Test
	void staleHintOnTheFarSideFallsBack() {
		for (RaceTrack track : RaceTrack.values()) {
			RacePoint start = track.pointAt(0.0D);
			RacePoint far = track.pointAt(0.5D);
			double dist = Math.hypot(far.x() - start.x(), far.z() - start.z());
			double full = track.progressAt(far.x(), far.z());
			double hinted = track.progressAt(far.x(), far.z(), 0.0D);
			if (dist > 26.0D) {
				assertEquals(full, hinted, 1.0E-9, track.name() + " legs " + dist + " apart");
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

	/**
	 * The course table, generically: every class has the same number of courses (six
	 * today, twelve once phase 2 lands), half sprints and half grands prix, course index
	 * = place in the class's rows, and every course its own silhouette.
	 */
	@Test
	void everyClassHasAsManySprintsAsGrandsPrix() {
		int total = 0;
		for (RaceClass rc : RaceClass.values()) {
			var tracks = RaceTrack.ofClass(rc);
			int perClass = tracks.size();
			total += perClass;
			assertEquals(RaceTrack.MAX_COURSES_PER_CLASS, perClass, rc.name() + " courses");
			assertEquals(perClass / 2, RaceTrack.sprintsOf(rc).size(), rc.name() + " sprints");
			assertEquals(perClass / 2, RaceTrack.grandsPrixOf(rc).size(), rc.name() + " grands prix");
			for (int i = 0; i < tracks.size(); i++) {
				RaceTrack t = tracks.get(i);
				assertEquals(i, t.getCourse(), t.name() + " course index is its place in the class's rows");
				assertEquals(t, RaceTrack.forClass(rc, i));
				assertEquals(t.getLaps() == 1, t.isSprint(), t.name());
				assertEquals(t.getLaps() > 1, t.isGrandPrix(), t.name());
				assertTrue(t.getLaps() == 1 || (t.getLaps() >= 3 && t.getLaps() <= 5), t.name() + " runs 1 or 3-5 laps: " + t.getLaps());
			}
			assertEquals(tracks.get(tracks.size() - 1), RaceTrack.forClass(rc, 99), "forClass clamps");
			assertEquals(tracks.get(0), RaceTrack.forClass(rc, -1), "forClass clamps");
		}
		assertEquals(total, RaceTrack.values().length);
		assertEquals(RaceTrack.values().length, java.util.Arrays.stream(RaceTrack.values()).map(RaceTrack::shape).distinct().count(),
				"every course has its own silhouette");
	}

	/** The swap (48-course plan, phase 1): the original rows keep their ordinals, 0-2 are now grands prix, 3-5 sprints. */
	@Test
	void theOriginalCoursesSwappedFormat() {
		for (RaceClass rc : RaceClass.values()) {
			var tracks = RaceTrack.ofClass(rc);
			for (int i = 0; i < 6; i++) {
				RaceTrack t = tracks.get(i);
				assertEquals(i < 3 ? 5 - i : 1, t.getLaps(), t.name() + " laps");
				assertEquals(rc.getId() * 6 + i, t.ordinal(), t.name() + " keeps its ordinal (saved heats, the picker's answer)");
			}
			// the three themes of a class each get a grand prix and a sprint, on six different silhouettes
			for (int i = 0; i < 3; i++) {
				assertEquals(tracks.get(i).theme(), tracks.get(i + 3).theme(), rc.name() + " theme pairs");
			}
		}
	}

	@Test
	void twelveCoursesPerClass() {
		for (RaceClass rc : RaceClass.values()) {
			assertEquals(12, RaceTrack.courseCount(rc), rc.name());
			assertEquals(6, RaceTrack.sprintsOf(rc).size(), rc.name() + " sprints");
			assertEquals(6, RaceTrack.grandsPrixOf(rc).size(), rc.name() + " grands prix");
			// each class gains one new theme: four themes a class
			assertEquals(4, RaceTrack.ofClass(rc).stream().map(RaceTrack::theme).distinct().count(), rc.name() + " themes");
			// the new rows: 6-8 sprints, 9-11 grands prix
			var tracks = RaceTrack.ofClass(rc);
			for (int i = 6; i < 12; i++) {
				assertEquals(i < 9, tracks.get(i).isSprint(), tracks.get(i).name());
			}
		}
		assertEquals(48, RaceTrack.values().length);
		assertEquals(16, RaceTrack.Theme.values().length);
		assertEquals(16, java.util.Arrays.stream(RaceTrack.values()).map(RaceTrack::theme).distinct().count(), "every theme raced");
	}

	/** Shortest sprint lap a class may have: its original sprints are 1150-1190 / 1280-1320 / 1420-1460 / 1560-1600. */
	static double sprintLapFloor(RaceClass rc) {
		return switch (rc) {
			case C -> 1100.0D;
			case B -> 1220.0D;
			case A -> 1360.0D;
			case S -> 1500.0D;
		};
	}

	/**
	 * Sprints are one long lap (at least the class's old long-course ballpark, two minutes
	 * at the reference pace); grands prix are 3-5 laps of a shorter circuit, never under
	 * 600 blocks (C_MEADOW, the old shortest sprint), the more laps the shorter the lap,
	 * and the whole heat between 1.6x and 3.4x the class's shortest sprint.
	 */
	@Test
	void sprintsAreOneLongLapGrandsPrixShortLapsMoreLapsShorter() {
		for (RaceClass rc : RaceClass.values()) {
			double shortestSprint = Double.MAX_VALUE;
			for (RaceTrack t : RaceTrack.sprintsOf(rc)) {
				assertEquals(1, t.getLaps());
				assertTrue(t.lapLength() >= sprintLapFloor(rc), t.name() + " sprint lap " + t.lapLength());
				assertTrue(t.lapLength() / PACE >= 120.0D, t.name() + " sprint lasts two minutes");
				shortestSprint = Math.min(shortestSprint, t.lapLength());
			}
			for (RaceTrack g : RaceTrack.grandsPrixOf(rc)) {
				assertTrue(g.getLaps() >= 3 && g.getLaps() <= 5, g.name() + " laps " + g.getLaps());
				// a grand prix is a short circuit: its lap is a fixed share of the class's shortest sprint
				double share = g.lapLength() / shortestSprint;
				double[] window = grandPrixLapShare(g.getLaps());
				assertTrue(share >= window[0] && share <= window[1], g.name() + " (" + g.getLaps() + " laps) lap is " + share
						+ "x the class's shortest sprint, wants " + window[0] + "-" + window[1]);
				double heat = g.raceLength() / shortestSprint;
				assertTrue(heat >= 1.15D && heat <= 1.6D, g.name() + " heat is " + heat + "x the class's shortest sprint");
				for (RaceTrack h : RaceTrack.grandsPrixOf(rc)) {
					if (g.getLaps() > h.getLaps()) {
						assertTrue(g.lapLength() <= h.lapLength() + 5.0D, g.name() + " (" + g.getLaps() + " laps, " + Math.round(g.lapLength())
								+ ") has a longer lap than " + h.name() + " (" + h.getLaps() + " laps, " + Math.round(h.lapLength()) + ")");
					}
				}
			}
		}
		// longer up the ladder
		RaceClass[] ladder = RaceClass.values();
		for (int c = 1; c < ladder.length; c++) {
			assertTrue(minLap(RaceTrack.sprintsOf(ladder[c])) > minLap(RaceTrack.sprintsOf(ladder[c - 1])), "sprints get longer up the ladder");
			assertTrue(minLap(RaceTrack.grandsPrixOf(ladder[c])) > minLap(RaceTrack.grandsPrixOf(ladder[c - 1])), "grand prix laps get longer up the ladder");
		}
	}

	/**
	 * Grand prix lap as a share of the class's shortest sprint lap (Ahmi: "grand prix are supposed to be
	 * short courses, multiple laps; sprints long one-lap tracks"): C about 5 x 300, 4 x 355, 3 x 440;
	 * the heat runs about 1.3x a sprint.
	 */
	static double[] grandPrixLapShare(int laps) {
		return switch (laps) {
			case 5 -> new double[] {0.24D, 0.29D};
			case 4 -> new double[] {0.28D, 0.34D};
			default -> new double[] {0.34D, 0.42D};
		};
	}

	private static double minLap(List<RaceTrack> tracks) {
		return tracks.stream().mapToDouble(RaceTrack::lapLength).min().orElse(0.0D);
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
		assertTrue(RaceTrack.B_FORD.isWaterSection(midOf(RaceTrack.B_FORD, RaceTrack.Feature.Type.WATER)));
		assertTrue(RaceTrack.B_CANYON.isSpaceSection(midOf(RaceTrack.B_CANYON, RaceTrack.Feature.Type.RIDGE)));
		assertFalse(RaceTrack.C_MEADOW.isWaterSection(0.5D));
		assertNotNull(RaceTrack.C_MEADOW.featureAt(boostMid(RaceTrack.C_MEADOW, 0)));
		assertEquals(null, RaceTrack.C_MEADOW.terrainAt(boostMid(RaceTrack.C_MEADOW, 0)));
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

	/**
	 * The island grid has a place for every course a class can hold, including the ones
	 * phase 2 will add: {@link RaceTrack#MAX_COURSES_PER_CLASS} slots per class row, every
	 * two slots far enough apart for two islands of {@link RaceTrack#MAX_ISLAND_RADIUS},
	 * all clear of the village, and the original six columns where they were built.
	 */
	@Test
	void theIslandGridHoldsTwelveCoursesPerClass() {
		double r = RaceTrack.MAX_ISLAND_RADIUS;
		List<double[]> slots = new java.util.ArrayList<>();
		for (RaceClass rc : RaceClass.values()) {
			for (int i = 0; i < RaceTrack.MAX_COURSES_PER_CLASS; i++) {
				double x = RaceTrack.slotX(i), z = RaceTrack.slotZ(rc);
				for (double[] s : slots) {
					// square islands in the worst case: the gap on either axis must hold both
					boolean apart = Math.abs(x - s[0]) > 2 * r + 40 || Math.abs(z - s[1]) > 2 * r + 40;
					assertTrue(apart, rc + " slot " + i + " at " + x + "," + z + " crowds " + s[0] + "," + s[1]);
				}
				assertTrue(z - r > SquareBuilder.PADDOCK_Z1 + 200, rc + " slot " + i + " too near the village");
				slots.add(new double[]{x, z});
			}
			for (int i = 0; i < 6; i++) {
				assertEquals(0.5D + (i - 2.5D) * 820.0D, RaceTrack.slotX(i), 1.0E-9, "original column " + i + " stays put");
			}
		}
		for (RaceTrack t : RaceTrack.values()) {
			assertTrue(t.getRadiusX() <= r && t.getRadiusZ() <= r, t.name() + " island " + Math.round(t.getRadiusX()) + " x "
					+ Math.round(t.getRadiusZ()) + " is bigger than the grid is spaced for (" + r + ")");
			assertEquals(RaceTrack.slotX(t.getCourse()), t.centerX(), 1.0E-9);
			assertEquals(RaceTrack.slotZ(t.getRaceClass()), t.centerZ(), 1.0E-9);
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

	/** Middle of the first feature of this type on the course (the table moves them about). */
	private static double midOf(RaceTrack track, RaceTrack.Feature.Type type) {
		for (RaceTrack.Feature f : track.features()) {
			if (f.type() == type) {
				return (f.start() + f.end()) / 2.0D;
			}
		}
		throw new AssertionError(track + " has no " + type);
	}

	/** Middle of the n-th boost strip. */
	private static double boostMid(RaceTrack track, int n) {
		int seen = 0;
		for (RaceTrack.Feature f : track.features()) {
			if (f.type() == RaceTrack.Feature.Type.BOOST && seen++ == n) {
				return (f.start() + f.end()) / 2.0D;
			}
		}
		throw new AssertionError(track + " has no boost " + n);
	}

	@Test
	void layoutStampsRoadFeaturesDetoursBoostsAndTheGrandstand() {
		RaceCourseLayout l = RaceCourseLayout.of(RaceTrack.B_FORD);
		RaceTrack t = RaceTrack.B_FORD;
		double ford = midOf(t, RaceTrack.Feature.Type.WATER);
		var mid = t.pointAtLane(ford, 0.0D);
		assertEquals("water", l.blocks().get(new RaceCourseLayout.Cell((int) Math.floor(mid.x()), (int) mid.y() - 1, (int) Math.floor(mid.z()))));
		var detour = t.pointAtLane(ford, -(RaceTrack.DETOUR_INNER + RaceTrack.DETOUR_OUTER) / 2.0D);
		assertTrue(l.onCourse(detour.x(), detour.z()), "detour is on course");
		var inside = t.pointAtLane(ford, RaceTrack.DETOUR_OUTER);
		assertFalse(l.onCourse(inside.x(), inside.z()), "infield is not road");
		var plain = t.pointAtLane(t.terrainFeatures().get(0).end() + 0.15D, 0.0D);
		assertTrue(l.onCourse(plain.x(), plain.z()));
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout lay = RaceCourseLayout.of(track);
			int seen = 0;
			for (RaceCourseLayout.Tile tile : lay.road()) {
				if ((seen++ % 64) != 0) {
					continue;
				}
				assertTrue(lay.onCourse(tile.x() + 0.5D, tile.z() + 0.5D), track.name() + " road " + tile);
				assertFalse(lay.onCourse(tile.x() + 100000.5D, tile.z()), track.name() + " off the island");
			}
		}
		assertTrue(l.chunks().size() > 50, "chunk set for force-loading");
		// ridge wall
		RaceCourseLayout r = RaceCourseLayout.of(RaceTrack.B_CANYON);
		var top = RaceTrack.B_CANYON.pointAtLane(midOf(RaceTrack.B_CANYON, RaceTrack.Feature.Type.RIDGE), 0.0D);
		assertTrue(r.blocks().containsKey(new RaceCourseLayout.Cell((int) Math.floor(top.x()), (int) top.y() - 1 + RaceTrack.B_CANYON.ridgeHeight(), (int) Math.floor(top.z()))), "ridge wall");
		// boost pad lies on the road at standing level, pointing along the track
		RaceCourseLayout c = RaceCourseLayout.of(RaceTrack.C_MEADOW);
		// the first strip of a course sits on the outside lane, the second in the centre: one lane, not the road
		var pad = RaceTrack.C_MEADOW.pointAtLane(boostMid(RaceTrack.C_MEADOW, 0), -3.0D);
		String padBlock = c.blocks().get(new RaceCourseLayout.Cell((int) Math.floor(pad.x()), (int) pad.y(), (int) Math.floor(pad.z())));
		assertNotNull(padBlock, "boost pad");
		assertTrue(padBlock.startsWith("chocobosreborn:boost_pad[facing="), padBlock);
		var clear = RaceTrack.C_MEADOW.pointAtLane(boostMid(RaceTrack.C_MEADOW, 0), 3.0D);
		assertEquals(null, c.blocks().get(new RaceCourseLayout.Cell((int) Math.floor(clear.x()), (int) clear.y(), (int) Math.floor(clear.z()))), "inside lane is clear");
		var centre = RaceTrack.C_MEADOW.pointAtLane(boostMid(RaceTrack.C_MEADOW, 1), 0.0D);
		assertNotNull(c.blocks().get(new RaceCourseLayout.Cell((int) Math.floor(centre.x()), (int) centre.y(), (int) Math.floor(centre.z()))), "second strip in the centre");
		// bog
		RaceCourseLayout a = RaceCourseLayout.of(RaceTrack.A_CRYSTAL);
		var bog = RaceTrack.A_CRYSTAL.pointAtLane(midOf(RaceTrack.A_CRYSTAL, RaceTrack.Feature.Type.MUD), 0.0D);
		assertEquals("mud", a.blocks().get(new RaceCourseLayout.Cell((int) Math.floor(bog.x()), (int) bog.y() - 1, (int) Math.floor(bog.z()))));
		// lava on the Nether course
		RaceCourseLayout n = RaceCourseLayout.of(RaceTrack.A_EMBER);
		var lava = RaceTrack.A_EMBER.pointAtLane(midOf(RaceTrack.A_EMBER, RaceTrack.Feature.Type.LAVA), 0.0D);
		assertEquals("lava", n.blocks().get(new RaceCourseLayout.Cell((int) Math.floor(lava.x()), (int) lava.y() - 1, (int) Math.floor(lava.z()))));
		// a landmark stands beside every course's far side
		for (RaceTrack track : RaceTrack.values()) {
			assertTrue(RaceCourseLayout.of(track).blocks().size() > 20_000, track.name() + " dressed");
		}
		// the crowd: every fan standing on a placed block, off the road (CourseCrowdTest has the rest)
		for (RaceTrack track : RaceTrack.values()) {
			RaceCourseLayout lay = RaceCourseLayout.of(track);
			assertTrue(lay.fanPosts().size() >= 30, track.name());
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
	void courseVersionElevenRelaysTheIslands() {
		// 6: the stamp gaps that dropped a racer through the island into the void are
		// plugged; 7: the plan is stamped in layers, so scenery, rails and pools no
		// longer land on the racing line; 8: the River cairn's spring sits in a basin, and
		// a relay clears what older plans left (stray water washed the boost pads out); 9: class-based
		// stands inside and outside the loop, the island ground reaching under each; 10: shortcut
		// markers; 11: the 48-course swap (grand prix laps lengthened on six courses, boost
		// strips and set pieces follow the new format).
		// Older islands have to be re-laid either way.
		assertEquals(11, SquareBuilder.COURSE_VERSION);
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
