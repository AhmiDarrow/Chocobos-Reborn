package tk.darrow.chocobosreborn.menu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import tk.darrow.chocobosreborn.ChocobosReborn;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;

public final class ModMenus {
	public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ChocobosReborn.MOD_ID);

	public static final DeferredHolder<MenuType<?>, MenuType<ChocoboInventoryMenu>> CHOCOBO = MENUS.register("chocobo",
			() -> IMenuTypeExtension.create((windowId, inv, buf) -> {
				int id = buf.readVarInt();
				ChocoboEntity bird = inv.player.level().getEntity(id) instanceof ChocoboEntity c ? c : null;
				if (bird == null) {
					throw new IllegalStateException("no chocobo " + id);
				}
				return new ChocoboInventoryMenu(windowId, inv, bird);
			}));

	private ModMenus() {
	}
}
