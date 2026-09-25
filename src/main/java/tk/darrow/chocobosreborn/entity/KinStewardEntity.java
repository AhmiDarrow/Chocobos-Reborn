package tk.darrow.chocobosreborn.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import tk.darrow.chocobosreborn.item.ModItems;
import tk.darrow.chocobosreborn.race.DuelDesk;
import tk.darrow.chocobosreborn.race.RaceManager;
import tk.darrow.chocobosreborn.race.RaceTrack;
import tk.darrow.chocobosreborn.race.TradeDesk;
import tk.darrow.chocobosreborn.race.RaceScoring;
import tk.darrow.chocobosreborn.race.RaceSession;
import tk.darrow.chocobosreborn.race.RaceShops;
import tk.darrow.chocobosreborn.race.Square;
import tk.darrow.chocobosreborn.race.TownRole;

/**
 * Chocobo Kin: Esther the Square Steward, the bookie, and the stall
 * keepers (tack, greens, fair, treats). Stall keepers trade for GP through
 * the vanilla merchant screen.
 */
public class KinStewardEntity extends PathfinderMob implements Merchant {
	private static final EntityDataAccessor<Integer> DATA_ROLE =
			SynchedEntityData.defineId(KinStewardEntity.class, EntityDataSerializers.INT);

	@Nullable
	private Player tradingPlayer;
	@Nullable
	private MerchantOffers offers;

	public KinStewardEntity(EntityType<? extends KinStewardEntity> type, Level level) {
		super(type, level);
		this.setCustomName(Component.translatable("chocobosreborn.kin.steward"));
		this.setCustomNameVisible(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 40.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.25D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ROLE, TownRole.STEWARD.ordinal());
	}

	@Override
	protected void registerGoals() {
		// keepers glance at whoever comes up to them and otherwise face their post
		this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
	}

