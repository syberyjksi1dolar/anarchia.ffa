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
 * Kostium Walentynkowy: +5 serc, +12% dodatkowych obrażeń, +5% obrony (zwrot 5% otrzymanych obrażeń)
 * i 100% odporności na obrażenia od upadku.
 */
public final class WalentynkiHandler {
	private static final Identifier HP_ID = Identifier.fromNamespaceAndPath(MyMod.MOD_ID, "walentynki_hp");
	private static boolean inside = false;

	private WalentynkiHandler() {
	}

	public static boolean wears(Player p) {
		return KostiumDresser.isActive(p, ModItems.WALENTYNKI_KLATA);
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (inside || blocked || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			// +5% obrony: oddajemy 5% otrzymanych obrażeń, jeśli gracz przeżył.
			if (entity instanceof Player victim && wears(victim) && victim.isAlive()) {
				victim.heal(damageTaken * 0.05F);
			}
			// +12% dodatkowych obrażeń w walce.
			if (source.getDirectEntity() instanceof Player attacker && wears(attacker) && entity != attacker && damageTaken > 0.0F) {
				inside = true;
				try {
					entity.invulnerableTime = 0;
					entity.hurtServer(level, source, damageTaken * 0.12F);
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
						hp.addTransientModifier(new AttributeModifier(HP_ID, 10.0, AttributeModifier.Operation.ADD_VALUE));
					} else if (!on && has) {
						hp.removeModifier(HP_ID);
						if (p.getHealth() > p.getMaxHealth()) {
							p.setHealth(p.getMaxHealth());
						}
					}
				}
				if (on) {
					p.resetFallDistance(); // 100% odporności na upadek
				}
			}
		});
	}
}
