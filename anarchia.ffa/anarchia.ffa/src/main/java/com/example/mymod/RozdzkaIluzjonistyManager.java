package com.example.mymod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Różdżka Iluzjonisty:
 *  - lewy klik (machnięcie): 7 par szczęk Evokera przed tobą, każda 5 serc (10 punktów); cooldown 10 s,
 *  - prawy klik: niewidzialność na 5 s bez cząsteczek, klon biegnący prosto 5 s, a przez 30 s +2 serca i Szybkość II; cooldown 40 s.
 */
public final class RozdzkaIluzjonistyManager {
	public static final int LEFT_COOLDOWN_TICKS = 10 * 20;
	public static final int RIGHT_COOLDOWN_TICKS = 40 * 20;
	public static final int INVISIBLE_TICKS = 5 * 20;
	public static final int BUFF_TICKS = 30 * 20;
	public static final int FANGS = 7;
	private static final float FANG_VANILLA = 6.0F;
	private static final float FANG_TOTAL = 10.0F;

	private static final Set<UUID> RIGHT_CLICKED = new HashSet<>();
	private static boolean inside = false;

	private record Clone(ServerLevel level, LivingEntity entity, double dx, double dz, float yaw, long endTick) {
	}

	private static final List<Clone> CLONES = new ArrayList<>();

	private RozdzkaIluzjonistyManager() {
	}

	public static void markRightClick(ServerPlayer player) {
		RIGHT_CLICKED.add(player.getUUID());
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// Lewy klik = machnięcie ręką z różdżką (nie liczymy prawego kliku, który też macha).
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				ItemStack held = player.getMainHandItem();
				if (held.is(ModItems.ROZDZKA_ILUZJONISTY) && player.swinging && player.swingTime <= 0
						&& !RIGHT_CLICKED.contains(player.getUUID())
						&& !player.getCooldowns().isOnCooldown(held)) {
					fangs((ServerLevel) player.level(), player);
					player.getCooldowns().addCooldown(held, LEFT_COOLDOWN_TICKS);
				}
			}
			RIGHT_CLICKED.clear();
			tickClones();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Clone c : CLONES) {
				c.entity().discard();
			}
			CLONES.clear();
			RIGHT_CLICKED.clear();
		});

		// Szczęki Evokera zadają normalnie 6; dobijamy do 10 punktów.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (inside || blocked || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			if (source.getDirectEntity() instanceof EvokerFangs f && f.getOwner() instanceof ServerPlayer owner
					&& owner.getMainHandItem().is(ModItems.ROZDZKA_ILUZJONISTY)) {
				inside = true;
				try {
					entity.invulnerableTime = 0;
					entity.hurtServer(level, source, FANG_TOTAL - FANG_VANILLA);
				} finally {
					inside = false;
				}
			}
		});
	}

	private static void fangs(ServerLevel level, ServerPlayer player) {
		float yaw = player.getYRot();
		double rad = Math.toRadians(yaw);
		double dx = -Math.sin(rad);
		double dz = Math.cos(rad);
		for (int i = 1; i <= FANGS; i++) {
			double x = player.getX() + dx * i;
			double z = player.getZ() + dz * i;
			double y = groundY(level, x, player.getY(), z);
			EvokerFangs fang = EntityType.EVOKER_FANGS.create(level, EntitySpawnReason.TRIGGERED);
			if (fang == null) {
				continue;
			}
			fang.setPos(x, y, z);
			fang.setYRot(yaw);
			fang.setOwner(player);
			level.addFreshEntity(fang);
		}
	}

	private static double groundY(ServerLevel level, double x, double startY, double z) {
		int by = (int) Math.floor(startY);
		BlockPos pos = new BlockPos((int) Math.floor(x), by, (int) Math.floor(z));
		for (int up = 0; up < 3 && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(); up++) {
			pos = pos.above();
		}
		for (int down = 0; down < 4 && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty(); down++) {
			pos = pos.below();
		}
		return pos.getY();
	}

	public static void invisibility(ServerLevel level, ServerPlayer player) {
		// Niewidzialność bez cząsteczek (ambient=false, visible=false, icon=true).
		player.addEffect(new MobEffectInstance(EffectUtil.get("invisibility"), INVISIBLE_TICKS, 0, false, false, true));
		EffectUtil.give(player, "health_boost", BUFF_TICKS, 0); // +2 serca
		EffectUtil.give(player, "speed", BUFF_TICKS, 1);        // Szybkość II
		spawnClone(level, player);
	}

	private static void spawnClone(ServerLevel level, ServerPlayer player) {
		LivingEntity clone = (LivingEntity) EntityType.MANNEQUIN.create(level, EntitySpawnReason.TRIGGERED);
		if (clone == null) {
			return;
		}
		float yaw = player.getYRot();
		double rad = Math.toRadians(yaw);
		clone.setPos(player.getX(), player.getY(), player.getZ());
		clone.setYRot(yaw);
		clone.setYHeadRot(yaw);
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
				EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND }) {
			clone.setItemSlot(slot, player.getItemBySlot(slot).copy());
		}
		level.addFreshEntity(clone);
		CLONES.add(new Clone(level, clone, -Math.sin(rad), Math.cos(rad), yaw,
				level.getGameTime() + INVISIBLE_TICKS));
	}

	private static void tickClones() {
		Iterator<Clone> it = CLONES.iterator();
		while (it.hasNext()) {
			Clone c = it.next();
			LivingEntity e = c.entity();
			if (e.isRemoved() || c.level().getGameTime() >= c.endTick()) {
				e.discard();
				it.remove();
				continue;
			}
			double speed = 0.22;
			double vy = e.getDeltaMovement().y;
			if (e.horizontalCollision && e.onGround()) {
				vy = 0.42; // przeszkoda na 1 blok: podskok
			}
			e.setDeltaMovement(c.dx() * speed, vy, c.dz() * speed);
			e.setYRot(c.yaw());
			e.setYHeadRot(c.yaw());
		}
	}
}
