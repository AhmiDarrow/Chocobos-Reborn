package tk.darrow.chocobosreborn.race;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Kart bumps ({@link RacerContact}). Birds run along +z at a gallop (1.2 blocks a
 * tick) unless a test says otherwise; lane +x is the inside.
 */
class RacerContactTest {
	private static final double GALLOP = 1.2D;
	private static final RacerContact.Guard CENTRE = new RacerContact.Guard(0.0D, 1.0D, 0.0D);

	private static RacerContact.Body bird(double x, double z, double vz) {
		return new RacerContact.Body(x, 64.0D, z, 0.0D, vz, 1.0D, true);
	}

	private static RacerContact.Body bird(double x, double z, double vx, double vz, double weight) {
		return new RacerContact.Body(x, 64.0D, z, vx, vz, weight, true);
	}

	private static RacerContact.Push hit(RacerContact.Body self, RacerContact.Body... others) {
		return RacerContact.resolve(self, self.vx(), self.vz() == 0.0D && self.vx() == 0.0D ? 1.0D : self.vz(),
				List.of(others), CENTRE);
	}

	@Test
	void birdsApartDoNothing() {
		RacerContact.Body me = bird(0.0D, 0.0D, GALLOP);
		assertEquals(RacerContact.Push.NONE, hit(me, bird(0.0D, 1.7D, 0.9D)), "a length ahead, not touching");
		assertEquals(RacerContact.Push.NONE, hit(me, bird(1.7D, 0.0D, GALLOP)), "alongside with air between");
		assertEquals(RacerContact.Push.NONE, hit(me, new RacerContact.Body(0.0D, 66.0D, 0.5D, 0.0D, 0.9D, 1.0D, true)),
				"one over a ridge above the other");
		assertEquals(RacerContact.Push.NONE, hit(me), "nobody near");
	}

	@Test
	void aCleanOvertakeCostsNothing() {
		// swinging out PASS_OFFSET to go round: centres never come within reach
		assertTrue(RacerLine.PASS_OFFSET > RacerContact.REACH);
		RacerContact.Body me = bird(RacerLine.PASS_OFFSET, 0.0D, GALLOP);
		for (double dz = -3.0D; dz <= 3.0D; dz += 0.25D) {
			assertFalse(hit(me, bird(0.0D, dz, 0.8D)).any(), "passing at " + dz);
		}
	}

	@Test
	void theRearBirdLosesPaceAndBouncesTheFrontOneKeepsItsLine() {
		RacerContact.Body rear = bird(0.0D, 0.0D, GALLOP);
		RacerContact.Body front = bird(0.1D, 1.4D, 0.9D);
		RacerContact.Push r = hit(rear, front);
		assertEquals(RacerContact.Kind.REAR, r.kind());
		assertTrue(r.dvz() < 0.0D, "bounces back off its tail: " + r);
		assertTrue(r.loss() >= 0.10D && r.loss() <= RacerContact.MAX_LOSS, "a noticeable 10-25% bump: " + r.loss());

		RacerContact.Push f = hit(front, rear);
		assertEquals(RacerContact.Kind.FRONT, f.kind());
		assertEquals(0.0D, f.loss(), 1e-12, "the front bird loses no pace");
		assertTrue(f.dvz() > 0.0D && f.dvz() <= RacerContact.FRONT_NUDGE_MAX + 1e-12, "only a small nudge on: " + f);
		assertEquals(0.0D, f.dvx(), 1e-12, "along its own line, not off it");
	}

	@Test
	void aHarderRearEndCostsMoreUpToTheCap() {
		RacerContact.Body front = bird(0.0D, 1.4D, 0.9D);
		double soft = hit(bird(0.0D, 0.0D, 1.0D), front).loss();
		double hard = hit(bird(0.0D, 0.0D, 1.3D), front).loss();
		double huge = hit(bird(0.0D, 0.0D, 3.0D), front).loss();
		assertTrue(hard > soft, soft + " < " + hard);
		assertEquals(RacerContact.MAX_LOSS, huge, 1e-12);
	}

