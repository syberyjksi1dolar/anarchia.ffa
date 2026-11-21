package com.example.mymod;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /inkognito: przełącznik. Losowy nick (litery i cyfry) nad głową i w czacie; prawdziwa tożsamość zostaje w systemie. */
public final class Inkognito {
	private Inkognito() {}

	private static final String CH = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	private static final Random RNG = new Random();
	private static final Set<UUID> ON = new HashSet<>();
	private static final Map<UUID, String> FAKE = new HashMap<>();

	public static boolean isOn(ServerPlayer p) {
		return ON.contains(p.getUUID());
	}

	/** Czy boty mogą rozpoznać prawdziwą rangę gracza (nie, gdy ma włączone inkognito). */
	public static boolean botsSeeRank(ServerPlayer p) {
		return !isOn(p);
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("inkognito").executes(ctx -> {
			ServerPlayer p = ctx.getSource().getPlayer();
			if (p == null) {
				ctx.getSource().sendFailure(Component.literal("Tej komendy może użyć tylko gracz."));
				return 0;
			}
			if (!RangiManager.isStaff(p)) {
				ctx.getSource().sendFailure(Component.literal("Tylko administrator lub twórca może użyć /inkognito."));
				return 0;
			}
			toggle(p);
			return 1;
		})));
	}

	private static void toggle(ServerPlayer p) {
		if (ON.remove(p.getUUID())) {
			FAKE.remove(p.getUUID());
			p.setCustomName(null);
			p.setCustomNameVisible(false);
			RangiManager.refreshTeam(p);
			p.sendSystemMessage(Component.literal("Inkognito wyłączone. Boty znów rozpoznają Twoją rangę.").withStyle(ChatFormatting.GREEN));
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 7; i++) sb.append(CH.charAt(RNG.nextInt(CH.length())));
		String nick = sb.toString();
		ON.add(p.getUUID());
		FAKE.put(p.getUUID(), nick);
		p.setCustomName(Component.literal(nick));
		p.setCustomNameVisible(true);
		RangiManager.refreshTeam(p);
		p.sendSystemMessage(Component.literal("Inkognito włączone. Twój nick: " + nick + ". Boty traktują Cię jak zwykłego gracza.").withStyle(ChatFormatting.GREEN));
		MyMod.LOGGER.info("Inkognito: {} -> {}", p.getName().getString(), nick);
	}
}
