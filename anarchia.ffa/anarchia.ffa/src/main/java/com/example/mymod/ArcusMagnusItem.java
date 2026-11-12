package com.example.mymod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Arcus Magnus: łuk z nieskończonymi strzałami (strzał prawym kliknięciem).
 * Trafienia pod rząd zadają 1, 2, 4, 8, 16, 32, 64 obrażeń (potem stale 64). Pudło resetuje serię.
 * Na ekranie widać bieżące obrażenia i w nawiasie następne, np. "8 obrażeń (16)".
 */
public class ArcusMagnusItem extends Item {
	public static final int MAX_DAMAGE = 64;

	private record Tracked(ServerLevel level, Entity arrow, UUID owner, long start) {
	}

	private static final List<Tracked> ARROWS = new ArrayList<>();
	private static final Map<UUID, Integer> CHAIN = new HashMap<>(); // aktualne obrażenia następnego trafienia

	public ArcusMagnusItem(Properties properties) {
		super(properties);
	}

	private static int current(UUID owner) {
		return CHAIN.getOrDefault(owner, 1);
	}

	private static void show(ServerPlayer player) {
		int cur = current(player.getUUID());
		int next = Math.min(MAX_DAMAGE, cur * 2);
		player.sendSystemMessage(Component.literal(cur + " obrażeń (" + next + ")"), true);
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (source.getDirectEntity() == null || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			UUID arrowId = source.getDirectEntity().getUUID();
			Iterator<Tracked> it = ARROWS.iterator();
			while (it.hasNext()) {
				Tracked t = it.next();
				if (!t.arrow().getUUID().equals(arrowId)) {
					continue;
				}
				it.remove();
				int value = current(t.owner());
				float extra = value - damageTaken;
				if (extra > 0.0F) {
					entity.invulnerableTime = 0;
					entity.hurtServer(level, source, extra);
				}
				CHAIN.put(t.owner(), Math.min(MAX_DAMAGE, value * 2)); // następne trafienie podwojone
				ServerPlayer owner = level.getServer().getPlayerList().getPlayer(t.owner());
				if (owner != null) {
					show(owner);
				}
				return;
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Tracked> it = ARROWS.iterator();
			while (it.hasNext()) {
				Tracked t = it.next();
				boolean slow = t.arrow().getDeltaMovement().lengthSqr() < 0.01 && t.arrow().tickCount > 3;
				if (t.arrow().isRemoved() || slow || t.level().getGameTime() - t.start() > 100) {
					t.arrow().discard();
					it.remove();
					CHAIN.put(t.owner(), 1); // pudło: seria od początku
					ServerPlayer owner = server.getPlayerList().getPlayer(t.owner());
					if (owner != null) {
						show(owner);
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ARROWS.clear();
			CHAIN.clear();
		});
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
			var arrow = EntityType.ARROW.create(serverLevel, EntitySpawnReason.TRIGGERED);
			if (arrow != null) {
				arrow.setOwner(player);
				arrow.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
				arrow.setBaseDamage(0.2); // zadaje 1, resztę dodajemy sami
				arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 3.0F, 1.0F);
				serverLevel.addFreshEntity(arrow);
				ARROWS.add(new Tracked(serverLevel, arrow, player.getUUID(), serverLevel.getGameTime()));
				show(sp);
			}
			player.getCooldowns().addCooldown(stack, 8);
		}
		return InteractionResult.SUCCESS;
	}
}
