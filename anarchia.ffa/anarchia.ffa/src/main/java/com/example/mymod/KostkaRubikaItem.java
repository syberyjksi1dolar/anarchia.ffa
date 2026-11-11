package com.example.mymod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Kostka Rubika: prawy klik miesza losowo przedmioty w twoim ekwipunku (poza samą kostką). Cooldown 3 s. */
public class KostkaRubikaItem extends Item {
	public static final int COOLDOWN_TICKS = 3 * 20;

	public KostkaRubikaItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack self = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(self)) {
			return InteractionResult.FAIL;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		List<Integer> slots = new ArrayList<>();
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = 0; i < 36; i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s == self) {
				continue; // samej kostki nie ruszamy
			}
			slots.add(i);
			stacks.add(s.copy());
		}
		Collections.shuffle(stacks);
		for (int k = 0; k < slots.size(); k++) {
			player.getInventory().setItem(slots.get(k), stacks.get(k));
		}
		player.getCooldowns().addCooldown(self, COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
	}
}
