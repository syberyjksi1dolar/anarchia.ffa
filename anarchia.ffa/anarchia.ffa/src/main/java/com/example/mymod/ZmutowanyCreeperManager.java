package com.example.mymod;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
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
	private static final Set<Creeper> CREEPERS = new HashSet<>();

	private ZmutowanyCreeperManager() {
	}

	public static void initialize() {
		ServerEntityEvents.ENTITY_LOAD.register((Entity entity, ServerLevel world) -> {
			if (entity instanceof Creeper creeper && creeper.getTags().contains(JajkoCreeperaItem.TAG)) {
				CREEPERS.add(creeper);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Creeper> it = CREEPERS.iterator();
			while (it.hasNext()) {
				Creeper creeper = it.next();
				if (creeper.isRemoved() || !(creeper.level() instanceof ServerLevel level)) {
					it.remove();
					continue;
				}
				if (creeper.getSwelling(1.0F) >= 0.75F) {
					explode(level, creeper);
					creeper.discard();
					it.remove();
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> CREEPERS.clear());
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
