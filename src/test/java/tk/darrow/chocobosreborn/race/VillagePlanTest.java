package tk.darrow.chocobosreborn.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagePlanTest {
	@Test
	void paddockVersionTwelveRebuildsTheVillage() {
		assertEquals(12, SquareBuilder.PADDOCK_VERSION);
	}

	@Test
	void practiceGatesSitOnTheIslandOffThePlazaAndAvenue() {
		assertTrue(VillageLayout.onIsland(VillageLayout.FUN_GATE_WEST_X, VillageLayout.FUN_GATE_Z));
		assertTrue(VillageLayout.onIsland(VillageLayout.FUN_GATE_EAST_X, VillageLayout.FUN_GATE_Z));
		assertTrue(Math.hypot(VillageLayout.FUN_GATE_WEST_X - VillageLayout.PX,
				VillageLayout.FUN_GATE_Z - VillageLayout.PZ) > 15.0D);
		assertTrue(Math.hypot(VillageLayout.FUN_GATE_EAST_X - VillageLayout.PX,
				VillageLayout.FUN_GATE_Z - VillageLayout.PZ) > 15.0D);
		assertTrue(Math.abs(VillageLayout.FUN_GATE_WEST_X) > 5);
		assertTrue(Math.abs(VillageLayout.FUN_GATE_EAST_X) > 5);
	}

	@Test
	void gysahlBedIsOnTheIslandWestOfTheGreensStall() {
		assertTrue(VillageLayout.onIsland(VillageLayout.GYSAHL_CX, VillageLayout.GYSAHL_CZ));
		var greens = TownPosts.keeperPosts().stream()
				.filter(post -> post.role() == TownRole.GREENS)
				.findFirst()
				.orElseThrow();
		assertTrue(VillageLayout.GYSAHL_CX < greens.x());
		assertTrue(Math.hypot(VillageLayout.GYSAHL_CX - VillageLayout.PX,
				VillageLayout.GYSAHL_CZ - VillageLayout.PZ) > 13.5D);
	}

	@Test
	void funGatesStillMapToSprintAndFirstGrandPrix() {
		assertEquals(0, RaceScoring.funGateCourse(false));
		assertEquals(3, RaceScoring.funGateCourse(true));
	}
}
