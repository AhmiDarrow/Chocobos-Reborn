package tk.darrow.chocobosreborn.net;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;

class RiderPredictionTest {
    private static final RiderPrediction.Rules RULES = new RiderPrediction.Rules(255, 0, 9173);
    private record Ack(int due, int sequence, RiderPrediction.State state) {}

    @Test void delayedAndBatchedAcknowledgementsDoNotChangeLocalDashDecisions() {
        for (int intelligence : new int[]{0, 50, 100}) for (int delay : new int[]{0, 3, 6, 12}) {
            var rules = new RiderPrediction.Rules(255, intelligence, 9173);
            var initial = new RiderPrediction.State(255, false);
            var client = new RiderPrediction.Client(0, initial, rules);
            var server = new RiderPrediction.Server(0);
            var authority = initial;
            var ideal = initial;
            var outbound = new ArrayDeque<RiderPrediction.Frame>();
            var replies = new ArrayDeque<Ack>();
            for (int tick = 1; tick <= 3000; tick++) {
                while (!replies.isEmpty() && replies.peekFirst().due <= tick) {
                    var ack = replies.removeFirst();
                    client.acknowledge(ack.sequence, ack.state, rules);
                }
                var predictedBefore = client.state();
                var frame = client.advance(tick % 400 < 240, tick % 601 != 0);
                assertNotNull(frame);
                assertEquals(RiderPrediction.dashing(ideal, frame), RiderPrediction.dashing(
                        predictedBefore, frame));
                ideal = RiderPrediction.step(ideal, frame, rules);
                assertEquals(ideal, client.state(), "local state must not depend on ACK timing");
                outbound.addLast(frame);
                // A periodic 600 ms stall delivers an ordered burst, just like the TCP proxy.
                if (tick % 400 >= 12) {
                    while (!outbound.isEmpty() && outbound.peekFirst().sequence() + delay <= tick) {
                        assertTrue(server.offer(outbound.removeFirst()));
                    }
                }
                RiderPrediction.Frame received;
                while ((received = server.poll(tick * 50_000_000L)) != null) {
                    authority = RiderPrediction.step(authority, received, rules);
                    if (received.sequence() % 5 == 0) replies.addLast(new Ack(tick + delay, received.sequence(), authority));
                }
            }
            while (!outbound.isEmpty()) assertTrue(server.offer(outbound.removeFirst()));
            RiderPrediction.Frame frame;
            while ((frame = server.poll(3000L * 50_000_000L)) != null) authority = RiderPrediction.step(authority, frame, rules);
            client.acknowledge(server.processed(), authority, rules);
            assertEquals(3000, server.processed());
            assertEquals(ideal, authority);
            assertEquals(ideal, client.state());
            assertTrue(client.pending().isEmpty());
        }
    }

    @Test void duplicateInputsNeverChargeTwiceAndGapsRequireResend() {
        var server = new RiderPrediction.Server(0);
        var first = new RiderPrediction.Frame(1, true, true);
        assertFalse(server.offer(new RiderPrediction.Frame(2, true, true)));
        assertTrue(server.offer(first));
        assertTrue(server.offer(first));
        assertEquals(first, server.poll(0));
        assertNull(server.poll(0));
        assertTrue(server.offer(first));
        assertNull(server.poll(0));
    }

    @Test void packetFloodCannotCreateSimulationTimeOrUnboundedQueues() {
        var server = new RiderPrediction.Server(0);
        for (int i = 1; i <= RiderPrediction.MAX_PENDING; i++) assertTrue(server.offer(new RiderPrediction.Frame(i, true, true)));
        assertFalse(server.offer(new RiderPrediction.Frame(RiderPrediction.MAX_PENDING + 1, true, true)));
        assertNotNull(server.poll(0));
        assertNotNull(server.poll(0));
        assertNull(server.poll(0));
        assertEquals(2, server.processed());
        assertNotNull(server.poll(50_000_000L));
        assertNull(server.poll(50_000_000L));
    }

    @Test void correctionsRebaseAndReplayWithoutReplenishingStamina() {
        var client = new RiderPrediction.Client(0, new RiderPrediction.State(255, false), RULES);
        for (int i = 0; i < 10; i++) client.advance(true, true);
        client.acknowledge(5, new RiderPrediction.State(100, false), RULES);
        assertEquals(95, client.state().stamina());
        client.acknowledge(4, new RiderPrediction.State(255, false), RULES);
        client.acknowledge(11, new RiderPrediction.State(255, false), RULES);
        assertEquals(95, client.state().stamina());
        client.acknowledge(5, new RiderPrediction.State(100, false), RULES);
        assertEquals(95, client.state().stamina());
    }

    @Test void prolongedOutageBoundsPendingHistoryAndStopsPrediction() {
        var client = new RiderPrediction.Client(0, new RiderPrediction.State(255, false), RULES);
        for (int i = 0; i < RiderPrediction.MAX_PENDING; i++) assertNotNull(client.advance(true, true));
        var before = client.state();
        assertFalse(client.canAdvance());
        assertNull(client.advance(true, true));
        assertEquals(before, client.state());
        assertEquals(RiderPrediction.MAX_PENDING, client.pending().size());
    }

    @Test void lastPointBuysOneDashFrameAndLockRecoversIdentically() {
        var state = new RiderPrediction.State(1, false);
        var frame = new RiderPrediction.Frame(1, true, true);
        assertTrue(RiderPrediction.dashing(state, frame));
        state = RiderPrediction.step(state, frame, RULES);
        assertEquals(new RiderPrediction.State(0, true), state);
        for (int sequence = 2; sequence <= 150; sequence++) {
            frame = new RiderPrediction.Frame(sequence, true, true);
            assertFalse(RiderPrediction.dashing(state, frame));
            state = RiderPrediction.step(state, frame, RULES);
        }
        assertEquals(50, state.stamina());
        assertTrue(RiderPrediction.dashing(state, new RiderPrediction.Frame(151, true, true)));
    }
}
