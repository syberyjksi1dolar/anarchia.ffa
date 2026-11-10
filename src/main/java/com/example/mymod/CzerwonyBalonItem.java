package com.example.mymod;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Czerwony Balon: prawy klik unosi cię w powietrze na 10 sekund (Lewitacja II),
 * a potem opadasz miękko (Powolne opadanie), więc nie giniesz od upadku. Cooldown 30 sekund.
 */
public class CzerwonyBalonItem extends Item {
	public static final int COOLDOWN_TICKS = 30 * 20;
	public static final int LEVITATION_TICKS = 10 * 20;
	public static final int SLOW_FALL_TICKS = 25 * 20;

	public CzerwonyBalonItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		EffectUtil.give(player, "levitation", LEVITATION_TICKS, 1);   // Lewitacja II
		EffectUtil.give(player, "slow_falling", SLOW_FALL_TICKS, 0);  // miękkie lądowanie
		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
	}
}
