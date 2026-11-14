package com.example.mymod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * GUI skrzyni (3 rzędy): nagrody w slotach od 0, a w ostatnim rzędzie przyciski:
 * 24 = pomarańczowy barwnik (otwórz od razu), 25 = zielony barwnik (animacja 5 s), 26 = bariera (zamknij).
 * Przedmiotów z GUI nie da się wyjąć.
 */
public class SkrzyniaMenu extends ChestMenu {
	public static final int BTN_NOW = 24;
	public static final int BTN_ANIM = 25;
	public static final int BTN_CLOSE = 26;

	final SimpleContainer box;
	final Crates.Crate crate;
	final BlockPos pos;
	boolean busy = false;

	public SkrzyniaMenu(int id, Inventory inventory, SimpleContainer box, Crates.Crate crate, BlockPos pos) {
		super(MenuType.GENERIC_9x3, id, inventory, box, 3);
		this.box = box;
		this.crate = crate;
		this.pos = pos;
	}

	@Override
	public void clicked(int slotId, int button, ClickType type, Player player) {
		if (slotId >= 0 && slotId < 27) {
			if (player instanceof ServerPlayer sp && !busy) {
				if (slotId == BTN_NOW) {
					SkrzynieManager.request(sp, this, false);
				} else if (slotId == BTN_ANIM) {
					SkrzynieManager.request(sp, this, true);
				} else if (slotId == BTN_CLOSE) {
					sp.closeContainer();
				}
			}
			return;
		}
		if (type == ClickType.QUICK_MOVE || type == ClickType.PICKUP_ALL || type == ClickType.QUICK_CRAFT) {
			return;
		}
		super.clicked(slotId, button, type, player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}
}
