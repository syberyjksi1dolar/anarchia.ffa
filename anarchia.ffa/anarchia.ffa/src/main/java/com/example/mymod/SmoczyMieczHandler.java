package com.example.mymod;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Smoczy Miecz: Ostrość V (+3 obrażeń), Aspekt ognia II (podpalenie 8 s) i niezniszczalność. */
public final class SmoczyMieczHandler {
	private static final float SHARPNESS_5 = 0.5F * 5 + 0.5F; // 3.0
	private static final float FIRE_ASPECT_2_SECONDS = 8.0F;
	private static boolean inside = false;

	private SmoczyMieczHandler() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (inside || blocked || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			if (!(source.getDirectEntity() instanceof Player player) || !player.getMainHandItem().is(ModItems.SMOCZY_MIECZ)) {
				return;
			}
			inside = true;
			try {
				entity.igniteForSeconds(FIRE_ASPECT_2_SECONDS);
				entity.invulnerableTime = 0;
				entity.hurtServer(level, source, SHARPNESS_5);
			} finally {
				inside = false;
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				for (ItemStack s : new ItemStack[] { p.getMainHandItem(), p.getOffhandItem() }) {
					if (s.is(ModItems.SMOCZY_MIECZ) && s.isDamaged()) {
						s.setDamageValue(0);
					}
				}
			}
		});
	}
}
