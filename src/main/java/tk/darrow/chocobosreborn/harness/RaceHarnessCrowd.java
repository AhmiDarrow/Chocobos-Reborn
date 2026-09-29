package tk.darrow.chocobosreborn.harness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.entity.ModEntities;
import tk.darrow.chocobosreborn.net.FrameBuffer;
import tk.darrow.chocobosreborn.net.PlayoutClock;
import tk.darrow.chocobosreborn.net.RaceMovePayloads;
import tk.darrow.chocobosreborn.race.RaceManager;
import tk.darrow.chocobosreborn.race.RaceSession;
import tk.darrow.chocobosreborn.race.RaceTrack;
import tk.darrow.chocobosreborn.race.RacerGoal;
import tk.darrow.chocobosreborn.race.RacerProfile;
import tk.darrow.chocobosreborn.race.RiderAuthority;
import tk.darrow.chocobosreborn.race.Square;

/**
 * Development only: a crowded heat on the dedicated latency server. The one real client
 * (LatencyRider, through the delay proxy) races {@code chocobosreborn.harness.crowd - 1} simulated
 * players and the AI that fill the field ({@code chocobosreborn.race.field}).
 *
 * <p>A simulated player is an ordinary {@link ServerPlayer} on the player list, on a mock
 * connection (as GameTests make them); everything the server sends it is read back from that
 * connection or handed over by {@link RiderAuthority.VirtualClients}. Its "client" runs here: a
 * copy of its bird, never added to the level ({@link ChocoboEntity#mirrorOf}), driven by the racing
 * AI the way a rider drives; each tick its position goes to the server as a real vehicle packet
 * through the rider's own link (latency, jitter, stalls), and the server's replies (teleports,
 * player teleports, keep-alives, frames) come back through it. It also plays the field back from
 * its frames exactly as a client does and records what it would have shown
 * ({@code motion-Virtual<n>.csv}), so smoothness is measured under every link, not just one.
 */
@EventBusSubscriber(modid = ChocobosReborn.MOD_ID)
public final class RaceHarnessCrowd implements RiderAuthority.VirtualClients {
	/** Humans in the heat, the real clients included; 0 or 1 leaves the ordinary harness in charge. */
	static final int HUMANS = Integer.getInteger("chocobosreborn.harness.crowd", 0);
	/**
	 * Real clients to wait for, by name ({@code chocobosreborn.harness.crowdNames}, comma-separated; the
	 * crowd race launcher starts them). Empty: the one real client LatencyRider, and simulated players for the rest.
	 */
	static final List<String> NAMES = java.util.Arrays.stream(System.getProperty("chocobosreborn.harness.crowdNames", "").split(","))
			.map(String::trim).filter(n -> !n.isEmpty()).toList();
	/** Simulated players on top of the real clients. */
	static final int SIMULATED = Integer.getInteger("chocobosreborn.harness.simulated", NAMES.isEmpty() ? Math.max(0, HUMANS - 1) : 0);
	private static final int MAX_TICKS = 7200;
	private static final Path OUT = Path.of(System.getProperty("chocobosreborn.harness.output", "build/latency/results"));
	private static final RaceHarnessCrowd INSTANCE = new RaceHarnessCrowd();

	/** One-way latency (ms), jitter (+- ms), and a stall of {@code burst} ms every {@code every} ms, per simulated player. */
	private static final double[][] LINKS = {
			{10, 2, 0, 0}, {25, 5, 0, 0}, {40, 10, 0, 0}, {60, 15, 0, 0}, {75, 20, 0, 0},
			{100, 30, 15000, 250}, {125, 40, 0, 0}, {150, 50, 0, 0}, {60, 30, 10000, 400},
	};

	private static final List<VirtualRider> riders = new ArrayList<>();
	private static final Map<UUID, VirtualRider> byPlayer = new HashMap<>();
	private static RaceSession session;
	private static final List<ServerPlayer> reals = new ArrayList<>();
	private static final List<ChocoboEntity> realBirds = new ArrayList<>();
	/** Real clients already taken to the square: the first to join must not wait in the overworld's night. */
	private static final java.util.Set<UUID> placed = new java.util.HashSet<>();
	private static boolean setup, complete;
	private static int wait = 400, ticks;
	private static long tickStart;

