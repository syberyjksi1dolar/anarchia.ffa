package com.example.mymod;

import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Kostiumy to jeden przedmiot (zakładany na klatę). Po założeniu ten handler sam ubiera resztę
 * kostiumu (głowa, spodnie, buty), a po zdjęciu je zabiera. Zwykłe elementy zbroi zajmujące
 * te miejsca wracają do ekwipunku (lub wypadają, gdy nie ma miejsca).
 */
public final class KostiumDresser {
	private record Costume(Item chest, Item head, Item legs, Item feet) {
		boolean isHidden(ItemStack s) {
			return s.is(head) || s.is(legs) || s.is(feet);
		}
	}

	private static final List<Costume> COSTUMES = List.of(
			new Costume(ModItems.SILACZ_KLATA, ModItems.SILACZ_HELM, ModItems.SILACZ_SPODNIE, ModItems.SILACZ_BUTY),
			new Costume(ModItems.DOLAR_KLATA, ModItems.DOLAR_HELM, ModItems.DOLAR_SPODNIE, ModItems.DOLAR_BUTY));

	private KostiumDresser() {
	}

	private static void fit(ServerPlayer p, EquipmentSlot slot, Item piece) {
		ItemStack cur = p.getItemBySlot(slot);
		if (cur.is(piece)) {
			return;
		}
		if (!cur.isEmpty()) {
			ItemStack back = cur.copy();
			p.setItemSlot(slot, ItemStack.EMPTY);
			if (!p.getInventory().add(back)) {
				p.drop(back, false);
			}
		}
		p.setItemSlot(slot, new ItemStack(piece));
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (!p.isAlive()) {
					continue;
				}
				ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
				for (Costume c : COSTUMES) {
					boolean worn = chest.is(c.chest());
					if (worn) {
						fit(p, EquipmentSlot.HEAD, c.head());
						fit(p, EquipmentSlot.LEGS, c.legs());
						fit(p, EquipmentSlot.FEET, c.feet());
					} else {
						// Kostium zdjęty: znikają jego ukryte części z każdego miejsca.
						for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
							if (c.isHidden(p.getItemBySlot(slot))) {
								p.setItemSlot(slot, ItemStack.EMPTY);
							}
						}
					}
					// Ukryte części nie mogą zostać w zwykłym ekwipunku.
					for (int i = 0; i < 36; i++) {
						if (c.isHidden(p.getInventory().getItem(i))) {
							p.getInventory().setItem(i, ItemStack.EMPTY);
						}
					}
				}
			}
		});
	}
}
