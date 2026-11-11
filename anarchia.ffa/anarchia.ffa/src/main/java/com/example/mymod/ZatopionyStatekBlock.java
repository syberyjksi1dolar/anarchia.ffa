package com.example.mymod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Zatopiony Statek: po postawieniu przez 10 sekund osłabia wszystkich dookoła (oprócz tego, kto go postawił).
 * Cooldown stawiania: 1 minuta 30 sekund.
 * Sama logika efektów jest w ZatopionyStatekManager.
 */
public class ZatopionyStatekBlock extends Block {
	/** Cooldown stawiania: 1 minuta 30 sekund. */
	public static final int PLACE_COOLDOWN_TICKS = 90 * 20;

	public ZatopionyStatekBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel serverLevel) {
			ZatopionyStatekManager.register(serverLevel, pos, placer == null ? null : placer.getUUID());
		}
	}
}
