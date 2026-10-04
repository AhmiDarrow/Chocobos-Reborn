package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Whistle eligibility is a pure predicate: only Follow comes; Stay, Wander, a heat, a lead, or the wrong rider do not. */
class WhistleRulesTest {
	@Test
	void onlyFollowIsCalled() {
		assertTrue(WhistleRules.eligible(WhistleRules.Facts.ready(0)), "follow is called");
		assertEquals(WhistleRules.Reason.PARKED, WhistleRules.reason(WhistleRules.Facts.ready(1)), "stay stays");
		assertEquals(WhistleRules.Reason.PARKED, WhistleRules.reason(WhistleRules.Facts.ready(2)), "wander stays");
		assertTrue(WhistleRules.eligible(WhistleRules.Facts.ready(0).withChick(true)), "a chicobo is called");
	}

	@Test
	void parkedBirdsAreCountedAndNamedWhenNothingFollows() {
		List<UUID> ids = List.of(new UUID(3L, 1L), new UUID(3L, 2L), new UUID(3L, 3L));
		WhistleRules.Selection selection = WhistleRules.select(ids, List.of(
				WhistleRules.Facts.ready(1), WhistleRules.Facts.ready(2), WhistleRules.Facts.ready(0)));
		assertEquals(List.of(ids.get(2)), selection.call(), "only the following bird comes");
		assertEquals(2, selection.parked(), "the stay and wander birds are counted as parked");
		assertEquals("chocobosreborn.whistle.parked", WhistleRules.noticeKey(0, false, 0, 0, 0, 0, 2),
				"nothing following says so");
		assertEquals("chocobosreborn.whistle.here", WhistleRules.noticeKey(0, false, 1, 0, 0, 0, 2),
				"a follower already here wins over parked birds");
		assertEquals("chocobosreborn.whistle.none", WhistleRules.noticeKey(0, false, 0, 0, 0, 0, 0),
				"no birds at all");
	}

	@Test
	void excludedBirdsStayPut() {
		WhistleRules.Facts ready = WhistleRules.Facts.ready(0);
		assertFalse(WhistleRules.eligible(ready.withRaceNpc(true)), "a race npc is not called");
		assertFalse(WhistleRules.eligible(ready.withTown(true)), "a town bird is not called");
		assertFalse(WhistleRules.eligible(ready.withRacing(true)), "a racing bird is not called");
		assertFalse(WhistleRules.eligible(ready.withActive(true)), "an active racer is not called");
		assertFalse(WhistleRules.eligible(ready.withScheduled(true)), "a bird on a heat is not called");
		assertFalse(WhistleRules.eligible(ready.withLeashed(true)), "a leashed bird is not called");
		assertFalse(WhistleRules.eligible(ready.withOtherRider(true)), "someone else's mount is not called");
		assertFalse(WhistleRules.eligible(ready.withDead(true)), "a dead bird is not called");
		assertFalse(WhistleRules.eligible(ready.withOwned(false)), "another player's bird is not called");
		assertFalse(WhistleRules.eligible(ready.withTame(false)), "a wild bird is not called");
		assertFalse(WhistleRules.eligible(ready.withRecordAlive(false)), "a ledger record marked dead is not called");
		assertEquals(WhistleRules.Reason.MOUNTED, WhistleRules.reason(ready.withOwnerRiding(true)),
				"the bird under the whistler stays mounted");
	}

	@Test
	void nineQualifyingBirdsCapAtEight() {
		List<UUID> ids = new ArrayList<>();
		List<WhistleRules.Facts> facts = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			ids.add(new UUID(0L, i + 1L));
			facts.add(WhistleRules.Facts.ready(0));
		}
		WhistleRules.Selection selection = WhistleRules.select(ids, facts);
		assertEquals(8, selection.call().size(), "nine qualifying birds return eight");
		assertTrue(selection.capped(), "nine qualifying birds set the cap");
		assertEquals(ids.subList(0, 8), selection.call(), "ledger order keeps the first eight");
		assertFalse(selection.call().contains(ids.get(8)), "the ninth stays out");
	}

	@Test
	void nearbyBirdsStayAndFarOrOtherDimensionBirdsCome() {
		WhistleRules.Facts ready = WhistleRules.Facts.ready(0);
		assertFalse(WhistleRules.eligible(ready.withPlace(true, 4.0D)), "within 16 in the same dimension is already here");
		assertFalse(WhistleRules.eligible(ready.withPlace(true, 16.0D)), "sixteen blocks in the same dimension is already here");
		assertTrue(WhistleRules.eligible(ready.withPlace(true, 16.01D)), "a far same-dimension bird is called");
		assertTrue(WhistleRules.eligible(ready.withPlace(false, 1.0D)), "an other-dimension bird is called");
		UUID far = new UUID(2L, 1L);
		UUID near = new UUID(2L, 2L);
		UUID other = new UUID(2L, 3L);
		WhistleRules.Selection selection = WhistleRules.select(List.of(far, near, other), List.of(
				ready.withPlace(true, 40.0D), ready.withPlace(true, 8.0D), ready.withPlace(false, 2.0D)));
		assertEquals(List.of(far, other), selection.call(), "the far bird and the other-dimension bird are called");
		assertEquals(1, selection.here(), "the bird within 16 was already here");
		assertFalse(selection.capped(), "two calls are under the cap");
	}
}
