package tk.darrow.chocobosreborn.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameBufferTest {
	private static FrameBuffer.Snap at(int tick, double x, float yaw) {
		return new FrameBuffer.Snap(tick, x, 64.0D, 0.0D, yaw, yaw, true);
	}

	@Test
	void betweenTwoFramesTheBirdIsInterpolated() {
		FrameBuffer b = new FrameBuffer();
		assertNull(b.sample(10.0D), "no frame, nothing to show");
		b.add(at(10, 0.0D, 0.0F));
		b.add(at(11, 2.0D, 0.0F));
		assertEquals(0.0D, b.sample(9.0D).x(), 1.0E-9, "before the first frame it stands at the first");
		assertEquals(1.0D, b.sample(10.5D).x(), 1.0E-9);
		assertEquals(1.5D, b.sample(10.75D).x(), 1.0E-9);
	}

	@Test
	void yawTurnsTheShortWayRound() {
		FrameBuffer b = new FrameBuffer();
		b.add(at(1, 0.0D, 170.0F));
		b.add(at(2, 0.0D, -170.0F));
		float half = b.sample(1.5D).yRot();
		assertTrue(Math.abs(Math.abs(half) - 180.0F) < 1.0E-3F, "170 to -170 passes through 180, not 0: " + half);
	}

	@Test
	void pastTheNewestFrameItCarriesOnBrieflyThenHolds() {
		FrameBuffer b = new FrameBuffer();
		b.add(at(1, 0.0D, 0.0F));
		b.add(at(2, 1.0D, 0.0F));
		assertEquals(2.5D, b.sample(3.5D).x(), 1.0E-9, "a late frame: the bird keeps its speed");
		assertEquals(1.0D + FrameBuffer.MAX_EXTRAPOLATE, b.sample(20.0D).x(), 1.0E-9, "then it holds rather than running off");
	}

	@Test
	void aTeleportIsNeverSweptAcross() {
		FrameBuffer b = new FrameBuffer();
		b.add(at(1, 0.0D, 0.0F));
		b.add(at(2, 500.0D, 0.0F));
		assertEquals(0.0D, b.sample(1.9D).x(), 1.0E-9, "shown at the old place until the new frame's tick");
		assertEquals(500.0D, b.sample(2.0D).x(), 1.0E-9);
		assertEquals(500.0D, b.sample(4.0D).x(), 1.0E-9, "and no extrapolation off a teleport");
	}

	@Test
	void repeatedOrOlderTicksAreIgnoredAndOldFramesDropped() {
		FrameBuffer b = new FrameBuffer();
		b.add(at(5, 0.0D, 0.0F));
		b.add(at(5, 9.0D, 0.0F));
		b.add(at(4, 9.0D, 0.0F));
		assertEquals(1, b.size());
		for (int t = 6; t < 200; t++) {
			b.add(at(t, t, 0.0F));
		}
		assertTrue(b.size() <= FrameBuffer.CAPACITY);
		b.sample(190.0D);
		assertTrue(b.size() <= FrameBuffer.KEEP_BEHIND + 12, "frames well behind the shown tick are dropped: " + b.size());
		assertEquals(190.0D, b.sample(190.0D).x(), 1.0E-9);
	}
}
