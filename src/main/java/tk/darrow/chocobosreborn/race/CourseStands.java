package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where a course's grandstands go and who sits in them. Pure (no Minecraft types), so
 * the server lays the stands from it ({@link RaceCourseLayout}) and the client draws the
 * crowd from it ({@code client.CourseCrowdRenderer}) without building the block plan.
 * <p>
 * Every course keeps its main stand in the infield along the start straight; the class
 * decides how many more it gets (C 2, B 4, A 6, S 9, alternating inside and outside the
 * loop), how deep they are (C 3-4 rows up to S 5-6) and how many fans fill them (about
 * 40 / 80 / 140 / 220 a course). A site has to be on a straight or a gentle sweeper, level,
 * clear of the start zone, every terrain feature and its detour, the boost strips and the
 * landmark; and because a circuit folds back on itself, no block of a stand may come within
 * three blocks of any road tile (six for the tall back wall) or of another stand.
 */
public final class CourseStands {
	/** A stand as the crowd renderer needs it: its box (for culling), a light probe and its fans. */
	public record Stand(int index, int side, int rows, boolean main, int minX, int minY, int minZ,
			int maxX, int maxY, int maxZ, int lightX, int lightY, int lightZ, int firstFan, int fanCount) {
	}

	/** One seat-row cell as the plan leaves it: the row that owns the column, seat or aisle, stripe, local ground. */
	record Cell(int x, int z, int row, boolean seat, int stripe, int ground, float yaw, float along) {
	}

	/** Build data for one stand. */
	record Plan(int index, double from, double to, int side, int rows, int y, boolean main, Tier tier) {
		double back() {
			return BASE + rows * 2.0D + 0.5D;
		}

		int roofY() {
			return y + rows + (tier == Tier.GRAND ? 5 : 4);
		}
	}

	/** Look of a class's stands: wooden benches for C, the quartz stand for B / A, a roofed grand stand for S. */
	enum Tier { BENCH, STAND, GRAND }

	/** Offset of the first seat row from the centre line (the road edge is at ROAD_HALF). */
	static final double BASE = RaceTrack.ROAD_HALF + 5.0D;
	/** A stand block may not be within this squared distance (tile centres) of a road tile. */
	static final int CLEAR2 = 9;
	/** Nor may the back wall be this close: it would wall off the view from another leg. */
	private static final int BACK_CLEAR2 = 36;
	/** Blocks of level ground either side of a stand along the road before the island tapers back. */
	static final double GROUND_RUN = 6.0D;

	private static final Map<RaceTrack, CourseStands> CACHE = new EnumMap<>(RaceTrack.class);

	private final RaceTrack track;
	private final double lap;
	private final List<Plan> plans = new ArrayList<>();
	private final List<List<Cell>> cells = new ArrayList<>();
	private final List<Stand> stands = new ArrayList<>();
	private final List<RaceCourseLayout.FanPost> fans = new ArrayList<>();
	// distance grid over the island: squared distance (capped) from each tile to the nearest road tile
	private final int gx0, gz0, gw, gh;
	private final byte[] roadDist;
	/** Tiles taken by a stand, grown by two: other stands and scenery keep off. */
	private final boolean[] claimed;

	/** Stands and crowd for a course: cheap enough for the client (road tiles only, no block plan). */
	public static CourseStands of(RaceTrack track) {
		synchronized (CACHE) {
			CourseStands s = CACHE.get(track);
			if (s != null) {
				return s;
			}
		}
		CourseStands made = new CourseStands(track, RaceCourseLayout.roadTiles(track));
		synchronized (CACHE) {
			return CACHE.computeIfAbsent(track, t -> made);
		}
	}

	/** For the layout, which has already laid its road tiles. */
	static CourseStands of(RaceTrack track, Set<RaceCourseLayout.Tile> road) {
		synchronized (CACHE) {
			return CACHE.computeIfAbsent(track, t -> new CourseStands(t, road));
		}
	}

