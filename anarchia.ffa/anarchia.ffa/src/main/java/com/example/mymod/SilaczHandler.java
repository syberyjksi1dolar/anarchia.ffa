package com.example.mymod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Kostium Siłacza (komplet: głowa, klata, spodnie, buty):
 * Siła I, +3 serca, 3x Shift (szybko) = Siła III na 30 s (cooldown 2 min),
 * po śmierci kostium zostaje na graczu.
 */
public final class SilaczHandler {
	private static final Identifier HP_ID = Identifier.fromNamespaceAndPath(MyMod.MOD_ID, "silacz_hp");
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private static final Map<UUID, Boolean> WAS_SNEAKING = new HashMap<>();
	private static final Map<UUID, Deque<Long>> PRESSES = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
	private static final Map<UUID, ItemStack[]> SAVED = new HashMap<>();

	private SilaczHandler() {
	}

	private static boolean isPiece(ItemStack s) {
		return s.is(ModItems.SILACZ_HELM) || s.is(ModItems.SILACZ_KLATA) || s.is(ModItems.SILACZ_SPODNIE) || s.is(ModItems.SILACZ_BUTY);
	}

	public static boolean wearsFullSet(ServerPlayer p) {
		return p.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.SILACZ_HELM)
				&& p.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.SILACZ_KLATA)
				&& p.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.SILACZ_SPODNIE)
				&& p.getItemBySlot(EquipmentSlot.FEET).is(ModItems.SILACZ_BUTY);
	}

	private static void restore(ServerPlayer p, ItemStack[] saved) {
		for (int i = 0; i < SLOTS.length; i++) {
			if (!saved[i].isEmpty() && p.getItemBySlot(SLOTS[i]).isEmpty()) {
				p.setItemSlot(SLOTS[i], saved[i]);
			}
		}
	}

	public static void initialize() {
		// Przed śmiercią zdejmujemy kostium, żeby nie wypadł; oddajemy po respawnie.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && !SAVED.containsKey(p.getUUID())) {
				ItemStack[] saved = new ItemStack[SLOTS.length];
				boolean any = false;
				for (int i = 0; i < SLOTS.length; i++) {
					ItemStack s = p.getItemBySlot(SLOTS[i]);
					if (isPiece(s)) {
						saved[i] = s.copy();
						p.setItemSlot(SLOTS[i], ItemStack.EMPTY);
						any = true;
					} else {
						saved[i] = ItemStack.EMPTY;
					}
				}
				if (any) {
					SAVED.put(p.getUUID(), saved);
				}
			}
			return true;
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			ItemStack[] saved = SAVED.remove(oldPlayer.getUUID());
			if (saved != null) {
				restore(newPlayer, saved);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long now = server.getTickCount();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				UUID id = p.getUUID();

				// Totem uratował gracza: oddajemy kostium od razu.
				ItemStack[] saved = SAVED.get(id);
				if (saved != null && p.isAlive()) {
					restore(p, saved);
					SAVED.remove(id);
				}

				boolean full = wearsFullSet(p);
				var hp = p.getAttribute(Attributes.MAX_HEALTH);
				if (hp != null) {
					boolean has = hp.getModifier(HP_ID) != null;
					if (full && !has) {
						hp.addTransientModifier(new AttributeModifier(HP_ID, 6.0, AttributeModifier.Operation.ADD_VALUE));
					} else if (!full && has) {
						hp.removeModifier(HP_ID);
					}
				}
				if (!full) {
					WAS_SNEAKING.remove(id);
					PRESSES.remove(id);
					continue;
				}
				if (now % 10 == 0) {
					p.addEffect(new net.minecraft.world.effect.MobEffectInstance(EffectUtil.get("strength"), 40, 0, true, false, true));
				}

				boolean sneaking = p.isShiftKeyDown();
				boolean was = WAS_SNEAKING.getOrDefault(id, false);
				WAS_SNEAKING.put(id, sneaking);
				if (sneaking && !was) {
					Deque<Long> q = PRESSES.computeIfAbsent(id, k -> new ArrayDeque<>());
					q.addLast(now);
					while (!q.isEmpty() && now - q.peekFirst() > 30) { // okno ok. 1,5 s
						q.removeFirst();
					}
					if (q.size() >= 3) {
						q.clear();
						long until = COOLDOWN_UNTIL.getOrDefault(id, 0L);
						if (now < until) {
							p.sendSystemMessage(Component.literal("Siła III: cooldown jeszcze " + ((until - now) / 20 + 1) + " s"), true);
						} else {
							p.addEffect(new net.minecraft.world.effect.MobEffectInstance(EffectUtil.get("strength"), 30 * 20, 2, false, true, true));
							COOLDOWN_UNTIL.put(id, now + 120 * 20);
							p.sendSystemMessage(Component.literal("Siła III na 30 sekund!"), true);
						}
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			WAS_SNEAKING.clear();
			PRESSES.clear();
			COOLDOWN_UNTIL.clear();
			SAVED.clear();
		});
	}
}
