package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;

/**
 * Ranked heats run on a timetable so several players can share one: heats go off
 * on the five-minute mark (game time), one per class. The first rider to sign up
 * with Esther picks the course; later riders of that class join and replace an AI
 * racer each (up to the six-bird field). Esther announces the heat to everyone in
 * Whiskerwind two minutes and one minute out, then counts the last ten seconds.
 * Riders who are not in the saddle in the Square when the heat goes off are dropped.
 */
public final class HeatSchedule {
	/** Ticks between heats (five minutes). */
	public static final int PERIOD = 6000;
	/** A sign-up inside the last ten seconds before a mark takes the next one; otherwise heats are every five minutes, full stop. */
	public static final int MIN_LEAD = 200;
	private static final int NOTICE_2MIN = 2400, NOTICE_1MIN = 1200, NOTICE_10S = 200;

	/** One scheduled heat: its course and who has entered (player -> bird). */
	public static final class Heat {
		final RaceTrack track;
		final long startTick;
		final Map<UUID, UUID> entrants = new LinkedHashMap<>();
		private int lastNotice = -1;   // seconds shown in the countdown

		Heat(RaceTrack track, long startTick) {
			this.track = track;
			this.startTick = startTick;
		}

		public RaceTrack track() {
			return track;
		}

		public long startTick() {
			return startTick;
		}

		public int entrants() {
			return entrants.size();
		}

		public boolean entered(UUID player) {
			return entrants.containsKey(player);
		}
	}

	private static final Map<RaceClass, Heat> HEATS = new EnumMap<>(RaceClass.class);

	private HeatSchedule() {
	}

	/** Server stop: nothing pending for the next server in this JVM. */
	public static void reset() {
		HEATS.clear();
	}

	public static @Nullable Heat pending(RaceClass raceClass) {
		return HEATS.get(raceClass);
	}

	/** Game time of the next mark that leaves at least {@link #MIN_LEAD} ticks to get ready. */
	public static long nextMark(long now) {
		long mark = (now / PERIOD + 1) * PERIOD;
		if (mark - now < MIN_LEAD) {
			mark += PERIOD;
		}
		return mark;
	}

	/** Seconds until the heat, for messages. */
	public static int secondsLeft(Heat heat, long now) {
		return (int) Math.max(0L, (heat.startTick - now + 19) / 20);
	}

	/**
	 * Esther, in the Square, with a rider on a race-ready bird: join the pending
	 * heat of the bird's class, or (no heat yet) open the course picker via the
	 * caller. Returns true when handled here (joined / already in), false when the
	 * caller should open the picker.
	 */
	public static boolean joinOrPick(ServerPlayer player, ChocoboEntity bird) {
		return false;   // the picker always opens: a bird may enter any course of its class or below
	}

