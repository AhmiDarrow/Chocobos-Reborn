package tk.darrow.chocobosreborn.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
import tk.darrow.chocobosreborn.race.RaceSession;
import tk.darrow.chocobosreborn.race.RaceTrack;
import tk.darrow.chocobosreborn.race.RacerContact;
import tk.darrow.chocobosreborn.race.RacerGoal;
import tk.darrow.chocobosreborn.race.RacerProfile;
import tk.darrow.chocobosreborn.race.SquareBuilder;

/**
 * A whole field of six AI birds off the real grid ({@link RaceTrack#stallPos}, the lanes
 * {@link RaceTrack#stallOffset} gives them), set up as {@code RaceSession.spawnField} sets up a
 * field bird, released together and solid (bumping on), run over several laps as a heat, with
 * the session's set-backs. The sweep ({@link AiLapSweepGameTests}) starts its birds spread round
 * the lap; this is the bunched start and the pack.
 *
 * <p>"Serah" (White, S_SKYWAY, the hub harness) sat at lap 0 + 0.17, on the first ridge, from
 * tick 300 to the end of the heat. Every stall (under 3 blocks of new ground in
 * {@link AiLapSweepGameTests#STALL_TICKS}) and every set-back fails the heat; {@code FIELD-HEAT}
 * lines log each bird's lap times and the field's mean lap, the pace the harness compares.
 */
@GameTestHolder(ChocobosReborn.MOD_ID)
@PrefixGameTestTemplate(false)
public class FieldHeatGameTests {
	/** Ticks the field waits on the grid before GO. */
	private static final int HOLD = 20;

	/** S_SKYWAY with the colours the S field climbs in (White, Black, Green), three heats. */
	@GameTest(template = "empty", timeoutTicks = 6000, batch = "field_heat_skyway")
	public static void skywayFieldClearsTheFirstRidge(GameTestHelper helper) {
		ChocoboColor[] s = {ChocoboColor.BLACK, ChocoboColor.WHITE, ChocoboColor.GREEN, ChocoboColor.BLACK, ChocoboColor.WHITE,
				ChocoboColor.WHITE};
		run(helper, RaceTrack.S_SKYWAY, s, 2, new long[]{11L, 12L, 13L});
	}

	/** A_CANOPY: an A field (every colour the class brings) over two laps, twice. */
	@GameTest(template = "empty", timeoutTicks = 6000, batch = "field_heat_canopy")
	public static void canopyFieldHoldsItsPace(GameTestHelper helper) {
		run(helper, RaceTrack.A_CANOPY, aField(), 2, new long[]{21L, 22L});
	}

	/** A_GROTTO: an A field over two laps, twice. */
	@GameTest(template = "empty", timeoutTicks = 6000, batch = "field_heat_grotto")
	public static void grottoFieldHoldsItsPace(GameTestHelper helper) {
		run(helper, RaceTrack.A_GROTTO, aField(), 2, new long[]{31L, 32L});
	}

	private static ChocoboColor[] aField() {
		return new ChocoboColor[]{ChocoboColor.YELLOW, ChocoboColor.GREEN, ChocoboColor.BLUE, ChocoboColor.BLACK, ChocoboColor.WHITE,
				ChocoboColor.BLUE};
	}

	/** One bird of a heat. */
	private static final class Bird {
		final ChocoboEntity e;
		final RacerGoal goal;
		final ChocoboColor colour;
		final int stall;
		final RaceLapProgress lap;
		final List<Integer> lapTicks = new ArrayList<>();
		double cum, best, lastRaw;
		int bestTick, heldUntil, ghostUntil, rescues, setBacks, finished = -1;
		String stall1;
		final java.util.ArrayDeque<String> recent = new java.util.ArrayDeque<>();

		Bird(ChocoboEntity e, RacerGoal goal, ChocoboColor colour, int stall, double start) {
			this.e = e;
			this.goal = goal;
			this.colour = colour;
			this.stall = stall;
			this.lap = new RaceLapProgress(start);
			this.lastRaw = start;
		}
	}

	/** Ticks a heat of {@code laps} gets: two and a half times the class field's clean laps, plus a start. */
	static int heatLimit(RaceTrack track, int laps) {
		return (int) Math.ceil(2.5D * laps * track.lapLength() / AiLapSweepGameTests.paceBlocksPerTick(track.getRaceClass())) + 300;
	}

