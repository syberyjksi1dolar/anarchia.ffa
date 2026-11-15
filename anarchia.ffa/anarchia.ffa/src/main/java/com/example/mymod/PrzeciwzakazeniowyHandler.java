package com.example.mymod;

import java.util.ArrayList;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Kostium Przeciwzakażeniowy: odporność na negatywne efekty, +3 serca, +10% obrony (zwrot 10% obrażeń)
 * i +20% dodatkowych obrażeń zadawanych przeciwnikowi ("zarażenie").
 */
public final class PrzeciwzakazeniowyHandler {
	private static final Identifier HP_ID = Identifier.fromNamespaceAndPath(MyMod.MOD_ID, "przeciwzakazeniowy_hp");
	private static boolean inside = false;

	private PrzeciwzakazeniowyHandler() {
	}

	public static boolean wears(Player p) {
		return KostiumDresser.isActive(p, ModItems.PZ_KLATA);
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (inside || blocked || damageTaken <= 0.0F || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			// +10% obrony: zwracamy 10% otrzymanych obrażeń.
			if (entity instanceof Player victim && victim.isAlive() && wears(victim)) {
				victim.heal(damageTaken * 0.10F);
			}
			// Zarażenie: przeciwnik dostaje 20% więcej obrażeń.
			if (source.getDirectEntity() instanceof Player attacker && wears(attacker) && entity != attacker && entity.isAlive()) {
				inside = true;
				try {
					entity.invulnerableTime = 0;
					entity.hurtServer(level, source, damageTaken * 0.20F);
				} finally {
					inside = false;
				}
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				boolean on = wears(p);
				var hp = p.getAttribute(Attributes.MAX_HEALTH);
				if (hp != null) {
					boolean has = hp.getModifier(HP_ID) != null;
					if (on && !has) {
						hp.addTransientModifier(new AttributeModifier(HP_ID, 6.0, AttributeModifier.Operation.ADD_VALUE));
					} else if (!on && has) {
						hp.removeModifier(HP_ID);
						if (p.getHealth() > p.getMaxHealth()) {
							p.setHealth(p.getMaxHealth());
						}
					}
				}
				if (on) {
					// Odporność na negatywne efekty: usuwamy wszystkie szkodliwe.
					for (MobEffectInstance e : new ArrayList<>(p.getActiveEffects())) {
						if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
							p.removeEffect(e.getEffect());
						}
					}
				}
			}
		});
	}
}
