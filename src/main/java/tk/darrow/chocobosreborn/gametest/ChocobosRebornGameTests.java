package tk.darrow.chocobosreborn.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.breed.ChocoboColor;
import tk.darrow.chocobosreborn.breed.ChocoboGrade;
import tk.darrow.chocobosreborn.breed.ChocoboGreen;
import tk.darrow.chocobosreborn.breed.ChocoboNut;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.entity.ModEntities;
import tk.darrow.chocobosreborn.item.ModItems;
import tk.darrow.chocobosreborn.race.FollowAcross;
import tk.darrow.chocobosreborn.race.RaceCourseLayout;
import tk.darrow.chocobosreborn.race.RacePoint;
import tk.darrow.chocobosreborn.race.RaceManager;
import tk.darrow.chocobosreborn.race.RaceSession;
import tk.darrow.chocobosreborn.race.RaceScoring;
import tk.darrow.chocobosreborn.race.RaceTrack;
import tk.darrow.chocobosreborn.race.Square;
import tk.darrow.chocobosreborn.race.SquareBuilder;

/**
 * Headless in-game checks (`gradlew runGameTestServer` / the `verification` run).
 * They exercise real entities, the datapack, the Square dimension and a full heat.
 */
@GameTestHolder(ChocobosReborn.MOD_ID)
@PrefixGameTestTemplate(false)
public class ChocobosRebornGameTests {
	private static final String EMPTY = "empty";

	private static ChocoboEntity spawnAdult(GameTestHelper helper, BlockPos pos, boolean male, ChocoboColor color) {
		ChocoboEntity bird = helper.spawn(ModEntities.CHOCOBO.get(), pos);
		bird.setColor(color);
		bird.setMale(male);
		bird.setAge(0);
		bird.setGrade(ChocoboGrade.GOOD);
		return bird;
	}

	/** Feeds of one green recorded on the bird (its {@code GreensFed} save data). */
	private static int fed(ChocoboEntity bird, ChocoboGreen green) {
		return bird.saveWithoutId(new CompoundTag()).getIntArray("GreensFed")[green.ordinal()];
	}

	private static Player owner(GameTestHelper helper, ChocoboEntity... birds) {
		// A ServerPlayer that is actually in the level, so isOwnedBy() resolves.
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		for (ChocoboEntity b : birds) {
			b.tame(p);
			b.setOrderedToSit(false);
		}
		return p;
	}

