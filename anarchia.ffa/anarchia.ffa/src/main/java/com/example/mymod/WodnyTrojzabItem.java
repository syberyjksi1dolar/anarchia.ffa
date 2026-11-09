package com.example.mymod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Wodny Trójząb:
 *  - prawy klik: torpeda jak przy trójzębie z Riptide, ale bez wody (cooldown 2 sekundy),
 *  - prawy klik + shift: wystrzeliwuje trójząb, który po trafieniu w byt lub blok wywołuje piorun,
 *    zadaje 10 HP (5 serc) i podpala wszystko w promieniu 5 bloków (cooldown 2 minuty).
 * Przedmiot jest nieskończony i się nie niszczy.
 */
public class WodnyTrojzabItem extends Item {
	public WodnyTrojzabItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		if (player.isShiftKeyDown()) {
			WodnyTrojzabManager.tryThrow(serverLevel, serverPlayer);
		} else {
			WodnyTrojzabManager.tryTorpedo(serverLevel, serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}
}
