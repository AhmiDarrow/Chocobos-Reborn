package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import tk.darrow.chocobosreborn.breed.ChocoboColor;

/**
 * Points by distance: a ranked win is worth round(4 x heat / the class's shortest sprint),
 * 36 promote, and the first-place purse scales by the same ratio. The checks are relative
 * (they hold for grand prix heats anywhere from about 1.15 to 3.4 sprints), so a rework of
 * the grand prix laps changes the table, not the rule. The table, with the economy check
 * (heat seconds from {@link RaceSim}), is written to {@code build/win_points.txt}.
 */
class WinPointsTest {
	private static final RaceClass[] LADDER = {RaceClass.C, RaceClass.B, RaceClass.A, RaceClass.S};
	private static final RaceClass[] PROMOTING = {RaceClass.C, RaceClass.B, RaceClass.A};
	/** Hold before GO, plus getting back to Esther and signing up (a sign-up needs 10 s in hand). */
	private static final double HOLD_S = 13.0D, TURNAROUND_S = 30.0D, MARK_S = 300.0D, LAST_CALL_S = 10.0D;

	private static double ratio(RaceTrack t) {
		return t.raceLength() / RaceScoring.referenceLength(t.getRaceClass());
	}

	@Test
	void theReferenceIsTheClassShortestSprint() {
		for (RaceClass rc : LADDER) {
			double min = RaceTrack.sprintsOf(rc).stream().mapToDouble(RaceTrack::raceLength).min().orElseThrow();
			assertEquals(min, RaceScoring.referenceLength(rc), 1e-9, rc.name());
		}
	}

	@Test
	void everySprintIsFourPoints() {
		for (RaceClass rc : LADDER) {
			double max = 0.0D;
			for (RaceTrack t : RaceTrack.sprintsOf(rc)) {
				assertEquals(4, t.winPoints(), t.name());
				max = Math.max(max, ratio(t));
			}
			// the clamp is only a guard: every sprint would round to 4 anyway
			assertTrue(max < 1.125D, rc.name() + " longest sprint is " + max + " of the shortest");
		}
	}

	@Test
	void aGrandPrixScoresByItsHeat() {
		for (RaceClass rc : LADDER) {
			for (RaceTrack t : RaceTrack.grandsPrixOf(rc)) {
				assertEquals(Math.round(4.0D * ratio(t)), t.winPoints(), t.name());
				assertTrue(t.winPoints() > 4, t.name() + " is worth more than a sprint");
			}
		}
	}

	@Test
	void grandPrixPointsNeverFallAsTheHeatGrows() {
		for (RaceClass rc : LADDER) {
			List<RaceTrack> gps = new ArrayList<>(RaceTrack.grandsPrixOf(rc));
			gps.sort(Comparator.comparingDouble(RaceTrack::raceLength));
			for (int i = 1; i < gps.size(); i++) {
				assertTrue(gps.get(i).winPoints() >= gps.get(i - 1).winPoints(),
						gps.get(i) + " is longer than " + gps.get(i - 1) + " but worth less");
			}
		}
	}

	@Test
	void theFormulaHoldsForAnyGrandPrixLength() {
		// today's heats are 2.0-2.75 sprints; the short-lap rework takes them to ~1.15-1.6
		double ref = 1150.0D;
		int last = 0;
		for (int k = 115; k <= 340; k++) {
			double r = k / 100.0D;
			int p = RaceScoring.winPoints(r * ref, ref);
			assertEquals(Math.round(4.0D * r), p, "ratio " + r);
			assertTrue(p > 4 && p >= last, "ratio " + r);
			last = p;
		}
		assertEquals(4, RaceScoring.winPoints(ref, ref));
		assertEquals(4, RaceScoring.winPoints(0.5D * ref, ref), "never under a sprint");
		assertEquals(8, RaceScoring.winPoints(2280.0D, 1150.0D));
		assertEquals(11, RaceScoring.winPoints(3150.0D, 1150.0D));
	}

