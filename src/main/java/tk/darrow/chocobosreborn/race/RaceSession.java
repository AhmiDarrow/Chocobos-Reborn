package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import tk.darrow.chocobosreborn.breed.ChocoboColor;
import tk.darrow.chocobosreborn.breed.ChocoboGrade;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.entity.KinStewardEntity;
import tk.darrow.chocobosreborn.entity.ModEntities;
import tk.darrow.chocobosreborn.item.ModItems;
import tk.darrow.chocobosreborn.sound.ModSounds;

/**
 * One heat at Whiskerwind: countdown at the stalls, laps, finish order,
 * awards (class wins, GP prizes, bets, duel stakes) and the walk back to the
 * paddock. A ranked heat has one human and five named AI regulars (Jolo and
 * Teiyo from Class B); a duel is the two riders alone, no AI field. The
 * course's chunks are force-loaded for the duration so the field keeps running
 * out of the riders' sight.
 */
public class RaceSession {
	public enum State { HOLD, RUNNING, DONE }

	/** Settle (chunks stream in for the transported riders) then a five-second visual countdown. */
	private static final int HOLD_TICKS = 260;
	private static final int COUNTDOWN_TICKS = 100;
	private static final int FINISH_GRACE_TICKS = 1200;
	/** Hard cap from GO so an AFK human cannot occupy a course forever. */
	private static final int RUN_CAP_TICKS = 12000;
	static final String NAME_TEIYO = "Teiyo";
	static final String NAME_JOLO = "Jolo";
	static final String NAME_AHMI = "Ahmi";
	static final String NAME_RISIKA = "Risika";
	public static final int FIELD = 6;

	private final class Racer {
		final UUID bird;
		@Nullable final UUID player;
		final String name;
		final double lane;
		final RaceLapProgress progress;
		/** The bird itself: the level lookup goes blind while a far course island's chunks are still loading. */
		final ChocoboEntity ref;
		int laps;
		int finishIndex = -1;
		/**
		 * Crossed the line at this tick (fractional, less the rider's ping credit) but not
		 * placed yet: {@link #settleFinishes} places finishers in crossing order.
		 */
		double finishTime = Double.NaN;
		double observedFinishTime = Double.NaN;
		int finishLatencyMs;
		/** Fine lap progress at the end of the last tick, for timing the crossing inside a tick. */
		double lastFine = Double.NaN;
		int startLatencyMs;
		boolean forfeited;
		boolean settled;
		@Nullable RacerGoal goal;
		/** Last position on the road: a bird off it this far from here is put back. */
		double roadX = Double.NaN, roadZ = Double.NaN;
		/** Tick a rescue hold ends (0 = none). */
		int heldUntil;
		/** Tick the ghosting after a set-back may end (0 = none): it lasts until the bird is clear of everyone. */
		int ghostUntil;
		/** The terrain feature this rider was last told about (lap * 100 + index), so each approach warns once. */
		int warnedFeature = -1;

		Racer(ChocoboEntity ref, @Nullable UUID player, String name, double lane, double startProgress) {
			this.ref = ref;
			this.bird = ref.getUUID();
			this.player = player;
			this.name = name;
			this.lane = lane;
			this.progress = new RaceLapProgress(startProgress);
		}

		boolean human() {
			return player != null;
		}

		/** Over the line, waiting to be placed. */
		boolean pending() {
			return finishIndex < 0 && !Double.isNaN(finishTime);
		}

		/** Still racing: not forfeited, not over the line (placed or pending). */
		boolean running() {
			return RaceScoring.stillOnCourse(forfeited, finishIndex) && !pending();
		}

		@Nullable ChocoboEntity entity() {
			if (ref.isAlive() && !ref.isRemoved()) {
				return ref;
			}
			Entity e = level.getEntity(bird);
			return e instanceof ChocoboEntity c && c.isAlive() ? c : null;
		}

		/** The bird even if it is dead: teardown must clear racing on a corpse or its rider is stuck on it. */
		@Nullable ChocoboEntity anyEntity() {
			if (!ref.isRemoved()) {
				return ref;
			}
			Entity e = level.getEntity(bird);
			return e instanceof ChocoboEntity c ? c : null;
		}

		@Nullable ServerPlayer serverPlayer() {
			return player == null ? null : level.getServer().getPlayerList().getPlayer(player);
		}
	}

	private final ServerLevel level;
	private final RaceTrack track;
	private final boolean ranked;
	private final List<Racer> racers = new ArrayList<>();
	/** Kin jockeys riding the AI racers. */
	private final List<KinStewardEntity> jockeys = new ArrayList<>();
	private final RaceCourseLayout layout;
	private final Set<Long> forced = new HashSet<>();
	private State state = State.HOLD;
	private int tick;
	private int firstFinishTick = -1;
	private int finishCount;
	/** Bet placed at the bookie before the start (ranked, first human). */
	@Nullable RaceScoring.BetPick bet;
	int stake;
	/** Riders only, no AI field (Sable's duels). */
	private final boolean duel;
	/** Stalls on the grid: the full field, or just the duellists (centred on the road). */
	private final int grid;
	/** Duel: GP each side put up; the winner takes both. */
	private int duelStake;
	private boolean duelPaid;
	private boolean aborting;
	/** The first human to drop out on their own (not a server-stop abort): a duel's pot goes to the other. */
	@Nullable private Racer firstQuit;
	/** Ranked bookie bets (one per human in a shared heat). */
	private final List<BookieBet> bets = new ArrayList<>();
	@Nullable private UUID bettor;

	private static final class BookieBet {
		final UUID player;
		final RaceScoring.BetPick pick;
		int stake;

		BookieBet(UUID player, RaceScoring.BetPick pick, int stake) {
			this.player = player;
			this.pick = pick;
			this.stake = stake;
		}
	}

	@Nullable
	private BookieBet betOf(UUID player) {
		for (BookieBet b : bets) {
			if (b.player.equals(player) && b.stake > 0) {
				return b;
			}
		}
		return null;
	}

	boolean hasBookieBet(UUID player) {
		return betOf(player) != null;
	}

	public RaceScoring.BetPick legalPick(RaceScoring.BetPick pick, UUID bettor) {
		return legalLivePick(pick, bettor);
	}

	public RaceScoring.BetPick nextPick(RaceScoring.BetPick pick, UUID bettor) {
		return RaceScoring.nextLivePick(pick, ranked, hasPlayer(bettor), hasJolo(), hasTeiyo(),
				humans().size() > 1);
	}

	/** The most Rook takes on {@code pick} in this heat: a payout never beats the purse. */
	public int maxStake(RaceScoring.BetPick pick) {
		return RaceScoring.maxStake(RaceScoring.purse(track), odds(pick));
	}

	/** A waiting stake placed before the heat was known: anything over this heat's cap goes back. */
	private int capStake(ServerPlayer p, RaceScoring.BetPick pick, int n) {
		int cap = maxStake(pick);
		if (n > cap) {
			DuelDesk.giveGp(p, n - cap);
			p.displayClientMessage(Component.translatable("chocobosreborn.bet.capped", n - cap, cap), false);
			return cap;
		}
		return n;
	}

