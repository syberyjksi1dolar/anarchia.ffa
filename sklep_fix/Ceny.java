package com.example.mymod;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Ceny skupu w sklepie (w $ za sztukę). Plik config/anarchia_ffa_ceny.json można edytować bez nowej wersji moda.
 * Klucze to nazwy przedmiotów z gry; specjalne: "drewno" (każda kłoda), "ominous_banner" (baner złowieszczy)
 * i "ominous_bottle" (cena za 1 poziom, każdy kolejny poziom dokłada tyle samo).
 */
public final class Ceny {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Double> DEFAULTS = new LinkedHashMap<>();
	private static final Map<String, Double> PRICES = new HashMap<>();

	static {
		DEFAULTS.put("drewno", 2.0);
		DEFAULTS.put("quartz", 3.0);
		DEFAULTS.put("raw_copper", 0.40);
		DEFAULTS.put("copper_ingot", 0.60);
		DEFAULTS.put("coal", 0.20);
		DEFAULTS.put("charcoal", 3.10);
		DEFAULTS.put("slime_ball", 1.0);
		DEFAULTS.put("dragon_breath", 100.0);
		DEFAULTS.put("crafting_table", 0.20);
		DEFAULTS.put("wind_charge", 3.0);
		DEFAULTS.put("pufferfish", 0.50);
		DEFAULTS.put("snowball", 0.03);
		DEFAULTS.put("shield", 0.60);
		DEFAULTS.put("ominous_banner", 4.0);
		DEFAULTS.put("arrow", 0.70);
		DEFAULTS.put("iron_ingot", 1.0);
		DEFAULTS.put("raw_iron", 0.50);
		DEFAULTS.put("emerald", 1.0);
		DEFAULTS.put("pointed_dripstone", 1.30);
		DEFAULTS.put("cobweb", 0.40);
		DEFAULTS.put("ominous_bottle", 10.0);
		DEFAULTS.put("diamond", 14.0);
		DEFAULTS.put("lapis_lazuli", 0.40);
		DEFAULTS.put("apple", 30.0);
		DEFAULTS.put("cobblestone", 0.02);
		DEFAULTS.put("water_bucket", 3.0);
		DEFAULTS.put("redstone", 0.20);
		DEFAULTS.put("fire_charge", 2.0);
		DEFAULTS.put("tnt", 10.0);
	}

	private Ceny() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("anarchia_ffa_ceny.json");
	}

	private static void load() {
		PRICES.clear();
		PRICES.putAll(DEFAULTS);
		try {
			Path f = file();
			if (Files.exists(f)) {
				Type type = new TypeToken<Map<String, Double>>() {
				}.getType();
				Map<String, Double> read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), type);
				if (read != null) {
					PRICES.putAll(read);
				}
			} else {
				Files.writeString(f, GSON.toJson(DEFAULTS), StandardCharsets.UTF_8);
			}
		} catch (IOException | RuntimeException e) {
			MyMod.LOGGER.warn("Nie udało się wczytać cen: {}", e.toString());
		}
	}

	/** Klucz ceny dla przedmiotu albo null, gdy nie jest skupowany. */
	public static String keyOf(ItemStack s) {
		if (s.isEmpty()) {
			return null;
		}
		String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		if (path.endsWith("_log")) {
			return "drewno";
		}
		if (s.is(Items.WHITE_BANNER)) {
			Component n = s.get(DataComponents.ITEM_NAME);
			if (n != null && n.getContents() instanceof TranslatableContents t
					&& t.getKey().equals("block.minecraft.ominous_banner")) {
				return "ominous_banner";
			}
			return null;
		}
		return DEFAULTS.containsKey(path) && !path.equals("drewno") && !path.equals("ominous_banner") ? path : null;
	}

	/** Cena jednej sztuki w groszach (-1, gdy nie skupujemy). */
	public static long price(ItemStack s) {
		String key = keyOf(s);
		if (key == null) {
			return -1;
		}
		double base = PRICES.getOrDefault(key, DEFAULTS.get(key));
		long cents = Math.round(base * 100.0);
		if (key.equals("ominous_bottle")) {
			var amp = s.get(DataComponents.OMINOUS_BOTTLE_AMPLIFIER);
			int level = amp == null ? 1 : amp.value() + 1;
			return cents * level;
		}
		return cents;
	}

	/** Cena z klucza (do podpisów w menu), w groszach. */
	public static long priceOfKey(String key) {
		return Math.round(PRICES.getOrDefault(key, DEFAULTS.getOrDefault(key, 0.0)) * 100.0);
	}

	public static void initialize() {
		PRICES.putAll(DEFAULTS);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> load());
	}
}
