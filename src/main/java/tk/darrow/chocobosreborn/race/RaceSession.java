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
 * Teiyo from Class B); a duel has two humans and four named pace birds. The
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
		boolean forfeited;
		boolean settled;
		@Nullable RacerGoal goal;

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
	/** The crowd in the infield grandstand, spawned for the heat and cleared after. */
	private final List<KinStewardEntity> fans = new ArrayList<>();
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
		return racers.stream().anyMatch(r -> NAME_JOLO.equals(r.name));
	}

	private boolean hasTeiyo() {
		return racers.stream().anyMatch(r -> NAME_TEIYO.equals(r.name));
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
		this(level, track, ranked, List.of(player), List.of(bird), 0);
	}

	/** A heat with one or two humans; {@code duelStake} > 0 means both put GP up (already taken). */
	public RaceSession(ServerLevel level, RaceTrack track, boolean ranked, List<ServerPlayer> players,
	                   List<ChocoboEntity> birds, int duelStake) {
		this.level = level;
		this.track = track;
		this.ranked = ranked;
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
			RacePoint stall = track.stallPos(i, FIELD);
			racers.add(new Racer(birds.get(i), players.get(i).getUUID(), players.get(i).getName().getString(),
					RaceTrack.stallOffset(i, FIELD), track.progressAt(stall.x(), stall.z())));
			birds.get(i).setRacing(true);
			birds.get(i).setRaceTrack(track.ordinal());
			birds.get(i).setOrderedToSit(false);
		}
		spawnField(players.size(), track.getRaceClass());
		spawnFans();
		for (Racer r : racers) {
			ChocoboEntity e = r.entity();
			if (e == null) {
				continue;
			}
			RacePoint stall = track.stallPos(racers.indexOf(r), FIELD);
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
		boolean teioh = ranked && raceClass.includesTeioh();
		int namedSlots = FIELD - humans - (teioh ? 2 : 0);
		List<FieldRoster.Entry> card = FieldRoster.draw(raceClass, Math.max(0, namedSlots),
				new java.util.Random(level.random.nextLong()));
		int cardIdx = 0;
		List<String> announced = new ArrayList<>();
		for (int i = humans; i < FIELD; i++) {
			ChocoboEntity npc = ModEntities.CHOCOBO.get().create(level);
			if (npc == null) {
				continue;
			}
			boolean isTeioh = teioh && i == FIELD - 2 && i >= humans;
			boolean isJolo = teioh && i == FIELD - 1 && i >= humans;
			FieldRoster.Entry entry = null;
			if (!isTeioh && !isJolo && cardIdx < card.size()) {
				entry = card.get(cardIdx++);
			}
			String name = isTeioh ? NAME_TEIYO : isJolo ? NAME_JOLO : entry != null ? entry.name() : "Racer";
			announced.add(name);
			RacePoint stall = track.stallPos(i, FIELD);
			npc.moveTo(stall.x(), stall.y(), stall.z(), track.facingYaw(), 0.0F);
			face(npc, track.facingYaw());
			npc.finalizeSpawn(level, level.getCurrentDifficultyAt(npc.blockPosition()), MobSpawnType.EVENT,
					new net.minecraft.world.entity.AgeableMob.AgeableMobGroupData(false));
			npc.setRaceNpc(true);
			npc.setPersistenceRequired();
			npc.setRacing(true);
			npc.setColor(isTeioh ? ChocoboColor.BLACK : isJolo ? ChocoboColor.GOLD
					: entry != null ? entry.color() : npcColor(raceClass, i));
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
			mountJockey(npc, isTeioh ? TownRole.JOCKEY_TEIYO : isJolo ? TownRole.JOCKEY_JOLO : jockeyLook(i), stall, name);
			Racer r = new Racer(npc, null, name, RaceTrack.stallOffset(i, FIELD), track.progressAt(stall.x(), stall.z()));
			RacerProfile.Role role = isTeioh ? RacerProfile.Role.TEIYO : isJolo ? RacerProfile.Role.JOLO : RacerProfile.Role.FIELD;
			r.goal = new RacerGoal(npc, track, r.lane, RacerProfile.of(raceClass, role));
			// every racer, rivals included, rolls its own form for this heat: +-5% (Ahmi: each race feels different)
			r.goal.speed = 1.0D + RacerProfile.VARIANCE * (2.0D * level.random.nextDouble() - 1.0D);
			r.goal.totalLaps = track.getLaps();
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

	/** Fans on the grandstand for this heat (one tribe look per seat, cycling). */
	private void spawnFans() {
		TownRole[] looks = {TownRole.FAN_SWARM, TownRole.FAN_CLOCK, TownRole.FAN_SPROUT, TownRole.FAN_CLAW};
		int i = 0;
		for (RaceCourseLayout.FanPost post : layout.fanPosts()) {
			KinStewardEntity fan = ModEntities.KIN_STEWARD.get().create(level);
			if (fan == null) {
				continue;
			}
			fan.moveTo(post.x(), post.y(), post.z(), post.yaw(), 0.0F);
			fan.setRole(looks[i++ % looks.length]);
			fan.installFan();
			fan.setPersistenceRequired();
			level.addFreshEntity(fan);
			fans.add(fan);
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
			case S -> i % 3 == 0 ? ChocoboColor.GOLD : (i % 3 == 1 ? ChocoboColor.BLACK : ChocoboColor.WHITE);
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

	/** Racers that have crossed the finish (tests, HUD). */
	public int finished() {
		return finishCount;
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
			if (!RaceScoring.stillOnCourse(me.forfeited, me.finishIndex)) {
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
			RacePoint stall = track.stallPos(i, FIELD);
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
			level.playSound(null, track.stallPos(2, FIELD).x(), track.stallPos(2, FIELD).y(), track.stallPos(2, FIELD).z(),
					net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.NEUTRAL, 1.5F, left <= 20 ? 1.4F : 1.0F);
		}
		if (left <= 0) {
			state = State.RUNNING;
			for (Racer r : racers) {
				ChocoboEntity e = r.anyEntity();
				if (e != null && e.isAlive()) {
					e.fillStamina();   // HOLD can drain a sprinting rider; everyone leaves the grid full
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
		Racer lead = leadHuman();
		for (Racer r : racers) {
			if (r.forfeited) {
				continue;
			}
			ChocoboEntity e = r.entity();
			if (e == null) {
				if (r.finishIndex < 0) {
					r.forfeited = true;
				}
				continue;
			}
			double progress = track.progressAt(e.getX(), e.getZ());
			boolean onCourse = layout.onCourse(e.getX(), e.getZ());
			if (RaceScoring.squareFallRescue(e.getY(), onCourse)) {
				RacePoint back = track.pointAtLane(progress, r.lane);
				moveRidden(e, back.x(), back.y(), back.z());
			}
			if (r.finishIndex >= 0) {
				continue;
			}
			if (!r.human() && r.goal != null) {
				r.goal.lapsDone = r.laps;
				double mine = lead == null ? 0.0D : lead.laps + lead.progress.lastProgress();
				r.goal.playerGap = mine - (r.laps + r.progress.lastProgress());
			}
			if (r.progress.update(progress, onCourse)) {
				r.laps++;
				ServerPlayer player = r.serverPlayer();
				if (RaceScoring.finished(r.laps, track.getLaps())) {
					r.finishIndex = finishCount++;
					if (r.human()) {
						e.setRacing(false);   // unlock dismount; grace must not DNF a placed rider
						if (firstFinishTick < 0) {
							firstFinishTick = tick;
						}
					}
					if (r.goal != null) {
						r.goal.lapsDone = r.laps;
					}
					if (player != null) {
						player.displayClientMessage(Component.translatable("chocobosreborn.race.finish",
								RaceScoring.placeOf(r.finishIndex, finishCount)), false);
					}
				} else if (player != null) {
					player.displayClientMessage(Component.translatable("chocobosreborn.race.lap",
							RaceScoring.displayLap(r.laps, track.getLaps()), track.getLaps()), true);
				}
			}
		}
		if (tick % 10 == 0) {
			for (Racer me : humans()) {
				ServerPlayer player = me.serverPlayer();
				if (player == null || me.finishIndex >= 0 || me.forfeited) {
					continue;
				}
				ChocoboEntity mine = me.entity();
				player.displayClientMessage(Component.translatable("chocobosreborn.race.hud",
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

	@Nullable
	private Racer leadHuman() {
		Racer best = null;
		double bestKey = -1.0D;
		for (Racer h : humans()) {
			if (h.forfeited) {
				continue;
			}
			double key = RaceScoring.sortKey(h.finishIndex >= 0, h.finishIndex, h.laps, currentProgress(h));
			if (best == null || key > bestKey) {
				best = h;
				bestKey = key;
			}
		}
		return best;
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
		return e == null ? 0.0D : track.progressAt(e.getX(), e.getZ());
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
			if (mine != null && !below && RaceScoring.awardsRankedWin(ranked, completed, place)) {
				mine.recordFirstPlace(true);
			}
			if (player == null) {
				if (me.player != null) {
					int gp = completed && duelStake <= 0 ? RacePrizes.gp(track.getRaceClass(), place, ranked) : 0;
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
			int gp = completed && duelStake <= 0 ? RacePrizes.gp(track.getRaceClass(), place, ranked) : 0;
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
				if (RaceScoring.winsUntilPromote(mine.raceClass(), mine.classWins()) == 0) {
					player.displayClientMessage(Component.translatable("chocobosreborn.almanac.d.racing_top",
							Component.translatable("chocobosreborn.class." + mine.raceClass().id()), mine.classWins()), false);
				} else {
					player.displayClientMessage(Component.translatable("chocobosreborn.race.class",
							Component.translatable("chocobosreborn.class." + mine.raceClass().id()), mine.classWins(),
							RaceClass.WINS_TO_PROMOTE), false);
				}
				SquareAdvancements.award(player, SquareAdvancements.FIRST_PLACE);
				if (mine.raceClass() == RaceClass.S) {
					SquareAdvancements.award(player, SquareAdvancements.CLASS_S);
				}
			}
		}
		boolean anyPlaced = humans().stream().anyMatch(h -> h.finishIndex >= 0);
		if (RaceScoring.scratchRefundsLeftoverBets(anyPlaced)) {
			refundAllBets();
		} else {
			settleRemainingBets();
		}
		settleDuel();
		teardown();
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
			if (r.name.equals(NAME_TEIYO)) {
				teiohFirst = true;
			} else if (r.name.equals(NAME_JOLO)) {
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
				.filter(r -> !r.human() && !NAME_TEIYO.equals(r.name) && !NAME_JOLO.equals(r.name))
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
		for (KinStewardEntity fan : fans) {
			fan.discard();
		}
		fans.clear();
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

	public boolean hasRacer(UUID bird) {
		return racers.stream().anyMatch(r -> r.bird.equals(bird));
	}

	/** A fan or jockey this heat spawned (course scrubs keep them). */
	boolean ownsKin(Entity kin) {
		return fans.contains(kin) || jockeys.contains(kin);
	}

	public void abort() {
		aborting = true;
		for (Racer h : humans()) {
			if (RaceScoring.stillOnCourse(h.forfeited, h.finishIndex)) {
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
			if (player.getUUID().equals(me.player) && RaceScoring.stillOnCourse(me.forfeited, me.finishIndex)) {
				forfeit(me, player);
				return;
			}
		}
	}
}
