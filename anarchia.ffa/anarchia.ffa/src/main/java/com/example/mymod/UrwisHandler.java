package com.example.mymod;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * Kostium Małego Urwisa: stała Szybkość II, 50% odporności na upadek i +10% dodatkowych obrażeń.
 * (Kradzież 3% majątku po zabiciu gracza wymaga systemu ekonomii, którego mod nie ma - pominięta.)
 */
public final class UrwisHandler {
	private static boolean inside = false;

	private UrwisHandler() {
	}

	public static boolean wears(Player p) {
		return KostiumDresser.isActive(p, ModItems.URWIS_KLATA);
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (inside || blocked || damageTaken <= 0.0F || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			// 50% odporności na upadek: zwracamy połowę obrażeń od upadku.
			if (entity instanceof Player victim && victim.isAlive() && wears(victim) && source.is(DamageTypeTags.IS_FALL)) {
				victim.heal(damageTaken * 0.5F);
			}
			// +10% dodatkowych obrażeń w walce.
			if (source.getDirectEntity() instanceof Player attacker && wears(attacker) && entity != attacker && entity.isAlive()) {
				inside = true;
				try {
					entity.invulnerableTime = 0;
					entity.hurtServer(level, source, damageTaken * 0.10F);
				} finally {
					inside = false;
				}
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 != 0) {
				return;
			}
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.isAlive() && wears(p)) {
					p.addEffect(new MobEffectInstance(EffectUtil.get("speed"), 40, 1, true, false, true));
				}
			}
		});
	}
}
