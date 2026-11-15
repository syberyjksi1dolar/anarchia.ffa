package com.example.mymod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Kostka Rubika: prawy klik na gracza miesza losowo przedmioty w JEGO ekwipunku (nie w twoim). Cooldown 3 s.
 * Kliknięcie w powietrze lub w byt bez ekwipunku nic nie robi.
 */
public class KostkaRubikaItem extends Item {
	public static final int COOLDOWN_TICKS = 3 * 20;

	public KostkaRubikaItem(Properties properties) {
		super(properties);
	}

	public static void initialize() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			ItemStack self = player.getItemInHand(hand);
			if (!self.is(ModItems.KOSTKA_RUBIKA)) {
				return InteractionResult.PASS;
			}
			if (!(level instanceof ServerLevel)) {
				return InteractionResult.SUCCESS;
			}
			if (!(entity instanceof ServerPlayer target) || target == player) {
				return InteractionResult.PASS;
			}
			if (GrinchCostumeHandler.isImmune(target)) {
				if (player instanceof ServerPlayer sp) {
					sp.sendSystemMessage(Component.literal("Cel jest chroniony Wybuchem Grincha!"), true);
				}
				return InteractionResult.FAIL;
			}
			if (player.getCooldowns().isOnCooldown(self)) {
				return InteractionResult.FAIL;
			}
			List<ItemStack> stacks = new ArrayList<>();
			for (int i = 0; i < 36; i++) {
				stacks.add(target.getInventory().getItem(i).copy());
			}
			Collections.shuffle(stacks);
			for (int i = 0; i < 36; i++) {
				target.getInventory().setItem(i, stacks.get(i));
			}
			target.containerMenu.broadcastChanges();
			target.sendSystemMessage(Component.literal("Ktoś pomieszał ci ekwipunek!"), true);
			player.getCooldowns().addCooldown(self, COOLDOWN_TICKS);
			return InteractionResult.SUCCESS;
		});
	}
}
