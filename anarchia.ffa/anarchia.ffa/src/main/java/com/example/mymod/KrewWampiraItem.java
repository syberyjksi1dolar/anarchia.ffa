package com.example.mymod;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Krew Wampira: prawy klik leczy do pełna, ale tylko gdy nie masz pełnego HP. Zużywalna, cooldown 10 s. */
public class KrewWampiraItem extends Item {
	public static final int COOLDOWN_TICKS = 10 * 20;

	public KrewWampiraItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack) || player.getHealth() >= player.getMaxHealth()) {
			return InteractionResult.FAIL;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		player.setHealth(player.getMaxHealth());
		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
