package com.example.mymod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;

/**
 * Wilk: użycie przywołuje oswojonego wilka, który atakuje najbliższego byta (nie właściciela).
 * Jeśli wilk zginie, jajko przepada; jeśli zabije byta, jajko wraca (raz na wilka).
 */
public class WilkItem extends Item {
	public static final double SEARCH_RADIUS = 12.0;

	private static final class Pack {
		final Entity wolf;
		final UUID owner;
		boolean returned = false;

		Pack(Entity wolf, UUID owner) {
			this.wolf = wolf;
			this.owner = owner;
		}
	}

	private static final Map<UUID, Pack> WOLVES = new HashMap<>();

	public WilkItem(Properties properties) {
		super(properties);
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			Entity killer = source.getEntity();
			if (killer != null && entity != killer) {
				Pack pack = WOLVES.get(killer.getUUID());
				if (pack != null && !pack.returned && entity.level() instanceof ServerLevel level) {
					pack.returned = true; // jedno jajko na wilka, bez powtórek za to samo zabójstwo
					ServerPlayer owner = level.getServer().getPlayerList().getPlayer(pack.owner);
					if (owner != null) {
						ItemStack egg = new ItemStack(ModItems.WILK);
						if (!owner.getInventory().add(egg)) {
							owner.drop(egg, false);
						}
					}
				}
			}
			return true;
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 != 0) {
				return;
			}
			Iterator<Map.Entry<UUID, Pack>> it = WOLVES.entrySet().iterator();
			while (it.hasNext()) {
				Pack pack = it.next().getValue();
				if (pack.wolf.isRemoved() || !pack.wolf.isAlive()
						|| !(pack.wolf instanceof net.minecraft.world.entity.Mob wolf)
						|| !(wolf.level() instanceof ServerLevel level)) {
					it.remove();
					continue;
				}
				LivingEntity best = null;
				double bestDist = Double.MAX_VALUE;
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, wolf.getBoundingBox().inflate(SEARCH_RADIUS),
						x -> x != wolf && !x.getUUID().equals(pack.owner) && Util13.targetable(x))) {
					double d = e.distanceToSqr(wolf);
					if (d < bestDist) {
						bestDist = d;
						best = e;
					}
				}
				wolf.setTarget(best);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> WOLVES.clear());
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		var wolf = EntityType.WOLF.create(serverLevel, EntitySpawnReason.TRIGGERED);
		if (wolf == null) {
			return InteractionResult.FAIL;
		}
		var look = player.getLookAngle();
		wolf.setPos(player.getX() + look.x * 1.5, player.getY(), player.getZ() + look.z * 1.5);
		wolf.tame(sp);
		serverLevel.addFreshEntity(wolf);
		WOLVES.put(wolf.getUUID(), new Pack(wolf, player.getUUID()));
		if (!player.getAbilities().instabuild) {
			player.getItemInHand(hand).shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