	@GameTest(template = EMPTY)
	public static void followStayWanderCommands(GameTestHelper helper) {
		ChocoboEntity bird = spawnAdult(helper, new BlockPos(2, 1, 2), true, ChocoboColor.YELLOW);
		Player p = owner(helper, bird);
		helper.assertTrue(bird.command() == ChocoboEntity.Command.FOLLOW, "tame bird starts on Follow");
		bird.giveCommand(ChocoboEntity.Command.WANDER, p);
		helper.runAtTickTime(3, () -> {
			helper.assertTrue(bird.command() == ChocoboEntity.Command.WANDER, "Wander");
			helper.assertTrue(bird.hasRestriction() && bird.getRestrictRadius() == ChocoboEntity.WANDER_RANGE,
					"a wandering bird keeps to its spot");
			// Stay on top of Wander, then stand up: back to wandering, not following
			bird.giveCommand(ChocoboEntity.Command.STAY, p);
			helper.assertTrue(bird.isOrderedToSit() && bird.command() == ChocoboEntity.Command.STAY, "Stay sits");
			bird.setOrderedToSit(false);
			helper.assertTrue(bird.command() == ChocoboEntity.Command.WANDER, "standing up resumes Wander");
			CompoundTag saved = bird.saveWithoutId(new CompoundTag());
			helper.assertTrue(saved.getBoolean("Wander") && saved.contains("WanderHome"), "Wander is saved");
			bird.giveCommand(ChocoboEntity.Command.FOLLOW, p);
			helper.assertTrue(bird.command() == ChocoboEntity.Command.FOLLOW && !bird.hasRestriction(),
					"Follow drops the wander range");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void tameFollowsAcrossDimensions(GameTestHelper helper) {
		ServerLevel here = helper.getLevel();
		ServerLevel there = here.getServer().getLevel(Level.NETHER);
		helper.assertTrue(there != null && there != here, "the nether is a second dimension");
		for (int cx = -1; cx <= 1; cx++) {
			for (int cz = -1; cz <= 1; cz++) {
				there.getChunk(cx, cz);
				there.setChunkForced(cx, cz, true);
			}
		}
		BlockPos pad = new BlockPos(0, 79, 0);
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				there.setBlockAndUpdate(pad.offset(x, 0, z), Blocks.STONE.defaultBlockState());
				for (int y = 1; y <= 5; y++) {
					there.setBlockAndUpdate(pad.offset(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		ChocoboEntity follow = spawnAdult(helper, new BlockPos(2, 1, 2), true, ChocoboColor.YELLOW);
		ChocoboEntity stay = spawnAdult(helper, new BlockPos(4, 1, 2), false, ChocoboColor.YELLOW);
		ChocoboEntity wander = spawnAdult(helper, new BlockPos(6, 1, 2), true, ChocoboColor.GREEN);
		ChocoboEntity racer = spawnAdult(helper, new BlockPos(3, 1, 4), false, ChocoboColor.BLUE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 back = player.position();
		follow.befriend(player);
		stay.tame(player);
		stay.giveCommand(ChocoboEntity.Command.STAY, player);
		wander.tame(player);
		wander.giveCommand(ChocoboEntity.Command.WANDER, player);
		racer.tame(player);
		racer.giveCommand(ChocoboEntity.Command.FOLLOW, player);
		racer.setRacing(true);
		helper.assertTrue(follow.command() == ChocoboEntity.Command.FOLLOW && !follow.isOrderedToSit(),
				"a new tame stands and follows");
		helper.assertTrue(FollowAcross.wants(follow) && !FollowAcross.wants(stay) && !FollowAcross.wants(wander)
				&& !FollowAcross.wants(racer), "only Follow, and not a racer, crosses");
		net.minecraft.world.entity.Entity arrived = Square.teleport(player, there, new Vec3(0.5D, 80.0D, 0.5D), 0.0F);
		helper.assertTrue(arrived instanceof ServerPlayer moved && moved.serverLevel() == there, "owner entered the nether");
		ServerPlayer owner = (ServerPlayer) arrived;
		// changeDimension queues the bird; a forced chunk lists it once it is ticking. That
		// takes a variable number of ticks (other tests laying whole courses at the same
		// moment make the server catch up in a burst), so wait for it rather than a fixed tick.
		helper.startSequence().thenWaitUntil(() -> {
			Entity live = there.getEntity(follow.getUUID());
			helper.assertTrue(live instanceof ChocoboEntity cb && cb.level() == there
							&& cb.command() == ChocoboEntity.Command.FOLLOW,
					"Follow crossed into the nether");
		}).thenExecute(() -> {
			Entity live = there.getEntity(follow.getUUID());
			helper.assertTrue(!stay.isRemoved() && stay.level() == here && stay.command() == ChocoboEntity.Command.STAY,
					"Stay stayed behind");
			helper.assertTrue(!wander.isRemoved() && wander.level() == here && wander.command() == ChocoboEntity.Command.WANDER,
					"Wander stayed behind");
			helper.assertTrue(!racer.isRemoved() && racer.level() == here && racer.racing(), "a racing bird stays on the course");
			if (live != null) {
				live.discard();
			}
			for (int cx = -1; cx <= 1; cx++) {
				for (int cz = -1; cz <= 1; cz++) {
					there.setChunkForced(cx, cz, false);
				}
			}
			Square.teleport(owner, here, back, owner.getYRot());
		}).thenSucceed();
	}

	@GameTest(template = EMPTY)
	public static void chocoboSpawnsAndGrows(GameTestHelper helper) {
		ChocoboEntity chick = helper.spawn(ModEntities.CHOCOBO.get(), new BlockPos(2, 1, 2));
		chick.setAge(-24000);
		helper.runAtTickTime(2, () -> {
			helper.assertTrue(chick.isBaby(), "chick should be a baby");
			helper.assertTrue(chick.growthStage() == 0, "stage 0 at hatch, got " + chick.growthStage());
			helper.assertTrue(chick.getAgeScale() < 0.3F, "chick scale");
			chick.setAge(-10000);
		});
		helper.runAtTickTime(6, () -> {
			helper.assertTrue(chick.growthStage() == 1, "stage 1 at -10000, got " + chick.growthStage());
			chick.setAge(0);
		});
		helper.runAtTickTime(10, () -> {
			helper.assertTrue(chick.growthStage() == 3, "adult at age 0");
			helper.assertTrue(Math.abs(chick.getBbHeight() - ChocoboEntity.ADULT_H) < 0.01F,
					"adult hitbox " + chick.getBbHeight());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY)
	public static void greensTrainAndSate(GameTestHelper helper) {
		ChocoboEntity bird = spawnAdult(helper, new BlockPos(2, 1, 2), true, ChocoboColor.YELLOW);
		Player p = owner(helper, bird);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.KRAKKA_GREEN.get(), 64));
		// survival: one feed lands and is eaten, the next waits out the training cooldown
		bird.mobInteract(p, InteractionHand.MAIN_HAND);
		bird.mobInteract(p, InteractionHand.MAIN_HAND);
		helper.assertTrue(p.getMainHandItem().getCount() == 63, "cooldown after a feed, hand=" + p.getMainHandItem().getCount());
		helper.assertTrue(fed(bird, ChocoboGreen.KRAKKA) == 1, "one krakka fed");
		// creative skips the cooldown: sated after 40, the rest refused
		p.getAbilities().instabuild = true;
		for (int i = 0; i < 45; i++) {
			bird.mobInteract(p, InteractionHand.MAIN_HAND);
		}
		helper.assertTrue(bird.trainedIntelligence() > 0, "krakka trains intelligence");
		helper.assertTrue(bird.trainedIntelligence() <= 100, "training capped");
		helper.assertTrue(fed(bird, ChocoboGreen.KRAKKA) == 40, "sated at 40 feeds, fed=" + fed(bird, ChocoboGreen.KRAKKA));
		helper.succeed();
	}

	/** A bird that cannot take a training green still eats one to heal, and only when hurt. */
	@GameTest(template = EMPTY)
	public static void fullBirdHealsFromGreens(GameTestHelper helper) {
		ChocoboEntity bird = spawnAdult(helper, new BlockPos(2, 1, 2), true, ChocoboColor.YELLOW);
		Player p = owner(helper, bird);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.KRAKKA_GREEN.get(), 64));
		bird.mobInteract(p, InteractionHand.MAIN_HAND);   // a training feed: now digesting
		int trained = bird.trainedIntelligence();
		helper.assertTrue(p.getMainHandItem().getCount() == 63, "first green trains");
		// digesting and at full health: refused, nothing eaten
		bird.mobInteract(p, InteractionHand.MAIN_HAND);
		helper.assertTrue(p.getMainHandItem().getCount() == 63, "a full, healthy bird refuses");
		// digesting and hurt: eats it and heals, learns nothing
		bird.setHealth(bird.getMaxHealth() - 10.0F);
		bird.mobInteract(p, InteractionHand.MAIN_HAND);
		helper.assertTrue(p.getMainHandItem().getCount() == 62, "a hurt, full bird eats the green");
		helper.assertTrue(bird.getHealth() > bird.getMaxHealth() - 10.0F, "and heals: " + bird.getHealth());
		helper.assertTrue(bird.trainedIntelligence() == trained && fed(bird, ChocoboGreen.KRAKKA) == 1,
				"but trains nothing");
		// sated (creative skips the digest wait) and hurt: still heals
		p.getAbilities().instabuild = true;
		for (int i = 0; i < 45; i++) {
			bird.setHealth(bird.getMaxHealth());
			bird.mobInteract(p, InteractionHand.MAIN_HAND);
		}
		helper.assertTrue(fed(bird, ChocoboGreen.KRAKKA) == 40, "sated");
		bird.setHealth(5.0F);
		bird.mobInteract(p, InteractionHand.MAIN_HAND);
		helper.assertTrue(bird.getHealth() > 5.0F && fed(bird, ChocoboGreen.KRAKKA) == 40, "a sated bird heals from a green");
		helper.succeed();
	}

	/** Tame adults shed feathers; wild birds and chicks do not. Brushing your bird frees one. */
	@GameTest(template = EMPTY, timeoutTicks = 40)
	public static void birdsShedAndBrushFeathers(GameTestHelper helper) {
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		ChocoboEntity tame = spawnAdult(helper, new BlockPos(1, 1, 1), true, ChocoboColor.YELLOW);
		ChocoboEntity wild = spawnAdult(helper, new BlockPos(7, 1, 1), true, ChocoboColor.YELLOW);
		ChocoboEntity chick = spawnAdult(helper, new BlockPos(7, 1, 7), true, ChocoboColor.YELLOW);
		ChocoboEntity brushed = spawnAdult(helper, new BlockPos(1, 1, 7), true, ChocoboColor.YELLOW);
		Player p = owner(helper, tame, chick, brushed);
		chick.setAge(-24000);
		for (ChocoboEntity b : new ChocoboEntity[]{tame, wild, chick, brushed}) {
			b.setNoAi(true);
		}
		tame.featherDueNow();
		wild.featherDueNow();
		chick.featherDueNow();
		helper.assertTrue(tame.shedsFeathers() && !wild.shedsFeathers() && !chick.shedsFeathers(), "only tame adults shed");
		ItemStack brush = new ItemStack(net.minecraft.world.item.Items.BRUSH);
		p.setItemInHand(InteractionHand.MAIN_HAND, brush);
		brushed.mobInteract(p, InteractionHand.MAIN_HAND);
		helper.assertTrue(brush.getDamageValue() == 16, "a brush stroke costs 16, got " + brush.getDamageValue());
		helper.runAtTickTime(5, () -> {
			helper.assertItemEntityCountIs(net.minecraft.world.item.Items.FEATHER, new BlockPos(1, 1, 1), 2.5D, 1);
			helper.assertItemEntityCountIs(net.minecraft.world.item.Items.FEATHER, new BlockPos(1, 1, 7), 2.5D, 1);
			helper.assertItemEntityCountIs(net.minecraft.world.item.Items.FEATHER, new BlockPos(7, 1, 1), 2.5D, 0);
			helper.assertItemEntityCountIs(net.minecraft.world.item.Items.FEATHER, new BlockPos(7, 1, 7), 2.5D, 0);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY)
	public static void nutMatesAndHatchesOwnedChick(GameTestHelper helper) {
		ChocoboEntity a = spawnAdult(helper, new BlockPos(1, 1, 1), true, ChocoboColor.YELLOW);
		ChocoboEntity b = spawnAdult(helper, new BlockPos(3, 1, 3), false, ChocoboColor.YELLOW);
		Player p = owner(helper, a, b);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PEPIO_NUT.get(), 2));
		a.mobInteract(p, InteractionHand.MAIN_HAND);
		b.mobInteract(p, InteractionHand.MAIN_HAND);
		helper.assertTrue(a.fedNut() == ChocoboNut.PEPIO, "nut recorded");
		helper.assertTrue(a.isInLove() && b.isInLove(), "both in love");
		helper.assertTrue(a.canMate(b), "opposite sexes with nuts can mate");
		AgeableMob chick = a.getBreedOffspring(helper.getLevel(), b);
		helper.assertTrue(chick instanceof ChocoboEntity, "chick created");
		ChocoboEntity c = (ChocoboEntity) chick;
		helper.assertTrue(c.isBaby(), "chick is baby");
		helper.assertTrue(c.color() == ChocoboColor.YELLOW, "plain nut keeps yellow");
		helper.assertTrue(c.isTame() && p.getUUID().equals(c.getOwnerUUID()), "chick owned");
		helper.assertTrue(c.geneSpeed() >= 1, "pepio talent is a born stat");
		helper.assertTrue(a.fedNut() == ChocoboNut.NONE, "nut consumed");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void sameSexCannotMate(GameTestHelper helper) {
		ChocoboEntity a = spawnAdult(helper, new BlockPos(1, 1, 1), true, ChocoboColor.YELLOW);
		ChocoboEntity b = spawnAdult(helper, new BlockPos(3, 1, 3), true, ChocoboColor.YELLOW);
		Player p = owner(helper, a, b);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CAROB_NUT.get(), 2));
		a.mobInteract(p, InteractionHand.MAIN_HAND);
		b.mobInteract(p, InteractionHand.MAIN_HAND);
		helper.assertFalse(a.canMate(b), "two males must not mate");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void goldNeedsZeio(GameTestHelper helper) {
		ChocoboEntity black = spawnAdult(helper, new BlockPos(1, 1, 1), true, ChocoboColor.BLACK);
		ChocoboEntity yellow = spawnAdult(helper, new BlockPos(3, 1, 3), false, ChocoboColor.YELLOW);
		yellow.setGrade(ChocoboGrade.WONDERFUL);
		Player p = owner(helper, black, yellow);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CAROB_NUT.get(), 2));
		black.mobInteract(p, InteractionHand.MAIN_HAND);
		yellow.mobInteract(p, InteractionHand.MAIN_HAND);
		for (int i = 0; i < 20; i++) {
			// re-arm the nut each try (offspring clears it)
			black.mobInteract(p, InteractionHand.MAIN_HAND);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CAROB_NUT.get(), 2));
			AgeableMob c = black.getBreedOffspring(helper.getLevel(), yellow);
			helper.assertTrue(c instanceof ChocoboEntity ce && ce.color() != ChocoboColor.GOLD, "no Gold without Zeio");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void saddleRideAndControl(GameTestHelper helper) {
		ChocoboEntity bird = spawnAdult(helper, new BlockPos(2, 1, 2), true, ChocoboColor.YELLOW);
		Player p = owner(helper, bird);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SADDLE.get()));
		bird.mobInteract(p, InteractionHand.MAIN_HAND);
		helper.assertTrue(bird.saddled(), "saddle goes on");
		helper.assertTrue(p.getMainHandItem().isEmpty(), "saddle consumed");
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		bird.mobInteract(p, InteractionHand.MAIN_HAND);
		helper.runAtTickTime(2, () -> {
			helper.assertTrue(p.getVehicle() == bird, "player mounted");
			helper.assertTrue(bird.getControllingPassenger() == p, "player controls the bird");
			helper.assertTrue(bird.canSprint(), "sprint dash allowed");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY)
	public static void vehicleReplayCanLandOnStepWithoutIgnoringWalls(GameTestHelper helper) {
		// The client falls, touches the lower floor and steps up in one tick.
		// Its resulting packet is upward even though the server still sees it airborne.
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
		ChocoboEntity bird = spawnAdult(helper, new BlockPos(1, 1, 2), true, ChocoboColor.GOLD);
		Vec3 start = helper.absoluteVec(new Vec3(1.5, 1.5424, 2.5));
		bird.setPos(start);
		bird.setOnGround(false);
		Vec3 replay = new Vec3(2.0, 2.0 - 1.5424 - 1.0E-6, 0);
		bird.move(net.minecraft.world.entity.MoverType.PLAYER, replay);
		helper.assertTrue(bird.getX() < start.x + 1.75, "native epsilon reproduces the landing rejection");
		bird.setPos(start);
		bird.setOnGround(false);
		Vec3 corrected = new Vec3(replay.x, RaceScoring.vehicleValidationY(replay.y), replay.z);
		bird.move(net.minecraft.world.entity.MoverType.PLAYER, corrected);
		helper.assertTrue(Math.abs(bird.getX() - (start.x + 2)) < 0.0001, "upward replay reaches the valid landing");
		for (int y = 2; y <= 6; y++) helper.setBlock(new BlockPos(3, y, 2), Blocks.STONE);
		bird.setPos(start);
		bird.setOnGround(false);
		bird.move(net.minecraft.world.entity.MoverType.PLAYER, corrected);
		helper.assertTrue(bird.getX() < start.x + 1.75, "solid walls still block the move");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void boostSweepFindsPadAboveMud(GameTestHelper helper) {
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.MUD);
		helper.setBlock(new BlockPos(3, 2, 2), tk.darrow.chocobosreborn.block.ModBlocks.BOOST_PAD.get());
		ChocoboEntity bird = spawnAdult(helper, new BlockPos(1, 2, 2), true, ChocoboColor.GOLD);
		Vec3 from = helper.absoluteVec(new Vec3(1.5, 1.9375, 2.5));
		Vec3 to = helper.absoluteVec(new Vec3(5.5, 1.9375, 2.5));
		bird.setPos(to);
		try {
			var sweep = ChocoboEntity.class.getDeclaredMethod("crossedBoostPad", double.class, double.class, double.class);
			sweep.setAccessible(true);
			helper.assertTrue((boolean) sweep.invoke(bird, from.x, from.y, from.z), "fast rider detects a pad even with feet sunk into mud");
			helper.setBlock(new BlockPos(3, 1, 2), Blocks.OAK_SLAB);
			bird.setPos(to.add(0, -0.4375, 0));
			helper.assertTrue((boolean) sweep.invoke(bird, from.x, from.y - 0.4375, from.z), "pad above a half slab is detected too");
			bird.setPos(to.add(0, 2, 0));
			helper.assertFalse((boolean) sweep.invoke(bird, from.x, from.y + 2, from.z), "flying above the pad does not trigger it");
			Vec3 cornerFrom = helper.absoluteVec(new Vec3(1.5, 2, 1.751));
			Vec3 cornerTo = helper.absoluteVec(new Vec3(5.5, 2, 2.151));
			bird.setPos(cornerTo);
			helper.assertTrue((boolean) sweep.invoke(bird, cornerFrom.x, cornerFrom.y, cornerFrom.z), "a thin diagonal pad crossing cannot fall between samples");
			helper.assertFalse((boolean) sweep.invoke(bird, cornerFrom.x - 1000, cornerFrom.y, cornerFrom.z), "course transfers do not probe a thousand-block path");
			double radius = bird.getBbWidth() / 2.0;
			Vec3 edgeFrom = helper.absoluteVec(new Vec3(1.5, 2, 2 - radius + 0.02));
			Vec3 edgeTo = helper.absoluteVec(new Vec3(5.5, 2, 2 - radius + 0.02));
			bird.setPos(edgeTo);
			helper.assertTrue((boolean) sweep.invoke(bird, edgeFrom.x, edgeFrom.y, edgeFrom.z), "the edge of the physical footprint crossing a strip triggers it");
			edgeFrom = edgeFrom.add(0, 0, -0.04);
			edgeTo = edgeTo.add(0, 0, -0.04);
			bird.setPos(edgeTo);
			helper.assertFalse((boolean) sweep.invoke(bird, edgeFrom.x, edgeFrom.y, edgeFrom.z), "nearby pads outside the actual footprint do not trigger");
		} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
		helper.succeed();
	}

	@GameTest(template = EMPTY, batch = "vehicle_packets")
	public static void queuedVehicleStepsKeepCollisionAndTeleportChecks(GameTestHelper helper) {
		for (int x = 0; x <= 80; x++) for (int z = 0; z <= 4; z++) {
			helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			for (int y = 1; y <= 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
		}
		ChocoboEntity bird = spawnAdult(helper, new BlockPos(2, 1, 2), true, ChocoboColor.YELLOW);
		ServerPlayer rider = (ServerPlayer) owner(helper, bird);
		bird.setSaddledForPreview(true);
		rider.startRiding(bird, true);
		// Mounting also sends a teleport; a real client acknowledges it before moving.
		try {
			var teleport = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");
			teleport.setAccessible(true);
			rider.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(teleport.getInt(rider.connection)));
		} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
		helper.assertTrue(bird.getControllingPassenger() == rider, "fixture rider controls the bird");
		// Establish the same baselines as connection.tick without doTick moving the
		// just-created mock player back to its distant login coordinates.
		try {
			var type = net.minecraft.server.network.ServerGamePacketListenerImpl.class;
			var lastVehicle = type.getDeclaredField("lastVehicle");
			lastVehicle.setAccessible(true);
			lastVehicle.set(rider.connection, bird);
			for (String prefix : new String[]{"vehicleFirstGood", "vehicleLastGood"}) {
				for (String axis : new String[]{"X", "Y", "Z"}) {
					var field = type.getDeclaredField(prefix + axis);
					field.setAccessible(true);
					field.setDouble(rider.connection, axis.equals("X") ? bird.getX() : axis.equals("Y") ? bird.getY() : bird.getZ());
				}
			}
		} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
		double start = bird.getX();
		for (int i = 1; i <= 30; i++) {
			vehiclePacket(rider, bird, start + i * 2);
			helper.assertTrue(Math.abs(bird.getX() - (start + i * 2)) < 1e-5, "queued step " + i + " accepted without a server tick between packets; x=" + bird.getX() + " start=" + start);
		}
		double accepted = bird.getX();
		vehiclePacket(rider, bird, accepted + 12);
		helper.assertTrue(Math.abs(bird.getX() - accepted) < 1e-5, "one oversized movement is still rejected");
		for (int y = 1; y <= 5; y++) for (int z = 0; z <= 4; z++) {
			helper.setBlock(new BlockPos(65, y, z), Blocks.STONE);
		}
		vehiclePacket(rider, bird, accepted + 4);
		helper.assertTrue(Math.abs(bird.getX() - accepted) < 1e-5, "a small movement cannot pass through a solid wall");
		bird.discard();
		helper.succeed();
	}

	private static void vehiclePacket(ServerPlayer rider, ChocoboEntity bird, double x) {
		var before = bird.position();
		bird.setPos(x, before.y, before.z);
		var packet = new net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket(bird);
		bird.setPos(before);
		rider.connection.handleMoveVehicle(packet);
	}

	/** The Glacier yellow racer must enter the opening, not jump against the preceding fence forever. */
    @GameTest(template = EMPTY, timeoutTicks = 1200, batch = "ai_detours")
    public static void glacierAiUsesTheDetourOpening(GameTestHelper helper) {
        aiUsesDetourOpening(helper, RaceTrack.B_GLACIER, RaceTrack.Feature.Type.WATER);
    }

    @GameTest(template = EMPTY, timeoutTicks = 1200, batch = "ai_detours")
    public static void deepsAiUsesTheRidgeDetour(GameTestHelper helper) {
        aiUsesDetourOpening(helper, RaceTrack.A_DEEPS, RaceTrack.Feature.Type.RIDGE);
    }

    @GameTest(template = EMPTY, timeoutTicks = 1200, batch = "ai_detours")
    public static void templeAiUsesTheBogDetour(GameTestHelper helper) {
        aiUsesDetourOpening(helper, RaceTrack.A_TEMPLE, RaceTrack.Feature.Type.MUD);
    }

    @GameTest(template = EMPTY, timeoutTicks = 1200, batch = "ai_detours")
    public static void starfallBlueAiClearsSecondRidge(GameTestHelper helper) {
        aiUsesDetourOpening(helper, RaceTrack.S_STARFALL, RaceTrack.Feature.Type.RIDGE, ChocoboColor.BLUE, 1);
    }

    @GameTest(template = EMPTY, timeoutTicks = 3500, batch = "ai_full_lap")
    public static void templeBlackAiKeepsItsLapValid(GameTestHelper helper) {
        aiUsesDetourOpening(helper, RaceTrack.A_TEMPLE, RaceTrack.Feature.Type.MUD, ChocoboColor.BLACK, 0, true);
    }

    private static void aiUsesDetourOpening(GameTestHelper helper, RaceTrack track, RaceTrack.Feature.Type type) {
        aiUsesDetourOpening(helper, track, type, ChocoboColor.YELLOW, 0);
    }

    // Isolated from town cleanup, which deliberately removes unregistered race NPCs.
    private static void aiUsesDetourOpening(GameTestHelper helper, RaceTrack track, RaceTrack.Feature.Type type, ChocoboColor color, int index) {
        aiUsesDetourOpening(helper, track, type, color, index, false);
    }

    private static void aiUsesDetourOpening(GameTestHelper helper, RaceTrack track, RaceTrack.Feature.Type type, ChocoboColor color, int index, boolean fullLap) {
        var feature = track.terrainFeatures().stream().filter(f -> f.type() == type).skip(index).findFirst().orElseThrow();
		ServerLevel level = helper.getLevel();
		RaceManager.testLevel = level;
		forceTrack(level, track, true);
		SquareBuilder.buildTrack(level, track);
		ChocoboEntity bird = ModEntities.CHOCOBO.get().create(level);
		bird.setColor(color);
		bird.setAge(0);
		bird.setPersistenceRequired();
		bird.setRaceClass(track.getRaceClass());
        bird.setGrade(ChocoboGrade.byRank(track.getRaceClass().getId() + 1));
		bird.setGenes(40, 40, 40, 40);
        int training = RaceScoring.fieldTraining(track.getRaceClass().getId(), false);
		bird.addTraining(training, training, training, training);
		bird.setRaceNpc(true);
		bird.setRacing(true);
		bird.fillStamina();
		bird.getRandom().setSeed(1234L);
		double startProgress = fullLap ? .02 : feature.start() - .045;
		double lane = fullLap ? RaceTrack.stallOffset(4, 6) : 1;
		RacePoint start = track.pointAtLane(startProgress, lane);
		RacePoint toward = track.pointAt(startProgress + .01);
		bird.moveTo(start.x(), start.y(), start.z(),
				(float) Math.toDegrees(Math.atan2(-(toward.x() - start.x()), toward.z() - start.z())), 0);
		level.addFreshEntity(bird);
		var goal = new tk.darrow.chocobosreborn.race.RacerGoal(bird, track, lane,
				tk.darrow.chocobosreborn.race.RacerProfile.of(track.getRaceClass(),
						tk.darrow.chocobosreborn.race.RacerProfile.Role.FIELD));
		goal.running = true;
		bird.installRacer(goal);
		bird.inventory().setItem(tk.darrow.chocobosreborn.menu.ChocoboInventoryMenu.SADDLE, new ItemStack(ModItems.SADDLE.get()));
		var jockey = ModEntities.KIN_STEWARD.get().create(level);
		jockey.setRole(tk.darrow.chocobosreborn.race.TownRole.JOCKEY_JOLO);
		jockey.moveTo(start.x(), start.y() + 1, start.z(), 0, 0);
		jockey.installJockey();
		level.addFreshEntity(jockey);
		jockey.startRiding(bird, true);
		boolean[] detour = {false};
		var lap = new tk.darrow.chocobosreborn.race.RaceLapProgress(startProgress);
		if (fullLap) helper.onEachTick(() -> helper.assertTrue(RaceCourseLayout.of(track).onCourse(bird.getX(), bird.getZ()),
				"AI leaves the legal course at " + track.progressAt(bird.getX(), bird.getZ()) + " position=" + bird.position()));
		helper.startSequence().thenWaitUntil(() -> {
			// after a batch that let go of thousands of chunks the island can take a while to tick
			// entities again; a bird in a chunk that does not tick yet only stands there
			AiLapSweepGameTests.islandTicking(helper, level, track);
		}).thenWaitUntil(() -> {
			double progress = track.progressAt(bird.getX(), bird.getZ(), lap.lastProgress());
			boolean credited = lap.update(progress, RaceCourseLayout.of(track).onCourse(bird.getX(), bird.getZ()));
			if (progress > feature.start() + .01 && progress < feature.end() - .01) {
				RacePoint center = track.pointAt(progress);
				detour[0] |= Math.hypot(bird.getX() - center.x(), bird.getZ() - center.z()) > 8;
			}
			helper.assertTrue(fullLap ? credited : progress > feature.end() + .04 && progress < feature.end() + .3, track.name() + " " + color + " racer clears route; progress=" + progress
					+ " position=" + bird.position() + " ticks=" + bird.tickCount + " noAI=" + bird.isNoAi() + " forward=" + bird.zza);
		}).thenExecute(() -> {
			helper.assertTrue(fullLap || detour[0], "the racer actually used the dry detour");
			jockey.discard();
			bird.discard();
			forceTrack(level, track, false);
		}).thenSucceed();
	}

	/**
	 * Racer contact: a faster AI bird starts six blocks behind a slower one in the same
	 * lane. It must never phase through or sit inside the other (contact starts at 1.6
	 * blocks between centres, the bird box is 1.75 wide: kart bumps, or a clean pass), and
	 * both must still get round a full lap with it credited.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 3500, batch = "ai_contact")
	public static void twoAiBirdsInOneLaneNeverOverlapAndBothLap(GameTestHelper helper) {
		RaceTrack track = RaceTrack.C_MEADOW;
		ServerLevel level = helper.getLevel();
		RaceManager.testLevel = level;
		forceTrack(level, track, true);
		SquareBuilder.buildTrack(level, track);
		double start = 0.03D;
		double behind = start - 6.0D / track.lapLength();
		ChocoboEntity front = contactRacer(level, track, start, 1.0D, 1.0D, 11L);
		ChocoboEntity rear = contactRacer(level, track, behind, 1.0D, 1.3D, 12L);
		var frontLap = new tk.darrow.chocobosreborn.race.RaceLapProgress(start);
		var rearLap = new tk.darrow.chocobosreborn.race.RaceLapProgress(behind);
		boolean[] lapped = {false, false};
		double[] closest = {Double.MAX_VALUE};
		helper.onEachTick(() -> {
			if (front.isRemoved() || rear.isRemoved()) return;
			double d = Math.hypot(front.getX() - rear.getX(), front.getZ() - rear.getZ());
			if (Math.abs(front.getY() - rear.getY()) < tk.darrow.chocobosreborn.race.RacerContact.HEIGHT) {
				closest[0] = Math.min(closest[0], d);
				helper.assertTrue(d > 0.9D, "racers inside each other: d=" + d + " front=" + front.position() + " rear=" + rear.position());
			}
		});
		helper.startSequence().thenWaitUntil(() -> {
			lapped[0] |= frontLap.update(track.progressAt(front.getX(), front.getZ(), frontLap.lastProgress()),
					RaceCourseLayout.of(track).onCourse(front.getX(), front.getZ()));
			lapped[1] |= rearLap.update(track.progressAt(rear.getX(), rear.getZ(), rearLap.lastProgress()),
					RaceCourseLayout.of(track).onCourse(rear.getX(), rear.getZ()));
			helper.assertTrue(lapped[0] && lapped[1], "both lap: front=" + lapped[0] + " rear=" + lapped[1]
					+ " closest=" + closest[0]);
		}).thenExecute(() -> {
			helper.assertTrue(closest[0] < 4.5D, "the two actually met on the road: closest " + closest[0]);
			front.discard();
			rear.discard();
			forceTrack(level, track, false);
			RaceManager.testLevel = null;
		}).thenSucceed();
	}

	private static ChocoboEntity contactRacer(ServerLevel level, RaceTrack track, double progress, double lane,
			double pace, long seed) {
		ChocoboEntity bird = ModEntities.CHOCOBO.get().create(level);
		bird.setColor(ChocoboColor.YELLOW);
		bird.setAge(0);
		bird.setPersistenceRequired();
		bird.setRaceClass(track.getRaceClass());
		bird.setGrade(ChocoboGrade.byRank(track.getRaceClass().getId() + 1));
		bird.setGenes(40, 40, 40, 40);
		int training = RaceScoring.fieldTraining(track.getRaceClass().getId(), false);
		bird.addTraining(training, training, training, training);
		bird.setRaceNpc(true);
		bird.setRacing(true);
		bird.setRaceTrack(track.ordinal());
		bird.fillStamina();
		bird.getRandom().setSeed(seed);
		RacePoint at = track.pointAtLane(progress, lane);
		RacePoint toward = track.pointAt(progress + .01);
		bird.moveTo(at.x(), at.y(), at.z(),
				(float) Math.toDegrees(Math.atan2(-(toward.x() - at.x()), toward.z() - at.z())), 0);
		level.addFreshEntity(bird);
		var goal = new tk.darrow.chocobosreborn.race.RacerGoal(bird, track, lane,
				tk.darrow.chocobosreborn.race.RacerProfile.of(track.getRaceClass(),
						tk.darrow.chocobosreborn.race.RacerProfile.Role.FIELD));
		goal.paceScale = pace;
		goal.running = true;
		bird.installRacer(goal);
		bird.inventory().setItem(tk.darrow.chocobosreborn.menu.ChocoboInventoryMenu.SADDLE, new ItemStack(ModItems.SADDLE.get()));
		return bird;
	}

	/** Exercise Minecraft's normalization too, not only our multiplier arithmetic. */
	private static double riddenAcceleration(ChocoboEntity bird, Player rider) {
		try {
			var input = ChocoboEntity.class.getDeclaredMethod("getRiddenInput", Player.class, net.minecraft.world.phys.Vec3.class);
			var speed = ChocoboEntity.class.getDeclaredMethod("getRiddenSpeed", Player.class);
			input.setAccessible(true);
			speed.setAccessible(true);
			bird.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
			bird.moveRelative((float) speed.invoke(bird, rider), (net.minecraft.world.phys.Vec3) input.invoke(bird, rider, net.minecraft.world.phys.Vec3.ZERO));
			return bird.getDeltaMovement().horizontalDistance();
		} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
	}

	@GameTest(template = EMPTY)
	public static void riddenBonusesSurviveInputNormalization(GameTestHelper helper) {
		ChocoboEntity bird = spawnAdult(helper, new BlockPos(2, 1, 2), true, ChocoboColor.GOLD);
		Player rider = owner(helper, bird);
		bird.setSaddledForPreview(true);
		rider.startRiding(bird, true);
		bird.setRacing(true);
		bird.fillStamina();
		rider.zza = 1;
		bird.setGenes(0, 100, 0, 100);
		bird.noteRiderDash(false);
		double cruise = riddenAcceleration(bird, rider);
		bird.noteRiderDash(true);
		double dash = riddenAcceleration(bird, rider);
		helper.assertTrue(Math.abs(dash / cruise - RaceScoring.dashMul()) < 0.0001, "dash increases actual acceleration by 62 percent");
		bird.noteRiderDash(false);
		bird.setGenes(100, 100, 0, 100);
		helper.assertTrue(Math.abs(riddenAcceleration(bird, rider) / cruise - 1.35) < 0.0001, "training survives native input normalization");
		bird.setRaceHeld(true);
		helper.assertTrue(riddenAcceleration(bird, rider) == 0, "grid hold has no acceleration");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void gridHoldPreservesStaminaAndClearsOnExit(GameTestHelper helper) {
		ChocoboEntity bird = spawnAdult(helper, new BlockPos(2, 1, 2), true, ChocoboColor.GOLD);
		Player rider = owner(helper, bird);
		bird.setSaddledForPreview(true);
		rider.startRiding(bird, true);
		bird.setRacing(true);
		bird.setRaceHeld(true);
		bird.fillStamina();
		int stamina = bird.stamina();
		rider.zza = 1;
		rider.setSprinting(true);
		bird.noteRiderDash(true);
		helper.runAtTickTime(8, () -> {
			helper.assertTrue(bird.stamina() == stamina, "holding dash on the grid cannot drain stamina");
			helper.assertTrue(bird.raceHeld(), "grid hold remains synchronized");
			bird.setRacing(false);
			helper.assertTrue(!bird.raceHeld(), "forfeit or finish releases the grid hold");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY)
	public static void datapackAndSquareDimensionLoaded(GameTestHelper helper) {
		// The gametest server only creates the overworld; the dimension itself is
		// checked on a dedicated-server boot (see HANDOFF). Datapack content here.
		helper.assertTrue(helper.getLevel().getServer().getRecipeManager()
				.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chocobosreborn", "chocobo_saddle")).isPresent(),
				"saddle recipe loaded");
		helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(
				net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
						net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chocobosreborn", "blocks/gysahl_green")))
				!= net.minecraft.world.level.storage.loot.LootTable.EMPTY, "gysahl loot table loaded");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void shopCatalogResolvesAndKeepersHaveNames(GameTestHelper helper) {
		// Every stall line names a real item, every shop role has at least one line,
		// every role has a display name, and fans / farmhands never open a screen.
		for (var line : tk.darrow.chocobosreborn.race.RaceShops.catalog()) {
			var id = net.minecraft.resources.ResourceLocation.parse(line.resultId());
			helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id), "shop item exists: " + line.resultId());
			helper.assertTrue(line.cost() > 0 && line.resultCount() > 0, "shop line sane: " + line.resultId());
			helper.assertTrue(java.util.Arrays.stream(tk.darrow.chocobosreborn.race.TownRole.values())
					.anyMatch(r -> r.shops() && r.id().equals(line.role())), "shop line role sells: " + line.role());
		}
		for (var role : tk.darrow.chocobosreborn.race.TownRole.values()) {
			var kin = ModEntities.KIN_STEWARD.get().create(helper.getLevel());
			helper.assertTrue(kin != null, "kin");
			kin.setRole(role);
			helper.assertTrue(kin.getDisplayName().getString().length() > 0, "kin named: " + role);
			if (role.shops()) {
				helper.assertTrue(!kin.getOffers().isEmpty(), "offers for " + role);
			}
			kin.discard();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void farmTemplatePlacesKinAndBirds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var mgr = level.getStructureManager();
		var opt = mgr.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chocobosreborn", "chocobo_farm"));
		helper.assertTrue(opt.isPresent(), "chocobo_farm template loads");
		var tpl = opt.get();
		helper.assertTrue(tpl.getSize().getX() == 27 && tpl.getSize().getZ() == 25, "farm size " + tpl.getSize());
		BlockPos origin = helper.absolutePos(BlockPos.ZERO).offset(400, 0, 400); // clear of the Square arena the heat test builds at the origin
		// entities only register in chunks that are entity-ticking; the test batch only forces its own footprint
		for (int cx = origin.getX() >> 4; cx <= (origin.getX() + 27) >> 4; cx++) {
			for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + 25) >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
		helper.assertTrue(tpl.placeInWorld(level, origin, origin, settings, level.random, 2), "farm placed");
		helper.assertTrue(level.getBlockState(origin.offset(2, 2, 14)).is(Blocks.OAK_FENCE), "paddock fence");
		helper.assertTrue(level.getBlockState(origin.offset(4, 2, 20)).is(
				tk.darrow.chocobosreborn.block.ModBlocks.GYSAHL_GREEN.get()), "gysahl patch");
		helper.runAtTickTime(100, () -> { // entity sections of freshly forced chunks load asynchronously
			var box = new net.minecraft.world.phys.AABB(origin.getX(), origin.getY(), origin.getZ(),
					origin.getX() + 27, origin.getY() + 13, origin.getZ() + 25);
			long kin = level.getEntities(ModEntities.KIN_STEWARD.get(), box, e -> true).size();
			long birds = level.getEntities(ModEntities.CHOCOBO.get(), box, e -> true).size();
			helper.assertTrue(kin == 2, "two kin, got " + kin + " all=" + level.getEntities(ModEntities.KIN_STEWARD.get(), e -> true).stream().map(e -> e.position().subtract(origin.getX(), origin.getY(), origin.getZ()).toString()).toList());
			helper.assertTrue(birds == 2, "two chocobos, got " + birds);
			var steward = level.getEntities(ModEntities.KIN_STEWARD.get(), box,
					e -> e.role() == tk.darrow.chocobosreborn.race.TownRole.FARMHAND);
			helper.assertTrue(steward.size() == 1, "Farmhand at the farm");
			// wipe the placement so it doesn't bleed into other tests
			level.getEntities(ModEntities.KIN_STEWARD.get(), box, e -> true).forEach(e -> e.discard());
			level.getEntities(ModEntities.CHOCOBO.get(), box, e -> true).forEach(e -> e.discard());
			for (int cx = origin.getX() >> 4; cx <= (origin.getX() + 27) >> 4; cx++) {
				for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + 25) >> 4; cz++) {
					level.setChunkForced(cx, cz, false);
				}
			}
			helper.succeed();
		});
	}

	private static void forceTrack(ServerLevel level, RaceTrack track, boolean on) {
		for (long key : RaceCourseLayout.of(track).chunks()) {
			level.setChunkForced((int) (key >> 32), (int) key, on);
		}
	}

	private static void forceArena(ServerLevel level, boolean on) {
		// the whole v13 village: x +-72, z -134 (the north posts and cottages) to the overlook and beyond
		for (int cx = -5; cx <= 4; cx++) {
			for (int cz = -9; cz <= 3; cz++) {
				level.setChunkForced(cx, cz, on);
			}
		}
	}

	/**
	 * A bird saved before the points ladder and bloodlines converts once on load, and only once
	 * (format 1 -> 2 -> 3: two old marks are 6 of 9, then 24 of 36); a format-2 bird's points of
	 * nine become points of 36 and its (blank) line is left alone.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 40)
	public static void oldSaveConvertsOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChocoboEntity bird = ModEntities.CHOCOBO.get().create(level);
		bird.setGrade(ChocoboGrade.byRank(3));
		bird.setGenes(0, 0, 0, 0);
		bird.setRaceClass(tk.darrow.chocobosreborn.race.RaceClass.B);
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		bird.saveWithoutId(tag);
		tag.remove("SaveFormat");      // as an older version wrote it
		tag.putInt("ClassWins", 2);    // two of the old three
		ChocoboEntity old = ModEntities.CHOCOBO.get().create(level);
		old.load(tag);
		helper.assertTrue(old.classWins() == 24, "two old marks became 24 points of 36: " + old.classWins());
		int floor = tk.darrow.chocobosreborn.breed.BreedGenes.gradeFloor(3);
		helper.assertTrue(old.geneSpeed() >= floor && old.geneStamina() >= floor && old.geneIntelligence() >= floor
				&& old.geneCooperation() >= floor, "a blank line rolled wild blood from its grade");
		net.minecraft.nbt.CompoundTag again = new net.minecraft.nbt.CompoundTag();
		old.saveWithoutId(again);
		ChocoboEntity reloaded = ModEntities.CHOCOBO.get().create(level);
		reloaded.load(again);
		helper.assertTrue(reloaded.classWins() == 24, "converted once, not again: " + reloaded.classWins());
		helper.assertTrue(reloaded.geneSpeed() == old.geneSpeed() && reloaded.geneCooperation() == old.geneCooperation(),
				"blood kept on the next load");
		net.minecraft.nbt.CompoundTag nine = new net.minecraft.nbt.CompoundTag();
		bird.saveWithoutId(nine);
		nine.putInt("SaveFormat", 2);   // the nine-point ladder
		nine.putInt("ClassWins", 5);
		ChocoboEntity two = ModEntities.CHOCOBO.get().create(level);
		two.load(nine);
		helper.assertTrue(two.classWins() == 20, "five of nine became 20 of 36: " + two.classWins());
		helper.assertTrue(two.geneSpeed() == 0 && two.geneCooperation() == 0, "format 2 does not roll blood again");
		helper.succeed();
	}

	/** Water run up to a boost pad goes round it: the pad stays put and nothing drops. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void boostPadIsWatertight(GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		BlockPos pad = new BlockPos(2, 2, 2);
		helper.setBlock(pad, tk.darrow.chocobosreborn.block.ModBlocks.BOOST_PAD.get().defaultBlockState());
		helper.setBlock(new BlockPos(1, 2, 2), Blocks.WATER);
		helper.runAtTickTime(60, () -> {
			helper.assertBlockPresent(tk.darrow.chocobosreborn.block.ModBlocks.BOOST_PAD.get(), pad);
			helper.assertBlockPresent(Blocks.WATER, new BlockPos(3, 2, 2));   // it flowed on round the pad
			helper.assertItemEntityNotPresent(tk.darrow.chocobosreborn.block.ModBlocks.BOOST_PAD.get().asItem());
			helper.succeed();
		});
	}

	/** A bed in Whiskerwind (bed_works false: vanilla would explode it) is furniture: the click is cancelled. */
	@GameTest(template = EMPTY)
	public static void squareBedsDoNotExplode(GameTestHelper helper) {
		RaceManager.testLevel = helper.getLevel();
		BlockPos foot = new BlockPos(2, 1, 2);
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState()
				.setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST));
		helper.setBlock(foot.east(), Blocks.RED_BED.defaultBlockState()
				.setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST)
				.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		BlockPos at = helper.absolutePos(foot);
		var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at),
				net.minecraft.core.Direction.UP, at, false);
		var event = net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(p, InteractionHand.MAIN_HAND, at, hit);
		helper.assertTrue(event.isCanceled(), "a bed click in the Square is cancelled before the bed can explode");
		helper.assertBlockPresent(Blocks.RED_BED, foot);
		helper.assertBlockPresent(Blocks.RED_BED, foot.east());
		helper.succeed();
	}