	void takeBookieBet(UUID player, RaceScoring.BetPick pick, int amount) {
		RaceScoring.BetPick legal = legalLivePick(pick, player);
		bets.add(new BookieBet(player, legal, amount));
		hold(player, amount);
		if (this.bet == null) {
			this.bet = legal;
			this.stake = amount;
			this.bettor = player;
		}
	}

	private boolean hasJolo() {
		// Class C: Ahmi takes the Jolo (slightly slower) slot; Risika takes Teiyo's.
		return racers.stream().anyMatch(r -> NAME_JOLO.equals(r.name) || NAME_AHMI.equals(r.name));
	}

	private boolean hasTeiyo() {
		return racers.stream().anyMatch(r -> NAME_TEIYO.equals(r.name) || NAME_RISIKA.equals(r.name));
	}

	private RaceScoring.BetPick legalLivePick(RaceScoring.BetPick pick, UUID bettor) {
		return RaceScoring.legalizeBettor(pick, ranked, hasPlayer(bettor), hasJolo(), hasTeiyo(),
				humans().size() > 1);
	}

	private void clearFirstSlotIf(UUID player) {
		if (player.equals(bettor)) {
			bet = null;
			stake = 0;
			bettor = null;
		}
	}

	/** Ranked heat, or a friendly against the field: one human. */
	public RaceSession(ServerLevel level, RaceTrack track, boolean ranked, ServerPlayer player, ChocoboEntity bird) {
		this(level, track, ranked, List.of(player), List.of(bird), false, 0);
	}

	/**
	 * A heat with one or more humans. A {@code duel} is those riders alone on the two
	 * centre stalls, no AI field, and pays only the pot; {@code duelStake} > 0 means both
	 * put GP up (already taken).
	 */
	public RaceSession(ServerLevel level, RaceTrack track, boolean ranked, List<ServerPlayer> players,
	                   List<ChocoboEntity> birds, boolean duel, int duelStake) {
		this.level = level;
		this.track = track;
		this.ranked = ranked;
		this.duel = duel;
		this.grid = duel ? players.size() : FIELD;
		this.duelStake = duelStake;
		if (duelStake > 0) {
			for (ServerPlayer p : players) {
				hold(p.getUUID(), duelStake);
			}
		}
		this.layout = RaceCourseLayout.of(track);
		forceChunks(true);
		SquareBuilder.buildTrack(level, track);
		SquareBuilder.scrubCourse(level, track);
		for (int i = 0; i < players.size(); i++) {
			RacePoint stall = track.stallPos(i, grid);
			racers.add(new Racer(birds.get(i), players.get(i).getUUID(), players.get(i).getName().getString(),
					RaceTrack.stallOffset(i, grid), track.progressAt(stall.x(), stall.z())));
			birds.get(i).setRacing(true);
			birds.get(i).setRaceHeld(true);
			birds.get(i).setRaceTrack(track.ordinal());
			birds.get(i).setOrderedToSit(false);
		}
		if (!duel) {
			spawnField(players.size(), track.getRaceClass());
		}
		for (Racer r : racers) {
			ChocoboEntity e = r.entity();
			if (e == null) {
				continue;
			}
			RacePoint stall = track.stallPos(racers.indexOf(r), grid);
			moveRidden(e, stall.x(), stall.y(), stall.z(), false);
			e.setYRot(track.facingYaw());
			e.setYBodyRot(track.facingYaw());
		}
		if (ranked) {
			for (ServerPlayer p : players) {
				RaceScoring.BetPick pending = RaceManager.takePendingBet(p);
				if (pending == null) {
					continue;
				}
				int n = RaceManager.takePendingStake(p);
				if (pending != RaceScoring.BetPick.SELF) {
					// a racer may only back themselves: a pick that pays when they lose goes back
					if (n > 0) {
						DuelDesk.giveGp(p, n);
						p.displayClientMessage(Component.translatable("chocobosreborn.bet.refunded", n), false);
					}
					continue;
				}
				RaceScoring.BetPick legal = legalLivePick(pending, p.getUUID());
				n = capStake(p, legal, n);
				if (n > 0) {
					bets.add(new BookieBet(p.getUUID(), legal, n));
					hold(p.getUUID(), n);
				}
				if (this.bet == null) {
					this.bet = legal;
					this.stake = n;
					this.bettor = p.getUUID();
				}
			}
		}
		for (ServerPlayer p : players) {
			p.displayClientMessage(Component.translatable("chocobosreborn.race.enter",
					Component.translatable("chocobosreborn.track." + track.id())), false);
		}
	}

	private void forceChunks(boolean on) {
		// forced chunks are saved with the world: note them so a crash does not leave the course loaded forever
		SquareData d = Square.isSquare(level) ? SquareData.get(level) : null;
		if (on) {
			for (long key : layout.chunks()) {
				int cx = (int) (key >> 32), cz = (int) key;
				level.setChunkForced(cx, cz, true);
				forced.add(key);
			}
			if (d != null) {
				d.addForcedChunks(forced);
			}
		} else {
			for (long key : forced) {
				level.setChunkForced((int) (key >> 32), (int) key, false);
			}
			if (d != null) {
				d.removeForcedChunks(forced);
			}
			forced.clear();
		}
	}

