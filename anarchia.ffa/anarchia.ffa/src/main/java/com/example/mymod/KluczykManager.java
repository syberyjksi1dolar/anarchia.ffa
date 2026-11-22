package com.example.mymod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Dzień Kluczyka: automat o 15:00, 18:00, 21:00, 00:00 oraz komenda /DK (tylko administrator). */
public final class KluczykManager {
	private KluczykManager() {}

	private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
	private static final int MAX_KEYS = 126;
	private static final int AUTO_SECONDS = 60;
	private static final int[] HOURS = {15, 18, 21, 0};
	private static final Random RNG = new Random();

	private static final class Event {
		double x1, y1, z1, x2, y2, z2;
		int total, dropped, ticks, elapsed;
	}

	private static final List<Event> EVENTS = new ArrayList<>();
	private static String lastAuto = "";

	private static Item[] keys() {
		return new Item[] {ModItems.KLUCZ_RZADKA, ModItems.KLUCZ_EPICKA, ModItems.KLUCZ_ANARCHICZNA, ModItems.KLUCZ_PETY,
				ModItems.KLUCZ_KOSTIUMY, ModItems.KLUCZ_DOLAROWA, ModItems.KLUCZ_EVENTOWA, ModItems.KLUCZ_SWIATECZNA,
				ModItems.KLUCZ_WALENTYNKOWA, ModItems.KLUCZ_HALLOWEENOWA, ModItems.KLUCZ_SZKOLNA,
				ModItems.KLUCZ_NOWE_EVENTOWKI, ModItems.KLUCZ_ZAJACA};
	}

	private static Path file() {
		return WorldData.file("anarchia_ffa_dk.txt");
	}

	public static void initialize() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			lastAuto = "";
			try {
				if (Files.exists(file())) lastAuto = Files.readString(file(), StandardCharsets.UTF_8).trim();
			} catch (IOException e) {
				MyMod.LOGGER.warn("DK: nie wczytano: {}", e.toString());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(KluczykManager::tick);
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("DK")
				.then(Commands.argument("argumenty", StringArgumentType.greedyString()).executes(ctx -> {
					CommandSourceStack s = ctx.getSource();
					ServerPlayer pl = s.getPlayer();
					if (pl != null && !RangiManager.isAdminPlayer(pl)) {
						s.sendFailure(Component.literal("Tylko administrator może użyć /DK."));
						return 0;
					}
					return runCommand(s, StringArgumentType.getString(ctx, "argumenty"));
				}))));
	}

	private static int runCommand(CommandSourceStack s, String args) {
		String[] t = args.trim().split("\\s+");
		if (t.length != 8) {
			s.sendFailure(Component.literal("Użycie: /DK X1 Y1 Z1 X2 Y2 Z2 czas liczba_kluczy (np. /DK 176 -3 188 289 -3 233 40s 126)"));
			return 0;
		}
		try {
			double[] c = new double[6];
			for (int i = 0; i < 6; i++) c[i] = Double.parseDouble(t[i]);
			String tm = t[6].toLowerCase();
			int sec;
			if (tm.endsWith("m")) sec = Integer.parseInt(tm.substring(0, tm.length() - 1)) * 60;
			else if (tm.endsWith("s")) sec = Integer.parseInt(tm.substring(0, tm.length() - 1));
			else sec = Integer.parseInt(tm);
			int count = Integer.parseInt(t[7]);
			if (sec < 1 || count < 1) throw new NumberFormatException();
			if (count > MAX_KEYS) count = MAX_KEYS;
			start(s.getServer(), c[0], c[1], c[2], c[3], c[4], c[5], sec, count);
			final int shown = count;
			s.sendSuccess(() -> Component.literal("Dzień Kluczyka rozpoczęty: " + shown + " kluczy, " + sec + " s."), true);
			return 1;
		} catch (NumberFormatException e) {
			s.sendFailure(Component.literal("Nieprawidłowe liczby. Przykład czasu: 40s lub 2m."));
			return 0;
		}
	}

	private static void start(MinecraftServer server, double x1, double y1, double z1, double x2, double y2, double z2, int seconds, int count) {
		Event e = new Event();
		e.x1 = Math.min(x1, x2); e.x2 = Math.max(x1, x2);
		e.y1 = Math.min(y1, y2); e.y2 = Math.max(y1, y2);
		e.z1 = Math.min(z1, z2); e.z2 = Math.max(z1, z2);
		e.total = Math.min(count, MAX_KEYS);
		e.ticks = Math.max(1, seconds * 20);
		EVENTS.add(e);
		server.getPlayerList().broadcastSystemMessage(
				Component.literal("Dzień Kluczyka się zaczyna! Klucze spadają na mapie.").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level == null) return;
		// automat
		if (server.getTickCount() % 100 == 0) {
			ZonedDateTime now = ZonedDateTime.now(ZONE);
			boolean hit = false;
			for (int h : HOURS) if (now.getHour() == h && now.getMinute() == 0) hit = true;
			String key = LocalDate.now(ZONE) + " " + now.getHour();
			if (hit && !key.equals(lastAuto)) {
				lastAuto = key;
				try {
					Files.writeString(file(), key, StandardCharsets.UTF_8);
				} catch (IOException ex) {
					MyMod.LOGGER.warn("DK: nie zapisano: {}", ex.toString());
				}
				start(server, 176, -3, 188, 289, -3, 233, AUTO_SECONDS, MAX_KEYS);
			}
		}
		// rozrzucanie
		Item[] ks = keys();
		for (Iterator<Event> it = EVENTS.iterator(); it.hasNext();) {
			Event e = it.next();
			e.elapsed++;
			int should = (int) Math.min(e.total, (long) e.total * e.elapsed / e.ticks);
			while (e.dropped < should) {
				double x = e.x1 + RNG.nextDouble() * (e.x2 - e.x1);
				double z = e.z1 + RNG.nextDouble() * (e.z2 - e.z1);
				double y = e.y1 + RNG.nextDouble() * (e.y2 - e.y1) + 0.5;
				level.getChunkAt(BlockPos.containing(x, y, z));
				level.addFreshEntity(new ItemEntity(level, x, y, z, new ItemStack(ks[RNG.nextInt(ks.length)])));
				e.dropped++;
			}
			if (e.dropped >= e.total) it.remove();
		}
	}
}
