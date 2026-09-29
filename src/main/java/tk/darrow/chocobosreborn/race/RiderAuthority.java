package tk.darrow.chocobosreborn.race;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.net.RaceMovePayloads;

/**
 * The rider drives their own bird; the server checks the drive is possible and otherwise takes it.
 *
 * <p>Vanilla replays every vehicle packet through the server's own physics and refuses it when
 * the result lands more than a quarter block from where the client said, or when a tick's
 * packets add up to more than ten blocks. Client and server physics never agree exactly on a
 * racing bird (steps, ridges, bumps against birds the two sides see in different places), so a
 * rider was snapped back over and over; and nothing told the client when the server itself had
 * moved the bird, so after a set-back every packet already in flight was refused in turn.
 *
 * <p>Here a rider's packet is taken as long as it is possible: within the bird's speed budget
 * ({@link #PER_TICK} blocks a server tick, banked up to {@link #BANK_TICKS} ticks so a backlog
 * after a stall catches up), no single step longer than {@link #STEP_CAP}, and not through a
 * wall. The server still replays the step for its side effects (fall damage, pressure plates,
 * portals) but keeps the client's result. A refused packet, and every move the server makes
 * itself ({@link RaceSession#moveRidden}), is a numbered {@link RaceMovePayloads.Teleport}:
 * packets the client sent before it answered are dropped without a correction, so one refusal
 * is one snap, never a chain of them.
 */
public final class RiderAuthority {
	/** The budget's numbers live in {@link RiderBudget} (pure, unit-tested). */
	public static final double PER_TICK = RiderBudget.PER_TICK;
	public static final double BANK_TICKS = RiderBudget.BANK_TICKS;
	public static final double STEP_CAP = RiderBudget.STEP_CAP;
	static final int RESEND_TICKS = 20;
	static final int GIVE_UP_TICKS = 200;
	/**
	 * Off with {@code -Dchocobosreborn.vanillaRiderMovement=true} on the server: riders' birds go back
	 * to vanilla's replay-and-refuse (for comparison runs, or if this ever misbehaves on a live server).
	 */
	public static final boolean ENABLED = !Boolean.getBoolean("chocobosreborn.vanillaRiderMovement");
	/**
	 * Race harness only: players whose client is simulated inside this server (they have no
	 * network channel). Their race packets are handed over here instead of sent.
	 */
	public interface VirtualClients {
		boolean isVirtual(ServerPlayer player);

		void teleport(ServerPlayer player, RaceMovePayloads.Teleport teleport);

		void frame(ServerPlayer player, RaceMovePayloads.Frame frame);
	}

	public static VirtualClients VIRTUAL;
	/** Set by GameTests: mock connections have no channel, so teleports are recorded instead of sent. */
	public static boolean testCapture;
	public static RaceMovePayloads.Teleport lastCaptured;

	private static final class State {
		int entityId = -1;
		int nextId;
		int awaiting;
		long sentAt;
		long firstSentAt;
		double budget = PER_TICK * BANK_TICKS;
		long budgetTick = Long.MIN_VALUE;
		int refused;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private RiderAuthority() {}

	private static State state(ServerPlayer rider, ChocoboEntity bird) {
		State s = STATES.computeIfAbsent(rider.getUUID(), u -> new State());
		if (s.entityId != bird.getId()) {
			s.entityId = bird.getId();
			s.awaiting = 0;
			s.budget = PER_TICK * BANK_TICKS;
			s.budgetTick = Long.MIN_VALUE;
		}
		return s;
	}

	/** A teleport of this rider's bird is unanswered: drop their packet, it was sent from where the bird was. */
	public static boolean awaiting(ServerPlayer rider, ChocoboEntity bird) {
		State s = STATES.get(rider.getUUID());
		return s != null && s.entityId == bird.getId() && s.awaiting != 0;
	}

	/** The server moved {@code bird} (already at its new place): tell its rider, and wait for the answer. */
	public static void teleport(ServerPlayer rider, ChocoboEntity bird) {
		if (!ENABLED) {
			return;
		}
		State s = state(rider, bird);
		s.awaiting = ++s.nextId;
		if (s.awaiting == 0) {
			s.awaiting = ++s.nextId;
		}
		long now = bird.level().getGameTime();
		s.sentAt = now;
		s.firstSentAt = now;
		s.budget = PER_TICK * BANK_TICKS;
		s.budgetTick = now;
		send(rider, bird, s);
	}

	private static void send(ServerPlayer rider, ChocoboEntity bird, State s) {
		var payload = new RaceMovePayloads.Teleport(bird.getId(), s.awaiting, bird.getX(), bird.getY(), bird.getZ(),
				bird.getYRot(), bird.getXRot());
		if (testCapture) {
			lastCaptured = payload;
			return;
		}
		if (VIRTUAL != null && VIRTUAL.isVirtual(rider)) {
			VIRTUAL.teleport(rider, payload);
			return;
		}
		// A connection that never negotiated the channel (a GameTest mock player) cannot take it.
		if (!rider.connection.hasChannel(RaceMovePayloads.Teleport.TYPE)) {
			s.awaiting = 0;
			return;
		}
		PacketDistributor.sendToPlayer(rider, payload);
	}

