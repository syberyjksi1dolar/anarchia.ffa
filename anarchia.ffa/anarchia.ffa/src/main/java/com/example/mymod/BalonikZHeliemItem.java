package com.example.mymod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Balonik z Helem: niszczy bloki nad tobą (3 x 3, do 10 bloków w górę) i unosi cię. Cooldown 40 s. */
public class BalonikZHeliemItem extends Item {
	public static final int COOLDOWN_TICKS = 40 * 20;
	public static final int HEIGHT = 10;

	private static final class Rise {
		final int startY;
		int layer = 1;

		Rise(int startY) {
			this.startY = startY;
		}
	}

	private static final Map<UUID, Rise> RISING = new HashMap<>();

	public BalonikZHeliemItem(Properties properties) {
		super(properties);
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Map.Entry<UUID, Rise>> it = RISING.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<UUID, Rise> e = it.next();
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				Rise r = e.getValue();
				if (p == null || !p.isAlive() || r.layer > HEIGHT || !(p.level() instanceof ServerLevel level)) {
					it.remove();
					continue;
				}
				int y = r.startY + 1 + r.layer;
				BlockPos c = new BlockPos(p.getBlockX(), y, p.getBlockZ());
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos pos = c.offset(dx, 0, dz);
						BlockState state = level.getBlockState(pos);
						if (state.isAir() || level.getBlockEntity(pos) != null || state.getDestroySpeed(level, pos) < 0) {
							continue;
						}
						level.destroyBlock(pos, true, p);
					}
				}
				p.setDeltaMovement(p.getDeltaMovement().x, 0.8, p.getDeltaMovement().z);
				p.hurtMarked = true;
				p.resetFallDistance();
				r.layer++;
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> RISING.clear());
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (!level.isClientSide()) {
			RISING.put(player.getUUID(), new Rise(player.getBlockY()));
			player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		}
		return InteractionResult.SUCCESS;
	}
}
