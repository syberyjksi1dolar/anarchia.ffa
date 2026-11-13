package com.example.mymod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Wędka 1.8: haczyk w blok przyciąga gracza do bloku; haczyk w gracza zadaje 2 serca (cooldown 10 s tylko na atak graczy).
 * Wędeczka Perełeczka: złapany byt nie może się teleportować ani używać pereł Endu, dopóki jest na lince.
 */
public final class WedkiHandler {
	public static final int ATTACK_COOLDOWN_TICKS = 10 * 20;
	public static final float PLAYER_DAMAGE = 4.0F; // 2 serca

	private static final Map<UUID, Integer> ATTACK_READY = new HashMap<>();
	/** Złapany byt -> ostatnia poprawna pozycja. */
	private static final Map<LivingEntity, Vec3> CAUGHT = new HashMap<>();
	private static final Map<LivingEntity, Integer> CAUGHT_SEEN = new HashMap<>();

	private WedkiHandler() {
	}

	private static boolean holds(ServerPlayer p, net.minecraft.world.item.Item item) {
		return p.getMainHandItem().is(item) || p.getOffhandItem().is(item);
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				var hook = p.fishing;
				if (hook == null || hook.isRemoved() || !(p.level() instanceof ServerLevel level)) {
					continue;
				}
				var hooked = hook.getHookedIn();

				if (holds(p, ModItems.WEDKA_18)) {
					if (hooked instanceof ServerPlayer victim && victim != p) {
						int ready = ATTACK_READY.getOrDefault(p.getUUID(), 0);
						if (now >= ready) {
							victim.hurtServer(level, level.damageSources().playerAttack(p), PLAYER_DAMAGE);
							ATTACK_READY.put(p.getUUID(), now + ATTACK_COOLDOWN_TICKS);
						}
					} else if (hooked == null && !hook.isInWater()
							&& (hook.onGround() || hook.horizontalCollision || hook.verticalCollision)) {
						Vec3 d = hook.position().subtract(p.position());
						double dist = d.length();
						if (dist > 2.0) {
							Vec3 dir = d.normalize();
							double speed = Math.min(1.7, 0.5 + dist * 0.09);
							p.setDeltaMovement(dir.x * speed, dir.y * speed + 0.25, dir.z * speed);
							p.hurtMarked = true;
							p.resetFallDistance();
						}
						hook.discard();
					}
				}

				if (holds(p, ModItems.WEDECZKA_PERELECZKA) && hooked instanceof LivingEntity target && target.isAlive()) {
					if (!CAUGHT.containsKey(target)) {
						CAUGHT.put(target, target.position());
					}
					CAUGHT_SEEN.put(target, now);
				}
			}

			Iterator<Map.Entry<LivingEntity, Vec3>> it = CAUGHT.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<LivingEntity, Vec3> e = it.next();
				LivingEntity target = e.getKey();
				Integer seen = CAUGHT_SEEN.get(target);
				// Linka zerwana, byt zginął lub zniknął: blokada znika.
				if (target.isRemoved() || !target.isAlive() || seen == null || seen < now) {
					CAUGHT_SEEN.remove(target);
					it.remove();
					continue;
				}
				Vec3 last = e.getValue();
				Vec3 pos = target.position();
				double dx = pos.x - last.x;
				double dz = pos.z - last.z;
				if (dx * dx + dz * dz > 9.0 || Math.abs(pos.y - last.y) > 6.0) {
					Util13.moveTo(target, last); // próba teleportu: wracamy na miejsce
				} else {
					e.setValue(pos);
				}
				if (target instanceof ServerPlayer victim) {
					victim.getCooldowns().addCooldown(new ItemStack(Items.ENDER_PEARL), 10);
					victim.getCooldowns().addCooldown(new ItemStack(Items.CHORUS_FRUIT), 10);
					victim.sendSystemMessage(Component.literal("Złapany na linkę: brak teleportacji"), true);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ATTACK_READY.clear();
			CAUGHT.clear();
			CAUGHT_SEEN.clear();
		});
	}
}
