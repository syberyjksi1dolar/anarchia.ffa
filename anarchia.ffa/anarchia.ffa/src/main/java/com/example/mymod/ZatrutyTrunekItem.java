package com.example.mymod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Zatruty Trunek i Rozgotowana Kukurydza (działają tak samo): rzucane jak mikstura.
 * W miejscu upadku powstaje obszar 7 x 8 bloków na 15 s, w którym byty co sekundę tracą 2 HP
 * i dostają Nudności na 20 s. Cooldown 70 s.
 */
public class ZatrutyTrunekItem extends Item {
	public static final int COOLDOWN_TICKS = 70 * 20;
	public static final int ZONE_TICKS = 15 * 20;

	private static final class Flight {
		final Entity potion;
		final UUID owner;
		Vec3 last;

		Flight(Entity potion, UUID owner) {
			this.potion = potion;
			this.owner = owner;
			this.last = potion.position();
		}
	}

	private static final class Zone {
		final ServerLevel level;
		final UUID owner;
		final Vec3 center;
		final long endTick;
		long nextDamage;

		Zone(ServerLevel level, UUID owner, Vec3 center, long endTick) {
			this.level = level;
			this.owner = owner;
			this.center = center;
			this.endTick = endTick;
			this.nextDamage = level.getGameTime();
		}
	}

	private static final List<Flight> FLIGHTS = new ArrayList<>();
	private static final List<Zone> ZONES = new ArrayList<>();

	public ZatrutyTrunekItem(Properties properties) {
		super(properties);
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Flight> fi = FLIGHTS.iterator();
			while (fi.hasNext()) {
				Flight f = fi.next();
				if (f.potion.isRemoved()) {
					if (f.potion.level() instanceof ServerLevel level) {
						ZONES.add(new Zone(level, f.owner, f.last, level.getGameTime() + ZONE_TICKS));
					}
					fi.remove();
				} else {
					f.last = f.potion.position();
				}
			}

			Iterator<Zone> zi = ZONES.iterator();
			while (zi.hasNext()) {
				Zone z = zi.next();
				long now = z.level.getGameTime();
				if (now >= z.endTick) {
					zi.remove();
					continue;
				}
				if (now % 4 == 0) {
					z.level.sendParticles(net.minecraft.core.particles.PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F), z.center.x, z.center.y + 0.5, z.center.z,
							8, 3.3, 0.4, 3.8, 0.0);
				}
				if (now >= z.nextDamage) {
					z.nextDamage = now + 20;
					AABB box = new AABB(z.center.x - 3.5, z.center.y - 1.0, z.center.z - 4.0,
							z.center.x + 3.5, z.center.y + 3.0, z.center.z + 4.0);
					for (LivingEntity e : z.level.getEntitiesOfClass(LivingEntity.class, box,
							x -> x.isAlive() && !x.isSpectator() && !x.getUUID().equals(z.owner))) {
						e.hurtServer(z.level, z.level.damageSources().magic(), 2.0F); // 1 serce
						EffectUtil.give(e, "nausea", 20 * 20, 0);
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			FLIGHTS.clear();
			ZONES.clear();
		});
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel) {
			var potion = EntityType.SPLASH_POTION.create(serverLevel, EntitySpawnReason.TRIGGERED);
			if (potion != null) {
				potion.setItem(new ItemStack(Items.SPLASH_POTION));
				potion.setOwner(player);
				potion.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
				potion.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.5F, 1.0F);
				serverLevel.addFreshEntity(potion);
				FLIGHTS.add(new Flight(potion, player.getUUID()));
			}
			player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
