package com.example.mymod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Złoty Bilet: prawy klik zużywa bilet i otwiera GUI z nagrodami (gracz wybiera jedną). */
public class ZlotyBiletItem extends Item {
	public ZlotyBiletItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack st = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (player instanceof ServerPlayer sp && ZlotyBilet.openRewards(sp)) {
			st.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
