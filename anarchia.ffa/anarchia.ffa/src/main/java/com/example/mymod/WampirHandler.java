package com.example.mymod;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Kostium Wampira: Pocałunek wampira leczy za 7% zadanych obrażeń, +4 serca, +10% obrażeń.
 * (Kradzież 2% majątku po zabiciu gracza wymaga systemu ekonomii, którego mod nie ma - pominięta.)
 */
public final class WampirHandler {
	private static final Identifier HP_ID = Identifier.fromNamespaceAndPath(MyMod.MOD_ID, "wampir_hp");
	private static boolean inside = false;

	private WampirHandler() {
	}

	public static boolean wears(Player p) {
		return KostiumDresser.isActive(p, ModItems.WAMPIR_KLATA);
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (inside || blocked || damageTaken <= 0.0F || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			if (source.getDirectEntity() instanceof Player attacker && wears(attacker) && entity != attacker) {
				// Pocałunek wampira: leczenie 7% zadanych obrażeń.
				attacker.heal(damageTaken * 0.07F);
				// +10% obrażeń.
				if (entity.isAlive()) {
					inside = true;
					try {
						entity.invulnerableTime = 0;
						entity.hurtServer(level, source, damageTaken * 0.10F);
					} finally {
						inside = false;
					}
				}
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				boolean on = wears(p) && p.isAlive();
				var hp = p.getAttribute(Attributes.MAX_HEALTH);
				if (hp == null) {
					continue;
				}
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
		});
	}
}
