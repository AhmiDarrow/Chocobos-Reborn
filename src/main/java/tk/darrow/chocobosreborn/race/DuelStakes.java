package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;

/**
 * The stakes the duel picker offers ({@code CourseSelectScreen}). The server clamps a posted stake to
 * 0..32 and then to the course purse ({@link RaceScoring#clampDuelStake(int, int)}), so the picker stops
 * at the biggest purse of the class it shows.
 */
public final class DuelStakes {
	/** The picker's fixed stake steps. */
	private static final int[] STEPS = {0, 4, 8, 16, 32};

	private DuelStakes() {
	}

	/** The biggest first-place purse among a class's courses: no duel stake in that class can be more. */
	public static int biggestPurse(RaceClass raceClass) {
		int max = 0;
		for (RaceTrack t : RaceTrack.ofClass(raceClass)) {
			max = Math.max(max, RaceScoring.purse(t));
		}
		return max;
	}

	/** The steps up to {@code maxPurse}, ending on the purse itself when it falls between two steps. */
	public static int[] upTo(int maxPurse) {
		ArrayList<Integer> out = new ArrayList<>();
		for (int s : STEPS) {
			if (s <= maxPurse) {
				out.add(s);
			}
		}
		int cap = RaceScoring.clampDuelStake(maxPurse);
		if (out.get(out.size() - 1) < cap) {
			out.add(cap);
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	/** The picked stake as the server will take it on {@code track}: never more than its purse. */
	public static int onCourse(int picked, RaceTrack track) {
		return RaceScoring.clampDuelStake(picked, RaceScoring.purse(track));
	}
}
