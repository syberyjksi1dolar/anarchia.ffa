package com.example.mymod;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Eventówki działające po uderzeniu:
 *  - Płatek Śniegu: trafiony byt zamarza na 3 sekundy (nie może się ruszać ani skakać), cooldown 10 s,
 *  - Serce: trafiony dostaje Spowolnienie i Ślepotę na 6 sekund, cooldown 10 s.
 */
public final class EventoweAtakiHandler {
	public static final int FREEZE_TICKS = 3 * 20;
	public static final int FREEZE_COOLDOWN_TICKS = 10 * 20;
	public static final int HEART_TICKS = 6 * 20;
	public static final int HEART_COOLDOWN_TICKS = 10 * 20;

	private EventoweAtakiHandler() {
	}

	public static void initialize() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!(level instanceof ServerLevel)) {
				return InteractionResult.PASS;
			}
			if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.isSpectator()) {
				return InteractionResult.PASS;
			}

			ItemStack stack = player.getItemInHand(hand);
			if (stack.is(ModItems.PLATEK_SNIEGU) && !player.getCooldowns().isOnCooldown(stack)) {
				EffectUtil.give(target, "slowness", FREEZE_TICKS, 6);     // Spowolnienie VII: stoi w miejscu
				EffectUtil.give(target, "jump_boost", FREEZE_TICKS, 128); // brak skoku
				player.getCooldowns().addCooldown(stack, FREEZE_COOLDOWN_TICKS);
			} else if (stack.is(ModItems.SERCE) && !player.getCooldowns().isOnCooldown(stack)) {
				EffectUtil.give(target, "slowness", HEART_TICKS, 2);      // Spowolnienie III
				EffectUtil.give(target, "blindness", HEART_TICKS, 0);     // Ślepota
				player.getCooldowns().addCooldown(stack, HEART_COOLDOWN_TICKS);
			}
			return InteractionResult.PASS; // normalne uderzenie nadal się odbywa
		});
	}
}
