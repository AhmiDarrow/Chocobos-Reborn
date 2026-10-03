package tk.darrow.chocobosreborn.client;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.net.FrameBuffer;
import tk.darrow.chocobosreborn.net.PlayoutClock;
import tk.darrow.chocobosreborn.net.RaceMovePayloads;

/**
 * This client's side of race movement.
 *
 * <p>Racing birds it does not drive are shown from their tick-stamped server frames, a short,
 * adaptive delay behind the fastest of them ({@link PlayoutClock}), interpolated between frames
 * ({@link FrameBuffer}). Each client tick sets a bird to where it stood at the shown tick, and
 * the game draws it between its last two tick positions, so the field glides at any frame
 * rate however the frames bunch up on the way.
 *
 * <p>A numbered teleport of the bird this client drives is applied at once and answered, so
 * the server takes its moves again from the new place.
 */
public final class RemoteRaceFrames implements ChocoboEntity.RemoteDisplay {
	public static final RemoteRaceFrames INSTANCE = new RemoteRaceFrames();
	/** Server teleports of this client's bird applied (the harness reports it; a refusal is one of them). */
	public static final AtomicInteger TELEPORTS = new AtomicInteger();
	private static final double NANOS_PER_TICK = 50_000_000.0D;
	/** A bird with no frame for a second has left the race (or the view): vanilla's packets take it back. */
	private static final int STALE_TICKS = 20;

	private record Arrival(RaceMovePayloads.Frame frame, long nanos) {}

	private final ConcurrentLinkedQueue<Arrival> incoming = new ConcurrentLinkedQueue<>();
	private final Map<Integer, FrameBuffer> buffers = new HashMap<>();
	private final Map<Integer, Integer> lastFrame = new HashMap<>();
	private final PlayoutClock clock = new PlayoutClock();
	private int clientTick;
	private double shownTick = Double.NaN;

	private RemoteRaceFrames() {}

	/** Network thread. */
	public void receive(RaceMovePayloads.Frame frame, long arrivedNanos) {
		incoming.add(new Arrival(frame, arrivedNanos));
		// Frames come every server tick; a long silence mid-race shows as the field freezing, then catching up.
		// Logged so a rough race on a real server can be read back from the client log (full-pack harness,
		// 2026-10-03: the whole connection went quiet for 0.5-3.4 s at a time, the server still ticking).
		long last = lastArrivalNanos;
		lastArrivalNanos = arrivedNanos;
		int gapMs = (int) ((arrivedNanos - last) / 1_000_000L);
		// queued frames still arrive in order: after a stall the next one is the next tick's (between races the tick jumps)
		if (last != 0L && gapMs > STALL_LOG_MS && frame.tick() <= lastFrameTick + 3) {
			tk.darrow.chocobosreborn.ChocobosReborn.LOGGER.warn("Race frames stalled {} ms (server ticks {} -> {})", gapMs, lastFrameTick, frame.tick());
		}
		lastFrameTick = Math.max(lastFrameTick, frame.tick());
	}

	/** Gap between frame arrivals worth a log line: five ticks. */
	private static final int STALL_LOG_MS = 250;
	private volatile long lastArrivalNanos;
	private volatile int lastFrameTick = Integer.MIN_VALUE / 2;

	/** Before the level ticks: take this tick's frames and choose the tick the field is shown at. */
	public void tick(ClientTickEvent.Pre event) {
		if (Minecraft.getInstance().level == null) {
			clear();
			return;
		}
		clientTick++;
		for (Arrival a; (a = incoming.poll()) != null; ) {
			RaceMovePayloads.Frame f = a.frame();
			clock.observe(a.nanos() / NANOS_PER_TICK, f.tick());
			buffers.computeIfAbsent(f.entityId(), id -> new FrameBuffer()).add(new FrameBuffer.Snap(f.tick(),
					f.x(), f.y(), f.z(), f.yRot(), f.bodyRot(), f.onGround()));
			lastFrame.put(f.entityId(), clientTick);
		}
		lastFrame.entrySet().removeIf(e -> {
			if (clientTick - e.getValue() > STALE_TICKS) {
				buffers.remove(e.getKey());
				return true;
			}
			return false;
		});
		shownTick = buffers.isEmpty() ? Double.NaN : clock.advance(System.nanoTime() / NANOS_PER_TICK);
	}

	@Override
	public boolean owns(int entityId) {
		return !Double.isNaN(shownTick) && buffers.containsKey(entityId);
	}

	@Override
	public boolean place(ChocoboEntity bird) {
		if (Double.isNaN(shownTick)) {
			return false;
		}
		FrameBuffer buffer = buffers.get(bird.getId());
		FrameBuffer.Snap s = buffer == null ? null : buffer.sample(shownTick);
		if (s == null) {
			return false;
		}
		bird.setPos(s.x(), s.y(), s.z());
		bird.setYRot(s.yRot());
		bird.yBodyRot = s.bodyRot();
		bird.setOnGround(s.onGround());
		return true;
	}

	@Override
	public double behindTicks() {
		return Double.isNaN(shownTick) ? -1.0D : clock.behind(System.nanoTime() / NANOS_PER_TICK);
	}

	/** Ticks the field is shown behind its fastest frames right now (the harness reports it). */
	public double delayTicks() {
		return clock.ready() ? clock.delay() : 0.0D;
	}

	/** The server moved the bird this client drives: take the new place, then answer. */
	public static void applyTeleport(RaceMovePayloads.Teleport p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (mc.level.getEntity(p.entityId()) instanceof ChocoboEntity bird && bird.isControlledByLocalInstance()) {
			bird.moveTo(p.x(), p.y(), p.z(), p.yRot(), p.xRot());   // also its old position: no streak across the course
			bird.adoptVehicleCorrection();
			if (mc.player.getVehicle() == bird) {
				bird.positionRider(mc.player);
				mc.player.setOldPosAndRot();
			}
			TELEPORTS.incrementAndGet();
		}
		PacketDistributor.sendToServer(new RaceMovePayloads.TeleportAck(p.entityId(), p.id()));
	}

	public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		clear();
	}

	private void clear() {
		incoming.clear();
		buffers.clear();
		lastFrame.clear();
		clock.reset();
		shownTick = Double.NaN;
	}
}
