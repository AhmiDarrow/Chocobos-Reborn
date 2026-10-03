package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import tk.darrow.chocobosreborn.breed.ChocoboColor;
import tk.darrow.chocobosreborn.race.RaceSim.Bird;
import tk.darrow.chocobosreborn.race.RaceSim.Course;
import tk.darrow.chocobosreborn.race.RaceSim.Drive;
import tk.darrow.chocobosreborn.race.RaceSim.Entrant;

/**
 * The ladder, simulated ({@link RaceSim}) over every course of each class:
 * <ul>
 * <li>C: a fresh Good bird driven well wins; the same bird cruising does not;</li>
 * <li>B: an untrained bird does not beat the best of the field; some training does;</li>
 * <li>A: an untrained bird loses; a half-trained one wins;</li>
 * <li>S: a half-trained bird loses; a near-maxed one wins, but only with the dash used well;</li>
 * <li>Teiyo is the fastest thing on the card and a well-driven bird of the class's level beats him.</li>
 * </ul>
 * "Wins" = beats the favourite field bird at the expected best form on a card.
 * The table is written to {@code build/race_sim.txt}.
 */
class RaceSimTest {
	private static final RaceClass[] LADDER = {RaceClass.C, RaceClass.B, RaceClass.A, RaceClass.S};

	/** The colour a rider brings to each class (breeding needs race wins). */
	static ChocoboColor riderColour(RaceClass rc) {
		return switch (rc) {
			case C -> ChocoboColor.YELLOW;
			case B -> ChocoboColor.GREEN;
			case A, S -> ChocoboColor.BLACK;
		};
	}

	static Bird fresh(ChocoboColor c) {
		return new Bird(c, 2, 0, 0, 0, 0);
	}

	static Bird trained(ChocoboColor c, int points) {
		return new Bird(c, 2, points, points, points, points);
	}

	/** Expected best form of {@code n} field birds each rolling +-VARIANCE. */
	static double bestForm(int n) {
		return RacerProfile.VARIANCE * (n - 1) / (double) (n + 1);
	}

	/** Field birds on a one-rider ranked card. */
	static int fieldBirds(RaceClass rc) {
		return RaceSession.FIELD - 1 - (rc.includesTeioh() ? 2 : 0);
	}

	static Set<ChocoboColor> homeColours(RaceClass rc) {
		Set<ChocoboColor> out = new LinkedHashSet<>();
		for (FieldRoster.Entry e : FieldRoster.home(rc)) out.add(e.color());
		return out;
	}

	/** The class favourite: the home colour with the best field land speed. */
	static ChocoboColor favourite(RaceClass rc) {
		ChocoboColor best = null;
		for (ChocoboColor c : homeColours(rc)) {
			if (best == null || RaceScoring.fieldLandSpeed(c, rc) > RaceScoring.fieldLandSpeed(best, rc)) best = c;
		}
		return best;
	}

	static double ticks(RaceClass rc, Function<Course, Entrant> who) {
		return RaceSim.classTicks(rc, who);
	}

	static double fieldAvg(RaceClass rc) {
		double sum = 0.0D;
		Set<ChocoboColor> cs = homeColours(rc);
		for (ChocoboColor c : cs) sum += ticks(rc, x -> Entrant.field(rc, c, 0.0D));
		return sum / cs.size();
	}

	static double fieldBest(RaceClass rc) {
		ChocoboColor fav = favourite(rc);
		return ticks(rc, x -> Entrant.field(rc, fav, bestForm(fieldBirds(rc))));
	}

	/** The field average on a card with this rider: the field keys its floor off the rider's cruise. */
	static double fieldAvg(RaceClass rc, Bird against) {
		double sum = 0.0D;
		Set<ChocoboColor> cs = homeColours(rc);
		for (ChocoboColor c : cs) sum += ticks(rc, x -> Entrant.field(rc, c, 0.0D, against));
		return sum / cs.size();
	}

	/** The field favourite on a card with this rider. */
	static double fieldBest(RaceClass rc, Bird against) {
		ChocoboColor fav = favourite(rc);
		return ticks(rc, x -> Entrant.field(rc, fav, bestForm(fieldBirds(rc)), against));
	}

	static double rider(RaceClass rc, Bird b, Drive d) {
		return ticks(rc, x -> Entrant.rider(b, d));
	}

	static double teiyo(RaceClass rc, Bird against, double form) {
		double pace = against.pace(against.color().landSpeed());
		return ticks(rc, x -> Entrant.rival(rc, false, pace, form));
	}

