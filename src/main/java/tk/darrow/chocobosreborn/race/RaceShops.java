package tk.darrow.chocobosreborn.race;

import java.util.List;

/**
 * ChocobosReborn stall tables, priced in GP (Chocobo Square money). Greens and
 * nuts follow the FF7 ladder: Gysahl is pocket change, Sylkis and Zeio are
 * the prize of a long season. Priced against the purses (20 / 40 / 80 / 150 GP
 * for a win in C / B / A / S; Ahmi: "prices need to be higher with the amount of
 * GP you win"). 128 is the ceiling two 64-stack cost slots can hold.
 */
public final class RaceShops {
	public record Line(String role, int cost, String resultId, int resultCount) {
	}

	private RaceShops() {
	}

	public static List<Line> catalog() {
		return List.of(
				new Line("tack", 12, "chocobosreborn:chocobo_saddle", 1),
				new Line("tack", 30, "chocobosreborn:chocobo_lure", 1),
				new Line("greens", 2, "chocobosreborn:gysahl_green", 8),
				new Line("greens", 2, "chocobosreborn:gysahl_green_seeds", 4),
				new Line("greens", 8, "chocobosreborn:krakka_green", 1),
				new Line("greens", 10, "chocobosreborn:tantal_green", 1),
				new Line("greens", 20, "chocobosreborn:pahsana_green", 1),
				new Line("greens", 25, "chocobosreborn:curiel_green", 1),
				new Line("greens", 40, "chocobosreborn:mimett_green", 1),
				new Line("greens", 75, "chocobosreborn:reagan_green", 1),
				new Line("greens", 128, "chocobosreborn:sylkis_green", 1),
				new Line("fair", 8, "chocobosreborn:chocobo_almanac", 1),
				new Line("fair", 30, "chocobosreborn:chocobo_pocketwatch", 1),
				new Line("fair", 5, "minecraft:firework_rocket", 4),
				new Line("fair", 8, "minecraft:lead", 1),
				new Line("stablehand", 2, "chocobosreborn:gysahl_green", 6),
				new Line("stablehand", 2, "chocobosreborn:gysahl_green_seeds", 3),
				new Line("stablehand", 10, "chocobosreborn:krakka_green", 1),
				new Line("stablehand", 5, "chocobosreborn:pepio_nut", 1),
				new Line("stablehand", 8, "chocobosreborn:luchile_nut", 1),
				// Marl's GP Exchange: GP for everyday goods (a C-class win is 20 GP, an S-class win 150)
				new Line("exchange", 3, "minecraft:bread", 4),
				new Line("exchange", 3, "minecraft:hay_block", 2),
				new Line("exchange", 3, "minecraft:torch", 16),
				new Line("exchange", 6, "minecraft:iron_ingot", 2),
				new Line("exchange", 6, "minecraft:emerald", 1),
				new Line("exchange", 6, "minecraft:lead", 2),
				new Line("exchange", 9, "minecraft:gold_ingot", 1),
				new Line("exchange", 9, "minecraft:golden_carrot", 4),
				new Line("exchange", 12, "minecraft:lapis_lazuli", 8),
				new Line("exchange", 12, "minecraft:redstone", 8),
				new Line("exchange", 18, "minecraft:name_tag", 1),
				new Line("exchange", 18, "minecraft:ender_pearl", 2),
				new Line("exchange", 24, "minecraft:saddle", 1),
				new Line("exchange", 30, "minecraft:experience_bottle", 8),
				new Line("exchange", 36, "minecraft:diamond", 1),
				new Line("exchange", 72, "minecraft:totem_of_undying", 1),
				new Line("exchange", 120, "minecraft:enchanted_golden_apple", 1),
				new Line("treats", 3, "chocobosreborn:pepio_nut", 1),
				new Line("treats", 5, "chocobosreborn:luchile_nut", 1),
				new Line("treats", 8, "chocobosreborn:saraha_nut", 1),
				new Line("treats", 12, "chocobosreborn:lasan_nut", 1),
				new Line("treats", 16, "chocobosreborn:pram_nut", 1),
				new Line("treats", 20, "chocobosreborn:porov_nut", 1),
				new Line("treats", 90, "chocobosreborn:carob_nut", 1),
				new Line("treats", 128, "chocobosreborn:zeio_nut", 1)
		);
	}
}