	@Test
	void tailgatingAtTheSamePaceIsARubNotABump() {
		RacerContact.Push p = hit(bird(0.0D, 0.0D, GALLOP), bird(0.0D, 1.5D, GALLOP));
		assertEquals(0.0D, p.loss(), 1e-12);
		assertTrue(p.dvz() < 0.0D && p.dvz() > -0.05D, "eases back out of the overlap: " + p);
	}

	@Test
	void sideBySideContactShovesBothApartAndBothKeepTheirPace() {
		// two birds drifting into each other at the same speed
		RacerContact.Body left = bird(-0.7D, 0.0D, 0.15D, GALLOP, 1.0D);
		RacerContact.Body right = bird(0.7D, 0.0D, -0.15D, GALLOP, 1.0D);
		RacerContact.Push l = RacerContact.resolve(left, 0.0D, 1.0D, List.of(right), new RacerContact.Guard(-0.7D, 1.0D, 0.0D));
		RacerContact.Push r = RacerContact.resolve(right, 0.0D, 1.0D, List.of(left), new RacerContact.Guard(0.7D, 1.0D, 0.0D));
		assertEquals(RacerContact.Kind.SIDE, l.kind());
		assertEquals(RacerContact.Kind.SIDE, r.kind());
		assertTrue(l.dvx() < -0.2D && r.dvx() > 0.2D, "felt, both ways: " + l + " " + r);
		assertEquals(-l.dvx(), r.dvx(), 1e-9, "equal birds, equal shove");
		assertEquals(0.0D, l.dvz(), 1e-9, "pace kept");
		assertEquals(0.0D, r.dvz(), 1e-9, "pace kept");
		assertEquals(0.0D, l.loss() + r.loss(), 1e-12);
	}

	@Test
	void aHeadOnAtAnAngleCostsBoth() {
		// converging at 45 degrees each, nose to nose
		double s = GALLOP * Math.sqrt(0.5D);
		RacerContact.Body a = bird(-0.5D, 0.0D, s, s, 1.0D);
		RacerContact.Body b = bird(0.5D, 1.0D, -s, -s, 1.0D);
		RacerContact.Push pa = RacerContact.resolve(a, s, s, List.of(b), RacerContact.Guard.NONE);
		RacerContact.Push pb = RacerContact.resolve(b, -s, -s, List.of(a), RacerContact.Guard.NONE);
		assertEquals(RacerContact.Kind.REAR, pa.kind());
		assertEquals(RacerContact.Kind.REAR, pb.kind());
		assertTrue(pa.loss() > 0.0D && pb.loss() > 0.0D);
		// each is pushed back the way it came
		assertTrue(pa.dvx() * s + pa.dvz() * s < 0.0D);
		assertTrue(pb.dvx() * -s + pb.dvz() * -s < 0.0D);
	}

	@Test
	void aDashingOrBoostedBirdShovesHarderAndIsShovedLess() {
		assertEquals(1.0D, RacerContact.weight(false, false), 1e-12);
		assertEquals(1.5D, RacerContact.weight(true, false), 1e-12);
		assertEquals(1.5D, RacerContact.weight(false, true), 1e-12);
		assertEquals(2.0D, RacerContact.weight(true, true), 1e-12);
		RacerContact.Body me = bird(-0.7D, 0.0D, 0.1D, GALLOP, 1.0D);
		double byPlain = Math.abs(RacerContact.resolve(me, 0.0D, 1.0D,
				List.of(bird(0.7D, 0.0D, -0.1D, GALLOP, 1.0D)), CENTRE).dvx());
		double byDasher = Math.abs(RacerContact.resolve(me, 0.0D, 1.0D,
				List.of(bird(0.7D, 0.0D, -0.1D, GALLOP, RacerContact.weight(true, true))), CENTRE).dvx());
		assertTrue(byDasher > byPlain * 1.2D, byPlain + " vs " + byDasher);
		RacerContact.Body dasher = bird(-0.7D, 0.0D, 0.1D, GALLOP, RacerContact.weight(true, false));
		double dasherShoved = Math.abs(RacerContact.resolve(dasher, 0.0D, 1.0D,
				List.of(bird(0.7D, 0.0D, -0.1D, GALLOP, 1.0D)), CENTRE).dvx());
		assertTrue(dasherShoved < byPlain, "the heavier bird gives less ground");
		// a dashing bird rear-ending pays less than a plain one ramming it
		RacerContact.Body front = bird(0.0D, 1.4D, 0.9D);
		double plainLoss = hit(bird(0.0D, 0.0D, 1.1D), front).loss();
		double dashLoss = hit(bird(0.0D, 0.0D, 0.0D, 1.1D, 1.5D), front).loss();
		assertTrue(dashLoss < plainLoss);
	}