	@Test
	void sprintWinsPromoteAtTheClassBar() {
		for (RaceClass rc : PROMOTING) {
			RaceTrack sprint = RaceTrack.sprintsOf(rc).get(0);
			int need = rc.pointsToPromote();
			int sprintPts = sprint.winPoints();
			int expectedWins = (need + sprintPts - 1) / sprintPts;
			int points = 0;
			for (int win = 1; win < expectedWins; win++) {
				RaceScoring.Promotion p = RaceScoring.afterFirstPlace(rc, points, sprintPts);
				assertFalse(p.promoted(), rc + " sprint win " + win);
				points = p.classWins();
			}
			assertEquals((expectedWins - 1) * sprintPts, points);
			RaceScoring.Promotion last = RaceScoring.afterFirstPlace(rc, points, sprintPts);
			assertTrue(last.promoted(), rc.name());
			assertEquals(rc.next(), last.raceClass());
		}
	}

	@Test
	void grandsPrixPromoteInFewerWinsThanSprints() {
		for (RaceClass rc : PROMOTING) {
			int need = rc.pointsToPromote();
			int sprintWins = need / 4;
			for (RaceTrack t : RaceTrack.grandsPrixOf(rc)) {
				int wins = winsToPromote(rc, t);
				int expected = (need + t.winPoints() - 1) / t.winPoints();
				assertEquals(expected, wins, t.name());
				assertTrue(wins < sprintWins, t.name() + " takes " + wins + " wins, no fewer than sprints");
				assertTrue(wins >= 3, t.name() + " takes only " + wins + " wins");
			}
		}
	}

	@Test
	void classSStaysAtTheTop() {
		for (RaceTrack t : RaceTrack.ofClass(RaceClass.S)) {
			RaceScoring.Promotion p = RaceScoring.afterFirstPlace(RaceClass.S, RaceClass.POINTS_A, t.winPoints());
			assertEquals(RaceClass.S, p.raceClass());
			assertFalse(p.promoted());
			assertEquals(RaceClass.POINTS_A, p.classWins());
		}
	}

	@Test
	void thePurseScalesLikeThePoints() {
		for (RaceClass rc : LADDER) {
			int base = RaceScoring.basePurse(rc);
			for (RaceTrack t : RaceTrack.ofClass(rc)) {
				int purse = RaceScoring.purse(t);
				assertEquals(Math.round(base * t.winPoints() / 4.0D), purse, t.name());
				if (t.isSprint()) {
					assertEquals(base, purse, t.name());
				} else {
					assertTrue(purse > base, t.name() + " pays more than a sprint");
				}
				// GP per point is the class base / 4, give or take the rounding
				assertTrue(Math.abs(purse / (double) t.winPoints() - base / 4.0D) <= 0.5D / t.winPoints() + 1e-9, t.name());
			}
		}
	}

	@Test
	void placesTakeHalfAndAQuarterOfThePurse() {
		for (RaceTrack t : RaceTrack.values()) {
			int purse = RaceScoring.purse(t);
			assertEquals(purse, RacePrizes.gp(t, 1, true), t.name());
			assertEquals(purse / 2, RacePrizes.gp(t, 2, true), t.name());
			assertEquals(purse / 4, RacePrizes.gp(t, 3, true), t.name());
			assertEquals(0, RacePrizes.gp(t, 4, true), t.name());
			assertEquals(purse / 2, RacePrizes.gp(t, 1, false), t.name() + " unranked pays half");
		}
	}

	private static int winsToPromote(RaceClass rc, RaceTrack t) {
		int points = 0;
		for (int win = 1; win < 50; win++) {
			RaceScoring.Promotion p = RaceScoring.afterFirstPlace(rc, points, t.winPoints());
			if (p.promoted()) {
				return win;
			}
			points = p.classWins();
		}
		return Integer.MAX_VALUE;
	}

	// ------------------------------------------------------------------ the table

	/** Seconds for the class favourite at its expected best form: the pace a winner has to beat. */
	private static double winningSeconds(RaceTrack t) {
		RaceClass rc = t.getRaceClass();
		ChocoboColor fav = RaceSimTest.favourite(rc);
		double form = RaceSimTest.bestForm(RaceSimTest.fieldBirds(rc));
		return RaceSim.heatTicks(RaceSim.course(t), RaceSim.Entrant.field(rc, fav, form)) / 20.0D;
	}

	/** Five-minute marks a heat ties up: hold, the heat, getting back to Esther before the last call. */
	private static int marks(double heatSeconds) {
		return (int) Math.ceil((HOLD_S + heatSeconds + TURNAROUND_S + LAST_CALL_S) / MARK_S);
	}

