package tk.darrow.chocobosreborn.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import tk.darrow.chocobosreborn.ChocobosReborn;

import java.util.List;

/**
 * A grey how-to line for any item or block that declares {@code <descriptionId>.hint} in en_us,
 * wrapped to tooltip width. Items with their own {@code chocobosreborn.tip.*} lines keep them. Only this
 * mod's items are considered: every companion mod runs the same hook, so an unscoped lookup would print
 * each hint once per installed mod.
 */
public final class ItemHints {
	private static final int WRAP_WIDTH = 220;

	private ItemHints() {}

	public static void tooltip(ItemTooltipEvent event) {
		if (!ChocobosReborn.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).getNamespace())) return;
		String key = event.getItemStack().getItem().getDescriptionId() + ".hint";
		if (!I18n.exists(key)) return;
		List<Component> lines = event.getToolTip();
		for (FormattedText part : Minecraft.getInstance().font.getSplitter().splitLines(Component.translatable(key), WRAP_WIDTH, Style.EMPTY)) {
			lines.add(Component.literal(part.getString()).withStyle(ChatFormatting.GRAY));
		}
	}
}
