package com.example.mymod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Wędka Gwardiana: zamiast haczyka wystrzeliwuje Gwardiana na 6 bloków. Trafiony dostaje Powolne Wydobywanie. Bez cooldownu. */
public class WedkaGwardianaItem extends Item {
	public static final double RANGE = 6.0;
	public static final double STEP = 0.9;

	private static final class Shot {
		final ServerLevel level;
		final LivingEntity guardian;
		final UUID owner;
		final Vec3 dir;
		double travelled;

		Shot(ServerLevel level, LivingEntity guardian, UUID owner, Vec3 dir) {
			this.level = level;
			this.guardian = guardian;
			this.owner = owner;
			this.dir = dir;
		}
	}

	private static final List<Shot> SHOTS = new ArrayList<>();

	public WedkaGwardianaItem(Properties properties) {
		super(properties);
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Shot> it = SHOTS.iterator();
			while (it.hasNext()) {
				Shot s = it.next();
				if (s.guardian.isRemoved()) {
					it.remove();
					continue;
				}
				s.guardian.setPos(s.guardian.position().add(s.dir.scale(STEP)));
				s.travelled += STEP;
				var targets = s.level.getEntitiesOfClass(LivingEntity.class, s.guardian.getBoundingBox().inflate(0.7),
						e -> e != s.guardian && e.isAlive() && !e.isSpectator() && !e.getUUID().equals(s.owner));
				if (!targets.isEmpty()) {
					EffectUtil.give(targets.get(0), "mining_fatigue", 10 * 20, 2); // Powolne Wydobywanie III
					s.guardian.discard();
					it.remove();
				} else if (s.travelled >= RANGE) {
					s.guardian.discard();
					it.remove();
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Shot s : SHOTS) {
				s.guardian.discard();
			}
			SHOTS.clear();
		});
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level instanceof ServerLevel serverLevel) {
			var guardian = EntityType.GUARDIAN.create(serverLevel, EntitySpawnReason.TRIGGERED);
			if (guardian != null) {
				Vec3 dir = player.getLookAngle();
				guardian.setNoAi(true);
				guardian.setNoGravity(true);
				guardian.setInvulnerable(true);
				guardian.setPos(player.getX() + dir.x, player.getEyeY() - 0.4 + dir.y, player.getZ() + dir.z);
				guardian.setYRot(player.getYRot());
				serverLevel.addFreshEntity(guardian);
				SHOTS.add(new Shot(serverLevel, guardian, player.getUUID(), dir));
			}
		}
		return InteractionResult.SUCCESS;
	}
}
