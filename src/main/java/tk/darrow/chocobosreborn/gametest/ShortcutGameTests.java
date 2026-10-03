package tk.darrow.chocobosreborn.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.breed.ChocoboColor;
import tk.darrow.chocobosreborn.breed.ChocoboGrade;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.entity.ModEntities;
import tk.darrow.chocobosreborn.item.ModItems;
import tk.darrow.chocobosreborn.race.RaceClass;
import tk.darrow.chocobosreborn.race.RaceManager;
import tk.darrow.chocobosreborn.race.RacePoint;
import tk.darrow.chocobosreborn.race.RaceScoring;
import tk.darrow.chocobosreborn.race.RaceTrack;
import tk.darrow.chocobosreborn.race.RacerGoal;
import tk.darrow.chocobosreborn.race.RacerProfile;
import tk.darrow.chocobosreborn.race.SquareBuilder;

/**
 * Every colour feature on every course is a shortcut: the bird that suits it (a Green over a ridge,
 * a Blue through the water, a Gold over lava) gets from just before the feature's detour opening to just past its rejoin in no
 * more ticks than the same bird at the same pace going round the detour (a Yellow). Each bird runs
 * the stretch alone, one after the other. {@code SHORTCUT} lines log both times and the gain, and
 * {@code SHORTCUT-TRACE} the suited bird tick by tick.
 * <p>1.1.9 (climb 1.2 a tick, landing on top, pace carried): B_KOPJE +7, B_CANYON +4, S_SKYWAY +2,
 * A_CRYSTAL 0, the B_FORD water 0 (Ahmi: the water is "a shortcut by just a bit, which is great").
 * At the ladder rate the ridges were -19, 0, -37 and -21.
 *
 * <p>Ahmi, 2026-10-02: the hill climbs "usually cost the racer time": the ridge face was climbed at
 * vanilla's ladder rate (0.2 a tick less gravity, about 8.5 ticks a block), slower than the detour.
 */
@GameTestHolder(ChocobosReborn.MOD_ID)
@PrefixGameTestTemplate(false)
public class ShortcutGameTests {
	/** Blocks before the opening the timing starts, and past the rejoin it stops. */
	private static final double MARGIN = 8.0D;
	/** Blocks of run-up before the timing starts. */
	private static final double RUN_UP = 40.0D;

	/** Most ticks one bird gets for one stretch before the leg is called stuck. */
	private static final int LEG_LIMIT = 900;

	/** The colour that takes a feature's direct line: Blue walks the water, Green climbs, Gold walks lava (Flame does not race). */
	static ChocoboColor suited(RaceTrack.Feature.Type type) {
		return switch (type) {
			case WATER -> ChocoboColor.BLUE;
			case RIDGE -> ChocoboColor.GREEN;
			case LAVA -> ChocoboColor.GOLD;
			default -> null;
		};
	}

	/** One test per course with water, a ridge or lava: every such feature on it, in lap order. */
	@GameTestGenerator
	public static Collection<TestFunction> shortcuts() {
		List<TestFunction> out = new ArrayList<>();
		for (RaceTrack track : RaceTrack.values()) {
			List<RaceTrack.Feature> fs = features(track);
			if (fs.isEmpty()) {
				continue;
			}
			String batch = "shortcut_" + track.getRaceClass().name().toLowerCase(Locale.ROOT) + (track.isSprint() ? "_sprint" : "_gp");
			out.add(new TestFunction(batch, "shortcut." + track.id(), ChocobosReborn.MOD_ID + ":empty",
					fs.size() * 2 * LEG_LIMIT + 400, 0L, true, helper -> run(helper, track, fs)));
		}
		return out;
	}

	static List<RaceTrack.Feature> features(RaceTrack track) {
		List<RaceTrack.Feature> fs = new ArrayList<>();
		for (RaceTrack.Feature f : track.terrainFeatures()) {
			if (suited(f.type()) != null) {
				fs.add(f);
			}
		}
		return fs;
	}

	/** One bird over one stretch. */
	private record Leg(RaceTrack.Feature feature, ChocoboColor colour, double from, double to, double spawnAt, double pace,
			boolean standingStart) {
	}

	/**
	 * Ticks a feature may lose when its run-up is cut short by the feature before it (both birds
	 * start from a standstill a few blocks before it: S_ECLIPSE's ridge after its bog, S_KEEP's
	 * after its lava, S_SKYWAY's after its water, -1 to -2). Every other feature must gain.
	 */
	private static final int STANDING_START_SLACK = 2;

