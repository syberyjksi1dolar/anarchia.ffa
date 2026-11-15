package com.example.mymod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Kostium Mima: +4 serca, 11% dodatkowych obrażeń i Lustro bólu (3x Shift): przez 10 s obrażenia, które
 * otrzymujesz, są przenoszone na wroga (dostajesz je z powrotem w zdrowiu, a wróg dostaje taką samą ilość).
 * Cooldown 2 min.
 */
public final class MimHandler {
	private static final Identifier HP_ID = Identifier.fromNamespaceAndPath(MyMod.MOD_ID, "mim_hp");
	public static final int MIRROR_TICKS = 10 * 20;
	public static final int COOLDOWN_TICKS = 120 * 20;

	private static final Map<UUID, Boolean> WAS_SNEAKING = new HashMap<>();
	private static final Map<UUID, Deque<Long>> PRESSES = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> MIRROR_UNTIL = new HashMap<>();
	private static boolean inside = false;

	private MimHandler() {
	}

	public static boolean wears(Player p) {
		return KostiumDresser.isActive(p, ModItems.MIM_KLATA);
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (inside || blocked || damageTaken <= 0.0F || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			// Lustro bólu: przenosimy otrzymane obrażenia na wroga.
			if (entity instanceof Player victim && victim.isAlive() && source.getEntity() instanceof LivingEntity attacker
					&& attacker != victim && wears(victim)) {
				Long until = MIRROR_UNTIL.get(victim.getUUID());
				if (until != null && level.getServer().getTickCount() < until) {
					inside = true;
					try {
						victim.heal(damageTaken);
						attacker.invulnerableTime = 0;
						attacker.hurtServer(level, level.damageSources().playerAttack(victim), damageTaken);
					} finally {
						inside = false;
					}
					return;
				}
			}
			// +11% dodatkowych obrażeń w walce.
			if (source.getDirectEntity() instanceof Player attacker && wears(attacker) && entity != attacker) {
				inside = true;
				try {
					entity.invulnerableTime = 0;
					entity.hurtServer(level, source, damageTaken * 0.11F);
				} finally {
					inside = false;
				}
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long now = server.getTickCount();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				UUID id = p.getUUID();
				boolean on = wears(p);
				var hp = p.getAttribute(Attributes.MAX_HEALTH);
				if (hp != null) {
					boolean has = hp.getModifier(HP_ID) != null;
					if (on && !has) {
						hp.addTransientModifier(new AttributeModifier(HP_ID, 8.0, AttributeModifier.Operation.ADD_VALUE));
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
					MIRROR_UNTIL.remove(id);
					continue;
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
							p.sendSystemMessage(Component.literal("Lustro bólu: cooldown jeszcze " + ((until - now) / 20 + 1) + " s"), true);
						} else {
							MIRROR_UNTIL.put(id, now + MIRROR_TICKS);
							COOLDOWN_UNTIL.put(id, now + COOLDOWN_TICKS);
							p.sendSystemMessage(Component.literal("Lustro bólu aktywne na 10 sekund!"), true);
						}
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			WAS_SNEAKING.clear();
			PRESSES.clear();
			COOLDOWN_UNTIL.clear();
			MIRROR_UNTIL.clear();
		});
	}
}
