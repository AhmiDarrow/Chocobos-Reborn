package tk.darrow.chocobosreborn.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import tk.darrow.chocobosreborn.breed.ChocoboColor;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.entity.ModEntities;

public class ChocoboSpawnEggItem extends DeferredSpawnEggItem {
	private final ChocoboColor color;

	public ChocoboSpawnEggItem(ChocoboColor color, int primary, int secondary, Properties properties) {
		super(ModEntities.CHOCOBO, primary, secondary, properties);
		this.color = color;
	}

	public ChocoboColor color() {
		return color;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = context.getClickedPos();
		if (context.getClickedFace() == Direction.UP) {
			pos = pos.above();
		} else {
			pos = pos.relative(context.getClickedFace());
		}
		ChocoboEntity bird = ModEntities.CHOCOBO.get().create(level);
		if (bird == null) {
			return InteractionResult.FAIL;
		}
		bird.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
		bird.setColor(color);
		bird.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.SPAWN_EGG, null);
		level.addFreshEntity(bird);
		if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.CONSUME;
	}
}
