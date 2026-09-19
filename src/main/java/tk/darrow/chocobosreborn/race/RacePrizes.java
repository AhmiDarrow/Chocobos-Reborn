package tk.darrow.chocobosreborn.race;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import tk.darrow.chocobosreborn.item.ModItems;

/**
 * Chocobo Square prizes. GP for placing, and the FF7 prize-table feel:
 * greens and nuts by class; Carob on a B win (15%), an A win (40%) or any S race; a rare Zeio on an S win.
 */
public final class RacePrizes {
	private RacePrizes() {
	}

	public static int gp(RaceClass rc, int place, boolean ranked) {
		int first = switch (rc) {
			case C -> 20;
			case B -> 40;
			case A -> 80;
			case S -> 150;
		};
		if (!ranked) {
			first /= 2;
		}
		return switch (place) {
			case 1 -> first;
			case 2 -> first / 2;
			case 3 -> first / 4;
			default -> 0;
		};
	}

	public static List<ItemStack> items(RaceClass rc, int place, boolean ranked, RandomSource random) {
		List<ItemStack> out = new ArrayList<>();
		if (!ranked || place > 2) {
			return out;
		}
		boolean win = place == 1;
		switch (rc) {
			case C -> {
				out.add(new ItemStack(ModItems.GYSAHL.get(), win ? 8 : 4));
				if (win) {
					out.add(new ItemStack(random.nextBoolean() ? ModItems.PEPIO_NUT.get() : ModItems.LUCHILE_NUT.get()));
				}
			}
			case B -> {
				out.add(new ItemStack(ModItems.MIMETT_GREEN.get(), win ? 3 : 1));
				out.add(new ItemStack(random.nextBoolean() ? ModItems.SARAHA_NUT.get() : ModItems.LASAN_NUT.get()));
				if (win && random.nextFloat() < 0.15F) {
					out.add(new ItemStack(ModItems.CAROB_NUT.get()));
				}
			}
			case A -> {
				out.add(new ItemStack(ModItems.REAGAN_GREEN.get(), win ? 2 : 1));
				out.add(new ItemStack(random.nextBoolean() ? ModItems.PRAM_NUT.get() : ModItems.POROV_NUT.get()));
				if (win && random.nextFloat() < 0.40F) {
					out.add(new ItemStack(ModItems.CAROB_NUT.get()));
				}
			}
			case S -> {
				out.add(new ItemStack(ModItems.SYLKIS_GREEN.get(), win ? 2 : 1));
				out.add(new ItemStack(ModItems.CAROB_NUT.get()));
				if (win && random.nextFloat() < 0.12F) {
					out.add(new ItemStack(ModItems.ZEIO_NUT.get()));
				}
			}
		}
		return out;
	}
}
