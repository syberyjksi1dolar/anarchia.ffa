package com.example.mymod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * Menu 6-rzędowe sklepu: przekazuje do akcji informację, czy kliknięto z Shiftem
 * (kliknięcia w górną część nie przenoszą przedmiotów).
 */
public class SklepMenu extends RangiMenu {
	public interface Klik {
		void klik(ServerPlayer p, int slot, boolean shift);
	}

	private final SimpleContainer box;
	private final Klik klik;

	public SklepMenu(int id, Inventory inventory, SimpleContainer box, Klik klik) {
		super(id, inventory, box, 6, (p, i) -> {
		});
		this.box = box;
		this.klik = klik;
	}

	@Override
	public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
		// PICKUP = zwykły klik, QUICK_MOVE = shift-klik
		boolean isShift = clickType == net.minecraft.world.inventory.ClickType.QUICK_MOVE;
		boolean isNormal = clickType == net.minecraft.world.inventory.ClickType.PICKUP || isShift;

		if (slotId >= 0 && slotId < box.getContainerSize() && isNormal) {
			if (player instanceof ServerPlayer sp && !box.getItem(slotId).isEmpty()) {
				klik.klik(sp, slotId, isShift);
			}
			return;
		}
		super.clicked(slotId, button, clickType, player);
	}
}
