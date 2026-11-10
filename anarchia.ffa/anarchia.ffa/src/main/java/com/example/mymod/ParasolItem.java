package com.example.mymod;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Parasol: prawy klik odpycha wszystkie byty w promieniu 6 bloków (poza tobą). Cooldown 10 sekund.
 */
public class ParasolItem extends Item {
	public static final int COOLDOWN_TICKS = 10 * 20;
	public static final double RADIUS = 6.0;
	public static final double PUSH = 1.8;
	public static final double LIFT = 0.5;

	public ParasolItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}

		AABB area = player.getBoundingBox().inflate(RADIUS);
		List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
				e -> e != player && e.isAlive() && !e.isSpectator());
		for (LivingEntity e : victims) {
			Vec3 away = e.position().subtract(player.position());
			double len = Math.sqrt(away.x * away.x + away.z * away.z);
			if (len > RADIUS) {
				continue;
			}
			double dx = len < 0.01 ? 1.0 : away.x / len;
			double dz = len < 0.01 ? 0.0 : away.z / len;
			e.setDeltaMovement(dx * PUSH, LIFT, dz * PUSH);
			e.hurtMarked = true; // wysyła nową prędkość do gracza
		}

		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1); // zużywalny: 64, 63, 62...
		}
		return InteractionResult.SUCCESS;
	}
}
