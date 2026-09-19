package tk.darrow.chocobosreborn.block;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import tk.darrow.chocobosreborn.ChocobosReborn;

public final class ModBlocks {
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ChocobosReborn.MOD_ID);

	public static final DeferredBlock<GysahlCropBlock> GYSAHL_GREEN = BLOCKS.register("gysahl_green",
			() -> new GysahlCropBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.PLANT)
					.noCollission()
					.randomTicks()
					.instabreak()
					.sound(SoundType.CROP)
					.pushReaction(PushReaction.DESTROY)));

	/** Wild Gysahl: placed by worldgen, including other mods' biomes. */
	public static final DeferredBlock<WildGysahlBlock> WILD_GYSAHL = BLOCKS.register("wild_gysahl",
			() -> new WildGysahlBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.PLANT)
					.noCollission()
					.instabreak()
					.sound(SoundType.CROP)
					.offsetType(BlockBehaviour.OffsetType.XZ)
					.ignitedByLava()
					.pushReaction(PushReaction.DESTROY)));

	public static final DeferredBlock<SquareGateBlock> SQUARE_GATE = BLOCKS.register("square_gate",
			() -> new SquareGateBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.GOLD)
					.strength(3.0F, 6.0F)
					.sound(SoundType.METAL)
					.requiresCorrectToolForDrops()));

	/** Boost strips on Whiskerwind's courses (course-only; no recipe). */
	public static final DeferredBlock<BoostPadBlock> BOOST_PAD = BLOCKS.register("boost_pad",
			() -> new BoostPadBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_CYAN)
					.noCollission()
					.noOcclusion()
					.instabreak()
					.sound(SoundType.WOOL)
					.pushReaction(PushReaction.DESTROY)));

	private ModBlocks() {
	}
}
