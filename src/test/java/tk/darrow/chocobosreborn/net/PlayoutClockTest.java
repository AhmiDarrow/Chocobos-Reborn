package tk.darrow.chocobosreborn.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The field is shown smoothly: a bird moving at a steady speed on the server moves at that
 * speed on the client, tick after tick, whatever the connection does to its frames.
 */
class PlayoutClockTest {
	/** One simulated race: server frames of a bird at 1 block/tick, delivered through {@code latency}. */
	private record Run(List<Double> steps, int starved, double finalDelay) {}

	private interface Latency {
		/** One-way delay in ticks of the frame taken on {@code serverTick}. */
		double of(int serverTick, Random rng);
	}

	private static Run simulate(Latency latency, int ticks, long seed) {
		Random rng = new Random(seed);
		PlayoutClock clock = new PlayoutClock();
		FrameBuffer buffer = new FrameBuffer();
		// frames in flight: {arrival, tick}; arrivals are made monotonic like TCP
		List<double[]> flight = new ArrayList<>();
		double lastArrival = Double.NEGATIVE_INFINITY;
		for (int t = 0; t < ticks + 40; t++) {
			double arrival = Math.max(lastArrival, t + latency.of(t, rng));
			lastArrival = arrival;
			flight.add(new double[] { arrival, t });
		}
		List<Double> steps = new ArrayList<>();
		double previous = Double.NaN;
		int starved = 0;
		int next = 0;
		// the client ticks on its own clock, a little off the server's and not exactly regular
		for (int c = 0; c < ticks; c++) {
			double now = c * 1.001D + rng.nextGaussian() * 0.05D;
			while (next < flight.size() && flight.get(next)[0] <= now) {
				int tick = (int) flight.get(next)[1];
				clock.observe(flight.get(next)[0], tick);
				buffer.add(new FrameBuffer.Snap(tick, tick, 64.0D, 0.0D, 0.0F, 0.0F, true));
				next++;
			}
			double shown = clock.advance(now);
			if (Double.isNaN(shown)) {
				continue;
			}
			if (shown > buffer.newestTick()) {
				starved++;
			}
			double x = buffer.sample(shown).x();
			if (!Double.isNaN(previous) && c > 60) {
				steps.add(x - previous);
			}
			previous = x;
		}
		return new Run(steps, starved, clock.delay());
	}

	private static double worstDeviation(List<Double> steps) {
		double worst = 0.0D;
		for (double s : steps) {
			worst = Math.max(worst, Math.abs(s - 1.0D));
		}
		return worst;
	}

	@Test
	void aSteadyConnectionShowsASteadyBirdAtTheShortestDelay() {
		Run run = simulate((t, rng) -> 2.0D, 1200, 1);
		assertEquals(0, run.starved(), "never shown past the newest frame");
		assertTrue(worstDeviation(run.steps()) < 0.06D, "each tick's step within 6 % of the true speed: " + worstDeviation(run.steps()));
		assertTrue(run.finalDelay() <= 2.1D, "a steady connection needs almost no buffer: " + run.finalDelay());
	}

	@Test
	void jitterIsAbsorbedByTheBufferNotShownAsStutter() {
		// 120 ms round trip with +-50 ms of jitter per frame: vanilla's lerp showed this as a stagger
		Run run = simulate((t, rng) -> 1.2D + rng.nextDouble() * 2.0D, 2400, 2);
		assertTrue(run.starved() < 2400 / 100, "starved on fewer than 1 % of ticks: " + run.starved());
		assertTrue(worstDeviation(run.steps()) < 0.25D, "worst tick step within 25 %: " + worstDeviation(run.steps()));
		double mean = run.steps().stream().mapToDouble(Math::abs).average().orElse(0.0D);
		assertEquals(1.0D, mean, 0.02D, "the bird keeps its true speed on average");
	}

	@Test
	void aConnectionThatSlowsDownEasesBackInsteadOfFreezing() {
		// latency jumps from 1 tick to 5 ticks half way through
		Run run = simulate((t, rng) -> t < 600 ? 1.0D : 5.0D, 1400, 3);
		assertTrue(run.starved() < 80, "a four-tick latency jump starves for under four seconds: " + run.starved());
		for (double s : run.steps()) {
			assertTrue(s > 0.0D && s < 1.3D, "the bird never stops dead or leaps: " + s);
		}
	}

	@Test
	void theFirstFrameIsTakenAtOnce() {
		PlayoutClock clock = new PlayoutClock();
		assertTrue(Double.isNaN(clock.advance(0.0D)), "nothing to show before a frame");
		clock.observe(100.0D, 5000);
		double shown = clock.advance(100.0D);
		assertEquals(5000.0D - clock.delay(), shown, 1.0E-9);
		assertEquals(shown + 1.0D, clock.advance(101.0D), 1.0E-9, "then it steps one tick per client tick");
	}
}
