package com.example.mymod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Chydro: prawy klik wystrzeliwuje kulę ognistą jak ghast (bez wybuchu, nie niszczy bloków).
 * Tam, gdzie kula trafi, powstaje klatka z niebieskiego koralowca (6 x 9 x 6) na 8 sekund.
 * Przedmiot jest nieskończony, cooldown: 1 minuta.
 */
public class ChydroKlatkaItem extends Item {
	public static final int COOLDOWN_TICKS = 60 * 20; // 1 minuta
	public static final double RANGE = 3.0;           // ile bloków kula może przelecieć

	public ChydroKlatkaItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}

		Vec3 look = player.getLookAngle();

		// Kula od ghasta z siłą wybuchu 0: leci i trafia, ale niczego nie rozwala ani nie podpala.
		LargeFireball fireball = new LargeFireball(serverLevel, player, look, 0);
		fireball.setPos(player.getX() + look.x, player.getEyeY() - 0.2 + look.y, player.getZ() + look.z);
		serverLevel.addFreshEntity(fireball);
		ChydroKlatkaManager.trackShot(serverLevel, fireball, player.getUUID());

		serverLevel.playSound(null, player.blockPosition(), SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);

		// Przedmiot jest nieskończony: nie zużywa się, tylko ma cooldown.
		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
	}
}
