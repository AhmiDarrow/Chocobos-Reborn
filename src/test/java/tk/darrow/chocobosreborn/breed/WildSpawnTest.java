package tk.darrow.chocobosreborn.breed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildSpawnTest {
	@Test
	void grassAlwaysWorksWhenBright() {
		assertTrue(WildSpawn.overworldGround(true, false, false, true));
		assertFalse(WildSpawn.overworldGround(true, false, false, false));
	}

	@Test
	void snowAcceptsWonderfulPads() {
		assertTrue(WildSpawn.overworldGround(false, true, true, true));
		assertFalse(WildSpawn.overworldGround(false, true, false, true));
		assertFalse(WildSpawn.overworldGround(false, false, true, true));
	}

	@Test
	void reversedPackSizesStillSpawn() {
		assertEquals(1, WildSpawn.packMin(3, 1));
		assertEquals(3, WildSpawn.packMax(3, 1));
		assertEquals(1, WildSpawn.packMin(1, 3));
		assertEquals(3, WildSpawn.packMax(1, 3));
		assertEquals(0, WildSpawn.packMin(-2, 4));
	}

	@Test
	void kwehIntervalNeverPlaysEveryTick() {
		assertEquals(24, WildSpawn.kwehIntervalTicks(100, 0.0D));
		assertEquals(24, WildSpawn.kwehIntervalTicks(0, 0.0D));
		assertEquals(2400, WildSpawn.kwehIntervalTicks(100, 0.99D));
		assertTrue(WildSpawn.kwehIntervalTicks(1, 0.0D) >= 24);
	}
}
