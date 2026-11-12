package com.example.mymod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Kostium Dolara (komplet): +6 serc, +2 HP do obrażeń w walce, -7% otrzymywanych obrażeń.
 * 3x Shift: przez 30 s zostawiasz za sobą ślad śniegu 3x3 (każdy kafelek znika po 10 s),
 * kto w niego wejdzie dostaje Słabość II i Spowolnienie II; ty masz Szybkość II. Cooldown 2:30.
 */
public final class DolarHandler {
	private static final Identifier HP_ID = Identifier.fromNamespaceAndPath(MyMod.MOD_ID, "dolar_hp");
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private record Snow(ServerLevel level, BlockPos pos, long expire, UUID owner) {
	}

	private static final Map<UUID, Boolean> WAS_SNEAKING = new HashMap<>();
	private static final Map<UUID, Deque<Long>> PRESSES = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> ACTIVE_UNTIL = new HashMap<>();
	private static final Map<UUID, ItemStack[]> SAVED = new HashMap<>();
	private static final List<Snow> SNOW = new ArrayList<>();
	private static boolean inside = false;

	private DolarHandler() {
	}

	private static boolean isPiece(ItemStack s) {
		return s.is(ModItems.DOLAR_HELM) || s.is(ModItems.DOLAR_KLATA) || s.is(ModItems.DOLAR_SPODNIE) || s.is(ModItems.DOLAR_BUTY);
	}

	public static boolean wearsFullSet(Player p) {
		return p.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.DOLAR_HELM)
				&& p.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.DOLAR_KLATA)
				&& p.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.DOLAR_SPODNIE)
				&& p.getItemBySlot(EquipmentSlot.FEET).is(ModItems.DOLAR_BUTY);
	}

	private static void restore(ServerPlayer p, ItemStack[] saved) {
		for (int i = 0; i < SLOTS.length; i++) {
			if (!saved[i].isEmpty() && p.getItemBySlot(SLOTS[i]).isEmpty()) {
				p.setItemSlot(SLOTS[i], saved[i]);
			}
		}
	}

	private static void removeSnow(Snow s) {
		if (s.level().getBlockState(s.pos()).is(Blocks.SNOW)) {
			s.level().setBlock(s.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	public static void initialize() {
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

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (inside || blocked || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			// -7% obrażeń: zwracamy 7% życia, jeśli gracz przeżył.
			if (entity instanceof Player victim && wearsFullSet(victim) && victim.isAlive()) {
				victim.heal(damageTaken * 0.07F);
			}
			// +2 HP do obrażeń w walce.
			if (source.getDirectEntity() instanceof Player attacker && wearsFullSet(attacker) && entity != attacker) {
				inside = true;
				try {
					entity.invulnerableTime = 0;
					entity.hurtServer(level, source, 2.0F);
				} finally {
					inside = false;
				}
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long now = server.getTickCount();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				UUID id = p.getUUID();
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
						hp.addTransientModifier(new AttributeModifier(HP_ID, 12.0, AttributeModifier.Operation.ADD_VALUE));
					} else if (!full && has) {
						hp.removeModifier(HP_ID);
					}
				}
				if (!full) {
					WAS_SNEAKING.remove(id);
					PRESSES.remove(id);
					ACTIVE_UNTIL.remove(id);
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
							p.sendSystemMessage(Component.literal("Śnieg: cooldown jeszcze " + ((until - now) / 20 + 1) + " s"), true);
						} else {
							ACTIVE_UNTIL.put(id, now + 30 * 20);
							COOLDOWN_UNTIL.put(id, now + 150 * 20);
							p.sendSystemMessage(Component.literal("Śnieżny ślad na 30 sekund!"), true);
						}
					}
				}

				// Aktywna umiejętność: szybkość i ślad śniegu 3x3 pod nogami.
				Long active = ACTIVE_UNTIL.get(id);
				if (active != null) {
					if (now >= active) {
						ACTIVE_UNTIL.remove(id);
					} else if (p.level() instanceof ServerLevel level) {
						p.addEffect(new net.minecraft.world.effect.MobEffectInstance(EffectUtil.get("speed"), 40, 1, true, false, true));
						BlockPos feet = p.blockPosition();
						for (int dx = -1; dx <= 1; dx++) {
							for (int dz = -1; dz <= 1; dz++) {
								BlockPos pos = feet.offset(dx, 0, dz);
								if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolidRender()) {
									level.setBlock(pos, Blocks.SNOW.defaultBlockState(), Block.UPDATE_ALL);
									SNOW.add(new Snow(level, pos, level.getGameTime() + 10 * 20, id));
								}
							}
						}
					}
				}
			}

			// Znikanie śniegu po 10 s i efekty dla wchodzących.
			Iterator<Snow> it = SNOW.iterator();
			while (it.hasNext()) {
				Snow s = it.next();
				if (s.level().getGameTime() >= s.expire()) {
					removeSnow(s);
					it.remove();
					continue;
				}
				if (now % 5 == 0) {
					for (LivingEntity e : s.level().getEntitiesOfClass(LivingEntity.class,
							new net.minecraft.world.phys.AABB(s.pos()).inflate(0.2, 0.5, 0.2),
							x -> x.isAlive() && !x.getUUID().equals(s.owner()))) {
						EffectUtil.give(e, "weakness", 5 * 20, 1);
						EffectUtil.give(e, "slowness", 5 * 20, 1);
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Snow s : SNOW) {
				removeSnow(s);
			}
			SNOW.clear();
			WAS_SNEAKING.clear();
			PRESSES.clear();
			COOLDOWN_UNTIL.clear();
			ACTIVE_UNTIL.clear();
			SAVED.clear();
		});
	}
}
