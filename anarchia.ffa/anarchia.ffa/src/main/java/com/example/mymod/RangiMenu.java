package com.example.mymod;

import java.util.function.BiConsumer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu 3-rzędowe dla kitów i kluczy rang: nic nie da się wyjąć, kliknięcie w slot wywołuje akcję. */
public class RangiMenu extends AbstractContainerMenu {
	private final BiConsumer<ServerPlayer, Integer> onClick;

	private class ClickSlot extends Slot {
		private final int index;

		ClickSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
			this.index = index;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			if (player instanceof ServerPlayer sp && !getItem().isEmpty()) {
				onClick.accept(sp, index);
			}
			return false;
		}
	}

	public RangiMenu(int id, Inventory inventory, SimpleContainer box, BiConsumer<ServerPlayer, Integer> onClick) {
		super(MenuType.GENERIC_9x3, id);
		this.onClick = onClick;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new ClickSlot(box, col + row * 9, 8 + col * 18, 18 + row * 18));
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, 8 + col * 18, 142));
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}
