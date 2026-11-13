package com.example.mymod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Działko: twardy blok (jak obsydian), który sam strzela strzałami w najbliższego byta. */
public class DzialkoBlock extends Block {
	public DzialkoBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof net.minecraft.server.level.ServerLevel sl && placer != null) {
			DzialkoManager.add(sl, pos, placer.getUUID());
		}
	}
}
