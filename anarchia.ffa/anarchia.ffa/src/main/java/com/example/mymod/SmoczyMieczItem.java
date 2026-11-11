package com.example.mymod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Smoczy Miecz: Ostrość V, Aspekt ognia II, niezniszczalny; prawy klik wystrzeliwuje perłę Endermana (cooldown 1 min). */
public class SmoczyMieczItem extends Item {
	public static final int PEARL_COOLDOWN_TICKS = 60 * 20;

	public SmoczyMieczItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel) {
			var pearl = EntityType.ENDER_PEARL.create(serverLevel, EntitySpawnReason.TRIGGERED);
			if (pearl != null) {
				pearl.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
				pearl.setOwner(player);
				pearl.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
				serverLevel.addFreshEntity(pearl);
			}
			player.getCooldowns().addCooldown(stack, PEARL_COOLDOWN_TICKS);
		}
		return InteractionResult.SUCCESS;
	}
}
