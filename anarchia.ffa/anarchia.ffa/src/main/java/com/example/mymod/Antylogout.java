package com.example.mymod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.Mannequin;

/** Antylogout: walka z graczem lub botem blokuje /spawn na 15 s. Dodaje też /spawn. */
public final class Antylogout {
	private Antylogout() {}

	public static final int SECONDS = 15;
	private static final Map<UUID, Long> UNTIL = new HashMap<>();

	public static boolean inCombat(ServerPlayer p, MinecraftServer s) {
		Long u = UNTIL.get(p.getUUID());
		return u != null && u > s.getTickCount();
	}

	private static void tag(ServerPlayer p) {
		UNTIL.put(p.getUUID(), p.level().getServer().getTickCount() + SECONDS * 20L);
	}

	public static void initialize() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer a)) return InteractionResult.PASS;
			if (entity instanceof ServerPlayer v) {
				tag(a);
				tag(v);
			} else if (entity instanceof Mannequin m && BotManager.isBot(m)) {
				tag(a);
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				Long u = UNTIL.get(p.getUUID());
				if (u == null) continue;
				long left = (u - server.getTickCount()) / 20;
				if (left > 0) {
					p.sendSystemMessage(Component.literal("Antylogout: " + left + " s").withStyle(ChatFormatting.RED), true);
				} else {
					UNTIL.remove(p.getUUID());
					p.sendSystemMessage(Component.literal("Antylogout zakończony.").withStyle(ChatFormatting.GREEN), true);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer p = handler.getPlayer();
			if (inCombat(p, server)) {
				server.getPlayerList().broadcastSystemMessage(
						Component.literal(p.getName().getString() + " wylogował się podczas walki!").withStyle(ChatFormatting.RED), false);
			}
			UNTIL.remove(p.getUUID());
		});
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("spawn").executes(ctx -> {
			ServerPlayer p = ctx.getSource().getPlayer();
			if (p == null) {
				ctx.getSource().sendFailure(Component.literal("Tej komendy może użyć tylko gracz."));
				return 0;
			}
			if (inCombat(p, ctx.getSource().getServer())) {
				ctx.getSource().sendFailure(Component.literal("Nie możesz użyć /spawn podczas walki (antylogout)."));
				return 0;
			}
			p.connection.teleport(BotManager.SPAWN_X, BotManager.SPAWN_Y, BotManager.SPAWN_Z, p.getYRot(), p.getXRot());
			return 1;
		})));
	}
}
