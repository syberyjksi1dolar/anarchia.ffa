package com.example.mymod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * Fajerwerka Wyrzutni: trzymana w ręce, przytrzymanie spacji wystrzeliwuje gracza w górę (max 8 bloków lotu).
 * Pasek pokazuje pozostałe paliwo; odnawia się po wylądowaniu.
 */
public class FajerwerkaWyrzutniItem extends Item {
	public static final double MAX_HEIGHT = 8.0;
	public static final double RISE_PER_TICK = 0.5;

	private static final Map<UUID, Double> USED = new HashMap<>();

	public FajerwerkaWyrzutniItem(Properties properties) {
		super(properties);
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				UUID id = p.getUUID();
				boolean holds = p.getMainHandItem().is(ModItems.FAJERWERKA_WYRZUTNI)
						|| p.getOffhandItem().is(ModItems.FAJERWERKA_WYRZUTNI);
				if (!holds) {
					USED.remove(id);
					continue;
				}
				boolean jump = p.getLastClientInput().jump();
				double used = USED.getOrDefault(id, 0.0);
				if (p.onGround() && !jump) {
					used = 0.0;
				}
				if (jump && used < MAX_HEIGHT) {
					p.setDeltaMovement(p.getDeltaMovement().x, RISE_PER_TICK, p.getDeltaMovement().z);
					p.hurtMarked = true;
					p.resetFallDistance();
					used += RISE_PER_TICK;
				}
				USED.put(id, used);
				double left = Math.max(0.0, 1.0 - used / MAX_HEIGHT);
				p.sendSystemMessage(Component.literal("Paliwo: " + Util13.bar(left, 8)), true);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> USED.clear());
	}
}
