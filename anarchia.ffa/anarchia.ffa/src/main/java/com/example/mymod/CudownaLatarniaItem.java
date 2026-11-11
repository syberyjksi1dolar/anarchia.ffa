package com.example.mymod;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** Cudowna Latarnia jako przedmiot: po postawieniu zostaje w ekwipunku, a na nim włącza się cooldown. */
public class CudownaLatarniaItem extends BlockItem {
	public CudownaLatarniaItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		if (player != null && player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (context.getClickedFace() != net.minecraft.core.Direction.UP) {
			return InteractionResult.FAIL; // tylko na górnej ścianie bloku (na podłodze)
		}
		int before = stack.getCount();
		InteractionResult result = super.place(context);
		if (player != null && result.consumesAction()) {
			stack.setCount(before); // przedmiot nie ubywa
			player.getCooldowns().addCooldown(stack, CudownaLatarniaBlock.COOLDOWN_TICKS);
		}
		return result;
	}
}
