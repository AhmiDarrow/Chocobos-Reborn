package tk.darrow.chocobosreborn.net;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * One remote bird's recent server frames, and where it stood at any server tick between them.
 *
 * <p>Between two frames the bird is interpolated (position linearly, yaw the short way round).
 * Past the newest frame it is carried on at its last velocity for up to {@link #MAX_EXTRAPOLATE}
 * ticks and then held, so a late frame shows as the bird easing, never as it jumping ahead and
 * back. Two frames further apart than {@link #TELEPORT} blocks are a teleport (a set-back, the
 * move to a course): the bird is shown at the first until the second's tick, then at the
 * second, never swept across the gap.
 *
 * <p>Pure arithmetic, no Minecraft types: unit-tested in {@code FrameBufferTest}.
 */
public final class FrameBuffer {
	public static final int CAPACITY = 64;
	public static final double MAX_EXTRAPOLATE = 3.0D;
	public static final double TELEPORT = 16.0D;
	/** Frames older than this many ticks behind the shown tick are dropped (one is always kept). */
	public static final double KEEP_BEHIND = 20.0D;
	/**
	 * A bird climbs a block edge in one tick (Minecraft's step-up); shown as it happens, that is a pop.
	 * A rise between two consecutive frames of more than {@link #STEP_MIN} and at most
	 * {@link #STEP_MAX} blocks is eased over {@link #STEP_EASE} ticks instead.
	 */
	public static final double STEP_MIN = 0.3D;
	public static final double STEP_MAX = 1.6D;
	public static final double STEP_EASE = 3.0D;

	public record Snap(int tick, double x, double y, double z, float yRot, float bodyRot, boolean onGround) {}

	private final ArrayDeque<Snap> snaps = new ArrayDeque<>();

	/** Frames arrive in order over TCP; a repeated or older tick is ignored. */
	public void add(Snap s) {
		if (!snaps.isEmpty() && s.tick() <= snaps.peekLast().tick()) {
			return;
		}
		snaps.addLast(s);
		while (snaps.size() > CAPACITY) {
			snaps.removeFirst();
		}
	}

	public boolean isEmpty() {
		return snaps.isEmpty();
	}

	public int newestTick() {
		return snaps.isEmpty() ? Integer.MIN_VALUE : snaps.peekLast().tick();
	}

	public int size() {
		return snaps.size();
	}

	/** Where the bird stood at server tick {@code t} (fractional). Null when no frame is held. */
	public Snap sample(double t) {
		if (snaps.isEmpty()) {
			return null;
		}
		trim(t);
		Snap first = snaps.peekFirst();
		if (t <= first.tick()) {
			return first;
		}
		Snap before = null;
		Snap prev = null;
		for (Iterator<Snap> it = snaps.iterator(); it.hasNext(); ) {
			Snap s = it.next();
			if (s.tick() > t) {
				Snap a = before;
				if (distance(a, s) > TELEPORT) {
					return a;
				}
				double f = (t - a.tick()) / (double) (s.tick() - a.tick());
				return eased(lerp(a, s, f), t);
			}
			prev = before;
			before = s;
		}
		// past the newest frame: carry on at its velocity for a little, then hold
		Snap last = before;
		if (prev == null || distance(prev, last) > TELEPORT) {
			return last;
		}
		double over = Math.min(t - last.tick(), MAX_EXTRAPOLATE);
		double span = last.tick() - prev.tick();
		double k = over / span;
		// never carry a climb on upward: a step is over in its tick
		double dy = last.y() - prev.y();
		double vy = dy > STEP_MIN ? 0.0D : dy;
		return eased(new Snap(last.tick(), last.x() + (last.x() - prev.x()) * k, last.y() + vy * k,
				last.z() + (last.z() - prev.z()) * k, last.yRot(), last.bodyRot(), last.onGround()), t);
	}

	/**
	 * Lower {@code s} by what is left of any step-up in the last {@link #STEP_EASE} ticks: the rise
	 * the frames make in one tick is shown spread linearly over three.
	 */
	private Snap eased(Snap s, double t) {
		double lower = 0.0D;
		Snap before = null;
		for (Snap f : snaps) {
			if (before != null && f.tick() - before.tick() == 1 && t > before.tick() && t < before.tick() + STEP_EASE) {
				double rise = f.y() - before.y();
				if (rise > STEP_MIN && rise <= STEP_MAX) {
					double since = t - before.tick();
					lower += rise * (Math.min(1.0D, since) - since / STEP_EASE);
				}
			}
			before = f;
		}
		return lower <= 0.0D ? s : new Snap(s.tick(), s.x(), s.y() - lower, s.z(), s.yRot(), s.bodyRot(), s.onGround());
	}

	/** Drop frames well behind {@code t}, keeping the newest one at or before it. */
	private void trim(double t) {
		while (snaps.size() > 2) {
			Iterator<Snap> it = snaps.iterator();
			it.next();
			Snap second = it.next();
			if (second.tick() < t - KEEP_BEHIND) {
				snaps.removeFirst();
			} else {
				break;
			}
		}
	}

	static double distance(Snap a, Snap b) {
		double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	static Snap lerp(Snap a, Snap b, double f) {
		return new Snap(a.tick(), a.x() + (b.x() - a.x()) * f, a.y() + (b.y() - a.y()) * f, a.z() + (b.z() - a.z()) * f,
				lerpDegrees(a.yRot(), b.yRot(), f), lerpDegrees(a.bodyRot(), b.bodyRot(), f), f < 0.5D ? a.onGround() : b.onGround());
	}

	/** Yaw the short way round. */
	static float lerpDegrees(float from, float to, double f) {
		double d = ((to - from) % 360.0D + 540.0D) % 360.0D - 180.0D;
		return (float) (from + d * f);
	}
}
