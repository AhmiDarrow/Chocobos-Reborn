package tk.darrow.chocobosreborn.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.item.ChocoboArmorItem;
import tk.darrow.chocobosreborn.item.SaddleItem;
import tk.darrow.chocobosreborn.item.SaddlebagsItem;

/**
 * The chocobo's equipment screen, laid out like a horse: saddle, armour and
 * saddlebags on the left, the bags' fifteen slots on the right (only while
 * saddlebags are worn), the player's inventory below.
 */
public class ChocoboInventoryMenu extends AbstractContainerMenu {
	public static final int SADDLE = 0, ARMOR = 1, BAGS = 2, BAG_START = 3, BAG_SIZE = ChocoboEntity.BAG_SIZE;
	private final ChocoboEntity bird;
	private final Container inv;

	public ChocoboInventoryMenu(int id, Inventory playerInv, ChocoboEntity bird) {
		super(ModMenus.CHOCOBO.get(), id);
		this.bird = bird;
		this.inv = bird.inventory();
		inv.startOpen(playerInv.player);
		addSlot(new Slot(inv, SADDLE, 8, 18) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof SaddleItem;
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}

			@Override
			public boolean mayPickup(Player player) {
				return !bird.isVehicle();
			}
		});
		addSlot(new Slot(inv, ARMOR, 8, 36) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof ChocoboArmorItem;
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		addSlot(new Slot(inv, BAGS, 8, 54) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof SaddlebagsItem;
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}

			@Override
			public boolean mayPickup(Player player) {
				// bags come off only when empty
				for (int i = BAG_START; i < BAG_START + BAG_SIZE; i++) {
					if (!inv.getItem(i).isEmpty()) {
						return false;
					}
				}
				return true;
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 5; col++) {
				addSlot(new Slot(inv, BAG_START + row * 5 + col, 80 + col * 18, 18 + row * 18) {
					@Override
					public boolean isActive() {
						return ChocoboInventoryMenu.this.bird.hasSaddlebags();
					}
				});
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
		}
	}

	public ChocoboEntity bird() {
		return bird;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return result;
		}
		ItemStack stack = slot.getItem();
		result = stack.copy();
		int birdSlots = BAG_START + BAG_SIZE;
		if ((index == BAGS || index == SADDLE) && !slot.mayPickup(player)) {
			return ItemStack.EMPTY;
		}
		if (index < birdSlots) {
			if (!moveItemStackTo(stack, birdSlots, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			if (slots.get(SADDLE).mayPlace(stack) && !slots.get(SADDLE).hasItem()) {
				if (!moveItemStackTo(stack, SADDLE, SADDLE + 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (slots.get(ARMOR).mayPlace(stack) && !slots.get(ARMOR).hasItem()) {
				if (!moveItemStackTo(stack, ARMOR, ARMOR + 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (slots.get(BAGS).mayPlace(stack) && !slots.get(BAGS).hasItem()) {
				if (!moveItemStackTo(stack, BAGS, BAGS + 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (bird.hasSaddlebags()) {
				if (!moveItemStackTo(stack, BAG_START, birdSlots, false)) {
					return ItemStack.EMPTY;
				}
			} else {
				return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return result;
	}

	@Override
	public boolean stillValid(Player player) {
		return !bird.isRemoved() && tk.darrow.chocobosreborn.race.RaceScoring.saddleBagStillValid(bird.isAlive(), bird.racing(),
				bird.distanceTo(player) < 8.0F) && inv.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		inv.stopOpen(player);
	}
}
