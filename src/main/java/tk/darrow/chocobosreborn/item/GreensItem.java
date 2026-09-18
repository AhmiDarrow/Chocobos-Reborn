package tk.darrow.chocobosreborn.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import tk.darrow.chocobosreborn.breed.ChocoboGreen;

/** One of the eight FF7 greens. Feeding logic lives in ChocoboEntity.mobInteract. */
public class GreensItem extends Item {
	private final ChocoboGreen green;

	public GreensItem(ChocoboGreen green, Properties properties) {
		super(properties);
		this.green = green;
	}

	public ChocoboGreen green() {
		return green;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
		if (green == ChocoboGreen.GYSAHL) {
			tip.add(Component.translatable("chocobosreborn.tip.gysahl").withStyle(ChatFormatting.GRAY));
		}
		StringBuilder sb = new StringBuilder();
		if (green.speed() > 0) {
			sb.append(Component.translatable("chocobosreborn.tip.speed").getString()).append(" +").append(green.speed()).append("  ");
		}
		if (green.stamina() > 0) {
			sb.append(Component.translatable("chocobosreborn.tip.stamina").getString()).append(" +").append(green.stamina()).append("  ");
		}
		if (green.intelligence() > 0) {
			sb.append(Component.translatable("chocobosreborn.tip.intelligence").getString()).append(" +").append(green.intelligence()).append("  ");
		}
		if (green.cooperation() > 0) {
			sb.append(Component.translatable("chocobosreborn.tip.cooperation").getString()).append(" +").append(green.cooperation());
		}
		tip.add(Component.literal(sb.toString().trim()).withStyle(ChatFormatting.GREEN));
		tip.add(Component.translatable("chocobosreborn.tip.satiety", green.satiety()).withStyle(ChatFormatting.DARK_GRAY));
		tip.add(Component.translatable("chocobosreborn.tip.train_pace").withStyle(ChatFormatting.DARK_GRAY));
	}
}