	/** The course picker answered for a ranked heat: schedule it (or join the one already pending). */
	public static void schedule(ServerPlayer player, ChocoboEntity bird, RaceTrack track) {
		RaceClass rc = track.getRaceClass();
		if (rc.getId() > bird.raceClass().getId()) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.wrong_class"), true);
			return;
		}
		Heat heat = HEATS.get(rc);
		if (heat == null) {
			long now = player.level().getGameTime();
			heat = new Heat(track, nextMark(now));
			HEATS.put(rc, heat);
			announce(player.serverLevel(), Component.translatable("chocobosreborn.heat.scheduled",
					Component.translatable("chocobosreborn.track." + track.id()), rc.name(),
					clock(secondsLeft(heat, now))));
		}
		enter(player, bird, heat);
	}

	private static void enter(ServerPlayer player, ChocoboEntity bird, Heat heat) {
		long now = player.level().getGameTime();
		for (Heat other : HEATS.values()) {
			if (other != heat && other.entrants.containsKey(player.getUUID())) {
				player.displayClientMessage(Component.translatable("chocobosreborn.heat.entered_already",
						Component.translatable("chocobosreborn.track." + other.track.id()), clock(secondsLeft(other, now))), false);
				return;
			}
		}
		if (heat.entrants.containsKey(player.getUUID())) {
			player.displayClientMessage(Component.translatable("chocobosreborn.heat.entered_already",
					Component.translatable("chocobosreborn.track." + heat.track.id()), clock(secondsLeft(heat, now))), false);
			return;
		}
		if (heat.entrants.size() >= RaceSession.FIELD) {
			player.displayClientMessage(Component.translatable("chocobosreborn.heat.full"), false);
			return;
		}
		heat.entrants.put(player.getUUID(), bird.getUUID());
		player.displayClientMessage(Component.translatable("chocobosreborn.heat.entered",
				Component.translatable("chocobosreborn.track." + heat.track.id()), clock(secondsLeft(heat, now)),
				heat.entrants.size(), RaceSession.FIELD), false);
	}

	/** Server tick in the Square: notices, countdown, and the start. */
	public static void tick(ServerLevel square) {
		if (HEATS.isEmpty()) {
			return;
		}
		long now = square.getGameTime();
		for (RaceClass rc : new ArrayList<>(HEATS.keySet())) {
			Heat heat = HEATS.get(rc);
			long left = heat.startTick - now;
			Component course = Component.translatable("chocobosreborn.track." + heat.track.id());
			if (left == NOTICE_2MIN || left == NOTICE_1MIN) {
				announce(square, Component.translatable("chocobosreborn.heat.notice", course, rc.name(),
						left / 1200, heat.entrants.size(), RaceSession.FIELD));
			} else if (left > NOTICE_10S && left % 20 == 0) {
				// the queue sees its timer ticking on the action bar
				for (UUID id : heat.entrants.keySet()) {
					ServerPlayer p = square.getServer().getPlayerList().getPlayer(id);
					if (p != null && p.level() == square) {   // Esther is only heard in Whiskerwind
						p.displayClientMessage(Component.translatable("chocobosreborn.heat.queue", course,
								clock((int) (left / 20)), heat.entrants.size(), RaceSession.FIELD), true);
					}
				}
			} else if (left <= NOTICE_10S && left > 0 && left % 20 == 0) {
				int s = (int) (left / 20);
				if (s != heat.lastNotice) {
					heat.lastNotice = s;
					for (ServerPlayer p : square.players()) {
						p.displayClientMessage(Component.translatable("chocobosreborn.heat.countdown", course, s), true);
					}
				}
			} else if (left <= 0) {
				HEATS.remove(rc);
				start(square, rc, heat);
			}
		}
	}

	private static void start(ServerLevel square, RaceClass rc, Heat heat) {
		List<ServerPlayer> players = new ArrayList<>();
		List<ChocoboEntity> birds = new ArrayList<>();
		for (Map.Entry<UUID, UUID> e : heat.entrants.entrySet()) {
			ServerPlayer p = square.getServer().getPlayerList().getPlayer(e.getKey());
			if (p == null || p.level() != square || RaceManager.sessionOf(p.getUUID()) != null) {
				continue;
			}
			Entity v = p.getVehicle();
			if (!(v instanceof ChocoboEntity bird) || !bird.getUUID().equals(e.getValue()) || !bird.saddled()
					|| bird.isBaby() || bird.armor() != null || bird.raceClass().getId() < rc.getId()) {
				p.displayClientMessage(Component.translatable("chocobosreborn.heat.missed"), false);
				continue;
			}
			players.add(p);
			birds.add(bird);
		}
		Component course = Component.translatable("chocobosreborn.track." + heat.track.id());
		if (players.isEmpty()) {
			announce(square, Component.translatable("chocobosreborn.heat.scratched", course));
			return;
		}
		if (RaceManager.trackBusy(heat.track)) {
			for (ServerPlayer p : players) {
				p.displayClientMessage(Component.translatable("chocobosreborn.race.occupied"), true);
			}
			return;
		}
		announce(square, Component.translatable("chocobosreborn.heat.off", course, players.size()));
		for (ServerPlayer p : players) {
			Titles.show(p, Component.translatable("chocobosreborn.heat.transport"), course, 5, 40, 10);
		}
		RaceManager.addSession(new RaceSession(square, heat.track, true, players, birds, 0));
	}

	/** Esther's voice to everyone in Whiskerwind (ServerLevel.players() is only that dimension's players). */
	private static void announce(ServerLevel square, Component msg) {
		Component line = Component.translatable("chocobosreborn.heat.esther", msg);
		for (ServerPlayer p : square.players()) {
			p.displayClientMessage(line, false);
		}
	}

	/** m:ss for a number of seconds. */
	public static String clock(int seconds) {
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