	@Test
	void ghostsAndHeldBirdsAreNotSolid() {
		assertTrue(RacerContact.solid(true, false, false));
		assertFalse(RacerContact.solid(false, false, false), "not racing: finished rider or forfeited");
		assertFalse(RacerContact.solid(true, true, false), "held: grid countdown or set-back");
		assertFalse(RacerContact.solid(true, false, true), "ghost: finished AI parking or just set back");
		RacerContact.Body me = bird(0.0D, 0.0D, GALLOP);
		RacerContact.Body ghost = new RacerContact.Body(0.0D, 64.0D, 1.0D, 0.0D, 0.5D, 1.0D, false);
		assertEquals(RacerContact.Push.NONE, hit(me, ghost), "drives through a ghost");
		RacerContact.Body meGhost = new RacerContact.Body(0.0D, 64.0D, 0.0D, 0.0D, GALLOP, 1.0D, false);
		assertEquals(RacerContact.Push.NONE, hit(meGhost, bird(0.0D, 1.0D, 0.5D)), "a ghost drives through everyone");
	}

	@Test
	void theGridIsWiderThanTheContactReach() {
		assertTrue(RaceTrack.STALL_SPACING > RacerContact.REACH, "birds on neighbouring stalls must not touch at GO");
		assertTrue(RacerContact.REACH < 1.75D, "the reach sits inside the 1.75 bird box");
	}

	@Test
	void everyShoveIsClamped() {
		// a boosted, dashing bird slamming into a light one from every side at once
		RacerContact.Body me = bird(0.0D, 0.0D, 0.0D, 0.2D, 1.0D);
		RacerContact.Push p = RacerContact.resolve(me, 0.0D, 1.0D, List.of(
				bird(0.3D, 0.0D, -3.0D, 0.2D, 2.0D),
				bird(0.0D, -0.3D, 0.0D, 3.0D, 2.0D),
				bird(0.2D, 0.2D, -2.0D, -2.0D, 2.0D)), RacerContact.Guard.NONE);
		assertTrue(Math.hypot(p.dvx(), p.dvz()) <= RacerContact.MAX_IMPULSE + 1e-9, p.toString());
		assertTrue(p.loss() <= RacerContact.MAX_LOSS);
	}