	private CourseStands(RaceTrack track, Set<RaceCourseLayout.Tile> road) {
		this.track = track;
		this.lap = track.lapLength();
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (RaceCourseLayout.Tile t : road) {
			minX = Math.min(minX, t.x());
			minZ = Math.min(minZ, t.z());
			maxX = Math.max(maxX, t.x());
			maxZ = Math.max(maxZ, t.z());
		}
		int pad = 48;
		gx0 = minX - pad;
		gz0 = minZ - pad;
		gw = maxX - minX + 2 * pad + 1;
		gh = maxZ - minZ + 2 * pad + 1;
		roadDist = new byte[gw * gh];
		claimed = new boolean[gw * gh];
		java.util.Arrays.fill(roadDist, Byte.MAX_VALUE);
		int r = 8;
		for (RaceCourseLayout.Tile t : road) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					int i = cell(t.x() + dx, t.z() + dz);
					int d2 = dx * dx + dz * dz;
					if (i >= 0 && d2 < roadDist[i]) {
						roadDist[i] = (byte) d2;
					}
				}
			}
		}
		planStands();
		for (Plan p : plans) {
			cells.add(seatCells(p));
		}
		seatFans();
	}

	// ------------------------------------------------------------ class numbers

	/** Stands beyond the main one. */
	static int extraStands(RaceClass c) {
		return switch (c) {
			case C -> 2;
			case B -> 4;
			case A -> 6;
			case S -> 9;
		};
	}

	/** Fans a course of this class seats (before the per-course wobble of up to 10 %). */
	static int fanTarget(RaceClass c) {
		return switch (c) {
			case C -> 40;
			case B -> 80;
			case A -> 140;
			case S -> 220;
		};
	}

	private static int mainRows(RaceClass c) {
		return switch (c) {
			case C, B -> 4;
			case A -> 5;
			case S -> 6;
		};
	}

	private static int extraRows(RaceClass c, int j) {
		return switch (c) {
			case C -> 3 + (j & 1);
			case B -> 4;
			case A -> 4 + (j & 1);
			case S -> 5 + (j & 1);
		};
	}

	/** Length along the road of an extra stand, in blocks. */
	private static double extraLength(RaceClass c) {
		return switch (c) {
			case C -> 22.0D;
			case B -> 26.0D;
			case A -> 28.0D;
			case S -> 32.0D;
		};
	}

	private static Tier tier(RaceClass c) {
		return switch (c) {
			case C -> Tier.BENCH;
			case B, A -> Tier.STAND;
			case S -> Tier.GRAND;
		};
	}

	// ------------------------------------------------------------ siting

	private void planStands() {
		RaceClass rc = track.getRaceClass();
		Tier tier = tier(rc);
		// the main stand: the infield beside the start straight, as it always was
		double mainFrom = 6.0D / lap, mainTo = Math.min(50.0D, lap * 0.06D) / lap;
		Plan main = null;
		for (int rows = mainRows(rc); rows >= 3 && main == null; rows--) {
			Plan p = new Plan(0, mainFrom, mainTo, 1, rows, level(mainFrom, mainTo), true, tier);
			if (footprintClear(p)) {
				main = p;
			}
		}
		if (main == null) {
			main = new Plan(0, mainFrom, mainTo, 1, 3, level(mainFrom, mainTo), true, tier);
		}
		accept(main);
		int extra = extraStands(rc);
		int seed = track.ordinal();
		double len = extraLength(rc);
		for (int j = 0; j < extra; j++) {
			int side = ((j + seed) & 1) == 0 ? -1 : 1;
			double target = 0.07D + 0.9D * ((j + 0.5D) / extra);
			int rows = extraRows(rc, j);
			Plan p = null;
			// straights first; then gentler sweepers, then a shorter stand; either side as a last resort.
			// The half and third-length stands only come into play on a short grand-prix lap crowded with
			// features (class S: ten stands on a 450-block lap); a course that seats every stand without them is unchanged
			double[][] tries = {{len, 0.30D}, {len, 0.55D}, {len * 0.7D, 0.55D}, {len * 0.7D, 0.8D}, {len * 0.5D, 0.8D}, {len * 0.5D, 1.2D}, {len * 0.35D, 1.2D}};
			for (double[] tr : tries) {
				p = search(plans.size(), target, side, rows, tr[0], tr[1], tier);
				if (p == null) {
					p = search(plans.size(), target, -side, rows, tr[0], tr[1], tier);
				}
				if (p == null && rows > 3) {
					p = search(plans.size(), target, side, rows - 1, tr[0], tr[1], tier);
				}
				if (p != null) {
					break;
				}
			}
			if (p != null) {
				accept(p);
			}
		}
	}

	/** First site from {@code target} outward (both ways round the lap) that passes every rule. */
	private Plan search(int index, double target, int side, int rows, double len, double maxTurn, Tier tier) {
		double span = len / lap;
		double step = 2.0D / lap;
		int n = (int) (0.5D / step) + 1;
		for (int i = 0; i <= n; i++) {
			for (int sgn = 1; sgn >= -1; sgn -= 2) {
				if (i == 0 && sgn < 0) {
					continue;
				}
				double from = target + sgn * i * step;
				from -= Math.floor(from);
				double to = from + span;
				if (!siteOk(from, to, side, maxTurn)) {
					continue;
				}
				Plan p = new Plan(index, from, to, side, rows, level(from, to), false, tier);
				if (footprintClear(p)) {
					return p;
				}
			}
		}
		return null;
	}

	/** The cheap rules: start zone, features, boosts, landmark, straightness, level, spacing on the same side. */
	private boolean siteOk(double from, double to, int side, double maxTurn) {
		if (from < 0.055D || to > 0.975D) {
			return false;
		}
		double margin = 12.0D / lap;
		double lm = RaceCourseLayout.landmarkT(track);
		double lmMargin = 22.0D / lap;
		if (from - lmMargin < lm && to + lmMargin > lm) {
			return false;
		}
		for (double t = from - margin; t <= to + margin; t += 1.0D / lap) {
			if (nearFeature(t)) {
				return false;
			}
			RaceTrack.Feature f = track.featureAt(t);
			if (f != null && f.type() == RaceTrack.Feature.Type.BOOST) {
				return false;
			}
		}
		if (track.turnAhead(from, (to - from) * lap) > maxTurn) {
			return false;
		}
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (double t = from; t <= to; t += 1.0D / lap) {
			int y = surf(t);
			lo = Math.min(lo, y);
			hi = Math.max(hi, y);
		}
		if (hi - lo > 2) {
			return false;
		}
		double gap = 8.0D / lap;
		for (Plan p : plans) {
			if (p.side() == side && from < p.to() + gap && to > p.from() - gap) {
				return false;
			}
		}
		return true;
	}

	/** No stand block near any road tile or another stand; the back wall well clear of the road. */
	private boolean footprintClear(Plan p) {
		int[] bad = {0};
		forFootprint(p, (x, z, o) -> {
			int i = cell(x, z);
			if (i < 0) {
				bad[0]++;
				return false;
			}
			int d2 = roadDist[i];
			if (d2 <= CLEAR2 || claimed[i] || (o >= p.back() - 0.75D && d2 <= BACK_CLEAR2)) {
				bad[0]++;
				return false;
			}
			return true;
		});
		return bad[0] == 0;
	}

	private void accept(Plan p) {
		plans.add(p);
		forFootprint(p, (x, z, o) -> {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					int i = cell(x + dx, z + dz);
					if (i >= 0) {
						claimed[i] = true;
					}
				}
			}
			return true;
		});
	}

	private interface FootprintVisitor {
		/** @return false to stop */
		boolean visit(int x, int z, double offset);
	}

	/** Every tile a stand's blocks can land on: front posts to back wall, the whole length, at half-block steps. */
	private void forFootprint(Plan p, FootprintVisitor v) {
		int steps = steps(p);
		for (int s = 0; s <= steps; s++) {
			double t = p.from() + (p.to() - p.from()) * s / steps;
			for (double o = BASE - 0.5D; o <= p.back() + 1.0E-6D; o += 0.5D) {
				double[] q = lane(track, t, p.side() * o);
				if (!v.visit(floor(q[0]), floor(q[1]), o)) {
					return;
				}
			}
		}
	}

	/** The stand's floor: the highest road surface alongside it (lower ground gets a plinth). */
	private int level(double from, double to) {
		int y = Integer.MIN_VALUE;
		for (double t = from; t <= to + 1.0E-9D; t += 0.5D / lap) {
			y = Math.max(y, surf(t));
		}
		return y;
	}

	// ------------------------------------------------------------ seats and fans

	/**
	 * The seat rows as the layout stamps them: row by row, half a block along and across,
	 * the front half of a row a seat and the back half the aisle; where two stamps share a
	 * column the later one wins, exactly as the block plan does.
	 */
	private List<Cell> seatCells(Plan p) {
		Map<Long, Cell> out = new LinkedHashMap<>();
		int steps = steps(p);
		for (int k = 0; k < p.rows(); k++) {
			for (int s = 0; s <= steps; s++) {
				double t = p.from() + (p.to() - p.from()) * s / steps;
				double[] tg = track.tangent(t);
				// toward the track: outward (tz, -tx) for an infield stand, inward for an outside one
				double vx = p.side() * tg[1], vz = -p.side() * tg[0];
				float yaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
				int ground = surf(t);
				for (double o = BASE + k * 2.0D; o < BASE + k * 2.0D + 2.0D; o += 0.5D) {
					double[] q = lane(track, t, p.side() * o);
					int x = floor(q[0]), z = floor(q[1]);
					boolean seat = o < BASE + k * 2.0D + 1.0D;
					out.put(RaceCourseLayout.roadKey(x, z), new Cell(x, z, k, seat, (s / 3 + k) % 2, ground, yaw, s / (float) steps));
				}
			}
		}
		return new ArrayList<>(out.values());
	}

	/**
	 * Fill the seats: a deterministic scatter weighted toward the front rows and the middle
	 * of each stand, at least three fans a stand, the course total from its class.
	 */
	private void seatFans() {
		RaceClass rc = track.getRaceClass();
		int base = fanTarget(rc);
		int wobble = Math.floorMod(track.ordinal() * 37 + 11, 11) - 5;   // -5..5 -> -10 %..+10 %
		int target = base + wobble * base / 50;
		record Slot(int stand, int order, double score) {
		}
		List<List<Slot>> perStand = new ArrayList<>();
		int total = 0;
		for (int si = 0; si < plans.size(); si++) {
			List<Slot> slots = new ArrayList<>();
			List<Cell> cs = cells.get(si);
			Set<Long> fixtures = fixtureKeys(plans.get(si));
			for (int ci = 0; ci < cs.size(); ci++) {
				Cell c = cs.get(ci);
				if (!c.seat() || fixtures.contains(RaceCourseLayout.roadKey(c.x(), c.z()))) {
					continue;
				}
				double r = unit(mix(track.ordinal() * 1_000_003L + si * 7919L, RaceCourseLayout.roadKey(c.x(), c.z())));
				double centre = Math.abs(c.along() - 0.5D) * 2.0D;
				slots.add(new Slot(si, ci, r * (1.0D + 0.12D * c.row() + 0.6D * centre)));
			}
			slots.sort((a, b) -> Double.compare(a.score(), b.score()));
			perStand.add(slots);
			total += slots.size();
		}
		target = Math.min(target, total);
		boolean[][] taken = new boolean[plans.size()][];
		for (int si = 0; si < plans.size(); si++) {
			taken[si] = new boolean[cells.get(si).size()];
		}
		int placed = 0;
		List<Slot> rest = new ArrayList<>();
		for (List<Slot> slots : perStand) {
			for (int i = 0; i < slots.size(); i++) {
				if (i < 3 && placed < target) {
					taken[slots.get(i).stand()][slots.get(i).order()] = true;
					placed++;
				} else {
					rest.add(slots.get(i));
				}
			}
		}
		rest.sort((a, b) -> Double.compare(a.score(), b.score()));
		for (int i = 0; i < rest.size() && placed < target; i++) {
			taken[rest.get(i).stand()][rest.get(i).order()] = true;
			placed++;
		}
		for (int si = 0; si < plans.size(); si++) {
			Plan p = plans.get(si);
			int first = fans.size();
			List<Cell> cs = cells.get(si);
			for (int ci = 0; ci < cs.size(); ci++) {
				if (taken[si][ci]) {
					Cell c = cs.get(ci);
					fans.add(new RaceCourseLayout.FanPost(c.x() + 0.5D, p.y() + c.row() + 1.5D, c.z() + 0.5D, c.yaw(), si));
				}
			}
			stands.add(box(p, first, fans.size() - first));
		}
	}

	private Stand box(Plan p, int firstFan, int fanCount) {
		int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		forFootprint(p, (x, z, o) -> {
			b[0] = Math.min(b[0], x);
			b[1] = Math.min(b[1], z);
			b[2] = Math.max(b[2], x);
			b[3] = Math.max(b[3], z);
			return true;
		});
		double mid = (p.from() + p.to()) / 2.0D;
		double[] probe = lane(track, mid, p.side() * (BASE + 0.5D));
		return new Stand(p.index(), p.side(), p.rows(), p.main(), b[0], p.y() - 3, b[1], b[2] + 1, p.roofY() + 4, b[3] + 1,
				floor(probe[0]), p.y() + 2, floor(probe[1]), firstFan, fanCount);
	}

	// ------------------------------------------------------------ queries

	public List<Stand> stands() {
		return Collections.unmodifiableList(stands);
	}

	public List<RaceCourseLayout.FanPost> fanPosts() {
		return Collections.unmodifiableList(fans);
	}

	List<Plan> plans() {
		return Collections.unmodifiableList(plans);
	}

	List<Cell> cells(int stand) {
		return cells.get(stand);
	}

	/** A tile a stand (or its two-block apron) takes: scenery keeps off. */
	boolean nearStand(int x, int z) {
		int i = cell(x, z);
		return i >= 0 && claimed[i];
	}

	/** Sides with a stand alongside t (with a few blocks' run-out): bit 1 inside, bit 2 outside. */
	int sidesAt(double t, double runBlocks) {
		int out = 0;
		double w = t - Math.floor(t);
		for (Plan p : plans) {
			double m = runBlocks / lap;
			if (w >= p.from() - m && w <= p.to() + m) {
				out |= p.side() > 0 ? 1 : 2;
			}
		}
		return out;
	}

	/**
	 * How far the island ground has to reach on one side at t for the stands there: the
	 * back wall plus three, easing back to {@code normal} over {@link #GROUND_RUN} blocks
	 * past either end so the rim tapers instead of stepping.
	 */
	double groundReach(double t, int side, double normal) {
		double reach = normal;
		double w = t - Math.floor(t);
		for (Plan p : plans) {
			if (p.side() != side) {
				continue;
			}
			double want = p.back() + 3.0D;
			double outside = Math.max(p.from() - w, w - p.to()) * lap;   // blocks beyond the nearer end
			if (outside <= 0.0D) {
				reach = Math.max(reach, want);
			} else if (outside < GROUND_RUN) {
				reach = Math.max(reach, want - (want - normal) * (outside / GROUND_RUN));
			}
		}
		return reach;
	}

	// ------------------------------------------------------------ geometry shared with the builder

	/** Stamps along a stand: every half block. */
	int steps(Plan p) {
		return (int) Math.ceil((p.to() - p.from()) * lap * 2.0D);
	}

	/** A front post (with its lamp) stands at this stamp. */
	static boolean postStep(int s) {
		return s % 8 == 0;
	}

	/** A banner hangs on the back wall at this stamp: every two blocks on a grand stand, four elsewhere. */
	static boolean bannerStep(Tier tier, int s) {
		int every = tier == Tier.GRAND ? 4 : 8;
		return s % every == every / 2;
	}

	/** Seat columns a post or a banner takes: nobody sits there. */
	private Set<Long> fixtureKeys(Plan p) {
		Set<Long> out = new java.util.HashSet<>();
		int steps = steps(p);
		for (int s = 0; s <= steps; s++) {
			double t = p.from() + (p.to() - p.from()) * s / steps;
			if (postStep(s)) {
				double[] q = lane(track, t, p.side() * (BASE - 0.5D));
				out.add(RaceCourseLayout.roadKey(floor(q[0]), floor(q[1])));
			}
			if (bannerStep(p.tier(), s)) {
				double[] q = lane(track, t, p.side() * (p.back() - 0.5D));
				out.add(RaceCourseLayout.roadKey(floor(q[0]), floor(q[1])));
			}
		}
		return out;
	}

	/**
	 * A point {@code offset} blocks off the line (positive inside), like
	 * {@link RaceTrack#pointAtLane} but with the normal blended between the spline's
	 * samples. The track's own normal turns in steps, one per block, which leaves gaps
	 * between stamps twenty blocks out on a bend: a stand would have holes in its rows.
	 * Returns {x, z}.
	 */
	static double[] lane(RaceTrack track, double t, double offset) {
		double w = t - Math.floor(t);
		int n = Math.max(16, (int) Math.round(track.lapLength()));
		double f = w * n;
		double i = Math.floor(f), u = f - i;
		double[] a = track.tangent((i + 0.5D) / n), b = track.tangent((i + 1.5D) / n);
		// blend from the middle of one sample to the middle of the next
		double[] c;
		double k;
		if (u < 0.5D) {
			c = track.tangent((i - 0.5D) / n);
			k = u + 0.5D;
			b = a;
			a = c;
		} else {
			k = u - 0.5D;
		}
		double tx = a[0] + (b[0] - a[0]) * k, tz = a[1] + (b[1] - a[1]) * k;
		double len = Math.hypot(tx, tz);
		if (len < 1.0E-9D) {
			tx = a[0];
			tz = a[1];
			len = 1.0D;
		}
		tx /= len;
		tz /= len;
		RacePoint p = track.pointAt(t);
		return new double[]{p.x() - tz * offset, p.z() + tx * offset};
	}

	// ------------------------------------------------------------ helpers

	private boolean nearFeature(double t) {
		double w = t - Math.floor(t);
		for (RaceTrack.Feature f : track.terrainFeatures()) {
			if (w >= f.start() - RaceTrack.DETOUR_CONNECT && w <= f.end() + RaceTrack.DETOUR_CONNECT) {
				return true;
			}
		}
		return false;
	}

	private int surf(double t) {
		return (int) track.groundY(t) - 1;
	}

	private int cell(int x, int z) {
		int gx = x - gx0, gz = z - gz0;
		if (gx < 0 || gz < 0 || gx >= gw || gz >= gh) {
			return -1;
		}
		return gz * gw + gx;
	}

	private static int floor(double v) {
		return (int) Math.floor(v);
	}

	private static long mix(long a, long b) {
		long z = a * 0x9E3779B97F4A7C15L + b;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private static double unit(long h) {
		return (h >>> 11) * 0x1.0p-53;
	}
}
