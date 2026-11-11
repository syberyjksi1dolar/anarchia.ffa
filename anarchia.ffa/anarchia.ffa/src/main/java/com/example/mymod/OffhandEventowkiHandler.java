package com.example.mymod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Eventówki działające, gdy trzymasz je w drugiej ręce:
 *  - Róża Kupidyna: Regeneracja II, Odporność I i dodatkowe serca (bez cooldownu),
 *  - Lizak: rozmiar 1 bloka (wysokość około 1) i Siła I.
 */
public final class OffhandEventowkiHandler {
	private static final double LIZAK_SCALE = 1.0 / 1.8; // gracz ma 1,8 bloku wysokości
	private static final int REFRESH_TICKS = 10;
	private static final int DURATION_TICKS = 60;

	private OffhandEventowkiHandler() {
	}

	private static void quiet(LivingEntity entity, String id, int amplifier) {
		entity.addEffect(new MobEffectInstance(EffectUtil.get(id), DURATION_TICKS, amplifier, true, false, true));
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % REFRESH_TICKS != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.getOffhandItem().is(ModItems.ROZA_KUPIDYNA)) {
					quiet(player, "regeneration", 1);  // Regeneracja II
					quiet(player, "resistance", 0);    // Odporność I
					quiet(player, "health_boost", 2);  // dodatkowe serca
				}

				AttributeInstance scale = player.getAttribute(Attributes.SCALE);
				if (player.getOffhandItem().is(ModItems.LIZAK)) {
					quiet(player, "strength", 0);      // Siła I
					if (scale != null && Math.abs(scale.getBaseValue() - LIZAK_SCALE) > 1.0E-6) {
						scale.setBaseValue(LIZAK_SCALE);
					}
				} else if (scale != null && Math.abs(scale.getBaseValue() - LIZAK_SCALE) < 1.0E-6) {
					scale.setBaseValue(1.0);
				}
			}
		});
	}
}
