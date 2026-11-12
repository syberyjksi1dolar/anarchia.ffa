package com.example.mymod;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Piernik: prawy klik daje Pośpiech X na 10 sekund. Cooldown 2 minuty. */
public class PiernikItem extends Item {
	public PiernikItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (!level.isClientSide()) {
			EffectUtil.give(player, "haste", 10 * 20, 9); // Haste X
			player.getCooldowns().addCooldown(stack, 120 * 20);
		}
		return InteractionResult.SUCCESS;
	}
}
