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
 * Cudowna Latarnia: po postawieniu daje stawiającemu Siłę II (10 s), Odporność II (20 s) i Regenerację I (20 s).
 * Kiedy efekty się kończą (20 s), latarnia znika. Wydobywa się jak obsydian.
 */
public class CudownaLatarniaBlock extends Block {
	public static final int COOLDOWN_TICKS = 90 * 20;
	public static final int LIFETIME_TICKS = 20 * 20;

	public CudownaLatarniaBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel serverLevel) {
			LatarniaManager.register(serverLevel, pos);
			if (placer instanceof Player player) {
				EffectUtil.give(player, "strength", 10 * 20, 1);     // Siła II
				EffectUtil.give(player, "resistance", 20 * 20, 1);   // Odporność II
				EffectUtil.give(player, "regeneration", 20 * 20, 0); // Regeneracja I
			}
		}
	}
}
