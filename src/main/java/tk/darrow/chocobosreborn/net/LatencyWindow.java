package tk.darrow.chocobosreborn.net;

import java.util.Arrays;

/** Bounded median of recent, matched server probes; safe across network and server threads. */
public final class LatencyWindow {
    private static final long STALE_NANOS = 5_000_000_000L;
    private final int[] samples = new int[5];
    private long pending, updated;
    private boolean waiting;
    private int count, next;

    public synchronized boolean begin(long now) {
        if (waiting && now - pending < STALE_NANOS) return false;
        pending = now;
        waiting = true;
        return true;
    }

    public synchronized void reply(long nonce, long now) {
        if (!waiting || nonce != pending || now < pending) return;
        waiting = false;
        if (now - pending >= STALE_NANOS) return;
        if (count > 0 && now - updated >= STALE_NANOS) { count = 0; next = 0; }
        samples[next] = (int) ((now - pending) / 1_000_000L);
        next = (next + 1) % samples.length;
        count = Math.min(count + 1, samples.length);
        updated = now;
    }

    public synchronized int millis(long now, int fallback) {
        if (count == 0 || now - updated >= STALE_NANOS) return Math.max(0, fallback);
        int[] sorted = Arrays.copyOf(samples, count);
        Arrays.sort(sorted);
        return sorted[count / 2];
    }
}
