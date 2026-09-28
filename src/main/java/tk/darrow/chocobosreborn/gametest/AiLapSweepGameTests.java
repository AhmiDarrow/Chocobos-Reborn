package tk.darrow.chocobosreborn.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.breed.ChocoboColor;
import tk.darrow.chocobosreborn.breed.ChocoboGrade;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.entity.ModEntities;
import tk.darrow.chocobosreborn.item.ModItems;
import tk.darrow.chocobosreborn.race.RaceClass;
import tk.darrow.chocobosreborn.race.RaceCourseLayout;
import tk.darrow.chocobosreborn.race.RaceLapProgress;
import tk.darrow.chocobosreborn.race.RaceManager;
import tk.darrow.chocobosreborn.race.RacePoint;
import tk.darrow.chocobosreborn.race.RaceScoring;
import tk.darrow.chocobosreborn.race.RaceTrack;
import tk.darrow.chocobosreborn.race.RacerContact;
import tk.darrow.chocobosreborn.race.RacerGoal;
import tk.darrow.chocobosreborn.race.RacerProfile;
import tk.darrow.chocobosreborn.race.SquareBuilder;

/**
 * Every course with a terrain feature, every colour its class races, from two start lanes:
 * each field AI bird has to get round one full lap in time, with no stall on the way.
 *
 * <p>Found by the hub harness: on A_CRYSTAL (5 x 372) two of three field birds froze for the
 * whole race just past a detour's rejoin (Vincent, Blue, 0 + 0.75 after the ridge; Cait,
 * Yellow, 0 + 0.55 after the water). One test per course, one batch per class, so a class's
 * courses run side by side (each island is far from the others). The birds are solid (racer
 * contact on) and start spread round the lap, so they meet and bump as a field does; off the
 * road they are set back as {@code RaceSession} does. Every stall (under 3 blocks of progress
 * in {@link #STALL_TICKS}) is logged as {@code AI-SWEEP} with course, colour, lane, progress,
 * position and the feature it is nearest.
 */
@GameTestHolder(ChocobosReborn.MOD_ID)
@PrefixGameTestTemplate(false)
public class AiLapSweepGameTests {
	/** No forward progress (3 blocks) for this long is a stall; the AI's own recovery starts at 10. */
	static final int STALL_TICKS = 100;
	private static final double STALL_BLOCKS = 3.0D;
	/** Start lanes: the outside and inside grid stalls. */
	private static final double[] LANES = {RaceTrack.stallOffset(0, 6), RaceTrack.stallOffset(5, 6)};

	/** The colours a field of this class can bring (FieldRoster home + guests, Jolo's Gold in S). */
	static List<ChocoboColor> colours(RaceClass rc) {
		return switch (rc) {
			case C -> List.of(ChocoboColor.YELLOW);
			case B -> List.of(ChocoboColor.YELLOW, ChocoboColor.GREEN, ChocoboColor.BLUE);
			case A -> List.of(ChocoboColor.YELLOW, ChocoboColor.GREEN, ChocoboColor.BLUE, ChocoboColor.BLACK, ChocoboColor.WHITE);
			case S -> List.of(ChocoboColor.YELLOW, ChocoboColor.GREEN, ChocoboColor.BLUE, ChocoboColor.BLACK, ChocoboColor.WHITE,
					ChocoboColor.GOLD);
		};
	}

	/** Blocks a tick the class field cruises at on the flat (movement speed x ~2.9 on ground). */
	static double paceBlocksPerTick(RaceClass rc) {
		return 2.9D * RaceScoring.fieldPaceAbs(rc);
	}

	/** Ticks a bird gets for one lap: two and a half times the class field's clean lap, plus a start. */
	static int lapLimit(RaceTrack track) {
		return (int) Math.ceil(2.5D * track.lapLength() / paceBlocksPerTick(track.getRaceClass())) + 300;
	}

