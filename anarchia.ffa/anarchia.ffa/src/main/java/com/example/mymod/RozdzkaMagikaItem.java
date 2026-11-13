package com.example.mymod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Różdżka Magika: 6 segmentów, każdy ładuje się za 4 HP zadanych obrażeń. Po pełnym naładowaniu
 * przytrzymanie prawego przycisku włącza niebieską wiązkę (łącznie 5 s), która zadaje obrażenia
 * trafionemu bytowi. Po wyczerpaniu 5 s pasek resetuje się do zera.
 */
public class RozdzkaMagikaItem extends Item {
	public static final float HP_PER_SEGMENT = 4.0F;
	public static final int SEGMENTS = 6;
	public static final int BEAM_TICKS = 5 * 20;
	/** Obrażenia wiązki i odstęp (jak cooldown miecza). */
	public static final float BEAM_DAMAGE = 1.0F;
	public static final int BEAM_INTERVAL = 12;
	public static final double RANGE = 20.0;

	private static final Map<UUID, Float> ACC = new HashMap<>();
	private static final Map<UUID, Integer> BEAM_USED = new HashMap<>();
	private static boolean inside = false;

	public RozdzkaMagikaItem(Properties properties) {
		super(properties);
	}

	private static boolean full(UUID id) {
		return ACC.getOrDefault(id, 0.0F) >= HP_PER_SEGMENT * SEGMENTS;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 72000;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (full(player.getUUID())) {
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}
		return InteractionResult.PASS;
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (inside || blocked || !(source.getDirectEntity() instanceof ServerPlayer p)) {
				return;
			}
			if (p.getMainHandItem().is(ModItems.ROZDZKA_MAGIKA) || p.getOffhandItem().is(ModItems.ROZDZKA_MAGIKA)) {
				float max = HP_PER_SEGMENT * SEGMENTS;
				ACC.put(p.getUUID(), Math.min(max, ACC.getOrDefault(p.getUUID(), 0.0F) + damageTaken));
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				UUID id = p.getUUID();
				boolean holds = p.getMainHandItem().is(ModItems.ROZDZKA_MAGIKA) || p.getOffhandItem().is(ModItems.ROZDZKA_MAGIKA);
				if (!holds || !(p.level() instanceof ServerLevel level)) {
					continue;
				}
				boolean beam = full(id) && p.isUsingItem() && p.getUseItem().is(ModItems.ROZDZKA_MAGIKA);
				if (!beam) {
					int segments = (int) (ACC.getOrDefault(id, 0.0F) / HP_PER_SEGMENT);
					if (full(id)) {
						int left = BEAM_TICKS - BEAM_USED.getOrDefault(id, 0);
						p.sendSystemMessage(Component.literal("Wiązka: " + Util13.bar(left / (double) BEAM_TICKS, SEGMENTS)
								+ " (przytrzymaj prawy przycisk)"), true);
					} else {
						p.sendSystemMessage(Component.literal("Magia: " + Util13.bar(segments / (double) SEGMENTS, SEGMENTS)), true);
					}
					continue;
				}
				int used = BEAM_USED.merge(id, 1, Integer::sum);
				Vec3 eye = p.getEyePosition();
				Vec3 look = p.getLookAngle();
				LivingEntity target = null;
				double reach = RANGE;
				for (double d = 0.5; d <= RANGE && target == null; d += 0.5) {
					Vec3 point = eye.add(look.scale(d));
					BlockPos bp = BlockPos.containing(point);
					if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
						reach = d;
						break;
					}
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
							new net.minecraft.world.phys.AABB(point, point).inflate(0.35),
							x -> x != p && Util13.targetable(x))) {
						target = e;
						reach = d;
						break;
					}
				}
				for (double d = 1.0; d <= reach; d += 0.5) {
					Vec3 pt = eye.add(look.scale(d)).add(0, -0.25, 0);
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pt.x, pt.y, pt.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
				if (target != null && used % BEAM_INTERVAL == 0) {
					inside = true;
					try {
						target.invulnerableTime = 0;
						target.hurtServer(level, level.damageSources().magic(), BEAM_DAMAGE);
					} finally {
						inside = false;
					}
				}
				p.sendSystemMessage(Component.literal("Wiązka: " + Util13.bar((BEAM_TICKS - used) / (double) BEAM_TICKS, SEGMENTS)), true);
				if (used >= BEAM_TICKS) {
					ACC.put(id, 0.0F);
					BEAM_USED.put(id, 0);
					p.stopUsingItem();
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ACC.clear();
			BEAM_USED.clear();
		});
	}
}
