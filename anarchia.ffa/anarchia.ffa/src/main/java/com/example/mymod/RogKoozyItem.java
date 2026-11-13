package com.example.mymod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Róg Koozy: byty w promieniu 5 bloków dostają 6 HP obrażeń i lecą 10 bloków od ciebie. Cooldown 60 s. */
public class RogKoozyItem extends Item {
	public static final double RADIUS = 5.0;
	public static final float DAMAGE = 6.0F;
	public static final int COOLDOWN_TICKS = 60 * 20;

	public RogKoozyItem(Properties properties) {
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
		for (LivingEntity e : serverLevel.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RADIUS),
				x -> x != player && Util13.targetable(x) && x.distanceToSqr(player) <= RADIUS * RADIUS)) {
			e.hurtServer(serverLevel, serverLevel.damageSources().playerAttack(player), DAMAGE);
			Vec3 away = e.position().subtract(player.position());
			Util13.launch(e, new Vec3(away.x, 0, away.z), 10.0, 0.6);
		}
		serverLevel.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 30, 1.0, 0.4, 1.0, 0.1);
		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
	}
}
