package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;
import java.util.List;

/**
 * A closed centre line through control points: Catmull-Rom in x/z with a height
 * profile, resampled to one sample per block of arc length. Everything the
 * courses need is a lookup on the samples: a point and lane offset at parameter
 * t (0..1 per lap), the nearest parameter to a position, curvature, extents.
 * <p>
 * Winding is normalised so a positive lane offset is always the inside of the
 * loop (the infield), matching the old ovals; t = 0 is the second control point
 * and the first three must be collinear (the start line and grid sit on them).
 */
public final class TrackSpline {
	public record Ctl(double x, double z, double y) {
	}

	private static final int DENSE = 24;          // spline evaluations per control segment
	private static final double MAX_SLOPE = 0.34; // blocks of rise per block along (a step every 3 blocks)

	private final double[] xs, zs, ys;
	private final int n;
	private final double length;
	private final double minX, maxX, minZ, maxZ;

	/**
	 * @param ctl       control points in course units (x, z) and blocks (y)
	 * @param scale     multiplies x and z so the lap comes out at the wanted length
	 * @param flat      parameter ranges [start, end] whose height is held at the range's first sample
	 */
	public TrackSpline(List<Ctl> ctl, double scale, List<double[]> flat) {
		List<Ctl> pts = orient(ctl);
		int m = pts.size();
		// dense evaluation with cumulative arc length
		List<double[]> dense = new ArrayList<>();
		for (int i = 0; i < m; i++) {
			Ctl p0 = pts.get((i - 1 + m) % m), p1 = pts.get(i), p2 = pts.get((i + 1) % m), p3 = pts.get((i + 2) % m);
			for (int k = 0; k < DENSE; k++) {
				double u = k / (double) DENSE;
				dense.add(new double[]{
						catmull(p0.x, p1.x, p2.x, p3.x, u) * scale,
						catmull(p0.z, p1.z, p2.z, p3.z, u) * scale,
						catmull(p0.y, p1.y, p2.y, p3.y, u)});
			}
		}
		int d = dense.size();
		double[] cum = new double[d + 1];
		for (int i = 0; i < d; i++) {
			double[] a = dense.get(i), b = dense.get((i + 1) % d);
			cum[i + 1] = cum[i] + Math.hypot(b[0] - a[0], b[1] - a[1]);
		}
		this.length = cum[d];
		this.n = Math.max(16, (int) Math.round(length));
		xs = new double[n];
		zs = new double[n];
		ys = new double[n];
		// t = 0 sits at the second control point: the first straight runs from there,
		// so the start line and the grid are on dead-straight road
		double s0 = cum[Math.min(DENSE, d - 1)];
		int j = 0;
		for (int i = 0; i < n; i++) {
			double s = s0 + length * i / n;
			if (s >= length) {
				s -= length;
				if (j > 0 && cum[j] > s) {
					j = 0;
				}
			}
			while (j < d - 1 && cum[j + 1] < s) {
				j++;
			}
			double[] a = dense.get(j), b = dense.get((j + 1) % d);
			double seg = cum[j + 1] - cum[j];
			double u = seg <= 1.0E-9 ? 0.0 : (s - cum[j]) / seg;
			xs[i] = a[0] + (b[0] - a[0]) * u;
			zs[i] = a[1] + (b[1] - a[1]) * u;
			ys[i] = a[2] + (b[2] - a[2]) * u;
		}
		// flat ranges (water, ridges, bogs, boost strips sit on level ground)
		boolean[] fixed = new boolean[n];
		for (double[] range : flat) {
			int s = index(range[0] - 0.01), e = index(range[1] + 0.01);
			double level = ys[index(range[0])];
			for (int i = s; ; i = (i + 1) % n) {
				ys[i] = level;
				fixed[i] = true;
				if (i == e) {
					break;
				}
			}
		}
		smooth(fixed);
		// the lowest road sits exactly on the base level
		double floor = Double.MAX_VALUE;
		for (double y : ys) {
			floor = Math.min(floor, y);
		}
		for (int i = 0; i < n; i++) {
			ys[i] -= floor;
		}
		double mnx = Double.MAX_VALUE, mxx = -Double.MAX_VALUE, mnz = Double.MAX_VALUE, mxz = -Double.MAX_VALUE;
		for (int i = 0; i < n; i++) {
			mnx = Math.min(mnx, xs[i]);
			mxx = Math.max(mxx, xs[i]);
			mnz = Math.min(mnz, zs[i]);
			mxz = Math.max(mxz, zs[i]);
		}
		minX = mnx;
		maxX = mxx;
		minZ = mnz;
		maxZ = mxz;
	}

