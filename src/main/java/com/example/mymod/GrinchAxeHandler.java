package com.example.mymod;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Efekt Siekiery Grincha: po uderzeniu zabiera 30% maksymalnego HP trafionego
 * i uderza w niego piorun. Cooldown 50 sekund (w tym czasie siekiera bije normalnie).
 */
public final class GrinchAxeHandler {
	public static final int COOLDOWN_TICKS = 50 * 20; // 50 sekund
	public static final float HEALTH_FRACTION = 0.30F;

	private GrinchAxeHandler() {
	}

	public static void initialize() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> onAttack(player, level, hand, entity));
	}

	private static InteractionResult onAttack(Player player, Level level, InteractionHand hand, Entity entity) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.PASS;
		}

		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(ModItems.GRINCH_AXE)) {
			return InteractionResult.PASS;
		}

		// Siekiera się nie psuje.
		stack.setDamageValue(0);

		if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.isSpectator()) {
			return InteractionResult.PASS;
		}
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.PASS;
		}

		// 30% maksymalnego HP, obrażenia magiczne (pancerz ich nie zmniejsza), zabójstwo liczy się graczowi.
		float amount = target.getMaxHealth() * HEALTH_FRACTION;
		target.hurtServer(serverLevel, serverLevel.damageSources().indirectMagic(player, player), amount);

		// Piorun tylko wizualny: sam nie rani ani nie podpala, więc nie trafi też użytkownika stojącego obok.
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.setPos(target.getX(), target.getY(), target.getZ());
			bolt.setVisualOnly(true);
			serverLevel.addFreshEntity(bolt);
		}

		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		return InteractionResult.PASS; // normalne uderzenie siekierą nadal się odbywa
	}
}
