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

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}
}
