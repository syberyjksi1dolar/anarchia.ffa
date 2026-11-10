package com.example.mymod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Zatopiony Statek: przez 10 sekund po postawieniu wszystkie byty w promieniu 14 bloków (oprócz właściciela)
 * dostają Powolne opadanie II, Zatrucie II i Osłabienie II (efekty kończą się po tych 10 sekundach).
 * Osoba, która postawiła statek, dostaje w tym czasie Siłę I.
 * Uwaga: lista postawionych statków jest tylko w pamięci, więc po restarcie serwera/świata
 * statki postawione wcześniej przestają działać (trzeba je postawić ponownie).
 */
public final class ZatopionyStatekManager {
	public static final double RADIUS = 14.0;
	/** Jak długo po postawieniu statek działa (efekty trwają maksymalnie tyle samo). */
	public static final int ACTIVE_TICKS = 10 * 20; // 10 sekund
	private static final int REFRESH_EVERY_TICKS = 10;

	private record Buff(String effectId, int amplifier) {
	}

	private static final List<Buff> BUFFS = List.of(
			new Buff("slow_falling", 1), // Powolne opadanie II
			new Buff("poison", 1),       // Zatrucie II
			new Buff("weakness", 1));    // Osłabienie II

	private record Ship(ServerLevel level, BlockPos pos, UUID owner, long expiresAt) {
	}

	private static final List<Ship> SHIPS = new ArrayList<>();

	private ZatopionyStatekManager() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % REFRESH_EVERY_TICKS == 0) {
				tick();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Ship ship : SHIPS) {
				if (ship.level().getBlockState(ship.pos()).is(ModBlocks.ZATOPIONY_STATEK)) {
					ship.level().removeBlock(ship.pos(), false);
				}
			}
			SHIPS.clear();
		});
		// Statek jest niezniszczalny, dopóki działa.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !state.is(ModBlocks.ZATOPIONY_STATEK));
	}

	public static void register(ServerLevel level, BlockPos pos, UUID owner) {
		Ship ship = new Ship(level, pos.immutable(), owner, level.getGameTime() + ACTIVE_TICKS);
		SHIPS.add(ship);
		apply(ship); // efekty od razu po postawieniu
	}

	private static void tick() {
		Iterator<Ship> it = SHIPS.iterator();
		while (it.hasNext()) {
			Ship ship = it.next();
			ServerLevel level = ship.level();
			BlockPos pos = ship.pos();

			if (level.getGameTime() >= ship.expiresAt()) {
				it.remove(); // minęło 10 sekund: statek znika
				if (level.hasChunkAt(pos) && level.getBlockState(pos).is(ModBlocks.ZATOPIONY_STATEK)) {
					level.removeBlock(pos, false);
				}
				continue;
			}
			if (!level.hasChunkAt(pos)) {
				continue; // chunk niezaładowany: poczekaj
			}
			if (!level.getBlockState(pos).is(ModBlocks.ZATOPIONY_STATEK)) {
				it.remove(); // blok zniknął
				continue;
			}
			if (level.getGameTime() % 20 < REFRESH_EVERY_TICKS) {
				apply(ship);
			}
		}
	}

	/** Nakłada efekty na wszystkich w promieniu (oprócz właściciela); wszystkie kończą się razem ze statkiem. */
	private static void apply(Ship ship) {
		ServerLevel level = ship.level();
		int remaining = (int) Math.max(20L, ship.expiresAt() - level.getGameTime());

		Vec3 center = Vec3.atCenterOf(ship.pos());
		AABB area = new AABB(center.x - RADIUS, center.y - RADIUS, center.z - RADIUS,
				center.x + RADIUS, center.y + RADIUS, center.z + RADIUS);
		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area,
				e -> e.isAlive() && !e.isSpectator()
						&& (ship.owner() == null || !e.getUUID().equals(ship.owner())));
		for (LivingEntity target : targets) {
			if (target.position().distanceTo(center) > RADIUS) {
				continue;
			}
			for (Buff buff : BUFFS) {
				ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT,
						Identifier.fromNamespaceAndPath("minecraft", buff.effectId()));
				Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getOrThrow(key);
				target.addEffect(new MobEffectInstance(effect, remaining, buff.amplifier(), false, true, true));
			}
		}

		// Osoba, która postawiła statek, dostaje Siłę I (na ten sam czas), jeśli jest w zasięgu.
		if (ship.owner() != null) {
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ship.owner());
			if (owner != null && owner.level() == level && owner.isAlive()
					&& owner.position().distanceTo(center) <= RADIUS) {
				ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT,
						Identifier.fromNamespaceAndPath("minecraft", "strength"));
				Holder<MobEffect> strength = BuiltInRegistries.MOB_EFFECT.getOrThrow(key);
				owner.addEffect(new MobEffectInstance(strength, remaining, 0, false, true, true)); // Siła I
			}
		}
	}
}
