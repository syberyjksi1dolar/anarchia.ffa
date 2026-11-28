package com.example.mymod;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * Panel ze statystykami po prawej stronie ekranu, osobny dla każdego gracza (wysyłany pakietami, bez
 * dotykania scoreboardu serwera). Pokazuje: gracza, rangę, serca, czas gry, zabójstwa, śmierci i serię.
 * Statystyki zapisują się w config/anarchia_ffa_statystyki.json.
 */
public final class StatsSidebar {
	private static final String OBJ_NAME = "anarchia_stats";
	private static final int LINES = 8;

	public static class Stat {
		public int kills;
		public int botKills;
		public int deaths;
		public int streak;
		public long seconds;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Stat> DATA = new HashMap<>();
	private static final Scoreboard DUMMY = new Scoreboard();
	private static final Objective OBJ = new Objective(DUMMY, OBJ_NAME, ObjectiveCriteria.DUMMY,
			Component.literal("ANARCHIA FFA").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
			ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);

	private StatsSidebar() {
	}

	private static Path file() {
		return WorldData.file("anarchia_ffa_statystyki.json");
	}

	private static void load() {
		DATA.clear();
		try {
			if (Files.exists(file())) {
				Type type = new TypeToken<Map<String, Stat>>() {
				}.getType();
				Map<String, Stat> read = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), type);
				if (read != null) {
					DATA.putAll(read);
				}
			}
		} catch (IOException | RuntimeException e) {
			MyMod.LOGGER.warn("Nie udało się wczytać statystyk: {}", e.toString());
		}
	}

	private static void save() {
		try {
			Files.writeString(file(), GSON.toJson(DATA), StandardCharsets.UTF_8);
		} catch (IOException e) {
			MyMod.LOGGER.warn("Nie udało się zapisać statystyk: {}", e.toString());
		}
	}

	private static Stat stat(ServerPlayer p) {
		return DATA.computeIfAbsent(p.getUUID().toString(), k -> new Stat());
	}

	private static String time(long seconds) {
		long d = seconds / 86400;
		long h = (seconds % 86400) / 3600;
		long m = (seconds % 3600) / 60;
		return d > 0 ? d + "d " + h + "h" : h + "h " + m + "m";
	}

	private static Component line(String label, Component value) {
		return Component.literal(label).withStyle(ChatFormatting.GRAY).append(value);
	}

	private static Component num(Object value, ChatFormatting color) {
		return Component.literal(String.valueOf(value)).withStyle(color);
	}

	/** Wysyła panel graczowi (przy wejściu) – tworzy cel i ustawia go na pasku bocznym. */
	private static void init(ServerPlayer p) {
		p.connection.send(new ClientboundSetObjectivePacket(OBJ, 0));
		p.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, OBJ));
		refresh(p);
	}

	/** Odświeża linie panelu. */
	private static void refresh(ServerPlayer p) {
		Stat s = stat(p);
		Component[] lines = new Component[LINES];
		lines[0] = line("Gracz: ", Component.literal(p.getName().getString()).withStyle(ChatFormatting.WHITE));
		lines[1] = line("Ranga: ", RangiManager.rankTag(p));
		String guild = BotManager.guildOf(p.getUUID());
		lines[2] = line("Klan: ", guild == null ? num("brak", ChatFormatting.DARK_GRAY)
				: Component.literal("KLAN-" + guild).withStyle(BotManager.guildFormat(guild)));
		lines[3] = line("Serca: ", num((int) (p.getMaxHealth() / 2.0F), ChatFormatting.RED));
		lines[4] = line("Zabójstwa: ", num(s.kills, ChatFormatting.GREEN));
		lines[5] = line("Zabite boty: ", num(s.botKills, ChatFormatting.AQUA));
		lines[6] = line("Śmierci: ", num(s.deaths, ChatFormatting.DARK_RED));
		lines[7] = line("Pieniądze: ", num(Pieniadze.format(p), ChatFormatting.GOLD));
		p.connection.send(new ClientboundSetObjectivePacket(OBJ, 2));
		for (int i = 0; i < LINES; i++) {
			p.connection.send(new ClientboundSetScorePacket("l" + i, OBJ_NAME, LINES - i, Optional.of(lines[i]),
					Optional.of(BlankFormat.INSTANCE)));
		}
	}

	public static void initialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> load());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> init(handler.getPlayer()));

		// Zabójstwa: gracze i agresywne moby. Śmierci: tylko z ręki bytu (moba lub gracza).
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer victim && source.getEntity() != null) {
				Stat v = stat(victim);
				v.deaths++;
				v.streak = 0;
			}
			if (source.getEntity() instanceof ServerPlayer killer && entity != killer
					&& (entity instanceof ServerPlayer || entity instanceof Enemy)) {
				Stat k = stat(killer);
				k.kills++;
				k.streak++;
			}
			if (source.getEntity() instanceof ServerPlayer bk && entity instanceof net.minecraft.world.entity.decoration.Mannequin mq
					&& BotManager.isBot(mq)) {
				stat(bk).botKills++; // licznik zabitych botów
			}
		});

		// Co sekundę: czas gry i odświeżenie panelu; zapis co minutę.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long t = server.getTickCount();
			if (t % 20 != 0) {
				return;
			}
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				stat(p).seconds++;
				refresh(p);
			}
			if (t % 1200 == 0) {
				save();
			}
		});
	}
}
