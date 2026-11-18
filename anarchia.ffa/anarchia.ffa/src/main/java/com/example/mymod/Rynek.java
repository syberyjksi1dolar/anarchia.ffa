package com.example.mymod;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Rynek botów. /wystaw [ilość] [za ile] wystawia przedmiot z ręki; oferty kupują boty (nie gracze), jeśli
 * uznają je za opłacalne i mają budżet. Od każdej transakcji: 3% dla KAŻDEGO operatora serwera i 1% dla
 * KAŻDEGO gracza z rangą TWÓRCA (pełna stawka każdemu, bez dzielenia), reszta dla sprzedającego.
 */
public final class Rynek {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Random RNG = new Random();
	private static final int MAX_OFERT = 20;
	private static final long DEFAULT_REF = 50; // grosze za sztukę dla przedmiotów bez ceny w sklepie
	private static final long MAX_CENA = 1_000_000_000L; // 10 mln $ w groszach

	public static class Offer {
		public long id;
		public String seller;
		public String sellerName;
		public String item; // ścieżka w rejestrze, np. "diamond"
		public int count;
		public long price; // łączna cena w groszach
	}

	private static final List<Offer> OFFERS = new ArrayList<>();
	private static long nextId = 1;

	/** Bot kupujący: budżet, skłonność do przepłacania (premium) i zainteresowania. */
	private static final class Bot {
		final String name;
		final double premium;
		final double activity;
		final long cap;
		final long regenPerMin;
		final String[] likes;
		long budget;

		Bot(String name, double premium, double activity, long capDollars, long regenDollarsPerMin, String... likes) {
			this.name = name;
			this.premium = premium;
			this.activity = activity;
			this.cap = capDollars * 100;
			this.regenPerMin = regenDollarsPerMin * 100;
			this.likes = likes;
			this.budget = this.cap;
		}

		boolean likes(String item) {
			for (String l : likes) {
				if (item.contains(l)) {
					return true;
				}
			}
			return false;
		}
	}

	private static final List<Bot> BOTY = List.of(
			// Kowalski: solidny rzemieślnik, płaci uczciwie za metale i narzędzia.
			new Bot("Bot_Kowalski", 1.6, 0.45, 3000, 120, "iron", "copper", "coal", "ingot", "pickaxe", "sword", "shield"),
			// Zosia: kolekcjonerka, płaci najwięcej, ale rzadko kupuje i tylko rzadkie rzeczy.
			new Bot("Bot_Zosia", 2.4, 0.15, 6000, 200, "diamond", "emerald", "dragon", "quartz", "lapis", "ominous"),
			// Gruby: oportunista, bierze tylko okazje (cena prawie jak w skupie), ale ma najgrubszy portfel.
			new Bot("Bot_Gruby", 1.1, 0.60, 12000, 400),
			// Alchemik: mikstury, kule i dziwne składniki.
			new Bot("Bot_Alchemik", 1.9, 0.30, 2500, 90, "potion", "charge", "slime", "breath", "bottle", "redstone", "pufferfish"),
			// Kapitan: zaopatrzenie wyprawy, drewno, jedzenie, kamień, strzały.
			new Bot("Bot_Kapitan", 1.4, 0.50, 2000, 100, "log", "apple", "arrow", "cobble", "bucket", "table", "web"),
			// Szalony: kupuje impulsywnie i potrafi zapłacić krocie, ale ma mały budżet.
			new Bot("Bot_Szalony", 3.0, 0.20, 800, 60, "tnt", "fire", "charge", "snowball"));

	/** Popyt na przedmiot (0,6-1,6): dryfuje w czasie i spada po zakupach. */
	private static final Map<String, Double> POPYT = new HashMap<>();