	static double jolo(RaceClass rc, Bird against) {
		double pace = against.pace(against.color().landSpeed());
		return ticks(rc, x -> Entrant.rival(rc, true, pace, 0.0D));
	}

	/**
	 * The bird it takes to beat the rivals, a notch over what beats the field:
	 * B half-trained, A three-quarters, S maxed.
	 */
	static Bird classLevel(RaceClass rc) {
		return switch (rc) {
			case C -> fresh(riderColour(rc));
			case B -> trained(riderColour(rc), 50);
			case A -> trained(riderColour(rc), 75);
			case S -> trained(riderColour(rc), 100);
		};
	}

	// ------------------------------------------------------------------ the ladder

	@Test
	void classCIsWonByAFreshGoodBirdDrivenWellButNotByCruising() {
		RaceClass rc = RaceClass.C;
		Bird b = fresh(riderColour(rc));
		assertTrue(rider(rc, b, Drive.SMART) < fieldBest(rc, b), "a fresh Good Yellow driven well beats the C field");
		assertTrue(rider(rc, b, Drive.CRUISE) > fieldAvg(rc, b), "cruising loses to an ordinary C bird");
	}

	/** 1.1.9: a quarter-trained Green no longer does (the field drives tidier and dashes harder); a third-trained one does. */
	@Test
	void classBNeedsSomeTraining() {
		RaceClass rc = RaceClass.B;
		Bird fresh = fresh(riderColour(rc)), some = trained(riderColour(rc), 35);
		assertTrue(rider(rc, fresh, Drive.SMART) > fieldBest(rc, fresh), "an untrained Green does not beat the best of B");
		assertTrue(rider(rc, some, Drive.SMART) < fieldBest(rc, some), "a third-trained Green driven well does");
	}

	@Test
	void classANeedsATrainedBird() {
		RaceClass rc = RaceClass.A;
		Bird fresh = fresh(riderColour(rc)), half = trained(riderColour(rc), 50);
		assertTrue(rider(rc, fresh, Drive.SMART) > fieldAvg(rc, fresh), "an untrained Black loses in A");
		assertTrue(rider(rc, half, Drive.SMART) < fieldBest(rc, half), "a half-trained Black driven well wins");
	}

	@Test
	void classSNeedsANearMaxedBirdAndTheDash() {
		RaceClass rc = RaceClass.S;
		ChocoboColor c = riderColour(rc);
		Bird half = trained(c, 50), near = trained(c, 90), maxed = trained(c, 100);
		assertTrue(rider(rc, half, Drive.SMART) > fieldAvg(rc, half), "half-trained loses in S");
		assertTrue(rider(rc, near, Drive.SMART) < fieldBest(rc, near), "near-maxed and driven well wins");
		assertTrue(rider(rc, maxed, Drive.CRUISE) > fieldBest(rc, maxed), "even maxed, cruising does not");
	}

	/**
	 * Ahmi, 2026-10-02: the field was "too easy overall, should not be rival skill level but
	 * better". It keys its floor off the rider (RaceScoring.fieldRiderShare, FIELD_TRAIN_SHARE),
	 * so a trained bird driven well still wins, but by a race, not a lap: from half-trained up
	 * the field's best takes under 1.8 x the rider's time (a maxed C or B bird was 2 to 2.4 x
	 * ahead of the 1.1.8 field), and stays behind Teiyo.
	 */
	@Test
	void theFieldStaysInTheRaceWithATrainedBird() {
		for (RaceClass rc : LADDER) {
			for (int pts : new int[]{50, 75, 100}) {
				Bird b = trained(riderColour(rc), pts);
				double me = rider(rc, b, Drive.SMART), field = fieldBest(rc, b);
				assertTrue(field < me * 1.80D, rc + " " + pts + ": the field best is in the race: " + field / me);
				if (rc.includesTeioh()) {
					assertTrue(field > teiyo(rc, b, 0.0D), rc + " " + pts + ": Teiyo stays ahead of the field");
				}
			}
		}
	}