	@Test
	void aShoveNeverCarriesABirdPastTheLaneLimit() {
		// the bird is near the outside (negative lane) and shoved outward hard, at every lane out there
		for (double lane = -RaceTrack.ROAD_HALF; lane <= 0.0D; lane += 0.25D) {
			RacerContact.Guard g = new RacerContact.Guard(lane, 1.0D, 0.0D);
			RacerContact.Body me = bird(lane, 0.0D, 0.0D, GALLOP, 1.0D);
			RacerContact.Push p = RacerContact.resolve(me, 0.0D, 1.0D,
					List.of(bird(lane + 0.6D, 0.0D, -0.6D, GALLOP, 2.0D)), g);
			double drift = p.dvx() * RacerContact.DRIFT;
			double end = lane + drift;
			assertTrue(end >= -RacerLine.LANE_LIMIT - 1e-9 || drift >= -1e-12,
					"lane " + lane + " drifts to " + end);
			assertTrue(drift <= 1e-12, "never pushed inward by a bird on its inside");
		}
		// same on the inside
		RacerContact.Guard in = new RacerContact.Guard(4.3D, 1.0D, 0.0D);
		RacerContact.Push p = RacerContact.resolve(bird(4.3D, 0.0D, 0.0D, GALLOP, 1.0D), 0.0D, 1.0D,
				List.of(bird(3.7D, 0.0D, 0.6D, GALLOP, 2.0D)), in);
		assertTrue(4.3D + p.dvx() * RacerContact.DRIFT <= RacerLine.LANE_LIMIT + 1e-9);
		// the rail sits on the kerb at ROAD_HALF + 1: the limit keeps half a block of air for a 1.75 bird
		assertTrue(RacerLine.LANE_LIMIT + 0.875D < RaceTrack.ROAD_HALF + 1.0D);
	}

	@Test
	void offTheRoadBandThereIsNoSidewaysShove() {
		// a detour lane (narrow, own rails): only the bounce along the road survives
		RacerContact.Guard detour = new RacerContact.Guard(-12.5D, 1.0D, 0.0D);
		RacerContact.Push side = RacerContact.resolve(bird(-12.5D, 0.0D, 0.2D, GALLOP, 1.0D), 0.0D, 1.0D,
				List.of(bird(-11.4D, 0.0D, -0.2D, GALLOP, 1.0D)), detour);
		assertEquals(0.0D, side.dvx(), 1e-12);
		RacerContact.Push rear = RacerContact.resolve(bird(-12.5D, 0.0D, 0.0D, GALLOP, 1.0D), 0.0D, 1.0D,
				List.of(bird(-12.3D, 1.4D, 0.0D, 0.8D, 1.0D)), detour);
		assertEquals(0.0D, rear.dvx(), 1e-12);
		assertTrue(rear.dvz() < 0.0D && rear.loss() > 0.0D, "a rear-end still costs pace on a detour");
	}

	@Test
	void theBumpFadesOverAMomentAndDoesNotStack() {
		RacerContact.Slow slow = new RacerContact.Slow();
		assertEquals(1.0D, slow.factor(), 1e-12);
		slow.hit(0.2D);
		assertEquals(0.8D, slow.factor(), 1e-12);
		slow.hit(0.1D);
		assertEquals(0.8D, slow.factor(), 1e-12, "a smaller bump inside a bigger one adds nothing");
		double lost = 0.0D;
		for (int i = 0; i < RacerContact.Slow.TICKS; i++) {
			lost += 1.0D - slow.factor();
			slow.tick();
		}
		assertEquals(1.0D, slow.factor(), 1e-12, "gone after " + RacerContact.Slow.TICKS + " ticks");
		// over the fade a 20% bump costs ~1.6 ticks of running: about two blocks at a gallop
		assertTrue(lost > 1.2D && lost < 2.0D, "lost " + lost);
		slow.hit(0.9D);
		assertEquals(1.0D - RacerContact.MAX_LOSS, slow.factor(), 1e-12, "capped");
		slow.clear();
		assertEquals(1.0D, slow.factor(), 1e-12);
	}

	@Test
	void aDrivingClientLeadsTheRemoteBirdsByItsRoundTrip() {
		assertEquals(2, RacerContact.leadTicks(0), "host: just the lerp");
		assertEquals(5, RacerContact.leadTicks(150));
		assertEquals(8, RacerContact.leadTicks(300));
		assertEquals(10, RacerContact.leadTicks(5000), "capped");
		assertEquals(2, RacerContact.leadTicks(-40));
		RacerContact.Body b = bird(0.0D, 0.0D, 1.0D).ahead(3);
		assertEquals(3.0D, b.z(), 1e-12);
	}
}
