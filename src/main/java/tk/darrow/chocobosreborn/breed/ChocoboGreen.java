package tk.darrow.chocobosreborn.breed;

import java.util.Locale;

/**
 * The eight Final Fantasy VII greens and what each one trains. Values are the
 * training points added per feed: speed, stamina, intelligence, cooperation
 * (FF7 stat names). Gysahl is the cheap green that also tames a wild bird.
 */
public enum ChocoboGreen {
	// Balance pass (Ahmi: "birds need to eat less or the greens do more"): every feed
	// gives twice the points it did, so a bird maxes a stat in a dozen Sylkis or a
	// couple of days of Gysahl instead of a granary.
	GYSAHL(2, 2, 0, 2, 40),
	KRAKKA(0, 0, 6, 0, 40),
	TANTAL(2, 4, 0, 0, 40),
	PAHSANA(0, 0, 4, 4, 30),
	CURIEL(4, 4, 0, 0, 30),
	MIMETT(4, 4, 0, 2, 24),
	REAGAN(4, 4, 4, 4, 16),
	SYLKIS(8, 8, 6, 6, 12);

	public static final int MAX_POINTS = 100;
	/** One training green every 5 minutes. Satiety is a second, longer gate. */
	public static final int TRAIN_COOLDOWN_TICKS = 6000;

	private final int speed;
	private final int stamina;
	private final int intelligence;
	private final int cooperation;
	/** Feeds of this green before the bird stops gaining from it (FF7 cap feel). */
	private final int satiety;

	ChocoboGreen(int speed, int stamina, int intelligence, int cooperation, int satiety) {
		this.speed = speed;
		this.stamina = stamina;
		this.intelligence = intelligence;
		this.cooperation = cooperation;
		this.satiety = satiety;
	}

	public int speed() {
		return speed;
	}

	public int stamina() {
		return stamina;
	}

	public int intelligence() {
		return intelligence;
	}

	public int cooperation() {
		return cooperation;
	}

	public int satiety() {
		return satiety;
	}

	/** Grade ladder: every 120 points of total training raises the grade one step (400 max = +3). */
	public static int gradeFromTraining(int baseRank, int totalPoints) {
		int bonus = totalPoints / 120;
		return Math.min(ChocoboGrade.WONDERFUL.getRank(), baseRank + bonus);
	}

	/** Creative / instabuild skips the wait so a test feed still works. Never-fed is 0. */
	public static boolean trainReady(long lastFeedGameTime, long now, boolean instabuild) {
		return instabuild || trainWaitTicks(lastFeedGameTime, now) <= 0;
	}

	public static int trainWaitTicks(long lastFeedGameTime, long now) {
		if (lastFeedGameTime <= 0L) {
			return 0;
		}
		long elapsed = now - lastFeedGameTime;
		if (elapsed < 0L) {
			return TRAIN_COOLDOWN_TICKS;
		}
		if (elapsed >= TRAIN_COOLDOWN_TICKS) {
			return 0;
		}
		return (int) (TRAIN_COOLDOWN_TICKS - elapsed);
	}

	/** Remaining wait as m:ss, rounding ticks up so 1 tick left is 0:01. */
	public static String trainWaitClock(int waitTicks) {
		int t = Math.max(0, waitTicks);
		int totalSec = (t + 19) / 20;
		int m = totalSec / 60;
		int s = totalSec % 60;
		return m + ":" + (s < 10 ? "0" : "") + s;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}
}
