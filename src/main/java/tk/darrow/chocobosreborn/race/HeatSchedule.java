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
		private int lastCall;          // 2 = two-minute call, 1 = one-minute call

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
	private static boolean loaded;

	private HeatSchedule() {
	}

	/** Server stop: drop the in-memory book. Pass the Square to forget the saved timetable too. */
	public static void reset() {
		reset(null);
	}

	public static void reset(@Nullable ServerLevel square) {
		HEATS.clear();
		loaded = false;
		if (square != null) {
			SquareData.get(square).setHeats(new net.minecraft.nbt.CompoundTag());
		}
	}

	public static @Nullable Heat pending(RaceClass raceClass) {
		return HEATS.get(raceClass);
	}

	public static boolean entered(UUID player) {
		for (Heat heat : HEATS.values()) {
			if (heat.entered(player)) {
				return true;
			}
		}
		return false;
	}

	/** Scratch this rider off the timetable (pocketwatch home, or sneak-click Esther). */
	public static boolean drop(ServerPlayer player) {
		ServerLevel square = Square.level(player.server);
		if (square != null) {
			ensureLoaded(square);
		}
		for (RaceClass rc : new ArrayList<>(HEATS.keySet())) {
			Heat heat = HEATS.get(rc);
			if (heat.entrants.remove(player.getUUID()) == null) {
				continue;
			}
			player.displayClientMessage(Component.translatable("chocobosreborn.heat.left",
					Component.translatable("chocobosreborn.track." + heat.track.id())), false);
			if (heat.entrants.isEmpty()) {
				HEATS.remove(rc);
				if (square != null) {
					announce(square, Component.translatable("chocobosreborn.heat.scratched",
							Component.translatable("chocobosreborn.track." + heat.track.id())));
				}
			}
			if (square != null) {
				persist(square);
			}
			return true;
		}
		return false;
	}

	/** True when this bird is already on a queued heat (almanac must not release it). */
	public static boolean hasBird(UUID bird) {
		for (Heat heat : HEATS.values()) {
			if (heat.entrants.containsValue(bird)) {
				return true;
			}
		}
		return false;
	}

	/** Game time of the next mark that leaves at least {@link #MIN_LEAD} ticks to get ready. */
	public static long nextMark(long now) {
		return RaceScoring.nextHeatMark(now, PERIOD, MIN_LEAD);
	}

	/** A saved mark that is already due must not fire on the first tick after reload. */
	public static long persistStart(long saved, long now) {
		return RaceScoring.persistHeatStart(saved, now, PERIOD, MIN_LEAD);
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
		if (!(player.level() instanceof ServerLevel sl)) {
			return false;
		}
		ensureLoaded(sl);
		boolean sawHeat = false;
		for (int id = bird.raceClass().getId(); id >= 0; id--) {
			Heat h = HEATS.get(RaceClass.byId(id));
			if (h == null) {
				continue;
			}
			sawHeat = true;
			if (!RaceScoring.joinSkipsFullHeat(h.entrants.size(), RaceSession.FIELD, h.entered(player.getUUID()))) {
				continue;
			}
			if (enter(player, bird, h)) {
				persist(sl);
			}
			return true;
		}
		if (sawHeat) {
			player.displayClientMessage(Component.translatable("chocobosreborn.heat.full"), false);
		}
		return false;   // no joinable heat: the caller opens the course picker
	}

	/** The course picker answered for a ranked heat: schedule it (or join the one already pending). */
	public static void schedule(ServerPlayer player, ChocoboEntity bird, RaceTrack track) {
		RaceClass rc = track.getRaceClass();
		if (!RaceScoring.mayEnterCourse(bird.raceClass().getId(), rc.getId())) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.wrong_class"), true);
			return;
		}
		if (player.level() instanceof ServerLevel sl) {
			ensureLoaded(sl);
		}
		Heat heat = HEATS.get(rc);
		boolean created = false;
		if (heat == null) {
			long now = player.level().getGameTime();
			heat = new Heat(track, nextMark(now));
			created = true;
		} else if (heat.track != track) {
			player.displayClientMessage(Component.translatable("chocobosreborn.heat.join_existing",
					Component.translatable("chocobosreborn.track." + heat.track.id())), false);
		}
		if (!enter(player, bird, heat)) {
			return;
		}
		if (created) {
			HEATS.put(rc, heat);
			long now = player.level().getGameTime();
			announce(player.serverLevel(), Component.translatable("chocobosreborn.heat.scheduled",
					Component.translatable("chocobosreborn.track." + track.id()), rc.name(),
					clock(secondsLeft(heat, now))));
			if (player.level() instanceof ServerLevel square && Square.isSquare(square)) {
				SquareBuilder.buildTrack(square, track);
			}
		}
		if (player.level() instanceof ServerLevel sl) {
			persist(sl);
		}
	}

	private static boolean enter(ServerPlayer player, ChocoboEntity bird, Heat heat) {
		if (!bird.isOwnedBy(player) && !player.getAbilities().instabuild) {
			player.displayClientMessage(Component.translatable("chocobosreborn.square.need_bird"), true);
			return false;
		}
		if (RaceManager.sessionOf(player.getUUID()) != null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.already"), true);
			return false;
		}
		long now = player.level().getGameTime();
		for (Heat other : HEATS.values()) {
			if (other != heat && other.entrants.containsKey(player.getUUID())) {
				player.displayClientMessage(Component.translatable("chocobosreborn.heat.entered_already",
						Component.translatable("chocobosreborn.track." + other.track.id()), clock(secondsLeft(other, now))), false);
				return false;
			}
		}
		if (heat.entrants.containsKey(player.getUUID())) {
			player.displayClientMessage(Component.translatable("chocobosreborn.heat.entered_already",
					Component.translatable("chocobosreborn.track." + heat.track.id()), clock(secondsLeft(heat, now))), false);
			return false;
		}
		if (heat.entrants.size() >= RaceSession.FIELD) {
			player.displayClientMessage(Component.translatable("chocobosreborn.heat.full"), false);
			return false;
		}
		heat.entrants.put(player.getUUID(), bird.getUUID());
		TradeDesk.withdraw(player);
		DuelDesk.withdraw(player);
		player.displayClientMessage(Component.translatable("chocobosreborn.heat.entered",
				Component.translatable("chocobosreborn.track." + heat.track.id()), clock(secondsLeft(heat, now)),
				heat.entrants.size(), RaceSession.FIELD), false);
		return true;
	}

	/** Server tick in the Square: notices, countdown, and the start. */
	public static void tick(ServerLevel square) {
		ensureLoaded(square);
		if (HEATS.isEmpty()) {
			return;
		}
		long now = square.getGameTime();
		boolean dirty = false;
		for (RaceClass rc : new ArrayList<>(HEATS.keySet())) {
			Heat heat = HEATS.get(rc);
			long left = heat.startTick - now;
			Component course = Component.translatable("chocobosreborn.track." + heat.track.id());
			if (left <= NOTICE_2MIN && left > NOTICE_1MIN && heat.lastCall < 2) {
				heat.lastCall = 2;
				announce(square, Component.translatable("chocobosreborn.heat.notice", course, rc.name(),
						2, heat.entrants.size(), RaceSession.FIELD));
			} else if (left <= NOTICE_1MIN && left > NOTICE_10S && heat.lastCall < 1) {
				heat.lastCall = 1;
				announce(square, Component.translatable("chocobosreborn.heat.notice", course, rc.name(),
						1, heat.entrants.size(), RaceSession.FIELD));
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
				if (RaceManager.trackBusy(heat.track)) {
					Heat next = new Heat(heat.track, nextMark(now));
					next.entrants.putAll(heat.entrants);
					HEATS.put(rc, next);
					announce(square, Component.translatable("chocobosreborn.race.occupied"));
					dirty = true;
					continue;
				}
				HEATS.remove(rc);
				dirty = true;
				start(square, rc, heat);
			}
		}
		if (dirty) {
			persist(square);
		}
	}

	private static void ensureLoaded(ServerLevel square) {
		if (loaded) {
			return;
		}
		loaded = true;
		if (read(SquareData.get(square).heats(), square.getGameTime())) {
			persist(square);
		}
	}

	private static void persist(ServerLevel square) {
		SquareData.get(square).setHeats(write());
	}

	private static net.minecraft.nbt.CompoundTag write() {
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
		for (Heat heat : HEATS.values()) {
			net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
			t.putInt("Track", heat.track.ordinal());
			t.putLong("Start", heat.startTick);
			net.minecraft.nbt.ListTag people = new net.minecraft.nbt.ListTag();
			for (Map.Entry<UUID, UUID> e : heat.entrants.entrySet()) {
				net.minecraft.nbt.CompoundTag row = new net.minecraft.nbt.CompoundTag();
				row.putUUID("P", e.getKey());
				row.putUUID("B", e.getValue());
				people.add(row);
			}
			t.put("Entrants", people);
			list.add(t);
		}
		tag.put("Entries", list);
		return tag;
	}

	/** @return true when a saved mark had already passed and was bumped */
	private static boolean read(net.minecraft.nbt.CompoundTag tag, long now) {
		HEATS.clear();
		boolean dirty = false;
		for (net.minecraft.nbt.Tag raw : tag.getList("Entries", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
			net.minecraft.nbt.CompoundTag t = (net.minecraft.nbt.CompoundTag) raw;
			RaceTrack track = RaceTrack.byId(t.getInt("Track"));
			long start = persistStart(t.getLong("Start"), now);
			if (start != t.getLong("Start")) {
				dirty = true;
			}
			Heat heat = new Heat(track, start);
			for (net.minecraft.nbt.Tag rowRaw : t.getList("Entrants", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
				net.minecraft.nbt.CompoundTag row = (net.minecraft.nbt.CompoundTag) rowRaw;
				if (row.hasUUID("P") && row.hasUUID("B")) {
					heat.entrants.put(row.getUUID("P"), row.getUUID("B"));
				}
			}
			if (!heat.entrants.isEmpty()) {
				HEATS.put(track.getRaceClass(), heat);
			}
		}
		return dirty;
	}

	private static void start(ServerLevel square, RaceClass rc, Heat heat) {
		List<ServerPlayer> players = new ArrayList<>();
		List<ChocoboEntity> birds = new ArrayList<>();
		for (Map.Entry<UUID, UUID> e : heat.entrants.entrySet()) {
			ServerPlayer p = square.getServer().getPlayerList().getPlayer(e.getKey());
			if (p == null || p.level() != square || RaceManager.sessionOf(p.getUUID()) != null) {
				if (p != null) {
					p.displayClientMessage(Component.translatable("chocobosreborn.heat.missed"), false);
					RaceManager.refundPendingBet(p);
				}
				continue;
			}
			Entity v = p.getVehicle();
			if (!(v instanceof ChocoboEntity bird) || !bird.getUUID().equals(e.getValue()) || !bird.saddled()
					|| bird.isBaby() || bird.armor() != null || bird.raceClass().getId() < rc.getId()
					|| (!bird.isOwnedBy(p) && !p.getAbilities().instabuild)) {
				p.displayClientMessage(Component.translatable("chocobosreborn.heat.missed"), false);
				RaceManager.refundPendingBet(p);
				continue;
			}
			players.add(p);
			birds.add(bird);
		}
		Component course = Component.translatable("chocobosreborn.track." + heat.track.id());
		if (players.isEmpty()) {
			for (UUID id : heat.entrants.keySet()) {
				ServerPlayer p = square.getServer().getPlayerList().getPlayer(id);
				if (p != null) {
					RaceManager.refundPendingBet(p);
				}
			}
			announce(square, Component.translatable("chocobosreborn.heat.scratched", course));
			return;
		}
		if (RaceManager.trackBusy(heat.track)) {
			Heat next = new Heat(heat.track, nextMark(square.getGameTime()));
			next.entrants.putAll(heat.entrants);
			HEATS.put(rc, next);
			announce(square, Component.translatable("chocobosreborn.race.occupied"));
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