	private static void run(GameTestHelper helper, RaceTrack track, List<RaceTrack.Feature> fs) {
		ServerLevel level = helper.getLevel();
		RaceManager.testLevel = level;
		AiLapSweepGameTests.force(level, track, true);
		SquareBuilder.buildTrack(level, track);
		double lap = track.lapLength();
		List<Leg> legs = new ArrayList<>();
		for (RaceTrack.Feature f : fs) {
			double from = f.start() - RaceTrack.DETOUR_CONNECT - MARGIN / lap;
			double to = f.end() + RaceTrack.DETOUR_CONNECT + MARGIN / lap;
			// start on plain road: past the end of any feature (a bog, another ridge) in the run-up,
			// never inside one (S_RIFT's second ridge set both birds down in its first)
			double spawnAt = from - RUN_UP / lap;
			for (RaceTrack.Feature g : track.terrainFeatures()) {
				double clear = g.end() + RaceTrack.DETOUR_CONNECT + 3.0D / lap;
				if (g != f && g.start() < f.start() && clear > spawnAt) {
					spawnAt = clear;
				}
			}
			from = Math.max(from, spawnAt + 6.0D / lap);
			// and stop before the next feature's swing: a bird that does not suit it brakes into
			// its detour there (S_SKYWAY's water runs into a ridge), which is that feature's cost
			for (RaceTrack.Feature g : track.terrainFeatures()) {
				double brake = g.start() - RaceTrack.DETOUR_CONNECT - (tk.darrow.chocobosreborn.race.RacerLine.CONNECTOR_BRAKE_LEAD + 2.0D) / lap;
				if (g != f && g.start() > f.end() && brake < to) {
					to = Math.max(f.end() + 2.0D / lap, brake);
				}
			}
			ChocoboColor suited = suited(f.type());
			// both birds at the suited colour's field pace: only the route differs
			double pace = RaceScoring.fieldLandSpeed(suited, track.getRaceClass());
			boolean standing = spawnAt > from - RUN_UP / lap + 1.0E-9D && (from - spawnAt) * lap < RUN_UP / 2.0D;
			legs.add(new Leg(f, suited, from, to, spawnAt, pace, standing));
			legs.add(new Leg(f, ChocoboColor.YELLOW, from, to, spawnAt, pace, standing));
		}
		int[] ticks = new int[legs.size()];
		double[] path = new double[legs.size()];
		Timer[] cur = {null};
		int[] which = {-1};
		boolean[] ready = {false};
		helper.onEachTick(() -> {
			if (!ready[0] || which[0] >= legs.size()) {
				return;
			}
			Timer tm = cur[0];
			if (tm == null) {
				which[0]++;
				if (which[0] < legs.size()) {
					Leg leg = legs.get(which[0]);
					cur[0] = new Timer(spawn(level, track, leg.colour(), leg.pace(), leg.spawnAt()), leg.spawnAt());
				}
				return;
			}
			Leg leg = legs.get(which[0]);
			tm.tick++;
			ChocoboEntity e = tm.bird;
			double p = track.progressAt(e.getX(), e.getZ(), tm.last);
			tm.last = p;
			if (tm.startTick < 0 && wrap(p - leg.from()) >= 0.0D) {
				tm.startTick = tm.tick;
				tm.x = e.getX();
				tm.z = e.getZ();
			}
			if (tm.startTick >= 0) {
				tm.path += Math.hypot(e.getX() - tm.x, e.getZ() - tm.z);
				tm.x = e.getX();
				tm.z = e.getZ();
			}
			if (e.horizontalCollision && tm.dumps < 2 && wrap(p - leg.feature().end()) > 0.0D) {
				tm.dumps++;
				tm.trace.append(String.format(Locale.ROOT, " {pressed past the end at (%.2f, %.2f, %.2f) yaw %.0f: %s}", e.getX(), e.getY(),
						e.getZ(), e.getYRot(), AiLapSweepGameTests.around(level, e)));
			}
			if (tm.startTick >= 0 || tm.tick % 5 == 0) {
				tm.trace.append(String.format(Locale.ROOT, " [%d p%.4f l%.1f y%.2f h%.2f vy%.2f g%s c%s cl%s st%d]", tm.tick - tm.startTick, p,
						track.laneAt(p, e.getX(), e.getZ()), e.getY(), e.getDeltaMovement().horizontalDistance(), e.getDeltaMovement().y,
						e.onGround() ? 1 : 0, e.horizontalCollision ? 1 : 0, e.onClimbable() ? 1 : 0, e.stamina()));
			}
			boolean done = tm.startTick >= 0 && wrap(p - leg.to()) >= 0.0D;
			if (done || tm.tick > LEG_LIMIT) {
				ticks[which[0]] = done ? tm.tick - tm.startTick : -1;
				path[which[0]] = tm.path;
				ChocobosReborn.LOGGER.info("SHORTCUT-TRACE {} {} {} {}:{}{}", track.name(), leg.feature().type(),
						String.format(Locale.ROOT, "%.3f", leg.feature().start()), leg.colour(), tm.trace,
						done ? "" : " STUCK " + AiLapSweepGameTests.around(level, e));
				e.discard();
				cur[0] = null;
			}
		});
		helper.startSequence().thenWaitUntil(() -> AiLapSweepGameTests.islandTicking(helper, level, track))
				.thenExecute(() -> ready[0] = true)
				.thenWaitUntil(() -> helper.assertTrue(which[0] >= legs.size(), track.name() + " legs running"))
				.thenExecute(() -> {
					AiLapSweepGameTests.force(level, track, false);
					List<String> bad = new ArrayList<>();
					for (int i = 0; i < legs.size(); i += 2) {
						Leg leg = legs.get(i);
						RaceTrack.Feature f = leg.feature();
						int over = ticks[i], round = ticks[i + 1], gain = round - over;
						ChocobosReborn.LOGGER.info("SHORTCUT {} {} {}: {} {} ticks ({} blocks), {} round the detour {} ticks ({} blocks), gain {} ticks",
								track.name(), f.type(), String.format(Locale.ROOT, "%.3f-%.3f", f.start(), f.end()), leg.colour(), over,
								String.format(Locale.ROOT, "%.0f", path[i]), ChocoboColor.YELLOW, round,
								String.format(Locale.ROOT, "%.0f", path[i + 1]), gain);
						if (over < 0 || round < 0) {
							bad.add(f.type() + " " + String.format(Locale.ROOT, "%.3f", f.start()) + " stuck (" + over + " / " + round + ")");
						} else if (gain < (leg.standingStart() ? -STANDING_START_SLACK : 0)) {
							bad.add(f.type() + " " + String.format(Locale.ROOT, "%.3f", f.start()) + " costs " + leg.colour() + " "
									+ (-gain) + " ticks (" + over + " over, " + round + " round)");
						}
					}
					helper.assertTrue(bad.isEmpty(), track.name() + ": " + String.join(" | ", bad));
				}).thenSucceed();
	}

