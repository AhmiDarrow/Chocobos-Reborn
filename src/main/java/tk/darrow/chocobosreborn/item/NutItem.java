package tk.darrow.chocobosreborn.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import tk.darrow.chocobosreborn.breed.ChocoboNut;

public class NutItem extends Item {
	private final ChocoboNut nut;

	public NutItem(ChocoboNut nut, Properties properties) {
		super(properties);
		this.nut = nut;
	}

	public ChocoboNut nut() {
		return nut;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
		tip.add(Component.translatable("chocobosreborn.tip.nut").withStyle(ChatFormatting.GRAY));
		tip.add(Component.translatable("chocobosreborn.tip.nut_talent", nut.getTier()).withStyle(ChatFormatting.GREEN));
		if (nut == ChocoboNut.CAROB) {
			tip.add(Component.translatable("chocobosreborn.tip.carob").withStyle(ChatFormatting.GOLD));
		} else if (nut == ChocoboNut.ZEIO) {
			tip.add(Component.translatable("chocobosreborn.tip.zeio").withStyle(ChatFormatting.GOLD));
		}
	}
}
