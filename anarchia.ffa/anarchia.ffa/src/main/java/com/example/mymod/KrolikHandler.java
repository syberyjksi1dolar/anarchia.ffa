package com.example.mymod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Kostium Zabójczego Królika: po pęknięciu totemu Siła II (30 s), 100% odporności na upadek,
 * stałe Szybkie Kopanie II oraz podwójny skok co 15 sekund (skocz, puść spację w powietrzu i skocz drugi raz).
 */
public final class KrolikHandler {
	public static final int DOUBLE_JUMP_COOLDOWN = 15 * 20;
	public static final int STRENGTH_TICKS = 30 * 20;

	private static final Map<UUID, Integer> TOTEMS = new HashMap<>();
	private static final Map<UUID, Boolean> RELEASED = new HashMap<>();
	private static final Map<UUID, Boolean> USED = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

	private KrolikHandler() {
	}

	public static boolean wears(Player p) {
		return KostiumDresser.isActive(p, ModItems.KROLIK_KLATA);
	}

	private static int totems(Player p) {
		int n = 0;
		if (p.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
			n += p.getMainHandItem().getCount();
		}
		if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			n += p.getOffhandItem().getCount();
		}
		return n;
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long now = server.getTickCount();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				UUID id = p.getUUID();
				if (!wears(p) || !p.isAlive()) {
					TOTEMS.remove(id);
					RELEASED.remove(id);
					USED.remove(id);
					continue;
				}
				// Stałe Szybkie Kopanie II.
				if (now % 10 == 0) {
					p.addEffect(new MobEffectInstance(EffectUtil.get("haste"), 40, 1, true, false, true));
				}
				// 100% odporności na upadek.
				p.resetFallDistance();

				// Pęknięcie totemu: ubyło totemów w rękach, a gracz żyje z minimalnym zdrowiem.
				int now_t = totems(p);
				Integer prev = TOTEMS.put(id, now_t);
				if (prev != null && now_t < prev && p.getHealth() <= 2.0F) {
					p.addEffect(new MobEffectInstance(EffectUtil.get("strength"), STRENGTH_TICKS, 1, true, true, true));
					p.sendSystemMessage(Component.literal("Totem pękł - Siła II na 30 sekund!"), true);
				}

				// Podwójny skok.
				boolean jump = p.getLastClientInput().jump();
				if (p.onGround() || p.isInWater()) {
					RELEASED.put(id, false);
					USED.put(id, false);
				} else {
					if (!jump) {
						RELEASED.put(id, true);
					} else if (RELEASED.getOrDefault(id, false) && !USED.getOrDefault(id, false)) {
						long until = COOLDOWN_UNTIL.getOrDefault(id, 0L);
						if (now >= until) {
							Vec3 v = p.getDeltaMovement();
							p.setDeltaMovement(v.x, 0.55, v.z);
							p.hurtMarked = true;
							p.resetFallDistance();
							USED.put(id, true);
							COOLDOWN_UNTIL.put(id, now + DOUBLE_JUMP_COOLDOWN);
						} else {
							USED.put(id, true);
							p.sendSystemMessage(Component.literal("Podwójny skok: jeszcze " + ((until - now) / 20 + 1) + " s"), true);
						}
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TOTEMS.clear();
			RELEASED.clear();
			USED.clear();
			COOLDOWN_UNTIL.clear();
		});
	}
}
