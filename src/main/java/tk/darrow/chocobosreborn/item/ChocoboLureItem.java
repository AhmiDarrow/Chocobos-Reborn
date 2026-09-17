package tk.darrow.chocobosreborn.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Chocobo Lure. In FF7 it is the materia that lets chocobos find you; here a
 * held lure draws wild chocobos toward the holder and outlines them so the
 * player can spot tracks across a field. Effects are applied in
 * ChocoboEntity (tempt goal + glow).
 */
public class ChocoboLureItem extends Item {
	public ChocoboLureItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
		tip.add(Component.translatable("chocobosreborn.tip.lure").withStyle(ChatFormatting.GRAY));
	}
}
