package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic block plan for one course: a themed circuit island floating in
 * the void, stamped along the centre line (never a bounding-box scan):
 * <ul>
 * <li>the road band (|offset| <= ROAD_HALF) on the hill profile, kerbs striped on
 * the corners, rails outside (the sky road has none), the island rock tapering
 * away underneath and a margin of ground either side with the theme's decoration
 * (trees, hay, cacti, lava pools, crystals, rainbow pillars...);</li>
 * <li>features: the band becomes water / lava (two deep, walled), a ridge wall of
 * {@link RaceTrack#ridgeHeight()} blocks or a bog of mud, with a detour road laid
 * outside (DETOUR_INNER..DETOUR_OUTER) and connectors at both ends; boost strips
 * lay {@code chocobosreborn:boost_pad} across the band;</li>
 * <li>a chequered start / finish line, a painted six-stall grid, a yellow arrow
 * just past it, a start / finish gantry with lights clear of a mounted bird,
 * lamps on both verges, warning posts before terrain, and the grandstands: the
 * main one in the infield beside the start straight and more inside and outside the
 * loop by class ({@link CourseStands}), with {@link #fanPosts()} for the crowd the
 * client draws.</li>
 * </ul>
 */
public final class RaceCourseLayout {
	public record Cell(int x, int y, int z) {
	}

	public record Tile(int x, int z) {
	}

	/** Where a fan stands (feet), the yaw that faces the track, and the stand ({@link CourseStands#stands()}) it sits in. */
	public record FanPost(double x, double y, double z, float yaw, int stand) {
	}

	/** Course-name wall sign on the marshal's tower, facing the grid. */
	public record BoardPost(int x, int y, int z, String facing) {
	}

	private static final Map<RaceTrack, RaceCourseLayout> CACHE = new EnumMap<>(RaceTrack.class);
	private static final double CONNECT = RaceTrack.DETOUR_CONNECT;
	private static final double MARGIN = 10.0D;        // ground either side of the kerbs
	/** Lane step of the ground stamp: finer than a block so the island has no gaps. */
	private static final double LANE_STEP = 0.25D;
	private static final String BOOST = "chocobosreborn:boost_pad";
	private static final String[] RAINBOW = {"red", "orange", "yellow", "lime", "light_blue", "blue", "purple"};

	private final RaceTrack track;
	private final RaceTrack.Theme theme;
	private final Map<Cell, String> blocks = new LinkedHashMap<>();
	private final Set<Tile> road = new HashSet<>();
	/** Sorted {@link #roadKey} values so {@link #onCourse} does not allocate a tile per probe. */
	private long[] roadKeys = new long[0];
	private final Set<Long> chunks = new HashSet<>();
	private final CourseStands stands;
	/** Every column a stand block went into, for the road-clearance test. */
	private final Set<Tile> standTiles = new HashSet<>();
	private boolean standing;
	private BoardPost board;
	private int decoSeed;
	/** Set while stamping scenery: {@link #put} then refuses to touch a road tile. */
	private boolean sparingRoad;
	/** Stand the theme's own set piece instead of the course's ({@link #dressedAs}). */
	private final boolean themePiece;
	/** Set while the set piece is stamped: {@link #put} records its cells in {@link #landmarkCells}. */
	private boolean landmarking;
	private final Map<Cell, String> landmarkCells = new HashMap<>();

	public static synchronized RaceCourseLayout of(RaceTrack track) {
		return CACHE.computeIfAbsent(track, RaceCourseLayout::new);
	}

	/**
	 * The course dressed in another theme, with that theme's own set piece at the far side
	 * ({@link #themeLandmark}); never cached. For tests and previews of a theme no course
	 * wears yet (the 48-course themes before phase 2 lays their courses).
	 */
	static RaceCourseLayout dressedAs(RaceTrack track, RaceTrack.Theme theme) {
		return new RaceCourseLayout(track, theme, true);
	}

	private RaceCourseLayout(RaceTrack track) {
		this(track, track.theme(), false);
	}

	private RaceCourseLayout(RaceTrack track, RaceTrack.Theme theme, boolean themePiece) {
		this.track = track;
		this.theme = theme;
		this.themePiece = themePiece;
		double lap = track.lapLength();
		int steps = (int) Math.ceil(lap * 2.0D);   // 0.5 blocks between stamps
		boolean sky = theme == RaceTrack.Theme.SKYWAY;
		// the road tiles first (no blocks yet): the stands are sited against every leg of the lap
		road.addAll(roadTiles(track));
		this.stands = CourseStands.of(track, road);
		// Three passes over the lap, not one: a circuit folds back on itself on a tight
		// corner, so a section stamped later lands on block columns an earlier one already
		// used. Laid in one pass, a margin, a tree or a lava pool overwrites road that is
		// already down — grass and cacti on the racing line, lava on a lap of the course
		// nowhere near its own feature. In layers, the road always wins.
		// Each pass stamps twice per step: at 0.5 blocks a tight corner skips whole block
		// columns and leaves a hole through the island that a racer drops into.
		for (int i = 0; i < steps; i++) {
			double t = i / (double) steps;
			stampGround(t, lap, sky);
			stampGround(t + 0.5D / steps, lap, sky);
		}
		groundUnderStands(sky);
		for (boolean features : new boolean[]{true, false}) {
			for (int i = 0; i < steps; i++) {
				double t = i / (double) steps;
				stampRoad(i, t, lap, sky, features);
				stampRoad(i, t + 0.5D / steps, lap, sky, features);
			}
		}
		sparingRoad = true;   // nothing below this line belongs on the racing line
		for (int i = 0; i < steps; i++) {
			double t = i / (double) steps;
			stampEdges(i, t, lap);
			stampEdges(i, t + 0.5D / steps, lap);
		}
		for (int i = 0; i < steps; i++) {
			double t = i / (double) steps;
			boolean nearFeature = nearFeature(t);
			boolean startZone = startZone(t);
			int sides = stands.sidesAt(t, 4.0D);
			boolean innerStand = (sides & 1) != 0, outerStand = (sides & 2) != 0;
			boolean corner = corner(t, lap);
			int surf = surf(t);
			// decoration in the margins: set pieces every 8 blocks alternating sides, small
			// ground details between them, flag lines along the straights; none in front of a stand
			if (!sky && !nearFeature && !startZone && !outerStand && i % 16 == 0) {
				decorate(t, -(RaceTrack.ROAD_HALF + 5.5D), surf);
			}
			if (!nearFeature && !innerStand && !startZone && i % 16 == 8) {
				decorate(t, RaceTrack.ROAD_HALF + 5.5D, surf);
			}
			int vergeSide = i % 12 == 3 ? -1 : 1;
			if (!sky && !nearFeature && !startZone && i % 6 == 3 && !(vergeSide > 0 ? innerStand : outerStand)) {
				verge(t, vergeSide * (RaceTrack.ROAD_HALF + 2.5D + (i % 5)), surf);
			}
			if (!nearFeature && !startZone && !corner && i % 24 == 12) {
				if (!outerStand) {
					flag(t, -(RaceTrack.ROAD_HALF + 2.0D), surf, i / 24);
				}
				if (!sky && !innerStand) {
					flag(t, RaceTrack.ROAD_HALF + 2.0D, surf, i / 24 + 1);
				}
			}
			if (sky && !nearFeature && !startZone && !outerStand && i % 40 == 0) {
				decorate(t, -(RaceTrack.ROAD_HALF + 1.0D), surf);
			}
		}
		lamps(lap);
		warnings(lap);
		sparingRoad = false;
		gantry();
		startLine(lap);
		startGrid(lap);
		startArrow(lap);
		shortcutMarkers(lap);
		sparingRoad = true;   // sited clear of every road tile, but never let a stand onto one
		standing = true;
		for (CourseStands.Plan plan : stands.plans()) {
			buildStand(plan, stands.cells(plan.index()));
		}
		standing = false;
		sparingRoad = false;
		landmarking = true;
		landmark();
		landmarking = false;
		plugHoles();
		for (Cell c : blocks.keySet()) {
			chunks.add(chunkKey(c.x() >> 4, c.z() >> 4));
		}
		indexRoad();
	}

	private void indexRoad() {
		long[] keys = new long[road.size()];
		int i = 0;
		for (Tile t : road) {
			keys[i++] = roadKey(t.x(), t.z());
		}
		Arrays.sort(keys);
		this.roadKeys = keys;
	}

	static long roadKey(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	/**
	 * Plug every column the racing area covers that the stamp left empty. A gap inside
	 * the road — the outside of a tight corner, the lip of a hill, the mouth of a
	 * detour — is a hole clean through the island into the void, and
	 * {@link RaceScoring#squareFallRescue} never fires above one because
	 * {@link #onCourse} is still true there: a racer who drops in falls out of the
	 * world, the client unloads the course and the rider is left in the sky.
	 */
	private void plugHoles() {
		Map<Tile, Integer> top = new HashMap<>();
		for (Cell c : blocks.keySet()) {
			Tile k = new Tile(c.x(), c.z());
			Integer y = top.get(k);
			if (y == null || c.y() > y) {
				top.put(k, c.y());
			}
		}
		Set<Tile> holes = new HashSet<>();
		for (Tile r : road) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					Tile t = new Tile(r.x() + dx, r.z() + dz);
					if (!top.containsKey(t)) {
						holes.add(t);
					}
				}
			}
		}
		// a hole's neighbours can be holes too, so settle the shallow ones first and repeat
		for (int pass = 0; pass < 4 && !holes.isEmpty(); pass++) {
			List<Tile> done = new ArrayList<>();
			for (Tile h : holes) {
				Integer y = neighbourLevel(top, h);
				if (y == null) {
					continue;
				}
				put(h.x(), y, h.z(), road.contains(h) ? theme.road : theme.ground);
				for (int d = 1; d <= (theme == RaceTrack.Theme.SKYWAY ? 2 : 5); d++) {
					put(h.x(), y - d, h.z(), theme.base);
				}
				top.put(h, y);
				done.add(h);
			}
			if (done.isEmpty()) {
				break;
			}
			holes.removeAll(done);
		}
	}

	/** Surface level to plug a hole at: the level most of its stamped neighbours sit at. */
	private static @org.jetbrains.annotations.Nullable Integer neighbourLevel(Map<Tile, Integer> top, Tile h) {
		Map<Integer, Integer> votes = new HashMap<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Integer y = top.get(new Tile(h.x() + dx, h.z() + dz));
				if (y != null) {
					votes.merge(y, 1, Integer::sum);
				}
			}
		}
		Integer best = null;
		int bestVotes = 0;
		for (Map.Entry<Integer, Integer> e : votes.entrySet()) {
			if (e.getValue() > bestVotes || (e.getValue() == bestVotes && best != null && e.getKey() < best)) {
				best = e.getKey();
				bestVotes = e.getValue();
			}
		}
		return best;
	}
	/**
	 * One cross-section of the island at {@code t}: rock underneath, the margins, the
	 * road band (or the feature across it), kerbs, rails and the detour. Lanes are
	 * stamped every 0.25 blocks and the caller stamps twice per step along the line,
	 * because a coarser stamp skips whole block columns on a tight corner and leaves a
	 * hole straight through the island.
	 */
	private void stampGround(double t, double lap, boolean sky) {
		boolean nearFeature = nearFeature(t);
		int surf = surf(t);
		double margin = sky ? 1.5D : MARGIN;
		double normalOuter = nearFeature ? RaceTrack.DETOUR_OUTER + 3.0D : RaceTrack.ROAD_HALF + 1.0D + margin;
		double normalInner = RaceTrack.ROAD_HALF + 1.0D + margin;
		// the island reaches out under every stand (either side), easing back past its ends
		double outer = stands.groundReach(t, -1, normalOuter);
		double inner = stands.groundReach(t, 1, normalInner);
		// island rock under everything, ground on top of the margins
		for (double o = -outer; o <= inner; o += LANE_STEP) {
			RacePoint q = track.pointAtLane(t, o);
			int x = floor(q.x()), z = floor(q.z());
			if ((o > normalInner || o < -normalOuter) && nearRoad(x, z)) {
				continue;   // a stand's apron reaching toward another leg of the lap: leave that leg alone
			}
			boolean underRoad = Math.abs(o) <= RaceTrack.ROAD_HALF + 1.0D || (nearFeature && o <= -RaceTrack.ROAD_HALF - 1.0D);
			int depth = underRoad ? (sky ? 2 : 5) : taper(o, -outer, inner);
			for (int d = 1; d <= depth; d++) {
				put(x, surf - d, z, theme.base);
			}
			if (!underRoad) {
				boolean rim = o < -outer + 0.75D || o > inner - 0.75D;
				put(x, surf, z, rim ? theme.wall : (sky ? theme.base : theme.ground));
			}
		}
	}

	/**
	 * Fill any column under a stand, its apron and its run-out that the ground stamp
	 * missed (the stamp walks the track's stepped normal, which opens gaps twenty blocks
	 * out on a bend): no stand stands over the void.
	 */
	private void groundUnderStands(boolean sky) {
		double lap = track.lapLength();
		for (CourseStands.Plan p : stands.plans()) {
			double reach = p.back() + 3.0D;
			double run = CourseStands.GROUND_RUN / lap;
			for (double t = p.from() - run; t <= p.to() + run; t += 0.25D / lap) {
				double outside = Math.max(p.from() - t, t - p.to()) * lap;
				double edge = outside <= 0.0D ? reach
						: reach - (reach - (RaceTrack.ROAD_HALF + 2.0D)) * (outside / CourseStands.GROUND_RUN);
				int surf = surf(t);
				for (double o = RaceTrack.ROAD_HALF + 2.0D; o <= edge; o += LANE_STEP) {
					double[] q = CourseStands.lane(track, t, p.side() * o);
					int x = floor(q[0]), z = floor(q[1]);
					if (blocks.containsKey(new Cell(x, surf - 1, z)) || blocks.containsKey(new Cell(x, surf, z)) || nearRoad(x, z)) {
						continue;
					}
					put(x, surf, z, edge - o < 0.75D ? theme.wall : (sky ? theme.base : theme.ground));
					int depth = taper(o, -1.0E9D, edge);
					for (int d = 1; d <= depth; d++) {
						put(x, surf - d, z, theme.base);
					}
				}
			}
		}
	}

	/**
	 * The racing surface at {@code t}: the road band (or the feature laid across it),
	 * the kerbs and rails either side, and the detour road around a feature.
	 */
	private void stampRoad(int i, double t, double lap, boolean sky, boolean featurePass) {
		RaceTrack.Feature ft = track.terrainAt(t);
		if ((ft != null) != featurePass) {
			return;   // pools and ridges first, plain road second: see the constructor
		}
		RaceTrack.Feature any = track.featureAt(t);
		boolean nearFeature = nearFeature(t);
		int surf = surf(t);
		// the band
		for (double o = -RaceTrack.ROAD_HALF; o <= RaceTrack.ROAD_HALF; o += LANE_STEP) {
			RacePoint q = track.pointAtLane(t, o);
			int x = floor(q.x()), z = floor(q.z());
			road.add(new Tile(x, z));
			if (ft == null) {
				String surface = sky ? RAINBOW[(i / 8) % RAINBOW.length] + "_concrete" : theme.road;
				RaceTrack.Feature soon = approachingBoost(t, lap);
				if (soon != null && boostLane(soon, o) && (i / 2) % 2 == 0) {
					surface = "yellow_concrete";
				}
				put(x, surf, z, surface);
				if (any != null && any.type() == RaceTrack.Feature.Type.BOOST && boostLane(any, o)) {
					put(x, surf + 1, z, BOOST + "[facing=" + cardinal(track.tangent(t)) + "]");
				}
			} else {
				switch (ft.type()) {
					case WATER -> {
						put(x, surf - 2, z, theme.base);
						put(x, surf - 1, z, "water");
						put(x, surf, z, "water");
					}
					case LAVA -> {
						put(x, surf - 2, z, theme.base);
						put(x, surf - 1, z, "lava");
						put(x, surf, z, "lava");
					}
					case MUD -> put(x, surf, z, "mud");
					case RIDGE -> {
						put(x, surf, z, theme.road);
						int ridge = track.ridgeHeight();
						for (int h = 1; h <= ridge; h++) {
							put(x, surf + h, z, h == ridge ? theme.wall : theme.base);
						}
					}
					default -> put(x, surf, z, theme.road);
				}
			}
		}
		boolean liquid = ft != null && (ft.type() == RaceTrack.Feature.Type.WATER || ft.type() == RaceTrack.Feature.Type.LAVA);
		if (liquid) {
			// one kerb stamp per step steps diagonally on a diagonal leg and the pool leaks out
			// through the corner: wall the whole strip between the band and the kerb instead
			for (int side = -1; side <= 1; side += 2) {
				for (double o = RaceTrack.ROAD_HALF + LANE_STEP; o <= RaceTrack.ROAD_HALF + 1.0D; o += LANE_STEP) {
					RacePoint q = track.pointAtLane(t, side * o);
					int x = floor(q.x()), z = floor(q.z());
					put(x, surf, z, theme.wall);
					put(x, surf + 1, z, theme.wall);
				}
			}
		}
		// detour road outside a feature, with connectors; its own kerb further out
		if (nearFeature) {
			double from = ft != null ? RaceTrack.DETOUR_INNER : RaceTrack.ROAD_HALF + 0.5D;
			for (double o = -RaceTrack.DETOUR_OUTER; o <= -from; o += LANE_STEP) {
				RacePoint q = track.pointAtLane(t, o);
				int x = floor(q.x()), z = floor(q.z());
				road.add(new Tile(x, z));
				put(x, surf, z, theme.road);
			}
			RacePoint edge = track.pointAtLane(t, -RaceTrack.DETOUR_OUTER - 1.0D);
			put(floor(edge.x()), surf, floor(edge.z()), ft == null ? "yellow_concrete" : theme.wall);
			// its rail goes up with the others, in the pass that spares the racing line
			if (ft != null) {
				// the strip between the feature and the detour: ground with a low wall on the feature side
				for (double o = -RaceTrack.DETOUR_INNER + 0.5D; o < -RaceTrack.ROAD_HALF - 1.0D; o += LANE_STEP) {
					RacePoint q = track.pointAtLane(t, o);
					put(floor(q.x()), surf, floor(q.z()), theme.ground);
				}
			}
		}
	}


	/**
	 * Kerbs and rails at {@code t}. Stamped after every band is down and with
	 * {@link #sparingRoad} set, so a corner that folds back on itself cannot stand a
	 * fence post in the racing line of the leg beside it.
	 */
	private void stampEdges(int i, double t, double lap) {
		RaceTrack.Feature ft = track.terrainAt(t);
		boolean nearFeature = nearFeature(t);
		boolean corner = corner(t, lap);
		int surf = surf(t);
		boolean liquid = ft != null && (ft.type() == RaceTrack.Feature.Type.WATER || ft.type() == RaceTrack.Feature.Type.LAVA);
		for (int side = -1; side <= 1; side += 2) {
			double o = side * (RaceTrack.ROAD_HALF + 1.0D);
			if (side < 0 && nearFeature && !liquid) {
				continue;   // the detour opens here; a pool keeps its kerb on both sides, or the
				            // water drains over the island the first time a block update reaches it
			}
			RacePoint q = track.pointAtLane(t, o);
			int x = floor(q.x()), z = floor(q.z());
			String kerb = corner ? ((i / 6) % 2 == 0 ? theme.kerbA : theme.kerbB) : theme.wall;
			put(x, surf, z, kerb);
			if (corner) {
				RacePoint wide = track.pointAtLane(t, side * (RaceTrack.ROAD_HALF + 2.0D));
				put(floor(wide.x()), surf, floor(wide.z()), kerb);
			}
			if (liquid) {
				put(x, surf + 1, z, theme.wall);
			} else if (!theme.rail.equals("air") && t >= 0.03D && (side < 0 || corner)) {
				put(x, surf + 1, z, theme.rail);
			}
		}
		if (nearFeature && !theme.rail.equals("air")) {
			RacePoint edge = track.pointAtLane(t, -RaceTrack.DETOUR_OUTER - 1.0D);
			put(floor(edge.x()), surf + 1, floor(edge.z()), theme.rail);
		}
	}

	/** Inside a feature or one of its connectors: the detour is open and the band is terrain. */
	private boolean nearFeature(double t) {
		return nearFeature(track, t);
	}

	private static boolean nearFeature(RaceTrack track, double t) {
		for (RaceTrack.Feature f : track.terrainFeatures()) {
			if (t >= f.start() - CONNECT && t <= f.end() + CONNECT) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The road tiles of a course (band and detours) without the rest of the plan: the same
	 * stamps {@link #stampRoad} lays, so stands can be sited against every leg of the lap
	 * (and the client can place the crowd) without building half a million blocks.
	 */
	static Set<Tile> roadTiles(RaceTrack track) {
		Set<Tile> out = new HashSet<>();
		int steps = (int) Math.ceil(track.lapLength() * 2.0D);
		for (int i = 0; i < steps; i++) {
			double t0 = i / (double) steps;
			for (double t : new double[]{t0, t0 + 0.5D / steps}) {
				for (double o = -RaceTrack.ROAD_HALF; o <= RaceTrack.ROAD_HALF; o += LANE_STEP) {
					RacePoint q = track.pointAtLane(t, o);
					out.add(new Tile(floor(q.x()), floor(q.z())));
				}
				if (nearFeature(track, t)) {
					double from = track.terrainAt(t) != null ? RaceTrack.DETOUR_INNER : RaceTrack.ROAD_HALF + 0.5D;
					for (double o = -RaceTrack.DETOUR_OUTER; o <= -from; o += LANE_STEP) {
						RacePoint q = track.pointAtLane(t, o);
						out.add(new Tile(floor(q.x()), floor(q.z())));
					}
				}
			}
		}
		return out;
	}

	/** Where along the lap the landmark stands: the far side, nudged off any terrain. */
	static double landmarkT(RaceTrack track) {
		double t = 0.5D;
		while (track.terrainAt(t) != null || track.terrainAt(t + 0.02D) != null || track.terrainAt(t - 0.02D) != null) {
			t += 0.03D;
		}
		return t;
	}

	/** On or beside a road tile. */
	private boolean nearRoad(int x, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (road.contains(new Tile(x + dx, z + dz))) {
					return true;
				}
			}
		}
		return false;
	}

	/** The grid, the line and the run-off behind it: no decoration here. */
	private static boolean startZone(double t) {
		return t < 0.05D || t > 0.985D;
	}

	/** A corner: kerbs are striped and the rail runs both sides. */
	private boolean corner(double t, double lap) {
		return track.turnAhead(t - 12.0D / lap, 24.0D) > 0.22D;
	}

	/** Surface block level of the road at t (a racer stands one above it). */
	private int surf(double t) {
		return (int) track.groundY(t) - 1;
	}

	// ------------------------------------------------------------ dressing

	private void lamps(double lap) {
		int lamps = (int) (lap / 40.0D);
		for (int i = 0; i < lamps; i++) {
			double t = i / (double) lamps;
			if (track.terrainAt(t) != null || t < 0.05D) {
				continue;
			}
			int sides = stands.sidesAt(t, 4.0D);
			int y = (int) track.groundY(t) - 1;
			for (int side : new int[]{-1, 1}) {
				if ((sides & (side > 0 ? 1 : 2)) != 0) {
					continue;   // the stand has its own lamps on its front posts
				}
				RacePoint q = track.pointAtLane(t, side * (RaceTrack.ROAD_HALF + 2.5D));
				int x = floor(q.x()), z = floor(q.z());
				if (stands.nearStand(x, z)) {
					continue;
				}
				put(x, y, z, theme.wall);
				put(x, y + 1, z, theme.post);
				put(x, y + 2, z, theme.post);
				put(x, y + 3, z, theme.post);
				put(x, y + 4, z, theme.lamp);
			}
		}
	}

	/** Chequered band across the road at t = 0, five rows so it reads at speed. */
	private void startLine(double lap) {
		for (int row = -2; row <= 2; row++) {
			double t = row / lap;
			if (t < 0.0D) {
				t += 1.0D;
			}
			int surf = (int) track.groundY(t) - 1;
			for (double o = -RaceTrack.ROAD_HALF; o <= RaceTrack.ROAD_HALF; o += 0.5D) {
				RacePoint q = track.pointAtLane(t, o);
				boolean white = ((floor(o + RaceTrack.ROAD_HALF) + row) & 1) == 0;
				put(floor(q.x()), surf, floor(q.z()), white ? "white_concrete" : "black_concrete");
			}
		}
	}

	/** Six stall boxes on the grid at t = 0.02, outlined in white. */
	private void startGrid(double lap) {
		int surf = (int) track.groundY(0.02D) - 1;
		for (int stall = 0; stall < 6; stall++) {
			double centre = RaceTrack.stallOffset(stall, 6);
			for (int row = 0; row < 4; row++) {
				double t = 0.02D + (row - 3) / lap;
				for (double o = centre - 0.8D; o <= centre + 0.8D; o += 0.5D) {
					boolean edge = row == 0 || row == 3 || o <= centre - 0.55D || o >= centre + 0.55D;
					if (!edge) {
						continue;
					}
					RacePoint q = track.pointAtLane(t, o);
					put(floor(q.x()), surf, floor(q.z()), "white_concrete");
				}
			}
		}
	}

	/** A yellow arrow on the road just past the grid, pointing the way round. */
	private void startArrow(double lap) {
		double t0 = 0.02D + 4.0D / lap;
		RacePoint origin = track.pointAtLane(t0, 0.0D);
		double[] tg = track.tangent(t0);
		int[] f = RaceScoring.arrowForward(tg[0], tg[1]);
		int[] r = RaceScoring.arrowRight(f[0], f[1]);
		int ox = floor(origin.x()), oz = floor(origin.z());
		int surf = (int) track.groundY(t0) - 1;
		int half = RaceScoring.startArrowHalf();
		for (int along = 0; along < RaceScoring.startArrowLength(); along++) {
			for (int across = -half; across <= half; across++) {
				if (!RaceScoring.startArrowCell(along, across)) {
					continue;
				}
				int x = ox + f[0] * along + r[0] * across;
				int z = oz + f[1] * along + r[1] * across;
				put(x, surf, z, RaceScoring.startArrowTip(along, across) ? "gold_block" : "yellow_concrete");
			}
		}
	}

	/** Coloured posts a few blocks before each terrain feature. */
	private void warnings(double lap) {
		for (RaceTrack.Feature f : track.terrainFeatures()) {
			double t = f.start() - 8.0D / lap;
			if (t < 0.06D) {
				t = 0.06D;
			}
			int surf = (int) track.groundY(t) - 1;
			String colour = switch (f.type()) {
				case WATER -> "light_blue";
				case LAVA -> "orange";
				case RIDGE -> "gray";
				case MUD -> "brown";
				default -> "yellow";
			};
			for (int side : new int[]{-1, 1}) {
				RacePoint q = track.pointAtLane(t, side * (RaceTrack.ROAD_HALF + 2.0D));
				int x = floor(q.x()), z = floor(q.z());
				put(x, surf + 1, z, theme.post);
				put(x, surf + 2, z, theme.post);
				put(x, surf + 3, z, colour + "_banner[rotation=8]");
			}
		}
	}

	/**
	 * Every terrain shortcut is marked where the road forks (Ahmi: "I have no idea
	 * where any are"): a gantry from the infield side arches over the straight line
	 * in the feature's colour, hung with a banner for each breed that can take it (a
	 * bog, which suits nobody, is hung brown), a sign says so, and a dashed stripe of
	 * the same colour runs down the middle of the road into the fork. The detour peels
	 * off to the outside as before.
	 */
	private void shortcutMarkers(double lap) {
		for (RaceTrack.Feature f : track.terrainFeatures()) {
			String colour = shortcutColour(f.type());
			double fork = f.start() - RaceTrack.DETOUR_CONNECT;
			// the stripe: dashed, three wide, from 22 blocks before the fork to the feature
			for (double d = 22.0D; d >= -RaceTrack.DETOUR_CONNECT * lap + 1.0D; d -= 0.5D) {
				double t = wrap(fork - d / lap);
				if (boostAt(t) || ((int) Math.floor(d / 2.0D) & 1) == 1) {
					continue;
				}
				int y = surf(t);
				for (double o = -1.0D; o <= 1.0D; o += 0.5D) {
					RacePoint q = track.pointAtLane(t, o);
					put(floor(q.x()), y, floor(q.z()), colour + "_concrete");
				}
			}
			// the gantry, just before the fork: a post on the infield verge, a beam over the road
			double t = wrap(fork - 4.0D / lap);
			int y = surf(t);
			RacePoint foot = track.pointAtLane(t, RaceTrack.ROAD_HALF + 2.5D);
			int fx = floor(foot.x()), fz = floor(foot.z());
			if (road.contains(new Tile(fx, fz))) {
				continue;   // a fold of the course: no post in anyone's racing line
			}
			for (int h = 1; h <= 7; h++) {
				put(fx, y + h, fz, h == 4 ? theme.lamp : theme.post);
			}
			double[] tg = track.tangent(t);
			String toward = facingOf(-tg[0], -tg[1]);   // the banners and sign face the riders coming up
			List<String> banners = shortcutBanners(f);
			int b = 0;
			for (double o = RaceTrack.ROAD_HALF + 2.5D; o >= -RaceTrack.ROAD_HALF - 0.5D; o -= 0.5D) {
				RacePoint q = track.pointAtLane(t, o);
				int bx = floor(q.x()), bz = floor(q.z());
				put(bx, y + 8, bz, colour + "_glazed_terracotta");
				put(bx, y + 9, bz, colour + "_concrete");
				if (o <= RaceTrack.ROAD_HALF && o > -RaceTrack.ROAD_HALF && Math.floorMod((int) Math.round(o * 2.0D), 4) == 0) {
					RacePoint front = track.pointAtLane(wrap(t - 1.0D / lap), o);
					put(floor(front.x()), y + 8, floor(front.z()),
							banners.get(b++ % banners.size()) + "_wall_banner[facing=" + toward + "]");
				}
			}
			RacePoint plate = track.pointAtLane(wrap(t - 1.0D / lap), RaceTrack.ROAD_HALF + 2.5D);
			put(floor(plate.x()), y + 3, floor(plate.z()), "oak_wall_sign[facing=" + toward + "]");
			shortcutSigns.add(new ShortcutSign(floor(plate.x()), y + 3, floor(plate.z()), f.type()));
		}
	}

	/** A sign on a shortcut gantry: where it is and which feature it names (text is written by the builder). */
	public record ShortcutSign(int x, int y, int z, RaceTrack.Feature.Type type) {
	}

	private final List<ShortcutSign> shortcutSigns = new ArrayList<>();

	public List<ShortcutSign> shortcutSigns() {
		return Collections.unmodifiableList(shortcutSigns);
	}

	static String shortcutColour(RaceTrack.Feature.Type type) {
		return switch (type) {
			case WATER -> "light_blue";
			case RIDGE -> "lime";
			case LAVA -> "orange";
			case MUD -> "brown";
			default -> "yellow";
		};
	}

	/** One banner colour per breed that can take this feature straight; brown for a bog. */
	static List<String> shortcutBanners(RaceTrack.Feature f) {
		List<String> out = new ArrayList<>();
		for (tk.darrow.chocobosreborn.breed.ChocoboColor c : tk.darrow.chocobosreborn.breed.ChocoboColor.values()) {
			if (f.suits(c)) {
				out.add(switch (c) {
					case YELLOW, GOLD -> "yellow";
					case GREEN -> "green";
					case BLUE -> "blue";
					case WHITE -> "white";
					case BLACK -> "black";
					case PURPLE -> "purple";
					case FLAME -> "red";
				});
			}
		}
		if (out.isEmpty()) {
			out.add("brown");
		}
		return out;
	}

	private boolean boostAt(double t) {
		for (RaceTrack.Feature f : track.features()) {
			if (f.type() == RaceTrack.Feature.Type.BOOST && t >= f.start() - 1.0D / track.lapLength() && t <= f.end() + 1.0D / track.lapLength()) {
				return true;
			}
		}
		return false;
	}

	private static double wrap(double t) {
		return t - Math.floor(t);
	}

	private static String facingOf(double dx, double dz) {
		return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
	}

	private RaceTrack.Feature approachingBoost(double t, double lap) {
		double window = 6.0D / lap;
		for (RaceTrack.Feature f : track.features()) {
			if (f.type() != RaceTrack.Feature.Type.BOOST) {
				continue;
			}
			if (t < f.start() && f.start() - t <= window) {
				return f;
			}
		}
		return null;
	}

	/** Start / finish gantry across the band at t = 0. */
	private void gantry() {
		int y = (int) track.groundY(0.0D) - 1;
		int beam = 11;
		for (double o : new double[]{-RaceTrack.ROAD_HALF - 1.5D, RaceTrack.ROAD_HALF + 1.5D}) {
			RacePoint q = track.pointAtLane(0.0D, o);
			int x = floor(q.x()), z = floor(q.z());
			for (int h = 0; h <= beam; h++) {
				put(x, y + h, z, h == 8 ? theme.lamp : theme.post);
			}
			put(x, y + beam + 1, z, theme.kerbA);
		}
		for (double o = -RaceTrack.ROAD_HALF - 1.5D; o <= RaceTrack.ROAD_HALF + 1.5D; o += 0.5D) {
			RacePoint q = track.pointAtLane(0.0D, o);
			int x = floor(q.x()), z = floor(q.z());
			put(x, y + beam, z, (floor(o) & 1) == 0 ? "black_concrete" : "white_concrete");
			// lights hang under the beam (red, amber, green), clear of a mounted bird
			int lane = floor(o + RaceTrack.ROAD_HALF + 1.5D);
			if (lane % 4 == 1) {
				put(x, y + beam - 1, z, "red_concrete");
				put(x, y + beam - 2, z, "yellow_concrete");
				put(x, y + beam - 3, z, "lime_concrete");
				put(x, y + beam - 4, z, "sea_lantern");
			}
		}
		// a marshal's tower beside the gantry
		RacePoint tower = track.pointAtLane(0.0D, -RaceTrack.ROAD_HALF - 4.0D);
		int tx = floor(tower.x()), tz = floor(tower.z());
		for (int h = 0; h <= 8; h++) {
			put(tx, y + h, tz, theme.post);
			put(tx + 1, y + h, tz, theme.post);
			put(tx, y + h, tz + 1, theme.post);
			put(tx + 1, y + h, tz + 1, theme.post);
		}
		for (int dx = -1; dx <= 2; dx++) {
			for (int dz = -1; dz <= 2; dz++) {
				put(tx + dx, y + 9, tz + dz, theme.wall);
				put(tx + dx, y + 10, tz + dz, (dx == -1 || dx == 2 || dz == -1 || dz == 2) ? theme.rail.equals("air") ? "air" : theme.rail : "air");
			}
		}
		put(tx, y + 11, tz, theme.lamp);
		put(tx + 1, y + 11, tz + 1, "yellow_banner[rotation=8]");
		RacePoint road = track.pointAt(0.0D);
		String facing = cardinal(new double[]{road.x() - tx, road.z() - tz});
		RacePoint face = track.pointAtLane(0.0D, -RaceTrack.ROAD_HALF - 3.0D);
		board = new BoardPost(floor(face.x()), y + 6, floor(face.z()), facing);
	}

	/**
	 * One grandstand from its {@link CourseStands.Plan}: tiered seat rows rising away from
	 * the road (a plinth where the ground dips), a back wall, a roof, front posts with
	 * lamps and banners. The class sets the look: C wooden benches under a striped
	 * awning, B / A the quartz stand, S a grand stand with a taller wall, a solid roof
	 * with a trim, flags on top and banners along the back.
	 */
	private void buildStand(CourseStands.Plan p, List<CourseStands.Cell> cells) {
		int y = p.y();
		CourseStands.Tier tier = p.tier();
		String wood = switch (theme) {
			case ORCHARD -> "cherry";
			case SHORE -> "bamboo";
			case FARMLAND -> "birch";
			case SAVANNA -> "acacia";
			case MUSHROOM -> "mangrove";
			default -> "oak";
		};
		// the seat rows, exactly as CourseStands placed the fans on them
		for (CourseStands.Cell c : cells) {
			for (int h = c.ground() + 1; h < y; h++) {
				put(c.x(), h, c.z(), theme.wall);
			}
			for (int h = 0; h <= c.row(); h++) {
				put(c.x(), y + h, c.z(), theme.wall);
			}
			String facing = yawFacing(c.yaw());
			String top;
			if (c.seat()) {
				top = tier == CourseStands.Tier.BENCH ? wood + "_stairs[facing=" + facing + "]" : stairs(facing);
			} else if (tier == CourseStands.Tier.BENCH) {
				top = wood + "_planks";
			} else if (tier == CourseStands.Tier.GRAND) {
				top = c.stripe() == 0 ? theme.kerbA : theme.kerbB;
			} else {
				top = c.stripe() == 0 ? "yellow_concrete" : "red_concrete";
			}
			put(c.x(), y + c.row() + 1, c.z(), top);
		}
		// back wall, roof, posts and banners
		int roof = p.roofY();
		double back = p.back();
		double from = p.from(), to = p.to();
		int steps = stands.steps(p);
		for (int s = 0; s <= steps; s++) {
			double t = from + (to - from) * s / steps;
			double[] tg = track.tangent(t);
			String facing = cardinal(new double[]{p.side() * tg[1], -p.side() * tg[0]});
			int ground = surf(t);
			double[] bq = CourseStands.lane(track, t, p.side() * back);
			int bx = floor(bq[0]), bz = floor(bq[1]);
			for (int h = ground + 1; h < y; h++) {
				put(bx, h, bz, theme.wall);
			}
			for (int h = y; h < roof; h++) {
				boolean band = h == y + p.rows() + 1 && (s / 6) % 2 == 0;
				put(bx, h, bz, band ? theme.kerbB : theme.wall);
			}
			for (double o = CourseStands.BASE - 0.5D; o <= back + 1.0E-6D; o += 0.5D) {
				double[] q = CourseStands.lane(track, t, p.side() * o);
				String cover = switch (tier) {
					case GRAND -> o < CourseStands.BASE ? theme.kerbA : theme.wall;
					case BENCH -> (s / 4) % 2 == 0 ? "white_wool" : "green_wool";
					default -> (s / 4) % 2 == 0 ? "yellow_wool" : "white_wool";   // striped awning
				};
				put(floor(q[0]), roof, floor(q[1]), cover);
			}
			if (CourseStands.postStep(s)) {
				double[] front = CourseStands.lane(track, t, p.side() * (CourseStands.BASE - 0.5D));
				int fx = floor(front[0]), fz = floor(front[1]);
				for (int h = ground + 1; h < y; h++) {
					put(fx, h, fz, theme.wall);
				}
				for (int h = y; h < roof; h++) {
					put(fx, h, fz, h == y + 3 ? theme.lamp : theme.post);
				}
			}
			if (CourseStands.bannerStep(tier, s)) {
				// a banner on the inside face of the back wall, above the top row
				double[] wq = CourseStands.lane(track, t, p.side() * (back - 0.5D));
				String colour = FLAG_COLOURS[Math.floorMod(s / 4 + p.index(), FLAG_COLOURS.length)];
				put(floor(wq[0]), y + p.rows() + 2, floor(wq[1]), colour + "_wall_banner[facing=" + facing + "]");
			}
			if (tier == CourseStands.Tier.GRAND && s % 8 == 0) {
				// flag poles along the roof line
				put(bx, roof + 1, bz, theme.post);
				put(bx, roof + 2, bz, theme.post);
				put(bx, roof + 3, bz, FLAG_COLOURS[Math.floorMod(s / 8 + p.index(), FLAG_COLOURS.length)]
						+ "_banner[rotation=" + bannerRotation(facing) + "]");
			}
		}
	}

	/** Compass direction a fan (and so a seat) with this yaw faces. */
	private static String yawFacing(float yaw) {
		double r = Math.toRadians(yaw);
		return cardinal(new double[]{-Math.sin(r), Math.cos(r)});
	}

	/** Standing-banner rotation that shows the face toward {@code facing}. */
	private static int bannerRotation(String facing) {
		return switch (facing) {
			case "south" -> 0;
			case "west" -> 4;
			case "north" -> 8;
			default -> 12;
		};
	}

	/**
	 * Boost strips cover one lane of the road, not its width: the strips of a course
	 * alternate outside, centre, inside, so a rider has to steer for them.
	 */
	boolean boostLane(RaceTrack.Feature strip, double o) {
		int slot = Math.floorMod(track.features().indexOf(strip), 3);
		return switch (slot) {
			case 0 -> o <= -1.5D && o >= -4.5D;
			case 1 -> o >= -1.5D && o <= 1.5D;
			default -> o >= 1.5D && o <= 4.5D;
		};
	}

	/** Nearest compass direction of a unit (x, z) vector. */
	static String cardinal(double[] v) {
		return Math.abs(v[0]) > Math.abs(v[1]) ? (v[0] > 0 ? "east" : "west") : (v[1] > 0 ? "south" : "north");
	}

	private static String stairs(String facing) {
		return "quartz_stairs[facing=" + facing + "]";
	}

	private static final String[] FLAG_COLOURS = {"yellow", "blue", "green", "orange", "white", "red"};

	/** A fence post with a tribe-coloured banner, along the straights. */
	private void flag(double t, double offset, int surf, int n) {
		RacePoint q = track.pointAtLane(t, offset);
		int x = floor(q.x()), z = floor(q.z());
		if (stands.nearStand(x, z)) {
			return;
		}
		String colour = FLAG_COLOURS[Math.floorMod(n, FLAG_COLOURS.length)];
		put(x, surf + 1, z, theme.post);
		put(x, surf + 2, z, theme.post);
		put(x, surf + 3, z, colour + "_banner[rotation=" + (Math.floorMod(n, 2) == 0 ? 4 : 12) + "]");
	}

	/** Small ground detail in the theme's margin: grass, flowers, snow, petals, lichen, fungi... */
	private void verge(double t, double offset, int surf) {
		RacePoint q = track.pointAtLane(t, offset);
		int x = floor(q.x()), z = floor(q.z());
		if (stands.nearStand(x, z)) {
			return;
		}
		int pick = Math.floorMod(x * 31 + z * 17, 4);
		String block = switch (theme) {
			case MEADOW -> pick == 0 ? "short_grass" : pick == 1 ? "short_grass" : pick == 2 ? "azure_bluet" : "poppy";
			case ORCHARD -> pick == 0 ? "pink_petals[flower_amount=3]" : pick == 1 ? "short_grass" : pick == 2 ? "sweet_berry_bush[age=2]" : "pink_petals[flower_amount=1]";
			case SHORE -> pick == 0 ? "sea_pickle[pickles=2]" : pick == 1 ? "dead_bush" : pick == 2 ? "sandstone_wall" : "dead_bush";
			case CANYON -> pick == 0 ? "dead_bush" : pick == 1 ? "red_sand" : pick == 2 ? "cactus" : "orange_terracotta";
			case RIVER -> pick == 0 ? "fern" : pick == 1 ? "short_grass" : pick == 2 ? "mossy_cobblestone" : "blue_orchid";
			case SNOW -> pick == 0 ? "snow" : pick == 1 ? "snow[layers=3]" : pick == 2 ? "packed_ice" : "snow";
			case CAVERN -> pick == 0 ? "glow_lichen[down=true]" : pick == 1 ? "small_amethyst_bud[facing=up]" : pick == 2 ? "pointed_dripstone[vertical_direction=up]" : "cobbled_deepslate_wall";
			case JUNGLE -> pick == 0 ? "fern" : pick == 1 ? "large_fern[half=lower]" : pick == 2 ? "moss_carpet" : "jungle_leaves[persistent=true]";
			case NETHER -> pick == 0 ? "crimson_nylium" : pick == 1 ? "warped_nylium" : pick == 2 ? "soul_soil" : "shroomlight";
			case SKYWAY -> "air";
			case KEEP -> pick == 0 ? "polished_blackstone_wall" : pick == 1 ? "magma_block" : pick == 2 ? "soul_fire" : "blackstone_wall";
			case END -> pick == 0 ? "end_rod" : pick == 1 ? "chorus_flower" : pick == 2 ? "end_stone_brick_wall" : "purpur_slab[type=bottom]";
			case FARMLAND -> pick == 0 ? "short_grass" : pick == 1 ? "dandelion" : pick == 2 ? "hay_block" : "cornflower";
			case SAVANNA -> pick == 0 ? "short_grass" : pick == 1 ? "dead_bush" : pick == 2 ? "coarse_dirt" : "short_grass";
			case MUSHROOM -> pick == 0 ? "crimson_roots" : pick == 1 ? "warped_roots" : pick == 2 ? "brown_mushroom_block" : "glow_lichen[down=true]";
			case DEEP_DARK -> pick == 0 ? "sculk_vein[down=true]" : pick == 1 ? "gray_carpet" : pick == 2 ? "cobbled_deepslate_wall" : "black_candle[candles=2,lit=true]";
		};
		if (block.equals("air")) {
			return;
		}
		if (block.equals("soul_fire")) {
			put(x, surf, z, "soul_soil");
		}
		if (block.equals("cactus")) {
			put(x, surf, z, "red_sand");
		}
		put(x, surf + 1, z, block);
	}

	/**
	 * Every course has its own set piece at the far side of the circuit (about t = 0.5, off
	 * any feature), so no two look alike from the saddle ({@code CourseIdentityTest}). The
	 * dispatch is per course, one method per class ({@link #landmarkC} .. {@link #landmarkS}),
	 * each followed by that class's set pieces: a new course adds one case line in its
	 * class's method and, if it needs one, a new set-piece method in its class's region. A
	 * course without a case of its own gets its theme's set piece ({@link #themeLandmark}).
	 */
	private void landmark() {
		double t = landmarkT(track);
		int surf = (int) track.groundY(t) - 1;
		if (themePiece) {
			onPlinth(t, surf, this::themeLandmark);
			sparingRoad = false;
			return;
		}
		switch (track.getRaceClass()) {
			case C -> landmarkC(t, surf);
			case B -> landmarkB(t, surf);
			case A -> landmarkA(t, surf);
			case S -> landmarkS(t, surf);
		}
		sparingRoad = false;
	}

	/** A set piece standing on a plinth beside the road: {@code at(x, y, z)} with y the plinth top + 1. */
	@FunctionalInterface
	private interface SetPiece {
		void at(int x, int y, int z);
	}

	/**
	 * Beside the road on the outside (ROAD_HALF + 6 out): a 7x7 plinth of the theme wall on
	 * five blocks of island rock, then the set piece on it. Stamped sparing the road, so a
	 * fold of the course never ends up wearing a landmark.
	 */
	private void onPlinth(double t, int surf, SetPiece piece) {
		sparingRoad = true;
		RacePoint q = track.pointAtLane(t, -(RaceTrack.ROAD_HALF + 6.0D));
		int x = floor(q.x()), z = floor(q.z());
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int d = 0; d <= 4; d++) {
					put(x + dx, surf - d, z + dz, theme.base);
				}
				put(x + dx, surf, z + dz, theme.wall);
			}
		}
		piece.at(x, surf + 1, z);
		sparingRoad = false;
	}

	/** The theme's own set piece: what a course of that theme stands by unless it has its own. */
	private void themeLandmark(int x, int y, int z) {
		switch (theme) {
			case MEADOW -> oakTree(x, y, z);
			case ORCHARD -> cherryTree(x, y, z);
			case SHORE -> lighthouse(x, y, z);
			case CANYON -> hoodoo(x, y, z);
			case RIVER -> cairn(x, y, z);
			case SNOW -> iceSpire(x, y, z);
			case CAVERN -> geode(x, y, z);
			case JUNGLE -> stepPyramid(x, y, z);
			case NETHER -> fortressTower(x, y, z);
			case SKYWAY -> prismSpire(x, y, z);
			case KEEP -> obelisk(x, y, z);
			case END -> obsidianSpire(x, y, z);
			case FARMLAND -> scarecrow(x, y, z);
			case SAVANNA -> greatAcacia(x, y, z);
			case MUSHROOM -> giantMushroom(x, y, z);
			case DEEP_DARK -> wardenFrame(x, y, z);
		}
	}

	// ================================================================ C landmarks

	private void landmarkC(double t, int surf) {
		switch (track) {
			case C_MEADOW -> onPlinth(t, surf, this::oakTree);
			case C_ORCHARD -> onPlinth(t, surf, this::cherryTree);
			case C_SHORE -> onPlinth(t, surf, this::lighthouse);
			case C_DOWNS -> onPlinth(t, surf, this::windmill);
			case C_CIDER -> onPlinth(t, surf, this::ciderBarn);
			case C_LAGOON -> onPlinth(t, surf, this::shipwreck);
			// ---- new C landmarks (phase 2) begin ----
			case C_HARVEST -> onPlinth(t, surf, this::scarecrow);
			case C_KITE_HILL -> onPlinth(t, surf, this::kite);
			case C_SCALLOP -> onPlinth(t, surf, this::sandcastle);
			case C_HEARTFIELD -> onPlinth(t, surf, this::balloon);
			case C_HONEYCOMB -> onPlinth(t, surf, this::apiary);
			case C_HORSESHOE -> onPlinth(t, surf, this::barnAndSilo);
			// ---- new C landmarks (phase 2) end ----
			default -> onPlinth(t, surf, this::themeLandmark);
		}
	}

	/** C_MEADOW: a lone oak. */
	private void oakTree(int x, int y, int z) {
		tree(x, y, z, "oak_log", "oak_leaves[persistent=true]", 7);
	}

	/** C_ORCHARD: a great cherry. */
	private void cherryTree(int x, int y, int z) {
		tree(x, y, z, "cherry_log", "cherry_leaves[persistent=true]", 8);
	}

	/** C_SHORE: a striped lighthouse. */
	private void lighthouse(int x, int y, int z) {
		for (int h = 0; h < 12; h++) {
			String band = (h / 2) % 2 == 0 ? "white_concrete" : "red_concrete";
			put(x, y + h, z, band);
			put(x + 1, y + h, z, band);
			put(x, y + h, z + 1, band);
			put(x + 1, y + h, z + 1, band);
		}
		for (int dx = 0; dx <= 1; dx++) {
			for (int dz = 0; dz <= 1; dz++) {
				put(x + dx, y + 12, z + dz, "sea_lantern");
				put(x + dx, y + 13, z + dz, "red_concrete");
			}
		}
	}

	/** C_DOWNS: a stone windmill, sails out over the meadow. */
	private void windmill(int x, int y, int z) {
		for (int h = 0; h < 9; h++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					put(x + dx, y + h, z + dz, h == 8 ? "dark_oak_planks" : "cobblestone");
				}
			}
		}
		put(x, y + 9, z, "dark_oak_planks");
		for (int r = 1; r <= 4; r++) {
			put(x + 2, y + 5 + r, z, "white_wool");
			put(x + 2, y + 5 - r, z, "white_wool");
			put(x + 2, y + 5, z + r, "white_wool");
			put(x + 2, y + 5, z - r, "white_wool");
			put(x + 2, y + 5 + r, z + 1, "oak_planks");
			put(x + 2, y + 5 - r, z - 1, "oak_planks");
		}
		put(x + 2, y + 5, z, "oak_log[axis=x]");
	}

	/** C_CIDER: a press barn with the year's barrels stacked outside. */
	private void ciderBarn(int x, int y, int z) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				if (wall) {
					for (int h = 0; h < 4; h++) {
						put(x + dx, y + h, z + dz, h == 0 || h == 3 ? "stripped_spruce_log[axis=y]" : "spruce_planks");
					}
				}
				put(x + dx, y + 4, z + dz, "spruce_planks");
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			put(x + dx, y + 5, z, "spruce_slab[type=bottom]");
		}
		put(x - 1, y, z - 3, "barrel[facing=up]");
		put(x, y, z - 3, "barrel[facing=up]");
		put(x - 1, y + 1, z - 3, "barrel[facing=up]");
		put(x + 1, y, z + 3, "hay_block[axis=y]");
		put(x + 1, y + 1, z + 3, "hay_block[axis=x]");
	}

	/** C_LAGOON: a hull beached on the sand, mast still standing. */
	private void shipwreck(int x, int y, int z) {
		for (int dx = -3; dx <= 3; dx++) {
			int rise = Math.abs(dx) >= 2 ? 1 : 0;
			for (int dz = -1; dz <= 1; dz++) {
				put(x + dx, y + rise, z + dz, "oak_planks");
				if (Math.abs(dz) == 1) {
					put(x + dx, y + 1 + rise, z + dz, "spruce_planks");
				}
			}
		}
		for (int h = 0; h < 7; h++) {
			put(x, y + 2 + h, z, "oak_log[axis=y]");
		}
		for (int h = 3; h <= 7; h++) {
			put(x, y + h, z + 1, "white_wool");
			put(x + (h % 2 == 0 ? 1 : -1), y + h, z + 1, "white_wool");
		}
		put(x, y + 9, z, "oak_fence");
	}

	/** FARMLAND: a scarecrow in a pumpkin hat over a haystack. */
	private void scarecrow(int x, int y, int z) {
		for (int h = 0; h < 3; h++) {
			put(x, y + h, z, "birch_fence");
		}
		put(x, y + 3, z, "hay_block[axis=y]");
		put(x, y + 4, z, "carved_pumpkin[facing=north]");
		put(x - 1, y + 3, z, "birch_fence");
		put(x + 1, y + 3, z, "birch_fence");
		for (int dx = -2; dx <= 2; dx++) {
			put(x + dx, y, z + 2, "hay_block[axis=x]");
			if (Math.abs(dx) < 2) {
				put(x + dx, y + 1, z + 2, "hay_block[axis=x]");
			}
		}
		put(x, y + 2, z + 2, "hay_block[axis=z]");
	}

	// ---- new C set pieces (phase 2): private methods, begin ----
	/** C_KITE_HILL: a kite flying high over its anchor post, spars across, a tail of bows. */
	private void kite(int x, int y, int z) {
		put(x, y, z, "oak_fence");
		for (int h = 1; h <= 7; h++) {
			put(x, y + h, z, "chain[axis=y]");
		}
		int cy = y + 11;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = -3; dy <= 3; dy++) {
				if (Math.abs(dx) + Math.abs(dy) > 3) {
					continue;
				}
				String cloth = dy > 0 ? (dx <= 0 ? "red_wool" : "light_blue_wool") : (dx <= 0 ? "light_blue_wool" : "red_wool");
				if (dx == 0) {
					cloth = "stripped_birch_log[axis=y]";
				} else if (dy == 0) {
					cloth = "stripped_birch_log[axis=x]";
				}
				put(x + dx, cy + dy, z, cloth);
			}
		}
		// the tail streams off the bottom point in bows
		put(x + 1, y + 7, z, "white_wool");
		put(x + 2, y + 6, z, "red_wool");
		put(x + 2, y + 5, z, "white_wool");
		put(x + 3, y + 4, z, "light_blue_wool");
	}

	/** C_SCALLOP: a sandcastle, four turrets round a keep, a flag on top. */
	private void sandcastle(int x, int y, int z) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
				boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				if (corner) {
					for (int h = 0; h < 4; h++) {
						put(x + dx, y + h, z + dz, "chiseled_sandstone");
					}
					put(x + dx, y + 4, z + dz, "sandstone_wall");
				} else if (wall) {
					put(x + dx, y, z + dz, "cut_sandstone");
					put(x + dx, y + 1, z + dz, "cut_sandstone");
					if ((dx + dz) % 2 == 0) {
						put(x + dx, y + 2, z + dz, "sandstone_slab[type=bottom]");
					}
				} else {
					for (int h = 0; h < 6; h++) {
						put(x + dx, y + h, z + dz, "smooth_sandstone");
					}
					if ((dx + dz) % 2 != 0) {
						put(x + dx, y + 6, z + dz, "sandstone_slab[type=bottom]");
					}
				}
			}
		}
		for (int h = 6; h <= 9; h++) {
			put(x, y + h, z, "oak_fence");
		}
		put(x + 1, y + 9, z, "cyan_wool");
		put(x + 1, y + 8, z, "cyan_wool");
		put(x + 2, y + 9, z, "cyan_wool");
	}

	/** C_HEARTFIELD: a striped hot-air balloon moored beside the road, burner lit. */
	private void balloon(int x, int y, int z) {
		put(x, y, z, "oak_fence");
		put(x, y + 1, z, "oak_fence");
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				put(x + dx, y + 2, z + dz, "spruce_planks");
				if (dx != 0 || dz != 0) {
					put(x + dx, y + 3, z + dz, "spruce_fence");
				}
				if (Math.abs(dx) == 1 && Math.abs(dz) == 1) {
					for (int h = 4; h <= 7; h++) {
						put(x + dx, y + h, z + dz, "oak_fence");   // the ropes up to the envelope
					}
				}
			}
		}
		put(x, y + 3, z, "lantern[hanging=false]");
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = -3; dy <= 3; dy++) {
				for (int dz = -3; dz <= 3; dz++) {
					int r2 = dx * dx + (dy < 0 ? dy * dy * 2 : dy * dy) + dz * dz;
					if (r2 > 11 || r2 < 5) {
						continue;
					}
					put(x + dx, y + 10 + dy, z + dz, Math.floorMod(dx + dz, 2) == 0 ? "red_wool" : "yellow_wool");
				}
			}
		}
		put(x, y + 14, z, "red_wool");
	}

	/** C_HONEYCOMB: a honeycomb tower of hives, honey dripping off its crown, flowers at its foot. */
	private void apiary(int x, int y, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int w = Math.abs(dz) == 2 ? 0 : 1;
				if (Math.abs(dx) > w) {
					continue;
				}
				for (int h = 0; h < 6; h++) {
					put(x + dx, y + h, z + dz, "honeycomb_block");
				}
				put(x + dx, y + 6, z + dz, "honey_block");
			}
		}
		put(x - 2, y + 1, z, "beehive[facing=west,honey_level=5]");
		put(x + 2, y + 3, z, "bee_nest[facing=east,honey_level=5]");
		put(x - 2, y + 4, z + 1, "beehive[facing=west,honey_level=5]");
		put(x, y + 7, z, "honey_block");
		put(x + 2, y, z - 2, "potted_oxeye_daisy");
		put(x - 2, y, z + 2, "potted_cornflower");
		put(x + 2, y, z + 2, "potted_allium");
		put(x - 2, y, z - 2, "potted_dandelion");
	}

	/** C_HORSESHOE: a red barn with a hay loft and a copper-capped silo. */
	private void barnAndSilo(int x, int y, int z) {
		for (int dx = -3; dx <= 0; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean wall = dx == -3 || dx == 0 || Math.abs(dz) == 2;
				if (wall) {
					for (int h = 0; h < 3; h++) {
						if (dx == 0 && dz == 0 && h < 2) {
							continue;   // the barn door, open
						}
						put(x + dx, y + h, z + dz, Math.abs(dz) == 2 && (dx == -3 || dx == 0) ? "white_terracotta" : "red_terracotta");
					}
				}
				put(x + dx, y + 3, z + dz, "dark_oak_planks");
				if (Math.abs(dz) <= 1) {
					put(x + dx, y + 4, z + dz, "dark_oak_planks");
				}
				if (dz == 0) {
					put(x + dx, y + 5, z + dz, "dark_oak_slab[type=bottom]");
				}
			}
		}
		put(x - 1, y, z, "hay_block[axis=x]");
		put(x + 1, y, z + 2, "hay_block[axis=z]");
		for (int dx = 2; dx <= 3; dx++) {
			for (int dz = -2; dz <= -1; dz++) {
				for (int h = 0; h < 9; h++) {
					put(x + dx, y + h, z + dz, "smooth_stone");
				}
				put(x + dx, y + 9, z + dz, "waxed_cut_copper");
			}
		}
		put(x + 2, y + 10, z - 2, "waxed_cut_copper_slab[type=bottom]");
		put(x + 3, y + 10, z - 1, "lightning_rod");
	}
	// ---- new C set pieces (phase 2) end ----

	// ================================================================ B landmarks

	private void landmarkB(double t, int surf) {
		switch (track) {
			case B_CANYON -> onPlinth(t, surf, this::hoodoo);
			case B_FORD -> onPlinth(t, surf, this::cairn);
			case B_FROST -> onPlinth(t, surf, this::iceSpire);
			case B_MESA -> onPlinth(t, surf, this::mesaArch);
			case B_RAPIDS -> onPlinth(t, surf, this::millWheel);
			case B_GLACIER -> onPlinth(t, surf, this::frozenFall);
			// ---- new B landmarks (phase 2) begin ----
			case B_ACACIA -> onPlinth(t, surf, this::themeLandmark);   // the first SAVANNA course: the great acacia
			case B_GULCH -> onPlinth(t, surf, this::balancedRock);
			case B_OXBOW -> onPlinth(t, surf, this::stiltHut);
			case B_BAOBAB -> onPlinth(t, surf, this::baobab);
			case B_KOPJE -> onPlinth(t, surf, this::kopje);
			case B_SNOWCAP -> onPlinth(t, surf, this::snowman);
			// ---- new B landmarks (phase 2) end ----
			default -> onPlinth(t, surf, this::themeLandmark);
		}
	}

	/** B_CANYON: a banded hoodoo. */
	private void hoodoo(int x, int y, int z) {
		pillar(x, y, z, "orange_terracotta", "red_terracotta", "terracotta", 9);
		pillar(x + 1, y, z, "yellow_terracotta", "orange_terracotta", "brown_terracotta", 7);
		pillar(x, y, z + 1, "red_terracotta", "brown_terracotta", "terracotta", 8);
		pillar(x + 1, y, z + 1, "orange_terracotta", "yellow_terracotta", "terracotta", 6);
		put(x, y + 10, z, "terracotta");
		put(x + 1, y + 10, z, "terracotta");
	}

	/** B_FORD: a mossy cairn with a spring in a basin on top. */
	private void cairn(int x, int y, int z) {
		for (int h = 0; h < 3; h++) {
			int r = 3 - h;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					put(x + dx, y + h, z + dz, (dx + dz + h) % 3 == 0 ? "mossy_cobblestone" : "cobblestone");
				}
			}
		}
		// sunk into the top step and ringed by it: a source set on the peak ran down
		// the cairn and over the road the first time anything touched it
		put(x, y + 2, z, "water");
	}

	/** B_FROST: an ice spire. */
	private void iceSpire(int x, int y, int z) {
		for (int h = 0; h < 10; h++) {
			int r = h < 3 ? 2 : h < 7 ? 1 : 0;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					put(x + dx, y + h, z + dz, h % 3 == 0 ? "blue_ice" : "packed_ice");
				}
			}
		}
		put(x, y + 10, z, "sea_lantern");
	}

	/** B_MESA: a terracotta arch standing off the canyon rim. */
	private void mesaArch(int x, int y, int z) {
		for (int h = 0; h < 8; h++) {
			put(x - 3, y + h, z, "orange_terracotta");
			put(x - 3, y + h, z + 1, "terracotta");
			put(x + 3, y + h, z, "red_terracotta");
			put(x + 3, y + h, z + 1, "orange_terracotta");
		}
		for (int dx = -3; dx <= 3; dx++) {
			int lift = Math.abs(dx) >= 2 ? 0 : 1;
			put(x + dx, y + 8 + lift, z, "orange_terracotta");
			put(x + dx, y + 8 + lift, z + 1, "yellow_terracotta");
			if (Math.abs(dx) < 2) {
				put(x + dx, y + 7 + lift, z, "brown_terracotta");
			}
		}
	}

	/** B_RAPIDS: a mill wheel over a dry stone race. */
	private void millWheel(int x, int y, int z) {
		for (int h = 0; h < 5; h++) {
			put(x + 2, y + h, z, "cobblestone");
			put(x + 2, y + h, z + 1, "mossy_cobblestone");
			put(x + 2, y + h, z - 1, "cobblestone");
		}
		put(x + 2, y + 5, z, "spruce_slab[type=bottom]");
		int cx = x - 1, cy = y + 4;
		for (int a = 0; a < 12; a++) {
			double ang = a * Math.PI / 6.0D;
			int dx = (int) Math.round(Math.cos(ang) * 3.0D), dy = (int) Math.round(Math.sin(ang) * 3.0D);
			put(cx, cy + dy, z + dx, "spruce_planks");
			put(cx, cy + dy / 2, z + dx / 2, "spruce_fence");
		}
		put(cx, cy, z, "stripped_spruce_log[axis=x]");
	}

	/** B_GLACIER: a waterfall caught mid-fall. */
	private void frozenFall(int x, int y, int z) {
		for (int dz = -2; dz <= 2; dz++) {
			int h = 9 - Math.abs(dz) * 2;
			for (int i = 0; i < h; i++) {
				put(x, y + i, z + dz, i > h - 3 ? "snow_block" : "packed_ice");
				put(x - 1, y + i, z + dz, i % 3 == 0 ? "blue_ice" : "ice");
			}
			put(x, y + h, z + dz, "snow[layers=4]");
		}
		put(x - 2, y, z, "blue_ice");
		put(x - 2, y, z + 1, "packed_ice");
		put(x - 2, y + 1, z, "ice");
	}

	/** SAVANNA: a great flat-crowned acacia, its trunk leaning out of a termite mound. */
	private void greatAcacia(int x, int y, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				put(x + dx, y, z + dz, "packed_mud");
			}
		}
		put(x, y + 1, z, "packed_mud");
		for (int h = 1; h < 8; h++) {
			put(x + (h >= 4 ? 1 : 0), y + h, z, "acacia_wood");
		}
		for (int dx = -2; dx <= 4; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (Math.abs(dx - 1) + Math.abs(dz) <= 4) {
					put(x + dx, y + 8, z + dz, "acacia_leaves[persistent=true]");
				}
			}
		}
		for (int dx = 0; dx <= 2; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				put(x + dx, y + 9, z + dz, "acacia_leaves[persistent=true]");
			}
		}
	}

	// ---- new B set pieces (phase 2): private methods, begin ----
	/** B_GULCH: a red boulder balanced on a wind-cut neck of sandstone. */
	private void balancedRock(int x, int y, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				put(x + dx, y, z + dz, "red_sandstone");
			}
		}
		put(x, y + 1, z, "red_sandstone");
		put(x + 1, y + 1, z, "cut_red_sandstone");
		for (int h = 2; h <= 5; h++) {
			put(x, y + h, z, h == 4 ? "chiseled_red_sandstone" : "cut_red_sandstone");
		}
		// the boulder: a squat ellipsoid, wider than its neck, leaning a block off centre
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int dy = -2; dy <= 2; dy++) {
					double ex = (dx - 0.5D) / 3.2D, ez = dz / 2.8D, ey = dy / 2.2D;
					if (ex * ex + ez * ez + ey * ey <= 1.0D) {
						put(x + dx, y + 8 + dy, z + dz, dy == 0 ? "orange_terracotta" : "red_sandstone");
					}
				}
			}
		}
		put(x - 2, y, z + 2, "dead_bush");
	}

	/** B_OXBOW: a fisher's hut on stilts, a lantern at the door and a barrel of the catch. */
	private void stiltHut(int x, int y, int z) {
		for (int dx = -2; dx <= 2; dx += 4) {
			for (int dz = -2; dz <= 2; dz += 4) {
				for (int h = 0; h < 4; h++) {
					put(x + dx, y + h, z + dz, "stripped_oak_log[axis=y]");
				}
			}
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				put(x + dx, y + 4, z + dz, "oak_planks");
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				boolean wall = Math.abs(dx) == 1 || Math.abs(dz) == 1;
				for (int h = 5; h <= 6 && wall; h++) {
					if (!(dx == 0 && dz == -1)) {   // the door
						put(x + dx, y + h, z + dz, "spruce_planks");
					}
				}
			}
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int lift = 2 - Math.max(Math.abs(dx), Math.abs(dz));
				put(x + dx, y + 6 + lift, z + dz, "dark_oak_slab[type=bottom]");
			}
		}
		put(x - 1, y + 5, z - 2, "lantern[hanging=true]");   // under the eave
		put(x + 1, y + 5, z - 2, "barrel[facing=east]");
	}

	/** B_BAOBAB: a baobab, a bottle trunk with a crown of stubby branches. */
	private void baobab(int x, int y, int z) {
		for (int h = 0; h < 8; h++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
					if (!corner || (h > 0 && h < 5)) {
						put(x + dx, y + h, z + dz, "stripped_jungle_wood");
					}
				}
			}
		}
		int[][] arms = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] a : arms) {
			for (int k = 2; k <= 3; k++) {
				put(x + a[0] * k, y + 7 + k - 2, z + a[1] * k, "jungle_wood");
			}
			int ex = x + a[0] * 3, ez = z + a[1] * 3;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					put(ex + dx, y + 9, ez + dz, "jungle_leaves[persistent=true]");
				}
			}
			put(ex, y + 10, ez, "jungle_leaves[persistent=true]");
		}
		put(x, y + 8, z, "jungle_wood");
	}

	/** B_KOPJE: a kopje, granite boulders piled into a lookout. */
	private void kopje(int x, int y, int z) {
		boulder(x - 1, y + 1, z, 2.6D, 1.8D, "granite");
		boulder(x + 2, y + 1, z + 1, 1.7D, 1.4D, "granite");
		boulder(x, y + 3, z - 1, 1.6D, 1.3D, "polished_granite");
		boulder(x + 1, y + 5, z, 1.1D, 1.0D, "granite");
		put(x + 1, y + 6, z, "polished_granite");
		put(x + 1, y + 7, z, "dead_bush");
	}

	/** A squashed ball of {@code block} about (x, y, z), {@code r} across and {@code h} up. */
	private void boulder(int x, int y, int z, double r, double h, String block) {
		int ri = (int) Math.ceil(r), hi = (int) Math.ceil(h);
		for (int dx = -ri; dx <= ri; dx++) {
			for (int dz = -ri; dz <= ri; dz++) {
				for (int dy = -hi; dy <= hi; dy++) {
					double ex = dx / r, ez = dz / r, ey = dy / h;
					if (ex * ex + ez * ez + ey * ey <= 1.0D) {
						put(x + dx, y + dy, z + dz, block);
					}
				}
			}
		}
	}

	/** B_SNOWCAP: a snowman in a top hat, coal buttons, a carrot nose and stick arms. */
	private void snowman(int x, int y, int z) {
		boulder(x, y + 2, z, 2.6D, 2.4D, "snow_block");
		boulder(x, y + 5, z, 1.9D, 1.6D, "snow_block");
		boulder(x, y + 8, z, 1.6D, 1.3D, "snow_block");
		// a face both ways along z (either may be the side the riders see), arms along x
		for (int side = -1; side <= 1; side += 2) {
			put(x, y + 5, z + side * 2, "coal_block");
			put(x, y + 3, z + side * 3, "coal_block");
			put(x, y + 8, z + side * 2, "orange_terracotta");
			put(x - 1, y + 9, z + side, "coal_block");
			put(x + 1, y + 9, z + side, "coal_block");
		}
		for (int k = 2; k <= 3; k++) {
			put(x + k, y + 5 + (k - 2), z, "spruce_fence");
			put(x - k, y + 5 + (k - 2), z, "spruce_fence");
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				put(x + dx, y + 10, z + dz, "black_wool");
			}
		}
		put(x, y + 11, z, "black_wool");
		put(x, y + 12, z, "black_wool");
	}
	// ---- new B set pieces (phase 2) end ----

	// ================================================================ A landmarks

	private void landmarkA(double t, int surf) {
		switch (track) {
			case A_CRYSTAL -> onPlinth(t, surf, this::geode);
			case A_CANOPY -> onPlinth(t, surf, this::stepPyramid);
			case A_EMBER -> onPlinth(t, surf, this::fortressTower);
			case A_DEEPS -> onPlinth(t, surf, this::dripstoneHall);
			case A_TEMPLE -> onPlinth(t, surf, this::idol);
			case A_INFERNO -> onPlinth(t, surf, this::boneArch);
			// ---- new A landmarks (phase 2) begin ----
			case A_TOADSTOOL -> onPlinth(t, surf, this::giantMushroom);
			case A_AMMONITE -> onPlinth(t, surf, this::ammoniteFossil);
			case A_FORGE -> onPlinth(t, surf, this::forgeAnvil);
			case A_GROTTO -> onPlinth(t, surf, this::blindfishStatue);
			case A_MOONSHELF -> onPlinth(t, surf, this::shelfFungus);
			case A_MACHETE -> onPlinth(t, surf, this::machetePlanted);
			// ---- new A landmarks (phase 2) end ----
			default -> onPlinth(t, surf, this::themeLandmark);
		}
	}

	/** A_CRYSTAL: an amethyst geode, split open on one side. */
	private void geode(int x, int y, int z) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int dy = 0; dy <= 6; dy++) {
					double r = Math.sqrt(dx * dx + dz * dz + (dy - 3) * (dy - 3));
					if (r <= 3.4D && r > 2.4D && !(dz > 1 && dy > 1 && dy < 5)) {
						put(x + dx, y + dy, z + dz, "budding_amethyst");
					} else if (r <= 2.4D) {
						put(x + dx, y + dy, z + dz, (dx + dz + dy) % 2 == 0 ? "amethyst_block" : "air");
					}
				}
			}
		}
		put(x, y + 3, z, "amethyst_cluster[facing=up]");
	}

	/** A_CANOPY: a mossy step pyramid with a gold cap and a vine. */
	private void stepPyramid(int x, int y, int z) {
		for (int h = 0; h < 5; h++) {
			int r = 4 - h;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					put(x + dx, y + h, z + dz, (dx * dz + h) % 4 == 0 ? "mossy_cobblestone" : "mossy_stone_bricks");
				}
			}
		}
		put(x, y + 5, z, "gold_block");
		put(x - 1, y + 5, z, "vine[east=true]");
	}

	/** A_EMBER: a fortress tower with a lava fall. */
	private void fortressTower(int x, int y, int z) {
		for (int h = 0; h < 11; h++) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
					put(x + dx, y + h, z + dz, edge ? "nether_bricks" : h == 10 ? "lava" : "air");
				}
			}
		}
		put(x, y + 10, z + 2, "lava");
		put(x, y + 9, z + 2, "air");
		for (int dx = -2; dx <= 2; dx += 2) {
			put(x + dx, y + 11, z - 2, "nether_brick_wall");
			put(x + dx, y + 11, z + 2, "nether_brick_wall");
		}
		put(x, y + 12, z, "glowstone");
	}

	/** A_DEEPS: a hall of dripstone columns under a tiled roof. */
	private void dripstoneHall(int x, int y, int z) {
		for (int i = 0; i < 4; i++) {
			int dx = i % 2 == 0 ? -2 : 2, dz = i < 2 ? -2 : 2;
			int h = 6 + i % 3;
			for (int k = 0; k < h; k++) {
				put(x + dx, y + k, z + dz, "dripstone_block");
			}
			put(x + dx, y + h, z + dz, "pointed_dripstone[vertical_direction=up]");
			put(x + dx, y + h + 3, z + dz, "pointed_dripstone[vertical_direction=down]");
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				put(x + dx, y + 9, z + dz, "deepslate_tiles");
			}
		}
		put(x, y + 1, z, "amethyst_cluster[facing=up]");
		put(x + 1, y + 1, z - 1, "amethyst_block");
	}

	/** A_TEMPLE: a carved idol half sunk in the moss, gold still in its eyes. */
	private void idol(int x, int y, int z) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
				if (corner) {
					continue;
				}
				for (int h = 0; h < 6; h++) {
					put(x + dx, y + h, z + dz, (h + dx + dz) % 4 == 0 ? "mossy_cobblestone" : "mossy_stone_bricks");
				}
			}
		}
		put(x - 1, y + 4, z - 2, "gold_block");
		put(x + 1, y + 4, z - 2, "gold_block");
		for (int dx = -1; dx <= 1; dx++) {
			put(x + dx, y + 2, z - 2, "chiseled_stone_bricks");
			put(x + dx, y + 6, z, "moss_block");
		}
		put(x, y + 7, z, "moss_block");
	}

	/** A_INFERNO: a bone arch over a soul fire. */
	private void boneArch(int x, int y, int z) {
		for (int h = 0; h < 6; h++) {
			put(x, y + h, z - 2, "bone_block[axis=y]");
			put(x, y + h, z + 2, "bone_block[axis=y]");
		}
		for (int dz = -2; dz <= 2; dz++) {
			put(x, y + 6 + (Math.abs(dz) == 2 ? 0 : 1), z + dz, "bone_block[axis=z]");
		}
		put(x, y, z, "soul_soil");
		put(x, y + 1, z, "soul_fire");
		put(x + 1, y, z + 1, "bone_block[axis=x]");
		put(x - 1, y, z - 1, "bone_block[axis=x]");
	}

	/** MUSHROOM: a giant red mushroom, froglights glowing under the cap. */
	private void giantMushroom(int x, int y, int z) {
		for (int h = 0; h < 8; h++) {
			put(x, y + h, z, "mushroom_stem");
		}
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				int r = Math.abs(dx) + Math.abs(dz);
				if (r <= 4) {
					put(x + dx, y + 8, z + dz, "red_mushroom_block");
				}
				if (r <= 2) {
					put(x + dx, y + 9, z + dz, "red_mushroom_block");
				}
				if (r == 5 && Math.abs(dx) < 3 && Math.abs(dz) < 3) {
					put(x + dx, y + 7, z + dz, "red_mushroom_block");
				}
			}
		}
		put(x + 1, y + 7, z, "ochre_froglight");
		put(x - 1, y + 7, z, "ochre_froglight");
	}

	// ---- new A set pieces (phase 2): private methods, begin ----
	// (A_TOADSTOOL, the first course on MUSHROOM, stands by the theme's own giant mushroom)

	/** A_AMMONITE: a fossil ammonite, a bone coil set in an upright slab of calcite with a tuff back. */
	private void ammoniteFossil(int x, int y, int z) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = 0; dy <= 6; dy++) {
				if (dx * dx + (dy - 3) * (dy - 3) <= 12) {
					put(x + dx, y + dy, z, "calcite");
					put(x + dx, y + dy, z + 1, "tuff");
				}
			}
		}
		// two and a quarter turns of the coil, widening from the heart outward
		for (double a = 0.0D; a < Math.PI * 4.5D; a += 0.15D) {
			double r = 0.4D + a / (Math.PI * 2.0D) * 1.25D;
			int dx = (int) Math.round(r * Math.cos(a)), dy = 3 + (int) Math.round(r * Math.sin(a));
			if (Math.abs(dx) <= 3 && dy >= 0 && dy <= 6) {
				put(x + dx, y + dy, z, "bone_block[axis=z]");
			}
		}
		put(x - 2, y, z - 1, "tuff");
		put(x + 2, y, z - 1, "tuff");
	}

	/** A_FORGE: an iron anvil, horn and heel, over a hearth of glowing magma. */
	private void forgeAnvil(int x, int y, int z) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean rim = Math.abs(dx) == 3 || Math.abs(dz) == 2;
				put(x + dx, y, z + dz, rim ? "polished_blackstone_bricks" : "magma_block");
			}
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				put(x + dx, y + 1, z + dz, "iron_block");   // foot
				put(x + dx, y + 4, z + dz, "iron_block");   // face
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			put(x + dx, y + 2, z, "iron_block");             // waist
			put(x + dx, y + 3, z, "iron_block");
		}
		put(x + 3, y + 4, z, "iron_block");                 // horn
		put(x + 3, y + 5, z, "iron_block");
		put(x - 3, y + 4, z, "iron_block");                 // heel
		put(x, y + 5, z, "anvil[facing=east]");
		put(x - 3, y + 1, z - 2, "polished_blackstone_wall");
		put(x - 3, y + 2, z - 2, "lantern[hanging=false]");
	}

	/** A_GROTTO: a pale blind cave fish leaping off a basalt stalk, no eyes, pink gills. */
	private void blindfishStatue(int x, int y, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				put(x + dx, y, z + dz, "smooth_basalt");
			}
		}
		put(x, y + 1, z, "smooth_basalt");
		put(x, y + 2, z, "smooth_basalt");
		put(x + 1, y + 1, z + 1, "glow_lichen[down=true]");
		put(x - 1, y + 1, z - 1, "glow_lichen[down=true]");
		// side on, tail to the west: rows from the top (dy 7) down to the lower tail fin (dy 2)
		String[] rows = {"#......", "##.###.", ".######", ".######", "##.###.", "#......"};
		for (int r = 0; r < rows.length; r++) {
			for (int c = 0; c < 7; c++) {
				if (rows[r].charAt(c) == '#') {
					put(x + c - 3, y + 7 - r, z, "white_terracotta");
				}
			}
		}
		put(x + 1, y + 5, z, "pink_terracotta");            // gills
		put(x + 1, y + 4, z, "pink_terracotta");
	}

	/** A_MOONSHELF: a dead trunk ringed with glowing shelf fungi. */
	private void shelfFungus(int x, int y, int z) {
		for (int h = 0; h < 12; h++) {
			put(x, y + h, z, "dark_oak_log[axis=y]");
		}
		put(x, y + 12, z, "dark_oak_wood");
		put(x + 1, y + 11, z, "dark_oak_wood");
		// {height, direction x, direction z, radius}: a half-disc shelf sticking out of the trunk
		int[][] shelves = {{3, 1, 0, 3}, {6, 0, -1, 3}, {8, -1, 0, 2}, {10, 0, 1, 2}};
		for (int[] s : shelves) {
			int h = s[0], ox = s[1], oz = s[2], rad = s[3];
			for (int a = 1; a <= rad; a++) {
				for (int b = -rad; b <= rad; b++) {
					if (a * a + b * b <= rad * rad + 1) {
						put(x + ox * a + oz * b, y + h, z + oz * a + ox * b, "brown_mushroom_block");
					}
				}
			}
			put(x + ox, y + h - 1, z + oz, "verdant_froglight");
		}
	}

	/** A_MACHETE: a great machete driven point first into a jungle stump. */
	private void machetePlanted(int x, int y, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				put(x + dx, y, z + dz, "jungle_wood");
				if (dx == 0 || dz == 0) {
					put(x + dx, y + 1, z + dz, "jungle_wood");
				}
			}
		}
		// the blade: spine on the west, edge on the east, widest just above the stump
		for (int h = 1; h <= 8; h++) {
			put(x - 1, y + h, z, "smooth_stone");
			put(x, y + h, z, "polished_diorite");
			if (h >= 2 && h <= 5) {
				put(x + 1, y + h, z, "polished_diorite");
			}
		}
		for (int dx = -2; dx <= 1; dx++) {
			put(x + dx, y + 9, z, "polished_blackstone");    // guard
		}
		for (int h = 10; h <= 12; h++) {
			put(x - 1, y + h, z, "stripped_mangrove_log[axis=y]");
		}
		put(x - 1, y + 13, z, "mangrove_wood");                // pommel
		put(x + 1, y + 1, z + 1, "vine[north=true]");
	}
	// ---- new A set pieces (phase 2) end ----

	// ================================================================ S landmarks

	private void landmarkS(double t, int surf) {
		switch (track) {
			// over the road, not on a plinth
			case S_SKYWAY -> archOver(t, surf, "magenta_stained_glass", "end_rod");
			case S_KEEP -> archOver(t, surf, "polished_blackstone_bricks", "soul_lantern[hanging=true]");
			case S_STARFALL -> halo(t, surf);
			case S_VOID -> onPlinth(t, surf, this::obsidianSpire);
			case S_CITADEL -> onPlinth(t, surf, this::gatehouse);
			case S_MAELSTROM -> onPlinth(t, surf, this::crystalCage);
			// ---- new S landmarks (phase 2) begin ----
			// S_ABYSS is the first course on DEEP_DARK: the theme's warden frame (default)
			case S_ZENITH -> onPlinth(t, surf, this::comet);
			case S_BASTION -> onPlinth(t, surf, this::belfry);
			case S_ORBIT -> onPlinth(t, surf, this::ringedPlanet);
			case S_ECLIPSE -> onPlinth(t, surf, this::eclipse);
			case S_RIFT -> onPlinth(t, surf, this::riftShards);
			// ---- new S landmarks (phase 2) end ----
			default -> onPlinth(t, surf, this::themeLandmark);
		}
	}

	/** S_VOID: an obsidian spire with a bedrock cap and end rods. */
	private void obsidianSpire(int x, int y, int z) {
		for (int h = 0; h < 14; h++) {
			put(x, y + h, z, "obsidian");
			put(x + 1, y + h, z, "obsidian");
			put(x, y + h, z + 1, "obsidian");
			put(x + 1, y + h, z + 1, "obsidian");
		}
		put(x, y + 14, z, "bedrock");
		put(x + 1, y + 14, z + 1, "end_rod");
		put(x + 1, y + 14, z, "end_rod");
		put(x, y + 14, z + 1, "end_rod");
	}

	/** S_CITADEL: a gatehouse flying the house colours. */
	private void gatehouse(int x, int y, int z) {
		for (int dz = -3; dz <= 3; dz += 6) {
			for (int h = 0; h < 10; h++) {
				for (int dx = -1; dx <= 0; dx++) {
					put(x + dx, y + h, z + dz,
							h % 4 == 3 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks");
				}
			}
			put(x, y + 10, z + dz, "polished_blackstone_brick_wall");
			put(x - 1, y + 10, z + dz, "polished_blackstone_brick_wall");
			for (int h = 4; h < 8; h++) {
				put(x + 1, y + h, z + dz, "red_wool");
			}
		}
		for (int dz = -2; dz <= 2; dz++) {
			put(x, y + 8, z + dz, "polished_blackstone_bricks");
			put(x - 1, y + 8, z + dz, "polished_blackstone");
			if (Math.abs(dz) == 1) {
				put(x, y + 7, z + dz, "soul_lantern[hanging=true]");
			}
		}
	}

	/** S_MAELSTROM: a caged crystal on an obsidian plinth. */
	private void crystalCage(int x, int y, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				put(x + dx, y, z + dz, "obsidian");
				put(x + dx, y + 1, z + dz, Math.abs(dx) + Math.abs(dz) == 2 ? "obsidian" : "purpur_block");
				put(x + dx, y + 7, z + dz, "purpur_slab[type=bottom]");
				if (Math.abs(dx) + Math.abs(dz) == 2) {
					for (int h = 2; h < 7; h++) {
						put(x + dx, y + h, z + dz, "iron_bars");
					}
				}
			}
		}
		put(x, y + 3, z, "magenta_stained_glass");
		put(x, y + 4, z, "end_rod");
	}

	/** SKYWAY (a course without its own): three rainbow glass spires round an end rod. */
	private void prismSpire(int x, int y, int z) {
		for (int i = 0; i < 3; i++) {
			int dx = i == 0 ? -1 : i == 1 ? 1 : 0, dz = i == 2 ? 1 : -1;
			for (int h = 0; h < 7 + i * 2; h++) {
				put(x + dx, y + h, z + dz, RAINBOW[(h + i * 2) % RAINBOW.length] + "_stained_glass");
			}
		}
		put(x, y, z, "sea_lantern");
		put(x, y + 1, z, "end_rod");
	}

	/** KEEP (a course without its own): a blackstone obelisk over a soul fire. */
	private void obelisk(int x, int y, int z) {
		for (int h = 0; h < 11; h++) {
			int r = h < 2 ? 1 : 0;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					put(x + dx, y + h, z + dz, h % 4 == 1 ? "gilded_blackstone" : "polished_blackstone_bricks");
				}
			}
		}
		put(x, y + 11, z, "crying_obsidian");
		put(x + 2, y - 1, z, "soul_soil");
		put(x + 2, y, z, "soul_fire");
	}

	/** DEEP_DARK: a warden's frame of reinforced deepslate, sculk creeping over the sill. */
	private void wardenFrame(int x, int y, int z) {
		for (int dz = -3; dz <= 3; dz++) {
			boolean post = Math.abs(dz) == 3;
			for (int h = 0; h < 7; h++) {
				if (post || h == 0 || h == 6) {
					put(x, y + h, z + dz, "reinforced_deepslate");
				}
			}
			put(x - 1, y, z + dz, "sculk");
			put(x + 1, y, z + dz, "sculk");
		}
		put(x, y + 7, z, "chiseled_deepslate");
		put(x, y + 5, z - 2, "soul_lantern[hanging=true]");
		put(x, y + 5, z + 2, "soul_lantern[hanging=true]");
		put(x - 1, y + 1, z, "sculk_catalyst");
	}

	// ---- new S set pieces (phase 2): private methods, begin ----
	/** S_ZENITH: a comet standing on its tail, a glowstone head streaming glass back down to the plinth. */
	private void comet(int x, int y, int z) {
		int hx = x + 2, hy = y + 11, hz = z - 2;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) < 3) {
						put(hx + dx, hy + dy, hz + dz, "glowstone");
					}
				}
			}
		}
		// the tail: white at its core, a blue fringe that thins out toward the plinth
		for (int k = 1; k <= 10; k++) {
			int tx = hx - k * 4 / 10, ty = hy - 1 - k, tz = hz + k * 4 / 10;
			if (k < 8) {
				put(tx - 1, ty + 1, tz, "light_blue_stained_glass");
				put(tx, ty + 1, tz + 1, "light_blue_stained_glass");
			}
			put(tx, ty, tz, "white_stained_glass");
		}
		put(hx + 1, hy + 1, hz - 1, "end_rod");
	}

	/** S_BASTION: a belfry on the bastion, a bell hung in the open top under a spire. */
	private void belfry(int x, int y, int z) {
		for (int h = 0; h < 12; h++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					// a solid shaft, then four corner posts round the open belfry
					if (h < 8 || (Math.abs(dx) == 1 && Math.abs(dz) == 1)) {
						put(x + dx, y + h, z + dz, h % 4 == 3 ? "cracked_polished_blackstone_bricks" : "polished_blackstone_bricks");
					}
				}
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				put(x + dx, y + 12, z + dz, "polished_blackstone_bricks");
			}
		}
		put(x, y + 11, z, "bell[attachment=ceiling]");
		put(x, y + 13, z, "polished_blackstone_brick_wall");
		put(x, y + 14, z, "polished_blackstone_brick_wall");
		put(x, y + 15, z, "soul_lantern[hanging=false]");
		// buttresses at the foot
		for (int s = -2; s <= 2; s += 4) {
			put(x + s, y, z, "chiseled_polished_blackstone");
			put(x, y, z + s, "chiseled_polished_blackstone");
		}
	}

	/** S_ORBIT: a ringed planet floating on end rods, banded gold and orange, its tilted ring of pale glass. */
	private void ringedPlanet(int x, int y, int z) {
		for (int h = 0; h < 6; h++) {
			put(x, y + h, z, "end_rod");
		}
		int cy = y + 9;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (dx * dx + dy * dy + dz * dz <= 5) {
						put(x + dx, cy + dy, z + dz, dy == 0 ? "orange_concrete" : Math.abs(dy) == 1 ? "yellow_concrete" : "white_concrete");
					}
				}
			}
		}
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				double r = Math.hypot(dx, dz);
				if (r > 2.5D && r <= 3.5D) {
					put(x + dx, cy + (int) Math.round(dx * 0.35D), z + dz, "light_gray_stained_glass");
				}
			}
		}
	}

	/** S_ECLIPSE: an eclipse on a quartz column, a black moon ringed by a burning corona. */
	private void eclipse(int x, int y, int z) {
		for (int h = 0; h < 5; h++) {
			put(x, y + h, z, "quartz_pillar");
		}
		int cy = y + 8;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = -3; dy <= 3; dy++) {
				double d = Math.hypot(dx, dy);
				if (d <= 2.3D) {
					put(x + dx, cy + dy, z, "black_concrete");
				} else if (d <= 3.4D) {
					put(x + dx, cy + dy, z, "ochre_froglight");
				}
			}
		}
		put(x, cy + 4, z, "end_rod");
	}

	/** S_RIFT: two deepslate shards leaning apart over a crack of sculk, a shrieker in the gap. */
	private void riftShards(int x, int y, int z) {
		for (int h = 0; h < 12; h++) {
			int lean = h / 4;
			String b = h % 3 == 2 ? "cracked_deepslate_bricks" : "deepslate_bricks";
			put(x - 1 - lean, y + h, z, b);
			if (h < 8) {
				put(x - 1 - lean, y + h, z + 1, b);
			}
			if (h < 9) {
				put(x + 1 + lean, y + h, z - 1, b);
				if (h < 5) {
					put(x + 1 + lean, y + h, z, b);
				}
			}
		}
		put(x, y - 1, z, "sculk");
		put(x, y - 1, z + 1, "sculk");
		put(x, y - 1, z - 1, "sculk");
		put(x, y, z, "sculk_shrieker");
		put(x, y, z + 2, "black_candle[candles=3,lit=true]");
		put(x, y, z - 2, "black_candle[candles=2,lit=true]");
	}
	// ---- new S set pieces (phase 2) end ----

	/** S_STARFALL: a ring hung over the road where the skyway sprint has its arch. */
	private void halo(double t, int surf) {
		for (double o = -(RaceTrack.ROAD_HALF + 2.0D); o <= RaceTrack.ROAD_HALF + 2.0D; o += 0.5D) {
			RacePoint q = track.pointAtLane(t, o);
			int x = floor(q.x()), z = floor(q.z());
			if (Math.abs(o) > RaceTrack.ROAD_HALF + 1.0D) {
				for (int h = 1; h <= 8; h++) {
					put(x, surf + h, z, "quartz_pillar[axis=y]");
				}
			}
			// the ring itself arcs from the pillar tops, clear of a mounted rider
			double rel = o / (RaceTrack.ROAD_HALF + 2.0D);
			int lift = (int) Math.round(Math.sqrt(Math.max(0.0D, 1.0D - rel * rel)) * 5.0D);
			put(x, surf + 8 + lift, z, Math.abs(o) < 2.0D ? "cyan_stained_glass" : "light_blue_stained_glass");
			if (Math.floorMod(floor(o), 4) == 0) {
				put(x, surf + 8, z, "end_rod");
			}
		}
	}

	/** An arch spanning the road at t: two pillars beside the kerbs and a beam seven blocks up with lights. */
	private void archOver(double t, int surf, String block, String light) {
		for (double o = -(RaceTrack.ROAD_HALF + 2.0D); o <= RaceTrack.ROAD_HALF + 2.0D; o += 0.5D) {
			RacePoint q = track.pointAtLane(t, o);
			int x = floor(q.x()), z = floor(q.z());
			put(x, surf + 8, z, block);
			if (Math.abs(o) > RaceTrack.ROAD_HALF + 1.0D) {
				for (int h = 1; h <= 7; h++) {
					put(x, surf + h, z, block);
				}
			} else if (Math.floorMod(floor(o), 3) == 0) {
				put(x, surf + 7, z, light);
			}
		}
	}

	/** One piece of theme decoration on the margin at (t, offset), on the ground level of the road there. */
	private void decorate(double t, double offset, int surf) {
		RacePoint q = track.pointAtLane(t, offset);
		int x = floor(q.x()), z = floor(q.z());
		if (stands.nearStand(x, z) || stands.nearStand(x + 2, z) || stands.nearStand(x - 2, z)
				|| stands.nearStand(x, z + 2) || stands.nearStand(x, z - 2)) {
			return;   // a set piece is up to five wide: keep it out of a stand on another leg
		}
		int y = surf + 1;
		int pick = (decoSeed++ * 7 + 3) % 5;
		switch (theme) {
			case MEADOW -> {
				switch (pick) {
					case 0, 1 -> tree(x, y, z, "oak_log", "oak_leaves[persistent=true]", 3);
					case 2 -> {
						put(x, y, z, "hay_block");
						put(x + 1, y, z, "hay_block");
						put(x, y + 1, z, "hay_block[axis=x]");
					}
					case 3 -> flowers(x, y, z, "poppy", "dandelion", "oxeye_daisy");
					default -> fenceRun(x, y, z, "oak_fence");
				}
			}
			case ORCHARD -> {
				switch (pick) {
					case 0, 1, 2 -> tree(x, y, z, "cherry_log", "cherry_leaves[persistent=true]", 4);
					case 3 -> {
						put(x, y, z, "pumpkin");
						put(x + 1, y, z + 1, "sweet_berry_bush[age=3]");
					}
					default -> put(x, y, z, "composter");
				}
			}
			case SHORE -> {
				switch (pick) {
					case 0, 1 -> {
						for (int h = 0; h < 5; h++) {
							put(x, y + h, z, "jungle_log");
						}
						cross(x, y + 5, z, "jungle_leaves[persistent=true]");
					}
					case 2 -> pool(x, y - 1, z, "water", "sand");
					case 3 -> put(x, y, z, "sea_pickle[pickles=3]");
					default -> put(x, y, z, "sandstone_wall");
				}
			}
			case CANYON -> {
				switch (pick) {
					case 0, 1 -> {
						put(x, y - 1, z, "red_sand");
						put(x, y, z, "cactus");
						put(x, y + 1, z, "cactus");
					}
					case 2 -> pillar(x, y, z, "orange_terracotta", "red_terracotta", "yellow_terracotta", 4);
					case 3 -> put(x, y, z, "dead_bush");
					default -> pillar(x, y, z, "terracotta", "brown_terracotta", "orange_terracotta", 6);
				}
			}
			case RIVER -> {
				switch (pick) {
					case 0, 1, 2 -> tree(x, y, z, "spruce_log", "spruce_leaves[persistent=true]", 4);
					case 3 -> {
						put(x, y, z, "mossy_cobblestone");
						put(x + 1, y, z, "cobblestone");
						put(x, y + 1, z, "mossy_cobblestone");
					}
					default -> put(x, y, z, "campfire[lit=true]");
				}
			}
			case SNOW -> {
				switch (pick) {
					case 0, 1 -> {
						tree(x, y, z, "spruce_log", "spruce_leaves[persistent=true]", 4);
						put(x, y + 7, z, "snow");
					}
					case 2 -> {
						cross(x, y - 1, z, "packed_ice");
						put(x, y - 1, z, "blue_ice");
					}
					case 3 -> put(x, y, z, "snow");
					default -> put(x, y, z, "spruce_fence");
				}
			}
			case CAVERN -> {
				switch (pick) {
					case 0 -> {
						put(x, y, z, "budding_amethyst");
						put(x, y + 1, z, "amethyst_cluster[facing=up]");
					}
					case 1, 2 -> {
						for (int h = 0; h < 4; h++) {
							put(x, y + h, z, "dripstone_block");
						}
						put(x, y + 4, z, "pointed_dripstone[vertical_direction=up]");
					}
					case 3 -> put(x, y, z, "glow_lichen[down=true]");
					default -> {
						put(x, y, z, "deepslate_bricks");
						put(x, y + 1, z, "sea_lantern");
					}
				}
			}
			case JUNGLE -> {
				switch (pick) {
					case 0, 1 -> tree(x, y, z, "jungle_log", "jungle_leaves[persistent=true]", 5);
					case 2 -> {
						for (int h = 0; h < 6; h++) {
							put(x, y + h, z, "bamboo_block");
						}
					}
					case 3 -> put(x, y, z, "melon");
					default -> {
						put(x, y, z, "mossy_cobblestone");
						put(x, y + 1, z, "fern");
					}
				}
			}
			case NETHER -> {
				switch (pick) {
					case 0, 1 -> pool(x, y - 1, z, "lava", "netherrack");
					case 2 -> {
						for (int h = 0; h < 4; h++) {
							put(x, y + h, z, "crimson_stem");
						}
						cross(x, y + 4, z, "nether_wart_block");
					}
					case 3 -> {
						put(x, y - 1, z, "soul_sand");
						put(x, y, z, "soul_fire");
					}
					default -> {
						put(x, y, z, "netherrack");
						put(x, y + 1, z, "fire");
					}
				}
			}
			case SKYWAY -> {
				String colour = RAINBOW[decoSeed % RAINBOW.length];
				for (int h = 0; h < 4; h++) {
					put(x, y + h, z, colour + "_stained_glass");
				}
				put(x, y + 4, z, "end_rod");
			}
			case KEEP -> {
				switch (pick) {
					case 0, 1 -> pool(x, y - 1, z, "lava", "blackstone");
					case 2 -> pillar(x, y, z, "polished_blackstone_bricks", "polished_blackstone_bricks", "polished_blackstone_wall", 5);
					case 3 -> {
						put(x, y - 1, z, "magma_block");
						put(x + 1, y - 1, z, "magma_block");
					}
					default -> {
						put(x, y, z, "chiseled_polished_blackstone");
						put(x, y + 1, z, "soul_lantern");
					}
				}
			}
			case END -> {
				switch (pick) {
					case 0, 1 -> {
						for (int h = 0; h < 6; h++) {
							put(x, y + h, z, "obsidian");
						}
						put(x, y + 6, z, "end_rod");
					}
					case 2 -> {
						put(x, y - 1, z, "end_stone");
						put(x, y, z, "chorus_plant");
						put(x, y + 1, z, "chorus_flower");
					}
					case 3 -> pillar(x, y, z, "purpur_pillar", "purpur_pillar", "purpur_block", 3);
					default -> put(x, y, z, "end_stone_brick_wall");
				}
			}
			case FARMLAND -> {
				switch (pick) {
					case 0 -> {   // a round of hay bales
						put(x, y, z, "hay_block");
						put(x + 1, y, z, "hay_block");
						put(x, y, z + 1, "hay_block");
						put(x, y + 1, z, "hay_block[axis=z]");
					}
					case 1 -> tree(x, y, z, "birch_log", "birch_leaves[persistent=true]", 4);
					case 2 -> {   // the pumpkin patch
						put(x, y, z, "pumpkin");
						put(x + 1, y, z + 1, "pumpkin");
						put(x - 1, y, z, "carved_pumpkin[facing=south]");
					}
					case 3 -> flowers(x, y, z, "cornflower", "dandelion", "oxeye_daisy");
					default -> fenceRun(x, y, z, "birch_fence");
				}
			}
			case SAVANNA -> {
				switch (pick) {
					case 0, 1 -> {   // an acacia: bent trunk, flat crown
						for (int h = 0; h < 4; h++) {
							put(x + (h >= 3 ? 1 : 0), y + h, z, "acacia_log");
						}
						for (int dx = -1; dx <= 3; dx++) {
							for (int dz = -2; dz <= 2; dz++) {
								if (Math.abs(dx - 1) + Math.abs(dz) <= 3) {
									put(x + dx, y + 4, z + dz, "acacia_leaves[persistent=true]");
								}
							}
						}
					}
					case 2 -> pillar(x, y, z, "orange_terracotta", "white_terracotta", "red_terracotta", 5);   // a mesa stack
					case 3 -> {   // a termite mound
						put(x, y, z, "packed_mud");
						put(x, y + 1, z, "packed_mud");
						put(x + 1, y, z, "packed_mud");
						put(x, y + 2, z, "mud_brick_wall");
					}
					default -> {
						put(x, y - 1, z, "coarse_dirt");
						put(x, y, z, "dead_bush");
					}
				}
			}
			case MUSHROOM -> {
				switch (pick) {
					case 0, 1 -> {   // a huge red mushroom
						for (int h = 0; h < 3; h++) {
							put(x, y + h, z, "mushroom_stem");
						}
						cross(x, y + 3, z, "red_mushroom_block");
						for (int dx = -1; dx <= 1; dx += 2) {
							for (int dz = -1; dz <= 1; dz += 2) {
								put(x + dx, y + 3, z + dz, "red_mushroom_block");
							}
						}
						put(x, y + 4, z, "red_mushroom_block");
					}
					case 2 -> {   // a huge brown mushroom, flat
						for (int h = 0; h < 4; h++) {
							put(x, y + h, z, "mushroom_stem");
						}
						for (int dx = -2; dx <= 2; dx++) {
							for (int dz = -2; dz <= 2; dz++) {
								if (Math.abs(dx) + Math.abs(dz) < 4) {
									put(x + dx, y + 4, z + dz, "brown_mushroom_block");
								}
							}
						}
					}
					case 3 -> {
						put(x, y, z, "tuff_bricks");
						put(x, y + 1, z, "pearlescent_froglight");
					}
					default -> {
						put(x, y, z, "mushroom_stem");
						put(x, y + 1, z, "brown_mushroom_block");
					}
				}
			}
			case DEEP_DARK -> {
				switch (pick) {
					case 0 -> {   // a sculk catalyst in a sculk patch
						cross(x, y - 1, z, "sculk");
						put(x, y, z, "sculk_catalyst");
					}
					case 1, 2 -> {   // an ancient city pillar with a soul lantern
						for (int h = 0; h < 4; h++) {
							put(x, y + h, z, h == 3 ? "chiseled_deepslate" : "deepslate_tiles");
						}
						put(x, y + 4, z, "soul_lantern[hanging=false]");
					}
					case 3 -> {
						put(x, y, z, "gray_wool");
						put(x + 1, y, z, "gray_carpet");
						put(x, y + 1, z, "black_candle[candles=3,lit=true]");
					}
					default -> {
						put(x, y, z, "cobbled_deepslate_wall");
						put(x, y + 1, z, "sculk_vein[down=true]");
					}
				}
			}
		}
	}

	private void tree(int x, int y, int z, String log, String leaves, int height) {
		for (int h = 0; h < height; h++) {
			put(x, y + h, z, log);
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				put(x + dx, y + height - 1, z + dz, leaves);
				put(x + dx, y + height, z + dz, leaves);
			}
		}
		put(x, y + height - 1, z, log);
		cross(x, y + height + 1, z, leaves);
		put(x, y + height + 1, z, leaves);
	}

	private void cross(int x, int y, int z, String block) {
		put(x, y, z, block);
		put(x + 1, y, z, block);
		put(x - 1, y, z, block);
		put(x, y, z + 1, block);
		put(x, y, z - 1, block);
	}

	private void flowers(int x, int y, int z, String a, String b, String c) {
		put(x, y, z, a);
		put(x + 1, y, z, b);
		put(x, y, z + 1, c);
		put(x - 1, y, z, b);
	}

	private void fenceRun(int x, int y, int z, String fence) {
		for (int d = -1; d <= 1; d++) {
			put(x + d, y, z, fence);
		}
	}

	private void pillar(int x, int y, int z, String a, String b, String top, int height) {
		for (int h = 0; h < height; h++) {
			put(x, y + h, z, (h & 1) == 0 ? a : b);
		}
		put(x, y + height, z, top);
	}

	/** A walled 3x3 pool sunk into the ground (rim on the ground level). */
	private void pool(int x, int y, int z, String liquid, String rim) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				put(x + dx, y - 1, z + dz, rim);
				put(x + dx, y, z + dz, edge ? rim : liquid);
			}
		}
	}

	// ------------------------------------------------------------- helpers

	private static int taper(double o, double lo, double hi) {
		double edge = Math.min(o - lo, hi - o);
		if (edge < 1.0D) {
			return 1;
		}
		if (edge < 2.5D) {
			return 2;
		}
		if (edge < 4.0D) {
			return 3;
		}
		return 5;
	}

	private static int floor(double v) {
		return (int) Math.floor(v);
	}

	private void put(int x, int y, int z, String block) {
		if (sparingRoad && road.contains(new Tile(x, z))) {
			return;   // a fold of the course put the racing line here: leave it alone
		}
		if (standing) {
			standTiles.add(new Tile(x, z));
		}
		if (landmarking) {
			landmarkCells.put(new Cell(x, y, z), block);
		}
		blocks.put(new Cell(x, y, z), block);
	}

	/** Every block the course's set piece laid (plinth included). */
	Set<String> landmarkBlocks() {
		return new HashSet<>(landmarkCells.values());
	}

	/**
	 * The set piece as built, moved to the origin ("dx,dy,dz=block" per cell): two courses
	 * with the same set piece have the same shape here wherever they stand.
	 */
	Set<String> landmarkShape() {
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
		for (Cell c : landmarkCells.keySet()) {
			x0 = Math.min(x0, c.x());
			y0 = Math.min(y0, c.y());
			z0 = Math.min(z0, c.z());
		}
		Set<String> out = new HashSet<>();
		for (Map.Entry<Cell, String> e : landmarkCells.entrySet()) {
			Cell c = e.getKey();
			out.add((c.x() - x0) + "," + (c.y() - y0) + "," + (c.z() - z0) + "=" + e.getValue());
		}
		return out;
	}

	public static long chunkKey(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	public Map<Cell, String> blocks() {
		return Collections.unmodifiableMap(blocks);
	}

	public Set<Tile> road() {
		return Collections.unmodifiableSet(road);
	}

	/**
	 * Chunks to clear before this island is laid again: its own, plus a ring for what an
	 * older, wider plan left at the edges. A relay only overwrites the plan's own cells,
	 * so an old pool or spring that the new plan does not cover would stay, its rim now
	 * perhaps road, and flood the course (and wash the boost pads out) the first time
	 * something touched it. Nothing near another island or the village is in the set.
	 */
	public Set<Long> clearChunks() {
		Set<Long> out = new HashSet<>();
		// the whole box of the island, infield included, and a ring round it: a course whose
		// lap grew (the 48-course swap lengthened grands prix) was laid smaller about the same
		// centre, so the old road can sit in what is now the infield
		int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		for (long key : chunks) {
			int cx = (int) (key >> 32), cz = (int) key;
			x0 = Math.min(x0, cx);
			x1 = Math.max(x1, cx);
			z0 = Math.min(z0, cz);
			z1 = Math.max(z1, cz);
		}
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				if (!nearVillage(x, z) && !nearOtherIsland(x, z)) {
					out.add(chunkKey(x, z));
				}
			}
		}
		return out;
	}

	/** Lowest and highest y the plan uses. */
	public int minY() {
		return blocks.keySet().stream().mapToInt(Cell::y).min().orElse(0);
	}

	public int maxY() {
		return blocks.keySet().stream().mapToInt(Cell::y).max().orElse(0);
	}

	/** Within a chunk of the footprint {@code SquareBuilder.buildPaddock} scrubs (village and shrine islet). */
	static boolean nearVillage(int cx, int cz) {
		int r = VillageLayout.RADIUS + 12;
		return inChunks(cx, cz, VillageLayout.CX - r, VillageLayout.CZ - r, VillageLayout.CX + r, VillageLayout.CZ + r + 30)
				|| inChunks(cx, cz, 44, -72, 66, -48);
	}

	/** Within a chunk of another course's island (its half extents plus a 16-block pad). */
	private boolean nearOtherIsland(int cx, int cz) {
		for (RaceTrack other : RaceTrack.values()) {
			if (other == track) {
				continue;
			}
			double rx = other.getRadiusX() + 16.0D, rz = other.getRadiusZ() + 16.0D;
			if (inChunks(cx, cz, floor(other.centerX() - rx), floor(other.centerZ() - rz),
					floor(other.centerX() + rx), floor(other.centerZ() + rz))) {
				return true;
			}
		}
		return false;
	}

	/** Chunk (cx, cz) touches the block box, grown by one chunk each way. */
	private static boolean inChunks(int cx, int cz, int x0, int z0, int x1, int z1) {
		return cx >= (x0 >> 4) - 1 && cx <= (x1 >> 4) + 1 && cz >= (z0 >> 4) - 1 && cz <= (z1 >> 4) + 1;
	}

	/** Chunk keys ({@link #chunkKey}) the course touches, for force-loading during a heat. */
	public Set<Long> chunks() {
		return Collections.unmodifiableSet(chunks);
	}

	/** Where the crowd stands, every stand of the course (see {@link CourseStands}). */
	public List<FanPost> fanPosts() {
		return stands.fanPosts();
	}

	/** The course's stands (boxes, light probes, fan ranges). */
	public List<CourseStands.Stand> stands() {
		return stands.stands();
	}

	/** Columns holding a stand block, for tests. */
	Set<Tile> standTiles() {
		return Collections.unmodifiableSet(standTiles);
	}

	/** Marshal-tower sign that names the course, facing the grid. */
	public BoardPost courseBoard() {
		return board;
	}

	public boolean onCourse(double x, double z) {
		if (!Double.isFinite(x) || !Double.isFinite(z)) {
			return false;
		}
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (Arrays.binarySearch(roadKeys, roadKey(bx + dx, bz + dz)) >= 0) {
					return true;
				}
			}
		}
		return false;
	}
}
