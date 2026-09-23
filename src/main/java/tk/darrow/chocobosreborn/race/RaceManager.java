package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;

/** Server-side driver for Chocobo Square: sessions, entry, and the way home. */
public final class RaceManager {
	private static final List<RaceSession> SESSIONS = new ArrayList<>();
	/** GameTests: treat this level as the Square (the test server has no custom dimensions). */
	public static @Nullable ServerLevel testLevel;

	private RaceManager() {
	}

	public static int liveSessions() {
		return (int) SESSIONS.stream().filter(RaceSession::live).count();
	}

	public static @Nullable RaceSession sessionOf(UUID player) {
		for (RaceSession s : SESSIONS) {
			if (s.live() && s.belongsTo(player)) {
				return s;
			}
		}
		return null;
	}

	/**
	 * Bookie: a racer's own heat, or the single HOLD heat in Whiskerwind so a
	 * spectator can still put GP on the board. Several open heats stay pending.
	 */
	public static @Nullable RaceSession sessionForBet(UUID player) {
		RaceSession mine = sessionOf(player);
		if (mine != null) {
			return mine;
		}
		RaceSession open = null;
		int n = 0;
		for (RaceSession s : SESSIONS) {
			if (s.live() && s.ranked() && RaceScoring.booksOpen(true, s.running())) {
				n++;
				open = s;
			}
		}
		if (!RaceScoring.attachSpectatorBet(false, HeatSchedule.entered(player), n)) {
			return null;
		}
		return open;
	}

	/** A live heat already on this course. */
	public static boolean trackBusy(RaceTrack track) {
		return SESSIONS.stream().anyMatch(s -> s.live() && s.track() == track);
	}

	/** True while a live heat owns this bird (player or field NPC). */
	public static boolean isActiveRacer(UUID bird) {
		return SESSIONS.stream().anyMatch(s -> s.live() && s.hasRacer(bird));
	}

	/** True while a live heat spawned this kin (grandstand fan or jockey). */
	static boolean isSessionKin(Entity kin) {
		return SESSIONS.stream().anyMatch(s -> s.live() && s.ownsKin(kin));
	}

	/** True when a live heat's island covers this point (stray fans/jockeys on other islands can go). */
	static boolean heatContains(double x, double z) {
		for (RaceSession s : SESSIONS) {
			if (!s.live()) {
				continue;
			}
			RaceTrack t = s.track();
			if (Math.abs(x - t.centerX()) <= t.getRadiusX() + 32.0D
					&& Math.abs(z - t.centerZ()) <= t.getRadiusZ() + 32.0D) {
				return true;
			}
		}
		return false;
	}

	public static boolean anyRunning() {
		return SESSIONS.stream().anyMatch(RaceSession::running);
	}

	// ------------------------------------------------------------------ entry

	/** Overworld Esther: bring a rider and their saddled bird to the Square paddock. */
	public static boolean enterSquare(ServerPlayer player, ChocoboEntity bird) {
		MinecraftServer server = player.server;
		ServerLevel square = Square.level(server);
		if (square == null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.square.missing"), false);
			return false;
		}
		if (Square.isSquare(player.level()) && sessionOf(player.getUUID()) != null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.already"), true);
			return false;
		}
		boolean owned = bird.isOwnedBy(player) || player.getAbilities().instabuild;
		if (!RaceScoring.canEnterSquare(bird.isBaby(), bird.saddled(), owned)) {
			player.displayClientMessage(Component.translatable("chocobosreborn.square.need_bird"), true);
			return false;
		}
		SquareBuilder.buildPaddock(square);
		SquareBuilder.spawnKeepers(square);
		SquareBuilder.requestKeeperSync(); // chunks around the village finish loading after the teleport
		SquareData data = SquareData.get(square);
		ServerLevel from = player.serverLevel();
		net.minecraft.world.phys.Vec3 origin = player.position();
		if (!Square.isSquare(player.level())) {
			// always refresh: a stale point (left by death, /tp or a disconnect) would drop them somewhere old
			data.putReturn(player.getUUID(), new SquareData.ReturnPoint(player.level().dimension(),
					player.position(), player.getYRot()));
		}
		Square.teleportMounted(player, bird, square, Square.ARRIVAL, Square.ARRIVAL_YAW);
		bringBirds(player, from, origin, square, Square.ARRIVAL, Square.ARRIVAL_YAW);
		payOwedGp(player);
		player.displayClientMessage(Component.translatable("chocobosreborn.square.welcome"), false);
		SquareAdvancements.award(player, SquareAdvancements.SQUARE);
		return true;
	}

