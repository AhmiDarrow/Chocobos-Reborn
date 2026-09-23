package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;
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
 * lamps on both verges, warning posts before terrain, and the grandstand in the
 * infield beside the start straight with {@link #fanPosts()} for the crowd.</li>
 * </ul>
 */
public final class RaceCourseLayout {
	public record Cell(int x, int y, int z) {
	}

	public record Tile(int x, int z) {
	}

	/** Where a fan stands (feet) and the yaw that faces the track. */
	public record FanPost(double x, double y, double z, float yaw) {
	}

	/** Course-name wall sign on the marshal's tower, facing the grid. */
	public record BoardPost(int x, int y, int z, String facing) {
	}

	private static final Map<RaceTrack, RaceCourseLayout> CACHE = new EnumMap<>(RaceTrack.class);
	private static final double CONNECT = 0.012D;      // t-length of a detour connector
	private static final double MARGIN = 10.0D;        // ground either side of the kerbs
	/** Lane step of the ground stamp: finer than a block so the island has no gaps. */
	private static final double LANE_STEP = 0.25D;
	private static final String BOOST = "chocobosreborn:boost_pad";
	private static final String[] RAINBOW = {"red", "orange", "yellow", "lime", "light_blue", "blue", "purple"};

	private final RaceTrack track;
	private final RaceTrack.Theme theme;
	private final Map<Cell, String> blocks = new LinkedHashMap<>();
	private final Set<Tile> road = new HashSet<>();
	private final Set<Long> chunks = new HashSet<>();
	private final List<FanPost> fans = new ArrayList<>();
	private BoardPost board;
	private int decoSeed;
	/** Set while stamping scenery: {@link #put} then refuses to touch a road tile. */
	private boolean sparingRoad;

	public static synchronized RaceCourseLayout of(RaceTrack track) {
		return CACHE.computeIfAbsent(track, RaceCourseLayout::new);
	}

	private RaceCourseLayout(RaceTrack track) {
		this.track = track;
		this.theme = track.theme();
		double lap = track.lapLength();
		int steps = (int) Math.ceil(lap * 2.0D);   // 0.5 blocks between stamps
		boolean sky = theme == RaceTrack.Theme.SKYWAY;
		double standFrom = 6.0D / lap, standTo = Math.min(50.0D, lap * 0.06D) / lap;
		// Three passes over the lap, not one: a circuit folds back on itself on a tight
		// corner, so a section stamped later lands on block columns an earlier one already
		// used. Laid in one pass, a margin, a tree or a lava pool overwrites road that is
		// already down — grass and cacti on the racing line, lava on a lap of the course
		// nowhere near its own feature. In layers, the road always wins.
		// Each pass stamps twice per step: at 0.5 blocks a tight corner skips whole block
		// columns and leaves a hole through the island that a racer drops into.
		for (int i = 0; i < steps; i++) {
			double t = i / (double) steps;
			stampGround(t, lap, sky, standFrom, standTo);
			stampGround(t + 0.5D / steps, lap, sky, standFrom, standTo);
		}
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
			boolean stand = stand(t, standFrom, standTo);
			boolean corner = corner(t, lap);
			int surf = surf(t);
			// decoration in the margins: set pieces every 8 blocks alternating sides, small
			// ground details between them, flag lines along the straights
			if (!sky && !nearFeature && !startZone && i % 16 == 0) {
				decorate(t, -(RaceTrack.ROAD_HALF + 5.5D), surf);
			}
			if (!nearFeature && !stand && !startZone && i % 16 == 8) {
				decorate(t, RaceTrack.ROAD_HALF + 5.5D, surf);
			}
			if (!sky && !nearFeature && !startZone && i % 6 == 3) {
				verge(t, (i % 12 == 3 ? -1 : 1) * (RaceTrack.ROAD_HALF + 2.5D + (i % 5)), surf);
			}
			if (!nearFeature && !startZone && !stand && !corner && i % 24 == 12) {
				flag(t, -(RaceTrack.ROAD_HALF + 2.0D), surf, i / 24);
				if (!sky) {
					flag(t, RaceTrack.ROAD_HALF + 2.0D, surf, i / 24 + 1);
				}
			}
			if (sky && !nearFeature && !startZone && i % 40 == 0) {
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
		grandstand(lap, standFrom, standTo);
		landmark();
		plugHoles();
		for (Cell c : blocks.keySet()) {
			chunks.add(chunkKey(c.x() >> 4, c.z() >> 4));
		}
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
	private void stampGround(double t, double lap, boolean sky, double standFrom, double standTo) {
		boolean nearFeature = nearFeature(t);
		int surf = surf(t);
		boolean stand = stand(t, standFrom, standTo);
		double margin = sky ? 1.5D : MARGIN;
		double outer = nearFeature ? RaceTrack.DETOUR_OUTER + 3.0D : RaceTrack.ROAD_HALF + 1.0D + margin;
		double inner = stand ? RaceTrack.ROAD_HALF + 22.0D : RaceTrack.ROAD_HALF + 1.0D + margin;
		// island rock under everything, ground on top of the margins
		for (double o = -outer; o <= inner; o += LANE_STEP) {
			RacePoint q = track.pointAtLane(t, o);
			int x = floor(q.x()), z = floor(q.z());
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
		for (RaceTrack.Feature f : track.terrainFeatures()) {
			if (t >= f.start() - CONNECT && t <= f.end() + CONNECT) {
				return true;
			}
		}
		return false;
	}

	/** The grid, the line and the run-off behind it: no decoration here. */
	private static boolean startZone(double t) {
		return t < 0.05D || t > 0.985D;
	}

	/** Alongside the grandstand: the infield is the stand, not margin. */
	private static boolean stand(double t, double standFrom, double standTo) {
		return t >= standFrom && t <= standTo;
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
		double standFrom = 6.0D / lap, standTo = Math.min(50.0D, lap * 0.06D) / lap;
		int lamps = (int) (lap / 40.0D);
		for (int i = 0; i < lamps; i++) {
			double t = i / (double) lamps;
			if (track.terrainAt(t) != null || t < 0.05D) {
				continue;
			}
			boolean stand = t >= standFrom && t <= standTo;
			int y = (int) track.groundY(t) - 1;
			for (int side : new int[]{-1, 1}) {
				if (side > 0 && stand) {
					continue;
				}
				RacePoint q = track.pointAtLane(t, side * (RaceTrack.ROAD_HALF + 2.5D));
				int x = floor(q.x()), z = floor(q.z());
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
	 * Tiered seats in the infield along the start straight: four rows rising
	 * inward, a roofed back wall with banners, lamps, and eight fan posts.
	 */
	private void grandstand(double lap, double from, double to) {
		int y = (int) track.groundY(0.02D) - 1;
		double[] tg = track.tangent(0.02D);
		// seats face the track: toward decreasing offset, i.e. the outward normal
		double nx = tg[1], nz = -tg[0];   // (+normal points inward; outward = -(-tz, tx))
		String facing = Math.abs(nx) > Math.abs(nz) ? (nx > 0 ? "east" : "west") : (nz > 0 ? "south" : "north");
		float yaw = (float) Math.toDegrees(Math.atan2(-nx, nz));
		double base = RaceTrack.ROAD_HALF + 4.5D;   // first row's offset
		int steps = (int) Math.ceil((to - from) * lap * 2.0D);
		for (int k = 0; k < 4; k++) {
			for (int s = 0; s <= steps; s++) {
				double t = from + (to - from) * s / steps;
				for (double o = base + k * 2.0D; o < base + k * 2.0D + 2.0D; o += 0.5D) {
					RacePoint q = track.pointAtLane(t, o);
					int x = floor(q.x()), z = floor(q.z());
					for (int h = 0; h <= k; h++) {
						put(x, y + h, z, theme.wall);
					}
					boolean seatRow = o < base + k * 2.0D + 1.0D;
					put(x, y + k + 1, z, seatRow ? stairs(facing) : ((s / 3 + k) % 2 == 0 ? "yellow_concrete" : "red_concrete"));
				}
			}
		}
		// back wall, roof and lamps
		for (int s = 0; s <= steps; s++) {
			double t = from + (to - from) * s / steps;
			RacePoint back = track.pointAtLane(t, base + 8.5D);
			int bx = floor(back.x()), bz = floor(back.z());
			for (int h = 0; h <= 7; h++) {
				put(bx, y + h, bz, h == 5 && (s / 6) % 2 == 0 ? theme.kerbB : theme.wall);
			}
			for (double o = base - 0.5D; o <= base + 8.5D; o += 0.5D) {
				RacePoint q = track.pointAtLane(t, o);
				put(floor(q.x()), y + 8, floor(q.z()), (s / 4) % 2 == 0 ? "yellow_wool" : "white_wool");   // striped awning
			}
			if (s % 8 == 0) {
				RacePoint front = track.pointAtLane(t, base - 0.5D);
				for (int h = 0; h <= 7; h++) {
					put(floor(front.x()), y + h, floor(front.z()), h == 3 ? theme.lamp : theme.post);
				}
			}
			if (s % 8 == 4) {
				put(bx, y + 6, bz, FLAG_COLOURS[Math.floorMod(s / 8, FLAG_COLOURS.length)] + "_banner[rotation=8]");
			}
		}
		// fan posts: two per row, spread along the stand, standing on the seats
		for (int k = 0; k < 4; k++) {
			for (int j = 0; j < 2; j++) {
				double t = from + (to - from) * (0.2D + 0.6D * j + 0.1D * k) / 1.3D;
				RacePoint q = track.pointAtLane(t, base + k * 2.0D + 0.5D);
				fans.add(new FanPost(Math.floor(q.x()) + 0.5D, y + k + 1.5D, Math.floor(q.z()) + 0.5D, yaw));
			}
		}
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
		String colour = FLAG_COLOURS[Math.floorMod(n, FLAG_COLOURS.length)];
		put(x, surf + 1, z, theme.post);
		put(x, surf + 2, z, theme.post);
		put(x, surf + 3, z, colour + "_banner[rotation=" + (Math.floorMod(n, 2) == 0 ? 4 : 12) + "]");
	}

	/** Small ground detail in the theme's margin: grass, flowers, snow, petals, lichen, fungi... */
	private void verge(double t, double offset, int surf) {
		RacePoint q = track.pointAtLane(t, offset);
		int x = floor(q.x()), z = floor(q.z());
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

	/** A themed monument at the far side of the circuit (t = 0.5), beside the road, and an arch over it for a few themes. */
	private void landmark() {
		double t = 0.5D;
		while (track.terrainAt(t) != null || track.terrainAt(t + 0.02D) != null || track.terrainAt(t - 0.02D) != null) {
			t += 0.03D;
		}
		int surf = (int) track.groundY(t) - 1;
		// the two courses that share a theme get different set pieces, so no two of the
		// twenty-four look the same from the saddle
		if (theme == RaceTrack.Theme.SKYWAY) {
			if (track.isShort()) {
				archOver(t, surf, "magenta_stained_glass", "end_rod");
			} else {
				halo(t, surf);
			}
			return;
		}
		if (theme == RaceTrack.Theme.KEEP && track.isShort()) {
			archOver(t, surf, "polished_blackstone_bricks", "soul_lantern[hanging=true]");
			return;
		}
		sparingRoad = true;   // a fold of the course must not end up wearing the landmark
		RacePoint q = track.pointAtLane(t, -(RaceTrack.ROAD_HALF + 6.0D));
		int x = floor(q.x()), z = floor(q.z());
		int y = surf + 1;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int d = 0; d <= 4; d++) {
					put(x + dx, surf - d, z + dz, theme.base);
				}
				put(x + dx, surf, z + dz, theme.wall);
			}
		}
		if (!track.isShort()) {
			grandPrixLandmark(x, y, z);
			sparingRoad = false;
			return;
		}
		switch (theme) {
			case MEADOW -> tree(x, y, z, "oak_log", "oak_leaves[persistent=true]", 7);
			case ORCHARD -> tree(x, y, z, "cherry_log", "cherry_leaves[persistent=true]", 8);
			case SHORE -> {   // a lighthouse
				for (int h = 0; h < 12; h++) {
					put(x, y + h, z, (h / 2) % 2 == 0 ? "white_concrete" : "red_concrete");
					put(x + 1, y + h, z, (h / 2) % 2 == 0 ? "white_concrete" : "red_concrete");
					put(x, y + h, z + 1, (h / 2) % 2 == 0 ? "white_concrete" : "red_concrete");
					put(x + 1, y + h, z + 1, (h / 2) % 2 == 0 ? "white_concrete" : "red_concrete");
				}
				for (int dx = 0; dx <= 1; dx++) {
					for (int dz = 0; dz <= 1; dz++) {
						put(x + dx, y + 12, z + dz, "sea_lantern");
						put(x + dx, y + 13, z + dz, "red_concrete");
					}
				}
			}
			case CANYON -> {   // a hoodoo
				pillar(x, y, z, "orange_terracotta", "red_terracotta", "terracotta", 9);
				pillar(x + 1, y, z, "yellow_terracotta", "orange_terracotta", "brown_terracotta", 7);
				pillar(x, y, z + 1, "red_terracotta", "brown_terracotta", "terracotta", 8);
				pillar(x + 1, y, z + 1, "orange_terracotta", "yellow_terracotta", "terracotta", 6);
				put(x, y + 10, z, "terracotta");
				put(x + 1, y + 10, z, "terracotta");
			}
			case RIVER -> {   // a mossy cairn with a spring in a basin on top
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
			case SNOW -> {   // an ice spire
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
			case CAVERN -> {   // a geode
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
			case JUNGLE -> {   // a mossy step pyramid
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
			case NETHER -> {   // a fortress tower with a lava fall
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
			case END -> {
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
			default -> {
			}
		}
		sparingRoad = false;
	}

	/** The set piece that belongs to a grand prix course, one per theme, never the sprint's. */
	private void grandPrixLandmark(int x, int y, int z) {
		switch (theme) {
			case MEADOW -> windmill(x, y, z);
			case ORCHARD -> ciderBarn(x, y, z);
			case SHORE -> shipwreck(x, y, z);
			case CANYON -> mesaArch(x, y, z);
			case RIVER -> millWheel(x, y, z);
			case SNOW -> frozenFall(x, y, z);
			case CAVERN -> dripstoneHall(x, y, z);
			case JUNGLE -> idol(x, y, z);
			case NETHER -> boneArch(x, y, z);
			case KEEP -> gatehouse(x, y, z);
			case END -> crystalCage(x, y, z);
			default -> {
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
		blocks.put(new Cell(x, y, z), block);
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
		for (long key : chunks) {
			int cx = (int) (key >> 32), cz = (int) key;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int x = cx + dx, z = cz + dz;
					if (!nearVillage(x, z) && !nearOtherIsland(x, z)) {
						out.add(chunkKey(x, z));
					}
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

	/** Where the crowd stands in the infield grandstand. */
	public List<FanPost> fanPosts() {
		return Collections.unmodifiableList(fans);
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
				if (road.contains(new Tile(bx + dx, bz + dz))) {
					return true;
				}
			}
		}
		return false;
	}
}
