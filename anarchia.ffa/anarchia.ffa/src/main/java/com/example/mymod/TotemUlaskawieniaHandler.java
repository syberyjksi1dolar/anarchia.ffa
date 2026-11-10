package com.example.mymod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Totem Ułaskawienia: trzymany w lewej ręce. Gdy umierasz, normalnie giniesz,
 * ale nie tracisz przedmiotów (wracają po odrodzeniu), a totem się zużywa.
 */
public final class TotemUlaskawieniaHandler {
	private static final Map<UUID, List<ItemStack>> SAVED = new HashMap<>();

	private TotemUlaskawieniaHandler() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && player.getOffhandItem().is(ModItems.TOTEM_ULASKAWIENIA)) {
				player.getOffhandItem().shrink(1); // totem się zużywa
				List<ItemStack> saved = new ArrayList<>();
				int size = player.getInventory().getContainerSize();
				for (int i = 0; i < size; i++) {
					saved.add(player.getInventory().getItem(i).copy());
					player.getInventory().setItem(i, ItemStack.EMPTY);
				}
				SAVED.put(player.getUUID(), saved);
			}
			return true; // śmierć dzieje się normalnie
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			List<ItemStack> saved = SAVED.remove(newPlayer.getUUID());
			if (saved == null) {
				return;
			}
			int size = Math.min(saved.size(), newPlayer.getInventory().getContainerSize());
			for (int i = 0; i < size; i++) {
				newPlayer.getInventory().setItem(i, saved.get(i));
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SAVED.clear());
	}
}
