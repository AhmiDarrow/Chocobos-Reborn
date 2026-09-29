package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RiderNudgeTest {
	/** A bird's box, 1.3 blocks wide, centred on x, z. */
	private static double[] push(double ax, double az, double bx, double bz) {
		double h = 0.65D;
		return RiderNudge.push(ax - h, ax + h, az - h, az + h, bx - h, bx + h, bz - h, bz + h);
	}

	@Test
	void birdsClearOfEachOtherAreLeftAlone() {
		assertNull(push(0.0D, 0.0D, 1.4D, 0.0D));
		assertNull(push(0.0D, 0.0D, 1.29D, 1.29D), "touching corners barely: left to the clients");
	}

	@Test
	void eachBirdTakesHalfTheWayOutAlongTheShallowSide() {
		// side by side, 0.4 into each other across x, fully level in z
		double[] a = push(0.0D, 0.0D, 0.9D, 0.1D);
		double[] b = push(0.9D, 0.1D, 0.0D, 0.0D);
		assertEquals(0.4D, a[2], 1.0E-9, "depth");
		assertTrue(a[0] < 0.0D && b[0] > 0.0D, "pushed apart, away from each other");
		assertEquals(0.0D, a[1], 1.0E-9, "along the shallow axis only");
		double carried = Math.abs(a[0]) * RiderNudge.DRIFT;
		assertEquals(0.4D * 0.5D + RiderNudge.MARGIN, carried, 1.0E-9, "the push carries it half the way out and a margin");
	}

	@Test
	void aDeepOverlapIsCappedNotFlung() {
		double[] a = push(0.0D, 0.0D, 0.05D, 0.0D);
		assertEquals(RiderNudge.MAX, Math.abs(a[0]), 1.0E-9);
	}

	@Test
	void aPushedRiderWaitsItsRoundTripUnlessDrivenDeeper() {
		int cooldown = RiderNudge.cooldown(150);
		assertEquals(5, cooldown);
		assertFalse(RiderNudge.due(103, 100, cooldown, 0.4D, 0.4D), "the push has not reached the server yet");
		assertTrue(RiderNudge.due(105, 100, cooldown, 0.4D, 0.3D));
		assertTrue(RiderNudge.due(101, 100, cooldown, 0.2D, 0.35D), "driven further in: pushed again at once");
	}
}
