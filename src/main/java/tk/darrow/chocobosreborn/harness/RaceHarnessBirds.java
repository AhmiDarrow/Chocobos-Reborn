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

	/** C is a normal yellow. B climbs or fords. A is a black, or a flame where the course is lava. S is a gold. */
	public static ChocoboColor colorFor(RaceTrack track) {
		boolean lava = false;
		boolean water = false;
		boolean ridge = false;
		for (RaceTrack.Feature feature : track.features()) {
			switch (feature.type()) {
				case LAVA -> lava = true;
				case WATER -> water = true;
				case RIDGE -> ridge = true;
				default -> {
				}
			}
		}
		return switch (track.getRaceClass()) {
			case C -> ChocoboColor.YELLOW;
			case B -> water && !ridge ? ChocoboColor.BLUE : ChocoboColor.GREEN;
			case A -> lava ? ChocoboColor.FLAME : ChocoboColor.BLACK;
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
