package com.example.mymod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Rzucane przedmioty (Kulka Bałwana, Perełka Zamianka): wyglądają jak śnieżka z ikoną przedmiotu,
 * a po zderzeniu sprawdzamy, w jakiego byta trafiły.
 */
public final class ThrowTracker {
	public interface OnHit {
		void hit(ServerPlayer owner, LivingEntity target);
	}

	private static final class Flight {
		final ServerLevel level;
		final Entity projectile;
		final UUID owner;
		final OnHit callback;
		Vec3 last;
		Vec3 velocity;

		Flight(ServerLevel level, Entity projectile, UUID owner, OnHit callback) {
			this.level = level;
			this.projectile = projectile;
			this.owner = owner;
			this.callback = callback;
			this.last = projectile.position();
			this.velocity = projectile.getDeltaMovement();
		}
	}

	private static final List<Flight> FLIGHTS = new ArrayList<>();

	private ThrowTracker() {
	}

	public static void throwItem(ServerPlayer player, ItemStack icon, float speed, OnHit callback) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		var ball = EntityType.SNOWBALL.create(level, EntitySpawnReason.TRIGGERED);
		if (ball == null) {
			return;
		}
		ball.setItem(icon.copyWithCount(1));
		ball.setOwner(player);
		ball.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
		ball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, speed, 1.0F);
		level.addFreshEntity(ball);
		FLIGHTS.add(new Flight(level, ball, player.getUUID(), callback));
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Flight> it = FLIGHTS.iterator();
			while (it.hasNext()) {
				Flight f = it.next();
				if (!f.projectile.isRemoved()) {
					f.last = f.projectile.position();
					f.velocity = f.projectile.getDeltaMovement();
					continue;
				}
				it.remove();
				ServerPlayer owner = server.getPlayerList().getPlayer(f.owner);
				if (owner == null) {
					continue;
				}
				Vec3 impact = f.last.add(f.velocity);
				LivingEntity best = null;
				double bestDist = Double.MAX_VALUE;
				for (LivingEntity e : f.level.getEntitiesOfClass(LivingEntity.class,
						new net.minecraft.world.phys.AABB(impact, impact).inflate(1.1),
						x -> x != owner && Util13.targetable(x))) {
					double d = e.getBoundingBox().getCenter().distanceToSqr(impact);
					if (d < bestDist) {
						bestDist = d;
						best = e;
					}
				}
				if (best != null) {
					f.callback.hit(owner, best);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> FLIGHTS.clear());
	}
}