	@GameTestGenerator
	public static Collection<TestFunction> aiLapSweep() {
		List<TestFunction> out = new ArrayList<>();
		for (RaceTrack track : RaceTrack.values()) {
			if (track.terrainFeatures().isEmpty()) {
				continue;
			}
			String batch = "ai_sweep_" + track.getRaceClass().name().toLowerCase(Locale.ROOT)
					+ (track.isSprint() ? "_sprint" : "_gp");
			out.add(new TestFunction(batch, "aisweep." + track.id(), ChocobosReborn.MOD_ID + ":empty",
					lapLimit(track) + 800, 0L, true, helper -> sweep(helper, track)));
		}
		return out;
	}

	/**
	 * "Vivi" (Blue, A_CRYSTAL): set down against the ridge face, a non-climber that cannot jump
	 * four blocks gets round by the detour and on past the ridge on its own. (Its own batch: the
	 * next test races the same island, and a test lets go of the island's chunks when it ends.)
	 */
	@net.minecraft.gametest.framework.GameTest(template = "empty", timeoutTicks = 1400, batch = "ai_recovery_face")
	public static void aiPinnedOnARidgeFaceGetsRound(GameTestHelper helper) {
		RaceTrack track = RaceTrack.A_CRYSTAL;
		RaceTrack.Feature ridge = track.terrainFeatures().get(2);
		recovers(helper, track, ChocoboColor.BLUE, ridge.start() - 0.9D / track.lapLength(), 0.0D, ridge.end() + 0.03D);
	}

	/**
	 * "Vincent" (Blue, A_CRYSTAL 0 + 0.75, 50 s): set down outside the band's rail just past
	 * the ridge detour's rejoin, it steers back into the opening and races on.
	 */
	@net.minecraft.gametest.framework.GameTest(template = "empty", timeoutTicks = 1400, batch = "ai_recovery_rail")
	public static void aiOutsideTheRailPastARejoinGetsBack(GameTestHelper helper) {
		RaceTrack track = RaceTrack.A_CRYSTAL;
		RaceTrack.Feature ridge = track.terrainFeatures().get(2);
		double past = ridge.end() + RaceTrack.DETOUR_CONNECT + 1.1D / track.lapLength();
		recovers(helper, track, ChocoboColor.BLUE, past, -7.25D, past + 0.04D);
	}

	/** One field bird set down at {@code start} on {@code lane}: it must reach {@code until} on the road, without a set-back. */
	private static void recovers(GameTestHelper helper, RaceTrack track, ChocoboColor colour, double start, double lane, double until) {
		ServerLevel level = helper.getLevel();
		RaceManager.testLevel = level;
		force(level, track, true);
		SquareBuilder.buildTrack(level, track);
		RaceCourseLayout layout = RaceCourseLayout.of(track);
		Runner[] runner = {null};
		double[] reached = {start};
		String[] setBack = {null};
		helper.onEachTick(() -> {
			if (runner[0] != null && setBack[0] == null && runner[0].goal.takeSetBack()) {
				ChocoboEntity e = runner[0].bird;
				setBack[0] = describe(track, layout, runner[0], track.progressAt(e.getX(), e.getZ(), reached[0]), level);
			}
		});
		helper.startSequence().thenWaitUntil(() -> islandTicking(helper, level, track)).thenExecute(() -> {
			runner[0] = spawn(level, track, colour, lane, start, 77L);
		}).thenWaitUntil(() -> {
			ChocoboEntity e = runner[0].bird;
			double p = track.progressAt(e.getX(), e.getZ(), reached[0]);
			reached[0] = p;
			boolean past = wrap(p - until) > 0.0D;
			helper.assertTrue(past && layout.onCourse(e.getX(), e.getZ()), track.name() + " " + colour + " still stuck: "
					+ describe(track, layout, runner[0], p, level));
		}).thenExecute(() -> {
			runner[0].bird.discard();
			force(level, track, false);
			helper.assertTrue(setBack[0] == null, track.name() + " " + colour + " needed a set-back: " + setBack[0]);
		}).thenSucceed();
	}

