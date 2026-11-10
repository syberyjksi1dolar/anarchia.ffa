package com.example.mymod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Różdżka Iluzjonisty: prawy klik = niewidzialność i klon, lewy klik = szczęki Evokera (patrz RozdzkaIluzjonistyManager). */
public class RozdzkaIluzjonistyItem extends Item {
	public RozdzkaIluzjonistyItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			RozdzkaIluzjonistyManager.markRightClick(serverPlayer);
			if (!player.getCooldowns().isOnCooldown(stack)) {
				RozdzkaIluzjonistyManager.invisibility(serverLevel, serverPlayer);
				player.getCooldowns().addCooldown(stack, RozdzkaIluzjonistyManager.RIGHT_COOLDOWN_TICKS);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
