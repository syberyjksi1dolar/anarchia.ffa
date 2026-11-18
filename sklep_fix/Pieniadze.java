package com.example.mymod;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/** Pieniądze graczy (w groszach, 100 groszy = 1$). Zapis: config/anarchia_ffa_pieniadze.json. */
public final class Pieniadze {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Long> DATA = new HashMap<>();

	private Pieniadze() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("anarchia_ffa_pieniadze.json");
	}

	public static long get(ServerPlayer p) {
		return DATA.getOrDefault(p.getUUID().toString(), 0L);
	}

	/** Dodaje pieniądze i zapisuje plik. Zwraca false, gdy zapis się nie udał (wtedy nic nie zmieniamy). */
	public static boolean add(ServerPlayer p, long cents) {
		String key = p.getUUID().toString();
		Long before = DATA.get(key);
		DATA.merge(key, cents, Long::sum);
		if (!save()) {
			if (before == null) {
				DATA.remove(key);
			} else {
				DATA.put(key, before);
			}
			return false;
		}
		return true;
	}

	/** Np. 1240 groszy = "12,40$". */
	public static String format(long cents) {
		return String.format(Locale.ROOT, "%d,%02d$", cents / 100, Math.abs(cents % 100));
	}

	public static String format(ServerPlayer p) {
		return format(get(p));
	}

	private static void load() {
		DATA.clear();
		try {
			Path f = file();
			if (Files.exists(f)) {
				Type type = new TypeToken<Map<String, Long>>() {
				}.getType();
				Map<String, Long> read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), type);
				if (read != null) {
					DATA.putAll(read);
				}
			}
		} catch (IOException | RuntimeException e) {
			MyMod.LOGGER.warn("Nie udało się wczytać pieniędzy: {}", e.toString());
		}
	}

	private static boolean save() {
		try {
			Files.writeString(file(), GSON.toJson(DATA), StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			MyMod.LOGGER.warn("Nie udało się zapisać pieniędzy: {}", e.toString());
			return false;
		}
	}

	public static void initialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> load());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());
	}
}
