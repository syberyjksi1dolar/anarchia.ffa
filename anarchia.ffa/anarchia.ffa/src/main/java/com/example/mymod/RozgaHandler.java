package com.example.mymod;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Rózga: trafiony byt dostaje Odrzut V (mocno odlatuje), a uderzający gracz dostaje Powolne opadanie na 5 sekund. Bez cooldownu. */
public final class RozgaHandler {
	public static final double KNOCKBACK = 2.5; // jak Odrzut V
	public static final int SLOW_FALL_TICKS = 5 * 20;

	private RozgaHandler() {
	}

	public static void initialize() {
		// Po uderzeniu (po zwykłym odrzucie z gry) odrzucamy jeszcze mocniej.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (blocked || !(source.getDirectEntity() instanceof Player player)
					|| !player.getMainHandItem().is(ModItems.ROZGA)) {
				return;
			}
			Vec3 away = entity.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() < 1.0E-4) {
				return;
			}
			away = away.normalize();
			entity.setDeltaMovement(away.x * KNOCKBACK, 0.5, away.z * KNOCKBACK);
			entity.hurtMarked = true;
			EffectUtil.give(player, "slow_falling", SLOW_FALL_TICKS, 0); // Powolne opadanie dostaje ten, kto uderzył
		});
	}
}
