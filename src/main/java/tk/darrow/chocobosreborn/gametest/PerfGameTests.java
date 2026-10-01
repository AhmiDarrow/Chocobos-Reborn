package tk.darrow.chocobosreborn.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.breed.ChocoboColor;
import tk.darrow.chocobosreborn.breed.ChocoboGrade;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.entity.KinStewardEntity;
import tk.darrow.chocobosreborn.entity.ModEntities;
import tk.darrow.chocobosreborn.item.ModItems;
import tk.darrow.chocobosreborn.race.RaceManager;
import tk.darrow.chocobosreborn.race.TownRole;

/**
 * The resting-bird move replay against the full move, and timings of the server's idle costs:
 * penned birds, residents walking through doors, the Square's idle tick. Builds high above the
 * test's own column (or in its own chunks), so nothing lands in another test's space. Timings go
 * to the log and {@code perf-results.txt} in the game directory; only correctness is asserted.
 */
@GameTestHolder(ChocobosReborn.MOD_ID)
@PrefixGameTestTemplate(false)
public class PerfGameTests {
	private static final String EMPTY = "empty";

	// ------------------------------------------------------------- helpers

	/** A stone floor of half-width {@code r} at {@code floor}, with clear air above. */
	private static void pad(ServerLevel level, BlockPos floor, int r) {
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				level.setBlock(floor.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 2);
				for (int y = 1; y <= 5; y++) {
					level.setBlock(floor.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
	}

	private static void clear(ServerLevel level, BlockPos floor, int r) {
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				level.setBlock(floor.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	/** A tame adult standing on {@code floor} (block centre), sitting if {@code sit}. */
	private static ChocoboEntity bird(ServerLevel level, BlockPos floor, Player owner, boolean sit) {
		ChocoboEntity b = ModEntities.CHOCOBO.get().create(level);
		b.moveTo(floor.getX() + 0.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D, 0.0F, 0.0F);
		b.setColor(ChocoboColor.YELLOW);
		b.setMale(true);
		b.setAge(0);
		b.setGrade(ChocoboGrade.GOOD);
		b.tame(owner);
		b.setOrderedToSit(sit);
		level.addFreshEntity(b);
		return b;
	}

	private static Player owner(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		return p;
	}

	private static void report(String line) {
		ChocobosReborn.LOGGER.info("[perf] {}", line);
		try {
			Files.writeString(Path.of("perf-results.txt"), line + System.lineSeparator(),
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ignored) {
		}
	}

	// ------------------------------------------------- resting move replay

	/**
	 * Two sitting birds on identical pads 30 blocks apart (one above the other, so every
	 * coordinate but y matches and y stays in one binade): one replays its resting move, one
	 * always takes the full move. Settled, pushed, then with the floor taken away, they must
	 * agree to the bit every tick.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void restingReplayMatchesFullMove(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		BlockPos padA = new BlockPos(o.getX() + 2, 70, o.getZ() + 2), padB = padA.above(30);
		pad(level, padA, 3);
		pad(level, padB, 3);
		Player owner = owner(helper);
		ChocoboEntity a = bird(level, padA, owner, true), b = bird(level, padB, owner, true);
		b.setRestReplay(false);
		double x0 = a.getX(), z0 = a.getZ();
		long[] mark = new long[1];
		int[] t = {0};
		helper.onEachTick(() -> {
			int tick = ++t[0];
			if (a.isRemoved() || b.isRemoved()) {
				return;
			}
			String where = "tick " + tick + ": ";
			helper.assertTrue(a.getX() == b.getX() && a.getZ() == b.getZ() && a.getY() + 30.0D == b.getY(),
					where + "same place " + a.position() + " vs " + b.position());
			helper.assertTrue(a.getDeltaMovement().equals(b.getDeltaMovement()),
					where + "same motion " + a.getDeltaMovement() + " vs " + b.getDeltaMovement());
			helper.assertTrue(a.onGround() == b.onGround() && a.verticalCollision == b.verticalCollision
					&& a.horizontalCollision == b.horizontalCollision && a.fallDistance == b.fallDistance,
					where + "same flags");
			helper.assertTrue(b.restReplays() == 0, where + "the full-move bird never replays");
			if (tick == 40) {
				helper.assertTrue(a.restReplays() > 20, "a settled bird replays its move: " + a.restReplays());
				mark[0] = a.restReplays();
				a.push(0.35D, 0.0D, 0.15D);   // what a mob or player walking into it does
				b.push(0.35D, 0.0D, 0.15D);
			} else if (tick == 41) {
				helper.assertTrue(a.restReplays() == mark[0], "a pushed bird takes the full move");
			} else if (tick == 90) {
				helper.assertTrue(a.getX() - x0 > 0.3D && a.getZ() - z0 > 0.1D, "pushed along: " + (a.getX() - x0));
				helper.assertTrue(a.restReplays() > mark[0] + 10, "and rests again once it stops");
				clear(level, padA, 3);
				clear(level, padB, 3);
			} else if (tick == 97) {
				helper.assertTrue(a.getY() < padA.getY() + 1.0D - 0.5D, "no floor: it falls, " + a.getY());
				a.discard();
				b.discard();
				helper.succeed();
			}
		});
	}

	/** A real mob walking into a resting bird shoves it, as it would any animal. */
	@GameTest(template = EMPTY, timeoutTicks = 120)
	public static void restingBirdIsPushedByAMob(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		BlockPos floor = new BlockPos(o.getX() + 2, 130, o.getZ() + 2);
		pad(level, floor, 3);
		ChocoboEntity bird = bird(level, floor, owner(helper), true);
		helper.runAtTickTime(30, () -> {
			helper.assertTrue(bird.restReplays() > 10, "resting: " + bird.restReplays());
			var pig = EntityType.PIG.create(level);
			pig.moveTo(bird.getX() + 0.4D, bird.getY(), bird.getZ() + 0.2D, 0.0F, 0.0F);
			pig.setNoAi(true);
			level.addFreshEntity(pig);
			Vec3 before = bird.position();
			helper.runAfterDelay(20, () -> {
				double moved = bird.position().subtract(before).horizontalDistance();
				helper.assertTrue(moved > 0.05D, "the pig shoved the bird: " + moved);
				pig.discard();
				bird.discard();
				clear(level, floor, 3);
				helper.succeed();
			});
		});
	}

	/** A ridden bird is the rider's to move: the replay never answers for it. */
	@GameTest(template = EMPTY, timeoutTicks = 120)
	public static void riddenBirdTakesTheFullMove(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		BlockPos floor = new BlockPos(o.getX() + 2, 160, o.getZ() + 2);
		pad(level, floor, 3);
		Player p = owner(helper);
		p.moveTo(floor.getX() + 3.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D);
		ChocoboEntity bird = bird(level, floor, p, true);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SADDLE.get()));
		bird.mobInteract(p, InteractionHand.MAIN_HAND);
		helper.assertTrue(bird.saddled(), "saddled");
		long[] mark = new long[1];
		helper.runAtTickTime(30, () -> {
			helper.assertTrue(bird.restReplays() > 10, "resting before the ride: " + bird.restReplays());
			bird.setOrderedToSit(false);
			p.startRiding(bird, true);
			helper.assertTrue(bird.getControllingPassenger() == p, "ridden");
			mark[0] = bird.restReplays();
		});
		helper.runAtTickTime(70, () -> {
			helper.assertTrue(bird.getControllingPassenger() == p, "still ridden");
			helper.assertTrue(bird.restReplays() == mark[0], "no replays while ridden: " + (bird.restReplays() - mark[0]));
			helper.assertTrue(bird.onGround(), "standing");
			p.stopRiding();
			bird.discard();
			clear(level, floor, 3);
			helper.succeed();
		});
	}

	/** A bird in water floats and swims as before: water always takes the full move. */
	@GameTest(template = EMPTY, timeoutTicks = 120)
	public static void birdInWaterNeverReplays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		BlockPos floor = new BlockPos(o.getX() + 2, 190, o.getZ() + 2);
		pad(level, floor, 4);
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				for (int y = 1; y <= 4; y++) {
					boolean rim = Math.abs(x) == 4 || Math.abs(z) == 4;
					level.setBlock(floor.offset(x, y, z), rim ? Blocks.STONE.defaultBlockState() : Blocks.WATER.defaultBlockState(), 2);
				}
			}
		}
		ChocoboEntity bird = bird(level, floor.above(2), owner(helper), true);
		helper.runAtTickTime(80, () -> {
			helper.assertTrue(bird.restReplays() == 0, "replays in water: " + bird.restReplays());
			helper.assertTrue(bird.isInWater() && bird.getY() > floor.getY() + 2.0D, "afloat at " + bird.getY());
			bird.discard();
			for (int x = -4; x <= 4; x++) {
				for (int z = -4; z <= 4; z++) {
					for (int y = 0; y <= 4; y++) {
						level.setBlock(floor.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
			helper.succeed();
		});
	}

	// ------------------------------------------------------------- timings

	/**
	 * Twenty sitting birds in a fenced pen (2 blocks apart, as tight as a real pen): server
	 * ticks of all of them, timed with the full move and with the replay, alternating.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 400, batch = "perf_birds")
	public static void restingBirdTickCost(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		BlockPos corner = new BlockPos(o.getX() - 3, 220, o.getZ() - 3);
		int w = 11, d = 9;
		for (int x = -1; x <= w; x++) {
			for (int z = -1; z <= d; z++) {
				level.setBlock(corner.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
				boolean rim = x == -1 || z == -1 || x == w || z == d;
				level.setBlock(corner.offset(x, 1, z), rim ? Blocks.OAK_FENCE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
				for (int y = 2; y <= 5; y++) {
					level.setBlock(corner.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		Player owner = owner(helper);
		List<ChocoboEntity> birds = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			for (int k = 0; k < 4; k++) {
				birds.add(bird(level, corner.offset(1 + 2 * i, 0, 1 + 2 * k), owner, true));
			}
		}
		helper.runAtTickTime(60, () -> {
			int iters = 400;
			long[] best = {Long.MAX_VALUE, Long.MAX_VALUE};
			long replays = 0;
			for (int round = 0; round < 4; round++) {
				for (int mode = 0; mode < 2; mode++) {
					boolean on = mode == 1;
					for (ChocoboEntity b : birds) {
						b.setRestReplay(on);
					}
					for (int i = 0; i < 40; i++) {
						for (ChocoboEntity b : birds) {
							level.tickNonPassenger(b);
						}
					}
					long r0 = 0;
					for (ChocoboEntity b : birds) {
						r0 += b.restReplays();
					}
					long t0 = System.nanoTime();
					for (int i = 0; i < iters; i++) {
						for (ChocoboEntity b : birds) {
							level.tickNonPassenger(b);
						}
					}
					long dt = System.nanoTime() - t0;
					best[mode] = Math.min(best[mode], dt);
					if (on) {
						long r1 = 0;
						for (ChocoboEntity b : birds) {
							r1 += b.restReplays();
						}
						replays = r1 - r0;
					}
				}
			}
			double full = best[0] / 1000.0D / iters / birds.size(), replay = best[1] / 1000.0D / iters / birds.size();
			report(String.format("resting bird server tick: full move %.1f us, replay %.1f us per bird (%.2fx); "
							+ "replayed %d of %d moves in the last timed run", full, replay, full / replay, replays,
					(long) iters * birds.size()));
			helper.assertTrue(replays > (long) iters * birds.size() / 2, "the pen's birds replay: " + replays);
			for (ChocoboEntity b : birds) {
				helper.assertTrue(b.onGround(), "every bird still standing on the floor");
				b.discard();
			}
			helper.succeed();
		});
	}

	/**
	 * Two corridors, each with five residents walking end to end through three oak doors, 90
	 * blocks apart (far beyond any re-plan radius). In one the kin re-plan on every door that
	 * swings near their path, as vanilla does; in the other they do not. Counts path searches,
	 * the time spent in them and the trips walked.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 1500, batch = "perf_kin")
	public static void stewardDoorReplans(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		int ox = o.getX(), oz = o.getZ();
		for (int cx = (ox - 4) >> 4; cx <= (ox + 16) >> 4; cx++) {
			for (int cz = (oz - 2) >> 4; cz <= (oz + 4) >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		int[] floors = {150, 240};
		for (int fy : floors) {
			corridor(level, ox, fy, oz);
		}
		List<List<KinStewardEntity>> groups = new ArrayList<>();
		long[][] trips = new long[2][5];
		helper.runAtTickTime(20, () -> {
			for (int g = 0; g < 2; g++) {
				List<KinStewardEntity> kin = new ArrayList<>();
				for (int i = 0; i < 5; i++) {
					KinStewardEntity k = ModEntities.KIN_STEWARD.get().create(level);
					boolean west = i % 2 == 0;
					k.moveTo(ox + (west ? -1.5D + i * 0.4D : 13.5D - i * 0.4D), floors[g] + 1.0D, oz + 0.5D + (i % 3), 0.0F, 0.0F);
					k.setRole(TownRole.RESIDENT_RANCHER);
					k.setDoorReplans(g == 0);
					level.addFreshEntity(k);
					kin.add(k);
				}
				groups.add(kin);
			}
		});
		int start = 25, end = 25 + 1200;
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (tick < start || groups.size() < 2) {
				return;
			}
			if (tick < end) {
				for (int g = 0; g < 2; g++) {
					for (int i = 0; i < 5; i++) {
						KinStewardEntity k = groups.get(g).get(i);
						if (k.getNavigation().isDone()) {
							boolean atEast = k.getX() > ox + 6.0D;
							if (Math.abs(k.getX() - (atEast ? ox + 13.5D : ox - 1.5D)) < 2.0D) {
								trips[g][i]++;
							}
							k.getNavigation().moveTo(atEast ? ox - 1.5D : ox + 13.5D, floors[g] + 1.0D, oz + 1.5D, 0.55D);
						}
					}
				}
				return;
			}
			if (tick == end) {
				long[] searches = new long[2], walked = new long[2];
				double[] ms = new double[2];
				for (int g = 0; g < 2; g++) {
					for (int i = 0; i < 5; i++) {
						KinStewardEntity k = groups.get(g).get(i);
						searches[g] += k.pathSearches();
						ms[g] += k.pathSearchNanos() / 1.0E6D;
						walked[g] += trips[g][i];
					}
				}
				report(String.format("residents through doors, 5 kin x 60 s: re-plan on doors %d searches (%.1f ms), %d trips; "
								+ "door swings ignored %d searches (%.1f ms), %d trips", searches[0], ms[0], walked[0],
						searches[1], ms[1], walked[1]));
				helper.assertTrue(walked[0] > 5 && walked[1] > 5, "both corridors walked: " + walked[0] + ", " + walked[1]);
				helper.assertTrue(searches[1] < searches[0], "fewer searches without door re-plans");
				for (List<KinStewardEntity> kin : groups) {
					kin.forEach(KinStewardEntity::discard);
				}
				for (int cx = (ox - 4) >> 4; cx <= (ox + 16) >> 4; cx++) {
					for (int cz = (oz - 2) >> 4; cz <= (oz + 4) >> 4; cz++) {
						level.setChunkForced(cx, cz, false);
					}
				}
				helper.succeed();
			}
		});
	}

	/** Floor at {@code y}, interior x -2..14 by z 0..2, walls 3 high, oak doors across it at x 3, 7 and 11. */
	private static void corridor(ServerLevel level, int ox, int y, int oz) {
		BlockState stone = Blocks.STONE_BRICKS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		for (int x = -3; x <= 15; x++) {
			for (int z = -1; z <= 3; z++) {
				level.setBlock(new BlockPos(ox + x, y, oz + z), stone, 2);
				boolean wall = z == -1 || z == 3 || x == -3 || x == 15;
				for (int h = 1; h <= 3; h++) {
					level.setBlock(new BlockPos(ox + x, y + h, oz + z), wall ? stone : air, 2);
				}
				level.setBlock(new BlockPos(ox + x, y + 4, oz + z), stone, 2);
			}
		}
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
		for (int dx : new int[]{3, 7, 11}) {
			for (int z = 0; z <= 2; z++) {
				for (int h = 1; h <= 3; h++) {
					BlockPos p = new BlockPos(ox + dx, y + h, oz + z);
					if (z == 1 && h == 1) {
						level.setBlock(p, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 18);
					} else if (z == 1 && h == 2) {
						level.setBlock(p, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 18);
					} else {
						level.setBlock(p, stone, 2);
					}
				}
			}
		}
	}

	/** The Square's server tick with nothing on the timetable and a visitor in town. */
	@GameTest(template = EMPTY, timeoutTicks = 100, batch = "perf_square")
	public static void squareIdleTickCost(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		owner(helper);
		// the Square lifts a visitor below y 50 back to the arrival point: hold every player in
		// this level well above it while the tick is timed, then put them back
		List<ServerPlayer> lifted = new ArrayList<>(level.players());
		List<Vec3> at = new ArrayList<>();
		for (ServerPlayer p : lifted) {
			at.add(p.position());
			p.setPos(p.getX(), 260.0D, p.getZ());
		}
		RaceManager.testLevel = level;
		try {
			var event = new net.neoforged.neoforge.event.tick.ServerTickEvent.Post(() -> true, level.getServer());
			int n = 200_000;
			long best = Long.MAX_VALUE;
			for (int round = 0; round < 3; round++) {
				long t0 = System.nanoTime();
				for (int i = 0; i < n; i++) {
					RaceManager.onServerTick(event);
				}
				best = Math.min(best, System.nanoTime() - t0);
			}
			report(String.format("Square idle server tick (no heat, one visitor): %.0f ns per tick (heats pending: %s)",
					(double) best / n, tk.darrow.chocobosreborn.race.HeatSchedule.anyPending()));
		} finally {
			RaceManager.testLevel = null;
			for (int i = 0; i < lifted.size(); i++) {
				lifted.get(i).setPos(at.get(i));
			}
		}
		helper.succeed();
	}
}