	private static void run(GameTestHelper helper, RaceTrack track, ChocoboColor[] colours, int laps, long[] seeds) {
		ServerLevel level = helper.getLevel();
		RaceManager.testLevel = level;
		AiLapSweepGameTests.force(level, track, true);
		SquareBuilder.buildTrack(level, track);
		RaceCourseLayout layout = RaceCourseLayout.of(track);
		double lapLen = track.lapLength();
		int limit = heatLimit(track, laps);
		List<Bird> field = new ArrayList<>();
		int[] heat = {-1};
		int[] tick = {0};
		List<String> bad = new ArrayList<>();
		List<Double> meanLaps = new ArrayList<>();
		boolean[] ready = {false};
		helper.onEachTick(() -> {
			if (!ready[0]) {
				return;
			}
			if (field.isEmpty()) {
				if (heat[0] + 1 >= seeds.length) {
					return;
				}
				heat[0]++;
				tick[0] = 0;
				java.util.Random rng = new java.util.Random(seeds[heat[0]]);
				for (int i = 0; i < RaceSession.FIELD; i++) {
					field.add(spawn(level, track, colours[i % colours.length], i, laps, rng));
				}
				return;
			}
			tick[0]++;
			if (tick[0] == HOLD) {
				for (Bird b : field) {
					b.e.setRaceHeld(false);
					b.e.fillStamina();
					b.goal.running = true;
				}
			}
			if (tick[0] < HOLD) {
				return;
			}
			boolean all = true;
			for (Bird b : field) {
				if (b.finished >= 0) {
					continue;
				}
				all = false;
				step(track, layout, level, b, tick[0], lapLen, laps);
			}
			if (all || tick[0] >= limit) {
				StringBuilder sb = new StringBuilder();
				double sum = 0.0D;
				int n = 0;
				for (Bird b : field) {
					sb.append(String.format(Locale.ROOT, " %s@%d:%s%s", b.colour, b.stall, b.lapTicks,
							b.finished < 0 ? "DNF" : "", b.rescues > 0 ? "(rescues " + b.rescues + ")" : ""));
					for (int k = 1; k < b.lapTicks.size(); k++) {
						sum += b.lapTicks.get(k) - b.lapTicks.get(k - 1);
						n++;
					}
					if (b.finished < 0) {
						double p = track.progressAt(b.e.getX(), b.e.getZ(), b.lap.lastProgress());
						bad.add("heat " + heat[0] + " no finish in " + limit + " ticks: "
								+ (b.stall1 != null ? b.stall1 : describe(track, layout, b, p, level)));
					} else if (b.stall1 != null) {
						bad.add("heat " + heat[0] + " stalled: " + b.stall1);
					} else if (b.setBacks > 0) {
						bad.add("heat " + heat[0] + " " + b.colour + "@" + b.stall + " set back " + b.setBacks + " times");
					}
					b.e.discard();
				}
				double mean = n == 0 ? Double.NaN : sum / n;
				meanLaps.add(mean);
				ChocobosReborn.LOGGER.info("FIELD-HEAT {} heat {} ticks {} mean lap (after the first) {} limit {}:{}", track.name(), heat[0],
						tick[0], String.format(Locale.ROOT, "%.1f", mean), limit, sb);
				field.clear();
			}
		});
		helper.startSequence().thenWaitUntil(() -> AiLapSweepGameTests.islandTicking(helper, level, track))
				.thenExecute(() -> ready[0] = true)
				.thenWaitUntil(() -> helper.assertTrue(heat[0] + 1 >= seeds.length && field.isEmpty(), "heats running"))
				.thenExecute(() -> {
					AiLapSweepGameTests.force(level, track, false);
					ChocobosReborn.LOGGER.info("FIELD-HEAT {} DONE mean laps {}", track.name(), meanLaps);
					helper.assertTrue(bad.isEmpty(), track.name() + " field (" + bad.size() + "): " + String.join(" | ", bad));
				}).thenSucceed();
	}

	/** {@code RaceSession.spawnField} for stall {@code i}: grade, class training, form, pace, held on the grid. */
	private static Bird spawn(ServerLevel level, RaceTrack track, ChocoboColor colour, int i, int laps, java.util.Random rng) {
		RaceClass rc = track.getRaceClass();
		ChocoboEntity bird = ModEntities.CHOCOBO.get().create(level);
		RacePoint stall = track.stallPos(i, RaceSession.FIELD);
		bird.moveTo(stall.x(), stall.y(), stall.z(), track.facingYaw(), 0.0F);
		bird.setYHeadRot(track.facingYaw());
		bird.setAge(0);
		bird.setRaceNpc(true);
		bird.setPersistenceRequired();
		bird.setRacing(true);
		bird.setColor(colour);
		bird.setGrade(ChocoboGrade.byRank(Math.min(4, rc.getId() + 1)));
		bird.setRaceClass(rc);
		bird.setGenes(40, 40, 40, 40);
		int train = Math.max(0, Math.min(100, RaceScoring.fieldTraining(rc.getId(), false) + rng.nextInt(9) - 4));
		bird.addTraining(train, train, train, train);
		bird.fillStamina();
		bird.getRandom().setSeed(rng.nextLong());
		bird.inventory().setItem(tk.darrow.chocobosreborn.menu.ChocoboInventoryMenu.SADDLE, new ItemStack(ModItems.SADDLE.get()));
		level.addFreshEntity(bird);
		bird.setRaceHeld(true);
		double lane = RaceTrack.stallOffset(i, RaceSession.FIELD);
		RacerGoal goal = new RacerGoal(bird, track, lane, RacerProfile.of(rc, RacerProfile.Role.FIELD));
		goal.speed = 1.0D + RacerProfile.VARIANCE * (2.0D * rng.nextDouble() - 1.0D);
		goal.totalLaps = laps;
		goal.paceScale = RaceScoring.fieldLandSpeed(colour, rc) / Math.max(0.05D, colour.landSpeed());
		bird.installRacer(goal);
		return new Bird(bird, goal, colour, i, track.progressAt(stall.x(), stall.z()));
	}

