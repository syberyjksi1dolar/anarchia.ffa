package com.example.mymod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bomba Maksima: prawy klik w blok wysadza obszar 5 x 5 (X i Z) i 3 bloki w dół (od klikniętego bloku).
 * Bez cooldownu, zużywalna (ubywa jedna sztuka, stackuje się do 64).
 */
public class BombaMaksimaItem extends Item {
	public static final int HALF = 2; // 5 bloków: środek +/- 2
	public static final int DEPTH = 3;

	public BombaMaksimaItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		Player player = context.getPlayer();
		BlockPos center = context.getClickedPos();

		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				for (int dy = 0; dy < DEPTH; dy++) {
					BlockPos pos = center.offset(dx, -dy, dz);
					BlockState state = serverLevel.getBlockState(pos);
					if (state.isAir() || state.getDestroySpeed(serverLevel, pos) < 0) {
						continue; // powietrze i bedrock zostają
					}
					serverLevel.destroyBlock(pos, true, player);
				}
			}
		}

		serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
				center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
		serverLevel.playSound(null, center, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 2.0F, 1.2F);

		if (player != null && !player.getAbilities().instabuild) {
			ItemStack stack = context.getItemInHand();
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
