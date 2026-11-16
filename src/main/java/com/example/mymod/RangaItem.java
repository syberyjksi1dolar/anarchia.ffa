package com.example.mymod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Przedmiot rangi: PPM aktywuje rangę na graczu.
 * Przedmiot jest zużywany tylko wtedy, gdy ranga została faktycznie aktywowana.
 */
public final class RangaItem extends Item {
	private final String rank;

	public RangaItem(String rank, Properties properties) {
		super(properties);
		this.rank = rank;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof net.minecraft.server.level.ServerLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		if (RangiManager.activateFromItem(serverPlayer, rank)) {
			player.getItemInHand(hand).shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
