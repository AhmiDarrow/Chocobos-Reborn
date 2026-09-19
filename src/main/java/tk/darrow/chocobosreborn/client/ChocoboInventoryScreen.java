package tk.darrow.chocobosreborn.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.menu.ChocoboInventoryMenu;

/**
 * Horse-style equipment screen: a drawn panel, slot wells, the bird's name and the bags' name,
 * and Follow / Stay / Wander tabs between the equipment and the bags.
 */
public class ChocoboInventoryScreen extends AbstractContainerScreen<ChocoboInventoryMenu> {
	private static final int PANEL = 0xFFC6C6C6, EDGE_L = 0xFFFFFFFF, EDGE_D = 0xFF555555, WELL = 0xFF8B8B8B, WELL_D = 0xFF373737;
	/** Command tabs, relative to the panel: one per {@link ChocoboEntity.Command}, stacked beside the equipment wells. */
	private static final int TAB_X = 29, TAB_Y = 18, TAB_W = 46, TAB_H = 16, TAB_STEP = 18;

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
		int tab = tabAt(mouseX, mouseY);
		if (tab >= 0 && menu.getCarried().isEmpty()) {
			g.renderTooltip(font, font.split(Component.translatable("chocobosreborn.command."
					+ ChocoboEntity.Command.values()[tab].id() + ".desc"), 160), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int tab = tabAt(mouseX, mouseY);
		if (tab >= 0 && button == 0) {
			if (tab != menu.bird().command().ordinal() && minecraft != null && minecraft.gameMode != null) {
				minecraft.gameMode.handleInventoryButtonClick(menu.containerId, tab);
				minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
						net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
			}
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private int tabAt(double mouseX, double mouseY) {
		double rx = mouseX - leftPos - TAB_X, ry = mouseY - topPos - TAB_Y;
		if (rx < 0 || rx >= TAB_W || ry < 0) {
			return -1;
		}
		int i = (int) (ry / TAB_STEP);
		return i < ChocoboEntity.Command.values().length && ry - i * TAB_STEP < TAB_H ? i : -1;
	}

	/** Raised button, or a sunk well for the bird's current order. */
	private void tabs(GuiGraphics g, int mouseX, int mouseY) {
		ChocoboEntity.Command current = menu.bird().command();
		int hover = tabAt(mouseX, mouseY);
		for (ChocoboEntity.Command c : ChocoboEntity.Command.values()) {
			int x = leftPos + TAB_X, y = topPos + TAB_Y + c.ordinal() * TAB_STEP;
			boolean on = c == current;
			g.fill(x, y, x + TAB_W, y + TAB_H, on ? EDGE_L : EDGE_D);
			g.fill(x, y, x + TAB_W - 1, y + TAB_H - 1, on ? WELL_D : EDGE_L);
			g.fill(x + 1, y + 1, x + TAB_W - 1, y + TAB_H - 1, on ? WELL : c.ordinal() == hover ? 0xFFDADADA : PANEL);
			Component label = Component.translatable("chocobosreborn.command." + c.id());
			g.drawString(font, label, x + (TAB_W - font.width(label)) / 2, y + 4, on ? 0xFFFFFFFF : 0xFF404040, on);
		}
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
		tabs(g, mouseX, mouseY);
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
