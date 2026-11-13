package com.example.mymod;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Dynamit: prawy klik w blok bedrocka niszczy dokładnie ten jeden blok. Zużywa jedną sztukę, bez cooldownu. */
public class DynamitItem extends Item {
	public DynamitItem(Properties properties) {
		super(properties);
	}

	public static void initialize() {
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(ModItems.DYNAMIT) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (!(level instanceof ServerLevel serverLevel)) {
				return InteractionResult.SUCCESS;
			}
			var pos = hitResult.getBlockPos();
			if (!serverLevel.getBlockState(pos).is(Blocks.BEDROCK)) {
				return InteractionResult.PASS;
			}
			serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			serverLevel.sendParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					1, 0.0, 0.0, 0.0, 0.0);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			return InteractionResult.SUCCESS;
		});
	}
}