	private void spawnField(int humans, RaceClass raceClass) {
		boolean rivals = ranked && raceClass.includesTeioh();
		boolean cRivals = rivals && raceClass.cClassRivals();
		int namedSlots = FIELD - humans - (rivals ? 2 : 0);
		List<FieldRoster.Entry> card = FieldRoster.draw(raceClass, Math.max(0, namedSlots),
				new java.util.Random(level.random.nextLong()));
		int cardIdx = 0;
		List<String> announced = new ArrayList<>();
		// the rivals key off the best rider's own bird, as FF7's Teioh does: its land
		// speed x grade x speed training, the pace the rider actually cruises at
		double riderPace = 0.0D;
		for (Racer h : racers) {
			ChocoboEntity b = h.entity();
			if (h.human() && b != null) {
				riderPace = Math.max(riderPace, b.color().landSpeed() * b.speedMul());
			}
		}
		if (riderPace <= 0.0D) {
			riderPace = RaceScoring.fieldPaceAbs(raceClass);
		}
		for (int i = humans; i < FIELD; i++) {
			ChocoboEntity npc = ModEntities.CHOCOBO.get().create(level);
			if (npc == null) {
				continue;
			}
			boolean isTeioh = rivals && i == FIELD - 2 && i >= humans;
			boolean isJolo = rivals && i == FIELD - 1 && i >= humans;
			FieldRoster.Entry entry = null;
			if (!isTeioh && !isJolo && cardIdx < card.size()) {
				entry = card.get(cardIdx++);
			}
			// Class C: Risika is the Teiyo-pace rival (slightly ahead of Ahmi); Ahmi is Jolo-pace.
			String name = isTeioh ? (cRivals ? NAME_RISIKA : NAME_TEIYO)
					: isJolo ? (cRivals ? NAME_AHMI : NAME_JOLO)
					: entry != null ? entry.name() : "Racer";
			announced.add(name);
			RacePoint stall = track.stallPos(i, FIELD);
			npc.moveTo(stall.x(), stall.y(), stall.z(), track.facingYaw(), 0.0F);
			face(npc, track.facingYaw());
			npc.finalizeSpawn(level, level.getCurrentDifficultyAt(npc.blockPosition()), MobSpawnType.EVENT,
					new net.minecraft.world.entity.AgeableMob.AgeableMobGroupData(false));
			npc.setRaceNpc(true);
			npc.setPersistenceRequired();
			npc.setRacing(true);
			// C rivals: Yellow race stats with Nether/End looks; B+ Teiyo Black, Jolo by class
			if (isTeioh || isJolo) {
				if (cRivals) {
					npc.setColor(ChocoboColor.YELLOW);
					npc.setLookColor(RaceScoring.cRivalLook(isTeioh));
				} else {
					npc.setColor(isTeioh ? ChocoboColor.BLACK : RaceScoring.joloColor(raceClass));
				}
			} else {
				npc.setColor(entry != null ? entry.color() : npcColor(raceClass, i));
			}
			npc.setGrade(ChocoboGrade.byRank(Math.min(4, raceClass.getId() + 1)));
			npc.setRaceClass(raceClass);
			int train = RaceScoring.fieldTraining(raceClass.getId(), isTeioh || isJolo);
			if (!isTeioh && !isJolo) {
				train = net.minecraft.util.Mth.clamp(train + level.random.nextInt(9) - 4, 0, 100);
			}
			npc.addTraining(train, train, train, train);
			npc.fillStamina();
			npc.setCustomName(Component.literal(name));
			npc.setCustomNameVisible(false);   // the jockey carries the name
			npc.inventory().setItem(tk.darrow.chocobosreborn.menu.ChocoboInventoryMenu.SADDLE, new ItemStack(ModItems.SADDLE.get()));
			level.addFreshEntity(npc);
			TownRole jockey = isTeioh
					? (cRivals ? TownRole.JOCKEY_RISIKA : TownRole.JOCKEY_TEIYO)
					: isJolo ? (cRivals ? TownRole.JOCKEY_AHMI : TownRole.JOCKEY_JOLO) : jockeyLook(i);
			mountJockey(npc, jockey, stall, name);
			Racer r = new Racer(npc, null, name, RaceTrack.stallOffset(i, FIELD), track.progressAt(stall.x(), stall.z()));
			RacerProfile.Role role = isTeioh ? RacerProfile.Role.TEIYO : isJolo ? RacerProfile.Role.JOLO : RacerProfile.Role.FIELD;
			r.goal = new RacerGoal(npc, track, r.lane, RacerProfile.of(raceClass, role));
			// every racer, rivals included, rolls its own form for this heat: +-5% (Ahmi: each race feels different)
			r.goal.speed = 1.0D + RacerProfile.VARIANCE * (2.0D * level.random.nextDouble() - 1.0D);
			r.goal.totalLaps = track.getLaps();
			if (isTeioh || isJolo) {
				double own = npc.color().landSpeed() * r.goal.profile.cruise() * npc.speedMul();
				r.goal.paceScale = RaceScoring.rivalPaceAbs(raceClass, isJolo, riderPace) / Math.max(0.01D, own);
			} else {
				// a field bird runs its class's land speed, keeping a quarter of its colour's edge
				r.goal.paceScale = RaceScoring.fieldLandSpeed(npc.color(), raceClass) / Math.max(0.05D, npc.color().landSpeed());
			}
			npc.installRacer(r.goal);
			racers.add(r);
		}
		if (!announced.isEmpty()) {
			String cardLine = String.join(", ", announced);
			for (Racer h : humans()) {
				ServerPlayer p = h.serverPlayer();
				if (p != null) {
					p.displayClientMessage(Component.translatable("chocobosreborn.race.card", cardLine), false);
				}
			}
		}
	}

	private static TownRole jockeyLook(int i) {
		TownRole[] looks = {TownRole.JOCKEY_SWARM, TownRole.JOCKEY_CLOCK, TownRole.JOCKEY_SPINDLE, TownRole.JOCKEY_SOIL, TownRole.JOCKEY_CLAW};
		return looks[Math.floorMod(i, looks.length)];
	}

	/** A Tribal Power kin in the saddle of an AI racer (visual only: the bird drives itself). */
	private void mountJockey(ChocoboEntity npc, TownRole look, RacePoint stall, String nametag) {
		KinStewardEntity kin = ModEntities.KIN_STEWARD.get().create(level);
		if (kin == null) {
			return;
		}
		kin.moveTo(stall.x(), stall.y() + 1.0D, stall.z(), track.facingYaw(), 0.0F);
		kin.setRole(look);
		kin.setCustomName(Component.literal(nametag));
		kin.setCustomNameVisible(true);
		kin.setPersistenceRequired();
		kin.installJockey();
		level.addFreshEntity(kin);
		jockeys.add(kin);
	}

	/** Mount after the client has the bird; same-tick startRiding logs "passengers for unknown entity". */
	private void seatJockeys() {
		int npc = 0;
		for (Racer r : racers) {
			if (r.human()) {
				continue;
			}
			if (npc >= jockeys.size()) {
				break;
			}
			KinStewardEntity kin = jockeys.get(npc++);
			ChocoboEntity bird = r.entity();
			if (bird != null && kin.getVehicle() == null && !kin.isRemoved()) {
				kin.startRiding(bird, true);
			}
		}
	}

	/** Point a bird (and whoever sits on it) up the road: body, head and rotation history together, or the body drifts back. */
	private static void face(ChocoboEntity bird, float yaw) {
		bird.setYRot(yaw);
		bird.yRotO = yaw;
		bird.setYBodyRot(yaw);
		bird.setYHeadRot(yaw);
		for (Entity p : bird.getPassengers()) {
			p.setYRot(yaw);
			p.yRotO = yaw;
			if (p instanceof net.minecraft.world.entity.LivingEntity le) {
				le.setYBodyRot(yaw);
				le.setYHeadRot(yaw);
			}
		}
	}

	private static ChocoboColor npcColor(RaceClass rc, int i) {
		return switch (rc) {
			case C -> ChocoboColor.YELLOW;
			case B -> i % 2 == 0 ? ChocoboColor.GREEN : ChocoboColor.BLUE;
			case A -> i % 2 == 0 ? ChocoboColor.WHITE : ChocoboColor.BLACK;
			case S -> i % 3 == 0 ? ChocoboColor.BLUE : (i % 3 == 1 ? ChocoboColor.BLACK : ChocoboColor.WHITE);
		};
	}

	public State state() {
		return state;
	}

	public boolean running() {
		return state == State.RUNNING;
	}

	public boolean live() {
		return state != State.DONE;
	}

	/** The first human (ranked bets, single-player callers). */
	public UUID player() {
		return racers.get(0).player == null ? new UUID(0L, 0L) : racers.get(0).player;
	}

	public boolean hasPlayer(UUID id) {
		return racers.stream().anyMatch(r -> id.equals(r.player) && !r.forfeited);
	}

	/** Still this heat's rider, including after a DNF, until teardown. */
	public boolean belongsTo(UUID id) {
		return racers.stream().anyMatch(r -> id.equals(r.player));
	}

	public boolean ranked() {
		return ranked;
	}

	public RaceTrack track() {
		return track;
	}

	/** Birds on the grid, riders and AI alike (tests). */
	public int fieldSize() {
		return racers.size();
	}

