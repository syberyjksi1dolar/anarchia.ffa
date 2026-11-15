package com.example.mymod;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Kostium Pirata: +13% dodatkowych obrażeń, -5% obrony (otrzymujesz 5% więcej obrażeń),
 * po zabiciu gracza leczy 5 serc (10 HP), po zabiciu innego byta 2 serca (4 HP).
 */
public final class PiratHandler {
	private static boolean inside = false;

	private PiratHandler() {
	}

	public static boolean wears(Player p) {
		return KostiumDresser.isActive(p, ModItems.PIRAT_KLATA);
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (source.getEntity() instanceof Player killer && killer != entity && killer.isAlive() && wears(killer)) {
				killer.heal(entity instanceof Player ? 10.0F : 4.0F);
			}
			return true;
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (inside || blocked || damageTaken <= 0.0F || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			// -5% obrony: pirat dostaje 5% więcej obrażeń.
			if (entity instanceof Player victim && victim.isAlive() && wears(victim)) {
				inside = true;
				try {
					victim.invulnerableTime = 0;
					victim.hurtServer(level, source, damageTaken * 0.05F);
				} finally {
					inside = false;
				}
			}
			// +13% dodatkowych obrażeń w walce.
			if (source.getDirectEntity() instanceof Player attacker && wears(attacker) && entity != attacker && entity.isAlive()) {
				inside = true;
				try {
					entity.invulnerableTime = 0;
					entity.hurtServer(level, source, damageTaken * 0.13F);
				} finally {
					inside = false;
				}
			}
		});
	}
}
