package com.example.mymod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * Wzmocniona Elytra: w locie ładuje się licznik 0-100% (im dłużej lecisz, tym szybciej).
 * Przy 100% wlecenie w byta zadaje 10 HP (20 punktów) i zeruje licznik. Po lądowaniu licznik się zeruje.
 */
public final class WzmocnionaElytraHandler {
	public static final float DAMAGE = 20.0F;

	private static final class State {
		float charge;
		int flightTicks;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private WzmocnionaElytraHandler() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tickPlayer(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> STATES.clear());
	}

	private static void tickPlayer(ServerPlayer player) {
		boolean wearing = player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.WZMOCNIONA_ELYTRA);
		if (!wearing || !player.isFallFlying()) {
			State old = STATES.remove(player.getUUID());
			if (old != null && old.charge > 0) {
				player.sendSystemMessage(Component.literal(""), true);
			}
			return;
		}

		State s = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		s.flightTicks++;
		if (s.charge < 100.0F) {
			s.charge = Math.min(100.0F, s.charge + 0.1F + s.flightTicks * 0.0006F);
		}
		if (s.flightTicks % 4 == 0) {
			player.sendSystemMessage(Component.literal("Ładunek: " + (int) s.charge + "%"), true);
		}

		if (s.charge >= 100.0F && player.level() instanceof ServerLevel level) {
			List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class,
					player.getBoundingBox().inflate(0.4),
					e -> e != player && e.isAlive() && !e.isSpectator());
			if (!hit.isEmpty()) {
				for (LivingEntity target : hit) {
					target.hurtServer(level, level.damageSources().playerAttack(player), DAMAGE);
				}
				s.charge = 0.0F;
				s.flightTicks = 0;
			}
		}
	}
}