	/** AI racers on the grid (tests: a duel has none). */
	public long aiRacers() {
		return racers.stream().filter(r -> !r.human()).count() + jockeys.size();
	}

	/** Racers that have crossed the finish (tests, HUD). */
	public int finished() {
		return finishCount;
	}

	/** Racers with at least {@code laps} laps done (a finisher has them all), for diagnostics and tests. */
	public int lapsDone(int laps) {
		int n = 0;
		for (Racer r : racers) {
			if (r.laps >= laps || r.finishIndex >= 0) {
				n++;
			}
		}
		return n;
	}

	/** Laps and lap progress of every racer, for diagnostics. */
	public String progressReport() {
		StringBuilder sb = new StringBuilder();
		for (Racer r : racers) {
			ChocoboEntity e = r.entity();
			sb.append(r.name).append('=').append(r.laps).append('+')
					.append(e == null ? "?" : String.format("%.2f", track.progressAt(e.getX(), e.getZ())))
					.append(r.finishIndex >= 0 ? "F" : r.forfeited ? "X" : "").append(' ');
		}
		return sb.toString();
	}

	/**
	 * Where every AI racer is, for the race harness's field log: name, laps, lap progress,
	 * position, lane, on-ground and wall-contact flags, colour and recovery mode.
	 */
	public List<java.util.Map<String, Object>> aiReport() {
		List<java.util.Map<String, Object>> out = new ArrayList<>();
		for (Racer r : racers) {
			if (r.human()) {
				continue;
			}
			ChocoboEntity e = r.entity();
			java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
			m.put("name", r.name);
			m.put("laps", r.laps);
			m.put("finished", r.finishIndex >= 0);
			if (e != null) {
				double p = track.progressAt(e.getX(), e.getZ(), r.progress.lastProgress());
				m.put("progress", Math.round(p * 1.0E4D) / 1.0E4D);
				m.put("x", Math.round(e.getX() * 100.0D) / 100.0D);
				m.put("y", Math.round(e.getY() * 100.0D) / 100.0D);
				m.put("z", Math.round(e.getZ() * 100.0D) / 100.0D);
				m.put("lane", Math.round(track.laneAt(p, e.getX(), e.getZ()) * 100.0D) / 100.0D);
				m.put("onGround", e.onGround());
				m.put("horizontalCollision", e.horizontalCollision);
				m.put("colour", e.color().name());
				m.put("held", e.raceHeld());
				m.put("mode", r.goal == null ? "" : r.goal.recoveryMode().name());
			}
			out.add(m);
		}
		return out;
	}

	/** Read-only diagnostics, retaining timing after teardown for race QA. */
	public record Timing(int laps, int place, boolean forfeited, double observedTick, double creditedTick,
	                     int startLatencyMs, int finishLatencyMs) {}

	public @Nullable Timing timing(UUID player) {
		for (Racer r : racers) {
			if (player.equals(r.player)) {
				return new Timing(r.laps, r.finishIndex < 0 ? 0 : r.finishIndex + 1, r.forfeited,
						r.observedFinishTime, r.finishTime, r.startLatencyMs, r.finishLatencyMs);
			}
		}
		return null;
	}

