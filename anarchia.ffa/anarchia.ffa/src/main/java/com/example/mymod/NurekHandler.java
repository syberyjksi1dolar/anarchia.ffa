package com.example.mymod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Kostium Nurka Głębinowego: +4 serca, stała Szybkość II, +30% obrażeń w wodzie i Gracja Delfina
 * (10 s) nadawana po wejściu do wody, nie częściej niż co 30 s.
 */
public final class NurekHandler {
	private static final Identifier HP_ID = Identifier.fromNamespaceAndPath(MyMod.MOD_ID, "nurek_hp");
	public static final int GRACE_TICKS = 10 * 20;
	public static final int GRACE_COOLDOWN = 30 * 20;

	private static final Map<UUID, Long> NEXT_GRACE = new HashMap<>();
	private static boolean inside = false;

	private NurekHandler() {
	}

	public static boolean wears(Player p) {
		return KostiumDresser.isActive(p, ModItems.NUREK_KLATA);
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (inside || blocked || damageTaken <= 0.0F || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			// +30% obrażeń w wodzie.
			if (source.getDirectEntity() instanceof Player attacker && wears(attacker) && attacker.isInWater()
					&& entity != attacker && entity.isAlive()) {
				inside = true;
				try {
					entity.invulnerableTime = 0;
					entity.hurtServer(level, source, damageTaken * 0.30F);
				} finally {
					inside = false;
				}
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long now = server.getTickCount();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				UUID id = p.getUUID();
				boolean on = wears(p) && p.isAlive();
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
					NEXT_GRACE.remove(id);
					continue;
				}
				if (now % 10 == 0) {
					p.addEffect(new MobEffectInstance(EffectUtil.get("speed"), 40, 1, true, false, true));
				}
				if (p.isInWater() && now >= NEXT_GRACE.getOrDefault(id, 0L)) {
					p.addEffect(new MobEffectInstance(EffectUtil.get("dolphins_grace"), GRACE_TICKS, 0, true, true, true));
					NEXT_GRACE.put(id, now + GRACE_COOLDOWN);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> NEXT_GRACE.clear());
	}
}
