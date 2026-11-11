package com.example.mymod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Wędka Nielota: złapany byt nie może latać ani używać elytry, dopóki jest na haczyku,
 * a po uwolnieniu (np. zmianie przedmiotu) zakaz trwa jeszcze 5 sekund.
 */
public final class WedkaNielotaManager {
	public static final int GROUNDED_TICKS = 5 * 20;

	private static final Map<LivingEntity, Integer> GROUNDED = new HashMap<>();

	private WedkaNielotaManager() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();

			// Kto ma zarzuconą naszą wędkę z kimś na haczyku, ten odnawia zakaz latania.
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				var hook = player.fishing;
				if (hook == null || hook.isRemoved()) {
					continue;
				}
				boolean ours = player.getMainHandItem().is(ModItems.WEDKA_NIELOTA)
						|| player.getOffhandItem().is(ModItems.WEDKA_NIELOTA);
				if (ours && hook.getHookedIn() instanceof LivingEntity target) {
					GROUNDED.put(target, now + GROUNDED_TICKS);
				}
			}

			Iterator<Map.Entry<LivingEntity, Integer>> it = GROUNDED.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<LivingEntity, Integer> e = it.next();
				LivingEntity target = e.getKey();
				if (target.isRemoved() || now >= e.getValue()) {
					it.remove();
					continue;
				}
				if (target.isFallFlying()) {
					target.stopFallFlying(); // koniec lotu elytrą
				}
				if (target instanceof ServerPlayer p && p.getAbilities().flying
						&& !p.getAbilities().instabuild && !p.isSpectator()) {
					p.getAbilities().flying = false; // koniec latania z uprawnieniem
					p.onUpdateAbilities();
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> GROUNDED.clear());
	}
}