	private List<Racer> humans() {
		List<Racer> out = new ArrayList<>();
		for (Racer r : racers) {
			if (r.human()) {
				out.add(r);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------- tick

	public void tick() {
		if (state == State.DONE) {
			return;
		}
		tick++;
		for (Racer me : humans()) {
			if (!me.running()) {
				continue;
			}
			ServerPlayer player = me.serverPlayer();
			ChocoboEntity mine = me.entity();
			if (player != null && mine != null && player.getVehicle() == mine
					&& (player.level() != level || player.distanceToSqr(mine) > 64.0D)) {
				// a command teleport moved the rider while the dismount veto held them on
				RaceManager.RELEASING.add(mine.getUUID());
				try {
					player.stopRiding();
				} finally {
					RaceManager.RELEASING.remove(mine.getUUID());
				}
			}
			if (player == null || mine == null || player.getVehicle() != mine) {
				if (player != null && mine != null && !RaceScoring.forfeitOnDismount(state == State.RUNNING)
						&& player.level() == level && player.distanceToSqr(mine) < 64.0D) {
					player.startRiding(mine, true);   // countdown: back in the saddle
					continue;
				}
				forfeit(me, player);
			}
		}
		if (humans().stream().allMatch(r -> r.forfeited)) {
			finish();
			return;
		}
		if (state == State.HOLD) {
			if (RaceScoring.seatJockeysOnHoldTick(tick)) {
				seatJockeys();
			}
			if (RaceScoring.scrubCourseOnHoldTick(tick)) {
				SquareBuilder.scrubCourse(level, track);
			}
			tickHold();
			return;
		}
		tickRunning();
	}

	private void tickHold() {
		for (int i = 0; i < racers.size(); i++) {
			if (racers.get(i).forfeited) {
				continue;
			}
			ChocoboEntity e = racers.get(i).entity();
			if (e == null || e.level() != level) {
				continue;
			}
			RacePoint stall = track.stallPos(i, grid);
			if (RaceScoring.driftedFromStall(e.getX() - stall.x(), e.getY() - stall.y(), e.getZ() - stall.z())) {
				moveRidden(e, stall.x(), stall.y(), stall.z());
			}
			e.setDeltaMovement(Vec3.ZERO);
			if (!racers.get(i).human()) {
				face(e, track.facingYaw());   // the field waits looking up the road, jockeys too
			}
		}
		int left = HOLD_TICKS - tick + 1;
		if (tick == 30) {
			// the riders have just been transported: a big banner while their chunks stream in
			for (Racer h : humans()) {
				if (h.forfeited) {
					continue;
				}
				ServerPlayer p = h.serverPlayer();
				if (p != null && p.level() == level) {
					Titles.show(p, Component.translatable("chocobosreborn.track." + track.id()),
							Component.translatable("chocobosreborn.race.get_ready"), 10, 80, 20);
				}
			}
		}
		if (tick % 4 == 0) {
			// a stream of sparks runs up the road from the grid so nobody sets off the wrong way
			double lap = track.lapLength();
			double phase = (tick % 40) / 40.0D;
			for (int i = 0; i < 6; i++) {
				RacePoint p = track.pointAt((10.0D + (i + phase) * 6.0D) / lap);
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, p.x(), p.y() + 1.2D, p.z(), 2, 0.6D, 0.1D, 0.6D, 0.0D);
			}
		}
		if (left > 0 && left <= COUNTDOWN_TICKS && left % 20 == 0) {
			for (Racer h : humans()) {
				if (h.forfeited) {
					continue;
				}
				ServerPlayer p = h.serverPlayer();
				if (p != null && p.level() == level) {
					Titles.count(p, left / 20);
				}
			}
			RacePoint mid = track.stallPos((grid - 1) / 2, grid);
			level.playSound(null, mid.x(), mid.y(), mid.z(),
					net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.NEUTRAL, 1.5F, left <= 20 ? 1.4F : 1.0F);
		}
		if (left <= 0) {
			state = State.RUNNING;
			for (Racer r : racers) {
				ServerPlayer starter = r.serverPlayer();
				r.startLatencyMs = starter == null ? 0 : tk.darrow.chocobosreborn.net.RaceLatency.millis(starter);
				ChocoboEntity e = r.anyEntity();
				if (e != null && e.isAlive()) {
					e.setRaceHeld(false);
					e.fillStamina();   // everyone leaves the grid full
                    if (starter != null) e.beginRaceInputs(starter);
				}
				if (r.goal != null) {
					r.goal.running = true;
				}
			}
			for (Racer h : humans()) {
				if (h.forfeited) {
					continue;
				}
				ServerPlayer p = h.serverPlayer();
				if (p != null && p.level() == level) {
					Titles.show(p, Component.translatable("chocobosreborn.race.go").withStyle(net.minecraft.ChatFormatting.GREEN),
							Component.empty(), 0, 20, 10);
				}
			}
		}
	}

	private void say(Component msg, boolean actionBar) {
		for (Racer h : humans()) {
			ServerPlayer p = h.serverPlayer();
			if (p != null && !h.forfeited) {
				p.displayClientMessage(msg, actionBar);
			}
		}
	}

	private void tickRunning() {
		for (Racer r : racers) {
			if (r.forfeited) {
				continue;
			}
			ChocoboEntity e = r.entity();
			if (e == null) {
				if (r.finishIndex < 0 && !r.pending()) {
					r.forfeited = true;
				}
				continue;
			}
			double hint = r.progress.lastProgress();
			double progress = track.progressAt(e.getX(), e.getZ(), hint);
			double fine = track.progressFineAt(e.getX(), e.getZ(), progress);
			double fineBefore = Double.isNaN(r.lastFine) ? fine : r.lastFine;
			r.lastFine = fine;
			boolean onCourse = layout.onCourse(e.getX(), e.getZ());
			boolean fell = RaceScoring.squareFallRescue(e.getY());
			if (r.finishIndex >= 0 || r.pending()) {
				if (fell) {
					RacePoint back = track.pointAtLane(progress, r.lane);
					moveRidden(e, back.x(), back.y(), back.z());
				}
				continue;
			}
			if (r.heldUntil > 0 && tick >= r.heldUntil) {
				r.heldUntil = 0;
				e.setRaceHeld(false);
			}
			if (r.ghostUntil > 0 && tick >= r.ghostUntil && !touchesRacer(e)) {
				r.ghostUntil = 0;
				e.setRaceGhost(false);   // solid again only once clear: never set down inside someone
			}
			RaceLapProgress.Step step = fell ? RaceLapProgress.Step.RESCUE
					: r.progress.step(progress, onCourse, RaceLapProgress.allowance(track.lapLength()));
			if (onCourse && !r.progress.offCourse()) {
				r.roadX = e.getX();
				r.roadZ = e.getZ();
			} else if (step == RaceLapProgress.Step.NONE && !Double.isNaN(r.roadX)
					&& RaceScoring.strayedTooFar(e.getX() - r.roadX, e.getZ() - r.roadZ)) {
				step = RaceLapProgress.Step.RESCUE;
			}
			if (step == RaceLapProgress.Step.NONE && !r.human() && r.goal != null && r.goal.takeSetBack()) {
				// an AI bird that got nowhere through its own recovery, twice (RacerRecovery): set it
				// back on the road like an off-road bird rather than let it rock on a wall all race
				step = RaceLapProgress.Step.RESCUE;
			}
			if (step == RaceLapProgress.Step.RESCUE) {
				rescue(r, e);
				continue;
			}
			if (r.human() && tick % 5 == 0) {
				warnShortcut(r, e, progress);
			}
			if (r.progress.offCourse() && r.human() && r.progress.offTicks() % 20 == 1) {
				ServerPlayer warned = r.serverPlayer();
				if (warned != null) {
					warned.displayClientMessage(Component.translatable("chocobosreborn.race.off_road",
							RaceScoring.offRoadSecondsLeft(r.progress.offTicks())), true);
				}
			}
			if (!r.human() && r.goal != null) {
				r.goal.lapsDone = r.laps;   // the field holds its own pace: no gap to anyone is fed
			}
			if (step == RaceLapProgress.Step.LAP) {
				r.laps++;
				ServerPlayer player = r.serverPlayer();
				if (RaceScoring.finished(r.laps, track.getLaps())) {
					// Account for outbound GO delay and inbound finish delay separately.
					r.observedFinishTime = tick - 1 + RaceScoring.crossFraction(fineBefore, fine);
					r.finishLatencyMs = player == null ? 0 : tk.darrow.chocobosreborn.net.RaceLatency.millis(player);
					r.finishTime = r.observedFinishTime - RaceScoring.lagCreditTicks(r.startLatencyMs, r.finishLatencyMs);
					if (!r.human()) {
						e.setRaceGhost(true);   // parks in the outside lane: no bumps for the birds still racing
					}
					if (r.human()) {
						e.setRacing(false);   // unlock dismount; grace must not DNF a placed rider
						if (firstFinishTick < 0) {
							firstFinishTick = tick;
						}
					}
					if (r.goal != null) {
						r.goal.lapsDone = r.laps;
					}
				} else if (player != null) {
					player.displayClientMessage(Component.translatable("chocobosreborn.race.lap",
							RaceScoring.displayLap(r.laps, track.getLaps()), track.getLaps()), true);
				}
			}
		}
		settleFinishes(false);
		if (tick % 10 == 0) {
			for (Racer me : humans()) {
				ServerPlayer player = me.serverPlayer();
				if (player == null || !me.running()) {
					continue;
				}
				ChocoboEntity mine = me.entity();
                if (mine != null && player.connection.hasChannel(tk.darrow.chocobosreborn.net.RiderPayloads.Hud.TYPE)) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                            new tk.darrow.chocobosreborn.net.RiderPayloads.Hud(mine.getId(),
                                    RaceScoring.displayLap(me.laps, track.getLaps()), track.getLaps(), placeNow(me), racers.size()));
                    continue;
                }
				boolean locked = mine != null && mine.dashLocked();
				player.displayClientMessage(Component.translatable(locked
								? "chocobosreborn.race.hud_locked" : "chocobosreborn.race.hud",
						RaceScoring.displayLap(me.laps, track.getLaps()), track.getLaps(), placeNow(me), racers.size(),
						mine == null ? 0 : mine.stamina(), mine == null ? 0 : mine.maxStamina()), true);
			}
		}
		boolean allDone = racers.stream().allMatch(r -> r.forfeited || r.finishIndex >= 0);
		boolean humansDone = humans().stream().allMatch(r -> r.finishIndex >= 0 || r.forfeited);
		boolean grace = firstFinishTick >= 0 && tick - firstFinishTick > FINISH_GRACE_TICKS;
		boolean timedOut = tick > HOLD_TICKS + RUN_CAP_TICKS;
		if (allDone || grace || timedOut || (humansDone && tick - firstFinishTick > 60)) {
			finish();
		}
	}

	/**
	 * Place finishers in crossing order. A crossing is only placed once no racer still
	 * on course could report an earlier one ({@link RaceScoring#finishSettled}); with
	 * {@code all} (the heat is ending) everyone over the line is placed now.
	 */
	private void settleFinishes(boolean all) {
		List<Racer> waiting = new ArrayList<>();
		for (Racer r : racers) {
			if (r.pending()) {
				waiting.add(r);
			}
		}
		waiting.sort(java.util.Comparator.comparingDouble(r -> r.finishTime));
		for (Racer r : waiting) {
			if (!all && !RaceScoring.finishSettled(r.finishTime, tick)) {
				break;
			}
			r.finishIndex = finishCount++;
			ServerPlayer player = r.serverPlayer();
			if (player != null) {
				player.displayClientMessage(Component.translatable("chocobosreborn.race.finish",
						RaceScoring.placeOf(r.finishIndex, finishCount)), false);
			}
		}
	}