	/** One bird of the sweep and what it has done. */
	private static final class Runner {
		final ChocoboEntity bird;
		final RacerGoal goal;
		final ChocoboColor colour;
		final double lane, start;
		final RaceLapProgress lap;
		double cum, best, lastRaw;
		int bestTick, heldUntil, ghostUntil, rescues, setBacks, lapTicks = -1;
		int stallTicks;
		String stall;
		final java.util.ArrayDeque<String> recent = new java.util.ArrayDeque<>();
		int trace;
		double roadX = Double.NaN, roadZ;

		Runner(ChocoboEntity bird, RacerGoal goal, ChocoboColor colour, double lane, double start) {
			this.bird = bird;
			this.goal = goal;
			this.colour = colour;
			this.lane = lane;
			this.start = start;
			this.lap = new RaceLapProgress(start);
			this.lastRaw = start;
		}
	}

	private static void sweep(GameTestHelper helper, RaceTrack track) {
		ServerLevel level = helper.getLevel();
		RaceManager.testLevel = level;
		force(level, track, true);
		SquareBuilder.buildTrack(level, track);
		RaceCourseLayout layout = RaceCourseLayout.of(track);
		List<ChocoboColor> colours = colours(track.getRaceClass());
		int n = colours.size() * LANES.length;
		List<Runner> runners = new ArrayList<>();
		int limit = lapLimit(track);
		int[] tick = {0};
		double lapLen = track.lapLength();
		helper.onEachTick(() -> {
			if (runners.isEmpty()) {
				return;   // the island's chunks are still coming up
			}
			tick[0]++;
			for (Runner r : runners) {
				if (r.lapTicks >= 0 || r.bird.isRemoved()) {
					continue;
				}
				ChocoboEntity e = r.bird;
				if (r.heldUntil > 0 && tick[0] >= r.heldUntil) {
					r.heldUntil = 0;
					e.setRaceHeld(false);
				}
				if (r.ghostUntil > 0 && tick[0] >= r.ghostUntil) {
					r.ghostUntil = 0;
					e.setRaceGhost(false);
				}
				double progress = track.progressAt(e.getX(), e.getZ(), r.lap.lastProgress());
				boolean onCourse = layout.onCourse(e.getX(), e.getZ());
				// forward progress, whatever the lap credit says: a stall is a stall on or off the road
				double d = progress - r.lastRaw;
				d -= Math.floor(d + 0.5D);
				r.lastRaw = progress;
				r.cum += d * lapLen;
				if (tick[0] % 2 == 0 || r.trace > 0) {
					String snap = String.format(Locale.ROOT, "[%d p%.4f l%.2f (%.2f,%.2f,%.2f) v(%.2f,%.2f,%.2f) g%s c%s cl%s %s nb%.1f]", tick[0],
							progress, track.laneAt(progress, e.getX(), e.getZ()), e.getX(), e.getY(), e.getZ(), e.getDeltaMovement().x,
							e.getDeltaMovement().y, e.getDeltaMovement().z, e.onGround() ? "1" : "0", e.horizontalCollision ? "1" : "0",
							e.onClimbable() ? "1" : "0", r.goal.recoveryMode(), nearest(runners, r));
					if (r.trace > 0) {
						r.trace--;
						ChocobosReborn.LOGGER.info("AI-SWEEP TRACE {} {}@{} {}", track.name(), r.colour, r.lane, snap);
					} else {
						r.recent.addLast(snap);
						if (r.recent.size() > 30) {
							r.recent.removeFirst();
						}
					}
				}
				if (r.cum > r.best + STALL_BLOCKS) {
					r.best = r.cum;
					r.bestTick = tick[0];
				} else if (tick[0] - r.bestTick >= STALL_TICKS && r.heldUntil == 0) {
					if (r.stall == null) {
						r.stall = describe(track, layout, r, progress, level);
						ChocobosReborn.LOGGER.warn("AI-SWEEP STALL {} blocks {} before {}", r.stall, around(level, e), String.join(" ", r.recent));
						r.trace = 40;
					}
					r.stallTicks = Math.max(r.stallTicks, tick[0] - r.bestTick);
				}
				boolean fell = RaceScoring.squareFallRescue(e.getY());
				RaceLapProgress.Step step = fell ? RaceLapProgress.Step.RESCUE
						: r.lap.step(progress, onCourse, RaceLapProgress.allowance(lapLen));
				if (onCourse && !r.lap.offCourse()) {
					r.roadX = e.getX();
					r.roadZ = e.getZ();
				} else if (step == RaceLapProgress.Step.NONE && !Double.isNaN(r.roadX)
						&& RaceScoring.strayedTooFar(e.getX() - r.roadX, e.getZ() - r.roadZ)) {
					step = RaceLapProgress.Step.RESCUE;
				}
				if (step == RaceLapProgress.Step.NONE && r.goal.takeSetBack()) {
					r.setBacks++;   // as RaceSession: the bird's own recovery gave up
					step = RaceLapProgress.Step.RESCUE;
				}
				if (step == RaceLapProgress.Step.RESCUE) {
					r.rescues++;
					ChocobosReborn.LOGGER.info("AI-SWEEP rescue {} (fell={}, anchor {})", describe(track, layout, r, progress, level), fell,
							String.format(Locale.ROOT, "%.4f", r.lap.lastProgress()));
					rescue(track, r, tick[0]);
				} else if (step == RaceLapProgress.Step.LAP) {
					r.lapTicks = tick[0];
					e.discard();
				}
			}
		});
		helper.startSequence().thenWaitUntil(() -> {
			// a bird that walks into a chunk that is not entity-ticking yet freezes there: wait for the island
			islandTicking(helper, level, track);
		}).thenExecute(() -> {
			List<Runner> spawned = new ArrayList<>();
			int k = 0;
			for (ChocoboColor colour : colours) {
				for (double lane : LANES) {
					double start = clearStart(track, 0.02D + k / (double) n);
					long seed = (long) track.ordinal() * 1000L + colour.ordinal() * 10L + k;
					spawned.add(spawn(level, track, colour, lane, start, seed));
					k++;
				}
			}
			runners.addAll(spawned);
		}).thenWaitUntil(() -> {
			boolean all = runners.stream().allMatch(r -> r.lapTicks >= 0);
			helper.assertTrue(all || tick[0] >= limit, "racing");
		}).thenExecute(() -> {
			List<String> bad = new ArrayList<>();
			StringBuilder laps = new StringBuilder();
			for (Runner r : runners) {
				laps.append(String.format(Locale.ROOT, " %s@%.1f:%s", r.colour, r.lane, r.lapTicks < 0 ? "DNF" : Integer.toString(r.lapTicks)));
				if (r.lapTicks < 0) {
					double p = track.progressAt(r.bird.getX(), r.bird.getZ(), r.lap.lastProgress());
					String now = describe(track, layout, r, p, level);
					ChocobosReborn.LOGGER.warn("AI-SWEEP DNF {} blocks {}", now, around(level, r.bird));
					bad.add("no lap in " + limit + " ticks: " + (r.stall != null ? r.stall : now));
				} else if (r.stall != null) {
					bad.add("stalled " + r.stallTicks + " ticks: " + r.stall);
				}
				r.bird.discard();
			}
			ChocobosReborn.LOGGER.info("AI-SWEEP DONE {} lap {} limit {} expect {}:{}", track.name(), Math.round(lapLen), limit,
					Math.round(lapLen / paceBlocksPerTick(track.getRaceClass())), laps);
			force(level, track, false);
			helper.assertTrue(bad.isEmpty(), track.name() + " AI stalls (" + bad.size() + "): " + String.join(" | ", bad));
		}).thenSucceed();
	}

