package com.example.mymod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Kostiumy to jeden przedmiot (zakładany na klatę). Po założeniu ten handler sam ubiera resztę
 * kostiumu (głowa, spodnie, buty). Zwykłą zbroję można założyć na każdą część; gdy założysz zwykły
 * napierśnik, klata kostiumu zostaje w ekwipunku, a kostium dalej działa (widać napierśnik).
 * Zdjęcie napierśnika zakłada klatę kostiumu z powrotem.
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

	/** Aktywny kostium gracza (klata kostiumu na ciele lub w ekwipunku pod zwykłym napierśnikiem). */
	private static final Map<UUID, Item> ACTIVE = new HashMap<>();
	/** Klata schowana na czas śmierci. */
	private static final Map<UUID, ItemStack> STASH = new HashMap<>();
	/** Ile ticków klata była poza ekwipunkiem (np. na kursorze). */
	private static final Map<UUID, Integer> MISSING = new HashMap<>();

	private KostiumDresser() {
	}

	/** Czy gracz ma aktywny kostium z daną klatą (także gdy na wierzchu jest zwykły napierśnik). */
	public static boolean isActive(Player p, Item chestItem) {
		return p.getItemBySlot(EquipmentSlot.CHEST).is(chestItem) || ACTIVE.get(p.getUUID()) == chestItem;
	}

	private static int findInInventory(ServerPlayer p, Item item) {
		for (int i = 0; i < 36; i++) {
			if (p.getInventory().getItem(i).is(item)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean carried(ServerPlayer p, Item item) {
		return p.containerMenu != null && p.containerMenu.getCarried().is(item);
	}

	private static void fit(ServerPlayer p, EquipmentSlot slot, Item piece) {
		ItemStack cur = p.getItemBySlot(slot);
		if (cur.is(piece) || !cur.isEmpty()) {
			return; // zwykła zbroja na wierzchu zostaje i jest widoczna
		}
		p.setItemSlot(slot, new ItemStack(piece));
	}

	public static void initialize() {
		// Śmierć: schowaj klatę z ekwipunku (trzymaną pod napierśnikiem), żeby nie wypadła.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && ACTIVE.containsKey(p.getUUID()) && !STASH.containsKey(p.getUUID())) {
				Item chest = ACTIVE.get(p.getUUID());
				int idx = findInInventory(p, chest);
				if (idx >= 0 && !p.getItemBySlot(EquipmentSlot.CHEST).is(chest)) {
					STASH.put(p.getUUID(), p.getInventory().getItem(idx).copy());
					p.getInventory().setItem(idx, ItemStack.EMPTY);
				}
			}
			return true;
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			ItemStack stash = STASH.remove(oldPlayer.getUUID());
			if (stash != null && newPlayer.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
				newPlayer.setItemSlot(EquipmentSlot.CHEST, stash);
			} else if (stash != null && !newPlayer.getInventory().add(stash)) {
				newPlayer.drop(stash, false);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				UUID id = p.getUUID();
				if (!p.isAlive()) {
					continue;
				}
				// Totem uratował gracza: klata wraca do ekwipunku.
				ItemStack stash = STASH.remove(id);
				if (stash != null && !p.getInventory().add(stash)) {
					p.drop(stash, false);
				}

				ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
				Item active = null;
				for (Costume c : COSTUMES) {
					if (chest.is(c.chest())) {
						active = c.chest();
						MISSING.remove(id);
					}
				}
				if (active == null) {
					Item prev = ACTIVE.get(id);
					if (prev != null) {
						int idx = findInInventory(p, prev);
						if (idx >= 0 && chest.isEmpty()) {
							// Zdjęto napierśnik: klata kostiumu wraca na ciało.
							p.setItemSlot(EquipmentSlot.CHEST, p.getInventory().getItem(idx).copy());
							p.getInventory().setItem(idx, ItemStack.EMPTY);
							active = prev;
							MISSING.remove(id);
						} else if (idx >= 0 || carried(p, prev) || p.getOffhandItem().is(prev)) {
							active = prev; // klata czeka w ekwipunku pod zwykłym napierśnikiem
							MISSING.remove(id);
						} else if (MISSING.merge(id, 1, Integer::sum) < 40) {
							active = prev; // krótka tolerancja na przekładanie przedmiotów
						}
					}
				}
				if (active == null) {
					ACTIVE.remove(id);
				} else {
					ACTIVE.put(id, active);
				}

				for (Costume c : COSTUMES) {
					if (c.chest() == active) {
						fit(p, EquipmentSlot.HEAD, c.head());
						fit(p, EquipmentSlot.LEGS, c.legs());
						fit(p, EquipmentSlot.FEET, c.feet());
					} else {
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
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ACTIVE.clear();
			STASH.clear();
			MISSING.clear();
		});
	}
}
