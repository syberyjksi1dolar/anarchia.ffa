package com.example.mymod;

import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Korona Anarchia: dopóki gracz ma ją na głowie, dostaje stałe efekty:
 * Siła III, Odporność IV, Szczęście II, Odporność na ogień, Szybkość IV.
 */
public final class AnarchiaKoronaHandler {
	private static final int REFRESH_EVERY_TICKS = 10;
	private static final int EFFECT_DURATION_TICKS = 40;

	/** Nazwa efektu i poziom (amplifier = poziom - 1). */
	private record Buff(String effectId, int amplifier) {
	}

	private static final List<Buff> BUFFS = List.of(
			new Buff("strength", 2),         // Siła III
			new Buff("resistance", 3),       // Odporność IV
			new Buff("luck", 1),             // Szczęście II
			new Buff("fire_resistance", 0),  // Odporność na ogień
			new Buff("speed", 3));           // Szybkość IV

	private AnarchiaKoronaHandler() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % REFRESH_EVERY_TICKS != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.ANARCHIA_KORONA)) {
					applyBuffs(player);
				}
			}
		});
	}

	private static void applyBuffs(ServerPlayer player) {
		for (Buff buff : BUFFS) {
			ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT,
					Identifier.fromNamespaceAndPath("minecraft", buff.effectId()));
			Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getOrThrow(key);
			// ambient = true (mała ikonka), bez cząsteczek, z ikoną w HUD.
			player.addEffect(new MobEffectInstance(effect, EFFECT_DURATION_TICKS, buff.amplifier(), true, false, true));
		}
	}
}
