package tk.darrow.chocobosreborn.race;

/**
 * Server-side lap credit, with a rescue instead of a lost lap (Ahmi: "making them
 * repeat the lap and putting them in last place is way too rough").
 *
 * <p>Progress only counts on the road. Off it, the last point on the road is held
 * as the anchor and nothing accrues. Coming back on:
 * <ul>
 * <li>no further round than the anchor plus the skip allowance (a wide line, a
 * slide across a verge): the stretch counts as driven and the lap goes on;</li>
 * <li>further round than that (a shortcut): {@link Step#RESCUE}. The session puts
 * the bird back on the road at the anchor. The lap is kept.</li>
 * </ul>
 * Off the road for {@link #OFF_LIMIT_TICKS} is also a rescue, as is a jump of more
 * than an eighth of a lap between two ticks (a teleport). Nothing forfeits a lap.
 */
public final class RaceLapProgress {
	/** Four seconds off the road and the bird is put back. */
	public static final int OFF_LIMIT_TICKS = 80;
	/** Road skipped by a wide line that still counts, in blocks. */
	public static final double SKIP_BLOCKS = 10.0D;
	/** A move this big between two ticks is a teleport, not riding. */
	private static final double JUMP = 0.125D;

	public enum Step { NONE, LAP, RESCUE }

	private double last;
	private double travelled;
	private boolean off;
	private int offTicks;
	/** Why the last {@link Step#RESCUE} came, for the server log. */
	private String rescueReason = "";

	public RaceLapProgress(double start) {
		last = start;
	}

	/** Last progress on the road (0..1 round the lap): the anchor while off it. */
	public double lastProgress() {
		return last;
	}

	public boolean offCourse() {
		return off;
	}

	public int offTicks() {
		return offTicks;
	}

	/** Why {@link #step} last returned {@link Step#RESCUE} (off-road time, a cut back on, a jump). */
	public String rescueReason() {
		return rescueReason;
	}

	/** Skip allowance as a share of the lap for a course of this length. */
	public static double allowance(double lapLength) {
		return SKIP_BLOCKS / Math.max(1.0D, lapLength);
	}

	/** The session has put the bird back at the anchor. */
	public void rescued() {
		off = false;
		offTicks = 0;
	}

	/** {@link #step} with a 1 % allowance, true on a completed lap (tests and older callers). */
	public boolean update(double progress, boolean onCourse) {
		return step(progress, onCourse, 0.01D) == Step.LAP;
	}

	public Step step(double progress, boolean onCourse, double allowance) {
		if (!Double.isFinite(progress)) {
			return Step.NONE;
		}
		if (!onCourse) {
			off = true;
			if (++offTicks >= OFF_LIMIT_TICKS) {
				offTicks = 0;   // once per limit, until the session puts it back
				rescueReason = "off-road " + OFF_LIMIT_TICKS + " ticks";
				return Step.RESCUE;
			}
			return Step.NONE;
		}
		double delta = wrap(progress - last);
		if (off) {
			off = false;
			offTicks = 0;
			if (delta > allowance) {
				off = true;      // still owed a put-back: keep the anchor
				rescueReason = String.format(java.util.Locale.ROOT, "back on %.4f lap past the anchor (allowance %.4f)", delta, allowance);
				return Step.RESCUE;
			}
		} else if (Math.abs(delta) > JUMP) {
			rescueReason = String.format(java.util.Locale.ROOT, "progress jumped %.4f lap in a tick", delta);
			return Step.RESCUE;
		}
		boolean crossed = delta > 0 && progress < last;
		last = progress;
		travelled = Math.max(0, travelled + delta);
		if (!crossed) {
			return Step.NONE;
		}
		boolean completed = travelled >= 0.95;
		travelled = 0;
		return completed ? Step.LAP : Step.NONE;
	}

	private static double wrap(double delta) {
		if (delta < -0.5) {
			return delta + 1;
		}
		if (delta > 0.5) {
			return delta - 1;
		}
		return delta;
	}
}
