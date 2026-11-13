package com.example.mymod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Kulka Bałwana: rzucana jak śnieżka; trafiony byt dostaje Spowolnienie I i Osłabienie I na 3 s. Bez cooldownu. */
public class KulkaBalwanaItem extends Item {
	public KulkaBalwanaItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = player.getItemInHand(hand);
		ThrowTracker.throwItem(sp, new ItemStack(Items.SNOWBALL), 1.5F, (owner, target) -> {
			EffectUtil.give(target, "slowness", 3 * 20, 0);
			EffectUtil.give(target, "weakness", 3 * 20, 0);
		});
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
