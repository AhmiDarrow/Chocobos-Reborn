package tk.darrow.chocobosreborn.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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

	/** Vanilla dispenser behaviour uses EntityType.spawn, which never sees this item's colour. */
	public static void registerDispensers() {
		DefaultDispenseItemBehavior behaviour = new DefaultDispenseItemBehavior() {
			@Override
			protected ItemStack execute(BlockSource source, ItemStack stack) {
				if (!(stack.getItem() instanceof ChocoboSpawnEggItem egg)) {
					return super.execute(source, stack);
				}
				ServerLevel level = source.level();
				Direction dir = source.state().getValue(DispenserBlock.FACING);
				BlockPos pos = source.pos().relative(dir);
				if (level.getFluidState(pos).is(FluidTags.LAVA) && !egg.color().lavaWalk() && !egg.color().fireImmune()) {
					return stack;
				}
				if (egg.spawnColored(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, dir.toYRot(), false, true) == null) {
					return stack;
				}
				stack.shrink(1);
				return stack;
			}
		};
		for (ChocoboColor c : ChocoboColor.values()) {
			DispenserBlock.registerBehavior(ModItems.eggItem(c), behaviour);
		}
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, net.minecraft.world.entity.LivingEntity target, InteractionHand hand) {
		if (!(target instanceof ChocoboEntity host) || host.townBird() || host.raceNpc() || host.racing()) {
			return InteractionResult.PASS;
		}
		if (!(player.level() instanceof ServerLevel sl)) {
			return InteractionResult.SUCCESS;
		}
		ChocoboEntity chick = spawnColored(sl, target.getX(), target.getY(), target.getZ(), target.getYRot(), true, false);
		if (chick == null) {
			return InteractionResult.FAIL;
		}
		chick.setOwnerUUID(player.getUUID());
		chick.setTame(true, false);
		tk.darrow.chocobosreborn.ledger.ChocoboLedger.get(sl).update(chick);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (context.getClickedFace() == Direction.UP) {
			pos = pos.above();
		} else {
			pos = pos.relative(context.getClickedFace());
		}
		boolean lava = level.getFluidState(pos).is(FluidTags.LAVA);
		boolean water = level.getFluidState(pos).is(FluidTags.WATER);
		if (lava || water) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel sl)) {
			return InteractionResult.SUCCESS;
		}
		if (spawnColored(sl, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
				level.random.nextFloat() * 360.0F, false, true) == null) {
			return InteractionResult.FAIL;
		}
		if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return InteractionResultHolder.pass(stack);
		}
		BlockPos pos = hit.getBlockPos();
		boolean water = level.getFluidState(pos).is(FluidTags.WATER);
		boolean lava = level.getFluidState(pos).is(FluidTags.LAVA);
		if (!water && !lava) {
			return InteractionResultHolder.pass(stack);
		}
		if (lava && !color.lavaWalk() && !color.fireImmune()) {
			return InteractionResultHolder.pass(stack);
		}
		if (!(level instanceof ServerLevel sl)) {
			return InteractionResultHolder.success(stack);
		}
		if (spawnColored(sl, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
				level.random.nextFloat() * 360.0F, false, true) == null) {
			return InteractionResultHolder.fail(stack);
		}
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return InteractionResultHolder.consume(stack);
	}

	private ChocoboEntity spawnColored(ServerLevel level, double x, double y, double z, float yaw, boolean baby,
	                                   boolean collide) {
		ChocoboEntity bird = ModEntities.CHOCOBO.get().create(level);
		if (bird == null) {
			return null;
		}
		bird.moveTo(x, y, z, yaw, 0.0F);
		bird.setColor(color);
		bird.setPersistenceRequired();
		if (baby) {
			bird.markChick();
		}
		if (collide && !level.noCollision(bird)) {
			return null;
		}
		bird.finalizeSpawn(level, level.getCurrentDifficultyAt(bird.blockPosition()), MobSpawnType.SPAWN_EGG, null);
		level.addFreshEntity(bird);
		return bird;
	}
}
