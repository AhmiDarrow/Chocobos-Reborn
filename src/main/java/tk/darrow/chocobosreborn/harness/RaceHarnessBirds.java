package tk.darrow.chocobosreborn.harness;

import tk.darrow.chocobosreborn.breed.ChocoboColor;
import tk.darrow.chocobosreborn.breed.ChocoboGrade;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.race.RaceScoring;
import tk.darrow.chocobosreborn.race.RaceTrack;

/** The bird a class actually races. Never a gold on a C sprint. */
public final class RaceHarnessBirds {
	private RaceHarnessBirds() {
	}

	/**
	 * Only colours that may race ({@link RaceScoring#mayRace}): C a yellow; B a blue on a
	 * water-only course, else a green; A a black (it goes round lava like everything but Gold);
	 * S a gold. A Flame bird on A_EMBER's lava is barred from racing and cannot climb: all three
	 * harness riders stood at its ridge face (0 + 0.8627) the whole heat.
	 */
	public static ChocoboColor colorFor(RaceTrack track) {
		boolean water = false;
		boolean ridge = false;
		for (RaceTrack.Feature feature : track.features()) {
			switch (feature.type()) {
				case WATER -> water = true;
				case RIDGE -> ridge = true;
				default -> {
				}
			}
		}
		return switch (track.getRaceClass()) {
			case C -> ChocoboColor.YELLOW;
			case B -> water && !ridge ? ChocoboColor.BLUE : ChocoboColor.GREEN;
			case A -> ChocoboColor.BLACK;
			case S -> ChocoboColor.GOLD;
		};
	}

	public static void dress(ChocoboEntity bird, RaceTrack track) {
		bird.setColor(colorFor(track));
		bird.setGrade(ChocoboGrade.byRank(Math.min(ChocoboGrade.WONDERFUL.getRank(), track.getRaceClass().getId() + 1)));
		bird.setRaceClass(track.getRaceClass());
		bird.setGenes(0, 0, 0, 0);
		int train = RaceScoring.fieldTraining(track.getRaceClass().getId(), false);
		bird.setTraining(train, train, train, train);
		bird.fillStamina();
	}
}
