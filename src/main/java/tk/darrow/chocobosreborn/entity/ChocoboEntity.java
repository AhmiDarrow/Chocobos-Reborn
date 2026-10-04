package tk.darrow.chocobosreborn.entity;

import tk.darrow.chocobosreborn.net.RiderPrediction;
import tk.darrow.chocobosreborn.net.RiderPayloads;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import tk.darrow.chocobosreborn.breed.BreedGenes;
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
import tk.darrow.chocobosreborn.race.RacePoint;
import tk.darrow.chocobosreborn.race.RaceScoring;
import tk.darrow.chocobosreborn.race.RaceTrack;
import tk.darrow.chocobosreborn.race.RacerContact;
import tk.darrow.chocobosreborn.race.Square;
import tk.darrow.chocobosreborn.sound.ModSounds;

public class ChocoboEntity extends TamableAnimal implements PlayerRideableJumping, net.minecraft.world.entity.HasCustomInventoryScreen, net.minecraft.world.ContainerListener {
	private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	/** Optional display plumage override (-1 = use {@link #DATA_COLOR}). Race stats stay on color(). */
	private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_GRADE = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_NUT = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_WINS = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_CLASS = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_CLASS_WINS = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	/** Ranked first places won while in Class C / B / A: the farm line counts each stage's own class. */
	private static final EntityDataAccessor<Integer> DATA_WINS_C = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_WINS_B = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_WINS_A = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
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
	/** Born stats passed by breeding. Greens add on top. Older birds have none. */
	private static final EntityDataAccessor<Integer> DATA_GENE_SPEED = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_GENE_STAMINA = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_GENE_INTEL = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_GENE_COOP = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	/** Which born stat sparked, 0..3. -1 when this chick did not spark. */
	private static final EntityDataAccessor<Integer> DATA_SPARK = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	/** Set when a dash spends the last point. Cleared once stamina reaches {@link tk.darrow.chocobosreborn.race.RaceScoring#DASH_READY}. */
	private static final EntityDataAccessor<Boolean> DATA_DASH_LOCKED = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.BOOLEAN);
	/** Synchronized countdown lock; never persisted beyond the live session. */
	private static final EntityDataAccessor<Boolean> DATA_RACE_HELD = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.BOOLEAN);
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
	/**
	 * Racer contact flags ({@link #CONTACT_GHOST}, {@link #CONTACT_DASH}), synced so every
	 * side resolving a bump knows which birds are solid and which are dashing.
	 */
	private static final EntityDataAccessor<Integer> DATA_CONTACT = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);
	private static final int CONTACT_GHOST = 1, CONTACT_DASH = 2;
	/** {@link Command} ordinal, synced so the equipment screen shows the current order. */
	private static final EntityDataAccessor<Integer> DATA_COMMAND = SynchedEntityData.defineId(ChocoboEntity.class, EntityDataSerializers.INT);

	/** What a tame bird does when nobody is riding it. Stay is vanilla's ordered-to-sit. */
	public enum Command {
		FOLLOW, STAY, WANDER;

		private static final Command[] VALUES = values();

		public static Command byId(int id) {
			return id >= 0 && id < VALUES.length ? VALUES[id] : FOLLOW;
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
	/**
	 * The driving client reports dash changes immediately and refreshes every five ticks. The vanilla
	 * sprint flag is not re-sent once the server has cleared it, which left a guest
	 * dashing for free after the bar first hit empty. {@code 0} means no fresh report.
	 */
	private boolean riderDashLinked;
	private int riderDashFresh;
	/** Client: the last dash bit sent, so a release is reported once. */
	private boolean clientDashSent;
	private boolean clientDashLinked;
	@Nullable private UUID dashRider;
	/** Game-day greens satiety last recovered, so unloaded birds still catch up. */
	private long lastGreensDay = Long.MIN_VALUE;
	/** Game time of the last training green. 0 = never trained. */
	private long lastGreensFeed;
	/** Ticks until a tame adult sheds its next feather; -1 until the first roll. */
	private int featherTicks = -1;
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
		builder.define(DATA_LOOK, -1);
		builder.define(DATA_GRADE, 1);
		builder.define(DATA_NUT, 0);
		builder.define(DATA_WINS, 0);
		builder.define(DATA_CLASS, 0);
		builder.define(DATA_CLASS_WINS, 0);
		builder.define(DATA_WINS_C, 0);
		builder.define(DATA_WINS_B, 0);
		builder.define(DATA_WINS_A, 0);
		builder.define(DATA_MALE, true);
		builder.define(DATA_SADDLED, false);
		builder.define(DATA_ARMOR, -1);
		builder.define(DATA_BAGS, false);
		builder.define(DATA_STAMINA, 80);
		builder.define(DATA_TR_SPEED, 0);
		builder.define(DATA_TR_STAMINA, 0);
		builder.define(DATA_TR_INTEL, 0);
		builder.define(DATA_TR_COOP, 0);
		builder.define(DATA_GENE_SPEED, 0);
		builder.define(DATA_GENE_STAMINA, 0);
		builder.define(DATA_GENE_INTEL, 0);
		builder.define(DATA_GENE_COOP, 0);
		builder.define(DATA_SPARK, -1);
		builder.define(DATA_DASH_LOCKED, false);
		builder.define(DATA_RACING, false);
		builder.define(DATA_RACE_HELD, false);
		builder.define(DATA_RACE_TRACK, -1);
		builder.define(DATA_STAGE, 3);
		builder.define(DATA_RACE_NPC, false);
		builder.define(DATA_TOWN_BIRD, false);
		builder.define(DATA_BOOST, false);
		builder.define(DATA_CONTACT, 0);
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

	/** Plumage drawn on the mesh; falls back to {@link #color()} when no look override is set. */
	public ChocoboColor displayColor() {
		int id = this.entityData.get(DATA_LOOK);
		return id < 0 ? color() : ChocoboColor.byId(id);
	}

	public void setColor(ChocoboColor color) {
		this.entityData.set(DATA_COLOR, color.getId());
		applyColorStats(true);
	}

	/** Set a display-only plumage (Class C rivals). Pass null to clear. */
	public void setLookColor(@org.jetbrains.annotations.Nullable ChocoboColor look) {
		this.entityData.set(DATA_LOOK, look == null ? -1 : look.getId());
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

	/**
	 * Ranked first places won while this bird was in {@code raceClass} (the win that
	 * promotes counts for the class it leaves). Class S wins count toward no stage: 0.
	 */
	public int winsInClass(RaceClass raceClass) {
		EntityDataAccessor<Integer> key = winsKey(raceClass);
		return key == null ? 0 : this.entityData.get(key);
	}

	public void setWinsInClass(RaceClass raceClass, int wins) {
		EntityDataAccessor<Integer> key = winsKey(raceClass);
		if (key != null) {
			this.entityData.set(key, Math.max(0, wins));
		}
	}

	/** Class C, B and A first places, in that order (ledger, save). */
	public int[] winsByClass() {
		return new int[]{winsInClass(RaceClass.C), winsInClass(RaceClass.B), winsInClass(RaceClass.A)};
	}

	@Nullable
	private static EntityDataAccessor<Integer> winsKey(RaceClass raceClass) {
		return switch (raceClass) {
			case C -> DATA_WINS_C;
			case B -> DATA_WINS_B;
			case A -> DATA_WINS_A;
			case S -> null;
		};
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

	public int geneSpeed() {
		return this.entityData.get(DATA_GENE_SPEED);
	}

	public int geneStamina() {
		return this.entityData.get(DATA_GENE_STAMINA);
	}

	public int geneIntelligence() {
		return this.entityData.get(DATA_GENE_INTEL);
	}

	public int geneCooperation() {
		return this.entityData.get(DATA_GENE_COOP);
	}

	/** Born plus greens, capped at 100. This is what the bird races with. Blood is what a chicobo inherits. */
	public int speedStat() {
		return tk.darrow.chocobosreborn.breed.BreedGenes.passed(geneSpeed(), trainedSpeed());
	}

	public int staminaStat() {
		return tk.darrow.chocobosreborn.breed.BreedGenes.passed(geneStamina(), trainedStamina());
	}

	public int intelligenceStat() {
		return tk.darrow.chocobosreborn.breed.BreedGenes.passed(geneIntelligence(), trainedIntelligence());
	}

	public int cooperationStat() {
		return tk.darrow.chocobosreborn.breed.BreedGenes.passed(geneCooperation(), trainedCooperation());
	}

	public int spark() {
		return this.entityData.get(DATA_SPARK);
	}

	public void setSpark(int stat) {
		this.entityData.set(DATA_SPARK, stat);
	}

	public int bloodSpeed() {
		return BreedGenes.blood(geneSpeed(), trainedSpeed());
	}

	public int bloodStamina() {
		return BreedGenes.blood(geneStamina(), trainedStamina());
	}

	public int bloodIntelligence() {
		return BreedGenes.blood(geneIntelligence(), trainedIntelligence());
	}

	public int bloodCooperation() {
		return BreedGenes.blood(geneCooperation(), trainedCooperation());
	}

	public void setGenes(int speed, int stamina, int intelligence, int cooperation) {
		this.entityData.set(DATA_GENE_SPEED, Math.min(ChocoboGreen.MAX_POINTS, Math.max(0, speed)));
		this.entityData.set(DATA_GENE_STAMINA, Math.min(ChocoboGreen.MAX_POINTS, Math.max(0, stamina)));
		this.entityData.set(DATA_GENE_INTEL, Math.min(ChocoboGreen.MAX_POINTS, Math.max(0, intelligence)));
		this.entityData.set(DATA_GENE_COOP, Math.min(ChocoboGreen.MAX_POINTS, Math.max(0, cooperation)));
	}

	/**
	 * Save format 5 (first places per class). A bird saved by an older version is brought
	 * onto the current rules once, step by step ({@link RaceScoring#convertedClassPoints}):
	 * format 1 -> 2 marks to points of nine; 2 -> 3 points of nine to 36; 3 -> 4 uniform 36
	 * to C 36 / B 54 / A 72; 4 -> 5 lifetime wins shared out over the classes it has
	 * reached ({@link RaceScoring#migratedWinsByClass}). Training, wins, colour and grade stay.
	 */
	static final int SAVE_FORMAT = 5;

	private void convertOldSave(int format) {
		this.entityData.set(DATA_CLASS_WINS, RaceScoring.convertedClassPoints(format, raceClass().getId(), classWins()));
		if (format < 5) {
			setWinsByClass(RaceScoring.migratedWinsByClass(raceClass().getId(), raceWins()));
		}
		if (format < 2 && !raceNpc() && !townBird()
				&& BreedGenes.blankLine(geneSpeed(), geneStamina(), geneIntelligence(), geneCooperation())) {
			rollWildBlood();
		}
	}

	private void setWinsByClass(int[] wins) {
		setWinsInClass(RaceClass.C, wins.length > 0 ? wins[0] : 0);
		setWinsInClass(RaceClass.B, wins.length > 1 ? wins[1] : 0);
		setWinsInClass(RaceClass.A, wins.length > 2 ? wins[2] : 0);
	}

	/** A wild bird's bloodline, rolled from its grade so a wonderful stray starts ahead of a poor one. */
	private void rollWildBlood() {
		int floor = BreedGenes.gradeFloor(bornGrade().getRank());
		setGenes(
				BreedGenes.wildGene(floor, random.nextInt(9)),
				BreedGenes.wildGene(floor, random.nextInt(9)),
				BreedGenes.wildGene(floor, random.nextInt(9)),
				BreedGenes.wildGene(floor, random.nextInt(9)));
		setSpark(-1);
	}

    private RiderPrediction.Server raceInputs;
    private RiderPrediction.Client racePrediction;
    private int raceInputEpoch, clientInputEpoch, lastInputAck, lastInputSnapshotTick;
    private long raceInputSeed;
    private UUID raceInputRider;
    private boolean frameDash, frameEmpty, frameReady;

    private boolean predictingRace() {
        return level().isClientSide && racing() && isControlledByLocalInstance() && racePrediction != null;
    }

    /** Called only at GO, after the normal full-stamina reset. */
    public void beginRaceInputs(ServerPlayer player) {
        // GameTest mock connections do not negotiate custom channels. Real clients require this channel.
        if (!player.connection.hasChannel(RiderPayloads.Snapshot.TYPE)) return;
        raceInputEpoch++;
        raceInputSeed = random.nextLong();
        raceInputRider = player.getUUID();
        raceInputs = new RiderPrediction.Server(System.nanoTime());
        lastInputAck = 0;
        sendRaceSnapshot(player, false);
    }

    private void sendRaceSnapshot(ServerPlayer player, boolean resend) {
        if (raceInputs == null) return;
        lastInputSnapshotTick = tickCount;
        lastInputAck = raceInputs.processed();
        PacketDistributor.sendToPlayer(player, new RiderPayloads.Snapshot(getId(), raceInputEpoch,
                lastInputAck, stamina(), dashLocked(), maxStamina(), intelligenceStat(), raceInputSeed, resend));
    }

    public void receiveRaceInput(ServerPlayer player, RiderPayloads.Input input) {
        if (!racing() || raceHeld() || raceInputs == null || !player.getUUID().equals(raceInputRider)) return;
        if (input.sequence() == 0) {
            if (tickCount - lastInputSnapshotTick >= 5) sendRaceSnapshot(player, true);
            return;
        }
        if (input.epoch() != raceInputEpoch || input.sequence() < 0) return;
        if (!raceInputs.offer(new RiderPrediction.Frame(input.sequence(), input.dash(), input.moving()))
                && tickCount - lastInputSnapshotTick >= 5) sendRaceSnapshot(player, true);
        drainRaceInputs(player);
    }

    private void drainRaceInputs(ServerPlayer player) {
        if (raceInputs == null || !player.getUUID().equals(raceInputRider)) return;
        RiderPrediction.Frame frame;
        var rules = new RiderPrediction.Rules(maxStamina(), intelligenceStat(), raceInputSeed);
        while ((frame = raceInputs.poll(System.nanoTime())) != null) {
            var before = new RiderPrediction.State(stamina(), dashLocked());
            var after = RiderPrediction.step(before, frame, rules);
            setRaceDashFlag(RiderPrediction.dashing(before, frame));
            setStamina(after.stamina());
            setDashLocked(after.locked());
        }
        if (raceInputs.processed() - lastInputAck >= 5 || tickCount - lastInputSnapshotTick >= 20)
            sendRaceSnapshot(player, false);
    }

    public void receiveRaceSnapshot(RiderPayloads.Snapshot snapshot) {
        if (!level().isClientSide || !racing() || !isControlledByLocalInstance() || snapshot.epoch() < clientInputEpoch) return;
        var state = new RiderPrediction.State(snapshot.stamina(), snapshot.locked());
        var rules = new RiderPrediction.Rules(snapshot.maximum(), snapshot.intelligence(), snapshot.seed());
        if (racePrediction == null || snapshot.epoch() != clientInputEpoch) {
            clientInputEpoch = snapshot.epoch();
            racePrediction = new RiderPrediction.Client(snapshot.acknowledged(), state, rules);
        } else {
            racePrediction.acknowledge(snapshot.acknowledged(), state, rules);
        }
        if (snapshot.resend()) for (var frame : racePrediction.pending()) sendRaceFrame(frame);
    }

    private void sendRaceFrame(RiderPrediction.Frame frame) {
        PacketDistributor.sendToServer(new RiderPayloads.Input(getId(), clientInputEpoch,
                frame.sequence(), frame.dash(), frame.moving()));
    }

    private void predictRaceFrame(Player player) {
        frameReady = false;
        if (racePrediction == null || !racePrediction.canAdvance()) {
            if (tickCount % 20 == 0) PacketDistributor.sendToServer(new RiderPayloads.Input(getId(), 0, 0, false, false));
            return;
        }
        var before = racePrediction.state();
        var frame = racePrediction.advance(clientWantsDash(player), player.zza != 0 || player.xxa != 0);
        frameDash = RiderPrediction.dashing(before, frame);
        frameEmpty = before.stamina() <= 0;
        frameReady = true;
        sendRaceFrame(frame);
    }

	public boolean dashLocked() {
		return predictingRace() ? racePrediction.state().locked() : this.entityData.get(DATA_DASH_LOCKED);
	}

	public void setDashLocked(boolean locked) {
		this.entityData.set(DATA_DASH_LOCKED, locked);
	}

	public boolean racing() {
		return this.entityData.get(DATA_RACING);
	}

	public Command command() {
		return Command.byId(this.entityData.get(DATA_COMMAND));
	}

	/** Gysahl succeeded. The bird is tame and its order is Follow. */
	public void befriend(Player player) {
		tame(player);
		heal(5.0F);
		ledgerUpdate();
		giveCommand(Command.FOLLOW, player);
		level().broadcastEntityEvent(this, (byte) 7);
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
			// The whistle reads the order from the ledger, so a parked bird is not loaded just to be left.
			ledgerUpdate();
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

	public boolean raceHeld() {
		return this.entityData.get(DATA_RACE_HELD);
	}

	public void setRaceHeld(boolean held) {
		this.entityData.set(DATA_RACE_HELD, held);
	}

	public void setRacing(boolean racing) {
		this.entityData.set(DATA_RACING, racing);
		if (!racing) {
            raceInputs = null;
            racePrediction = null;
            frameReady = false;
			setRaceHeld(false);
			this.entityData.set(DATA_CONTACT, 0);
			contactSlow.clear();
		}
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

	// ------------------------------------------------------ racer contact

	/** Pace loss from the last bump, fading (see {@link RacerContact.Slow}). */
	private final RacerContact.Slow contactSlow = new RacerContact.Slow();
	/** Where this bird was at the end of the last tick, and its smoothed velocity from that (every side). */
	private double contactPrevX = Double.NaN, contactPrevZ, contactVx, contactVz;

	/**
	 * Installed by the client mod: this client's round trip in ms (vanilla's player-list
	 * latency). Null on a dedicated server.
	 */
	@Nullable
	public static java.util.function.IntSupplier CLIENT_RTT_MS;

	/**
	 * Installed by the client mod ({@code client/RemoteRaceFrames}): racing birds this client
	 * does not drive are played back from their server frames instead of vanilla's lerp.
	 */
	public interface RemoteDisplay {
		/** Put {@code bird} where its frames say it stood at the tick shown now; false when it has none. */
		boolean place(ChocoboEntity bird);
		/** True while frames for this entity are arriving: vanilla's position packets are then ignored. */
		boolean owns(int entityId);
		/** Ticks the field is shown behind the fastest frames (see {@code PlayoutClock#behind}). */
		double behindTicks();
	}

	public static RemoteDisplay REMOTE_DISPLAY;
	/** Tick-stamped frames of the field go out unless -Dchocobosreborn.framePlayback=false (see ChocobosRebornClient). */
	private static final boolean SEND_RACE_FRAMES = Boolean.parseBoolean(System.getProperty("chocobosreborn.framePlayback", "true"));

	/** Exempt from contact: finished and parking, or just set back on the road. */
	public boolean raceGhost() {
		return (this.entityData.get(DATA_CONTACT) & CONTACT_GHOST) != 0;
	}

	public void setRaceGhost(boolean ghost) {
		setContactFlag(CONTACT_GHOST, ghost);
	}

	/** Dashing, as the server last knew it (AI: its goal; a rider: its acknowledged input). */
	public boolean raceDashFlag() {
		return (this.entityData.get(DATA_CONTACT) & CONTACT_DASH) != 0;
	}

	public void setRaceDashFlag(boolean dash) {
		setContactFlag(CONTACT_DASH, dash);
	}

	private void setContactFlag(int bit, boolean on) {
		int was = this.entityData.get(DATA_CONTACT);
		int now = on ? was | bit : was & ~bit;
		if (now != was) {
			this.entityData.set(DATA_CONTACT, now);
		}
	}

	/** Solid for racer contact: in a live heat, not held on the grid or after a set-back, not a ghost. */
	public boolean contactSolid() {
		return RacerContact.solid(racing(), raceHeld(), raceGhost(), getControllingPassenger() instanceof Player)
				&& isAlive() && !isPassenger();
	}

	/** Velocity for contact and traffic: measured from positions, so it holds for birds this side does not simulate. */
	public double contactVx() {
		return contactVx;
	}

	public double contactVz() {
		return contactVz;
	}

	/** Weight for contact: the simulating client knows its own dash and pad; everyone else goes by the synced flags. */
	private double contactWeight() {
		if (level().isClientSide && isControlledByLocalInstance()) {
			return RacerContact.weight(predictingRace() && frameDash, localBoostTicks > 0);
		}
		return RacerContact.weight(raceDashFlag(), boosting());
	}

	private void trackContactVelocity() {
		if (!racing()) {
			contactPrevX = Double.NaN;
			contactVx = contactVz = 0.0D;
			return;
		}
		if (!Double.isNaN(contactPrevX)) {
			double dx = getX() - contactPrevX, dz = getZ() - contactPrevZ;
			if (dx * dx + dz * dz > 16.0D) {
				dx = dz = 0.0D;   // a teleport (set-back, grid) is not a speed
			}
			// a rider's bird moves in packet bursts on the server: smooth them
			contactVx = contactVx * 0.5D + dx * 0.5D;
			contactVz = contactVz * 0.5D + dz * 0.5D;
		}
		contactPrevX = getX();
		contactPrevZ = getZ();
	}

	/**
	 * Kart bumps, resolved for this bird by the side that simulates it (the server for
	 * an AI bird, the driving client for a rider's), against the other solid racers
	 * as this side sees them. Only this bird's own velocity and pace change. The
	 * driving client leads the remote birds it sees by its round trip, so it compares
	 * the moment the server will see ({@link RacerContact#leadTicks}).
	 */
	private void applyRacerContact() {
		contactSlow.tick();
		if (!contactSolid()) {
			contactSlow.clear();
			return;
		}
		int lead = level().isClientSide && CLIENT_RTT_MS != null
				? RacerContact.leadTicks(CLIENT_RTT_MS.getAsInt(), REMOTE_DISPLAY != null ? REMOTE_DISPLAY.behindTicks() : -1.0D) : 0;
		double reach = RacerContact.REACH + 2.0D + lead * 0.5D;
		List<ChocoboEntity> near = level().getEntitiesOfClass(ChocoboEntity.class,
				getBoundingBox().inflate(reach, RacerContact.HEIGHT, reach), e -> e != this && e.contactSolid());
		if (!near.isEmpty()) {
			List<RacerContact.Body> others = new ArrayList<>(near.size());
			for (ChocoboEntity o : near) {
				others.add(new RacerContact.Body(o.getX(), o.getY(), o.getZ(), o.contactVx, o.contactVz,
						o.contactWeight(), true).ahead(o.isControlledByLocalInstance() ? 0 : lead));
			}
			Vec3 v = getDeltaMovement();
			double hx = v.x, hz = v.z;
			if (hx * hx + hz * hz < 0.0025D) {
				float yaw = getYRot() * ((float) Math.PI / 180F);
				hx = -Mth.sin(yaw);
				hz = Mth.cos(yaw);
			}
			RacerContact.Push push = RacerContact.resolve(
					new RacerContact.Body(getX(), getY(), getZ(), v.x, v.z, contactWeight(), true),
					hx, hz, others, contactGuard());
			if (push.any()) {
				setDeltaMovement(v.add(push.dvx(), 0.0D, push.dvz()));
				if (push.loss() > 0.0D) {
					contactSlow.hit(push.loss());
				}
			}
		}
		double pace = contactSlow.factor();
		if (pace < 1.0D) {
			setSpeed((float) (getSpeed() * pace));
		}
	}

	/** Where this bird sits across its course's road, for {@link RacerContact.Guard}. */
	private RacerContact.Guard contactGuard() {
		int id = raceTrack();
		if (id < 0) {
			return RacerContact.Guard.NONE;
		}
		RaceTrack track = RaceTrack.byId(id);
		double t = track.progressAt(getX(), getZ(), contactHint);
		contactHint = t;
		RacePoint c = track.pointAt(t);
		double[] tg = track.tangent(t);
		double lx = -tg[1], lz = tg[0];
		return new RacerContact.Guard((getX() - c.x()) * lx + (getZ() - c.z()) * lz, lx, lz);
	}

	private double contactHint = -1.0D;

	/** Town / NPC / live heat, including the finish-grace while the bird still carries a course. */
	private boolean squareProtected() {
		return RaceScoring.squareNpcProtected(townBird(), raceNpc(), racing(),
				raceTrack() >= 0 && Square.isSquare(level()));
	}

	/** Speed training: +0.35% top speed per point, +35% at 100. */
	public static final double SPEED_PER_POINT = RaceScoring.SPEED_PER_POINT;

	public double speedMul() {
		return RaceScoring.gradeSpeedMul(grade().getRank()) * RaceScoring.speedTrainingMul(speedStat());
	}

	public boolean male() {
		return this.entityData.get(DATA_MALE);
	}

	public void setMale(boolean male) {
		this.entityData.set(DATA_MALE, male);
	}

	public int stamina() {
		return predictingRace() ? racePrediction.state().stamina() : this.entityData.get(DATA_STAMINA);
	}

	public int maxStamina() {
		return RaceScoring.maxStamina(grade().getRank(), raceClass().getId(), false) + staminaStat();
	}

	public void setStamina(int value) {
		this.entityData.set(DATA_STAMINA, Mth.clamp(value, 0, maxStamina()));
	}

	/**
	 * The driving client reports the dash key, not the latched sprint flag.
	 * A release clears immediately; a hold stays fresh for a few ticks of lag.
	 */
	public void noteRiderDash(boolean dash) {
		this.dashRider = getControllingPassenger() == null ? null : getControllingPassenger().getUUID();
		this.riderDashLinked = true;
		this.riderDashFresh = dash ? 20 : 0;
	}

	/**
	 * Prefer the controlling rider's own dash report. Before the first report arrives,
	 * vanilla sprint input is a fallback; a previous rider's report never carries over.
	 */
	private boolean riderWantsDash(Player player) {
		if (riderDashLinked && player.getUUID().equals(dashRider)) {
			return riderDashFresh > 0;
		}
		return RaceScoring.riderWantsDash(player.isSprinting(), player.zza, color().fly() && !racing(), onGround());
	}

	/** Driving client: dash key held, moving forward, and not an airborne dive. */
	private boolean clientWantsDash(Player player) {
		boolean key = !raceHeld() && (SPRINT_KEY != null ? SPRINT_KEY.getAsBoolean() : player.isSprinting());
		return RaceScoring.riderWantsDash(key, player.zza, color().fly() && !racing(), onGround());
	}

	public void fillStamina() {
		setStamina(maxStamina());
		setDashLocked(false);
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

	/** A town bird's patch (ranch, stable yard or nursery): x, z and radius. Null for the old village-wide range. */
	@Nullable
	private int[] townHome;

	public void setTownHome(int x, int z, int radius) {
		townHome = new int[]{x, z, radius};
		restrictTo(new BlockPos(x, tk.darrow.chocobosreborn.race.SquareBuilder.GROUND_Y, z), radius);
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
	 * First-place finish at Chocobo Square. Ranked wins count for the farm line in the class
	 * the bird is in when it wins ({@link #winsInClass}) and earn {@code points} toward
	 * promotion ({@link RaceScoring#winPoints}: 4 a sprint, a grand prix by its length;
	 * {@link RaceClass#pointsToPromote()} promote). The class never drops. Returns the outcome, or
	 * null for an unranked finish.
	 */
	@org.jetbrains.annotations.Nullable
	public RaceScoring.Promotion recordFirstPlace(boolean ranked, int points) {
		if (!ranked) {
			return null;
		}
		this.entityData.set(DATA_WINS, raceWins() + 1);
		setWinsInClass(raceClass(), winsInClass(raceClass()) + 1);
		RaceScoring.Promotion p = RaceScoring.afterFirstPlace(raceClass(), classWins(), points);
		this.entityData.set(DATA_CLASS, p.raceClass().getId());
		this.entityData.set(DATA_CLASS_WINS, p.classWins());
		return p;
	}

	/** Stamp training exactly. Feeding still goes through {@link #addTraining}. */
	public void setTraining(int speed, int stamina, int intelligence, int cooperation) {
		int cap = ChocoboGreen.MAX_POINTS;
		this.entityData.set(DATA_TR_SPEED, Math.max(0, Math.min(cap, speed)));
		this.entityData.set(DATA_TR_STAMINA, Math.max(0, Math.min(cap, stamina)));
		this.entityData.set(DATA_TR_INTEL, Math.max(0, Math.min(cap, intelligence)));
		this.entityData.set(DATA_TR_COOP, Math.max(0, Math.min(cap, cooperation)));
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
			rollWildBlood();
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
		if (this.entityData.get(DATA_LOOK) >= 0) {
			tag.putInt("LookPlumage", this.entityData.get(DATA_LOOK));
		}
		tag.putInt("Grade", bornGrade().getRank());
		tag.putInt("Nut", fedNut().ordinal());
		tag.putInt("RaceWins", raceWins());
		tag.putInt("RaceClass", raceClass().getId());
		tag.putInt("ClassWins", classWins());
		tag.putIntArray("WinsByClass", winsByClass());
		tag.putInt("SaveFormat", SAVE_FORMAT);
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
		tag.putInt("GeneSpeed", geneSpeed());
		tag.putInt("GeneStamina", geneStamina());
		tag.putInt("GeneIntel", geneIntelligence());
		tag.putInt("GeneCoop", geneCooperation());
		tag.putInt("Spark", spark());
		tag.putBoolean("DashLocked", dashLocked());
		tag.putIntArray("GreensFed", greensFed.clone());
		tag.putBoolean("RaceNpc", raceNpc());
		tag.putBoolean("TownBird", townBird());
		if (townHome != null) {
			tag.putIntArray("TownHome", townHome);
		}
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
		if (featherTicks >= 0) {
			tag.putInt("FeatherTicks", featherTicks);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.entityData.set(DATA_COLOR, ChocoboColor.byId(tag.getInt("Plumage")).getId());
		this.entityData.set(DATA_LOOK, tag.contains("LookPlumage") ? tag.getInt("LookPlumage") : -1);
		setGrade(ChocoboGrade.byRank(tag.getInt("Grade")));
		this.entityData.set(DATA_NUT, tag.getInt("Nut"));
		this.entityData.set(DATA_WINS, tag.getInt("RaceWins"));
		this.entityData.set(DATA_CLASS, tag.getInt("RaceClass"));
		this.entityData.set(DATA_CLASS_WINS, tag.getInt("ClassWins"));
		setWinsByClass(tag.getIntArray("WinsByClass"));
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
		this.entityData.set(DATA_GENE_SPEED, tag.getInt("GeneSpeed"));
		this.entityData.set(DATA_GENE_STAMINA, tag.getInt("GeneStamina"));
		this.entityData.set(DATA_GENE_INTEL, tag.getInt("GeneIntel"));
		this.entityData.set(DATA_GENE_COOP, tag.getInt("GeneCoop"));
		this.entityData.set(DATA_SPARK, tag.contains("Spark") ? tag.getInt("Spark") : -1);
		this.entityData.set(DATA_DASH_LOCKED, tag.getBoolean("DashLocked"));
		int[] fed = tag.getIntArray("GreensFed");
		for (int i = 0; i < Math.min(fed.length, greensFed.length); i++) {
			greensFed[i] = fed[i];
		}
		this.entityData.set(DATA_RACE_NPC, tag.getBoolean("RaceNpc"));
		this.entityData.set(DATA_TOWN_BIRD, tag.getBoolean("TownBird"));
		int[] home = tag.getIntArray("TownHome");
		townHome = home.length == 3 ? home : null;
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
		featherTicks = tag.contains("FeatherTicks") ? tag.getInt("FeatherTicks") : -1;
		int format = tag.getInt("SaveFormat");
		if (format < SAVE_FORMAT) {
			convertOldSave(format);
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
			tk.darrow.chocobosreborn.race.ChocoboWhistle.onAdded(this);
			if (townBird() && Square.isSquare(level())) {
				if (townHome != null) {
					restrictTo(new BlockPos(townHome[0], tk.darrow.chocobosreborn.race.SquareBuilder.GROUND_Y, townHome[1]), townHome[2]);
				} else {
					restrictTo(new BlockPos(0, tk.darrow.chocobosreborn.race.SquareBuilder.GROUND_Y, -72), 45);
				}
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
					befriend(player);
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
		if (stack.is(net.minecraft.world.item.Items.BRUSH) && isTame() && isOwnedBy(player) && !isBaby()) {
			// brushing your bird is petting it: a feather comes loose, like an armadillo's scute
			if (!level().isClientSide) {
				spawnAtLocation(new ItemStack(net.minecraft.world.item.Items.FEATHER), getBbHeight() * 0.5F);
				playSound(net.minecraft.sounds.SoundEvents.BRUSH_GENERIC, 1.0F, 1.0F);
				level().broadcastEntityEvent(this, (byte) 7);   // hearts
				gameEvent(net.minecraft.world.level.gameevent.GameEvent.ENTITY_INTERACT, player);
				stack.hurtAndBreak(16, player, getSlotForHand(hand));
			}
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
						RaceClass stage = BreedRules.stageClass(color(), fed);
						int need = BreedingOdds.minWinsEach(stage);
						int have = winsInClass(stage);
						if (have < need) {
							player.displayClientMessage(Component.translatable("chocobosreborn.nut.needs_wins",
									Component.translatable("chocobosreborn.nut." + fed.id()), need,
									Component.translatable("chocobosreborn.class." + stage.id()), have), true);
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
		boolean instabuild = player.getAbilities().instabuild;
		boolean sated = greensFed[idx] >= green.satiety();
		boolean digesting = !ChocoboGreen.trainReady(lastGreensFeed, level().getGameTime(), instabuild);
		if ((sated || digesting) && getHealth() < getMaxHealth()) {
			// too full to learn from it, but a hurt bird still eats a green to heal
			if (!level().isClientSide) {
				heal(3.0F);
				level().broadcastEntityEvent(this, (byte) 18);
				playSound(ModSounds.KWEH.get(), 0.6F, 1.2F);
				player.displayClientMessage(Component.translatable("chocobosreborn.greens.heal_only"), true);
				if (!instabuild) {
					stack.shrink(1);
				}
			}
			return InteractionResult.sidedSuccess(level().isClientSide);
		}
		if (sated) {
			if (!level().isClientSide) {
				player.displayClientMessage(Component.translatable("chocobosreborn.greens.sated"), true);
			}
			return InteractionResult.sidedSuccess(level().isClientSide);
		}
		if (digesting) {
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
		// each stage counts only the first places both parents won in its class
		RaceClass stage = BreedRules.stageClass(color(), mate.color(), nut);
		int guarantee = BreedingOdds.guaranteeWins(stage);
		int minEach = BreedingOdds.minWinsEach(stage);
		int winsHere = winsInClass(stage);
		int winsThere = mate.winsInClass(stage);
		boolean qualify = BreedingOdds.qualifies(winsHere, winsThere, minEach);
		boolean hit = random.nextDouble() < BreedingOdds.chance(winsHere, winsThere, minEach, guarantee);
		ChocoboColor inherit = random.nextBoolean() ? color() : mate.color();
		ChocoboColor child = BreedRules.resolve(color(), mate.color(), grade(), mate.grade(), nut, qualify, hit,
				random.nextBoolean(), inherit);
		chick.setColor(child);
		int rank = (bornGrade().getRank() + mate.bornGrade().getRank()) / 2;
		if (nut == ChocoboNut.ZEIO) {
			rank += 1;
		}
		chick.setGrade(ChocoboGrade.byRank(rank));
		// Most chicks sit a little under the blood. One in eight sparks one stat
		// toward the stronger parent. A born 97 or better still needs that spark
		// on a pair whose blood is already elite.
		boolean spark = random.nextInt(BreedGenes.SPARK_ODDS) == 0;
		int favor = spark ? random.nextInt(4) : -1;
		int floor = BreedGenes.gradeFloor(chick.bornGrade().getRank());
		int tier = nut.getTier();
		int[] mine = {bloodSpeed(), bloodStamina(), bloodIntelligence(), bloodCooperation()};
		int[] theirs = {mate.bloodSpeed(), mate.bloodStamina(), mate.bloodIntelligence(), mate.bloodCooperation()};
		boolean[] primary = {true, true, false, false};
		int[] born = new int[4];
		for (int i = 0; i < 4; i++) {
			boolean favored = spark && i == favor;
			born[i] = BreedGenes.childGene(mine[i], theirs[i], BreedGenes.nutGift(tier, primary[i]),
					BreedGenes.favorLean(favored, random.nextInt(101)), random.nextInt(21) - 12, floor,
					favored ? BreedGenes.SPARK : 0);
		}
		chick.setGenes(born[0], born[1], born[2], born[3]);
		chick.setSpark(favor);
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

	/**
	 * Installed by the client mod: the dash key is physically down. Minecraft's
	 * sprint flag stays latched while W is held, so using that flag spends the
	 * bar on the first press and never opens regen until the rider lets go of
	 * forward. Null on a dedicated server.
	 */
	@Nullable
	public static java.util.function.BooleanSupplier SPRINT_KEY;

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
	/** Speed taken while bogged (-55%). Boost strength comes from intelligence. */
	private static final double BOG_DRAG = -0.55D;
	private int boostTicks;
	/** Where the bird was at the end of the last tick: a ridden bird's server delta is ~0 and its
	 *  xo is refreshed after the rider's move packet lands, so neither shows real movement. */
	private double trackX = Double.NaN, trackY, trackZ;
	/**
	 * The driving client's own boost timer. A rider's bird moves where its client says,
	 * so a pad found on the server's copy (one ping behind) and synced back (another)
	 * would hand the host a boost on the pad and a guest one down the road. Every rider,
	 * host or guest, times the boost from its own client instead; the server's copy
	 * still drives the sparks, the whoosh and {@link #boosting()} for onlookers.
	 */
	private int localBoostTicks;
	private double localX = Double.NaN, localY, localZ;

	/** Boosting from a pad right now (both sides). */
	public boolean boosting() {
		return this.entityData.get(DATA_BOOST);
	}

	/**
	 * Whiskerwind course effects: a boost pad under the feet gives a burst of speed
	 * (with a whoosh and a spark trail); standing on mud bogs the bird down. Both
	 * are movement-speed modifiers, so they work for riders and race AI alike.
	 */
	/** Pads and bogs matter for a heat, a field bird, or someone in the saddle. Parked Square birds do not probe. */
	private boolean courseEffectsActive() {
		return racing() || raceNpc() || getControllingPassenger() instanceof Player;
	}

	private void tickCourseEffects() {
		if (!onCourseGround() || !courseEffectsActive()) {
			// a mangrove swamp's mud is not a course bog, and a bird standing in the Square
			// does not need a block probe every tick
			if (!Double.isNaN(trackX) || boostTicks > 0 || boosting()) {
				trackX = Double.NaN;
				boostTicks = 0;
				if (boosting()) {
					this.entityData.set(DATA_BOOST, false);
				}
				speedMod(BOOST_ID, false, 0.0D);
				speedMod(BOG_ID, false, BOG_DRAG);
			}
			return;
		}
		double prevX = trackX, prevY = trackY, prevZ = trackZ;
		double mx = Double.isNaN(prevX) ? 0.0D : getX() - prevX, mz = Double.isNaN(prevX) ? 0.0D : getZ() - prevZ;
		trackX = getX();
		trackY = getY();
		trackZ = getZ();
		boolean moving = mx * mx + mz * mz > 0.001D;
		if (moving && crossedBoostPad(prevX, prevY, prevZ)) {
			if (boostTicks <= 0) {
				level().playSound(null, this, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH,
						net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, 1.5F);
			}
			boostTicks = RaceScoring.boostTicks(intelligenceStat());
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
		// AI birds take the boost as an attribute; a rider's client applies it in getRiddenSpeed.
		boolean ridden = getControllingPassenger() instanceof Player;
		speedMod(BOOST_ID, boostTicks > 0 && !ridden, RaceScoring.boostPower(intelligenceStat()));
		speedMod(BOG_ID, bog && !ridden, BOG_DRAG);
	}

	/** Boost pads and bogs are Whiskerwind course features (plus the GameTest stand-in for the Square). */
	private boolean onCourseGround() {
		return Square.isSquare(level()) || (tk.darrow.chocobosreborn.race.RaceManager.testLevel != null
				&& level() == tk.darrow.chocobosreborn.race.RaceManager.testLevel);
	}

	/** {@link #localBoostTicks}: the same pad test and timing as {@link #tickCourseEffects}, on the driving client. */
	private void tickLocalBoost() {
		if (!onCourseGround()) {
			localX = Double.NaN;
			localBoostTicks = 0;
			return;
		}
		double prevX = localX, prevY = localY, prevZ = localZ;
		double mx = Double.isNaN(prevX) ? 0.0D : getX() - prevX, mz = Double.isNaN(prevX) ? 0.0D : getZ() - prevZ;
		localX = getX();
		localY = getY();
		localZ = getZ();
		if (mx * mx + mz * mz > 0.001D && crossedBoostPad(prevX, prevY, prevZ)) {
			localBoostTicks = RaceScoring.boostTicks(intelligenceStat());
		}
		if (localBoostTicks > 0) {
			localBoostTicks--;
		}
	}

	/** Boost for the rider's input: the driving client's own timer, the synced flag anywhere else. */
	private boolean riderBoost() {
		return level().isClientSide ? localBoostTicks > 0 : boosting();
	}

	/** Sweep all three axes; half-block surfaces (and mud) put feet below the pad's block. */
	private boolean crossedBoostPad(double prevX, double prevY, double prevZ) {
		if (isBoostPadAt(getX(), getY(), getZ())) {
			return true;
		}
		if (Double.isNaN(prevX)) {
			return false;
		}
		Vec3 from = new Vec3(prevX, prevY, prevZ);
		Vec3 to = position();
		// A course transfer is not a drive across every block between the courses.
		if (from.distanceToSqr(to) > 40.0D * 40.0D) return false;
		return boostAlong(from, to) || boostAlong(from.add(0, 0.5D, 0), to.add(0, 0.5D, 0));
	}

	private boolean boostAlong(Vec3 from, Vec3 to) {
		// Sweep the actual footprint, not just its centre. A bird can touch a
		// diagonal strip with its feet while its centre narrowly misses the voxel.
		double radius = Math.max(0, getBbWidth() * 0.5D - 1.0E-7D);
		int reach = (int) Math.ceil(radius);
		return net.minecraft.world.level.BlockGetter.traverseBlocks(from, to, this,
				(bird, pos) -> {
					for (int x = -reach; x <= reach; x++) {
						for (int z = -reach; z <= reach; z++) {
							BlockPos pad = pos.offset(x, 0, z);
							if (!bird.isBoostPad(pad)) continue;
							var contact = new net.minecraft.world.phys.AABB(pad).inflate(radius, 0, radius);
							if (contact.contains(from) || contact.contains(to) || contact.clip(from, to).isPresent()) return Boolean.TRUE;
						}
					}
					return null;
				}, bird -> Boolean.FALSE);
	}

	private boolean isBoostPadAt(double x, double y, double z) {
		return isBoostPad(BlockPos.containing(x, y, z)) || isBoostPad(BlockPos.containing(x, y + 0.5D, z));
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

	/** Turn this bird into a Square AI racer: only the racer goal and floating. */
	public void installRacer(net.minecraft.world.entity.ai.goal.Goal racer) {
		this.racerTrack = racer instanceof tk.darrow.chocobosreborn.race.RacerGoal g ? g.track() : null;
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
			// Vanilla floats a mob up out of lava at the first touch (water only past the jump
			// threshold). A lava walker stands on the pool (canStandOnFluid) and dips its feet
			// into the surface: hopping there, it spent every tick in the air and crawled at air
			// speed. An AI Gold stood hopping in S_CITADEL's and S_KEEP's lava all race. It
			// floats like it does in water: only when it is actually under.
			if (color().lavaWalk() && isInLava() && getFluidHeight(FluidTags.LAVA) <= getFluidJumpThreshold()) {
				return isInWater() && getFluidHeight(FluidTags.WATER) > getFluidJumpThreshold();
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
		float catchup = RaceScoring.turnCatchup(cooperationStat());
		this.setYRot(Mth.rotLerp(catchup, this.getYRot(), player.getYRot()));
		this.setXRot(Mth.rotLerp(catchup, this.getXRot(), player.getXRot() * 0.5F));
		this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
        if (racing() && !raceHeld()) {
            if (level().isClientSide && isControlledByLocalInstance()) predictRaceFrame(player);
            else if (player instanceof ServerPlayer serverPlayer) drainRaceInputs(serverPlayer);
        }
		if (!level().isClientSide && !raceHeld() && raceInputs == null) {
			int st = stamina();
			int max = maxStamina();
			// in the air on a flier, sprint means "down", not dash (the client report already says so)
			boolean wantsDash = riderWantsDash(player);
			if (riderDashFresh > 0) {
				riderDashFresh--;
			}
			// Emptying the bar locks dash until it climbs back to 50. Holding the
			// key through that climb does not spend the points as they return.
			boolean locked = RaceScoring.stillDashLocked(dashLocked(), st);
			if (dashLocked() && !locked) {
				setDashLocked(false);
			}
			if (racing()) {
				setRaceDashFlag(wantsDash && !locked && st > 0);
			}
			if (wantsDash && !locked && st > 0) {
				boolean skip = RaceScoring.intelSkipsDashDrain(intelligenceStat(), tickCount, random.nextInt(100));
				int next = skip ? st : st - 1;
				setStamina(next);
				if (next <= 0) {
					setDashLocked(true);
				}
			} else {
				// Recover: quick when standing, slow while cruising. Do not clear the
				// rider's sprint flag here: that syncs to a guest a ping later and her
				// client keeps the dash without ever sending START_SPRINTING again.
				int gain = player.zza == 0.0F && player.xxa == 0.0F ? 2 : (tickCount % 3 == 0 ? 1 : 0);
				if (st < max && gain > 0) {
					setStamina(st + gain);
				}
			}
		}
		super.tickRidden(player, travel);
	}

	@Override
	protected Vec3 getRiddenInput(Player player, Vec3 travel) {
		if (raceHeld() || (level().isClientSide && racing() && (racePrediction == null || !racePrediction.canAdvance()))) {
			return Vec3.ZERO;
		}
		float strafe = player.xxa * RaceScoring.strafeMul(cooperationStat());
		float forward = player.zza;
		if (forward <= 0.0F) {
			forward *= 0.25F;
		}
		return new Vec3(strafe, 0.0D, forward);
	}

	@Override
	protected float getRiddenSpeed(Player player) {
		if (raceHeld() || (level().isClientSide && racing() && !frameReady)) return 0.0F;
		// Minecraft normalizes input vectors longer than one: speed bonuses must
		// scale acceleration here, not the directional input above.
		double mul = 1.0D;
		// Same signal as the drain: the dash key on the driving client, the
		// RiderDash report on the server. The latched sprint flag stays true
		// while W is held, which left the bar empty and pulsed dash speed.
		boolean wantsDash = level().isClientSide ? clientWantsDash(player) : riderWantsDash(player);
		if (predictingRace() ? frameDash : wantsDash && !dashLocked() && stamina() > 0) {
			mul *= RaceScoring.dashMul();
		} else if (predictingRace() ? frameEmpty : stamina() <= 0) {
			mul *= RaceScoring.emptyStaminaMul();
		}
		if (riderBoost()) {
			mul *= 1.0D + RaceScoring.boostPower(intelligenceStat());
		}
		if (onGround() && Square.isSquare(level()) && getBlockStateOn().is(Blocks.MUD)) {
			mul *= 1.0D + BOG_DRAG;   // a course bog: same x0.45 the AI gets (not the Overworld's mud)
		}
		ChocoboColor c = color();
		boolean water = isInWater() || (c.waterWalk() && level().getFluidState(blockPosition().below()).is(FluidTags.WATER))
				|| (c.lavaWalk() && level().getFluidState(blockPosition().below()).is(FluidTags.LAVA));
		// mountedCruise carries grade; training is applied exactly once.
		double cruise = RaceScoring.mountedCruise(c.landSpeed(), c.waterSpeed(), water, racing(), grade().getRank());
		double training = RaceScoring.speedTrainingMul(speedStat());
		return (float) (getAttributeValue(Attributes.MOVEMENT_SPEED) * mul * training * cruise / Math.max(0.05D, c.landSpeed()));
	}

	@Override
	public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
		if (level().isClientSide && !isControlledByLocalInstance()) {
			if (REMOTE_DISPLAY != null && REMOTE_DISPLAY.owns(getId())) {
				return;   // played back from its server frames (tick()): vanilla's untimed packets would only fight them
			}
			// How far the server's bird moved since its last position (the glide still under
			// way ends there), not how far this client's drawn bird is from it. The drawn bird
			// trails by the glide itself, so measured from it a bird over 1.35 blocks a tick
			// (A and S pace, a B dash) looked late on every packet: the glide grew a tick each
			// time up to 8, and the field was drawn up to ~11 blocks behind where it raced.
			double fromX = lerpSteps > 0 ? lerpX : getX(), fromY = lerpSteps > 0 ? lerpY : getY(), fromZ = lerpSteps > 0 ? lerpZ : getZ();
			double dx = x - fromX, dy = y - fromY, dz = z - fromZ;
			steps = RaceScoring.remoteGlideSteps(steps, dx * dx + dy * dy + dz * dz);
		}
		super.lerpTo(x, y, z, yRot, xRot, steps);
	}

	/** Every racing bird's position this tick, stamped with the tick, for the players near it (batched by RaceFrameSender). */
	private void broadcastRaceFrame() {
		if (!(level() instanceof net.minecraft.server.level.ServerLevel server)) {
			return;
		}
		tk.darrow.chocobosreborn.net.RaceFrameSender.add(server, new tk.darrow.chocobosreborn.net.RaceMovePayloads.Frame(getId(),
				(int) server.getGameTime(), getX(), getY(), getZ(), getYRot(), yBodyRot, onGround()), getControllingPassenger());
	}

	@Override
	public void travel(Vec3 travel) {
		if (level().isClientSide && !isControlledByLocalInstance()) {
			// The server stream places this bird. Local physics would run it ahead of
			// that stream, and the next position packet would yank it back.
			setDeltaMovement(Vec3.ZERO);
			// Vanilla feeds the gait from the end of travel(). Skipping it froze the
			// legs of every bird this client does not drive; the lerp still moves
			// the bird, so the stride follows that motion. A racing climber's stride
			// counts its climb too, so it runs up a ridge face instead of floating.
			calculateEntityAnimation(racing() && color().climb());
			return;
		}
		if (raceHeld()) {
			pendingJump = -1;
			flapTicks = 0;
			ridgeCarry = 0.0D;
			setDeltaMovement(Vec3.ZERO);
			contactSlow.clear();
			super.travel(Vec3.ZERO);
			return;
		}
		if (racing() || getControllingPassenger() instanceof Player) {
			// this side simulates the bird (the early return above takes remote birds); ridden birds
			// bump each other outside heats too, since vanilla never pushes a vehicle
			applyRacerContact();
		}
		RaceTrack.Feature suited = racing() ? suitedFeature() : null;
		if (suited != null) {
			setSpeed((float) (getSpeed() * RaceScoring.suitedPace(suited.type())));
		}
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
		ridgeClimbing = false;
		super.travel(travel);
		keepRidgePace();
	}

	/** Progress hint for {@link #onSuitedFeature}. */
	private double featureHint = -1.0D;

	/**
	 * On the direct line of a course feature this bird's colour suits: a Green on the ridge, a
	 * Blue on the water, a Gold on the lava. It runs there at {@link RaceScoring#suitedPace}
	 * (the bird is in its element), so the feature is a shortcut wherever it sits: a ridge's climb
	 * and drop cost ticks, and on a few courses the detour is barely longer than the direct line
	 * (ShortcutGameTests). Off the band (the detour) it is plain road.
	 */
	private RaceTrack.@org.jetbrains.annotations.Nullable Feature suitedFeature() {
		RaceTrack track = raceTrack() >= 0 ? RaceTrack.byId(raceTrack()) : racerTrack;
		if (track == null) {
			return null;
		}
		double t = track.progressAt(getX(), getZ(), featureHint);
		featureHint = t;
		RaceTrack.Feature f = track.featureAt(t);
		return f != null && f.terrain() && f.suits(color()) && Math.abs(track.laneAt(t, getX(), getZ())) <= RaceTrack.RIDGE_BAND_HALF
				? f : null;
	}

	/** Pace a racing climber hit its ridge face at (blocks a tick); 0 off a ridge. See {@link #keepRidgePace}. */
	private double ridgeCarry;
	/** This climber's horizontal pace at the end of its last tick clear of any wall. */
	private double ridgeRunPace;
	/** Ticks since this climber was last on the ridge face. */
	private int ridgeCarryTicks;
	/** Set by {@link #handleRelativeFrictionAndCalculateMovement} on a tick spent going up the ridge face. */
	private boolean ridgeClimbing;

	/**
	 * A racing climber bounds up its ridge face at {@link RaceScoring#RIDGE_CLIMB_LIFT}
	 * instead of vanilla's ladder 0.2 (about 8.5 ticks a block once gravity takes its share).
	 * At the ladder rate the ridge cost a Green more than the detour saved (Ahmi,
	 * 2026-10-02: "the climb ... usually costs the racer time"; ShortcutGameTests). The last
	 * push only reaches the top ({@link RaceScoring#ridgeLift}): at full lift a bird crested
	 * still rising and flew 20-odd ticks over the ridge, where it can neither steer nor dash.
	 */
	@Override
	public Vec3 handleRelativeFrictionAndCalculateMovement(Vec3 travel, float friction) {
		Vec3 v = super.handleRelativeFrictionAndCalculateMovement(travel, friction);
		if (racing() && v.y == 0.2D && horizontalCollision && onClimbable() && color().climb()) {
			ridgeClimbing = true;
			if (ridgeCarry <= 0.0D) {
				ridgeCarry = ridgeRunPace;
			}
			ridgeCarryTicks = 0;
			return new Vec3(v.x, RaceScoring.ridgeLift(ridgeTopY() - getY()), v.z);
		}
		return v;
	}

	/** Standing level on top of the ridge this climber is on (NaN with no known course). */
	private double ridgeTopY() {
		RaceTrack track = raceTrack() >= 0 ? RaceTrack.byId(raceTrack()) : racerTrack;
		if (track == null || climbHint < 0.0D) {
			return Double.NaN;
		}
		return track.groundY(climbHint) + track.ridgeHeight();
	}

	/**
	 * Over the ridge a climber keeps the pace it hit the face at: the face stops it dead, the
	 * ladder clamp holds it to 0.15 sideways, and the drop off the far end is air (a mob keeps
	 * 0.91 of its speed a tick there and barely accelerates). From the crest until it is back
	 * on the road past the ridge band, its pace in the air does not fall under that carry,
	 * the way it faces: the AI's heading follows its steering and a rider's the mouse, so a
	 * ridge on a bend is still taken round the bend (held along its own drift, the carry ran
	 * an A_AMMONITE Black straight off the course in the air). Runs on whichever side simulates
	 * the bird (the rider's client for a ridden bird, the server for the field), like the rest
	 * of travel.
	 */
	private void keepRidgePace() {
		Vec3 d = getDeltaMovement();
		double h = d.horizontalDistance();
		if (!racing() || !color().climb()) {
			ridgeCarry = 0.0D;
			return;
		}
		if (ridgeCarry <= 0.0D) {
			if (!horizontalCollision && onGround()) {
				// blocks it actually covered this tick: on the ground that is about twice the
				// velocity left after friction, so the velocity would carry only half the pace
				ridgeRunPace = Math.hypot(getX() - xo, getZ() - zo);
			}
			return;
		}
		if (ridgeClimbing) {
			return;
		}
		if (ridgeCarryTicks == 0 && d.y > 0.0D) {
			// first tick off the face: it is over the top, so stop rising and land on it
			d = new Vec3(d.x, 0.0D, d.z);
			setDeltaMovement(d);
		}
		if (++ridgeCarryTicks > RaceScoring.RIDGE_CARRY_TICKS || (onGround() && !onRidge())) {
			ridgeCarry = 0.0D;
			return;
		}
		// in the air only: on its feet the bird runs (and gets RaceScoring.SUITED_FEATURE_PACE);
		// in the air a mob keeps 0.91 of its speed a tick and barely accelerates, so the hop off
		// the face and the drop off the far end would crawl. Air velocity is what it covers next tick.
		if (!onGround() && h < ridgeCarry) {
			float yaw = getYRot() * Mth.DEG_TO_RAD;
			setDeltaMovement(-Mth.sin(yaw) * ridgeCarry, d.y, Mth.cos(yaw) * ridgeCarry);
		}
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

	/** In a race no higher than a hill step ({@link RaceScoring#stepHeight}); the same on the rider's client and the server. */
	@Override
	public float maxUpStep() {
		return RaceScoring.stepHeight(racing(), super.maxUpStep());
	}

	/**
	 * A rider's bird moves (its client's travel, the server's replay of the rider's packet)
	 * with the on-ground flag its footing gives, not the one its side's last move left: see
	 * {@link RaceScoring#riderStepGround}. The move itself sets the flag again as usual.
	 */
	@Override
	public void move(net.minecraft.world.entity.MoverType type, Vec3 movement) {
		if ((type == net.minecraft.world.entity.MoverType.SELF || type == net.minecraft.world.entity.MoverType.PLAYER)
				&& !noPhysics && getControllingPassenger() instanceof Player) {
			boolean ground = RaceScoring.riderStepGround(true, onGround(), hasFooting());
			if (ground != onGround()) {
				setOnGround(ground);
			}
		}
		if (type != net.minecraft.world.entity.MoverType.SELF) {
			restBox = null;
			super.move(type, movement);
			return;
		}
		if (replayRest(movement)) {
			return;
		}
		double x = getX(), y = getY(), z = getZ();
		float fly = flyDist, walk = walkDist, stepDist = moveDist;
		super.move(type, movement);
		noteRest(movement, x, y, z, fly, walk, stepDist);
	}

	// ------------------------------------------------------------ resting move

	/*
	 * A bird standing on the ground (penned, sitting, waiting for its owner) still moves every
	 * tick: gravity asks for 0.08 down and the full collision pass (every collision shape round a
	 * 1.75 x 3.25 box, then the supporting-block search) answers "nothing moves". When a full move
	 * from this exact box, with this exact request, ended where it began, on the ground, and the
	 * blocks it could have hit since are the very same states, the answer is known: the replay
	 * sets what that move sets and keeps every block hook it fires (landing, stepping on, standing
	 * inside, fire). A push, a rider, water, a broken floor or anything else that differs takes
	 * the full move again. Every twentieth tick takes it regardless.
	 */

	/** Full moves taken at least this often, whatever the replay would say. */
	private static final int REST_REFRESH_TICKS = 20;
	/** Box, request and support-layer states of the last full move that ended at rest; null if it did not. */
	private @Nullable net.minecraft.world.phys.AABB restBox;
	private double restDy;
	private net.minecraft.world.level.block.state.BlockState[] restStates = new net.minecraft.world.level.block.state.BlockState[0];
	private boolean restReplayEnabled = true;
	/** Moves answered by the replay (GameTests read it). */
	private long restReplays;

	/** GameTests compare the replay against the full move by turning it off on one bird. */
	public void setRestReplay(boolean on) {
		restReplayEnabled = on;
		restBox = null;
	}

	public long restReplays() {
		return restReplays;
	}

	/** Only an unridden, out-of-race bird asking to go straight down while it stands on the ground. */
	private boolean mayRest(Vec3 movement) {
		return restReplayEnabled && !level().isClientSide && !noPhysics
				&& movement.x == 0.0D && movement.z == 0.0D && movement.y < 0.0D
				&& onGround() && fallDistance == 0.0F && stuckSpeedMultiplier.lengthSqr() <= 1.0E-7D
				&& !isPassenger() && !isVehicle() && !racing() && !raceNpc() && raceTrack() < 0
				&& !isInWater() && !isInLava() && !isInFluidType() && !isInPowderSnow;
	}

	/** After a full move: remember it if it ended exactly where it began, on the ground. */
	private void noteRest(Vec3 movement, double x, double y, double z, float fly, float walk, float stepDist) {
		restBox = null;
		if (isRemoved() || !mayRest(movement) || getX() != x || getY() != y || getZ() != z
				|| flyDist != fly || walkDist != walk || moveDist != stepDist
				|| !verticalCollisionBelow || horizontalCollision) {
			return;
		}
		net.minecraft.world.phys.AABB box = getBoundingBox();
		if (!restNeighboursClear(box, movement) || !supportStates(box, movement.y, true)) {
			return;
		}
		restBox = box;
		restDy = movement.y;
	}

	/** Nothing but blocks could stop the bird: no boat, minecart or shell under it, no world border at hand. */
	private boolean restNeighboursClear(net.minecraft.world.phys.AABB box, Vec3 movement) {
		net.minecraft.world.phys.AABB swept = box.expandTowards(movement);
		return !level().getWorldBorder().isInsideCloseToBorder(this, swept)
				&& level().getEntityCollisions(this, swept).isEmpty();
	}

	/**
	 * The cells whose collision shapes a straight-down move (and the supporting-block search)
	 * can meet: the collision scan's columns, from its lowest layer up to the layer holding the
	 * box's floor. Shapes higher up start at or above the floor and never stop a fall. With
	 * {@code record} the states are stored (refused if one has a block entity or a shape that
	 * is not fixed by its state, or is a liquid this breed can stand on); otherwise compared.
	 */
	private boolean supportStates(net.minecraft.world.phys.AABB box, double dy, boolean record) {
		int x0 = Mth.floor(box.minX - 1.0E-7D) - 1, x1 = Mth.floor(box.maxX + 1.0E-7D) + 1;
		int z0 = Mth.floor(box.minZ - 1.0E-7D) - 1, z1 = Mth.floor(box.maxZ + 1.0E-7D) + 1;
		int y0 = Mth.floor(box.minY + dy - 1.0E-7D) - 1, y1 = Mth.floor(box.minY + 1.0E-7D);
		int n = (x1 - x0 + 1) * (z1 - z0 + 1) * (y1 - y0 + 1);
		if (record && restStates.length < n) {
			restStates = new net.minecraft.world.level.block.state.BlockState[n];
		}
		ChocoboColor c = record ? color() : null;
		boolean standsOnLiquid = c != null && (c.waterWalk() || c.lavaWalk());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int i = 0;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				net.minecraft.world.level.BlockGetter chunk = level().getChunkForCollisions(x >> 4, z >> 4);
				for (int y = y0; y <= y1; y++, i++) {
					net.minecraft.world.level.block.state.BlockState st = chunk == null ? null : chunk.getBlockState(pos.set(x, y, z));
					if (record) {
						if (st != null && (st.hasBlockEntity() || st.getBlock().hasDynamicShape()
								|| (standsOnLiquid && st.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock))) {
							return false;
						}
						restStates[i] = st;
					} else if (restStates[i] != st) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * The full move's answer, when it is known: see the note above. Mirrors {@code Entity.move}
	 * for a request the collision pass cuts to nothing on the ground.
	 */
	private boolean replayRest(Vec3 movement) {
		net.minecraft.world.phys.AABB box = restBox;
		if (box == null || movement.y != restDy || (tickCount + getId()) % REST_REFRESH_TICKS == 0 || !mayRest(movement)) {
			return false;
		}
		net.minecraft.world.phys.AABB now = getBoundingBox();
		if ((now != box && !now.equals(box)) || !supportStates(box, movement.y, false) || !restNeighboursClear(box, movement)) {
			return false;
		}
		restReplays++;
		wasOnFire = isOnFire();
		// the request was straight down and met the floor at once
		horizontalCollision = false;
		minorHorizontalCollision = false;
		verticalCollision = true;
		verticalCollisionBelow = true;
		// on the ground already, on the same supporting block (same states, same box)
		BlockPos on = getOnPosLegacy();
		net.minecraft.world.level.block.state.BlockState state = level().getBlockState(on);
		checkFallDamage(0.0D, true, state, on);
		if (isRemoved()) {
			return true;
		}
		net.minecraft.world.level.block.Block block = state.getBlock();
		block.updateEntityAfterFallOn(level(), this);
		if (onGround()) {
			block.stepOn(level(), on, state, this);
		}
		// no distance walked: no step sound, no step vibration (the full move that set this up
		// already moved the next step past the distance walked)
		tryCheckInsideBlocks();
		float f = getBlockSpeedFactor();
		setDeltaMovement(getDeltaMovement().multiply(f, 1.0D, f));
		if (getRemainingFireTicks() != -getFireImmuneTicks() || wasOnFire || isOnFire()) {
			restFireTail();
		}
		return true;
	}

	/** {@code Entity.move}'s closing fire bookkeeping, for a bird that is or was alight. */
	private void restFireTail() {
		boolean wet = isInPowderSnow || isInWaterRainOrBubble() || isInFluidType((type, height) -> canFluidExtinguish(type));
		if (level().getBlockStatesIfLoaded(getBoundingBox().deflate(1.0E-6D))
				.noneMatch(s -> s.is(BlockTags.FIRE) || s.is(Blocks.LAVA))) {
			if (getRemainingFireTicks() <= 0) {
				setRemainingFireTicks(-getFireImmuneTicks());
			}
			if (wasOnFire && wet) {
				playEntityOnFireExtinguishedSound();
			}
		}
		if (isOnFire() && wet) {
			setRemainingFireTicks(-getFireImmuneTicks());
		}
	}

	/** Something solid (or water a water bird stands on) within {@link RaceScoring#FOOTING_PROBE} under the feet. */
	public boolean hasFooting() {
		net.minecraft.world.phys.AABB box = getBoundingBox();
		return !level().noCollision(this, new net.minecraft.world.phys.AABB(box.minX, box.minY - RaceScoring.FOOTING_PROBE,
				box.minZ, box.maxX, box.minY, box.maxZ));
	}

	/**
	 * The server rejected the rider's last move and sent the bird back
	 * ({@code ClientboundMoveVehiclePacket}; vanilla only sets the position). Take the rest
	 * of the server's state too: it holds a rider's bird still (no velocity) and has no
	 * leftover on-ground flag. Keeping this side's momentum and flag replayed the same
	 * rejected move from the same spot every tick, and the rider froze there.
	 */
	public void adoptVehicleCorrection() {
		setDeltaMovement(Vec3.ZERO);
		ridgeCarry = 0.0D;
		setOnGround(hasFooting());
		resetFallDistance();
		localX = Double.NaN;   // the snap back is not a ride across a boost pad
	}

	@Override
	public boolean onClimbable() {
		boolean climbs = color().climb(), racing = racing();
		// onRidge is only worked out for a climber in a race that is pressed on something
		return RaceScoring.mayClimb(climbs, horizontalCollision, racing, climbs && racing && horizontalCollision && onRidge())
				|| super.onClimbable();
	}

	/** Progress hint for {@link #onRidge}, so a climber pressed on a wall does not scan the whole lap each tick. */
	private double climbHint = -1.0D;
	/** The course of the racer goal installed on a field bird ({@link #installRacer}); server side only. */
	private @org.jetbrains.annotations.Nullable RaceTrack racerTrack;

	/**
	 * On its course's ridge band. A rider's bird reads its synced track (so the rider's client
	 * and the server agree); a field bird is never given one (RaceSession sets it on the
	 * riders' birds only), so it reads its racer goal's. byId(-1) is C_MEADOW, which has no
	 * ridge: reading that left every AI climber (Teioh included) pressed on the ridge face.
	 * A racing bird with no known course climbs as it always did.
	 */
	private boolean onRidge() {
		RaceTrack track = raceTrack() >= 0 ? RaceTrack.byId(raceTrack()) : racerTrack;
		if (track == null) {
			return true;
		}
		double t = track.progressAt(getX(), getZ(), climbHint);
		climbHint = t;
		return track.ridgeBandAt(t, track.laneAt(t, getX(), getZ()));
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
		pendingJump = Mth.clamp(power, 0, 100);
	}

	@Override
	public void handleStartJump(int power) {
		pendingJump = Mth.clamp(power, 0, 100);
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
			if (tickCount % 10 == 0) {
				tk.darrow.chocobosreborn.race.FollowAcross.towardOwner(this);
			}
			// A nut stays until they hatch; keep the love window open so a missed path
			// does not lock Carob/Zeio forever.
			if (fedNut() != ChocoboNut.NONE && !isInLove() && canFallInLove()) {
				setInLove(null);
			}
			if (townBird() && isBaby() && getAge() > -6000) {
				setAge(-24000);   // the nursery's chicks stay chicks
			}
			int stage = computeStage();
			if (stage != growthStage()) {
				this.entityData.set(DATA_STAGE, stage);
				refreshDimensions();
			}
			if (!(getControllingPassenger() instanceof Player)) {
				riderDashLinked = false;
				riderDashFresh = 0;
				dashRider = null;
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
			tickFeathers();
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

	/** Minimum and spread of the gap between shed feathers: one every 5 to 10 minutes. */
	public static final int FEATHER_MIN_TICKS = 6000, FEATHER_SPREAD_TICKS = 6000;

	/** Your own adult birds; not chicks, wild birds, the Square's town birds or anyone mid-heat. */
	public boolean shedsFeathers() {
		return isTame() && !isBaby() && !raceNpc() && !townBird() && !racing() && raceTrack() < 0;
	}

	/** A tame adult drops a feather now and then, the way a chicken lays. */
	private void tickFeathers() {
		if (!shedsFeathers()) {
			return;
		}
		if (featherTicks < 0) {
			featherTicks = FEATHER_MIN_TICKS + random.nextInt(FEATHER_SPREAD_TICKS + 1);
		}
		if (--featherTicks <= 0) {
			spawnAtLocation(new ItemStack(net.minecraft.world.item.Items.FEATHER));
			playSound(net.minecraft.sounds.SoundEvents.CHICKEN_EGG, 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
			gameEvent(net.minecraft.world.level.gameevent.GameEvent.ENTITY_PLACE);
			featherTicks = FEATHER_MIN_TICKS + random.nextInt(FEATHER_SPREAD_TICKS + 1);
		}
	}

	/** Shed a feather on the next tick (GameTests). */
	public void featherDueNow() {
		featherTicks = 1;
	}

	@Override
	public void tick() {
		if (level().isClientSide && REMOTE_DISPLAY != null && !isControlledByLocalInstance() && REMOTE_DISPLAY.place(this)) {
			lerpSteps = 0;   // its old and new positions are this tick's two frames: drawn between them, never lerped
		}
		super.tick();
		trackContactVelocity();
		if (!level().isClientSide && racing() && SEND_RACE_FRAMES) {
			broadcastRaceFrame();
		}
		if (level().isClientSide) {
            if (!racing()) { racePrediction = null; frameReady = false; }
			if (getControllingPassenger() instanceof Player rider && isControlledByLocalInstance()) {
				tickLocalBoost();
				boolean dash = clientWantsDash(rider);
				if (!racing() && (!clientDashLinked || dash != clientDashSent || (dash && tickCount % 5 == 0))) {
					net.neoforged.neoforge.network.PacketDistributor.sendToServer(
							new tk.darrow.chocobosreborn.net.RacePayloads.RiderDash(getId(), dash));
					clientDashSent = dash;
					clientDashLinked = true;
				}
			} else {
				localX = Double.NaN;
				localBoostTicks = 0;
				clientDashSent = false;
				clientDashLinked = false;
			}
		}
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
