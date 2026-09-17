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
			if (s.live() && s.hasPlayer(player)) {
				return s;
			}
		}
		return null;
	}

	/** A live heat already on this course. */
	public static boolean trackBusy(RaceTrack track) {
		return SESSIONS.stream().anyMatch(s -> s.live() && s.track() == track);
	}

	/** True while a live heat owns this bird (player or field NPC). */
	public static boolean isActiveRacer(UUID bird) {
		return SESSIONS.stream().anyMatch(s -> s.live() && s.hasRacer(bird));
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
			s.abort();
		}
		SquareData.ReturnPoint rp = SquareData.get(square).takeReturn(player.getUUID());
		ServerLevel target = rp == null ? player.server.overworld() : player.server.getLevel(rp.dimension());
		if (target == null) {
			target = player.server.overworld();
		}
		Vec3 pos = rp == null ? Vec3.atBottomCenterOf(target.getSharedSpawnPos()) : rp.pos();
		float yaw = rp == null ? 0.0F : rp.yaw();
		net.minecraft.world.phys.Vec3 origin = player.position();
		DuelDesk.withdraw(player);
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
			return false;
		}
		if (!(player.getVehicle() instanceof ChocoboEntity bird)) {
			player.displayClientMessage(Component.translatable("chocobosreborn.square.need_bird"), true);
			return false;
		}
		if (sessionOf(player.getUUID()) != null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.already"), true);
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
		if (ranked && track.getRaceClass().getId() > bird.raceClass().getId()) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.wrong_class"), true);
			return false;
		}
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
		if (sessionOf(a.getUUID()) != null || sessionOf(b.getUUID()) != null || trackBusy(track)) {
			a.displayClientMessage(Component.translatable("chocobosreborn.race.occupied"), true);
			b.displayClientMessage(Component.translatable("chocobosreborn.race.occupied"), true);
			return false;
		}
		RaceSession session = new RaceSession(level, track, false, java.util.List.of(a, b), java.util.List.of(birdA, birdB), stake);
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
		if (!(player.getVehicle() instanceof ChocoboEntity bird) || !bird.saddled() || bird.isBaby()) {
			player.displayClientMessage(Component.translatable("chocobosreborn.square.need_bird"), true);
			return;
		}
		if (bird.armor() != null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.no_armor"), true);
			return;
		}
		if (mode == 1) {
			DuelDesk.post(player, track, Math.max(0, Math.min(64, stake)));
		} else {
			if (track.getRaceClass().getId() > bird.raceClass().getId()) {
				player.displayClientMessage(Component.translatable("chocobosreborn.race.wrong_class"), true);
				return;
			}
			if (sessionOf(player.getUUID()) != null) {
				player.displayClientMessage(Component.translatable("chocobosreborn.race.already"), true);
				return;
			}
			HeatSchedule.schedule(player, bird, track);
		}
	}

	/** Owned, awake birds near the player follow them through a teleport. */
	public static void bringBirds(ServerPlayer player, ServerLevel from, net.minecraft.world.phys.Vec3 origin,
	                              ServerLevel target, net.minecraft.world.phys.Vec3 dest, float yaw) {
		java.util.List<ChocoboEntity> birds = from.getEntitiesOfClass(ChocoboEntity.class,
				new net.minecraft.world.phys.AABB(origin, origin).inflate(16.0D),
				b -> b.isTame() && player.getUUID().equals(b.getOwnerUUID()) && !b.isOrderedToSit() && !b.racing()
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
		RaceSession s = sessionOf(player.getUUID());
		if (s != null) {
			if (!RaceScoring.booksOpen(true, s.running()) || !RaceScoring.mayPlaceBet(true, s.bet != null, amount)) {
				return false;
			}
			s.bet = RaceScoring.legalize(pick, s.ranked(), s.track().getRaceClass().includesTeioh());
			s.stake = amount;
			return true;
		}
		CompoundTag tag = player.getPersistentData();
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
		if (SESSIONS.isEmpty()) {
			return;
		}
		for (RaceSession s : new ArrayList<>(SESSIONS)) {
			s.tick();
		}
		SESSIONS.removeIf(s -> !s.live());
	}

	@SubscribeEvent
	public static void onMount(EntityMountEvent event) {
		if (!event.isDismounting()) {
			return;
		}
		Entity vehicle = event.getEntityBeingMounted();
		Entity rider = event.getEntityMounting();
		if (vehicle instanceof ChocoboEntity bird && !bird.racing() && bird.isAlive() && rider.isAlive()
				&& rider == bird.getControllingPassenger() && !RELEASING.contains(bird.getUUID())
				&& !(rider instanceof ServerPlayer sp0 && sp0.isChangingDimension())
				&& (!bird.onGround() || bird.isInWater()
				|| bird.level().getFluidState(bird.blockPosition().below()).is(net.minecraft.tags.FluidTags.WATER))) {
			// sneak is "down" while flying or on water; dismount on solid ground only
			event.setCanceled(true);
			return;
		}
		if (vehicle instanceof ChocoboEntity bird && bird.racing() && bird.isAlive() && !bird.isRemoved()
				&& rider.isAlive() && !rider.isRemoved()
				// a teleport / dimension change dismounts through the same hook: let it
				&& !(rider instanceof ServerPlayer sp && sp.isChangingDimension())
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

	@SubscribeEvent
	public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
		DuelDesk.clear();
		TradeDesk.clear();
		for (RaceSession s : new ArrayList<>(SESSIONS)) {
			if (s.live()) {
				s.abort();
			}
		}
		SESSIONS.clear();
		SquareBuilder.resetPending();
		HeatSchedule.reset();
		testLevel = null;
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		RaceSession s = sessionOf(event.getEntity().getUUID());
		if (s != null) {
			s.abort();
		}
	}
}