	/** Pocketwatch on foot: the player alone goes to the Square paddock. */
	public static boolean enterSquareOnFoot(ServerPlayer player) {
		MinecraftServer server = player.server;
		ServerLevel square = Square.level(server);
		if (square == null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.square.missing"), false);
			return false;
		}
		if (Square.isSquare(player.level()) && sessionOf(player.getUUID()) != null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.already"), true);
			return false;
		}
		SquareBuilder.buildPaddock(square);
		SquareBuilder.spawnKeepers(square);
		SquareBuilder.requestKeeperSync();
		ServerLevel from = player.serverLevel();
		net.minecraft.world.phys.Vec3 origin = player.position();
		if (!Square.isSquare(player.level())) {
			SquareData.get(square).putReturn(player.getUUID(), new SquareData.ReturnPoint(player.level().dimension(),
					player.position(), player.getYRot()));
		}
		Square.teleport(player, square, Square.ARRIVAL, Square.ARRIVAL_YAW);
		bringBirds(player, from, origin, square, Square.ARRIVAL, Square.ARRIVAL_YAW);
		payOwedGp(player);
		player.displayClientMessage(Component.translatable("chocobosreborn.square.welcome"), false);
		SquareAdvancements.award(player, SquareAdvancements.SQUARE);
		return true;
	}

	/** Back to where the player came from (bird too, if riding). */
	public static void leaveSquare(ServerPlayer player) {
		ServerLevel square = Square.level(player.server);
		if (square == null || !Square.isSquare(player.level())) {
			return;
		}
		RaceSession s = sessionOf(player.getUUID());
		if (s != null) {
			s.forfeitPlayer(player);
		}
		HeatSchedule.drop(player);
		SquareData.ReturnPoint rp = SquareData.get(square).takeReturn(player.getUUID());
		ServerLevel target = rp == null ? player.server.overworld() : player.server.getLevel(rp.dimension());
		if (target == null) {
			target = player.server.overworld();
		}
		Vec3 pos = rp == null ? Vec3.atBottomCenterOf(target.getSharedSpawnPos()) : rp.pos();
		float yaw = rp == null ? 0.0F : rp.yaw();
		net.minecraft.world.phys.Vec3 origin = player.position();
		DuelDesk.withdraw(player);
		TradeDesk.withdraw(player);
		refundPendingBet(player);
		if (player.getVehicle() instanceof ChocoboEntity bird) {
			Square.teleportMounted(player, bird, target, pos, yaw);
		} else {
			Square.teleport(player, target, pos, yaw);
		}
		bringBirds(player, square, origin, target, pos, yaw);
	}

