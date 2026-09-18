package tk.darrow.chocobosreborn.race;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldRosterTest {
	private static final RaceClass[] LADDER = {RaceClass.C, RaceClass.B, RaceClass.A, RaceClass.S};

	@Test
	void eachClassHasAHomeRosterOfSixteen() {
		for (RaceClass rc : LADDER) {
			assertEquals(16, FieldRoster.home(rc).size(), rc.name());
		}
	}

	@Test
	void fiveRegularsFromCBandAGuestOneClassUp() {
		assertEquals(5, FieldRoster.crossUpCount(RaceClass.C));
		assertEquals(5, FieldRoster.crossUpCount(RaceClass.B));
		assertEquals(5, FieldRoster.crossUpCount(RaceClass.A));
		assertEquals(0, FieldRoster.crossUpCount(RaceClass.S));
		assertEquals(16, FieldRoster.eligible(RaceClass.C).size());
		assertEquals(21, FieldRoster.eligible(RaceClass.B).size());
		assertEquals(21, FieldRoster.eligible(RaceClass.A).size());
		assertEquals(21, FieldRoster.eligible(RaceClass.S).size());
	}

	@Test
	void aDrawNeverRepeatsAndStaysEligible() {
		for (RaceClass rc : LADDER) {
			List<FieldRoster.Entry> card = FieldRoster.draw(rc, 5, new Random(42L));
			assertEquals(5, card.size(), rc.name());
			Set<String> names = new HashSet<>();
			for (FieldRoster.Entry e : card) {
				assertTrue(e.eligible(rc), e.name() + " on " + rc);
				assertTrue(names.add(e.name()), e.name());
			}
		}
	}

	@Test
	void teiyoAndJoloAreNotInThePadPool() {
		Set<String> names = FieldRoster.names();
		assertEquals(64, names.size());
		assertFalse(names.contains("Teiyo"));
		assertFalse(names.contains("Jolo"));
		assertFalse(names.contains("Joe"));
	}

	@Test
	void crossoversFromCAppearOnBAndNotOnA() {
		int guests = 0;
		for (FieldRoster.Entry e : FieldRoster.eligible(RaceClass.B)) {
			if (e.home() == RaceClass.C) {
				assertTrue(e.crossUp());
				assertFalse(e.eligible(RaceClass.A));
				guests++;
			}
		}
		assertEquals(5, guests);
	}
}
