package tk.darrow.chocobosreborn.item;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.block.ModBlocks;
import tk.darrow.chocobosreborn.breed.ChocoboColor;
import tk.darrow.chocobosreborn.breed.ChocoboGreen;
import tk.darrow.chocobosreborn.breed.ChocoboNut;
import tk.darrow.chocobosreborn.entity.ModEntities;

/**
 * Final Fantasy VII chocobo economy: the eight greens, the eight nuts, the
 * Chocobo Lure, GP for Chocobo Square, a saddle, the Almanac, the Pocketwatch, and the
 * Spawn eggs per colour.
 */
public final class ModItems {
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ChocobosReborn.MOD_ID);

	// greens
	public static final DeferredItem<Item> GYSAHL_SEEDS = ITEMS.register("gysahl_green_seeds",
			() -> new ItemNameBlockItem(ModBlocks.GYSAHL_GREEN.get(), new Item.Properties()));
	public static final DeferredItem<Item> GYSAHL = green("gysahl_green", ChocoboGreen.GYSAHL, true);
	public static final DeferredItem<Item> KRAKKA_GREEN = green("krakka_green", ChocoboGreen.KRAKKA, false);
	public static final DeferredItem<Item> TANTAL_GREEN = green("tantal_green", ChocoboGreen.TANTAL, false);
	public static final DeferredItem<Item> PAHSANA_GREEN = green("pahsana_green", ChocoboGreen.PAHSANA, false);
	public static final DeferredItem<Item> CURIEL_GREEN = green("curiel_green", ChocoboGreen.CURIEL, false);
	public static final DeferredItem<Item> MIMETT_GREEN = green("mimett_green", ChocoboGreen.MIMETT, false);
	public static final DeferredItem<Item> REAGAN_GREEN = green("reagan_green", ChocoboGreen.REAGAN, false);
	public static final DeferredItem<Item> SYLKIS_GREEN = green("sylkis_green", ChocoboGreen.SYLKIS, false);

	// nuts
	public static final DeferredItem<Item> PEPIO_NUT = nut("pepio_nut", ChocoboNut.PEPIO);
	public static final DeferredItem<Item> LUCHILE_NUT = nut("luchile_nut", ChocoboNut.LUCHILE);
	public static final DeferredItem<Item> SARAHA_NUT = nut("saraha_nut", ChocoboNut.SARAHA);
	public static final DeferredItem<Item> LASAN_NUT = nut("lasan_nut", ChocoboNut.LASAN);
	public static final DeferredItem<Item> PRAM_NUT = nut("pram_nut", ChocoboNut.PRAM);
	public static final DeferredItem<Item> POROV_NUT = nut("porov_nut", ChocoboNut.POROV);
	public static final DeferredItem<Item> CAROB_NUT = nut("carob_nut", ChocoboNut.CAROB);
	public static final DeferredItem<Item> ZEIO_NUT = ITEMS.register("zeio_nut",
			() -> new NutItem(ChocoboNut.ZEIO, new Item.Properties().rarity(Rarity.RARE)));

	// tools and Square
	public static final DeferredItem<Item> CHOCOBO_LURE = ITEMS.register("chocobo_lure",
			() -> new ChocoboLureItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
	public static final DeferredItem<Item> SADDLE = ITEMS.register("chocobo_saddle",
			() -> new SaddleItem(new Item.Properties().stacksTo(1)));
	public static final DeferredItem<Item> ALMANAC = ITEMS.register("chocobo_almanac",
			() -> new ChocoboAlmanacItem(new Item.Properties().stacksTo(1)));
	public static final DeferredItem<Item> POCKETWATCH = ITEMS.register("chocobo_pocketwatch",
			() -> new ChocoboPocketwatchItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
	public static final DeferredItem<Item> GP = ITEMS.registerSimpleItem("gp");
	public static final DeferredItem<Item> SADDLEBAGS = ITEMS.register("saddlebags",
			() -> new SaddlebagsItem(new Item.Properties().stacksTo(1)));
	public static final DeferredItem<Item> ARMOR_LEATHER = armor("leather_chocobo_armor", ChocoboArmorItem.Tier.LEATHER, Rarity.COMMON);
	public static final DeferredItem<Item> ARMOR_IRON = armor("iron_chocobo_armor", ChocoboArmorItem.Tier.IRON, Rarity.COMMON);
	public static final DeferredItem<Item> ARMOR_DIAMOND = armor("diamond_chocobo_armor", ChocoboArmorItem.Tier.DIAMOND, Rarity.RARE);
	public static final DeferredItem<Item> ARMOR_NETHERITE = armor("netherite_chocobo_armor", ChocoboArmorItem.Tier.NETHERITE, Rarity.EPIC);

	private static DeferredItem<Item> armor(String id, ChocoboArmorItem.Tier tier, Rarity rarity) {
		Item.Properties p = new Item.Properties().stacksTo(1).rarity(rarity);
		if (tier == ChocoboArmorItem.Tier.NETHERITE) {
			p.fireResistant();
		}
		return ITEMS.register(id, () -> new ChocoboArmorItem(tier, p));
	}
	public static final DeferredItem<BlockItem> BOOST_PAD = ITEMS.register("boost_pad",
			() -> new BlockItem(ModBlocks.BOOST_PAD.get(), new Item.Properties()));
	public static final DeferredItem<BlockItem> SQUARE_GATE = ITEMS.register("square_gate",
			() -> new BlockItem(ModBlocks.SQUARE_GATE.get(), new Item.Properties()));

	public static final DeferredItem<DeferredSpawnEggItem> YELLOW_EGG = egg("yellow_chocobo_spawn_egg", ChocoboColor.YELLOW, 0xF5B812, 0x2D6EDC);
	public static final DeferredItem<DeferredSpawnEggItem> GREEN_EGG = egg("green_chocobo_spawn_egg", ChocoboColor.GREEN, 0x4CB05A, 0x2D6EDC);
	public static final DeferredItem<DeferredSpawnEggItem> BLUE_EGG = egg("blue_chocobo_spawn_egg", ChocoboColor.BLUE, 0x3A8FD0, 0x2D6EDC);
	public static final DeferredItem<DeferredSpawnEggItem> WHITE_EGG = egg("white_chocobo_spawn_egg", ChocoboColor.WHITE, 0xE8E4DC, 0x2D6EDC);
	public static final DeferredItem<DeferredSpawnEggItem> BLACK_EGG = egg("black_chocobo_spawn_egg", ChocoboColor.BLACK, 0x2C2A32, 0x2D6EDC);
	public static final DeferredItem<DeferredSpawnEggItem> GOLD_EGG = egg("gold_chocobo_spawn_egg", ChocoboColor.GOLD, 0xE8A416, 0x2D6EDC);
	public static final DeferredItem<DeferredSpawnEggItem> PURPLE_EGG = egg("purple_chocobo_spawn_egg", ChocoboColor.PURPLE, 0x9254D6, 0xDDDFA5);
	public static final DeferredItem<DeferredSpawnEggItem> FLAME_EGG = egg("flame_chocobo_spawn_egg", ChocoboColor.FLAME, 0xB8271E, 0xFF8C1A);
	public static final DeferredItem<DeferredSpawnEggItem> STEWARD_EGG = ITEMS.register("kin_steward_spawn_egg",
			() -> new DeferredSpawnEggItem(ModEntities.KIN_STEWARD, 0x5A4030, 0xF4C42A, new Item.Properties()));

	private static DeferredItem<Item> green(String id, ChocoboGreen green, boolean edible) {
		return ITEMS.register(id, () -> {
			Item.Properties p = new Item.Properties();
			if (edible) {
				p = p.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).build());
			}
			return new GreensItem(green, p);
		});
	}

	private static DeferredItem<Item> nut(String id, ChocoboNut nut) {
		return ITEMS.register(id, () -> new NutItem(nut, new Item.Properties()));
	}

	private static DeferredItem<DeferredSpawnEggItem> egg(String id, ChocoboColor color, int primary, int secondary) {
		return ITEMS.register(id, () -> new ChocoboSpawnEggItem(color, primary, secondary, new Item.Properties()));
	}

	public static Item greenItem(ChocoboGreen g) {
		return switch (g) {
			case GYSAHL -> GYSAHL.get();
			case KRAKKA -> KRAKKA_GREEN.get();
			case TANTAL -> TANTAL_GREEN.get();
			case PAHSANA -> PAHSANA_GREEN.get();
			case CURIEL -> CURIEL_GREEN.get();
			case MIMETT -> MIMETT_GREEN.get();
			case REAGAN -> REAGAN_GREEN.get();
			case SYLKIS -> SYLKIS_GREEN.get();
		};
	}

	public static Item eggItem(ChocoboColor color) {
		return switch (color) {
			case YELLOW -> YELLOW_EGG.get();
			case GREEN -> GREEN_EGG.get();
			case BLUE -> BLUE_EGG.get();
			case WHITE -> WHITE_EGG.get();
			case BLACK -> BLACK_EGG.get();
			case GOLD -> GOLD_EGG.get();
			case PURPLE -> PURPLE_EGG.get();
			case FLAME -> FLAME_EGG.get();
		};
	}

	private ModItems() {
	}
}