	@Test
	void teiyoIsTheToughestOnTheCardAndBeatable() {
		for (RaceClass rc : new RaceClass[]{RaceClass.B, RaceClass.A, RaceClass.S}) {
			Bird level = classLevel(rc);
			double teiyo = teiyo(rc, level, 0.0D);
			assertTrue(teiyo < fieldBest(rc, level), rc + ": Teiyo beats the best field bird");
			assertTrue(jolo(rc, level) < fieldAvg(rc, level), rc + ": Jolo beats an ordinary field bird");
			assertTrue(teiyo < jolo(rc, level), rc + ": Teiyo ahead of Jolo");
			assertTrue(rider(rc, level, Drive.SMART) < teiyo, rc + ": a well-driven class-level bird beats Teiyo");
			assertTrue(rider(rc, level, Drive.CRUISE) > teiyo, rc + ": cruising does not");
		}
		// Teiyo keys off the rider's bird: in S even a maxed Gold has a race on its hands
		Bird gold = trained(ChocoboColor.GOLD, 100);
		double g = rider(RaceClass.S, gold, Drive.SMART);
		double t = teiyo(RaceClass.S, gold, 0.0D);
		// 6 % before the 48-course swap (Teiyo ~4 % behind). Since sprints are one long lap and
		// grands prix 3-5 short ones, the Gold gains more over a heat: Teiyo ~8.2 % behind (HANDOFF
		// "48 courses, phase 1": a balance call for Ahmi, not retuned here)
		assertTrue(t > g && t < g * 1.09D, "S Teiyo stays within 9 % of a maxed Gold driven well: " + t / g);
	}

	@Test
	void theFieldGetsFasterUpTheLadder() {
		for (int i = 1; i < LADDER.length; i++) {
			// per-block pace: lap lengths differ by class, so compare cruise in movement-speed units
			assertTrue(RaceScoring.fieldPaceAbs(LADDER[i]) > RaceScoring.fieldPaceAbs(LADDER[i - 1]), LADDER[i].name());
		}
	}

	@Test
	void theAiNeverRunsItsBarIntoTheLock() {
		// drive the AI rule over a whole heat and watch the bar: it never hits zero
		for (RaceClass rc : LADDER) {
			RacerProfile p = RacerProfile.of(rc, RacerProfile.Role.FIELD);
			for (RaceTrack tr : RaceTrack.ofClass(rc)) {
				Course c = RaceSim.course(tr);
				double pool = 150.0D, st = pool;
				boolean dashing = false;
				for (int k = 0; k < c.laps * c.n; k++) {
					int i = k % c.n;
					double lift = p.cornerLift(c.turn[i]);
					boolean push = RacerProfile.finalPush(c.laps - k / (double) c.n, c.laps);
					dashing = p.wantsDash(st / pool, dashing, c.straight[i], lift, push);
					st = dashing ? st - 1.0D : Math.min(pool, st + 1.0D / 3.0D);
					assertTrue(st >= 1.0D, tr + " emptied the bar at " + k);
				}
			}
		}
	}

	// ------------------------------------------------------------------ the table

