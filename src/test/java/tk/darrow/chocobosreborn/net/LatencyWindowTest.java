package tk.darrow.chocobosreborn.net;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LatencyWindowTest {
    @Test void ignoresUnsolicitedDuplicateAndExpiredReplies() {
        LatencyWindow window = new LatencyWindow();
        window.reply(0, 100_000_000);
        assertEquals(17, window.millis(100_000_000, 17));
        assertTrue(window.begin(1_000_000_000));
        assertFalse(window.begin(1_100_000_000));
        window.reply(99, 1_120_000_000);
        assertEquals(17, window.millis(1_120_000_000, 17));
        window.reply(1_000_000_000, 1_120_000_000);
        assertEquals(120, window.millis(1_120_000_000, 17));
        window.reply(1_000_000_000, 1_900_000_000);
        assertEquals(120, window.millis(1_900_000_000, 17));
        assertEquals(17, window.millis(6_120_000_000L, 17));
    }

    @Test void followsNewLatencyWithoutLettingOneSpikeDominate() {
        LatencyWindow window = new LatencyWindow();
        long time = 0;
        for (int ms : new int[] {120, 121, 119, 120, 900}) {
            assertTrue(window.begin(time));
            window.reply(time, time + ms * 1_000_000L);
            time += 1_000_000_000;
        }
        assertEquals(120, window.millis(time, 0));
        for (int i = 0; i < 3; i++) {
            assertTrue(window.begin(time));
            window.reply(time, time + 300_000_000);
            time += 1_000_000_000;
        }
        assertEquals(300, window.millis(time, 0));
    }

    @Test void stalledProbeCanBeReplacedAndOldEchoCannotOverwriteIt() {
        LatencyWindow window = new LatencyWindow();
        window.begin(0);
        assertTrue(window.begin(6_000_000_000L));
        window.reply(0, 6_010_000_000L);
        assertEquals(0, window.millis(6_010_000_000L, 0));
        window.reply(6_000_000_000L, 6_100_000_000L);
        assertEquals(100, window.millis(6_100_000_000L, 0));
    }
}