	@Test
	void writeTheTable() throws IOException {
		List<String> out = new ArrayList<>();
		out.add("Points by distance: points = round(4 x heat / shortest sprint of the class);");
		out.add("promote at C 36 / B 54 / A 72. purse = class base x points / 4 (2nd half, 3rd a quarter).");
		out.add("secs = the class favourite at its best form (RaceSim), the pace a winner beats.");
		out.add("GP/min = purse per minute of racing; marks = five-minute heat marks the heat ties up");
		out.add("(13 s hold + heat + 30 s back to Esther + 10 s last call); GP/hr = purse x 12 / marks.");
		out.add("old = 1 / 3 points (9 promote) and base / 3x base purses.");
		out.add("");
		out.add(String.format(Locale.ROOT, "%-14s %-9s %6s %5s %4s %4s %5s %6s %6s %5s %6s %8s",
				"course", "format", "heat", "x ref", "pts", "wins", "purse", "old", "secs", "GP/m", "marks", "GP/hr"));
		for (RaceClass rc : LADDER) {
			double ref = RaceScoring.referenceLength(rc);
			int base = RaceScoring.basePurse(rc);
			int need = rc.pointsToPromote();
			double[] perMin = new double[2], perHour = new double[2], oldPerMin = new double[2], secsPerBlock = new double[2];
			int[] n = new int[2];
			List<RaceTrack> order = new ArrayList<>(RaceTrack.sprintsOf(rc));
			List<RaceTrack> gps = new ArrayList<>(RaceTrack.grandsPrixOf(rc));
			gps.sort(Comparator.comparingDouble(RaceTrack::raceLength));
			order.addAll(gps);
			for (RaceTrack t : order) {
				int k = t.isSprint() ? 0 : 1;
				int pts = t.winPoints(), purse = RaceScoring.purse(t), old = t.isSprint() ? base : base * 3;
				double secs = winningSeconds(t);
				int m = marks(secs);
				perMin[k] += purse / (secs / 60.0D);
				oldPerMin[k] += old / (secs / 60.0D);
				perHour[k] += purse * 12.0D / m;
				secsPerBlock[k] += secs / t.raceLength();
				n[k]++;
				String wins = rc == RaceClass.S ? "-" : String.valueOf((need + pts - 1) / pts);
				String format = t.isSprint() ? "sprint" : t.getLaps() + " x " + Math.round(t.lapLength());
				out.add(String.format(Locale.ROOT, "%-14s %-9s %6.0f %5.2f %4d %4s %5d %6d %6.0f %5.1f %6d %8.0f",
						t.name(), format, t.raceLength(), t.raceLength() / ref, pts, wins, purse, old, secs,
						purse / (secs / 60.0D), m, purse * 12.0D / m));
			}
			out.add(String.format(Locale.ROOT, "%s mean GP per racing minute: sprints %.1f, grands prix %.1f (old %.1f); GP per hour on the marks: sprints %.0f, grands prix %.0f",
					rc, perMin[0] / n[0], perMin[1] / n[1], oldPerMin[1] / n[1], perHour[0] / n[0], perHour[1] / n[1]));
			// the short-lap rework: a grand prix of about 1.3 sprints, timed at this class's grand-prix pace per block
			double shortHeat = 1.3D * ref, shortSecs = shortHeat * secsPerBlock[1] / n[1];
			int shortPts = RaceScoring.winPoints(shortHeat, ref), shortPurse = (int) Math.round(base * shortPts / 4.0D);
			out.add(String.format(Locale.ROOT, "%s at 1.3x (short-lap rework, est.): %d pts, %d wins, purse %d, ~%.0f s, %.1f GP/min, %d mark(s), %.0f GP/hr",
					rc, shortPts, (need + shortPts - 1) / shortPts, shortPurse, shortSecs,
					shortPurse / (shortSecs / 60.0D), marks(shortSecs), shortPurse * 12.0D / marks(shortSecs)));
			out.add("");
		}
		Path file = Path.of("build", "win_points.txt");
		Files.createDirectories(file.getParent());
		Files.write(file, out);
		out.forEach(System.out::println);
	}
}
