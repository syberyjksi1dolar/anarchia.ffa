package com.example.mymod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Jednorożec (osiodłany koń) z Rogu Jednorożca: siedzisz na nim 10 sekund.
 * Gdy wjedziesz w blok, rozwala obszar 4 x 4 x 4 przed sobą.
 */
public final class JednorozecManager {
	public static final int RIDE_TICKS = 10 * 20;

	private static final class Ride {
		final ServerLevel level;
		final LivingEntity horse;
		final UUID rider;
		final long expiresAt;
		long lastBreak;

		Ride(ServerLevel level, LivingEntity horse, UUID rider, long expiresAt) {
			this.level = level;
			this.horse = horse;
			this.rider = rider;
			this.expiresAt = expiresAt;
		}
	}

	private static final List<Ride> RIDES = new ArrayList<>();

	private JednorozecManager() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick(server));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Ride r : RIDES) {
				r.horse.discard();
			}
			RIDES.clear();
		});
	}

	public static void summon(ServerLevel level, ServerPlayer player) {
		var horse = EntityType.HORSE.create(level, EntitySpawnReason.TRIGGERED);
		if (horse == null) {
			return;
		}
		horse.setPos(player.getX(), player.getY(), player.getZ());
		horse.setYRot(player.getYRot());
		horse.setCustomName(Component.literal("Jednorożec"));
		horse.setTamed(true);
		try {
			horse.setItemSlot(EquipmentSlot.valueOf("SADDLE"), new ItemStack(Items.SADDLE));
		} catch (IllegalArgumentException e) {
			// brak slotu siodła w tej wersji
		}
		horse.addEffect(new MobEffectInstance(EffectUtil.get("speed"), RIDE_TICKS, 2, false, false, false));
		level.addFreshEntity(horse);
		player.startRiding(horse);
		RIDES.add(new Ride(level, horse, player.getUUID(), level.getGameTime() + RIDE_TICKS));
	}

	private static void tick(net.minecraft.server.MinecraftServer server) {
		Iterator<Ride> it = RIDES.iterator();
		while (it.hasNext()) {
			Ride r = it.next();
			if (r.horse.isRemoved()) {
				it.remove();
				continue;
			}
			ServerPlayer rider = server.getPlayerList().getPlayer(r.rider);
			if (r.level.getGameTime() >= r.expiresAt || rider == null) {
				r.horse.discard();
				it.remove();
				continue;
			}
			if (r.horse.horizontalCollision && r.level.getGameTime() - r.lastBreak >= 5) {
				r.lastBreak = r.level.getGameTime();
				smash(r.level, r.horse, rider);
			}
		}
	}

	/** Rozwala obszar 4 x 4 x 4 tuż przed jednorożcem. */
	private static void smash(ServerLevel level, LivingEntity horse, ServerPlayer rider) {
		double rad = Math.toRadians(horse.getYRot());
		int fx = (int) Math.round(-Math.sin(rad) * 2.0);
		int fz = (int) Math.round(Math.cos(rad) * 2.0);
		BlockPos center = BlockPos.containing(horse.getX() + fx, horse.getY(), horse.getZ() + fz);
		for (int dx = -1; dx <= 2; dx++) {
			for (int dy = 0; dy < 4; dy++) {
				for (int dz = -1; dz <= 2; dz++) {
					BlockPos pos = center.offset(dx, dy, dz);
					BlockState state = level.getBlockState(pos);
					if (state.isAir() || state.getDestroySpeed(level, pos) < 0) {
						continue;
					}
					level.destroyBlock(pos, true, rider);
				}
			}
		}
	}
}
