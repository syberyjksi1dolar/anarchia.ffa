package com.example.mymod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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

/** Boski Topór: odrzuca byty w promieniu 15 bloków i daje ci 10 s niewrażliwości na obrażenia. Cooldown 120 s. */
public class BoskiToporItem extends Item {
	public static final double RADIUS = 15.0;
	public static final int INVULN_TICKS = 10 * 20;
	public static final int COOLDOWN_TICKS = 120 * 20;

	private static final Map<UUID, Long> INVULN_UNTIL = new HashMap<>();

	public BoskiToporItem(Properties properties) {
		super(properties);
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			Long until = INVULN_UNTIL.get(entity.getUUID());
			if (until == null) {
				return true;
			}
			if (entity.level().getGameTime() >= until) {
				INVULN_UNTIL.remove(entity.getUUID());
				return true;
			}
			return false;
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> INVULN_UNTIL.clear());
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
			Vec3 away = e.position().subtract(player.position());
			Util13.launch(e, new Vec3(away.x, 0, away.z), RADIUS, 0.7);
		}
		serverLevel.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 40, 1.5, 0.5, 1.5, 0.1);
		INVULN_UNTIL.put(player.getUUID(), serverLevel.getGameTime() + INVULN_TICKS);
		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
	}
}
