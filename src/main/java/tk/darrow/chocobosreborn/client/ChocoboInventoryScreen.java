package tk.darrow.chocobosreborn.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.menu.ChocoboInventoryMenu;

/** Horse-style equipment screen: a drawn panel, slot wells, the bird's name and the bags' name. */
public class ChocoboInventoryScreen extends AbstractContainerScreen<ChocoboInventoryMenu> {
	private static final int PANEL = 0xFFC6C6C6, EDGE_L = 0xFFFFFFFF, EDGE_D = 0xFF555555, WELL = 0xFF8B8B8B, WELL_D = 0xFF373737;

	public ChocoboInventoryScreen(ChocoboInventoryMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		this.imageWidth = 176;
		this.imageHeight = 166;
		this.inventoryLabelY = imageHeight - 94;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		renderTooltip(g, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
		int x = leftPos, y = topPos;
		g.fill(x, y, x + imageWidth, y + imageHeight, EDGE_D);
		g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL);
		g.fill(x + 1, y + 1, x + imageWidth - 2, y + 2, EDGE_L);
		g.fill(x + 1, y + 1, x + 2, y + imageHeight - 2, EDGE_L);
		well(g, x + 7, y + 17);
		well(g, x + 7, y + 35);
		well(g, x + 7, y + 53);
		ChocoboEntity bird = menu.bird();
		if (bird.hasSaddlebags()) {
			for (int row = 0; row < 3; row++) {
				for (int col = 0; col < 5; col++) {
					well(g, x + 79 + col * 18, y + 17 + row * 18);
				}
			}
		} else {
			g.drawString(font, Component.translatable("chocobosreborn.inv.no_bags"), x + 80, y + 40, 0xFF6A6A6A, false);
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				well(g, x + 7 + col * 18, y + 83 + row * 18);
			}
		}
		for (int col = 0; col < 9; col++) {
			well(g, x + 7 + col * 18, y + 141);
		}
		// slot hints when empty
		hint(g, x + 8, y + 18, 0, "S");
		hint(g, x + 8, y + 36, 1, "A");
		hint(g, x + 8, y + 54, 2, "B");
	}

	private void hint(GuiGraphics g, int x, int y, int slot, String letter) {
		if (menu.getSlot(slot).getItem().isEmpty()) {
			g.drawString(font, letter, x + 5, y + 4, 0xFF5A5A5A, false);
		}
	}

	private void well(GuiGraphics g, int x, int y) {
		g.fill(x, y, x + 18, y + 18, WELL_D);
		g.fill(x + 1, y + 1, x + 18, y + 18, EDGE_L);
		g.fill(x + 1, y + 1, x + 17, y + 17, WELL);
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, title, titleLabelX, titleLabelY, 0xFF404040, false);
		ItemStack bags = menu.getSlot(ChocoboInventoryMenu.BAGS).getItem();
		if (!bags.isEmpty()) {
			Component name = bags.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) ? bags.getHoverName()
					: Component.translatable("item.chocobosreborn.saddlebags");
			g.drawString(font, name, 80, 6, 0xFF404040, false);
		}
		g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF404040, false);
	}
}
