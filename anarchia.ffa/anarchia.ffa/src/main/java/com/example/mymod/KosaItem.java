package com.example.mymod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Kosa: prawy klik zamyka ciebie i byt przed tobą w klatce na 15 s (patrz KosaKlatkaManager). Cooldown 60 s. */
public class KosaItem extends Item {
	public static final int COOLDOWN_TICKS = 60 * 20;

	public KosaItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			if (KosaKlatkaManager.tryCage(serverLevel, serverPlayer)) {
				player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