	/** A fan in the stands: follows the riders all the way round the course. */
	public void installFan() {
		this.goalSelector.removeAllGoals(g -> true);
		this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 64.0F, 1.0F));
	}

	/**
	 * In the saddle the kin sits. Vanilla subtracts this point from the vehicle's seat
	 * (a player's is 0.6 up, which is why players sit 0.6 lower than their seat point);
	 * a kin with folded legs needs a little more than a player.
	 */
	@Override
	public net.minecraft.world.phys.Vec3 getVehicleAttachmentPoint(net.minecraft.world.entity.Entity vehicle) {
		// the bird's seat is a player's seat: a kin sits 0.30 further forward and 0.15 to the left of it
		// (subtracted from the seat, in world space, so rotate by the kin's yaw, which follows the bird)
		double rad = Math.toRadians(getYRot());
		double fx = -Math.sin(rad), fz = Math.cos(rad);   // forward
		double rx = -Math.cos(rad), rz = -Math.sin(rad);  // right
		double wantX = fx * 0.30D - rx * 0.15D, wantZ = fz * 0.30D - rz * 0.15D;
		return new net.minecraft.world.phys.Vec3(-wantX, 1.15D, -wantZ);
	}

	/** A jockey on an AI racer: no goals of its own; the racer goal turns it with the bird. */
	public void installJockey() {
		this.goalSelector.removeAllGoals(g -> true);
		this.targetSelector.removeAllGoals(g -> true);
	}

	public TownRole role() {
		return TownRole.byId(this.entityData.get(DATA_ROLE));
	}

	public void setRole(TownRole role) {
		this.entityData.set(DATA_ROLE, role.ordinal());
		this.setCustomName(Component.translatable("chocobosreborn.kin." + role.id()));
		this.offers = null;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_ROLE.equals(key) && getCustomName() == null) {
			setCustomName(Component.translatable("chocobosreborn.kin." + role().id()));
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("Role", role().ordinal());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		setRole(TownRole.byId(tag.getInt("Role")));
	}

	// ----------------------------------------------------------- interaction

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (level().isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.PASS;
		}
		TownRole role = role();
		if (role.shops()) {
			if (tradingPlayer != null) {
				sp.displayClientMessage(Component.translatable("chocobosreborn.shop.busy"), true);
				return InteractionResult.CONSUME;
			}
			setTradingPlayer(sp);
			openTradingScreen(sp, getDisplayName(), 1);
			return InteractionResult.CONSUME;
		}
		if (role == TownRole.BOOKIE) {
			return bookie(sp);
		}
		if (role == TownRole.DUEL) {
			return duel(sp);
		}
		if (role == TownRole.BROKER) {
			TradeDesk.interact(sp, sp.isSecondaryUseActive());
			return InteractionResult.CONSUME;
		}
		if (role.fans()) {
			sp.displayClientMessage(Component.translatable("chocobosreborn.fan.cheer." + random.nextInt(4)), true);
			return InteractionResult.CONSUME;
		}
		if (role.jockey()) {
			return InteractionResult.CONSUME;
		}
		return steward(sp);
	}

	/**
	 * Esther / Farmhand. Overworld: take the player to Whiskerwind (their birds
	 * follow). Square: open the course picker for a ranked heat, or send them home.
	 */
	private InteractionResult steward(ServerPlayer player) {
		boolean inSquare = Square.isSquare(level());
		ChocoboEntity bird = player.getVehicle() instanceof ChocoboEntity b ? b : null;
		if (!inSquare) {
			if (bird != null) {
				RaceManager.enterSquare(player, bird);
			} else {
				RaceManager.enterSquareOnFoot(player);
			}
			return InteractionResult.CONSUME;
		}
		if (bird == null) {
			if (player.isSecondaryUseActive() && tk.darrow.chocobosreborn.race.HeatSchedule.entered(player.getUUID())) {
				// "sneak-click Esther to scratch": sneaking in the saddle dismounts first, so the
				// scratch lands here on foot; it must not also send the rider home
				tk.darrow.chocobosreborn.race.HeatSchedule.drop(player);
				tk.darrow.chocobosreborn.race.RaceManager.refundPendingBet(player);
			} else if (player.isSecondaryUseActive()) {
				RaceManager.leaveSquare(player);
			} else {
				player.displayClientMessage(Component.translatable("chocobosreborn.square.home_hint"), true);
			}
			return InteractionResult.CONSUME;
		}
		if (RaceManager.sessionOf(player.getUUID()) != null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.already"), true);
			return InteractionResult.CONSUME;
		}
		if (!bird.saddled() || bird.isBaby()
				|| (!bird.isOwnedBy(player) && !player.getAbilities().instabuild)) {
			player.displayClientMessage(Component.translatable("chocobosreborn.square.need_bird"), true);
			return InteractionResult.CONSUME;
		}
		if (bird.armor() != null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.no_armor"), true);
			return InteractionResult.CONSUME;
		}
		if (player.isSecondaryUseActive() && tk.darrow.chocobosreborn.race.HeatSchedule.entered(player.getUUID())) {
			tk.darrow.chocobosreborn.race.HeatSchedule.drop(player);
			tk.darrow.chocobosreborn.race.RaceManager.refundPendingBet(player);
			return InteractionResult.CONSUME;
		}
		// a heat of this class already on the timetable: join it; otherwise pick its course
		if (tk.darrow.chocobosreborn.race.HeatSchedule.joinOrPick(player, bird)) {
			return InteractionResult.CONSUME;
		}
		net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
				new tk.darrow.chocobosreborn.net.RacePayloads.OpenCourseSelect(bird.raceClass().getId(), 0));
		return InteractionResult.CONSUME;
	}

	/** Sable, the duel master: accept the oldest open challenge, or post one via the course picker. */
	private InteractionResult duel(ServerPlayer player) {
		if (!(player.getVehicle() instanceof ChocoboEntity bird) || !bird.saddled() || bird.isBaby()) {
			java.util.List<DuelDesk.Challenge> open = DuelDesk.others(player.getUUID());
			if (DuelDesk.mine(player.getUUID()) != null && player.isSecondaryUseActive()) {
				DuelDesk.withdraw(player);
			} else if (open.isEmpty()) {
				player.displayClientMessage(Component.translatable("chocobosreborn.duel.hint"), true);
			} else {
				DuelDesk.Challenge c = open.get(0);
				player.displayClientMessage(Component.translatable("chocobosreborn.duel.open", c.name(),
						Component.translatable("chocobosreborn.track." + c.track().id()), c.stake()), false);
			}
			return InteractionResult.CONSUME;
		}
		if (player.isSecondaryUseActive() && DuelDesk.mine(player.getUUID()) != null) {
			DuelDesk.withdraw(player);
			return InteractionResult.CONSUME;
		}
		if (RaceManager.sessionOf(player.getUUID()) != null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.already"), true);
			return InteractionResult.CONSUME;
		}
		if (bird.armor() != null) {
			player.displayClientMessage(Component.translatable("chocobosreborn.race.no_armor"), true);
			return InteractionResult.CONSUME;
		}
		if (DuelDesk.accept(player, bird)) {
			return InteractionResult.CONSUME;
		}
		net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
				new tk.darrow.chocobosreborn.net.RacePayloads.OpenCourseSelect(bird.raceClass().getId(), 1));
		return InteractionResult.CONSUME;
	}

	/** Bookie: hand over GP with a pick in mind. Empty hand cycles the pick. */
	private InteractionResult bookie(ServerPlayer player) {
		if (player.isSecondaryUseActive() && RaceManager.hasPendingBet(player)) {
			RaceManager.refundPendingBet(player);
			return InteractionResult.CONSUME;
		}
		RaceSession s = RaceManager.sessionForBet(player.getUUID());
		if (s != null && !RaceScoring.booksOpen(true, s.running())) {
			player.displayClientMessage(Component.translatable("chocobosreborn.bet.closed"), true);
			return InteractionResult.CONSUME;
		}
		// No heat yet: the bet waits for the next one Esther starts. Picks are offered
		// for a ranked heat of the bird the player is riding (or the field if on foot).
		boolean ranked = s == null || s.ranked();
		// an entered rider's odds are the heat's class, which may be below their bird's (half purse, lower odds)
		tk.darrow.chocobosreborn.race.RaceClass heatClass = s != null ? s.track().getRaceClass()
				: tk.darrow.chocobosreborn.race.HeatSchedule.enteredClass(player.getUUID());
		boolean teioh = heatClass != null ? heatClass.includesTeioh()
				: player.getVehicle() instanceof ChocoboEntity b && b.raceClass().includesTeioh();
		int classId = heatClass != null ? heatClass.getId()
				: player.getVehicle() instanceof ChocoboEntity b2 ? b2.raceClass().getId() : 0;
		ItemStack hand = player.getMainHandItem();
		if (!hand.is(ModItems.GP.get())) {
			hand = player.getOffhandItem();
		}
		// entered in the next heat: a racer may only back themselves
		boolean racing = s == null && tk.darrow.chocobosreborn.race.HeatSchedule.entered(player.getUUID());
		RaceScoring.BetPick pick = pickFor(player);
		if (s != null) {
			pick = s.legalPick(pick, player.getUUID());
		} else {
			pick = racing ? RaceScoring.BetPick.SELF : RaceScoring.legalize(pick, ranked, teioh);
		}
		setPick(player, pick);
		if (!hand.is(ModItems.GP.get())) {
			pick = s != null ? s.nextPick(pick, player.getUUID())
					: racing ? RaceScoring.BetPick.SELF : RaceScoring.nextPick(pick, ranked, teioh);
			setPick(player, pick);
			int odds = s != null ? s.odds(pick)
					: RaceScoring.odds(pick, classId, RaceScoring.expectedFieldBirds(1, teioh));
			player.displayClientMessage(Component.translatable("chocobosreborn.bet.pick",
					Component.translatable("chocobosreborn.bet." + pick.name().toLowerCase(java.util.Locale.ROOT)),
					odds), true);
			return InteractionResult.CONSUME;
		}
		if (s != null && RaceManager.hasPendingBet(player)) {
			int moved = RaceManager.settlePendingOnto(player, s);
			if (moved > 0) {
				player.displayClientMessage(Component.translatable("chocobosreborn.bet.placed", moved,
						Component.translatable("chocobosreborn.bet." + pick.name().toLowerCase(java.util.Locale.ROOT))), false);
			}
			return InteractionResult.CONSUME;
		}
		int stake = RaceScoring.clampStake(hand.getCount());
		if (RaceManager.placeBet(player, pick, stake)) {
			hand.shrink(stake);
			player.displayClientMessage(Component.translatable("chocobosreborn.bet.placed", stake,
					Component.translatable("chocobosreborn.bet." + pick.name().toLowerCase(java.util.Locale.ROOT))), false);
			if (s == null) {
				player.displayClientMessage(Component.translatable("chocobosreborn.bet.no_heat"), false);
			}
		} else {
			player.displayClientMessage(Component.translatable("chocobosreborn.bet.refused"), true);
		}
		return InteractionResult.CONSUME;
	}

	private static RaceScoring.BetPick pickFor(ServerPlayer player) {
		CompoundTag tag = player.getPersistentData();
		int i = tag.getInt("chocobosreborn_pick");
		RaceScoring.BetPick[] v = RaceScoring.BetPick.values();
		return v[Math.max(0, Math.min(v.length - 1, i))];
	}

	private static void setPick(ServerPlayer player, RaceScoring.BetPick pick) {
		player.getPersistentData().putInt("chocobosreborn_pick", pick.ordinal());
	}

	// --------------------------------------------------------------- merchant

	@Override
	public void setTradingPlayer(@Nullable Player player) {
		this.tradingPlayer = player;
	}

	@Override
	public @Nullable Player getTradingPlayer() {
		return tradingPlayer;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!level().isClientSide && tradingPlayer != null && tickCount % 20 == 0
				&& (tradingPlayer.isRemoved() || tradingPlayer.distanceTo(this) > 8.0F
				|| !(tradingPlayer.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu))) {
			if (tradingPlayer instanceof ServerPlayer sp
					&& sp.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu) {
				sp.closeContainer();
			}
			setTradingPlayer(null);
		}
	}

	@Override
	public MerchantOffers getOffers() {
		if (offers == null) {
			offers = new MerchantOffers();
			Item gp = ModItems.GP.get();
			for (RaceShops.Line line : RaceShops.catalog()) {
				if (!line.role().equals(role().id())) {
					continue;
				}
				Item result = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(line.resultId())).orElse(null);
				if (result == null) {
					continue;
				}
				// GP stacks to 64: anything dearer needs the second cost slot
				int cost = line.cost();
				ItemCost costA = new ItemCost(gp, Math.min(64, cost));
				java.util.Optional<ItemCost> costB = cost > 64 ? java.util.Optional.of(new ItemCost(gp, Math.min(64, cost - 64)))
						: java.util.Optional.empty();
				offers.add(new MerchantOffer(costA, costB, new ItemStack(result, line.resultCount()),
						Integer.MAX_VALUE, 0, 0.0F));
			}
		}
		return offers;
	}

	@Override
	public void overrideOffers(MerchantOffers offers) {
		this.offers = offers;
	}

	@Override
	public void notifyTrade(MerchantOffer offer) {
		offer.increaseUses();
		playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
	}

	@Override
	public void notifyTradeUpdated(ItemStack stack) {
	}

	@Override
	public int getVillagerXp() {
		return 0;
	}

	@Override
	public void overrideXp(int xp) {
	}

	@Override
	public boolean showProgressBar() {
		return false;
	}

	@Override
	public SoundEvent getNotifyTradeSound() {
		return SoundEvents.VILLAGER_YES;
	}

	@Override
	public boolean isClientSide() {
		return level().isClientSide;
	}


	// ------------------------------------------------------------- hardiness

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(source);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	private static final double CHEER_RANGE_SQR = 48.0D * 48.0D;
	private static long cheerBucket = Long.MIN_VALUE;
	private static final List<Vec3> cheerBirds = new ArrayList<>();
	private static AABB cheerBox;

	/**
	 * Fans: is a heat running nearby? One search for the whole grandstand each half
	 * second, then a distance check. The renderer is the only caller, on the client.
	 */
	public boolean cheering() {
		if (!role().fans() || !level().isClientSide) {
			return false;
		}
		long bucket = level().getGameTime() / 10L;
		if (bucket != cheerBucket) {
			cheerBucket = bucket;
			cheerBirds.clear();
			for (ChocoboEntity bird : level().getEntitiesOfClass(ChocoboEntity.class, cheerSearchBox(), ChocoboEntity::racing)) {
				cheerBirds.add(new Vec3(bird.getX(), bird.getY(), bird.getZ()));
			}
		}
		double x = getX(), y = getY(), z = getZ();
		for (int i = 0; i < cheerBirds.size(); i++) {
			Vec3 p = cheerBirds.get(i);
			double dx = p.x - x, dy = p.y - y, dz = p.z - z;
			if (dx * dx + dy * dy + dz * dz <= CHEER_RANGE_SQR) {
				return true;
			}
		}
		return false;
	}

	private static AABB cheerSearchBox() {
		if (cheerBox == null) {
			double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
			double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
			for (RaceTrack t : RaceTrack.values()) {
				minX = Math.min(minX, t.centerX() - t.getRadiusX() - 64.0D);
				maxX = Math.max(maxX, t.centerX() + t.getRadiusX() + 64.0D);
				minZ = Math.min(minZ, t.centerZ() - t.getRadiusZ() - 64.0D);
				maxZ = Math.max(maxZ, t.centerZ() + t.getRadiusZ() + 64.0D);
			}
			cheerBox = new AABB(minX, 0.0D, minZ, maxX, 256.0D, maxZ);
		}
		return cheerBox;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}
}
