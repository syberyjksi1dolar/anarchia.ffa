package com.example.mymod;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** Zatopiony Statek jako przedmiot: po postawieniu zostaje w ekwipunku, a na nim włącza się cooldown. */
public class ZatopionyStatekItem extends BlockItem {
	public ZatopionyStatekItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		if (player != null && player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		int before = stack.getCount();
		InteractionResult result = super.place(context);
		if (player != null && result.consumesAction()) {
			stack.setCount(before); // przedmiot nie ubywa
			player.getCooldowns().addCooldown(stack, ZatopionyStatekBlock.PLACE_COOLDOWN_TICKS);
		}
		return result;
	}
}
