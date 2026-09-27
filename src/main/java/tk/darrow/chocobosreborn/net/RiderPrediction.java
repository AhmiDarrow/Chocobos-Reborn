package tk.darrow.chocobosreborn.net;

import java.util.ArrayDeque;
import java.util.List;
import tk.darrow.chocobosreborn.race.RaceScoring;

/** Shared, deterministic stamina accounting for acknowledged race input frames. */
public final class RiderPrediction {
    public static final int MAX_PENDING = 256;
    public record State(int stamina, boolean locked) {}
    public record Frame(int sequence, boolean dash, boolean moving) {}
    public record Rules(int maximum, int intelligence, long seed) {}

    private RiderPrediction() {}

    public static boolean dashing(State state, Frame frame) {
        return frame.dash && state.stamina > 0 && !RaceScoring.stillDashLocked(state.locked, state.stamina);
    }

    public static State step(State before, Frame frame, Rules rules) {
        int stamina = Math.max(0, Math.min(rules.maximum, before.stamina));
        boolean locked = RaceScoring.stillDashLocked(before.locked, stamina);
        if (dashing(new State(stamina, locked), frame)) {
            long hash = rules.seed + frame.sequence * 0x9E3779B97F4A7C15L;
            hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
            hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
            int roll = (int) Long.remainderUnsigned(hash ^ (hash >>> 31), 100);
            if (!RaceScoring.intelSkipsDashDrain(rules.intelligence, frame.sequence, roll)) stamina--;
            if (stamina == 0) locked = true;
        } else {
            stamina = Math.min(rules.maximum, stamina + (!frame.moving ? 2 : frame.sequence % 3 == 0 ? 1 : 0));
        }
        return new State(stamina, locked);
    }

    /** Rebase on an authoritative acknowledgement, then replay only unacknowledged input. */
    public static final class Client {
        private final ArrayDeque<Frame> pending = new ArrayDeque<>();
        private State state;
        private Rules rules;
        private int sequence, acknowledged;

        public Client(int sequence, State state, Rules rules) {
            this.sequence = this.acknowledged = sequence;
            this.state = state;
            this.rules = rules;
        }
        public State state() { return state; }
        public boolean canAdvance() { return pending.size() < MAX_PENDING; }
        public List<Frame> pending() { return List.copyOf(pending); }
        public Frame advance(boolean dash, boolean moving) {
            if (!canAdvance()) return null;
            Frame frame = new Frame(++sequence, dash, moving);
            pending.addLast(frame);
            state = step(state, frame, rules);
            return frame;
        }
        public void acknowledge(int ack, State authoritative, Rules authoritativeRules) {
            if (ack < acknowledged || ack > sequence) return;
            acknowledged = ack;
            rules = authoritativeRules;
            while (!pending.isEmpty() && pending.peekFirst().sequence <= ack) pending.removeFirst();
            state = authoritative;
            for (Frame frame : pending) state = step(state, frame, rules);
        }
    }

    /** Ordered, bounded input queue: bursts cannot buy more simulation time than has elapsed. */
    public static final class Server {
        private final ArrayDeque<Frame> pending = new ArrayDeque<>();
        private final long started;
        private int accepted, processed;
        public Server(long now) { started = now; }
        public int processed() { return processed; }
        public boolean offer(Frame frame) {
            if (frame.sequence <= accepted) return true; // duplicate: never charge twice
            if (frame.sequence != accepted + 1 || pending.size() >= MAX_PENDING) return false;
            pending.addLast(frame);
            accepted = frame.sequence;
            return true;
        }
        public Frame poll(long now) {
            long budget = Math.max(0, now - started) / 50_000_000L + 2;
            if (pending.isEmpty() || pending.peekFirst().sequence > budget) return null;
            Frame frame = pending.removeFirst();
            processed = frame.sequence;
            return frame;
        }
    }
}