	private int placeNow(Racer me) {
		double mineKey = RaceScoring.sortKey(me.finishIndex >= 0, me.finishIndex, me.laps, currentProgress(me));
		int ahead = 0;
		for (Racer r : racers) {
			if (r == me || r.forfeited) {
				continue;
			}
			double key = RaceScoring.sortKey(r.finishIndex >= 0, r.finishIndex, r.laps, currentProgress(r));
			if (key > mineKey) {
				ahead++;
			}
		}
		return ahead + 1;
	}

	private double currentProgress(Racer r) {
		ChocoboEntity e = r.entity();
		return e == null ? 0.0D : track.progressAt(e.getX(), e.getZ(), r.progress.lastProgress());
	}

	// ----------------------------------------------------------------- finish

	private void forfeit(Racer me, @Nullable ServerPlayer player) {
		me.forfeited = true;
		ChocoboEntity bird = me.anyEntity();
		if (bird != null) {
			bird.setRacing(false);
			bird.setRaceTrack(-1);
			if (!bird.isAlive()) {
				bird.ejectPassengers();
			} else if (bird.level() == level) {
				// a rider forfeiting by dying must not be force-seated again as a corpse
				moveRidden(bird, Square.ARRIVAL.x, Square.ARRIVAL.y, Square.ARRIVAL.z, player == null || player.isAlive());
			}
		}
		if (!aborting && firstQuit == null) {
			firstQuit = me;
		}
		if (player != null && Square.isSquare(player.level())
				&& (bird == null || player.getVehicle() != bird)) {
			player.teleportTo(Square.ARRIVAL.x, Square.ARRIVAL.y, Square.ARRIVAL.z);
			player.fallDistance = 0.0F;
		}
		if (player != null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.forfeit"), false);
			if (RaceScoring.refundBookieOnForfeit(state == State.RUNNING)) {
				refundStake(player);
			}
		}
		if (!aborting && humans().stream().allMatch(r -> r.forfeited)) {
			finish();
		}
	}

	private void finish() {
		if (state == State.DONE) {
			return;
		}
		settleFinishes(true);
		for (Racer me : humans()) {
			if (me.settled) {
				continue;
			}
			me.settled = true;
			ServerPlayer player = me.serverPlayer();
			int place = RaceScoring.resultPlace(me.finishIndex, me.forfeited, finishCount, racers.size(), placeNow(me));
			boolean completed = me.finishIndex >= 0;
			ChocoboEntity mine = me.entity();
			// racing below your class: half the purse and no credit toward promotion
			boolean below = mine != null && mine.raceClass().getId() > track.getRaceClass().getId();
			int earned = track.winPoints();
			RaceScoring.Promotion won = null;
			if (mine != null && !below && RaceScoring.awardsRankedWin(ranked, completed, place)) {
				won = mine.recordFirstPlace(true, earned);
			}
			if (player == null) {
				if (me.player != null) {
					int gp = completed && !duel ? RacePrizes.gp(track, place, ranked) : 0;
					if (below) {
						gp /= 2;
					}
					if (gp > 0) {
						RaceManager.oweGp(level.getServer(), me.player, gp);
					}
					settleBet(null, me.player, place == 1);
				}
				continue;
			}
			player.displayClientMessage(Component.translatable("chocobosreborn.race.result", place, racers.size()), false);
			int gp = completed && !duel ? RacePrizes.gp(track, place, ranked) : 0;
			if (below) {
				gp /= 2;
				player.displayClientMessage(Component.translatable("chocobosreborn.race.below_class"), false);
			}
			if (gp > 0) {
				for (int n : RaceCurrency.stacks(gp, 64)) {
					give(player, new ItemStack(ModItems.GP.get(), n));
				}
				player.displayClientMessage(Component.translatable("chocobosreborn.race.prize_gp", gp), false);
			}
			if (completed) {
				List<ItemStack> prizes = RacePrizes.items(track.getRaceClass(), place, ranked, level.random);
				for (int k = 0; k < prizes.size(); k++) {
					if (below && (k & 1) == 1) {
						continue;   // every other item when racing below your class
					}
					ItemStack prize = prizes.get(k);
					Component prizeName = prize.getHoverName();
					give(player, prize);
					player.displayClientMessage(Component.translatable("chocobosreborn.race.prize_item", prizeName), false);
				}
			}
			settleBet(player, player.getUUID(), place == 1);
			if (place == 1) {
				// Player-relative: teardown teleports home and a world sting at the line dies.
				player.playNotifySound(ModSounds.RACE_VICTORY.get(), SoundSource.MUSIC, 1.0F, 1.0F);
			}
			if (mine != null && ranked && completed && place == 1 && !below) {
				Component cls = Component.translatable("chocobosreborn.class." + mine.raceClass().id());
				if (won != null && won.promoted()) {
					// "+10 points: promoted to Class B!"
					player.displayClientMessage(Component.translatable("chocobosreborn.race.promoted", earned, cls), false);
				} else if (RaceScoring.winsUntilPromote(mine.raceClass(), mine.classWins()) == 0) {
					player.displayClientMessage(Component.translatable("chocobosreborn.almanac.d.racing_top",
							cls, mine.raceWins()), false);
				} else {
					// "+10 points (26 of 36), Class C"
					player.displayClientMessage(Component.translatable("chocobosreborn.race.class",
							earned, mine.classWins(), mine.raceClass().pointsToPromote(), cls), false);
				}
				SquareAdvancements.award(player, SquareAdvancements.FIRST_PLACE);
				if (mine.raceClass() == RaceClass.S) {
					SquareAdvancements.award(player, SquareAdvancements.CLASS_S);
				}
			}
		}
		recordWinner();
		boolean anyPlaced = racers.stream().anyMatch(r -> r.finishIndex >= 0);
		if (RaceScoring.scratchRefundsLeftoverBets(anyPlaced)) {
			refundAllBets();
		} else {
			settleRemainingBets();
		}
		settleDuel();
		teardown();
	}

	/** A ranked heat's winner goes on Whiskerwind's board (AI regulars too); a rider's win lights the plaza. */
	private void recordWinner() {
		if (!ranked || duel) {
			return;
		}
		for (Racer r : racers) {
			if (r.finishIndex != 0) {
				continue;
			}
			ChocoboEntity b = r.entity();
			String bird = b != null && b.hasCustomName() && r.human() ? b.getCustomName().getString()
					: b != null ? "#chocobosreborn.color." + b.color().id() : "?";
			double seconds = Double.isNaN(r.finishTime) ? 0.0D : Math.max(0.0D, (r.finishTime - HOLD_TICKS) / 20.0D);
			TownLife.recordWinner(level, track.getRaceClass(), r.name, bird, track.id(), seconds, r.human());
			return;
		}
	}

