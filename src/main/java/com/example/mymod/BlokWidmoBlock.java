package com.example.mymod;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Blok Widmo: po postawieniu od razu znika i zadaje 10 HP (20 punktów) bytom w promieniu 5 bloków. Cooldown 2 min. */
public class BlokWidmoBlock extends Block {
	public static final int COOLDOWN_TICKS = 2 * 60 * 20;
	public static final double RADIUS = 5.0;
	public static final float DAMAGE = 20.0F;

	public BlokWidmoBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		serverLevel.removeBlock(pos, false);
		if (placer instanceof Player player) {
			player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		}
		AABB area = new AABB(pos).inflate(RADIUS);
		List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
				e -> e.isAlive() && !e.isSpectator() && e != placer);
		for (LivingEntity victim : victims) {
			victim.hurtServer(serverLevel, placer instanceof Player p
					? serverLevel.damageSources().indirectMagic(p, p)
					: serverLevel.damageSources().magic(), DAMAGE);
		}
	}
}
