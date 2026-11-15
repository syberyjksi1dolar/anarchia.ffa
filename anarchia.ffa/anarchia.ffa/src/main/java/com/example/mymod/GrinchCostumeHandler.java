package com.example.mymod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Kostium Grincha: +3 serca, +10% obrażeń i Wybuch Grincha (3x Shift): przez 10 s nie da się nałożyć na
 * ciebie eventówek (kostka Rubika, klatka Kosy, negatywne efekty). Cooldown 60 s (założenie).
 * (Kradzież 3% majątku po zabiciu gracza wymaga systemu ekonomii, którego mod nie ma - pominięta.)
 */
public final class GrinchCostumeHandler {
	private static final Identifier HP_ID = Identifier.fromNamespaceAndPath(MyMod.MOD_ID, "kgrinch_hp");
	public static final int BLOCK_TICKS = 10 * 20;
	public static final int COOLDOWN_TICKS = 60 * 20;

	private static final Map<UUID, Boolean> WAS_SNEAKING = new HashMap<>();
	private static final Map<UUID, Deque<Long>> PRESSES = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> BLOCK_UNTIL = new HashMap<>();
	private static long nowTick = 0;
	private static boolean inside = false;

	private GrinchCostumeHandler() {
	}

	public static boolean wears(Player p) {
		return KostiumDresser.isActive(p, ModItems.KGRINCH_KLATA);
	}

	/** Czy na tego gracza nie da się teraz nakładać eventówek. */
	public static boolean isImmune(LivingEntity e) {
		if (!(e instanceof Player p)) {
			return false;
		}
		Long until = BLOCK_UNTIL.get(p.getUUID());
		return until != null && nowTick < until;
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (inside || blocked || damageTaken <= 0.0F || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			if (source.getDirectEntity() instanceof Player attacker && wears(attacker) && entity != attacker
					&& entity.isAlive()) {
				inside = true;
				try {
					entity.invulnerableTime = 0;
					entity.hurtServer(level, source, damageTaken * 0.10F);
				} finally {
					inside = false;
				}
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long now = server.getTickCount();
			nowTick = now;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				UUID id = p.getUUID();
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
				if (!on) {
					WAS_SNEAKING.remove(id);
					PRESSES.remove(id);
					BLOCK_UNTIL.remove(id);
					continue;
				}
				// Podczas Wybuchu usuwamy negatywne efekty nałożone przez eventówki.
				if (isImmune(p) && now % 5 == 0) {
					List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> bad = new ArrayList<>();
					for (MobEffectInstance e : p.getActiveEffects()) {
						if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
							bad.add(e.getEffect());
						}
					}
					for (var h : bad) {
						p.removeEffect(h);
					}
				}
				// Wykrywanie 3x Shift.
				boolean sneaking = p.isShiftKeyDown();
				boolean was = WAS_SNEAKING.getOrDefault(id, false);
				WAS_SNEAKING.put(id, sneaking);
				if (sneaking && !was) {
					Deque<Long> q = PRESSES.computeIfAbsent(id, k -> new ArrayDeque<>());
					q.addLast(now);
					while (!q.isEmpty() && now - q.peekFirst() > 30) {
						q.removeFirst();
					}
					if (q.size() >= 3) {
						q.clear();
						long until = COOLDOWN_UNTIL.getOrDefault(id, 0L);
						if (now < until) {
							p.sendSystemMessage(Component.literal("Wybuch Grincha: cooldown jeszcze " + ((until - now) / 20 + 1) + " s"), true);
						} else {
							BLOCK_UNTIL.put(id, now + BLOCK_TICKS);
							COOLDOWN_UNTIL.put(id, now + COOLDOWN_TICKS);
							p.sendSystemMessage(Component.literal("Wybuch Grincha! Eventówki nie działają na ciebie przez 10 sekund!"), true);
						}
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			WAS_SNEAKING.clear();
			PRESSES.clear();
			COOLDOWN_UNTIL.clear();
			BLOCK_UNTIL.clear();
		});
	}
}