	/** Spectator (and extra same-heat) Rook stakes: score against the actual first-place bird. */
	private void settleRemainingBets() {
		for (BookieBet b : List.copyOf(bets)) {
			if (b.stake <= 0) {
				continue;
			}
			boolean playerFirst = false;
			for (Racer h : humans()) {
				if (b.player.equals(h.player)) {
					playerFirst = RaceScoring.resultPlace(h.finishIndex, h.forfeited, finishCount, racers.size(),
							placeNow(h)) == 1;
					break;
				}
			}
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(b.player);
			settleBet(p, b.player, playerFirst);
		}
	}

	/** Duel: the human who finished first (or the one still standing) takes both stakes; nobody finishes = refund. */
	private void settleDuel() {
		if (duelStake <= 0 || duelPaid) {
			return;
		}
		duelPaid = true;
		List<Racer> hs = humans();
		for (Racer h : hs) {
			if (h.player != null) {
				release(h.player, duelStake);
			}
		}
		Racer winner = null;
		for (Racer h : hs) {
			if (h.forfeited) {
				continue;
			}
			if (winner == null) {
				winner = h;
			} else {
				double a = RaceScoring.sortKey(h.finishIndex >= 0, h.finishIndex, h.laps, currentProgress(h));
				double b = RaceScoring.sortKey(winner.finishIndex >= 0, winner.finishIndex, winner.laps, currentProgress(winner));
				if (a > b) {
					winner = h;
				}
			}
		}
		if (winner == null && firstQuit != null && hs.size() == 2) {
			// both gone, but one walked out first: the other side of the duel takes the pot
			winner = hs.get(0) == firstQuit ? hs.get(1) : hs.get(0);
		}
		if (winner == null || (winner.finishIndex < 0 && hs.stream().noneMatch(r -> r.forfeited))) {
			for (Racer h : hs) {
				ServerPlayer p = h.serverPlayer();
				if (p != null) {
					for (int n : RaceCurrency.stacks(duelStake, 64)) {
						give(p, new ItemStack(ModItems.GP.get(), n));
					}
					p.displayClientMessage(Component.translatable("chocobosreborn.duel.refund", duelStake), false);
				} else if (h.player != null) {
					RaceManager.oweGp(level.getServer(), h.player, duelStake);
				}
			}
			return;
		}
		ServerPlayer w = winner.serverPlayer();
		int pot = duelStake * hs.size();
		if (w != null) {
			for (int n : RaceCurrency.stacks(pot, 64)) {
				give(w, new ItemStack(ModItems.GP.get(), n));
			}
		} else if (winner.player != null) {
			RaceManager.oweGp(level.getServer(), winner.player, pot);
		}
		for (Racer h : hs) {
			ServerPlayer p = h.serverPlayer();
			if (p != null) {
				p.displayClientMessage(Component.translatable("chocobosreborn.duel.result", winner.name, pot), false);
			}
		}
	}

	private void settleBet(@Nullable ServerPlayer player, UUID id, boolean playerFirst) {
		BookieBet b = betOf(id);
		if (b == null) {
			return;
		}
		boolean joeFirst = false;
		boolean teiohFirst = false;
		boolean fieldFirst = false;
		boolean opponentFirst = false;
		for (Racer r : racers) {
			if (r.finishIndex != 0) {
				continue;
			}
			if (r.human()) {
				if (!ranked && !id.equals(r.player)) {
					opponentFirst = true;
				}
				continue;
			}
			if (r.name.equals(NAME_TEIYO) || r.name.equals(NAME_RISIKA)) {
				teiohFirst = true;
			} else if (r.name.equals(NAME_JOLO) || r.name.equals(NAME_AHMI)) {
				joeFirst = true;
			} else {
				fieldFirst = true;
			}
		}
		boolean won = RaceScoring.pickWon(b.pick, ranked, playerFirst, joeFirst, teiohFirst, fieldFirst, opponentFirst);
		int held = b.stake;
		int pay = RaceScoring.payout(held, odds(b.pick), won);
		b.stake = 0;
		release(id, held);
		clearFirstSlotIf(id);
		if (pay > 0) {
			if (player != null) {
				for (int n : RaceCurrency.stacks(pay, 64)) {
					give(player, new ItemStack(ModItems.GP.get(), n));
				}
				player.displayClientMessage(Component.translatable("chocobosreborn.bet.won", pay), false);
			} else {
				RaceManager.oweGp(level.getServer(), id, pay);
			}
		} else if (player != null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.bet.lost", held), false);
		}
	}

	/** A heat that never settles hands the stake back. */
	private void refundStake(ServerPlayer player) {
		BookieBet b = betOf(player.getUUID());
		if (b == null) {
			return;
		}
		int n = b.stake;
		b.stake = 0;
		release(player.getUUID(), n);
		clearFirstSlotIf(player.getUUID());
		for (int stack : RaceCurrency.stacks(n, 64)) {
			give(player, new ItemStack(ModItems.GP.get(), stack));
		}
		player.displayClientMessage(Component.translatable("chocobosreborn.bet.refunded", n), false);
	}

	/** Abort / teardown: every remaining bookie stake goes back (or is owed if they logged off). */
	private void refundAllBets() {
		for (BookieBet b : bets) {
			if (b.stake <= 0) {
				continue;
			}
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(b.player);
			if (p != null) {
				refundStake(p);
			} else {
				RaceManager.oweGp(level.getServer(), b.player, b.stake);
				release(b.player, b.stake);
				b.stake = 0;
			}
		}
		bet = null;
		stake = 0;
		bettor = null;
	}

	/** Payout multiplier on this card: FIELD is priced by how many AI birds it actually covers. */
	public int odds(RaceScoring.BetPick pick) {
		return RaceScoring.odds(pick, track.getRaceClass().getId(), fieldBirds());
	}

	/** AI racers that are neither Teiyo nor Jolo: the ones a FIELD bet backs. */
	private int fieldBirds() {
		return (int) racers.stream()
				.filter(r -> !r.human() && !NAME_TEIYO.equals(r.name) && !NAME_JOLO.equals(r.name)
						&& !NAME_AHMI.equals(r.name) && !NAME_RISIKA.equals(r.name))
				.count();
	}

	@Nullable
	private SquareData squareData() {
		ServerLevel square = Square.level(level.getServer());
		return square == null ? null : SquareData.get(square);
	}

	/** GP taken from a player and not yet paid out or refunded: written to disk so a crash refunds it. */
	private void hold(UUID player, int amount) {
		SquareData d = squareData();
		if (d != null) {
			d.holdStake(player, amount);
		}
	}

