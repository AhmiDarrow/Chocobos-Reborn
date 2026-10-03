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
	void aStepUpIsEasedOverThreeTicksNotPopped() {
		FrameBuffer b = new FrameBuffer();
		for (int t = 0; t <= 10; t++) {
			b.add(new FrameBuffer.Snap(t, t * 0.4D, 64.0D, 0.0D, 0.0F, 0.0F, true));
		}
		for (int t = 11; t <= 20; t++) {
			b.add(new FrameBuffer.Snap(t, t * 0.4D, 65.0D, 0.0D, 0.0F, 0.0F, true));
		}
		assertEquals(64.0D, b.sample(10.0D).y(), 1.0E-9, "still below the step at its start");
		assertEquals(64.0D + 1.0D / 3.0D, b.sample(11.0D).y(), 1.0E-9, "a third of the way up one tick later, not all of it");
		assertEquals(64.0D + 2.0D / 3.0D, b.sample(12.0D).y(), 1.0E-9);
		assertEquals(65.0D, b.sample(13.0D).y(), 1.0E-9, "on the step after three ticks");
		double previous = b.sample(9.5D).y();
		for (double t = 9.6D; t < 14.0D; t += 0.1D) {
			double y = b.sample(t).y();
			assertTrue(y >= previous - 1.0E-9 && y - previous <= 0.34D / 10.0D * 10.0D, "climbs steadily, never jumps: " + y);
			previous = y;
		}
		assertEquals(4.4D, b.sample(11.0D).x(), 1.0E-9, "the step never delays the bird along the road");
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
