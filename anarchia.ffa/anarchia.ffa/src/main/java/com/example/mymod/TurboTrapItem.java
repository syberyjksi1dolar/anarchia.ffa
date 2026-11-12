package com.example.mymod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turbo Trap: prawy klik w blok wykopuje dół 3 x 1 x 10 (szerokość, głębokość, wysokość)
 * z jednym ostrym naciekiem na dnie. Cooldown 60 s.
 */
public class TurboTrapItem extends Item {
	public static final int COOLDOWN_TICKS = 60 * 20;
	public static final int DEPTH = 10;

	public TurboTrapItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		if (player != null && player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos top = context.getClickedPos();
		Direction facing = context.getHorizontalDirection();
		boolean alongX = facing == Direction.NORTH || facing == Direction.SOUTH; // szerokość 3 w poprzek kierunku patrzenia

		for (int w = -1; w <= 1; w++) {
			for (int d = 0; d < DEPTH; d++) {
				BlockPos pos = alongX ? top.offset(w, -d, 0) : top.offset(0, -d, w);
				BlockState old = serverLevel.getBlockState(pos);
				if (old.isAir() || serverLevel.getBlockEntity(pos) != null || old.getDestroySpeed(serverLevel, pos) < 0) {
					continue;
				}
				serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		// Ostry naciek na dnie, na środku.
		BlockPos bottom = top.below(DEPTH - 1);
		if (serverLevel.getBlockState(bottom).isAir()) {
			serverLevel.setBlock(bottom, Blocks.POINTED_DRIPSTONE.defaultBlockState(), Block.UPDATE_ALL);
		}
		if (player != null) {
			player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		}
		return InteractionResult.SUCCESS;
	}
}