	private Rynek() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("anarchia_ffa_rynek.json");
	}

	private static void load() {
		OFFERS.clear();
		try {
			Path f = file();
			if (Files.exists(f)) {
				Type type = new TypeToken<List<Offer>>() {
				}.getType();
				List<Offer> read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), type);
				if (read != null) {
					OFFERS.addAll(read);
				}
			}
		} catch (IOException | RuntimeException e) {
			MyMod.LOGGER.warn("Nie udało się wczytać rynku: {}", e.toString());
		}
		for (Offer o : OFFERS) {
			nextId = Math.max(nextId, o.id + 1);
		}
	}

	private static boolean save() {
		try {
			Files.writeString(file(), GSON.toJson(OFFERS), StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			MyMod.LOGGER.warn("Nie udało się zapisać rynku: {}", e.toString());
			return false;
		}
	}

	private static Item itemOf(String path) {
		for (Item item : BuiltInRegistries.ITEM) {
			if (BuiltInRegistries.ITEM.getKey(item).getPath().equals(path)) {
				return item;
			}
		}
		return null;
	}

	private static String nameOf(String path) {
		Item it = itemOf(path);
		return it == null ? path : new ItemStack(it).getHoverName().getString();
	}

	private static void give(ServerPlayer p, ItemStack stack) {
		if (!p.getInventory().add(stack)) {
			p.drop(stack, false);
		}
	}

	// ===== komendy =====

	private static long parseCents(String s) {
		try {
			double v = Double.parseDouble(s.replace(',', '.').replace("$", ""));
			if (Double.isNaN(v) || Double.isInfinite(v)) {
				return -1;
			}
			return Math.round(v * 100.0);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static int wystaw(CommandSourceStack src, int ilosc, String cenaText) {
		ServerPlayer p = src.getPlayer();
		if (p == null) {
			src.sendFailure(Component.literal("Tej komendy może użyć tylko gracz."));
			return 0;
		}
		long cena = parseCents(cenaText);
		if (cena < 1 || cena > MAX_CENA) {
			src.sendFailure(Component.literal("Podaj poprawną cenę, np. /wystaw 32 500 (cena za całą ilość)."));
			return 0;
		}
		ItemStack hand = p.getMainHandItem();
		if (hand.isEmpty()) {
			src.sendFailure(Component.literal("Weź do ręki przedmiot, który chcesz wystawić."));
			return 0;
		}
		if (!hand.getComponentsPatch().isEmpty()) {
			src.sendFailure(Component.literal("Tego przedmiotu nie da się wystawić (ma zaklęcia, nazwę albo jest uszkodzony)."));
			return 0;
		}
		int mine = 0;
		for (Offer o : OFFERS) {
			if (o.seller.equals(p.getUUID().toString())) {
				mine++;
			}
		}
		if (mine >= MAX_OFERT) {
			src.sendFailure(Component.literal("Masz już " + MAX_OFERT + " ofert. Wycofaj je komendą /wycofaj."));
			return 0;
		}
		Inventory inv = p.getInventory();
		int have = 0;
		int[] take = new int[36];
		for (int i = 0; i < 36 && have < ilosc; i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, hand)) {
				int n = Math.min(ilosc - have, s.getCount());
				take[i] = n;
				have += n;
			}
		}
		if (have < ilosc) {
			src.sendFailure(Component.literal("Masz tylko " + have + "x " + hand.getHoverName().getString() + "."));
			return 0;
		}
		Offer o = new Offer();
		o.id = nextId++;
		o.seller = p.getUUID().toString();
		o.sellerName = p.getName().getString();
		o.item = BuiltInRegistries.ITEM.getKey(hand.getItem()).getPath();
		o.count = ilosc;
		o.price = cena;
		OFFERS.add(o);
		if (!save()) {
			OFFERS.remove(o);
			src.sendFailure(Component.literal("Nie udało się zapisać oferty. Przedmioty zostały u ciebie."));
			return 0;
		}
		// Przedmioty zabieramy dopiero po zapisaniu oferty.
		for (int i = 0; i < 36; i++) {
			if (take[i] > 0) {
				inv.getItem(i).shrink(take[i]);
			}
		}
		src.sendSuccess(() -> Component.literal("[RYNEK] Wystawiono " + o.count + "x " + nameOf(o.item) + " za "
				+ Pieniadze.formatShort(o.price) + ". Czeka na kupca (wycofasz: /wycofaj).").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int wycofaj(CommandSourceStack src) {
		ServerPlayer p = src.getPlayer();
		if (p == null) {
			src.sendFailure(Component.literal("Tej komendy może użyć tylko gracz."));
			return 0;
		}
		List<Offer> mine = new ArrayList<>();
		for (Offer o : OFFERS) {
			if (o.seller.equals(p.getUUID().toString())) {
				mine.add(o);
			}
		}
		if (mine.isEmpty()) {
			src.sendFailure(Component.literal("Nie masz ofert na rynku."));
			return 0;
		}
		OFFERS.removeAll(mine);
		if (!save()) {
			OFFERS.addAll(mine);
			src.sendFailure(Component.literal("Nie udało się zapisać rynku. Spróbuj ponownie."));
			return 0;
		}
		for (Offer o : mine) {
			Item item = itemOf(o.item);
			if (item == null) {
				continue;
			}
			int left = o.count;
			int max = new ItemStack(item).getMaxStackSize();
			while (left > 0) {
				int n = Math.min(left, max);
				give(p, new ItemStack(item, n));
				left -= n;
			}
		}
		src.sendSuccess(() -> Component.literal("[RYNEK] Wycofano " + mine.size() + " ofert. Przedmioty wróciły do ekwipunku.")
				.withStyle(ChatFormatting.YELLOW), false);
		return 1;
	}

	/** Lista własnych ofert (po kliknięciu jabłka w /sklep). */
	public static void showOffers(ServerPlayer p) {
		int n = 0;
		p.sendSystemMessage(Component.literal("[RYNEK] Boty kupują twoje oferty. Wystaw: /wystaw [ilość] [za ile], wycofaj: /wycofaj.")
				.withStyle(ChatFormatting.GOLD));
		for (Offer o : OFFERS) {
			if (o.seller.equals(p.getUUID().toString())) {
				n++;
				p.sendSystemMessage(Component.literal(" - " + o.count + "x " + nameOf(o.item) + " za " + Pieniadze.formatShort(o.price))
						.withStyle(ChatFormatting.GRAY));
			}
		}
		if (n == 0) {
			p.sendSystemMessage(Component.literal("Nie masz teraz ofert.").withStyle(ChatFormatting.GRAY));
		}
	}

	// ===== prowizje i zakup =====

	/** UUID operatorów serwera z pliku ops.json (także offline). */
	private static List<String> operatorIds() {
		List<String> out = new ArrayList<>();
		try {
			Path f = FabricLoader.getInstance().getGameDir().resolve("ops.json");
			if (Files.exists(f)) {
				JsonElement root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
				if (root.isJsonArray()) {
					for (JsonElement e : root.getAsJsonArray()) {
						if (e.isJsonObject() && e.getAsJsonObject().has("uuid")) {
							out.add(e.getAsJsonObject().get("uuid").getAsString().toLowerCase(Locale.ROOT));
						}
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			MyMod.LOGGER.warn("Nie udało się odczytać ops.json: {}", e.toString());
		}
		return out;
	}

	private static boolean buy(MinecraftServer server, Bot bot, Offer o) {
		if (!OFFERS.contains(o)) {
			return false;
		}
		long total = o.price;
		Map<String, Long> payouts = new LinkedHashMap<>();
		long commissions = 0;
		long admin = total * 3 / 100;
		long tworca = total / 100;
		for (String id : operatorIds()) {
			payouts.merge(id, admin, Long::sum);
			commissions += admin;
		}
		for (String id : RangiManager.creatorIds()) {
			payouts.merge(id.toLowerCase(Locale.ROOT), tworca, Long::sum);
			commissions += tworca;
		}
		if (commissions > total) {
			// Zabezpieczenie: prowizje nigdy nie przekroczą wartości transakcji.
			double k = (double) total / (double) commissions;
			commissions = 0;
			for (Map.Entry<String, Long> e : payouts.entrySet()) {
				e.setValue((long) Math.floor(e.getValue() * k));
				commissions += e.getValue();
			}
		}
		long sellerGets = total - commissions;
		payouts.merge(o.seller, sellerGets, Long::sum);
		// Najpierw usuwamy ofertę (zapis), potem wypłacamy; każdy błąd cofa całość.
		OFFERS.remove(o);
		if (!save()) {
			OFFERS.add(o);
			return false;
		}
		if (!Pieniadze.addAll(payouts)) {
			OFFERS.add(o);
			save();
			return false;
		}
		bot.budget -= total;
		double d = POPYT.getOrDefault(o.item, 1.0);
		POPYT.put(o.item, Math.max(0.6, d * 0.92));

		String nazwa = nameOf(o.item);
		server.getPlayerList().broadcastSystemMessage(Component.literal("[RYNEK] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(bot.name + " kupił " + o.count + "x " + nazwa + " za " + Pieniadze.formatShort(total)
						+ " od gracza " + o.sellerName + "!").withStyle(ChatFormatting.WHITE)), false);
		try {
			ServerPlayer seller = server.getPlayerList().getPlayer(UUID.fromString(o.seller));
			if (seller != null) {
				seller.sendSystemMessage(Component.literal("[RYNEK] Gracz " + bot.name + " kupił Twój przedmiot z rynku!")
						.withStyle(ChatFormatting.GREEN));
				seller.sendSystemMessage(Component.literal("Wpłynęło " + Pieniadze.formatShort(sellerGets) + " (po prowizji).")
						.withStyle(ChatFormatting.GRAY));
			}
		} catch (IllegalArgumentException ignored) {
			// zły UUID w pliku: pomijamy powiadomienie
		}
		return true;
	}

	/** Co 5 sekund: popyt się zmienia, budżety rosną, a każdy bot może kupić jedną ofertę. */
	private static void cycle(MinecraftServer server) {
		for (String k : new ArrayList<>(POPYT.keySet())) {
			double d = POPYT.get(k) + (1.0 - POPYT.get(k)) * 0.02 + (RNG.nextDouble() - 0.5) * 0.06;
			POPYT.put(k, Math.max(0.6, Math.min(1.6, d)));
		}
		if (OFFERS.isEmpty()) {
			for (Bot b : BOTY) {
				b.budget = Math.min(b.cap, b.budget + b.regenPerMin / 12);
			}
			return;
		}
		List<Bot> order = new ArrayList<>(BOTY);
		Collections.shuffle(order, RNG);
		for (Bot b : order) {
			b.budget = Math.min(b.cap, b.budget + b.regenPerMin / 12);
			if (RNG.nextDouble() > b.activity) {
				continue;
			}
			Offer best = null;
			double bestScore = 0;
			for (Offer o : new ArrayList<>(OFFERS)) {
				Item item = itemOf(o.item);
				if (item == null) {
					continue;
				}
				long ref = Ceny.price(new ItemStack(item));
				if (ref < 0) {
					ref = DEFAULT_REF;
				}
				double demand = POPYT.computeIfAbsent(o.item, k -> 0.8 + RNG.nextDouble() * 0.5);
				double prem = b.premium * demand * (b.likes(o.item) ? 1.3 : 1.0);
				double worth = ref * (double) o.count * prem;
				if (o.price > worth || o.price > b.budget * 0.9) {
					continue;
				}
				double score = worth / (double) o.price;
				if (score > bestScore) {
					bestScore = score;
					best = o;
				}
			}
			if (best != null) {
				buy(server, b, best);
			}
		}
	}

	public static void initialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> load());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 100 == 0) {
				cycle(server);
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("wystaw")
				.then(Commands.argument("ilosc", IntegerArgumentType.integer(1, 2304))
						.then(Commands.argument("cena", StringArgumentType.word())
								.executes(ctx -> wystaw(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "ilosc"),
										StringArgumentType.getString(ctx, "cena"))))));
		d.register(Commands.literal("wycofaj").executes(ctx -> wycofaj(ctx.getSource())));
	}
}
