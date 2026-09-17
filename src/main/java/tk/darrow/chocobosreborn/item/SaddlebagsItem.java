package tk.darrow.chocobosreborn.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Saddlebags: 15 slots of storage on a chocobo. Name them on an anvil; the name heads the bird's inventory. */
public class SaddlebagsItem extends Item {
	public SaddlebagsItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tip, TooltipFlag flag) {
		tip.add(Component.translatable("chocobosreborn.tip.saddlebags").withStyle(ChatFormatting.GRAY));
	}
}
