package tk.darrow.chocobosreborn.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

/** Global loot modifier: add {@code count} of {@code item} with {@code chance}. */
public class AddItemModifier extends LootModifier {
	public static final MapCodec<AddItemModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
			LootModifier.codecStart(inst)
					.and(BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(m -> m.item))
					.and(Codec.INT.optionalFieldOf("count", 1).forGetter(m -> m.count))
					.and(Codec.FLOAT.optionalFieldOf("chance", 1.0F).forGetter(m -> m.chance))
					.apply(inst, AddItemModifier::new));

	private final Item item;
	private final int count;
	private final float chance;

	public AddItemModifier(LootItemCondition[] conditions, Item item, int count, float chance) {
		super(conditions);
		this.item = item;
		this.count = count;
		this.chance = chance;
	}

	@Override
	protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
		if (ctx.getRandom().nextFloat() < chance) {
			loot.add(new ItemStack(item, count));
		}
		return loot;
	}

	@Override
	public MapCodec<? extends IGlobalLootModifier> codec() {
		return CODEC;
	}
}
