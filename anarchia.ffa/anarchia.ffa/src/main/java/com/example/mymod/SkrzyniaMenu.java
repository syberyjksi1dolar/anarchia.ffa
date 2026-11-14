package com.example.mymod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * GUI skrzyni (6 rzędów): nagrody w slotach od 0, a w dolnym rzędzie przyciski:
 * 46 = niebieski barwnik (wszystkie klucze), 48 = zielony (animacja 5 s), 49 = bariera (zamknij), 50 = pomarańczowy (od razu).
 * Przedmiotów z GUI nie da się wyjąć ani włożyć. Menu nie używa ClickType (zmieniony w 26.1) -
 * kliknięcie w slot jest wykrywane w Slot.mayPickup.
 */
public class SkrzyniaMenu extends AbstractContainerMenu {
	/** Skrzynia ma 6 rzędów: nagrody w slotach 0-44, przyciski w dolnym rzędzie. */
	public static final int REWARD_SLOTS = 45;
	public static final int BTN_ALL = 46; // niebieski: otwórz wszystkie klucze
	public static final int BTN_ANIM = 48; // zielony: animacja 5 s
	public static final int BTN_CLOSE = 49; // bariera: zamknij
	public static final int BTN_NOW = 50; // pomarańczowy: od razu

	final SimpleContainer box;
	final Crates.Crate crate;
	final BlockPos pos;
	boolean busy = false;

	private class BoxSlot extends Slot {
		private final int index;

		BoxSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
			this.index = index;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			if (player instanceof ServerPlayer sp && !busy) {
				if (index == BTN_ALL) {
					SkrzynieManager.requestAll(sp, SkrzyniaMenu.this);
				} else if (index == BTN_NOW) {
					SkrzynieManager.request(sp, SkrzyniaMenu.this, false);
				} else if (index == BTN_ANIM) {
					SkrzynieManager.request(sp, SkrzyniaMenu.this, true);
				} else if (index == BTN_CLOSE) {
					sp.closeContainer();
				}
			}
			return false;
		}
	}

	public SkrzyniaMenu(int id, Inventory inventory, SimpleContainer box, Crates.Crate crate, BlockPos pos) {
		super(MenuType.GENERIC_9x6, id);
		this.box = box;
		this.crate = crate;
		this.pos = pos;
		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new BoxSlot(box, col + row * 9, 8 + col * 18, 18 + row * 18));
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, 8 + col * 18, 198));
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
