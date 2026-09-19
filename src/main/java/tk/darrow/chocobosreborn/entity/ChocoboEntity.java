package tk.darrow.chocobosreborn.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import tk.darrow.chocobosreborn.breed.BreedingOdds;
import tk.darrow.chocobosreborn.breed.BreedRules;
import tk.darrow.chocobosreborn.breed.ChocoboColor;
import tk.darrow.chocobosreborn.breed.ChocoboGreen;
import tk.darrow.chocobosreborn.breed.ChocoboGrade;
import tk.darrow.chocobosreborn.breed.ChocoboNut;
import tk.darrow.chocobosreborn.breed.Ff7Line;
import tk.darrow.chocobosreborn.item.GreensItem;
import tk.darrow.chocobosreborn.item.ModItems;
import tk.darrow.chocobosreborn.item.NutItem;
import tk.darrow.chocobosreborn.item.SaddleItem;
import tk.darrow.chocobosreborn.race.RaceClass;
import tk.darrow.chocobosreborn.race.RaceScoring;
import tk.darrow.chocobosreborn.race.Square;
import tk.darrow.chocobosreborn.sound.ModSounds;

public class ChocoboEntity extends TamableAnimal implements PlayerRideableJumping, net.minecraft.world.entity.HasCustomInventoryScreen, net.minecraft.world.ContainerListener {
	private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_GRADE = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_NUT = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_WINS = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_CLASS = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_CLASS_WINS = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_MALE = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_SADDLED = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.BOOLEAN);
	/** Worn armour tier ordinal, -1 for none (synced for the renderer's mesh choice). */
	private static final EntityDataAccessor<Integer> DATA_ARMOR = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_BAGS = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.BOOLEAN);
	public static final int SLOT_SADDLE = 0, SLOT_ARMOR = 1, SLOT_BAGS = 2, BAG_START = 3, BAG_SIZE = 15;
	private static final net.minecraft.resources.ResourceLocation ARMOR_ID =
			net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chocobosreborn", "armor");
	/** Saddle, armour, saddlebags, then fifteen bag slots. */
	private final net.minecraft.world.SimpleContainer inventory = new net.minecraft.world.SimpleContainer(BAG_START + BAG_SIZE);
	private static final EntityDataAccessor<Integer> DATA_STAMINA = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	/** FF7 training from greens: speed, stamina, intelligence, cooperation (0..100 each). */
	private static final EntityDataAccessor<Integer> DATA_TR_SPEED = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TR_STAMINA = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TR_INTEL = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TR_COOP = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	/** Racing flag for the Square (music + dismount lock). */
	private static final EntityDataAccessor<Boolean> DATA_RACING = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.BOOLEAN);
	/** Ordinal of the course being raced (-1 off the course); the client picks the music loop from it. */
	private static final EntityDataAccessor<Integer> DATA_RACE_TRACK = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	/** Growth stage, decided on the server (client-side {@code getAge()} is not reliable). */
	private static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	/** Square racer owned by the track, not a player. */
	private static final EntityDataAccessor<Boolean> DATA_RACE_NPC = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.BOOLEAN);
	/** Whiskerwind's own birds: scenery that wanders the village and can never be tamed or fed. */
	private static final EntityDataAccessor<Boolean> DATA_TOWN_BIRD = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.BOOLEAN);
	/** Boosting from a pad (synced: the rider's client scales its own input from it). */
	private static final EntityDataAccessor<Boolean> DATA_BOOST = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.BOOLEAN);
	/** {@link Command} ordinal, synced so the equipment screen shows the current order. */
	private static final EntityDataAccessor<Integer> DATA_COMMAND = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);

	/** What a tame bird does when nobody is riding it. Stay is vanilla's ordered-to-sit. */
	public enum Command {
		FOLLOW, STAY, WANDER;

		public static Command byId(int id) {
			return id >= 0 && id < values().length ? values()[id] : FOLLOW;
		}

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** A wandering bird strolls within this many blocks of where it was told to wander. */
	public static final int WANDER_RANGE = 24;

	/** Steve is 1.8 m. Hitbox height of a grown bird (3.25 m to the crest, ~1.8x Steve). */
	public static final float PLAYER_H = 1.8F;
	public static final float ADULT_H = 3.25F;
	/** Saddle seat height: the saddled mesh's seat top is at 1.37 of 2.25 -> 1.98 m at ADULT_H; sit a hair into it. */
	public static final float SEAT_H = 1.92F;
	/** The saddle sits behind the bird's centre (mesh y +0.25 of 2.25 -> 0.36 m at ADULT_H). */
	public static final float SEAT_BACK = 0.36F;

	private static final int CHICK_AGE = -24000;
	/** Feeds per green so far (FF7 greens stop helping after a while). Not synced. */
	private final int[] greensFed = new int[ChocoboGreen.values().length];
	/** Ticks of lift after a rider jump on a flying bird. */
	private int flapTicks;
	/** Ticks before a ridden bird will use a Square gate again. */
	private int gateCooldown;
	/** Rider is holding sneak: dive (fliers) / submerge (water birds). Set in travel(). */
	private boolean descending;
	/** Server-side gate so a sprint dash stops the moment stamina hits zero. */
	private boolean dashing;
	/** Game-day greens satiety last recovered, so unloaded birds still catch up. */
	private long lastGreensDay = Long.MIN_VALUE;
	/** Game time of the last training green. 0 = never trained. */
	private long lastGreensFeed;
	/** Wander mode (kept under Stay, so standing up returns to wandering, not following). */
	private boolean wander;
	/** Centre of a wandering bird's range; null until it is first set down. */
	@Nullable
	private BlockPos wanderHome;

	public ChocoboEntity(EntityType<? extends ChocoboEntity> type, Level level) {
		super(type, level);
		inventory.addListener(this);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 30.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.20D)
				.add(Attributes.STEP_HEIGHT, 1.0D)
				.add(Attributes.FOLLOW_RANGE, 16.0D);
	}

	// ------------------------------------------------------------------ data

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_COLOR, 0);
		builder.define(DATA_GRADE, 1);
		builder.define(DATA_NUT, 0);
		builder.define(DATA_WINS, 0);
		builder.define(DATA_CLASS, 0);
		builder.define(DATA_CLASS_WINS, 0);
		builder.define(DATA_MALE, true);
		builder.define(DATA_SADDLED, false);
		builder.define(DATA_ARMOR, -1);
		builder.define(DATA_BAGS, false);
		builder.define(DATA_STAMINA, 80);
		builder.define(DATA_TR_SPEED, 0);
		builder.define(DATA_TR_STAMINA, 0);
		builder.define(DATA_TR_INTEL, 0);
		builder.define(DATA_TR_COOP, 0);
		builder.define(DATA_RACING, false);
		builder.define(DATA_RACE_TRACK, -1);
		builder.define(DATA_STAGE, 3);
		builder.define(DATA_RACE_NPC, false);
		builder.define(DATA_TOWN_BIRD, false);
		builder.define(DATA_BOOST, false);
		builder.define(DATA_COMMAND, Command.FOLLOW.ordinal());
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new ChocoFloatGoal());
		this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
		this.goalSelector.addGoal(2, new PanicGoal(this, 1.4D) {
			@Override
			public boolean canUse() {
				return !Square.isSquare(level()) && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !Square.isSquare(level()) && super.canContinueToUse();
			}
		});
		this.goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.BreedGoal(this, 1.0D));
		this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.2D, 8.0F, 3.0F) {
			@Override
			public boolean canUse() {
				return !wander && !Square.isSquare(level()) && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !wander && !Square.isSquare(level()) && super.canContinueToUse();
			}
		});
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.1D,
				Ingredient.of(ModItems.GYSAHL.get(), ModItems.CHOCOBO_LURE.get()), false) {
			@Override
			public boolean canUse() {
				return !Square.isSquare(level()) && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !Square.isSquare(level()) && super.canContinueToUse();
			}
		});
		this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.9D) {
			@Override
			public boolean canUse() {
				return !color().waterWalk() && (townBird() || !Square.isSquare(level())) && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !color().waterWalk() && (townBird() || !Square.isSquare(level())) && super.canContinueToUse();
			}
		});
		this.goalSelector.addGoal(4, new net.minecraft.world.entity.ai.goal.RandomStrollGoal(this, 0.9D) {
			@Override
			public boolean canUse() {
				return color().waterWalk() && (townBird() || !Square.isSquare(level())) && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return color().waterWalk() && (townBird() || !Square.isSquare(level())) && super.canContinueToUse();
			}
		});
		this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
	}

	public ChocoboColor color() {
		return ChocoboColor.byId(this.entityData.get(DATA_COLOR));
	}

	public void setColor(ChocoboColor color) {
		this.entityData.set(DATA_COLOR, color.getId());
		applyColorStats(true);
	}

	/** Born grade plus one step per 120 training points (SPEC: greens lift the effective grade). */
	public ChocoboGrade grade() {
		int total = trainedSpeed() + trainedStamina() + trainedIntelligence() + trainedCooperation();
		return ChocoboGrade.byRank(ChocoboGreen.gradeFromTraining(this.entityData.get(DATA_GRADE), total));
	}

	/** Grade as rolled at spawn / hatch, without training. */
	public ChocoboGrade bornGrade() {
		return ChocoboGrade.byRank(this.entityData.get(DATA_GRADE));
	}

	public void setGrade(ChocoboGrade grade) {
		this.entityData.set(DATA_GRADE, grade.getRank());
	}

	public ChocoboNut fedNut() {
		return ChocoboNut.byId(this.entityData.get(DATA_NUT));
	}

	public int raceWins() {
		return this.entityData.get(DATA_WINS);
	}

	public RaceClass raceClass() {
		return RaceClass.byId(this.entityData.get(DATA_CLASS));
	}

	public int classWins() {
		return this.entityData.get(DATA_CLASS_WINS);
	}

	public boolean saddled() {
		return this.entityData.get(DATA_SADDLED);
	}

	public int trainedSpeed() {
		return this.entityData.get(DATA_TR_SPEED);
	}

	public int trainedStamina() {
		return this.entityData.get(DATA_TR_STAMINA);
	}

	public int trainedIntelligence() {
		return this.entityData.get(DATA_TR_INTEL);
	}

	public int trainedCooperation() {
		return this.entityData.get(DATA_TR_COOP);
	}

	public void addTraining(int speed, int stamina, int intelligence, int cooperation) {
		this.entityData.set(DATA_TR_SPEED, Math.min(ChocoboGreen.MAX_POINTS, trainedSpeed() + speed));
		this.entityData.set(DATA_TR_STAMINA, Math.min(ChocoboGreen.MAX_POINTS, trainedStamina() + stamina));
		this.entityData.set(DATA_TR_INTEL, Math.min(ChocoboGreen.MAX_POINTS, trainedIntelligence() + intelligence));
		this.entityData.set(DATA_TR_COOP, Math.min(ChocoboGreen.MAX_POINTS, trainedCooperation() + cooperation));
	}

	public boolean racing() {
		return this.entityData.get(DATA_RACING);
	}

	public Command command() {
		return Command.byId(this.entityData.get(DATA_COMMAND));
	}

	/** Owner's order from the equipment screen or a plain right-click: say it, and kweh it. */
	public void giveCommand(Command command, @Nullable Player player) {
		if (command != Command.STAY) {
			boolean wasWander = wander;
			wander = command == Command.WANDER;
			if (wander && !wasWander) {
				wanderHome = blockPosition();
			} else if (!wander && wasWander) {
				clearWanderRange();
			}
		}
		setOrderedToSit(command == Command.STAY);
		if (!level().isClientSide) {
			playSound(switch (command) {
				case FOLLOW -> ModSounds.KWEH_FOLLOW.get();
				case STAY -> ModSounds.KWEH_STAY.get();
				case WANDER -> ModSounds.KWEH_WANDER.get();
			}, 0.8F, 1.0F);
			if (player != null) {
				player.displayClientMessage(Component.translatable("chocobosreborn.command." + command.id() + ".told", getDisplayName()), true);
			}
		}
	}

	@Override
	public void setOrderedToSit(boolean sit) {
		super.setOrderedToSit(sit);
		syncCommand();
	}

	private void syncCommand() {
		Command c = isOrderedToSit() ? Command.STAY : wander ? Command.WANDER : Command.FOLLOW;
		this.entityData.set(DATA_COMMAND, c.ordinal());
	}

	/** Drop the wander range. By radius, not centre: the spot may already be forgotten mid-ride. */
	private void clearWanderRange() {
		if (hasRestriction() && getRestrictRadius() == WANDER_RANGE) {
			clearRestriction();
		}
		wanderHome = null;
	}

	/**
	 * Keep a wandering bird near its spot. Leading or riding it somewhere moves the
	 * spot (as does a far jump, e.g. a teleport), so it wanders wherever it was left.
	 */
	private void tickWanderRange() {
		if (wander && !isTame()) {
			// released to the wild (almanac): no more orders, no range
			wander = false;
			clearWanderRange();
			syncCommand();
		}
		if (!wander || Square.isSquare(level())) {
			return;
		}
		if (isLeashed() || isVehicle() || isPassenger()) {
			wanderHome = null;
			return;
		}
		if (wanderHome == null || !wanderHome.closerToCenterThan(position(), WANDER_RANGE * 3)) {
			wanderHome = blockPosition();
		}
		if (!hasRestriction() || !getRestrictCenter().equals(wanderHome) || getRestrictRadius() != WANDER_RANGE) {
			restrictTo(wanderHome, WANDER_RANGE);
		}
	}

	/** Almanac release: a nut in the bird must not finish a pairing after it goes wild. */
	public void clearNut() {
		this.entityData.set(DATA_NUT, 0);
	}

	public int raceTrack() {
		return this.entityData.get(DATA_RACE_TRACK);
	}

	public void setRaceTrack(int ordinal) {
		this.entityData.set(DATA_RACE_TRACK, ordinal);
	}

	public void setRacing(boolean racing) {
		this.entityData.set(DATA_RACING, racing);
		if (racing) {
			dropSquareLeash();
		}
		// keep raceTrack after the line so the course loop can finish before the sting
	}

	/** LeadItem runs before mobInteract; Square birds and live racers must not walk off on a lead. */
	private void dropSquareLeash() {
		if (isLeashed()) {
			dropLeash(true, true);
		}
	}

	@Override
	public boolean canBeLeashed() {
		return !squareProtected() && super.canBeLeashed();
	}

	@Override
	public boolean isPushable() {
		return !squareProtected() && super.isPushable();
	}

	/** Town / NPC / live heat, including the finish-grace while the bird still carries a course. */
	private boolean squareProtected() {
		return RaceScoring.squareNpcProtected(townBird(), raceNpc(), racing(),
				raceTrack() >= 0 && Square.isSquare(level()));
	}

	/** Speed training: +0.35% top speed per point, +35% at 100. */
	public static final double SPEED_PER_POINT = RaceScoring.SPEED_PER_POINT;

	public double speedMul() {
		return RaceScoring.gradeSpeedMul(grade().getRank()) * RaceScoring.speedTrainingMul(trainedSpeed());
	}

	public boolean male() {
		return this.entityData.get(DATA_MALE);
	}

	public void setMale(boolean male) {
		this.entityData.set(DATA_MALE, male);
	}

	public int stamina() {
		return this.entityData.get(DATA_STAMINA);
	}

	public int maxStamina() {
		return RaceScoring.maxStamina(grade().getRank(), raceClass().getId(), false) + trainedStamina();
	}

	public void setStamina(int value) {
		this.entityData.set(DATA_STAMINA, Mth.clamp(value, 0, maxStamina()));
	}

	public void fillStamina() {
		setStamina(maxStamina());
	}

	public void setRaceClass(RaceClass raceClass) {
		this.entityData.set(DATA_CLASS, raceClass.getId());
	}

	public boolean townBird() {
		return this.entityData.get(DATA_TOWN_BIRD);
	}

	public void setTownBird(boolean town) {
		this.entityData.set(DATA_TOWN_BIRD, town);
		if (town) {
			dropSquareLeash();
		}
	}

	public boolean raceNpc() {
		return this.entityData.get(DATA_RACE_NPC);
	}

	public void setRaceNpc(boolean npc) {
		this.entityData.set(DATA_RACE_NPC, npc);
		if (npc) {
			dropSquareLeash();
		}
	}

	/**
	 * First-place finish at Chocobo Square. Ranked wins count for the farm line
	 * and promote the class after three (never demoted).
	 */
	public void recordFirstPlace(boolean ranked) {
		if (!ranked) {
			return;
		}
		this.entityData.set(DATA_WINS, raceWins() + 1);
		RaceScoring.Promotion p = RaceScoring.afterFirstPlace(raceClass(), classWins());
		this.entityData.set(DATA_CLASS, p.raceClass().getId());
		this.entityData.set(DATA_CLASS_WINS, p.classWins());
	}

	/** 0 chicobo (25% player), 1 (50% player), 2 (75% player), 3 adult. */
	public int growthStage() {
		return this.entityData.get(DATA_STAGE);
	}

	/** Spawn-egg chicks: age and hitbox before collision / addFreshEntity. */
	public void markChick() {
		setAge(-24000);
		this.entityData.set(DATA_STAGE, 0);
		refreshDimensions();
	}

	private int computeStage() {
		if (!isBaby()) {
			return 3;
		}
		int age = getAge();
		if (age < -16000) {
			return 0;
		}
		if (age < -8000) {
			return 1;
		}
		return 2;
	}

	@Override
	public float getAgeScale() {
		return switch (growthStage()) {
			case 0 -> (PLAYER_H * 0.25F) / ADULT_H;
			case 1 -> (PLAYER_H * 0.50F) / ADULT_H;
			case 2 -> (PLAYER_H * 0.75F) / ADULT_H;
			default -> 1.0F;
		};
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_STAGE.equals(key)) {
			refreshDimensions();
		}
	}

	private void applyColorStats(boolean heal) {
		ChocoboColor c = color();
		var health = getAttribute(Attributes.MAX_HEALTH);
		var speed = getAttribute(Attributes.MOVEMENT_SPEED);
		var step = getAttribute(Attributes.STEP_HEIGHT);
		if (health != null) {
			health.setBaseValue(c.maxHealth());
		}
		if (speed != null) {
			speed.setBaseValue(c.landSpeed());
		}
		if (step != null) {
			step.setBaseValue(c.stepHeight());
		}
		if (heal) {
			this.setHealth((float) c.maxHealth());
		} else {
			this.setHealth(Math.min(getHealth(), (float) c.maxHealth()));
		}
	}

	// ----------------------------------------------------------------- spawn

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
	                                    MobSpawnType reason, @Nullable SpawnGroupData data) {
		if (reason != MobSpawnType.SPAWN_EGG && reason != MobSpawnType.BREEDING) {
			boolean nether = level.getLevel().dimension() == Level.NETHER;
			boolean end = level.getLevel().dimension() == Level.END;
			setColor(nether ? ChocoboColor.FLAME : end ? ChocoboColor.PURPLE : ChocoboColor.YELLOW);
			Biome biome = level.getBiome(blockPosition()).value();
			net.minecraft.world.level.block.state.BlockState pad = level.getBlockState(blockPosition().below());
			boolean cold = biome.coldEnoughToSnow(blockPosition())
					&& (pad.is(Blocks.SNOW) || pad.is(Blocks.SNOW_BLOCK) || pad.is(Blocks.ICE) || pad.is(Blocks.PACKED_ICE));
			setGrade(WildGrade.roll(cold, random.nextInt(100)));
		}
		setMale(random.nextBoolean());
		this.entityData.set(DATA_STAGE, computeStage());
		applyColorStats(true);
		return super.finalizeSpawn(level, difficulty, reason, data);
	}

	/** Wild grade bands. Cold pads (snow / ice) are the only place Wonderful appears. */
	public static final class WildGrade {
		private WildGrade() {
		}

		public static ChocoboGrade roll(boolean cold, int roll) {
			if (cold) {
				if (roll < 12) {
					return ChocoboGrade.WONDERFUL;
				}
				if (roll < 22) {
					return ChocoboGrade.GREAT;
				}
				if (roll < 45) {
					return ChocoboGrade.GOOD;
				}
				if (roll < 78) {
					return ChocoboGrade.AVERAGE;
				}
				return ChocoboGrade.POOR;
			}
			if (roll < 8) {
				return ChocoboGrade.GREAT;
			}
			if (roll < 30) {
				return ChocoboGrade.GOOD;
			}
			if (roll < 70) {
				return ChocoboGrade.AVERAGE;
			}
			return ChocoboGrade.POOR;
		}
	}

	public static boolean checkSpawn(EntityType<ChocoboEntity> type, LevelAccessor level, MobSpawnType spawn,
	                                 BlockPos pos, net.minecraft.util.RandomSource random) {
		BlockPos below = pos.below();
		if (level instanceof ServerLevelAccessor sla && tk.darrow.chocobosreborn.race.Square.isSquare(sla.getLevel())) {
			return false;   // the racetrack is not a wild pad
		}
		if (level instanceof ServerLevelAccessor sla && sla.getLevel().dimension() == Level.NETHER) {
			// Flame chocobos: Nether-native Spark tribe. Any solid non-fire floor.
			return level.getBlockState(below).isSolid()
					&& !level.getBlockState(below).is(Blocks.MAGMA_BLOCK)
					&& level.getBlockState(pos).isAir();
		}
		if (level instanceof ServerLevelAccessor sla && sla.getLevel().dimension() == Level.END) {
			// Purple chocobos: the End's outer islands, on end stone.
			return level.getBlockState(below).is(Blocks.END_STONE) && level.getBlockState(pos).isAir();
		}
		boolean animals = level.getBlockState(below).is(BlockTags.ANIMALS_SPAWNABLE_ON);
		boolean snow = level.getBlockState(below).is(Blocks.SNOW) || level.getBlockState(below).is(Blocks.SNOW_BLOCK)
				|| level.getBlockState(below).is(Blocks.ICE) || level.getBlockState(below).is(Blocks.PACKED_ICE);
		boolean cold = level.getBiome(pos).value().coldEnoughToSnow(pos);
		boolean bright = level.getRawBrightness(pos, 0) > 8;
		return tk.darrow.chocobosreborn.breed.WildSpawn.overworldGround(animals, snow, cold, bright);
	}

	// ------------------------------------------------------------------- nbt

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		// Pending almanac-release must land before vanilla writes OwnerUUID / Tame,
		// or the chunk save keeps a tamed bird and clears the queue.
		if (level() instanceof ServerLevel sl) {
			tk.darrow.chocobosreborn.ledger.ChocoboLedger.get(sl).applyPendingRelease(this);
		}
		super.addAdditionalSaveData(tag);
		ledgerUpdate();
		tag.putInt("Plumage", color().getId());
		tag.putInt("Grade", bornGrade().getRank());
		tag.putInt("Nut", fedNut().ordinal());
		tag.putInt("RaceWins", raceWins());
		tag.putInt("RaceClass", raceClass().getId());
		tag.putInt("ClassWins", classWins());
		tag.putBoolean("Male", male());
		tag.putBoolean("Saddled", saddled());
		net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack st = inventory.getItem(i);
			if (!st.isEmpty()) {
				CompoundTag it = new CompoundTag();
				it.putByte("Slot", (byte) i);
				items.add(st.save(registryAccess(), it));
			}
		}
		tag.put("Equipment", items);
		tag.putInt("Stamina", stamina());
		tag.putInt("TrSpeed", trainedSpeed());
		tag.putInt("TrStamina", trainedStamina());
		tag.putInt("TrIntel", trainedIntelligence());
		tag.putInt("TrCoop", trainedCooperation());
		tag.putIntArray("GreensFed", greensFed.clone());
		tag.putBoolean("RaceNpc", raceNpc());
		tag.putBoolean("TownBird", townBird());
		tag.putBoolean("Wander", wander);
		if (wanderHome != null) {
			tag.putLong("WanderHome", wanderHome.asLong());
		}
		if (lastGreensDay != Long.MIN_VALUE) {
			tag.putLong("LastGreensDay", lastGreensDay);
		}
		if (lastGreensFeed > 0L) {
			tag.putLong("LastGreensFeed", lastGreensFeed);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.entityData.set(DATA_COLOR, ChocoboColor.byId(tag.getInt("Plumage")).getId());
		setGrade(ChocoboGrade.byRank(tag.getInt("Grade")));
		this.entityData.set(DATA_NUT, tag.getInt("Nut"));
		this.entityData.set(DATA_WINS, tag.getInt("RaceWins"));
		this.entityData.set(DATA_CLASS, tag.getInt("RaceClass"));
		this.entityData.set(DATA_CLASS_WINS, tag.getInt("ClassWins"));
		this.entityData.set(DATA_MALE, tag.getBoolean("Male"));
		this.entityData.set(DATA_SADDLED, tag.getBoolean("Saddled"));
		inventory.clearContent();
		for (net.minecraft.nbt.Tag t : tag.getList("Equipment", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
			CompoundTag it = (CompoundTag) t;
			int slot = it.getByte("Slot") & 255;
			if (slot < inventory.getContainerSize()) {
				inventory.setItem(slot, ItemStack.parse(registryAccess(), it).orElse(ItemStack.EMPTY));
			}
		}
		// older birds saved only the flag: give them a saddle in the slot
		if (tag.getBoolean("Saddled") && inventory.getItem(SLOT_SADDLE).isEmpty()) {
			inventory.setItem(SLOT_SADDLE, new ItemStack(ModItems.SADDLE.get()));
		}
		syncEquipment();
		if (tag.contains("Stamina")) {
			this.entityData.set(DATA_STAMINA, tag.getInt("Stamina"));
		}
		this.entityData.set(DATA_TR_SPEED, tag.getInt("TrSpeed"));
		this.entityData.set(DATA_TR_STAMINA, tag.getInt("TrStamina"));
		this.entityData.set(DATA_TR_INTEL, tag.getInt("TrIntel"));
		this.entityData.set(DATA_TR_COOP, tag.getInt("TrCoop"));
		int[] fed = tag.getIntArray("GreensFed");
		for (int i = 0; i < Math.min(fed.length, greensFed.length); i++) {
			greensFed[i] = fed[i];
		}
		this.entityData.set(DATA_RACE_NPC, tag.getBoolean("RaceNpc"));
		this.entityData.set(DATA_TOWN_BIRD, tag.getBoolean("TownBird"));
		wander = tag.getBoolean("Wander");
		wanderHome = tag.contains("WanderHome") ? BlockPos.of(tag.getLong("WanderHome")) : null;
		// vanilla reads "Sitting" into its field directly, past setOrderedToSit
		syncCommand();
		if (tag.contains("LastGreensDay")) {
			lastGreensDay = tag.getLong("LastGreensDay");
		}
		if (tag.contains("LastGreensFeed")) {
			lastGreensFeed = tag.getLong("LastGreensFeed");
		}
		this.entityData.set(DATA_STAGE, computeStage());
		// Attributes follow the plumage; health is whatever was saved.
		applyColorStats(false);
	}

	@Override
	public void onAddedToLevel() {
		super.onAddedToLevel();
		if (level() instanceof ServerLevel sl) {
			tk.darrow.chocobosreborn.ledger.ChocoboLedger.get(sl).applyPendingRelease(this);
			ledgerUpdate();
			if (townBird() && Square.isSquare(level())) {
				restrictTo(new net.minecraft.core.BlockPos(0, tk.darrow.chocobosreborn.race.SquareBuilder.GROUND_Y, -72), 30);
			}
		}
	}

	// ------------------------------------------------------------ interaction

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (raceNpc()) {
			return InteractionResult.sidedSuccess(level().isClientSide);
		}
		if (townBird()) {
			if (!level().isClientSide) {
				player.displayClientMessage(Component.translatable("chocobosreborn.square.town_bird"), true);
			}
			return InteractionResult.sidedSuccess(level().isClientSide);
		}
		if (racing()) {
			return InteractionResult.sidedSuccess(level().isClientSide);
		}
		if (stack.is(ModItems.GYSAHL.get())) {
			if (!isTame()) {
				if (!level().isClientSide && random.nextFloat() < 0.33F) {
					tame(player);
					heal(5.0F);
					ledgerUpdate();
					setOrderedToSit(true);
					level().broadcastEntityEvent(this, (byte) 7);
				} else if (!level().isClientSide) {
					level().broadcastEntityEvent(this, (byte) 6);
				}
				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}
				return InteractionResult.sidedSuccess(level().isClientSide);
			}
			if (isOwnedBy(player)) {
				return feedGreen(player, stack, ChocoboGreen.GYSAHL);
			}
			if (getHealth() < getMaxHealth()) {
				if (!level().isClientSide) {
					heal(5.0F);
				}
				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}
				return InteractionResult.sidedSuccess(level().isClientSide);
			}
			// unowned, full health: consume the click so the edible green is not eaten
			return InteractionResult.sidedSuccess(level().isClientSide);
		}
		if (isTame() && isOwnedBy(player)) {
			if (stack.getItem() instanceof NutItem nutItem && !isBaby() && !racing()) {
				// FF7: a nut is what mates two adults.
				// Vanilla canFallInLove is false while inLove, so sneak-replace must run first.
				if (isInLove() || fedNut() != ChocoboNut.NONE) {
					if (!player.isSecondaryUseActive()) {
						return InteractionResult.sidedSuccess(level().isClientSide);
					}
					if (level().isClientSide) {
						return InteractionResult.SUCCESS;
					}
					this.entityData.set(DATA_NUT, nutItem.nut().ordinal());
					setInLove(player);
					setOrderedToSit(false);
					if (!player.getAbilities().instabuild) {
						stack.shrink(1);
					}
					return InteractionResult.CONSUME;
				}
				if (!canFallInLove()) {
					return InteractionResult.sidedSuccess(level().isClientSide);
				}
				if (!level().isClientSide) {
					this.entityData.set(DATA_NUT, nutItem.nut().ordinal());
					if (canFallInLove()) {
						setInLove(player);
					}
					setOrderedToSit(false);
					ChocoboNut fed = nutItem.nut();
					if (fed == ChocoboNut.CAROB || fed == ChocoboNut.ZEIO) {
						int need = minWinsEach(color(), color(), fed);
						if (fed == ChocoboNut.CAROB && (color() == ChocoboColor.GREEN || color() == ChocoboColor.BLUE)) {
							need = 2;
						}
						if (raceWins() < need) {
							player.displayClientMessage(Component.translatable("chocobosreborn.nut.needs_wins",
									Component.translatable("chocobosreborn.nut." + fed.id()), need, raceWins()), true);
						}
					}
					if (!player.getAbilities().instabuild) {
						stack.shrink(1);
					}
				}
				return InteractionResult.sidedSuccess(level().isClientSide);
			}
			if (stack.getItem() instanceof GreensItem greens && greens.green() != ChocoboGreen.GYSAHL) {
				return feedGreen(player, stack, greens.green());
			}
			int equipSlot = stack.getItem() instanceof SaddleItem ? SLOT_SADDLE
					: stack.getItem() instanceof tk.darrow.chocobosreborn.item.ChocoboArmorItem ? SLOT_ARMOR
					: stack.getItem() instanceof tk.darrow.chocobosreborn.item.SaddlebagsItem ? SLOT_BAGS : -1;
			if (equipSlot >= 0 && !isBaby()) {
				if (!inventory.getItem(equipSlot).isEmpty()) {
					if (!level().isClientSide) {
						player.displayClientMessage(Component.translatable("chocobosreborn.equip.occupied"), true);
					}
					return InteractionResult.sidedSuccess(level().isClientSide);
				}
				if (level().isClientSide) {
					return InteractionResult.SUCCESS;
				}
				inventory.setItem(equipSlot, stack.copyWithCount(1));
				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}
				return InteractionResult.sidedSuccess(level().isClientSide);
			}
			if (player.isSecondaryUseActive() && stack.isEmpty() && !isBaby()) {
				// Sneak + empty hand: the equipment screen (saddle, armour, saddlebags)
				if (!level().isClientSide) {
					openCustomInventoryScreen(player);
				}
				return InteractionResult.sidedSuccess(level().isClientSide);
			}
			if (saddled() && !player.isSecondaryUseActive() && !isBaby() && !racing()) {
				setOrderedToSit(false);
				if (!level().isClientSide) {
					player.startRiding(this);
				}
				return InteractionResult.sidedSuccess(level().isClientSide);
			}
			if (!player.isSecondaryUseActive() && !racing()) {
				if (!level().isClientSide) {
					giveCommand(isOrderedToSit() ? (wander ? Command.WANDER : Command.FOLLOW) : Command.STAY, player);
				}
				return InteractionResult.sidedSuccess(level().isClientSide);
			}
		}
		return super.mobInteract(player, hand);
	}

	/** FF7 training: each green adds its stat points until the bird is sated on it. */
	private InteractionResult feedGreen(Player player, ItemStack stack, ChocoboGreen green) {
		int idx = green.ordinal();
		if (greensFed[idx] >= green.satiety()) {
			if (!level().isClientSide) {
				player.displayClientMessage(Component.translatable("chocobosreborn.greens.sated"), true);
			}
			return InteractionResult.sidedSuccess(level().isClientSide);
		}
		boolean instabuild = player.getAbilities().instabuild;
		if (!ChocoboGreen.trainReady(lastGreensFeed, level().getGameTime(), instabuild)) {
			if (!level().isClientSide) {
				int wait = ChocoboGreen.trainWaitTicks(lastGreensFeed, level().getGameTime());
				player.displayClientMessage(Component.translatable("chocobosreborn.greens.wait",
						ChocoboGreen.trainWaitClock(wait)), true);
			}
			return InteractionResult.sidedSuccess(level().isClientSide);
		}
		if (!level().isClientSide) {
			greensFed[idx]++;
			lastGreensFeed = level().getGameTime();
			addTraining(green.speed(), green.stamina(), green.intelligence(), green.cooperation());
			this.entityData.set(DATA_STAMINA, Math.min(maxStamina(), stamina() + 20));
			if (getHealth() < getMaxHealth()) {
				heal(3.0F);
			}
			if (isBaby()) {
				// greens are food: a fed chick grows faster, like vanilla animals
				ageUp(getSpeedUpSecondsWhenFeeding(-getAge()), true);
			}
			level().broadcastEntityEvent(this, (byte) 18);
			playSound(ModSounds.KWEH.get(), 0.6F, 1.2F);
			player.displayClientMessage(Component.translatable("chocobosreborn.greens.fed",
					Component.translatable("item.chocobosreborn." + green.id() + "_green"),
					trainedSpeed(), trainedStamina(), trainedIntelligence(), trainedCooperation(),
					green.satiety() - greensFed[idx]), true);
			if (!instabuild) {
				stack.shrink(1);
			}
		}
		return InteractionResult.sidedSuccess(level().isClientSide);
	}

	@Override
	public boolean isFood(ItemStack stack) {
		// Mating is by nut through the owner (FF7); vanilla food-breeding is off so
		// a wild bird does not go into love from a stray green.
		return false;
	}

	// ------------------------------------------------------------- equipment

	public net.minecraft.world.SimpleContainer inventory() {
		return inventory;
	}

	/** Client-side previews (the almanac) set the synced flag directly; the container sync is server-only. */
	public void setSaddledForPreview(boolean saddled) {
		this.entityData.set(DATA_SADDLED, saddled);
	}

	public boolean hasSaddlebags() {
		return this.entityData.get(DATA_BAGS);
	}

	/** Worn armour tier, or null. */
	@Nullable
	public tk.darrow.chocobosreborn.item.ChocoboArmorItem.Tier armor() {
		int id = this.entityData.get(DATA_ARMOR);
		return id < 0 ? null : tk.darrow.chocobosreborn.item.ChocoboArmorItem.Tier.byId(id);
	}

	/** Saddle flag, armour tier and its attribute, bags flag: all from the container. */
	private void syncEquipment() {
		this.entityData.set(DATA_SADDLED, inventory.getItem(SLOT_SADDLE).getItem() instanceof SaddleItem);
		ItemStack armor = inventory.getItem(SLOT_ARMOR);
		tk.darrow.chocobosreborn.item.ChocoboArmorItem.Tier tier =
				armor.getItem() instanceof tk.darrow.chocobosreborn.item.ChocoboArmorItem a ? a.tier() : null;
		this.entityData.set(DATA_ARMOR, tier == null ? -1 : tier.ordinal());
		this.entityData.set(DATA_BAGS, inventory.getItem(SLOT_BAGS).getItem() instanceof tk.darrow.chocobosreborn.item.SaddlebagsItem);
		var inst = getAttribute(Attributes.ARMOR);
		if (inst != null) {
			inst.removeModifier(ARMOR_ID);
			if (tier != null) {
				inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(ARMOR_ID, tier.armor(),
						net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
			}
		}
	}

	@Override
	public void containerChanged(net.minecraft.world.Container container) {
		if (!level().isClientSide) {
			syncEquipment();
		}
	}

	@Override
	public void openCustomInventoryScreen(Player player) {
		// the riding path (inventory key) skips mobInteract's owner check
		if (player instanceof net.minecraft.server.level.ServerPlayer sp && !isBaby() && !racing()
				&& (isOwnedBy(player) || player.getAbilities().instabuild)) {
			sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
					(id, inv, p) -> new tk.darrow.chocobosreborn.menu.ChocoboInventoryMenu(id, inv, this), getDisplayName()),
					buf -> buf.writeVarInt(getId()));
		}
	}

	@Override
	public void dropEquipment() {
		super.dropEquipment();
		if (!level().isClientSide) {
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack st = inventory.getItem(i);
				if (!st.isEmpty()) {
					spawnAtLocation(st);
					inventory.setItem(i, ItemStack.EMPTY);
				}
			}
		}
	}

	// -------------------------------------------------------------- breeding

	@Override
	public boolean canMate(Animal other) {
		if (!(other instanceof ChocoboEntity mate) || mate == this) {
			return false;
		}
		if (raceNpc() || mate.raceNpc() || racing() || mate.racing()
				|| squareProtected() || mate.squareProtected()) {
			return false;
		}
		return isInLove() && mate.isInLove() && male() != mate.male()
				&& fedNut() != ChocoboNut.NONE && mate.fedNut() != ChocoboNut.NONE;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
		// Vanilla spawn eggs call this with mate == this; that is not a farm pairing.
		if (!(other instanceof ChocoboEntity mate) || mate == this) {
			return null;
		}
		ChocoboEntity chick = ModEntities.CHOCOBO.get().create(level);
		if (chick == null) {
			return null;
		}
		ChocoboNut nut = ChocoboNut.stronger(fedNut(), mate.fedNut());
		int wins = raceWins() + mate.raceWins();
		int guarantee = guaranteeWins(color(), mate.color(), nut);
		int minEach = minWinsEach(color(), mate.color(), nut);
		boolean hit = random.nextDouble() < BreedingOdds.chance(raceWins(), mate.raceWins(), minEach, guarantee);
		ChocoboColor inherit = random.nextBoolean() ? color() : mate.color();
		ChocoboColor child = BreedRules.resolve(color(), mate.color(), grade(), mate.grade(), nut, wins, hit,
				random.nextBoolean(), inherit);
		chick.setColor(child);
		int rank = (bornGrade().getRank() + mate.bornGrade().getRank()) / 2;
		if (nut == ChocoboNut.ZEIO) {
			rank += 1;
		}
		chick.setGrade(ChocoboGrade.byRank(rank));
		// Nut talent: the chick starts with training points from the nut tier and
		// a share of what its parents were fed.
		int talent = nut.getTier() * 4;
		chick.addTraining(talent + (trainedSpeed() + mate.trainedSpeed()) / 6,
				talent + (trainedStamina() + mate.trainedStamina()) / 6,
				talent / 2 + (trainedIntelligence() + mate.trainedIntelligence()) / 6,
				talent / 2 + (trainedCooperation() + mate.trainedCooperation()) / 6);
		chick.setMale(random.nextBoolean());
		chick.setAge(CHICK_AGE);
		chick.entityData.set(DATA_STAGE, 0);
		java.util.UUID owner = getOwnerUUID() != null ? getOwnerUUID() : mate.getOwnerUUID();
		if (owner != null) {
			chick.setOwnerUUID(owner);
			chick.setTame(true, false);
		}
		this.entityData.set(DATA_NUT, 0);
		mate.entityData.set(DATA_NUT, 0);
		tk.darrow.chocobosreborn.ledger.ChocoboLedger.get(level).hatched(chick, this, mate, nut.ordinal(), level.getGameTime());
		return chick;
	}

	private void ledgerUpdate() {
		if (level() instanceof ServerLevel sl && isTame() && !raceNpc()) {
			tk.darrow.chocobosreborn.ledger.ChocoboLedger.get(sl).update(this);
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel sl && isTame()) {
			tk.darrow.chocobosreborn.ledger.ChocoboLedger.get(sl).died(getUUID());
		}
	}

	/**
	 * Installed by the client mod: true when the local player is driving this bird
	 * and not sneaking. Then the bird is not pickable, so the rider's crosshair
	 * passes through it to blocks and other entities (a 3 m bird otherwise eats
	 * every downward click). Sneak to target the bird itself (saddle off, feeding).
	 */
	@Nullable
	public static java.util.function.Predicate<ChocoboEntity> LOCAL_RIDER;

	@Override
	public boolean isPickable() {
		return super.isPickable() && !(LOCAL_RIDER != null && LOCAL_RIDER.test(this));
	}

	@Override
	public boolean canBeHitByProjectile() {
		// rider shots spawn inside this 3 m box; they must pass through
		return super.canBeHitByProjectile() && !(getControllingPassenger() instanceof Player)
				&& !squareProtected();
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.isInvulnerableTo(source);
		}
		if (squareProtected()) {
			return true;
		}
		return super.isInvulnerableTo(source);
	}

	/** Fighting from the saddle: a rider's own swings, arrows and sweeps never land on the bird. */
	@Override
	public boolean hurt(DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		Entity direct = source.getDirectEntity();
		if ((attacker != null && hasPassenger(attacker)) || (direct != null && hasPassenger(direct))) {
			return false;
		}
		return super.hurt(source, amount);
	}

	// ------------------------------------------------------- course effects

	private static final net.minecraft.resources.ResourceLocation BOOST_ID = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chocobosreborn", "boost");
	private static final net.minecraft.resources.ResourceLocation BOG_ID = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chocobosreborn", "bog");
	private static final int BOOST_TICKS = 50;
	/** Speed added while boosting (+55%) and taken while bogged (-55%). */
	private static final double BOOST_POWER = 0.55D, BOG_DRAG = -0.55D;
	private int boostTicks;
	/** Where the bird was at the end of the last tick: a ridden bird's server delta is ~0 and its
	 *  xo is refreshed after the rider's move packet lands, so neither shows real movement. */
	private double trackX = Double.NaN, trackZ;

	/** Boosting from a pad right now (both sides). */
	public boolean boosting() {
		return this.entityData.get(DATA_BOOST);
	}

	/**
	 * Whiskerwind course effects: a boost pad under the feet gives a burst of speed
	 * (with a whoosh and a spark trail); standing on mud bogs the bird down. Both
	 * are movement-speed modifiers, so they work for riders and race AI alike.
	 */
	private void tickCourseEffects() {
		if (!onCourseGround()) {
			// a mangrove swamp's mud is not a course bog, and nothing here needs a block probe every tick
			trackX = Double.NaN;
			boostTicks = 0;
			if (boosting()) {
				this.entityData.set(DATA_BOOST, false);
			}
			speedMod(BOOST_ID, false, BOOST_POWER);
			speedMod(BOG_ID, false, BOG_DRAG);
			return;
		}
		double prevX = trackX, prevZ = trackZ;
		double mx = Double.isNaN(prevX) ? 0.0D : getX() - prevX, mz = Double.isNaN(prevX) ? 0.0D : getZ() - prevZ;
		trackX = getX();
		trackZ = getZ();
		boolean moving = mx * mx + mz * mz > 0.001D;
		if (moving && crossedBoostPad(prevX, prevZ)) {
			if (boostTicks <= 0) {
				level().playSound(null, this, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH,
						net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, 1.5F);
			}
			boostTicks = BOOST_TICKS;
		}
		if (boostTicks > 0) {
			boostTicks--;
			if (tickCount % 2 == 0 && level() instanceof ServerLevel sl) {
				sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, getX(), getY() + 0.5D, getZ(), 2,
						0.35D, 0.2D, 0.35D, 0.01D);
			}
		}
		boolean bog = onGround() && getBlockStateOn().is(Blocks.MUD);
		if (boosting() != (boostTicks > 0)) {
			this.entityData.set(DATA_BOOST, boostTicks > 0);
		}
		// AI birds take the boost as an attribute; a rider's client applies it to its input (getRiddenInput)
		boolean ridden = getControllingPassenger() instanceof Player;
		speedMod(BOOST_ID, boostTicks > 0 && !ridden, BOOST_POWER);
		speedMod(BOG_ID, bog && !ridden, BOG_DRAG);
	}

	/** Boost pads and bogs are Whiskerwind course features (plus the GameTest stand-in for the Square). */
	private boolean onCourseGround() {
		return Square.isSquare(level()) || (tk.darrow.chocobosreborn.race.RaceManager.testLevel != null
				&& level() == tk.darrow.chocobosreborn.race.RaceManager.testLevel);
	}

	/** Current block plus the path since last tick, so a dash cannot skip a 1-block strip. */
	private boolean crossedBoostPad(double prevX, double prevZ) {
		if (isBoostPad(blockPosition())) {
			return true;
		}
		if (Double.isNaN(prevX)) {
			return false;
		}
		int n = RaceScoring.boostPadSamples(getX() - prevX, getZ() - prevZ);
		for (int i = 0; i <= n; i++) {
			double f = i / (double) n;
			if (isBoostPad(BlockPos.containing(prevX + (getX() - prevX) * f, getY(), prevZ + (getZ() - prevZ) * f))) {
				return true;
			}
		}
		return false;
	}

	private boolean isBoostPad(BlockPos pos) {
		return level().getBlockState(pos).is(tk.darrow.chocobosreborn.block.ModBlocks.BOOST_PAD.get());
	}

	private void speedMod(net.minecraft.resources.ResourceLocation id, boolean on, double amount) {
		var inst = getAttribute(Attributes.MOVEMENT_SPEED);
		if (inst == null) {
			return;
		}
		boolean has = inst.hasModifier(id);
		if (on && !has) {
			inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, amount,
					net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (!on && has) {
			inst.removeModifier(id);
		}
	}

	/** FF7: first-place finishes EACH parent needs before a Carob / Zeio can change the line. */
	public static int minWinsEach(ChocoboColor a, ChocoboColor b, ChocoboNut nut) {
		if (nut == ChocoboNut.ZEIO) {
			return 3;
		}
		boolean greenBlue = (a == ChocoboColor.GREEN && b == ChocoboColor.BLUE)
				|| (a == ChocoboColor.BLUE && b == ChocoboColor.GREEN);
		return greenBlue ? 2 : 1;
	}

	/** Combined first-place wins that make the farm-line roll certain (README farm line). */
	public static int guaranteeWins(ChocoboColor a, ChocoboColor b, ChocoboNut nut) {
		if (nut == ChocoboNut.ZEIO) {
			return 12;
		}
		boolean greenBlue = (a == ChocoboColor.GREEN && b == ChocoboColor.BLUE)
				|| (a == ChocoboColor.BLUE && b == ChocoboColor.GREEN);
		return greenBlue ? 9 : 4;
	}

	/** Turn this bird into a Square AI racer: only the racer goal and floating. */
	public void installRacer(net.minecraft.world.entity.ai.goal.Goal racer) {
		this.moveControl = new tk.darrow.chocobosreborn.race.RacerMoveControl(this);
		this.goalSelector.removeAllGoals(g -> true);
		this.targetSelector.removeAllGoals(g -> true);
		this.goalSelector.addGoal(0, new ChocoFloatGoal());
		this.goalSelector.addGoal(1, racer);
	}

	/** Vanilla float jumps every tick in liquid; that fights sneak-dive on river and lava birds. */
	private final class ChocoFloatGoal extends FloatGoal {
		ChocoFloatGoal() {
			super(ChocoboEntity.this);
		}

		@Override
		public boolean canUse() {
			if (getControllingPassenger() instanceof Player p && (descending || p.isShiftKeyDown())) {
				return false;
			}
			return super.canUse();
		}
	}

	// ---------------------------------------------------------------- riding

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		if (saddled() && getFirstPassenger() instanceof Player player) {
			return player;
		}
		if (getFirstPassenger() instanceof KinStewardEntity) {
			return null;   // a kin jockey is along for the ride: the bird's own AI drives (Mob would hand it the reins)
		}
		return super.getControllingPassenger();
	}

	@Override
	public boolean canSprint() {
		return true;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, net.minecraft.world.entity.EntityDimensions dimensions, float scale) {
		float grow = getAgeScale();
		return new Vec3(0.0D, SEAT_H * grow, -SEAT_BACK * grow).yRot(-getYRot() * ((float) Math.PI / 180F));
	}

	@Override
	protected void tickRidden(Player player, Vec3 travel) {
		float catchup = RaceScoring.turnCatchup(trainedCooperation());
		this.setYRot(Mth.rotLerp(catchup, this.getYRot(), player.getYRot()));
		this.setXRot(Mth.rotLerp(catchup, this.getXRot(), player.getXRot() * 0.5F));
		this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
		if (!level().isClientSide) {
			int st = stamina();
			int max = maxStamina();
			// in the air on a flier, sprint means "down", not dash
			boolean wantsDash = player.isSprinting() && player.zza > 0.0F && !(color().fly() && !onGround());
			if (wantsDash && st > 0) {
				dashing = true;
				boolean skip = RaceScoring.intelSkipsDashDrain(trainedIntelligence(), tickCount, random.nextInt(100));
				setStamina(skip ? st : st - 1);
			} else {
				dashing = false;
				// Recover: quick when standing, slow while cruising.
				int gain = player.zza == 0.0F && player.xxa == 0.0F ? 2 : (tickCount % 3 == 0 ? 1 : 0);
				if (st < max && gain > 0) {
					setStamina(st + gain);
				}
			}
			if (dashing && RaceScoring.dashEnds(stamina())) {
				dashing = false;
				player.setSprinting(false);
			}
		}
		super.tickRidden(player, travel);
	}

	@Override
	protected Vec3 getRiddenInput(Player player, Vec3 travel) {
		float strafe = player.xxa * RaceScoring.strafeMul(trainedCooperation());
		float forward = player.zza;
		if (forward <= 0.0F) {
			forward *= 0.25F;
		}
		double mul = speedMul();
		// Airborne sprint is "dive" on a flier, not a stamina dash (tickRidden matches).
		boolean airDive = color().fly() && !onGround();
		if (player.isSprinting() && stamina() > 0 && !airDive) {
			mul *= RaceScoring.dashMul();
		} else if (stamina() <= 0) {
			mul *= RaceScoring.emptyStaminaMul();
		}
		if (boosting()) {
			mul *= 1.0D + BOOST_POWER;   // boost pad: +55%, same as the AI attribute
		}
		if (onGround() && Square.isSquare(level()) && getBlockStateOn().is(Blocks.MUD)) {
			mul *= 1.0D + BOG_DRAG;   // a course bog: same x0.45 the AI gets (not the Overworld's mud)
		}
		ChocoboColor c = color();
		boolean water = isInWater() || (c.waterWalk() && level().getFluidState(blockPosition().below()).is(FluidTags.WATER))
				|| (c.lavaWalk() && level().getFluidState(blockPosition().below()).is(FluidTags.LAVA));
		// mountedCruise carries grade; divide speedMul back out so training is not applied twice.
		double cruise = RaceScoring.mountedCruise(c.landSpeed(), c.waterSpeed(), water, racing(), grade().getRank());
		double training = RaceScoring.speedTrainingMul(trainedSpeed());
		return new Vec3(strafe, 0.0D, forward).scale(mul / speedMul() * training * cruise / Math.max(0.05D, c.landSpeed()));
	}

	@Override
	protected float getRiddenSpeed(Player player) {
		return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
	}

	@Override
	public void travel(Vec3 travel) {
		ChocoboColor c = color();
		Player player = getControllingPassenger() instanceof Player p ? p : null;
		// Down: sneak for water birds; for a flier in the air it is the sprint key (left
		// ctrl), so it works as fast as climbing and never dismounts.
		boolean flying = c.fly() && !onGround() && RaceScoring.mayFlyDuringRace(racing(), true);
		descending = player != null && (flying ? player.isSprinting() : player.isShiftKeyDown());
		if (player != null) {
			applyPendingJump();
		}
		if (c.fly() && player != null && RaceScoring.mayFlyDuringRace(racing(), true) && !onGround()) {
			// Powered flight: hold the air (gravity nearly cancelled), climb by looking
			// up while moving, dive by sneaking, flap for a burst of lift. No glide clamp.
			double dy = 0.07D;
			if (descending) {
				dy = -0.16D;
			} else if (player.getXRot() < -15.0F && player.zza > 0.0F) {
				dy = 0.16D;
			}
			if (flapTicks > 0) {
				dy += 0.10D;
				flapTicks--;
			}
			setDeltaMovement(getDeltaMovement().add(0.0D, dy, 0.0D));
		} else if (descending && player != null && (isInWater() || waterBelow())) {
			// water birds: sneak to go under
			setDeltaMovement(getDeltaMovement().add(0.0D, -0.05D, 0.0D));
		}
		super.travel(travel);
	}

	private boolean waterBelow() {
		return level().getFluidState(blockPosition().below()).is(FluidTags.WATER);
	}

	/** Airborne fliers steer with their ridden speed instead of the 0.02 mob air crawl. */
	@Override
	protected float getFlyingSpeed() {
		ChocoboColor c = color();
		if (c.fly() && !onGround() && getControllingPassenger() instanceof Player && RaceScoring.mayFlyDuringRace(racing(), true)) {
			// ~1.5x the ground terminal speed, scaled by the colour's air/land ratio
			return (float) (getSpeed() * 0.20D * c.airSpeed() / Math.max(0.05D, c.landSpeed()));
		}
		return super.getFlyingSpeed();
	}

	@Override
	public boolean onClimbable() {
		return (color().climb() && horizontalCollision) || super.onClimbable();
	}

	@Override
	public boolean canStandOnFluid(FluidState state) {
		ChocoboColor c = color();
		if (descending) {
			return false;   // rider is sneaking: let the bird go under
		}
		if (state.is(FluidTags.WATER)) {
			// FF7: river birds cross shallow water; only Gold (and its dyes) cross the ocean.
			return c.waterWalk() && (c.deepWater() || waterIsShallow());
		}
		if (state.is(FluidTags.LAVA)) {
			return c.lavaWalk();
		}
		return false;
	}

	/** Shallow = at most three blocks of water under the bird (a river, not a sea). */
	private boolean waterIsShallow() {
		// Standing on water means blockPosition() is the air above it: probe from the
		// block below. Up to four blocks of water = a sea, not a river.
		BlockPos pos = blockPosition();
		int depth = 0;
		for (int i = 1; i <= 4; i++) {
			if (!level().getFluidState(pos.below(i)).is(FluidTags.WATER)) {
				break;
			}
			depth++;
		}
		return depth <= 3;
	}

	@Override
	public boolean canJump() {
		return saddled();
	}

	/** Pending rider jump (0-100), set on the controlling side; applied in travel(). */
	private int pendingJump = -1;

	@Override
	public void onPlayerJump(int power) {
		// Runs on the client that drives the bird (LocalPlayer) and is echoed to the
		// server via handleStartJump; the impulse must be applied where travel() runs.
		pendingJump = Math.max(0, power);
	}

	@Override
	public void handleStartJump(int power) {
		pendingJump = Math.max(0, power);
	}

	private void applyPendingJump() {
		if (pendingJump < 0) {
			return;
		}
		int power = pendingJump;
		pendingJump = -1;
		if (color().fly() && RaceScoring.mayFlyDuringRace(racing(), true)) {
			flapTicks = 6 + power / 10;
			setDeltaMovement(getDeltaMovement().add(0.0D, 0.35D, 0.0D));
		} else if (onGround()) {
			setDeltaMovement(getDeltaMovement().add(0.0D, 0.42D + 0.004D * power, 0.0D));
		}
	}

	@Override
	public void handleStopJump() {
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return saddled() && !isBaby() && super.canAddPassenger(passenger);
	}

	// ------------------------------------------------------------------ tick

	@Override
	public void aiStep() {
		super.aiStep();
		if (!level().isClientSide) {
			if (isInLove() && isOrderedToSit()) {
				setOrderedToSit(false);
			}
			tickWanderRange();
			// A nut stays until they hatch; keep the love window open so a missed path
			// does not lock Carob/Zeio forever.
			if (fedNut() != ChocoboNut.NONE && !isInLove() && canFallInLove()) {
				setInLove(null);
			}
			int stage = computeStage();
			if (stage != growthStage()) {
				this.entityData.set(DATA_STAGE, stage);
				refreshDimensions();
			}
			if (!isVehicle() && stamina() < maxStamina() && tickCount % 4 == 0) {
				setStamina(stamina() + 1);
			}
			// A sated bird gets its appetite back slowly: one feed of each green per day,
			// including days spent unloaded.
			long day = level().getGameTime() / 24000L;
			if (lastGreensDay == Long.MIN_VALUE) {
				lastGreensDay = day;
			} else if (day > lastGreensDay) {
				int days = (int) Math.min(40L, day - lastGreensDay);
				lastGreensDay = day;
				for (int n = 0; n < days; n++) {
					for (int i = 0; i < greensFed.length; i++) {
						if (greensFed[i] > 0) {
							greensFed[i]--;
						}
					}
				}
			}
			// Gold: a slow natural regen (FF7's golden bird never stays hurt)
			if (color() == ChocoboColor.GOLD && tickCount % 40 == 0 && getHealth() < getMaxHealth()) {
				heal(1.0F);
			}
			tickCourseEffects();
			// Square gates: a ridden bird walking into one uses it (the rider cannot
			// reliably click a block from a 3 m saddle).
			if (gateCooldown > 0) {
				gateCooldown--;
			} else if (tickCount % 5 == 0 && getControllingPassenger() instanceof net.minecraft.server.level.ServerPlayer sp
					&& getDeltaMovement().horizontalDistanceSqr() > 0.004D) {
				net.minecraft.world.phys.AABB box = getBoundingBox().inflate(0.6D, 0.0D, 0.6D);
				for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
						BlockPos.containing(box.maxX, box.maxY + 1.0D, box.maxZ))) {
					net.minecraft.world.level.block.state.BlockState st = level().getBlockState(p);
					if (st.getBlock() instanceof tk.darrow.chocobosreborn.block.SquareGateBlock gate) {
						gateCooldown = 100;
						gate.rideThrough(st, (net.minecraft.server.level.ServerLevel) level(), p, sp, this);
						break;
					}
				}
			}
			// Chocobo Lure: wild birds show themselves to a player carrying one.
			if (!isTame() && !squareProtected() && tickCount % 20 == 0) {
				Player lurer = level().getNearestPlayer(this, 32.0D);
				if (lurer != null && (lurer.getMainHandItem().is(ModItems.CHOCOBO_LURE.get())
						|| lurer.getOffhandItem().is(ModItems.CHOCOBO_LURE.get()))) {
					addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false));
				}
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		ChocoboColor c = color();
		if (c.fireImmune() && isOnFire()) {
			clearFire();
		}
		if (!level().isClientSide && Square.isSquare(level()) && !racing() && !raceNpc() && raceTrack() < 0
				&& RaceScoring.squarePetFallRescue(getY(),
				getControllingPassenger() instanceof Player && color().fly() && !onGround() && getY() >= 20.0D)) {
			if (getControllingPassenger() instanceof net.minecraft.server.level.ServerPlayer) {
				tk.darrow.chocobosreborn.race.RaceSession.moveRidden(this, Square.ARRIVAL.x, Square.ARRIVAL.y,
						Square.ARRIVAL.z);
			} else {
				teleportTo(Square.ARRIVAL.x, Square.ARRIVAL.y, Square.ARRIVAL.z);
			}
			fallDistance = 0.0F;
		}
		if (!level().isClientSide && getControllingPassenger() instanceof LivingEntity rider && tickCount % 20 == 0) {
			if (c.waterBreathing()) {
				rider.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false));
			}
			if (c.nightVision()) {
				rider.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false));
			}
			if (c.riderFireResist()) {
				rider.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false));
			}
			if (c.riderSlowFalling()) {
				rider.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false));
				addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false));
			}
		}
	}

	@Override
	public int getAmbientSoundInterval() {
		return 900;   // a kweh now and then, not every few seconds (Ahmi: "a bit too often")
	}

	/** Lore: a tame bird says "kweh" (calm); a wild or alarmed one says "wark". */
	@Override
	protected SoundEvent getAmbientSound() {
		return isTame() ? ModSounds.KWEH.get() : ModSounds.WARK.get();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.WARK.get();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.WARK.get();
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return (color().fly() || color().riderSlowFalling()) ? false : super.causeFallDamage(distance, multiplier, source);
	}

	@Override
	public boolean fireImmune() {
		return color().fireImmune() || super.fireImmune();
	}

	@Override
	protected int decreaseAirSupply(int current) {
		// canBreatheUnderwater() is final in this mapping; sneak-dive is "down" for river birds.
		return color().waterWalk() ? current : super.decreaseAirSupply(current);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return !isTame() && !raceNpc() && !townBird() && super.removeWhenFarAway(distance);
	}

}
