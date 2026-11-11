package com.example.mymod;

import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;

/**
 * Zmutowany Creeper: zanim vanillowy wybuch zdąży zniszczyć bloki, sami robimy wybuch,
 * który nie rusza bloków, a każdemu w promieniu zadaje 50% jego maksymalnego HP. Potem creeper znika.
 * Lont jest o 30% krótszy niż u zwykłego creepera (próg 0.75 z 1.0 zamiast pełnego).
 */
public final class ZmutowanyCreeperManager {
	public static final double RADIUS = 4.0;

	private ZmutowanyCreeperManager() {
	}

	private static boolean isMutant(Creeper creeper) {
		return creeper.getCustomName() != null && "Zmutowany Creeper".equals(creeper.getCustomName().getString());
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!(player.level() instanceof ServerLevel level)) {
					continue;
				}
				List<Creeper> creepers = level.getEntitiesOfClass(Creeper.class,
						player.getBoundingBox().inflate(64.0), c -> c.isAlive() && isMutant(c));
				for (Creeper creeper : creepers) {
					if (creeper.getSwelling(1.0F) >= 0.75F) {
						explode(level, creeper);
						creeper.discard();
					}
				}
			}
		});
	}

	private static void explode(ServerLevel level, Creeper creeper) {
		AABB area = creeper.getBoundingBox().inflate(RADIUS);
		List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, area,
				e -> e != creeper && e.isAlive() && !e.isSpectator());
		for (LivingEntity victim : victims) {
			if (victim.distanceTo(creeper) <= RADIUS) {
				victim.hurtServer(level, level.damageSources().magic(), victim.getMaxHealth() * 0.5F);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, creeper.getX(), creeper.getY() + 0.5, creeper.getZ(),
				1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, creeper.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.5F, 0.8F);
	}
}