	/** Reverse the loop (keeping the first point first) so positive lane offsets point inward. */
	private static List<Ctl> orient(List<Ctl> ctl) {
		double area = 0.0;
		for (int i = 0; i < ctl.size(); i++) {
			Ctl a = ctl.get(i), b = ctl.get((i + 1) % ctl.size());
			area += a.x * b.z - b.x * a.z;
		}
		if (area > 0.0) {
			return ctl;
		}
		// reversed as [p2, p1, p0, p(m-1), ...]: the second point is still p1 on the start straight
		List<Ctl> out = new ArrayList<>();
		int m = ctl.size();
		for (int k = 0; k < m; k++) {
			out.add(ctl.get(((2 - k) % m + m) % m));
		}
		return out;
	}

	private static double catmull(double p0, double p1, double p2, double p3, double t) {
		double t2 = t * t, t3 = t2 * t;
		return 0.5 * ((2.0 * p1) + (-p0 + p2) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
	}

	/** Moving average of the height outside the fixed ranges, then a slope clamp so every rise is walkable. */
	private void smooth(boolean[] fixed) {
		double[] avg = ys.clone();
		for (int i = 0; i < n; i++) {
			if (fixed[i]) {
				continue;
			}
			double sum = 0.0;
			for (int k = -6; k <= 6; k++) {
				sum += ys[(i + k + n) % n];
			}
			avg[i] = sum / 13.0;
		}
		System.arraycopy(avg, 0, ys, 0, n);
		for (int pass = 0; pass < 4; pass++) {
			for (int i = 1; i <= n; i++) {
				clampStep((i - 1) % n, i % n, fixed);
			}
			for (int i = n; i >= 1; i--) {
				clampStep(i % n, (i - 1) % n, fixed);
			}
		}
	}

	private void clampStep(int from, int to, boolean[] fixed) {
		if (fixed[to]) {
			return;
		}
		double lo = ys[from] - MAX_SLOPE, hi = ys[from] + MAX_SLOPE;
		ys[to] = Math.max(lo, Math.min(hi, ys[to]));
	}

	public int samples() {
		return n;
	}

	public double length() {
		return length;
	}

	public double minX() {
		return minX;
	}

	public double maxX() {
		return maxX;
	}

	public double minZ() {
		return minZ;
	}

	public double maxZ() {
		return maxZ;
	}

	private int index(double t) {
		double w = t - Math.floor(t);
		return ((int) Math.floor(w * n)) % n;
	}

	/** Centre line at t, interpolated between samples (y = ground height, fractional). */
	public double[] at(double t) {
		double w = t - Math.floor(t);
		double f = w * n;
		int i = (int) Math.floor(f) % n, k = (i + 1) % n;
		double u = f - Math.floor(f);
		return new double[]{xs[i] + (xs[k] - xs[i]) * u, zs[i] + (zs[k] - zs[i]) * u, ys[i] + (ys[k] - ys[i]) * u};
	}

	/** Unit tangent at t (direction of travel). */
	public double[] tangent(double t) {
		int i = index(t);
		double tx = xs[(i + 1) % n] - xs[(i - 1 + n) % n];
		double tz = zs[(i + 1) % n] - zs[(i - 1 + n) % n];
		double len = Math.hypot(tx, tz);
		return len < 1.0E-9 ? new double[]{1.0, 0.0} : new double[]{tx / len, tz / len};
	}

	/** Point at lane offset: positive = inside the loop. */
	public double[] atLane(double t, double offset) {
		double[] p = at(t);
		double[] tg = tangent(t);
		double px = -tg[1], pz = tg[0];
		return new double[]{p[0] + px * offset, p[1] + pz * offset, p[2]};
	}

	/** Absolute heading change (radians) over the {@code span} blocks ahead of t; 0 on a straight. */
	public double turn(double t, double span) {
		double[] a = tangent(t), b = tangent(t + span / length);
		double dot = Math.max(-1.0, Math.min(1.0, a[0] * b[0] + a[1] * b[1]));
		return Math.acos(dot);
	}

	/** Signed turn: positive when the course bends toward the inside (a left / right decided by winding). */
	public double signedTurn(double t, double span) {
		double[] a = tangent(t), b = tangent(t + span / length);
		double cross = a[0] * b[1] - a[1] * b[0];
		return Math.copySign(turn(t, span), cross);
	}

	/** Samples either side of a hint. A fast bird stays inside this across a lagged tick. */
	private static final int HINT_WINDOW = 48;
	/**
	 * A local sample farther than this is the wrong leg: courses keep other legs at
	 * least 30 blocks apart, and a detour sits about 16 off the line.
	 */
	private static final double HINT_ACCEPT2 = 26.0D * 26.0D;

	/** Parameter of the nearest sample to (x, z). */
	public double nearest(double x, double z) {
		return nearestFrom(x, z, -1.0D);
	}

	/**
	 * Nearest sample, starting near {@code hint} (0..1) when that section is the one
	 * under the point. A hint below 0 scans the lap. A bird on a hairpin stays on the
	 * local leg; a hint from the far side of the course falls back to a full scan.
	 */
	public double nearestFrom(double x, double z, double hint) {
		if (hint >= 0.0D && hint < 1.0D && n > 0) {
			int center = Math.floorMod((int) Math.round(hint * n), n);
			int best = bestWindow(x, z, center, HINT_WINDOW);
			double dx = xs[best] - x, dz = zs[best] - z;
			if (dx * dx + dz * dz <= HINT_ACCEPT2) {
				return best / (double) n;
			}
		}
		return bestFull(x, z) / (double) n;
	}

	/**
	 * Like {@link #nearest}, refined onto the line between the nearest sample and its
	 * better neighbour, so it moves smoothly instead of a block at a time (0..1).
	 */
	public double nearestFine(double x, double z) {
		int i = (int) Math.round(nearest(x, z) * n) % n;
		return refine(x, z, i);
	}

	/** {@link #nearestFine} around a coarse parameter already known, with no second scan. */
	public double nearestFineFrom(double x, double z, double coarse) {
		if (!(coarse >= 0.0D) || n <= 0) {
			return nearestFine(x, z);
		}
		int i = Math.floorMod((int) Math.round(coarse * n), n);
		return refine(x, z, i);
	}

	private int bestFull(double x, double z) {
		int best = 0;
		double bd = Double.MAX_VALUE;
		for (int i = 0; i < n; i++) {
			double dx = xs[i] - x, dz = zs[i] - z;
			double d = dx * dx + dz * dz;
			if (d < bd) {
				bd = d;
				best = i;
			}
		}
		return best;
	}

	/** Lowest index wins a tie, the same rule as {@link #bestFull}. */
	private int bestWindow(double x, double z, int center, int radius) {
		int best = center;
		double bd = Double.MAX_VALUE;
		for (int k = -radius; k <= radius; k++) {
			int i = Math.floorMod(center + k, n);
			double dx = xs[i] - x, dz = zs[i] - z;
			double d = dx * dx + dz * dz;
			if (d < bd || (d == bd && i < best)) {
				bd = d;
				best = i;
			}
		}
		return best;
	}

	private double refine(double x, double z, int i) {
		double best = i, bd = Double.MAX_VALUE;
		for (int dir = -1; dir <= 1; dir += 2) {
			int j = (i + dir + n) % n;
			double sx = xs[j] - xs[i], sz = zs[j] - zs[i];
			double len2 = sx * sx + sz * sz;
			double s = len2 < 1.0E-12 ? 0.0 : Math.max(0.0, Math.min(1.0, ((x - xs[i]) * sx + (z - zs[i]) * sz) / len2));
			double dx = xs[i] + sx * s - x, dz = zs[i] + sz * s - z;
			double d = dx * dx + dz * dz;
			if (d < bd) {
				bd = d;
				best = i + dir * s;
			}
		}
		double t = best / n;
		return t - Math.floor(t);
	}

	/** Shortest distance between two samples that are at least {@code minGap} blocks apart along the line. */
	public double closestLegs(double minGap) {
		return closestLegsAt(minGap)[0];
	}

	/** {distance, t of one sample, t of the other} for the closest pair of far-apart samples. */
	public double[] closestLegsAt(double minGap) {
		double best = Double.MAX_VALUE;
		int bi = 0, bj = 0;
		int gap = (int) minGap;
		for (int i = 0; i < n; i += 2) {
			for (int j = i + gap; j < n && j - i <= n - gap; j += 2) {
				double dx = xs[i] - xs[j], dz = zs[i] - zs[j];
				double d = Math.hypot(dx, dz);
				if (d < best) {
					best = d;
					bi = i;
					bj = j;
				}
			}
		}
		return new double[]{best, bi / (double) n, bj / (double) n};
	}

	public double heightAt(int i) {
		return ys[((i % n) + n) % n];
	}
}