	public static boolean active() {
		return "server".equals(System.getProperty("chocobosreborn.harness")) && HUMANS > 1;
	}

	private static RaceTrack track() {
		return RaceTrack.valueOf(System.getProperty("chocobosreborn.harness.track", "C_MEADOW"));
	}

	private static void append(String file, String text) {
		try {
			Files.createDirectories(OUT);
			Files.writeString(OUT.resolve(file), text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}

	// ----------------------------------------------------------------- VirtualClients

	@Override
	public boolean isVirtual(ServerPlayer player) {
		return byPlayer.containsKey(player.getUUID());
	}

	@Override
	public void teleport(ServerPlayer player, RaceMovePayloads.Teleport teleport) {
		VirtualRider r = byPlayer.get(player.getUUID());
		if (r != null) r.down(() -> r.applyTeleport(teleport));
	}

	@Override
	public void frame(ServerPlayer player, RaceMovePayloads.Frame frame) {
		VirtualRider r = byPlayer.get(player.getUUID());
		if (r != null) {
			r.down(() -> r.receiveFrame(frame));
		}
	}

	// ----------------------------------------------------------------- ticking

	@SubscribeEvent
	public static void pre(ServerTickEvent.Pre event) {
		if (!active() || complete) return;
		tickStart = System.nanoTime();
		// what the simulated clients sent arrives with the rest of this tick's packets
		for (VirtualRider r : riders) r.clientTick();
	}

	@SubscribeEvent
	public static void post(ServerTickEvent.Post event) {
		if (!active() || complete) return;
		MinecraftServer server = event.getServer();
		List<String> wanted = NAMES.isEmpty() ? List.of("LatencyRider") : NAMES;
		List<ServerPlayer> online = new ArrayList<>();
		for (String name : wanted) {
			ServerPlayer p = server.getPlayerList().getPlayerByName(name);
			if (p == null) continue;
			online.add(p);
			if (!setup && placed.add(p.getUUID())) {
				p.setGameMode(GameType.SURVIVAL);
				RaceManager.enterSquareOnFoot(p);
			}
		}
		if (online.size() < wanted.size()) {
			if (session != null) {
				// a real client dropped out mid-race: end it, the clients stop on done.txt
				append("events.jsonl", "{\"event\":\"error\",\"reason\":\"participant_disconnected\",\"online\":" + online.size() + "}\n");
				if (session.live()) session.abort();
				append("done.txt", "failed");
				complete = true;
			}
			return;
		}
		// a real connection ticks its player after the level; a mock connection is not on the server's list
		for (VirtualRider r : riders) r.player.connection.tick();
		if (!setup) {
			setup = true;
			RiderAuthority.VIRTUAL = INSTANCE;
			if (NAMES.isEmpty()) configureRealLink();
			for (ServerPlayer real : online) {
				reals.add(real);
				realBirds.add(bird(real));
			}
			for (int i = 1; i <= SIMULATED; i++) {
				VirtualRider r = new VirtualRider(server, i, LINKS[(i - 1) % LINKS.length]);
				riders.add(r);
				byPlayer.put(r.player.getUUID(), r);
			}
			append("events.jsonl", "{\"event\":\"crowd\",\"real\":" + reals.size() + ",\"simulated\":" + riders.size()
					+ ",\"field\":" + RaceSession.FIELD + "}\n");
			return;
		}
		if (session == null) {
			if (--wait > 0) return;
			RaceTrack track = track();
			List<ServerPlayer> players = new ArrayList<>();
			List<ChocoboEntity> birds = new ArrayList<>();
			for (int i = 0; i < reals.size(); i++) {
				RaceHarnessBirds.dress(realBirds.get(i), track);
				reals.get(i).startRiding(realBirds.get(i), true);
				players.add(reals.get(i));
				birds.add(realBirds.get(i));
			}
			for (VirtualRider r : riders) {
				RaceHarnessBirds.dress(r.bird, track);
				r.player.startRiding(r.bird, true);
				players.add(r.player);
				birds.add(r.bird);
			}
			session = new RaceSession(players.get(0).serverLevel(), track, false, players, birds, false, 0);
			try {
				var register = RaceManager.class.getDeclaredMethod("addSession", RaceSession.class);
				register.setAccessible(true);
				register.invoke(null, session);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
			for (int i = 0; i < riders.size(); i++) riders.get(i).startClient(track, reals.size() + i);
			ticks = 0;
			append("events.jsonl", "{\"event\":\"start\",\"track\":\"" + track.name() + "\",\"humans\":" + players.size()
					+ ",\"field\":" + session.fieldSize() + ",\"laps\":" + track.getLaps() + "}\n");
			return;
		}
		ticks++;
		double tickMs = (System.nanoTime() - tickStart) / 1e6;
		int[] kinds = overlapKinds(players0());
		append("crowd-server.csv", String.format(Locale.ROOT, "%s,%d,%.3f,%d,%d,%d,%d,%d,%.3f%n", track().name(), ticks, tickMs,
				session.fieldSize(), kinds[0] + kinds[1] + kinds[2], kinds[0], kinds[1], kinds[2], kinds[3] / 1000.0));
		if (ticks % 20 == 0) for (VirtualRider r : riders) {
			r.flush();
			r.trace(ticks);
		}
		if (!session.live() || ticks > MAX_TICKS) {
			boolean timeout = session.live();
			String report = session.progressReport();
			if (timeout) session.abort();
			append("events.jsonl", "{\"event\":\"end\",\"track\":\"" + track().name() + "\",\"ticks\":" + ticks
					+ ",\"timeout\":" + timeout + ",\"report\":\"" + report + "\"}\n");
			for (VirtualRider r : riders) {
				r.flush();
				append("crowd-riders.csv", r.summary() + "\n");
				server.getPlayerList().remove(r.player);
			}
			RiderAuthority.VIRTUAL = null;
			append("done.txt", "complete");
			complete = true;
		}
	}

	/** Any racing bird in the level of the heat's first rider. */
	private static ServerLevel players0() {
		return reals.isEmpty() ? null : reals.get(0).serverLevel();
	}

	/**
	 * Pairs of solid racers whose bodies overlap on the server this tick (they should never), by kind:
	 * {rider and rider, rider and AI, AI and AI, deepest overlap in thousandths of a block}.
	 */
	private static int[] overlapKinds(ServerLevel level) {
		int[] out = new int[4];
		if (level == null || realBirds.isEmpty()) return out;
		List<ChocoboEntity> birds = level.getEntitiesOfClass(ChocoboEntity.class,
				realBirds.get(0).getBoundingBox().inflate(2048.0D, 256.0D, 2048.0D), ChocoboEntity::contactSolid);
		for (int i = 0; i < birds.size(); i++) {
			for (int j = i + 1; j < birds.size(); j++) {
				net.minecraft.world.phys.AABB a = birds.get(i).getBoundingBox(), b = birds.get(j).getBoundingBox();
				if (!a.deflate(0.1D).intersects(b.deflate(0.1D))) continue;
				int riders = (birds.get(i).getControllingPassenger() instanceof ServerPlayer ? 1 : 0)
						+ (birds.get(j).getControllingPassenger() instanceof ServerPlayer ? 1 : 0);
				out[riders == 2 ? 0 : riders == 1 ? 1 : 2]++;
				double depth = Math.min(Math.min(a.maxX, b.maxX) - Math.max(a.minX, b.minX), Math.min(a.maxZ, b.maxZ) - Math.max(a.minZ, b.minZ));
				out[3] = Math.max(out[3], (int) Math.round(depth * 1000.0D));
			}
		}
		return out;
	}

	private static void configureRealLink() {
		try {
			Files.createDirectories(OUT);
			// the real client: a 120 ms round trip with +-20 ms of jitter each way
			Files.writeString(OUT.resolve("network.json"), "{\"delay_ms\":60,\"jitter_ms\":20,\"burst_ms\":0}");
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}

	/** A saddled, tamed bird for {@code owner}, waiting at the square's arrival point. */
	private static ChocoboEntity bird(ServerPlayer owner) {
		ServerLevel level = owner.serverLevel();
		ChocoboEntity b = ModEntities.CHOCOBO.get().create(level);
		b.moveTo(Square.ARRIVAL.x, Square.ARRIVAL.y, Square.ARRIVAL.z, 0, 0);
		b.setAge(0);
		RaceHarnessBirds.dress(b, track());
		b.setSaddledForPreview(true);
		b.tame(owner);
		b.setOrderedToSit(false);
		level.addFreshEntity(b);
		return b;
	}

	// ----------------------------------------------------------------- one simulated player

	private static final class Link {
		final double base, jitter, every, burst;
		final Random rng;
		double lastDue;

		Link(double[] profile, long seed) {
			base = profile[0];
			jitter = profile[1];
			every = profile[2];
			burst = profile[3];
			rng = new Random(seed);
		}

		/** When something sent now arrives: in order, like TCP. */
		double due(double nowMs) {
			double d = nowMs + base + (rng.nextDouble() * 2.0D - 1.0D) * jitter;
			if (every > 0.0D) {
				double phase = nowMs % every;
				if (phase < burst) d = Math.max(d, nowMs - phase + burst + base);
			}
			d = Math.max(d, lastDue);
			lastDue = d;
			return d;
		}
	}

	private record Timed(double due, Runnable action) {}

	private static final class VirtualRider {
		final int index;
		final ServerPlayer player;
		final Connection connection;
		final ChocoboEntity bird;
		final Link up, down;
		final ArrayDeque<Timed> toServer = new ArrayDeque<>();
		final ArrayDeque<Timed> toClient = new ArrayDeque<>();
		final PlayoutClock clock = new PlayoutClock();
		final Map<Integer, FrameBuffer> buffers = new HashMap<>();
		final StringBuilder motion = new StringBuilder();
		ChocoboEntity shadow;
		RacerGoal goal;
		/** The hold has lifted once: the race is on for this client. */
		boolean released;
		RaceTrack track;
		int clientTicks, teleports, corrections, playerTeleports, frames;

		VirtualRider(MinecraftServer server, int index, double[] profile) {
			this.index = index;
			String name = "Virtual" + index;
			CommonListenerCookie cookie = CommonListenerCookie.createInitial(
					new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name), false);
			ServerLevel level = server.overworld();
			this.player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
			this.connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			server.getPlayerList().placeNewPlayer(connection, player, cookie);
			player.setGameMode(GameType.SURVIVAL);
			RaceManager.enterSquareOnFoot(player);
			this.bird = bird(player);
			this.up = new Link(profile, 1000L + index);
			this.down = new Link(profile, 2000L + index);
		}

		void down(Runnable action) {
			toClient.addLast(new Timed(down.due(now()), action));
		}

		void up(Runnable action) {
			toServer.addLast(new Timed(up.due(now()), action));
		}

		static double now() {
			return System.nanoTime() / 1e6;
		}

		/** The rider's client: its own copy of the bird, driven the way a rider drives. */
		void startClient(RaceTrack track, int stall) {
			this.track = track;
			ServerLevel level = bird.level() instanceof ServerLevel l ? l : null;
			if (level == null) return;
			ChocoboEntity copy = ModEntities.CHOCOBO.get().create(level);
			CompoundTag tag = bird.saveWithoutId(new CompoundTag());
			copy.load(tag);
			copy.setUUID(UUID.randomUUID());
			copy.mirrorOf = bird;
			copy.moveTo(bird.getX(), bird.getY(), bird.getZ(), bird.getYRot(), 0.0F);
			copy.setRacing(true);
			copy.setRaceTrack(track.ordinal());
			RacerGoal goal = new RacerGoal(copy, track, RaceTrack.stallOffset(stall, RaceSession.FIELD),
					RacerProfile.of(track.getRaceClass(), RacerProfile.Role.FIELD));
			goal.speed = 1.0D + 0.05D * (new Random(index).nextDouble() * 2.0D - 1.0D);
			goal.totalLaps = track.getLaps();
			goal.paceScale = 1.0D;
			copy.installRacer(goal);
			copy.fillStamina();
			copy.setPersistenceRequired();
			this.goal = goal;
			this.shadow = copy;
		}

		void clientTick() {
			double now = now();
			readServer();
			while (!toClient.isEmpty() && toClient.peekFirst().due() <= now) toClient.pollFirst().action().run();
			if (shadow != null && bird.racing()) {
				// what the client learns from the synced entity data
				shadow.setRaceHeld(bird.raceHeld());
				shadow.setRaceGhost(bird.raceGhost());
				// the session starts its AI at GO; a rider starts when the grid hold lifts
				if (!released && !bird.raceHeld()) {
					released = true;
					goal.running = true;
				}
				shadow.setOldPosAndRot();
				shadow.tickCount++;
				shadow.tick();
				ServerboundMoveVehiclePacket move = new ServerboundMoveVehiclePacket(shadow);
				up(() -> player.connection.handleMoveVehicle(move));
				clientTicks++;
				sampleField(now);
			}
			while (!toServer.isEmpty() && toServer.peekFirst().due() <= now) toServer.pollFirst().action().run();
		}

		/** Everything the server wrote to this player's connection. */
		void readServer() {
			EmbeddedChannel channel = (EmbeddedChannel) connection.channel();
			for (Object message; (message = channel.readOutbound()) != null; ) {
				if (message instanceof ClientboundKeepAlivePacket keep) {
					long id = keep.getId();
					down(() -> up(() -> player.connection.handleKeepAlive(new ServerboundKeepAlivePacket(id))));
				} else if (message instanceof ClientboundPlayerPositionPacket position) {
					int id = position.getId();
					playerTeleports++;
					down(() -> up(() -> player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id))));
				} else if (message instanceof ClientboundMoveVehiclePacket correction) {
					// vanilla handling (the baseline run): the client snaps its bird and echoes the spot
					corrections++;
					double x = correction.getX(), y = correction.getY(), z = correction.getZ();
					float yRot = correction.getYRot(), xRot = correction.getXRot();
					down(() -> {
						if (shadow != null) {
							shadow.moveTo(x, y, z, yRot, xRot);
							shadow.setDeltaMovement(Vec3.ZERO);
						}
					});
				}
			}
		}

		void applyTeleport(RaceMovePayloads.Teleport t) {
			teleports++;
			if (shadow != null) {
				shadow.moveTo(t.x(), t.y(), t.z(), t.yRot(), t.xRot());
				shadow.setDeltaMovement(Vec3.ZERO);
			}
			up(() -> RiderAuthority.ack(player, t.entityId(), t.id()));
		}

		void receiveFrame(RaceMovePayloads.Frame f) {
			frames++;
			clock.observe(now() / 50.0D, f.tick());
			buffers.computeIfAbsent(f.entityId(), id -> new FrameBuffer()).add(new FrameBuffer.Snap(f.tick(),
					f.x(), f.y(), f.z(), f.yRot(), f.bodyRot(), f.onGround()));
		}

		/** Where this client would show each other bird this tick (the same rows the real client writes). */
		void sampleField(double nowMs) {
			if (buffers.isEmpty()) return;
			double shown = clock.advance(nowMs / 50.0D);
			if (Double.isNaN(shown)) return;
			for (Map.Entry<Integer, FrameBuffer> e : buffers.entrySet()) {
				FrameBuffer.Snap s = e.getValue().sample(shown);
				if (s == null || shadow.distanceToSqr(s.x(), s.y(), s.z()) > 96.0D * 96.0D) continue;
				motion.append(String.format(Locale.ROOT, "%s,%d,%d,%.4f,%.4f,%.4f%n", track.name(), clientTicks, e.getKey(),
						s.x(), s.y(), s.z()));
			}
		}

		/** Where this client and its server bird stand, every second (debugging a simulated rider that does not race). */
		void trace(int tick) {
			if (shadow == null) return;
			append("crowd-debug.csv", String.format(Locale.ROOT, "%s,%d,%.2f,%.2f,%.2f,%.3f,%.2f,%.2f,%.2f,%b,%b,%b,%b,%d,%d%n",
					player.getGameProfile().getName(), tick, shadow.getX(), shadow.getY(), shadow.getZ(),
					shadow.getDeltaMovement().horizontalDistance(), bird.getX(), bird.getY(), bird.getZ(),
					player.getVehicle() == bird, bird.raceHeld(), goal != null && goal.running,
					RiderAuthority.awaiting(player, bird), toServer.size(), toClient.size()));
		}

		void flush() {
			if (motion.isEmpty()) return;
			append("motion-" + player.getGameProfile().getName() + ".csv", motion.toString());
			motion.setLength(0);
		}

		String summary() {
			return String.format(Locale.ROOT, "%s,%.0f,%.0f,%d,%d,%d,%d,%d,%.2f", player.getGameProfile().getName(),
					up.base * 2, up.jitter, clientTicks, teleports, corrections, playerTeleports, frames, clock.delay());
		}
	}
}