	@Test
	void writeTheTable() throws IOException {
		List<String> out = new ArrayList<>();
		out.add("Mean heat seconds over every course of each class. before = the 1.0.18 AI and rival pacing,");
		out.add("after = this build (a rider's bird is the same in both). Field best = the class favourite colour at");
		out.add("the expected best form of the card; Teiyo is paced off the rider named in the row (FF7 Teioh).");
		out.add("Rider rows: does the bird beat the field best / Teiyo, before -> after. The field keeps a share of the");
		out.add("rider's cruise as its floor (RaceScoring.fieldRiderShare), so the field best is per rider.");
		out.add("");
		out.add(String.format(Locale.ROOT, "%-5s %-40s %8s %8s   %s", "class", "who", "before", "after", "beats field best / Teiyo"));
		for (RaceClass rc : LADDER) {
			ChocoboColor col = riderColour(rc);
			double oldBest = oldFieldBest(rc), best = fieldBest(rc);
			secs(out, rc, "field average", oldFieldAvg(rc), fieldAvg(rc), "");
			secs(out, rc, "field best (" + favourite(rc) + ", +" + pct(bestForm(fieldBirds(rc))) + " form)", oldBest, best, "");
			Object[][] refs = {
					{"fresh Good " + col + " driven well", fresh(col), Drive.SMART},
					{"fresh Good " + col + " cruising", fresh(col), Drive.CRUISE},
					{"25-trained " + col + " driven well", trained(col, 25), Drive.SMART},
					{"50-trained " + col + " driven well", trained(col, 50), Drive.SMART},
					{"75-trained " + col + " driven well", trained(col, 75), Drive.SMART},
					{"90-trained " + col + " driven well", trained(col, 90), Drive.SMART},
					{"maxed " + col + " driven well", trained(col, 100), Drive.SMART},
					{"maxed " + col + " cruising", trained(col, 100), Drive.CRUISE},
			};
			if (rc == RaceClass.S) {
				refs = java.util.Arrays.copyOf(refs, refs.length + 1);
				refs[refs.length - 1] = new Object[]{"maxed GOLD driven well", trained(ChocoboColor.GOLD, 100), Drive.SMART};
			}
			for (Object[] r : refs) {
				Bird b = (Bird) r[1];
				double t = rider(rc, b, (Drive) r[2]);
				double keyed = fieldBest(rc, b);
				String verdict = yn(t < oldBest) + "->" + yn(t < keyed)
						+ String.format(Locale.ROOT, " (field best %.0fs)", keyed / 20.0D);
				if (rc.includesTeioh()) {
					double oldT = oldTeiyo(rc, b), newT = teiyo(rc, b, 0.0D);
					verdict += " / " + yn(t < oldT) + "->" + yn(t < newT)
							+ String.format(Locale.ROOT, "  (Teiyo %.0fs -> %.0fs)", oldT / 20.0D, newT / 20.0D);
				}
				out.add(String.format(Locale.ROOT, "%-5s %-40s %8s %7.1fs   %s", rc, (String) r[0], "", t / 20.0D, verdict));
			}
			if (rc.includesTeioh()) {
				Bird level = classLevel(rc);
				secs(out, rc, "Jolo vs the " + (level.speed() == 100 ? "maxed" : level.speed() + "-trained") + " rider",
						oldJolo(rc, level), jolo(rc, level), "");
			}
			Bird lvl = classLevel(rc);
			double lvlPace = lvl.pace(lvl.color().landSpeed());
			out.add(diag(rc, "  dash share / bar left: field favourite", c -> Entrant.field(rc, favourite(rc), 0.0D)));
			out.add(diag(rc, "  dash share / bar left: class-level rider", c -> Entrant.rider(lvl, Drive.SMART)));
			if (rc.includesTeioh()) {
				out.add(diag(rc, "  dash share / bar left: Teiyo", c -> Entrant.rival(rc, false, lvlPace, 0.0D)));
			}
			out.add("");
		}
		Path file = Path.of("build", "race_sim.txt");
		Files.createDirectories(file.getParent());
		Files.write(file, out);
		out.forEach(System.out::println);
	}

	private static String yn(boolean win) {
		return win ? "W" : "L";
	}

	private static void secs(List<String> out, RaceClass rc, String who, double before, double after, String note) {
		out.add(String.format(Locale.ROOT, "%-5s %-40s %7.1fs %7.1fs   %s", rc, who, before / 20.0D, after / 20.0D, note));
	}

	private static String diag(RaceClass rc, String who, Function<Course, Entrant> e) {
		double share = 0.0D, left = 0.0D;
		int n = 0;
		for (RaceTrack tr : RaceTrack.ofClass(rc)) {
			RaceSim.heatTicks(RaceSim.course(tr), e.apply(RaceSim.course(tr)));
			share += RaceSim.lastDashShare;
			left += RaceSim.lastLeftover;
			n++;
		}
		return String.format(Locale.ROOT, "%-5s %-40s %7.0f%% %7.0f%%", rc, who, share / n * 100.0D, left / n * 100.0D);
	}

	private static String pct(double v) {
		return String.format(Locale.ROOT, "%.1f%%", v * 100.0D);
	}

	private static double oldFieldAvg(RaceClass rc) {
		double sum = 0.0D;
		Set<ChocoboColor> cs = homeColours(rc);
		for (ChocoboColor c : cs) sum += ticks(rc, x -> RaceSim.Legacy.field(rc, c, 0.0D));
		return sum / cs.size();
	}

	private static double oldFieldBest(RaceClass rc) {
		ChocoboColor best = null;
		for (ChocoboColor c : homeColours(rc)) if (best == null || c.landSpeed() > best.landSpeed()) best = c;
		ChocoboColor fav = best;
		return ticks(rc, x -> RaceSim.Legacy.field(rc, fav, bestForm(fieldBirds(rc))));
	}

	private static double oldTeiyo(RaceClass rc, Bird against) {
		return ticks(rc, x -> RaceSim.Legacy.rival(rc, false, against, 0.0D));
	}

	private static double oldJolo(RaceClass rc, Bird against) {
		return ticks(rc, x -> RaceSim.Legacy.rival(rc, true, against, 0.0D));
	}
}