	/** One tick of the session's bookkeeping for a field bird (RaceSession.tickRunning). */
	private static void step(RaceTrack track, RaceCourseLayout layout, ServerLevel level, Bird b, int tick, double lapLen, int laps) {
		ChocoboEntity e = b.e;
		if (b.heldUntil > 0 && tick >= b.heldUntil) {
			b.heldUntil = 0;
			e.setRaceHeld(false);
		}
		if (b.ghostUntil > 0 && tick >= b.ghostUntil) {
			b.ghostUntil = 0;
			e.setRaceGhost(false);
		}
		double progress = track.progressAt(e.getX(), e.getZ(), b.lap.lastProgress());
		boolean onCourse = layout.onCourse(e.getX(), e.getZ());
		double d = progress - b.lastRaw;
		d -= Math.floor(d + 0.5D);
		b.lastRaw = progress;
		b.cum += d * lapLen;
		if (tick % 2 == 0) {
			b.recent.addLast(String.format(Locale.ROOT, "[%d p%.4f l%.2f y%.2f v%.2f g%s c%s cl%s %s]", tick, progress,
					track.laneAt(progress, e.getX(), e.getZ()), e.getY(), e.getDeltaMovement().horizontalDistance(), e.onGround() ? "1" : "0",
					e.horizontalCollision ? "1" : "0", e.onClimbable() ? "1" : "0", b.goal.recoveryMode()));
			if (b.recent.size() > 30) {
				b.recent.removeFirst();
			}
		}
		if (b.cum > b.best + 3.0D) {
			b.best = b.cum;
			b.bestTick = tick;
		} else if (tick - b.bestTick >= AiLapSweepGameTests.STALL_TICKS && b.heldUntil == 0 && b.stall1 == null) {
			b.stall1 = describe(track, layout, b, progress, level);
			ChocobosReborn.LOGGER.warn("FIELD-HEAT STALL {} before {}", b.stall1, String.join(" ", b.recent));
		}
		boolean fell = RaceScoring.squareFallRescue(e.getY());
		RaceLapProgress.Step st = fell ? RaceLapProgress.Step.RESCUE : b.lap.step(progress, onCourse, RaceLapProgress.allowance(lapLen));
		if (st == RaceLapProgress.Step.NONE && b.goal.takeSetBack()) {
			b.setBacks++;
			ChocobosReborn.LOGGER.warn("FIELD-HEAT SET-BACK {} before {}", describe(track, layout, b, progress, level),
					String.join(" ", b.recent));
			st = RaceLapProgress.Step.RESCUE;
		}
		if (st == RaceLapProgress.Step.RESCUE) {
			b.rescues++;
			RacePoint at = RaceSession.setBackPoint(level, track, e, b.lap.lastProgress());
			double[] tg = track.tangent(b.lap.lastProgress());
			e.moveTo(at.x(), at.y(), at.z(), (float) Math.toDegrees(Math.atan2(-tg[0], tg[1])), 0.0F);
			e.setDeltaMovement(Vec3.ZERO);
			e.setRaceHeld(true);
			e.setRaceGhost(true);
			b.heldUntil = tick + RaceScoring.RESCUE_HOLD_TICKS;
			b.ghostUntil = b.heldUntil + RacerContact.RESCUE_GHOST_TICKS;
			b.lap.rescued();
		} else if (st == RaceLapProgress.Step.LAP) {
			b.lapTicks.add(tick - HOLD);
			b.goal.lapsDone = b.lapTicks.size();
			if (b.lapTicks.size() >= laps) {
				b.finished = tick;
				e.setRaceGhost(true);
			}
		}
	}

	private static String describe(RaceTrack track, RaceCourseLayout layout, Bird b, double progress, ServerLevel level) {
		ChocoboEntity e = b.e;
		return String.format(Locale.ROOT, "%s %s stall %d: laps %d progress %.4f lane %.2f pos (%.2f, %.2f, %.2f) onCourse=%b collide=%b ground=%b climb=%b rescues=%d setBacks=%d mode=%s near %s blocks %s racers%s",
				track.name(), b.colour, b.stall, b.lapTicks.size(), progress, track.laneAt(progress, e.getX(), e.getZ()), e.getX(), e.getY(),
				e.getZ(), layout.onCourse(e.getX(), e.getZ()), e.horizontalCollision, e.onGround(), e.onClimbable(), b.rescues, b.setBacks,
				b.goal.recoveryMode(), AiLapSweepGameTests.nearFeature(track, progress), AiLapSweepGameTests.around(level, e),
				AiLapSweepGameTests.racersNear(level, e));
	}
}
