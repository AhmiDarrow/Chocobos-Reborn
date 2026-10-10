package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import tk.darrow.chocobosreborn.ChocobosReborn;

/**
 * Courses laid while their heat counts down, not in one tick. A course's first build (and every
 * relay after a {@link SquareBuilder#COURSE_VERSION} bump) worked out a plan of ~190k cells and set
 * them all, plus the slot sweep, inside the tick a heat was posted: a freeze of seconds for
 * everyone on the server. Now the plan is worked out on a background thread when the heat is posted
 * and the island goes down a chunk at a time inside a few milliseconds a tick. A heat that starts
 * first finishes the rest at once ({@link SquareBuilder#buildTrack} calls {@link #finishNow}).
 * Server thread only, apart from the plan itself.
 */
public final class CourseBuilds {
	/** Server-thread time a tick may spend laying courses. */
	private static final long BUDGET_NANOS = 5_000_000L;

	private record Plan(RaceCourseLayout layout, long[] order, Set<Long> clear,
	                    Map<Long, List<Map.Entry<RaceCourseLayout.Cell, String>>> cells) {
	}

	private static final class Job {
		final CompletableFuture<Plan> plan;
		int next;
		int cleared;

		Job(CompletableFuture<Plan> plan) {
			this.plan = plan;
		}

		/** Lay chunks until done (true) or past the deadline. */
		boolean step(ServerLevel level, Plan p, long deadline) {
			Map<RaceCourseLayout.Cell, String> blocks = p.layout().blocks();
			int lo = Math.max(level.getMinBuildHeight(), p.layout().minY() - 16);
			int hi = Math.min(level.getMaxBuildHeight() - 1, p.layout().maxY() + 32);
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			while (next < p.order().length) {
				long key = p.order()[next++];
				if (p.clear().contains(key)) {
					cleared += SquareBuilder.clearChunk(level, blocks, lo, hi, (int) (key >> 32), (int) key);
				}
				List<Map.Entry<RaceCourseLayout.Cell, String>> here = p.cells().get(key);
				if (here != null) {
					for (Map.Entry<RaceCourseLayout.Cell, String> e : here) {
						RaceCourseLayout.Cell c = e.getKey();
						SquareBuilder.place(level, pos.set(c.x(), c.y(), c.z()), SquareBuilder.state(level, e.getValue()));
					}
				}
				if (System.nanoTime() > deadline) {
					return next >= p.order().length;
				}
			}
			return true;
		}
	}

	private static final Map<RaceTrack, Job> JOBS = new EnumMap<>(RaceTrack.class);

	private CourseBuilds() {
	}

	/** A heat was posted on {@code track}: start laying it if it is not laid yet. */
	public static void request(ServerLevel level, RaceTrack track) {
		SquareData data = SquareData.get(level);
		SquareBuilder.syncCourseVersion(data);
		if (data.isBuilt(track) || JOBS.containsKey(track)) {
			return;
		}
		boolean stale = data.isStale(track);
		JOBS.put(track, new Job(CompletableFuture.supplyAsync(() -> plan(track, stale), Util.backgroundExecutor())));
	}

	/** Off the server thread: the plan, its cells by chunk, and the chunks to sweep (the whole slot when stale). */
	private static Plan plan(RaceTrack track, boolean stale) {
		RaceCourseLayout layout = RaceCourseLayout.adopt(track, RaceCourseLayout.build(track));
		Map<Long, List<Map.Entry<RaceCourseLayout.Cell, String>>> cells = new HashMap<>();
		for (Map.Entry<RaceCourseLayout.Cell, String> e : layout.blocks().entrySet()) {
			RaceCourseLayout.Cell c = e.getKey();
			cells.computeIfAbsent(RaceCourseLayout.chunkKey(c.x() >> 4, c.z() >> 4), k -> new ArrayList<>()).add(e);
		}
		// an island an older plan laid may have been much bigger: sweep its whole slot, not just the new box
		Set<Long> clear = new LinkedHashSet<>(stale ? layout.slotClearChunks() : layout.clearChunks());
		Set<Long> all = new LinkedHashSet<>(cells.keySet());
		all.addAll(clear);
		long[] order = new long[all.size()];
		int i = 0;
		for (long key : all) {
			order[i++] = key;
		}
		return new Plan(layout, order, clear, cells);
	}

	/** Server tick: lay what fits in the budget. */
	public static void tick(ServerLevel square) {
		if (JOBS.isEmpty()) {
			return;
		}
		long deadline = System.nanoTime() + BUDGET_NANOS;
		Iterator<Map.Entry<RaceTrack, Job>> it = JOBS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<RaceTrack, Job> entry = it.next();
			RaceTrack track = entry.getKey();   // read before it.remove(): an EnumMap entry is dead after it
			Job job = entry.getValue();
			if (!job.plan.isDone()) {
				continue;
			}
			Plan p = planOf(track, job);
			if (p == null) {
				it.remove();   // buildTrack lays it the old way when the heat starts
				continue;
			}
			if (job.step(square, p, deadline)) {
				it.remove();
				SquareBuilder.finishTrack(square, track, p.layout(), job.cleared);
			}
			if (System.nanoTime() > deadline) {
				return;
			}
		}
	}

	/** The heat is starting: lay whatever is left now. False if there was no build under way. */
	static boolean finishNow(ServerLevel level, RaceTrack track) {
		Job job = JOBS.remove(track);
		if (job == null) {
			return false;
		}
		Plan p = planOf(track, job);
		if (p == null) {
			return false;
		}
		job.step(level, p, Long.MAX_VALUE);
		SquareBuilder.finishTrack(level, track, p.layout(), job.cleared);
		return true;
	}

	private static Plan planOf(RaceTrack track, Job job) {
		try {
			return job.plan.join();
		} catch (RuntimeException error) {
			ChocobosReborn.LOGGER.error("Whiskerwind: planning {} off the server thread failed; it is laid when its heat starts", track.id(), error);
			return null;
		}
	}

	/** Server stop: a half-laid course is relaid from the start next time (it is not marked built). */
	public static void reset() {
		JOBS.clear();
	}
}
