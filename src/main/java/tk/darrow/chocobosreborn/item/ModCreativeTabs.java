package tk.darrow.chocobosreborn.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import tk.darrow.chocobosreborn.ChocobosReborn;

public final class ModCreativeTabs {
	public static final DeferredRegister<CreativeModeTab> TABS =
			DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ChocobosReborn.MOD_ID);

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () ->
			CreativeModeTab.builder()
					.title(Component.translatable("itemGroup.chocobosreborn"))
					.icon(() -> ModItems.GYSAHL.get().getDefaultInstance())
					.displayItems((params, out) -> ModItems.ITEMS.getEntries().forEach(item -> out.accept(item.get())))
					.build());

	private ModCreativeTabs() {
	}
}