	/** The rider's client applied teleport {@code id}: packets from now on start where it put the bird. */
	public static void ack(ServerPlayer rider, int entityId, int id) {
		State s = STATES.get(rider.getUUID());
		if (s != null && s.entityId == entityId && s.awaiting == id) {
			s.awaiting = 0;
		}
	}

	/**
	 * Why this step cannot be the bird's, or null when it can. Spends the step from the bird's
	 * budget when it is taken.
	 */
	public static String refuse(ServerLevel level, ServerPlayer rider, ChocoboEntity bird,
	                            double fx, double fy, double fz, double tx, double ty, double tz) {
		State s = state(rider, bird);
		long now = level.getGameTime();
		double dx = tx - fx, dy = ty - fy, dz = tz - fz;
		double step = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (step > STEP_CAP) {
			return "step " + String.format(java.util.Locale.ROOT, "%.2f", step) + " over " + STEP_CAP;
		}
		double budget = refill(s.budget, s.budgetTick, now);
		if (step > budget) {
			return "speed: " + String.format(java.util.Locale.ROOT, "%.2f", step) + " with "
					+ String.format(java.util.Locale.ROOT, "%.2f", budget) + " banked";
		}
		if (passesThroughWall(level, bird, fx, fy, fz, tx, ty, tz)) {
			return "through a wall";
		}
		s.budget = budget - step;
		s.budgetTick = now;
		return null;
	}

	public static double refill(double budget, long last, long now) {
		return RiderBudget.refill(budget, last, now);
	}

	/** Count a refusal against the rider (logged by the caller). */
	public static int refused(ServerPlayer rider) {
		State s = STATES.get(rider.getUUID());
		return s == null ? 0 : ++s.refused;
	}

	/**
	 * Would the bird's body pass through a block on the straight way from one position to the
	 * next? The box is narrowed a little and lifted by the bird's step height, so the ground,
	 * steps and a wall it brushes do not count; only a block the middle of the bird would go
	 * through does. A bird already inside something (stuck) is never refused for it.
	 */
	public static boolean passesThroughWall(ServerLevel level, ChocoboEntity bird,
	                                        double fx, double fy, double fz, double tx, double ty, double tz) {
		double dx = tx - fx, dy = ty - fy, dz = tz - fz;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 0.5D) {
			return false;
		}
		EntityDimensions d = bird.getDimensions(bird.getPose());
		double lift = bird.maxUpStep() + 0.1D;
		if (core(level, bird, d, fx, fy, fz, lift)) {
			return false;
		}
		int n = (int) Math.ceil(length / 0.4D);
		for (int i = 1; i <= n; i++) {
			double f = i / (double) n;
			if (core(level, bird, d, fx + dx * f, fy + dy * f, fz + dz * f, lift)) {
				return true;
			}
		}
		return false;
	}

	private static boolean core(ServerLevel level, ChocoboEntity bird, EntityDimensions d, double x, double y, double z, double lift) {
		AABB box = d.makeBoundingBox(x, y, z);
		double top = Math.max(box.minY + lift + 0.1D, box.maxY - 0.3D);
		AABB core = new AABB(box.minX + 0.3D, box.minY + lift, box.minZ + 0.3D, box.maxX - 0.3D, top, box.maxZ - 0.3D);
		return !level.noCollision(bird, core);
	}

	/** Resend an unanswered teleport each second; give up after ten (the rider left or runs another build). */
	public static void tick(MinecraftServer server) {
		if (STATES.isEmpty()) {
			return;
		}
		for (var it = STATES.entrySet().iterator(); it.hasNext(); ) {
			var e = it.next();
			State s = e.getValue();
			if (s.awaiting == 0) {
				continue;
			}
			ServerPlayer rider = server.getPlayerList().getPlayer(e.getKey());
			if (rider == null) {
				it.remove();
				continue;
			}
			if (!(rider.level().getEntity(s.entityId) instanceof ChocoboEntity bird)) {
				s.awaiting = 0;
				continue;
			}
			long now = bird.level().getGameTime();
			if (now - s.firstSentAt > GIVE_UP_TICKS) {
				ChocobosReborn.LOGGER.warn("{} never answered teleport {} of {}; taking their moves again",
						rider.getName().getString(), s.awaiting, bird.getName().getString());
				s.awaiting = 0;
			} else if (now - s.sentAt >= RESEND_TICKS) {
				s.sentAt = now;
				send(rider, bird, s);
			}
		}
	}

	public static void forget(ServerPlayer rider) {
		STATES.remove(rider.getUUID());
	}
}
