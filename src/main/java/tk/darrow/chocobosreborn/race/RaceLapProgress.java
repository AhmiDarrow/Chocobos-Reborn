package tk.darrow.chocobosreborn.race;

/** Server-side lap credit: leaving the course forfeits that lap until the next start crossing. */
public final class RaceLapProgress {
	private double last;
	private double travelled;
	private boolean waitingForStart;

	public RaceLapProgress(double start) {
		last = start;
	}

	/** Last progress fed to {@link #update} (0..1 around the oval). */
	public double lastProgress() {
		return last;
	}

	public boolean waitingForStart() {
		return waitingForStart;
	}

	public boolean update(double progress, boolean onCourse) {
		double delta = progress - last;
		if (delta < -0.5) {
			delta += 1;
		}
		if (delta > 0.5) {
			delta -= 1;
		}
		boolean crossed = delta > 0 && progress < last;
		last = progress;
		if (!onCourse || !Double.isFinite(delta) || Math.abs(delta) > 0.125) {
			travelled = 0;
			waitingForStart = true;
			return false;
		}
		if (waitingForStart) {
			if (crossed) {
				waitingForStart = false;
			}
			return false;
		}
		travelled = Math.max(0, travelled + delta);
		if (!crossed) {
			return false;
		}
		boolean completed = travelled >= 0.95;
		travelled = 0;
		return completed;
	}
}