	private void release(UUID player, int amount) {
		SquareData d = squareData();
		if (d != null) {
			d.releaseStake(player, amount);
		}
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			net.minecraft.world.entity.item.ItemEntity dropped = player.drop(stack, false);
			if (dropped != null && Square.isSquare(player.level())) {
				dropped.teleportTo(Square.ARRIVAL.x, Square.ARRIVAL.y + 0.5D, Square.ARRIVAL.z);
			}
		}
	}

	private void teardown() {
		state = State.DONE;
		refundAllBets();
		for (Racer r : racers) {
			ServerPlayer player = r.serverPlayer();
			ChocoboEntity e = r.anyEntity();
			if (e == null) {
				continue;
			}
			if (r.human()) {
				e.setRacing(false);
				e.setRaceTrack(-1);
				if (r.forfeited) {
					continue;   // already sent home; do not yank a later heat or a shop visit
				}
				if (!e.isAlive()) {
					e.ejectPassengers();   // the dismount lock no longer applies; free the rider
					continue;
				}
				if (e.level() != level) {
					continue;   // already left Whiskerwind (forfeit + pocketwatch); do not yank overworld coords
				}
				double ox = (racers.indexOf(r) - 0.5D) * 2.0D;
				moveRidden(e, Square.ARRIVAL.x + ox, Square.ARRIVAL.y, Square.ARRIVAL.z);
				if (player != null && player.getVehicle() != e && player.level() == level) {
					player.teleportTo(Square.ARRIVAL.x + ox, Square.ARRIVAL.y, Square.ARRIVAL.z);
				}
			} else {
				e.discard();
			}
		}
		for (KinStewardEntity jockey : jockeys) {
			jockey.discard();
		}
		jockeys.clear();
		forceChunks(false);
	}

	/**
	 * Teleport a bird and, when a player is driving it, tell that client too: the
	 * vanilla teleport packet is ignored for a locally controlled vehicle, so the
	 * client would keep its own position and fight the server every tick.
	 */
	public static void moveRidden(ChocoboEntity bird, double x, double y, double z) {
		moveRidden(bird, x, y, z, true);
	}

	/**
	 * {@code remount} false at heat start: HOLD seats the rider next tick, after the
	 * client has the bird at the stalls (same-tick startRiding warns "unknown entity").
	 */
	public static void moveRidden(ChocoboEntity bird, double x, double y, double z, boolean remount) {
		ServerPlayer rider = bird.getControllingPassenger() instanceof ServerPlayer sp ? sp : null;
		if (rider != null) {
			RaceManager.RELEASING.add(bird.getUUID());
			try {
				rider.stopRiding();
			} finally {
				RaceManager.RELEASING.remove(bird.getUUID());
			}
			bird.teleportTo(x, y, z);
			rider.teleportTo(x, y, z);
			if (remount) {
				rider.startRiding(bird, true);
			}
			return;
		}
		bird.teleportTo(x, y, z);
	}

	/** Tell a rider about the terrain feature coming up: take it straight if the bird suits it, else the road round. */
	private void warnShortcut(Racer r, ChocoboEntity e, double progress) {
		java.util.List<RaceTrack.Feature> features = track.terrainFeatures();
		for (int i = 0; i < features.size(); i++) {
			RaceTrack.Feature f = features.get(i);
			double ahead = RaceScoring.blocksAhead(progress, f.start() - RaceTrack.DETOUR_CONNECT, track.lapLength());
			int key = r.laps * 100 + i;
			if (ahead < 0.0D || ahead > RaceScoring.SHORTCUT_WARN_BLOCKS || r.warnedFeature == key) {
				continue;
			}
			r.warnedFeature = key;
			ServerPlayer player = r.serverPlayer();
			if (player == null) {
				return;
			}
			Component name = Component.translatable("chocobosreborn.race.feature." + f.type().name().toLowerCase(java.util.Locale.ROOT));
			String line = f.type() == RaceTrack.Feature.Type.MUD ? "chocobosreborn.race.shortcut.bog"
					: f.suits(e.color()) ? "chocobosreborn.race.shortcut.yes" : "chocobosreborn.race.shortcut.no";
			player.displayClientMessage(Component.translatable(line, name), true);
			if (f.suits(e.color())) {
				player.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 1.5F);
			}
			return;
		}
	}

	/**
	 * Off the road too long, too far, over the edge, or back on further round than a
	 * wide line allows: set the bird down on the road where it left it, facing up the
	 * course, and hold it a second. The lap is kept (Mario Kart's pick-up, gentler).
	 */
	private void rescue(Racer r, ChocoboEntity e) {
		double t = r.progress.lastProgress();
		RacePoint at = setBackPoint(level, track, e, t);
		moveRidden(e, at.x(), at.y(), at.z());
		double[] tg = track.tangent(t);
		face(e, (float) Math.toDegrees(Math.atan2(-tg[0], tg[1])));
		e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		e.setRaceHeld(true);
		e.setRaceGhost(true);
		r.heldUntil = tick + RaceScoring.RESCUE_HOLD_TICKS;
		r.ghostUntil = r.heldUntil + RacerContact.RESCUE_GHOST_TICKS;
		r.progress.rescued();
		r.roadX = at.x();
		r.roadZ = at.z();
		r.lastFine = Double.NaN;
		ServerPlayer player = r.serverPlayer();
		if (player != null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.rescued"), true);
			player.playNotifySound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
		}
	}

	/**
	 * Where a set-back puts {@code e} down at lap progress {@code t}: its line there
	 * ({@link RaceTrack#setBackPoint}: a climber on top of a ridge, anyone else round it), lifted
	 * clear of any block its box would stand in. The old point was the road level whatever the
	 * course laid there, so a climber set back on a ridge was put inside it and never moved again
	 * ("Serah", S_SKYWAY, 1600 ticks at 0 + 0.17).
	 */
	public static RacePoint setBackPoint(ServerLevel level, RaceTrack track, ChocoboEntity e, double t) {
		RacePoint at = track.setBackPoint(t, e.color());
		double y = at.y();
		for (int up = 0; up < 8; up++) {
			net.minecraft.world.phys.AABB box = e.getDimensions(e.getPose()).makeBoundingBox(at.x(), y, at.z());
			if (level.noCollision(e, box)) {
				break;
			}
			y += 1.0D;
		}
		return new RacePoint(at.x(), y, at.z());
	}

	/** Another solid racer within contact reach of {@code e} (a set-back bird stays a ghost until clear). */
	private boolean touchesRacer(ChocoboEntity e) {
		for (Racer o : racers) {
			ChocoboEntity b = o.entity();
			if (b == null || b == e || !b.contactSolid()) {
				continue;
			}
			if (Math.abs(b.getY() - e.getY()) < RacerContact.HEIGHT
					&& Math.hypot(b.getX() - e.getX(), b.getZ() - e.getZ()) < RacerContact.REACH + 0.25D) {
				return true;
			}
		}
		return false;
	}

	public boolean hasRacer(UUID bird) {
		return racers.stream().anyMatch(r -> r.bird.equals(bird));
	}

	/** A jockey this heat spawned (course scrubs keep them). The crowd is client-side scenery now. */
	boolean ownsKin(Entity kin) {
		return jockeys.contains(kin);
	}

	public void abort() {
		settleFinishes(true);
		aborting = true;
		for (Racer h : humans()) {
			if (h.running()) {
				forfeit(h, h.serverPlayer());
			}
		}
		aborting = false;
		if (state != State.DONE) {
			boolean anyPlaced = humans().stream().anyMatch(h -> h.finishIndex >= 0);
			if (anyPlaced) {
				finish();
			} else {
				settleDuel();
				refundAllBets();
				teardown();
			}
		}
	}

	/** One rider left (logout): the rest of the heat keeps running. */
	void forfeitPlayer(ServerPlayer player) {
		for (Racer me : humans()) {
			if (player.getUUID().equals(me.player) && me.running()) {
				forfeit(me, player);
				return;
			}
		}
	}
}
