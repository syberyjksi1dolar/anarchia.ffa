package com.example.mymod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu wyboru miecza dla Zmianek (3 rzędy). Kliknięcie miecza losuje mu procenty; nic nie da się wyjąć. */
public class ZmiankiMenu extends AbstractContainerMenu {
	private final int[] map;
	private boolean done = false;

	private class SwordSlot extends Slot {
		private final int index;

		SwordSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
			this.index = index;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			if (player instanceof ServerPlayer sp && !done && !getItem().isEmpty()) {
				apply(sp, index);
			}
			return false;
		}
	}

	public ZmiankiMenu(int id, Inventory inventory, SimpleContainer box, int[] map) {
		super(MenuType.GENERIC_9x3, id);
		this.map = map;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new SwordSlot(box, col + row * 9, 8 + col * 18, 18 + row * 18));
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

	private void apply(ServerPlayer sp, int index) {
		done = true;
		ItemStack real = sp.getInventory().getItem(map[index]);
		if (!ZmiankiItem.isSword(real)) {
			sp.closeContainer();
			return;
		}
		// Zużywamy jedną Zmiankę z ekwipunku.
		boolean paid = false;
		for (int i = 0; i < 36; i++) {
			ItemStack s = sp.getInventory().getItem(i);
			if (s.is(ModItems.ZMIANKI)) {
				s.shrink(1);
				paid = true;
				break;
			}
		}
		if (!paid) {
			sp.sendSystemMessage(Component.literal("Nie masz już Zmianek!"), true);
			sp.closeContainer();
			return;
		}
		int v = ZmiankiItem.roll(sp.getRandom());
		ZmiankiItem.setPercent(real, v);
		sp.sendSystemMessage(Component.literal("Wylosowano dodatkowe obrażenia: " + (v > 0 ? "+" : "") + v + "%"), true);
		sp.closeContainer();
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