	/**
	 * Asserts every chunk of the island ticks entities (a bird in one that does not just stands
	 * there). The test server ticks as fast as it can, so a wait counted in ticks can run out in
	 * a couple of seconds while the previous batch's chunks are still being saved: while it is
	 * not ready this gives the chunk threads a few milliseconds of real time per tick.
	 */
	static void islandTicking(GameTestHelper helper, ServerLevel level, RaceTrack track) {
		for (long key : RaceCourseLayout.of(track).chunks()) {
			if (!level.isPositionEntityTicking(new BlockPos(((int) (key >> 32) << 4) + 8, 65, ((int) key << 4) + 8))) {
				try {
					Thread.sleep(5L);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				helper.fail("island chunks entity-ticking: " + track.name());
			}
		}
	}

	/** First point at or after {@code t} that is on plain road, clear of every feature and its connectors. */
	private static double clearStart(RaceTrack track, double t) {
		double pad = track.detourConnect() + 12.0D / track.lapLength();
		for (int i = 0; i < 400; i++) {
			double w = t - Math.floor(t);
			boolean clear = true;
			for (RaceTrack.Feature f : track.terrainFeatures()) {
				double a = f.start() - pad, b = f.end() + pad;
				if ((w >= a && w <= b) || (w + 1.0D >= a && w + 1.0D <= b) || (w - 1.0D >= a && w - 1.0D <= b)) {
					clear = false;
					break;
				}
			}
			if (clear) {
				return w;
			}
			t += 0.0025D;
		}
		return t - Math.floor(t);
	}

	private static Runner spawn(ServerLevel level, RaceTrack track, ChocoboColor colour, double lane, double start, long seed) {
		ChocoboEntity bird = ModEntities.CHOCOBO.get().create(level);
		RaceClass rc = track.getRaceClass();
		bird.setColor(colour);
		bird.setAge(0);
		bird.setPersistenceRequired();
		bird.setRaceClass(rc);
		bird.setGrade(ChocoboGrade.byRank(Math.min(4, rc.getId() + 1)));
		bird.setGenes(40, 40, 40, 40);
		int training = RaceScoring.fieldTraining(rc.getId(), false);
		bird.addTraining(training, training, training, training);
		bird.setRaceNpc(true);
		bird.setRacing(true);
		bird.fillStamina();
		bird.getRandom().setSeed(seed);
		RacePoint at = track.pointAtLane(start, lane);
		double[] tg = track.tangent(start);
		bird.moveTo(at.x(), at.y(), at.z(), (float) Math.toDegrees(Math.atan2(-tg[0], tg[1])), 0.0F);
		level.addFreshEntity(bird);
		RacerGoal goal = new RacerGoal(bird, track, lane, RacerProfile.of(rc, RacerProfile.Role.FIELD));
		goal.paceScale = RaceScoring.fieldLandSpeed(colour, rc) / Math.max(0.05D, colour.landSpeed());
		goal.totalLaps = 1;
		goal.running = true;
		bird.installRacer(goal);
		bird.inventory().setItem(tk.darrow.chocobosreborn.menu.ChocoboInventoryMenu.SADDLE, new ItemStack(ModItems.SADDLE.get()));
		return new Runner(bird, goal, colour, lane, start);
	}

	/** {@code RaceSession.rescue}: back on the road where it left it, held a second, a ghost a while longer. */
	private static void rescue(RaceTrack track, Runner r, int tick) {
		ChocoboEntity e = r.bird;
		double t = r.lap.lastProgress();
		RacePoint at = tk.darrow.chocobosreborn.race.RaceSession.setBackPoint((ServerLevel) e.level(), track, e, t);
		double[] tg = track.tangent(t);
		e.moveTo(at.x(), at.y(), at.z(), (float) Math.toDegrees(Math.atan2(-tg[0], tg[1])), 0.0F);
		e.setDeltaMovement(Vec3.ZERO);
		e.setRaceHeld(true);
		e.setRaceGhost(true);
		r.heldUntil = tick + RaceScoring.RESCUE_HOLD_TICKS;
		r.ghostUntil = r.heldUntil + RacerContact.RESCUE_GHOST_TICKS;
		r.lap.rescued();
		r.roadX = at.x();
		r.roadZ = at.z();
	}

	private static String describe(RaceTrack track, RaceCourseLayout layout, Runner r, double progress, ServerLevel level) {
		ChocoboEntity e = r.bird;
		String near = nearFeature(track, progress);
		BlockPos bp = e.blockPosition();
		return String.format(Locale.ROOT,
				"%s %s start-lane %.1f from %.4f: progress %.4f lane %.2f pos (%.2f, %.2f, %.2f) onCourse=%b collide=%b ground=%b feet=%s below=%s rescues=%d setBacks=%d mode=%s near %s",
				track.name(), r.colour, r.lane, r.start, progress, track.laneAt(progress, e.getX(), e.getZ()), e.getX(), e.getY(), e.getZ(),
				layout.onCourse(e.getX(), e.getZ()), e.horizontalCollision, e.onGround(),
				level.getBlockState(bp).getBlock().getDescriptionId().replace("block.minecraft.", ""),
				level.getBlockState(bp.below()).getBlock().getDescriptionId().replace("block.minecraft.", ""), r.rescues, r.setBacks,
				r.goal.recoveryMode(), near);
	}

	/** The terrain feature nearest {@code progress}: "on RIDGE[...] +3.1b", "WATER[...] end+5.6b". */
	static String nearFeature(RaceTrack track, double progress) {
		double lap = track.lapLength();
		String near = "";
		double bestD = Double.MAX_VALUE;
		for (RaceTrack.Feature f : track.terrainFeatures()) {
			double toStart = wrap(progress - f.start()) * lap, toEnd = wrap(progress - f.end()) * lap;
			String s;
			double d;
			if (progress >= f.start() && progress <= f.end()) {
				s = String.format(Locale.ROOT, "on %s[%.4f-%.4f] +%.1fb", f.type(), f.start(), f.end(), toStart);
				d = 0.0D;
			} else if (Math.abs(toStart) < Math.abs(toEnd)) {
				s = String.format(Locale.ROOT, "%s[%.4f-%.4f] start%+.1fb", f.type(), f.start(), f.end(), toStart);
				d = Math.abs(toStart);
			} else {
				s = String.format(Locale.ROOT, "%s[%.4f-%.4f] end%+.1fb", f.type(), f.start(), f.end(), toEnd);
				d = Math.abs(toEnd);
			}
			if (d < bestD) {
				bestD = d;
				near = s;
			}
		}
		return near;
	}

	/** The 3 x 3 columns round the bird's feet, one below to one above: "y:dx,dz=block" for anything not air. */
	static String around(ServerLevel level, ChocoboEntity e) {
		StringBuilder sb = new StringBuilder();
		BlockPos c = e.blockPosition();
		for (int dy = -1; dy <= 2; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					var s = level.getBlockState(c.offset(dx, dy, dz));
					if (!s.isAir()) {
						sb.append(dy).append(':').append(dx).append(',').append(dz).append('=')
								.append(s.getBlock().getDescriptionId().replace("block.minecraft.", "")).append(' ');
					}
				}
			}
		}
		return String.format(Locale.ROOT, "yaw %.0f v (%.2f, %.2f, %.2f) at %s: %s", e.getYRot(), e.getDeltaMovement().x,
				e.getDeltaMovement().y, e.getDeltaMovement().z, c.toShortString(), sb);
	}

	/** Distance to the nearest other bird of the sweep still on the course. */
	private static double nearest(List<Runner> runners, Runner me) {
		double best = 99.0D;
		for (Runner o : runners) {
			if (o != me && !o.bird.isRemoved()) {
				best = Math.min(best, o.bird.position().distanceTo(me.bird.position()));
			}
		}
		return best;
	}

	private static double wrap(double d) {
		return d - Math.floor(d + 0.5D);
	}

	static void force(ServerLevel level, RaceTrack track, boolean on) {
		for (long key : RaceCourseLayout.of(track).chunks()) {
			level.setChunkForced((int) (key >> 32), (int) key, on);
		}
	}
}