	private static final class Timer {
		final ChocoboEntity bird;
		double last;
		int tick, startTick = -1;
		double x, z, path;
		int dumps;
		final StringBuilder trace = new StringBuilder();

		Timer(ChocoboEntity bird, double start) {
			this.bird = bird;
			this.last = start;
		}
	}

	/** A field bird of the class at {@code pace} (movement speed), on the centre line at {@code start}. */
	private static ChocoboEntity spawn(ServerLevel level, RaceTrack track, ChocoboColor colour, double pace, double start) {
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
		bird.getRandom().setSeed(5L);
		RacePoint at = track.pointAtLane(start, 0.0D);
		double[] tg = track.tangent(start);
		bird.moveTo(at.x(), at.y(), at.z(), (float) Math.toDegrees(Math.atan2(-tg[0], tg[1])), 0.0F);
		level.addFreshEntity(bird);
		RacerGoal goal = new RacerGoal(bird, track, 0.0D, RacerProfile.of(rc, RacerProfile.Role.FIELD));
		goal.paceScale = pace / Math.max(0.05D, colour.landSpeed());
		goal.totalLaps = 1;
		goal.running = true;
		bird.installRacer(goal);
		bird.inventory().setItem(tk.darrow.chocobosreborn.menu.ChocoboInventoryMenu.SADDLE, new ItemStack(ModItems.SADDLE.get()));
		return bird;
	}

	private static double wrap(double d) {
		return d - Math.floor(d + 0.5D);
	}
}
