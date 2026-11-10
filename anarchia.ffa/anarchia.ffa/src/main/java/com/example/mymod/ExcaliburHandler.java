package com.example.mymod;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Excalibur: Ostrość 7 (+4 obrażeń) oraz +58% dodatkowych obrażeń. */
public final class ExcaliburHandler {
	private static final float SHARPNESS_7 = 0.5F * 7 + 0.5F;
	private static final float BONUS_PERCENT = 0.58F;
	private static boolean inside = false;

	private ExcaliburHandler() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (inside || blocked || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			if (!(source.getDirectEntity() instanceof Player player) || !player.getMainHandItem().is(ModItems.EXCALIBUR)) {
				return;
			}
			float extra = SHARPNESS_7 + damageTaken * BONUS_PERCENT;
			inside = true;
			try {
				((LivingEntity) entity).invulnerableTime = 0;
				entity.hurtServer(level, source, extra);
			} finally {
				inside = false;
			}
		});
	}
}