	/** Square Esther / gates: start a heat on course {@code course} for the bird's class. */
	public static boolean startRace(ServerPlayer player, int course, boolean ranked) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!Square.isSquare(level) && level != testLevel) {
			player.displayClientMessage(Component.translatable("chocobosreborn.gate.only_square"), true);
			return false;
		}
		if (!(player.getVehicle() instanceof ChocoboEntity bird)) {
			player.displayClientMessage(Component.translatable("chocobosreborn.square.need_bird"), true);
			return false;
		}
		return startRace(player, RaceTrack.forClass(bird.raceClass(), course), ranked);
	}

	/** Start a heat on a chosen course (the course picker's answer). */
	public static boolean startRace(ServerPlayer player, RaceTrack track, boolean ranked) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!Square.isSquare(level) && level != testLevel) {
			player.displayClientMessage(Component.translatable("chocobosreborn.gate.only_square"), true);
			return false;
		}
		if (!(player.getVehicle() instanceof ChocoboEntity bird)) {
			player.displayClientMessage(Component.translatable("chocobosreborn.square.need_bird"), true);
			return false;
		}
		boolean owned = bird.isOwnedBy(player) || player.getAbilities().instabuild;
		if (!RaceScoring.canEnterSquare(bird.isBaby(), bird.saddled(), owned)) {
			player.displayClientMessage(Component.translatable("chocobosreborn.square.need_bird"), true);
			return false;
		}
		if (sessionOf(player.getUUID()) != null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.already"), true);
			return false;
		}
		if (HeatSchedule.entered(player.getUUID())) {
			player.displayClientMessage(Component.translatable("chocobosreborn.heat.wait"), true);
			return false;
		}
		if (trackBusy(track)) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.occupied"), true);
			return false;
		}
		if (bird.armor() != null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.no_armor"), true);
			return false;
		}
		if (ranked && !RaceScoring.mayEnterCourse(bird.raceClass().getId(), track.getRaceClass().getId())) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.wrong_class"), true);
			return false;
		}
		DuelDesk.withdraw(player);
		TradeDesk.withdraw(player);
		RaceSession session = new RaceSession(level, track, ranked, player, bird);
		SESSIONS.add(session);
		return true;
	}

	/** Two humans on one course (duel master); stakes already taken by DuelDesk. */
	public static boolean startDuel(ServerPlayer a, ChocoboEntity birdA, ServerPlayer b, ChocoboEntity birdB,
	                                RaceTrack track, int stake) {
		if (!(a.level() instanceof ServerLevel level) || a.level() != b.level()) {
			return false;
		}
		if (birdA.armor() != null || birdB.armor() != null) {
			a.displayClientMessage(Component.translatable("chocobosreborn.race.no_armor"), true);
			b.displayClientMessage(Component.translatable("chocobosreborn.race.no_armor"), true);
			return false;
		}
		if (!birdA.saddled() || !birdB.saddled() || birdA.isBaby() || birdB.isBaby()
				|| (!birdA.isOwnedBy(a) && !a.getAbilities().instabuild)
				|| (!birdB.isOwnedBy(b) && !b.getAbilities().instabuild)) {
			a.displayClientMessage(Component.translatable("chocobosreborn.square.need_bird"), true);
			b.displayClientMessage(Component.translatable("chocobosreborn.square.need_bird"), true);
			return false;
		}
		if (!RaceScoring.mayEnterCourse(birdA.raceClass().getId(), track.getRaceClass().getId())
				|| !RaceScoring.mayEnterCourse(birdB.raceClass().getId(), track.getRaceClass().getId())) {
			a.displayClientMessage(Component.translatable("chocobosreborn.race.wrong_class"), true);
			b.displayClientMessage(Component.translatable("chocobosreborn.race.wrong_class"), true);
			return false;
		}
		if (sessionOf(a.getUUID()) != null || sessionOf(b.getUUID()) != null || trackBusy(track)
				|| HeatSchedule.entered(a.getUUID()) || HeatSchedule.entered(b.getUUID())) {
			a.displayClientMessage(Component.translatable("chocobosreborn.race.occupied"), true);
			b.displayClientMessage(Component.translatable("chocobosreborn.race.occupied"), true);
			return false;
		}
		DuelDesk.consume(a.server, a.getUUID());
		DuelDesk.withdraw(b);
		TradeDesk.withdraw(a);
		TradeDesk.withdraw(b);
		RaceSession session = new RaceSession(level, track, false, java.util.List.of(a, b), java.util.List.of(birdA, birdB), true, stake);
		SESSIONS.add(session);
		return true;
	}

	/** A session built elsewhere (the heat timetable). */
	static void addSession(RaceSession session) {
		SESSIONS.add(session);
	}

	/** The course picker answered: mode 0 = enter the timetable's next ranked heat, 1 = post a duel challenge. */
	public static void onCourseChosen(ServerPlayer player, int trackId, int mode, int stake) {
		RaceTrack track = RaceTrack.byId(trackId);
		if (!Square.isSquare(player.level())) {
			return;
		}
		if (!(player.getVehicle() instanceof ChocoboEntity bird) || !bird.saddled() || bird.isBaby()
				|| (!bird.isOwnedBy(player) && !player.getAbilities().instabuild)) {
			player.displayClientMessage(Component.translatable("chocobosreborn.square.need_bird"), true);
			return;
		}
		if (bird.armor() != null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.no_armor"), true);
			return;
		}
		if (!RaceScoring.mayEnterCourse(bird.raceClass().getId(), track.getRaceClass().getId())) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.wrong_class"), true);
			return;
		}
		if (mode == 1) {
			DuelDesk.post(player, track, RaceScoring.clampDuelStake(stake));
		} else {
			if (sessionOf(player.getUUID()) != null) {
				player.displayClientMessage(Component.translatable("chocobosreborn.race.already"), true);
				return;
			}
			HeatSchedule.schedule(player, bird, track);
		}
	}

	/** Owned birds set to Follow near the player come with them through a teleport. */
	public static void bringBirds(ServerPlayer player, ServerLevel from, net.minecraft.world.phys.Vec3 origin,
	                              ServerLevel target, net.minecraft.world.phys.Vec3 dest, float yaw) {
		java.util.List<ChocoboEntity> birds = from.getEntitiesOfClass(ChocoboEntity.class,
				new net.minecraft.world.phys.AABB(origin, origin).inflate(16.0D),
				b -> b.isTame() && player.getUUID().equals(b.getOwnerUUID())
						&& b.command() == ChocoboEntity.Command.FOLLOW && !b.racing()
						&& !b.isVehicle());
		int i = 0;
		for (ChocoboEntity b : birds) {
			double ox = (i % 3 - 1) * 2.0D, oz = (i / 3 + 1) * 2.0D;
			Square.teleport(b, target, dest.add(ox, 0.0D, oz), yaw);
			i++;
		}
	}

	/**
	 * Bookie: record a bet. With a live heat in its hold it goes on that session;
	 * otherwise it waits in the player's persistent data for the next heat Esther
	 * starts (the rider is locked in the stall once a heat exists, so Rook has to
	 * take bets beforehand).
	 */
	public static boolean placeBet(ServerPlayer player, RaceScoring.BetPick pick, int stake) {
		int amount = RaceScoring.clampStake(stake);
		RaceSession s = sessionForBet(player.getUUID());
		if (s != null) {
			if (!RaceScoring.booksOpen(true, s.running())
					|| !RaceScoring.mayPlaceBet(true, s.hasBookieBet(player.getUUID()), amount)) {
				return false;
			}
			s.takeBookieBet(player.getUUID(), s.legalPick(pick, player.getUUID()), amount);
			return true;
		}
		CompoundTag tag = player.getPersistentData();
		if (SESSIONS.stream().anyMatch(RaceSession::live) && !HeatSchedule.entered(player.getUUID())) {
			return false;
		}
		if (HeatSchedule.entered(player.getUUID()) && pick != RaceScoring.BetPick.SELF) {
			return false;   // entered racers may only back themselves
		}
		if (!RaceScoring.mayPlaceBet(true, tag.contains(PENDING_BET), amount)) {
			return false;
		}
		tag.putInt(PENDING_BET, pick.ordinal());
		tag.putInt(PENDING_STAKE, amount);
		return true;
	}

	/** Birds whose rider a session is deliberately letting go (a teleported player). */
	static final java.util.Set<java.util.UUID> RELEASING = new java.util.HashSet<>();

	private static final String PENDING_BET = "chocobosreborn_pending_bet";
	private static final String PENDING_STAKE = "chocobosreborn_pending_stake";
	private static final String SQUARE_DEATH = "chocobosreborn_square_death";

	@org.jetbrains.annotations.Nullable
	static RaceScoring.BetPick takePendingBet(ServerPlayer player) {
		CompoundTag tag = player.getPersistentData();
		if (!tag.contains(PENDING_BET)) {
			return null;
		}
		int i = tag.getInt(PENDING_BET);
		tag.remove(PENDING_BET);
		RaceScoring.BetPick[] v = RaceScoring.BetPick.values();
		return v[Math.max(0, Math.min(v.length - 1, i))];
	}

	static int takePendingStake(ServerPlayer player) {
		CompoundTag tag = player.getPersistentData();
		int n = tag.getInt(PENDING_STAKE);
		tag.remove(PENDING_STAKE);
		return n;
	}

	/** True if the player holds a bet waiting for the next heat. */
	public static boolean hasPendingBet(ServerPlayer player) {
		return player.getPersistentData().contains(PENDING_BET);
	}

	/**
	 * Move a waiting stake onto the live HOLD without taking another stack of GP.
	 * Returns the attached stake, or 0 if the pending was refunded / could not attach.
	 */
	public static int settlePendingOnto(ServerPlayer player, RaceSession s) {
		if (!hasPendingBet(player)) {
			return 0;
		}
		if (!RaceScoring.booksOpen(true, s.running()) || s.hasBookieBet(player.getUUID())) {
			refundPendingBet(player);
			return 0;
		}
		RaceScoring.BetPick pending = takePendingBet(player);
		int n = takePendingStake(player);
		if (pending == null || n <= 0) {
			return 0;
		}
		RaceScoring.BetPick legal = s.legalPick(pending, player.getUUID());
		if (legal == RaceScoring.BetPick.SELF && pending != RaceScoring.BetPick.SELF) {
			// the bettor is riding in this heat: a pick that pays when they lose goes back
			DuelDesk.giveGp(player, n);
			player.displayClientMessage(Component.translatable("chocobosreborn.bet.refunded", n), false);
			return 0;
		}
		s.takeBookieBet(player.getUUID(), legal, n);
		return n;
	}

	/** Scratch / miss: hand the waiting stake back. */
	public static void refundPendingBet(ServerPlayer player) {
		if (!hasPendingBet(player)) {
			return;
		}
		takePendingBet(player);
		int n = takePendingStake(player);
		if (n > 0) {
			DuelDesk.giveGp(player, n);
			player.displayClientMessage(Component.translatable("chocobosreborn.bet.refunded", n), false);
		}
	}

	// ----------------------------------------------------------------- events

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		ServerLevel square = testLevel != null ? testLevel : Square.level(event.getServer());
		if (square != null) {
			SquareBuilder.tickKeeperSync(square);
			DuelDesk.tick(square);
			TradeDesk.tick(square);
			HeatSchedule.tick(square);
		}
		if (square != null) {
			for (ServerPlayer p : square.players()) {
				// invulnerable is not the same as unstoppable: a visitor who goes over the edge
				// still falls for ever, and out-of-world damage bypasses protection anyway
				if (RaceScoring.squareVisitorFallRescue(p.getY(), p.getVehicle() != null)) {
					p.teleportTo(Square.ARRIVAL.x, Square.ARRIVAL.y, Square.ARRIVAL.z);
					p.resetFallDistance();
				}
				if (p.isOnFire()) {
					p.setRemainingFireTicks(0);   // a lava feature cannot hurt them; do not leave them alight
				}
			}
		}
		if (SESSIONS.isEmpty()) {
			return;
		}
		for (RaceSession s : new ArrayList<>(SESSIONS)) {
			s.tick();
		}
		SESSIONS.removeIf(s -> !s.live());
	}

	/**
	 * Nothing in Whiskerwind hurts a visitor. The birds are already protected there
	 * ({@link ChocoboEntity#isInvulnerableTo}); without this the rider of a bird that
	 * cannot take a lava feature burned to death on the course while the bird it was
	 * sitting on walked out unharmed, and their inventory dropped into the void.
	 */
	@SubscribeEvent
	public static void onInvulnerabilityCheck(net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent event) {
		if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)) {
			return;
		}
		if (RaceScoring.squareRiderProtected(Square.isSquare(player.level()),
				event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))) {
			event.setInvulnerable(true);
		}
	}

	@SubscribeEvent
	public static void onMount(EntityMountEvent event) {
		// The server decides who rides. The client only ever dismounts because a passengers packet
		// told it to, and that packet lands before the bird's racing flag update: a client-side veto
		// kept the player seated on a bird the server had already released (a /tp mid-heat).
		if (!event.isDismounting() || event.getLevel().isClientSide()) {
			return;
		}
		Entity vehicle = event.getEntityBeingMounted();
		Entity rider = event.getEntityMounting();
		// a logout dismounts too (PlayerList.remove): never hold a disconnected player
		boolean leaving = rider instanceof ServerPlayer spl && (spl.isChangingDimension() || spl.hasDisconnected());
		// only the rider's own sneak-dismount is locked: /tp, waystones and other teleports call
		// stopRiding() before isChangingDimension() is set and must be let through
		if (vehicle instanceof ChocoboEntity bird && !bird.racing() && bird.isAlive() && rider.isAlive()
				&& rider.isShiftKeyDown()
				&& rider == bird.getControllingPassenger() && !RELEASING.contains(bird.getUUID())
				&& !leaving) {
			boolean inOrOnWater = bird.isInWater()
					|| bird.level().getFluidState(bird.blockPosition().below()).is(net.minecraft.tags.FluidTags.WATER);
			if (RaceScoring.lockOffCourseDismount(bird.color().fly(), bird.onGround(), bird.color().waterWalk(),
					inOrOnWater)) {
				event.setCanceled(true);
				return;
			}
		}
		if (vehicle instanceof ChocoboEntity bird && bird.racing() && bird.isAlive() && !bird.isRemoved()
				&& rider.isAlive() && !rider.isRemoved()
				// a teleport / dimension change dismounts through the same hook: let it
				&& !leaving
				&& !RELEASING.contains(bird.getUUID())
				&& RaceScoring.cancelPassengerDismount(true, false, false, true, true)) {
			event.setCanceled(true);
		}
	}

	/** Statics must not outlive the server: singleplayer switches worlds in one JVM. */
	/** Whiskerwind and the courses are protected: no breaking or placing by players (operators in creative excepted). */
	@SubscribeEvent
	public static void onBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
		if (event.getLevel() instanceof ServerLevel level && Square.isSquare(level) && !bypasses(event.getPlayer())) {
			event.setCanceled(true);
			event.getPlayer().displayClientMessage(Component.translatable("chocobosreborn.square.protected"), true);
		}
	}

	@SubscribeEvent
	public static void onPlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
		if (event.getLevel() instanceof ServerLevel level && Square.isSquare(level)
				&& event.getEntity() instanceof ServerPlayer player && !bypasses(player)) {
			event.setCanceled(true);
			player.displayClientMessage(Component.translatable("chocobosreborn.square.protected"), true);
		}
	}

	/**
	 * Whiskerwind's dimension has {@code bed_works: false} (no respawning or sleeping the
	 * night away out here), and vanilla answers that like the Nether: the bed explodes. The
	 * village's beds are furniture, so a click on one (or on a respawn anchor) does nothing.
	 */
	@SubscribeEvent
	public static void onUseBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
		if (!Square.isSquare(event.getLevel()) && (testLevel == null || event.getLevel() != testLevel)) {
			return;
		}
		var block = event.getLevel().getBlockState(event.getPos()).getBlock();
		if (block instanceof net.minecraft.world.level.block.BedBlock
				|| block instanceof net.minecraft.world.level.block.RespawnAnchorBlock) {
			event.setCanceled(true);
			event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
			if (!event.getLevel().isClientSide) {
				event.getEntity().displayClientMessage(Component.translatable("chocobosreborn.square.bed"), true);
			}
		}
	}

	private static boolean bypasses(net.minecraft.world.entity.player.Player player) {
		return player.isCreative() && player.hasPermissions(2);
	}

	/** Nothing spawns on its own in the Square: only what the mod places (keepers, fields, fans, jockeys). */
	@SubscribeEvent
	public static void onFinalizeSpawn(net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent event) {
		if (!Square.isSquare(event.getLevel().getLevel())) {
			return;
		}
		switch (event.getSpawnType()) {
			case EVENT, SPAWN_EGG, BREEDING, COMMAND, BUCKET, DISPENSER, TRIGGERED -> {
			}
			default -> event.setSpawnCancelled(true);
		}
	}

	/** A crash mid-heat leaves the course force-loaded (forced chunks are saved with the world): let them go. */
	@SubscribeEvent
	public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
		ServerLevel square = Square.level(event.getServer());
		if (square == null) {
			return;
		}
		for (long key : SquareData.get(square).takeForcedChunks()) {
			square.setChunkForced((int) (key >> 32), (int) key, false);
		}
	}

	@SubscribeEvent
	public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
		for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
			refundPendingBet(p);
		}
		DuelDesk.refundAndClear(event.getServer());
		TradeDesk.clear();
		for (RaceSession s : new ArrayList<>(SESSIONS)) {
			if (s.live()) {
				s.abort();
			}
		}
		for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
			payOwedGp(p);
		}
		SESSIONS.clear();
		SquareBuilder.resetPending();
		ServerLevel square = Square.level(event.getServer());
		if (square != null) {
			HeatSchedule.reset(square);
		} else {
			HeatSchedule.reset();
		}
		testLevel = null;
	}

	@SubscribeEvent
	public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer sp) || !Square.isSquare(sp.level())) {
			return;
		}
		RaceSession s = sessionOf(sp.getUUID());
		if (s != null) {
			s.forfeitPlayer(sp);
		}
		DuelDesk.withdraw(sp);
		TradeDesk.withdraw(sp);
		sp.getPersistentData().putBoolean(SQUARE_DEATH, true);
	}

	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		CompoundTag old = event.getOriginal().getPersistentData();
		CompoundTag now = event.getEntity().getPersistentData();
		if (old.contains(PENDING_BET)) {
			now.putInt(PENDING_BET, old.getInt(PENDING_BET));
			now.putInt(PENDING_STAKE, old.getInt(PENDING_STAKE));
		}
		if (old.contains("chocobosreborn_pick")) {
			now.putInt("chocobosreborn_pick", old.getInt("chocobosreborn_pick"));
		}
		if (event.isWasDeath() && old.getBoolean(SQUARE_DEATH)) {
			now.putBoolean(SQUARE_DEATH, true);
		}
	}

	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer sp) || !sp.getPersistentData().getBoolean(SQUARE_DEATH)) {
			return;
		}
		sp.getPersistentData().remove(SQUARE_DEATH);
		ServerLevel square = Square.level(sp.server);
		if (square == null) {
			return;
		}
		Square.teleport(sp, square, Square.ARRIVAL, Square.ARRIVAL_YAW);
	}

	private static final java.util.Map<java.util.UUID, Integer> OWED_GP = new java.util.HashMap<>();

	/** GP that could not be handed over because the player had already left. */
	static void oweGp(net.minecraft.server.MinecraftServer server, UUID id, int amount) {
		if (amount <= 0) {
			return;
		}
		ServerLevel square = Square.level(server);
		if (square != null) {
			SquareData.get(square).oweGp(id, amount);
		} else {
			OWED_GP.merge(id, amount, Integer::sum);
		}
	}

	private static void payOwedGp(ServerPlayer player) {
		int n = 0;
		Integer mem = OWED_GP.remove(player.getUUID());
		if (mem != null) {
			n += mem;
		}
		ServerLevel square = Square.level(player.server);
		if (square != null) {
			n += SquareData.get(square).takeOwedGp(player.getUUID());
		}
		if (n > 0) {
			DuelDesk.giveGp(player, n);
			player.displayClientMessage(Component.translatable("chocobosreborn.gp.owed", n), false);
		}
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer sp) {
			payOwedGp(sp);
		}
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer sp)) {
			return;
		}
		DuelDesk.withdraw(sp);
		TradeDesk.withdraw(sp);
		refundPendingBet(sp);
		HeatSchedule.drop(sp);
		RaceSession s = sessionOf(sp.getUUID());
		if (s != null) {
			s.forfeitPlayer(sp);
		}
	}
}
