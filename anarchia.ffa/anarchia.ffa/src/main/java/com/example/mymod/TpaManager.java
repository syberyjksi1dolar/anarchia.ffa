package com.example.mymod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/** /tpa (dla każdego): po odliczaniu 5-4-3-2-1 teleportuje w losowe miejsce w obszarze 1500 x 1500. */
public final class TpaManager {
	private TpaManager() {}

	private static final Random RNG = new Random();
	private static final int COUNTDOWN_TICKS = 100; // 5 sekund
	private static final Map<UUID, Integer> WAIT = new HashMap<>();

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> {
			for (String lit : new String[] {"tpa"}) {
				d.register(Commands.literal(lit).executes(ctx -> {
				ServerPlayer p = ctx.getSource().getPlayer();
				if (p == null) {
					ctx.getSource().sendFailure(Component.literal("Tej komendy może użyć tylko gracz."));
					return 0;
				}
				MinecraftServer server = ctx.getSource().getServer();
				if (p.level() != server.overworld()) {
					ctx.getSource().sendFailure(Component.literal("/tpa działa tylko w głównym świecie."));
					return 0;
				}
				if (Antylogout.inCombat(p, server)) {
					ctx.getSource().sendFailure(Component.literal("Nie możesz użyć /tpa podczas walki (antylogout)."));
					return 0;
				}
				if (WAIT.containsKey(p.getUUID())) {
					ctx.getSource().sendFailure(Component.literal("Teleportacja już trwa."));
					return 0;
				}
				WAIT.put(p.getUUID(), COUNTDOWN_TICKS);
				return 1;
	
			}));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> WAIT.remove(h.getPlayer().getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(TpaManager::tick);
	}

	private static void title(ServerPlayer p, String text, ChatFormatting color) {
		p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 22, 3));
		p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(text).withStyle(color, ChatFormatting.BOLD)));
	}

	private static void tick(MinecraftServer server) {
		if (WAIT.isEmpty()) return;
		Iterator<Map.Entry<UUID, Integer>> it = WAIT.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null || !p.isAlive()) {
				it.remove();
				continue;
			}
			int left = e.getValue();
			if (left % 20 == 0 && left > 0) {
				title(p, "Teleportacja za " + (left / 20), ChatFormatting.YELLOW);
			}
			if (left <= 0) {
				it.remove();
				teleport(server, p);
			} else {
				e.setValue(left - 1);
			}
		}
	}

	private static void teleport(MinecraftServer server, ServerPlayer p) {
		ServerLevel level = server.overworld();
		int half = BotManager.HALF;
		for (int tries = 0; tries < 30; tries++) {
			int x = RNG.nextInt(half * 2 + 1) - half;
			int z = RNG.nextInt(half * 2 + 1) - half;
			if (Math.abs(x) < 14 && Math.abs(z) < 14) continue; // nie na spawnie
			level.getChunk(x >> 4, z >> 4); // wczytuje teren
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (level.getFluidState(new BlockPos(x, y - 1, z)).is(net.minecraft.tags.FluidTags.WATER)
					|| level.getFluidState(new BlockPos(x, y - 1, z)).is(net.minecraft.tags.FluidTags.LAVA)) continue;
			p.connection.teleport(x + 0.5, y, z + 0.5, p.getYRot(), p.getXRot());
			title(p, "Teleportacja!", ChatFormatting.GREEN);
			return;
		}
		p.sendSystemMessage(Component.literal("Nie udało się znaleźć bezpiecznego miejsca. Spróbuj ponownie.").withStyle(ChatFormatting.RED));
	}
}
