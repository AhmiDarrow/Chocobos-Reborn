package tk.darrow.chocobosreborn.race;

import java.util.List;

/**
 * ChocobosReborn stall tables, priced in GP (Chocobo Square money). Greens and
 * nuts follow the FF7 ladder: Gysahl is pocket change, Sylkis and Zeio are
 * the prize of a long season. A sprint win pays 6 / 12 / 24 / 48 GP in
 * C / B / A / S. A grand prix pays three times that. 128 is the ceiling two
 * 64-stack cost slots can hold.
 */
public final class RaceShops {
	public record Line(String role, int cost, String resultId, int resultCount) {
	}

	private RaceShops() {
	}

	public static List<Line> catalog() {
		return List.of(
				new Line("tack", 8, "chocobosreborn:chocobo_saddle", 1),
				new Line("tack", 16, "chocobosreborn:chocobo_lure", 1),
				new Line("greens", 2, "chocobosreborn:gysahl_green", 8),
				new Line("greens", 2, "chocobosreborn:gysahl_green_seeds", 4),
				new Line("greens", 4, "chocobosreborn:krakka_green", 1),
				new Line("greens", 6, "chocobosreborn:tantal_green", 1),
				new Line("greens", 10, "chocobosreborn:pahsana_green", 1),
				new Line("greens", 14, "chocobosreborn:curiel_green", 1),
				new Line("greens", 22, "chocobosreborn:mimett_green", 1),
				new Line("greens", 40, "chocobosreborn:reagan_green", 1),
				new Line("greens", 80, "chocobosreborn:sylkis_green", 1),
				new Line("fair", 4, "chocobosreborn:chocobo_almanac", 1),
				new Line("fair", 16, "chocobosreborn:chocobo_pocketwatch", 1),
				new Line("fair", 4, "minecraft:firework_rocket", 4),
				new Line("fair", 4, "minecraft:lead", 1),
				new Line("stablehand", 2, "chocobosreborn:gysahl_green", 6),
				new Line("stablehand", 2, "chocobosreborn:gysahl_green_seeds", 3),
				// Marl's GP Exchange. A C sprint is 6 GP, an S sprint is 48.
				new Line("exchange", 2, "minecraft:bread", 4),
				new Line("exchange", 2, "minecraft:hay_block", 2),
				new Line("exchange", 2, "minecraft:torch", 16),
				new Line("exchange", 4, "minecraft:iron_ingot", 2),
				new Line("exchange", 4, "minecraft:emerald", 1),
				new Line("exchange", 4, "minecraft:lead", 2),
				new Line("exchange", 6, "minecraft:gold_ingot", 1),
				new Line("exchange", 6, "minecraft:golden_carrot", 4),
				new Line("exchange", 8, "minecraft:lapis_lazuli", 8),
				new Line("exchange", 8, "minecraft:redstone", 8),
				new Line("exchange", 12, "minecraft:name_tag", 1),
				new Line("exchange", 12, "minecraft:ender_pearl", 2),
				new Line("exchange", 16, "minecraft:saddle", 1),
				new Line("exchange", 20, "minecraft:experience_bottle", 8),
				new Line("exchange", 24, "minecraft:diamond", 1),
				new Line("exchange", 48, "minecraft:totem_of_undying", 1),
				new Line("exchange", 80, "minecraft:enchanted_golden_apple", 1),
				new Line("treats", 2, "chocobosreborn:pepio_nut", 1),
				new Line("treats", 3, "chocobosreborn:luchile_nut", 1),
				new Line("treats", 5, "chocobosreborn:saraha_nut", 1),
				new Line("treats", 8, "chocobosreborn:lasan_nut", 1),
				new Line("treats", 10, "chocobosreborn:pram_nut", 1),
				new Line("treats", 14, "chocobosreborn:porov_nut", 1),
				new Line("treats", 48, "chocobosreborn:carob_nut", 1),
				new Line("treats", 96, "chocobosreborn:zeio_nut", 1)
		);
	}
}
