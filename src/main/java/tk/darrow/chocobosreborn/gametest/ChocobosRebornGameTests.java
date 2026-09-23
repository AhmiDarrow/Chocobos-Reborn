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
		helper.assertTrue(c.trainedSpeed() >= 4, "pepio talent");
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
		for (int cx = -5; cx <= 4; cx++) {
			for (int cz = -5; cz <= 3; cz++) {
				level.setChunkForced(cx, cz, on);
			}
		}
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
		helper.assertTrue(square.getBlockState(new BlockPos(6, 64, -58)).is(Blocks.SMOOTH_SANDSTONE), "paddock floor");
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
			// a sprint is 600 blocks: after ~90 s most of the field has finished it (the
			// stalled human keeps the heat open for the finish grace), which proves the AI
			// gets round the kerbs, rails, boosts and corners of a real circuit at pace
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
			helper.assertTrue(s.finished() >= 3, "AI racers finish the sprint: " + s.progressReport() + " " + where);
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
