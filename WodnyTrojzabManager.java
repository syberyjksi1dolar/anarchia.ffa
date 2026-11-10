package com.example.mymod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Logika Wodnego Trójzębu (torpeda i wystrzał z piorunem). */
public final class WodnyTrojzabManager {
	public static final int TORPEDO_COOLDOWN_TICKS = 2 * 20;       // 2 sekundy
	public static final int THROW_COOLDOWN_TICKS = 2 * 60 * 20;    // 2 minuty
	public static final double TORPEDO_SPEED = 2.5;                // siła torpedy
	public static final float THROW_SPEED = 2.5F;                  // prędkość wystrzału
	public static final double RADIUS = 5.0;                       // promień wybuchu (bloki)
	public static final float DAMAGE = 10.0F;                      // 10 HP = 5 serc
	public static final float FIRE_SECONDS = 8.0F;
	private static final int MAX_FLIGHT_TICKS = 5 * 20;            // po 5 s w locie trójząb znika

	private static final Map<UUID, Long> TORPEDO_READY_AT = new HashMap<>();
	private static final Map<UUID, Long> THROW_READY_AT = new HashMap<>();

	private record Shot(ServerLevel level, ThrownTrident trident, UUID owner, long startTick) {
	}

	private static final List<Shot> SHOTS = new ArrayList<>();

	private WodnyTrojzabManager() {
	}

	public static void initialize() {
		ServerTickEvents.START_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SHOTS.clear();
			TORPEDO_READY_AT.clear();
			THROW_READY_AT.clear();
		});
	}

	/** Zwraca ile sekund zostało do końca cooldownu (0 = gotowe). */
	private static int secondsLeft(Map<UUID, Long> map, ServerPlayer player, long now) {
		Long readyAt = map.get(player.getUUID());
		if (readyAt == null || now >= readyAt) {
			return 0;
		}
		return (int) Math.ceil((readyAt - now) / 20.0);
	}

	/** Prawy klik: torpeda bez wody. */
	public static void tryTorpedo(ServerLevel level, ServerPlayer player) {
		long now = level.getGameTime();
		int left = secondsLeft(TORPEDO_READY_AT, player, now);
		if (left > 0) {
			return;
		}

		Vec3 look = player.getLookAngle();
		player.setDeltaMovement(look.scale(TORPEDO_SPEED));
		player.hurtMarked = true; // wymusza wysłanie prędkości do gracza
		player.resetFallDistance();
		level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.0F);

		TORPEDO_READY_AT.put(player.getUUID(), now + TORPEDO_COOLDOWN_TICKS);
	}

	/** Prawy klik + shift: wystrzał trójzębu. */
	public static void tryThrow(ServerLevel level, ServerPlayer player) {
		long now = level.getGameTime();
		int left = secondsLeft(THROW_READY_AT, player, now);
		if (left > 0) {
			return;
		}

		ThrownTrident trident = new ThrownTrident(level, player, new ItemStack(ModItems.WODNY_TROJZAB));
		trident.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, THROW_SPEED, 0.5F);
		level.addFreshEntity(trident);
		SHOTS.add(new Shot(level, trident, player.getUUID(), now));
		level.playSound(null, player.blockPosition(), SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F);

		THROW_READY_AT.put(player.getUUID(), now + THROW_COOLDOWN_TICKS);
	}

	private static void tick() {
		Iterator<Shot> it = SHOTS.iterator();
		while (it.hasNext()) {
			Shot shot = it.next();
			ThrownTrident trident = shot.trident();
			ServerLevel level = shot.level();

			if (trident.isRemoved()) {
				it.remove();
				continue;
			}
			if (level.getGameTime() - shot.startTick() > MAX_FLIGHT_TICKS) {
				trident.discard();
				it.remove();
				continue;
			}

			// Sprawdzamy trafienie zanim trójząb zdąży sam coś zranić: patrzymy, co jest na jego drodze w tym ticku.
			Vec3 from = trident.position();
			Vec3 delta = trident.getDeltaMovement();
			Vec3 to = from.add(delta);
			Vec3 hitPoint = null;

			AABB path = trident.getBoundingBox().expandTowards(delta).inflate(0.5);
			List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, path,
					e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(shot.owner()));
			if (!entities.isEmpty()) {
				LivingEntity target = entities.get(0);
				hitPoint = target.position().add(0.0, target.getBbHeight() / 2.0, 0.0);
			} else {
				BlockHitResult blockHit = level.clip(new ClipContext(from, to,
						ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, trident));
				if (blockHit.getType() != HitResult.Type.MISS) {
					hitPoint = blockHit.getLocation();
				} else if (delta.lengthSqr() < 0.0001) {
					hitPoint = from; // utknął w miejscu
				}
			}

			if (hitPoint != null) {
				ServerPlayer owner = level.getServer().getPlayerList().getPlayer(shot.owner());
				strike(level, hitPoint, owner);
				trident.discard();
				it.remove();
			}
		}
	}

	/** Piorun, 10 HP i ogień w promieniu 5 bloków (właściciel jest pomijany). */
	private static void strike(ServerLevel level, Vec3 at, ServerPlayer owner) {
		// Piorun tylko wizualny, żeby nie raził użytkownika; obrażenia i ogień robimy sami.
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.setPos(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}

		DamageSource source = owner != null
				? level.damageSources().indirectMagic(owner, owner)
				: level.damageSources().magic();

		AABB area = new AABB(at.x - RADIUS, at.y - RADIUS, at.z - RADIUS, at.x + RADIUS, at.y + RADIUS, at.z + RADIUS);
		List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, area,
				e -> e.isAlive() && !e.isSpectator() && (owner == null || e != owner));
		for (LivingEntity victim : victims) {
			if (victim.position().distanceTo(at) > RADIUS) {
				continue;
			}
			victim.hurtServer(level, source, DAMAGE);
			victim.igniteForSeconds(FIRE_SECONDS);
		}

		// Ogień na ziemi w promieniu 5 bloków: tam, gdzie jest powietrze nad pełnym blokiem.
		BlockPos center = BlockPos.containing(at);
		int r = (int) Math.ceil(RADIUS);
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos pos = center.offset(dx, dy, dz);
					if (Vec3.atCenterOf(pos).distanceTo(at) > RADIUS || !level.isInWorldBounds(pos)) {
						continue;
					}
					if (!level.getBlockState(pos).isAir()) {
						continue;
					}
					BlockPos below = pos.below();
					if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
						level.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
					}
				}
			}
		}
	}
}