	/** A saddled, tamed bird for {@code owner} at the Square's arrival point. */
	private static ChocoboEntity duelBird(ServerLevel level, ServerPlayer owner) {
		ChocoboEntity bird = ModEntities.CHOCOBO.get().create(level);
		bird.moveTo(Square.ARRIVAL.x, Square.ARRIVAL.y, Square.ARRIVAL.z, 0.0F, 0.0F);
		bird.setColor(ChocoboColor.YELLOW);
		bird.setAge(0);
		level.addFreshEntity(bird);
		bird.tame(owner);
		bird.setOrderedToSit(false);
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SADDLE.get()));
		bird.mobInteract(owner, InteractionHand.MAIN_HAND);
		return bird;
	}

	/** A duel is the two riders alone: no AI field, no jockeys, the two centre stalls. */
	@GameTest(template = EMPTY, timeoutTicks = 400)
	public static void duelIsOneOnOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RaceManager.testLevel = level;
		RaceTrack track = RaceTrack.C_SHORE;
		forceTrack(level, track, true);
		ServerPlayer a = helper.makeMockServerPlayerInLevel();
		ServerPlayer b = helper.makeMockServerPlayerInLevel();
		a.setGameMode(GameType.SURVIVAL);
		b.setGameMode(GameType.SURVIVAL);
		ChocoboEntity birdA = duelBird(level, a);
		ChocoboEntity birdB = duelBird(level, b);
		helper.runAtTickTime(5, () -> {
			helper.assertTrue(birdA.saddled() && birdB.saddled(), "saddled");
			helper.assertTrue(RaceManager.startDuel(a, birdA, b, birdB, track, 0), "duel starts");
			RaceSession s = RaceManager.sessionOf(a.getUUID());
			helper.assertTrue(s != null && s == RaceManager.sessionOf(b.getUUID()), "one session for both riders");
			helper.assertTrue(s.fieldSize() == 2, "two birds on the grid, got " + s.fieldSize());
			helper.assertTrue(s.aiRacers() == 0, "no AI racers or jockeys, got " + s.aiRacers());
			RacePoint left = track.stallPos(0, 2), right = track.stallPos(1, 2);
			helper.assertTrue(birdA.distanceToSqr(left.x(), left.y(), left.z()) < 1.0D, "rider A on a centre stall");
			helper.assertTrue(birdB.distanceToSqr(right.x(), right.y(), right.z()) < 1.0D, "rider B on a centre stall");
			helper.assertTrue(RaceCourseLayout.of(track).onCourse(left.x(), left.z())
					&& RaceCourseLayout.of(track).onCourse(right.x(), right.z()), "centre stalls on the road");
			s.abort();
			helper.assertTrue(RaceManager.sessionOf(a.getUUID()) == null, "duel settled after abort");
			forceTrack(level, track, false);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY, timeoutTicks = 4000)
	public static void squareBuildsCourseAndRunsHeat(GameTestHelper helper) {
		// The gametest server has no custom dimensions: build and race in this level.
		ServerLevel square = helper.getLevel();
		RaceManager.testLevel = square;
		// Entities are only visible to lookups in entity-ticking chunks; the mock
		// player's tickets arrive asynchronously, so pin the arena chunks first.
		forceArena(square, true);
		forceTrack(square, RaceTrack.C_MEADOW, true);   // the course island sits far from the village
		SquareBuilder.buildPaddock(square);
		SquareBuilder.spawnKeepers(square);
		// the verification world persists between runs: an older island with this
		// ordinal would otherwise be taken as built and the new plan never placed
		tk.darrow.chocobosreborn.race.SquareData.get(square).clearBuilt();
		// a spring an older course version left on the island: the relay must take it away
		RacePoint mid = RaceTrack.C_MEADOW.pointAt(0.5D);
		BlockPos stale = new BlockPos((int) Math.floor(mid.x()), (int) mid.y() + 6, (int) Math.floor(mid.z()));
		while (RaceCourseLayout.of(RaceTrack.C_MEADOW).blocks().containsKey(
				new RaceCourseLayout.Cell(stale.getX(), stale.getY(), stale.getZ()))) {
			stale = stale.above();
		}
		square.setBlock(stale, Blocks.WATER.defaultBlockState(), 2);
		SquareBuilder.buildTrack(square, RaceTrack.C_MEADOW);
		helper.assertTrue(square.getBlockState(stale).isAir(), "old water cleared off the island at " + stale);
		// road surface at the start line, lamps, finish gate
		RaceCourseLayout layout = RaceCourseLayout.of(RaceTrack.C_MEADOW);
		int placed = 0;
		for (var e : layout.blocks().entrySet()) {
			var c = e.getKey();
			if (!square.getBlockState(new BlockPos(c.x(), c.y(), c.z())).isAir()) {
				placed++;
			}
		}
		helper.assertTrue(placed > layout.blocks().size() * 0.95, "course placed: " + placed + "/" + layout.blocks().size());
		helper.assertTrue(square.getBlockState(new BlockPos(5, 64, -50)).is(Blocks.SMOOTH_SANDSTONE), "paddock floor");
		// Keepers: one per post, however many times the Square is entered, even with a stray duplicate.
		var dup = ModEntities.KIN_STEWARD.get().create(square);
		helper.assertTrue(dup != null, "dup kin");
		dup.moveTo(2.5D, 65.0D, -56.5D, 0.0F, 0.0F);
		dup.setRole(tk.darrow.chocobosreborn.race.TownRole.STEWARD);
		square.addFreshEntity(dup);
		SquareBuilder.requestKeeperSync(); // the deferred sync runs once the village chunks have entities loaded
		helper.runAtTickTime(100, () -> {
			helper.assertTrue(SquareBuilder.spawnKeepers(square), "village chunks entity-loaded");
			SquareBuilder.spawnKeepers(square);
			for (var post : tk.darrow.chocobosreborn.race.TownPosts.keeperPosts()) {
				long want = tk.darrow.chocobosreborn.race.TownPosts.keeperPosts().stream().filter(q -> q.role() == post.role()).count();
				long n = square.getEntities(ModEntities.KIN_STEWARD.get(), e -> e.role() == post.role() && !e.isRemoved()).size();
				helper.assertTrue(n == want, post.role() + " keepers: " + n + " want " + want);
			}
		});

		// A heat with a real ServerPlayer riding a saddled bird, in the Square.
		ServerPlayer sp = helper.makeMockServerPlayerInLevel();
		sp.setGameMode(GameType.SURVIVAL);
		ChocoboEntity bird = ModEntities.CHOCOBO.get().create(square);
		helper.assertTrue(bird != null, "bird");
		bird.moveTo(Square.ARRIVAL.x, Square.ARRIVAL.y, Square.ARRIVAL.z, 0.0F, 0.0F);
		bird.setColor(ChocoboColor.YELLOW);
		bird.setAge(0);
		square.addFreshEntity(bird);
		bird.tame(sp);
		bird.setOrderedToSit(false);
		Player p = sp;
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SADDLE.get()));
		bird.mobInteract(p, InteractionHand.MAIN_HAND);
		Square.teleportMounted(sp, bird, square, Square.ARRIVAL, Square.ARRIVAL_YAW);
		helper.runAtTickTime(5, () -> {
			ChocoboEntity mine = sp.getVehicle() instanceof ChocoboEntity c ? c : null;
			helper.assertTrue(mine != null, "mounted in the Square");
			helper.assertTrue(RaceManager.startRace(sp, 0, true), "heat starts");
			RaceSession s = RaceManager.sessionOf(sp.getUUID());
			helper.assertTrue(s != null && s.state() == RaceSession.State.HOLD, "hold");
			helper.assertTrue(mine.racing(), "racing flag");
			// code-awarded advancement resolves against the datapack
			tk.darrow.chocobosreborn.race.SquareAdvancements.award(sp, tk.darrow.chocobosreborn.race.SquareAdvancements.SQUARE);
			var adv = square.getServer().getAdvancements().get(tk.darrow.chocobosreborn.race.SquareAdvancements.SQUARE);
			helper.assertTrue(adv != null && sp.getAdvancements().getOrStartProgress(adv).isDone(), "square advancement awarded");
		});
		helper.runAtTickTime(300, () -> {
			// hold = settle + five-second countdown (RaceSession.HOLD_TICKS 260) after the start at tick 5
			RaceSession s = RaceManager.sessionOf(sp.getUUID());
			helper.assertTrue(s != null && s.running(), "running after the hold");
			long npcs = square.getEntities(ModEntities.CHOCOBO.get(), e -> e.raceNpc()).size();
			helper.assertTrue(npcs == 5, "five AI racers, got " + npcs);
		});
		helper.runAtTickTime(500, () -> {
			// AI racers have moved off the stalls
			double moved = square.getEntities(ModEntities.CHOCOBO.get(), e -> e.raceNpc()).stream()
					.mapToDouble(e -> e.distanceToSqr(RaceTrack.C_MEADOW.stallPos(1, 6).x(), 65.0D,
							RaceTrack.C_MEADOW.stallPos(1, 6).z())).max().orElse(0.0D);
			helper.assertTrue(moved > 25.0D, "AI racers move, max d2=" + moved);
		});
		helper.runAtTickTime(2100, () -> {
			// course 0 is a five-lap grand prix of 305-block laps (short grands prix): after
			// ~92 s of racing (GO at ~265) three of the field have two laps done, 610 blocks,
			// the distance the old single 600-block lap asked for in the same time (about 70 %
			// of the C field's ~9.7 blocks a second). The stalled human keeps the heat open, and
			// it proves the AI gets round the chicane, kerbs, rails, boosts and corners of a
			// real circuit at pace, lap after lap
			RaceSession s = RaceManager.sessionOf(sp.getUUID());
			helper.assertTrue(s != null && s.running(), "heat still running with the human stalled");
			StringBuilder where = new StringBuilder();
			for (ChocoboEntity e : square.getEntities(ModEntities.CHOCOBO.get(), ChocoboEntity::raceNpc)) {
				BlockPos bp = e.blockPosition();
				where.append(String.format("[%.1f,%.1f,%.1f g=%s col=%s feet=%s below=%s on=%s] ", e.getX(), e.getY(), e.getZ(),
						e.onGround(), e.horizontalCollision, square.getBlockState(bp).getBlock().getDescriptionId(),
						square.getBlockState(bp.below()).getBlock().getDescriptionId(),
						RaceCourseLayout.of(RaceTrack.C_MEADOW).onCourse(e.getX(), e.getZ())));
			}
			helper.assertTrue(s.lapsDone(2) >= 3, "AI racers finish two laps: " + s.progressReport() + " " + where);
			s.abort();
			helper.assertTrue(RaceManager.sessionOf(sp.getUUID()) == null, "heat settled after abort");
			long npcs = square.getEntities(ModEntities.CHOCOBO.get(), e -> e.raceNpc()).size();
			helper.assertTrue(npcs == 0, "AI racers cleared, got " + npcs);
			RaceManager.testLevel = null;
			forceArena(square, false);
			forceTrack(square, RaceTrack.C_MEADOW, false);
			helper.succeed();
		});
	}
}
