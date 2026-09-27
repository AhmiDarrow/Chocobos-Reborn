package tk.darrow.chocobosreborn.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EyeBlinkTest {
	@Test
	void blinksBrieflyAndStaggersBirds() {
		for (int id = 1; id <= 32; id++) {
			int total = 0, consecutive = 0;
			for (int tick = 0; tick < 1000; tick++) {
				boolean closed = EyeBlink.closed(tick, id);
				if (tick < 20) assertFalse(closed);
				consecutive = closed ? consecutive + 1 : 0;
				assertTrue(consecutive <= 3, "blink must not stick shut");
				if (closed) total++;
			}
			assertTrue(total >= 12 && total <= 30);
		}
		boolean staggered = false;
		for (int tick = 20; tick < 500; tick++) staggered |= EyeBlink.closed(tick, 1) != EyeBlink.closed(tick, 2);
		assertTrue(staggered);
	}
}
