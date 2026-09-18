package tk.darrow.chocobosreborn.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.race.RaceManager;
import tk.darrow.chocobosreborn.race.Square;

/**
 * Chocobo Pocketwatch: winds the player to Whiskerwind (the Chocobo Square)
 * and back to where they left. A saddled, owned bird under the player comes too.
 */
public class ChocoboPocketwatchItem extends Item {
	private static final int COOLDOWN = 60;

	public ChocoboPocketwatchItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}
		if (Square.isSquare(level)) {
			// leaveSquare forfeits only this rider; the course islands are too far
			// from the return gate to walk, and racing cancels dismount.
			RaceManager.leaveSquare(sp);
		} else if (sp.getVehicle() instanceof ChocoboEntity bird) {
			if (!RaceManager.enterSquare(sp, bird)) {
				return InteractionResultHolder.fail(stack);
			}
		} else if (!RaceManager.enterSquareOnFoot(sp)) {
			return InteractionResultHolder.fail(stack);
		}
		sp.getCooldowns().addCooldown(this, COOLDOWN);
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tip, TooltipFlag flag) {
		tip.add(Component.translatable("chocobosreborn.tip.watch").withStyle(ChatFormatting.GRAY));
		tip.add(Component.translatable("chocobosreborn.tip.watch2").withStyle(ChatFormatting.DARK_GRAY));
	}
}
