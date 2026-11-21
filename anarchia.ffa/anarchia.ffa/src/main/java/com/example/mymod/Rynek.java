package com.example.mymod;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

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
		public long created; // moment wystawienia (ms); po 24 h oferta wygasa
	}

	private static final long WAZNOSC_MS = 24L * 60L * 60L * 1000L;

	private static boolean expired(Offer o) {
		return System.currentTimeMillis() - o.created >= WAZNOSC_MS;
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
			if (o.created == 0) {
				o.created = System.currentTimeMillis();
			}
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


	// ===== API dla botów-graczy (handel botów między sobą) =====

	static List<Offer> snapshot() {
		return new ArrayList<>(OFFERS);
	}

	static boolean botList(String nick, String item, int count, long price) {
		String id = "bot:" + nick;
		long mine = 0;
		for (Offer x : OFFERS) {
			if (id.equals(x.seller)) {
				mine++;
			}
		}
		if (mine >= 5 || count < 1 || price < 1) {
			return false;
		}
		Offer o = new Offer();
		o.id = nextId++;
		o.seller = id;
		o.sellerName = nick;
		o.item = item;
		o.count = count;
		o.price = price;
		o.created = System.currentTimeMillis();
		OFFERS.add(o);
		if (!save()) {
			OFFERS.remove(o);
			return false;
		}
		return true;
	}

	/** Zakup oferty przez bota za pieniądze bota (saldo w Pieniadze pod kluczem bot:nick). */
	static boolean botBuy(MinecraftServer server, Offer o, String buyerNick) {
		String buyerId = "bot:" + buyerNick;
		if (!OFFERS.contains(o) || expired(o) || buyerId.equals(o.seller)) {
			return false;
		}
		long total = o.price;
		if (Pieniadze.getById(buyerId) < total) {
			return false;
		}
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
			double k = (double) total / (double) commissions;
			commissions = 0;
			for (Map.Entry<String, Long> e : payouts.entrySet()) {
				e.setValue((long) Math.floor(e.getValue() * k));
				commissions += e.getValue();
			}
		}
		payouts.merge(o.seller, total - commissions, Long::sum);
		payouts.merge(buyerId, -total, Long::sum);
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
		BotManager.addItem(buyerNick, o.item, o.count);
		return true;
	}

	/** Wygasłe oferty botów schodzą z rynku; zwracamy je, żeby przedmioty wróciły do sprzedawcy. */
	static List<Offer> removeExpiredBotOffers() {
		List<Offer> out = new ArrayList<>();
		for (Offer o : new ArrayList<>(OFFERS)) {
			if (o.seller != null && o.seller.startsWith("bot:") && expired(o)) {
				OFFERS.remove(o);
				out.add(o);
			}
		}
		if (!out.isEmpty() && !save()) {
			OFFERS.addAll(out);
			return new ArrayList<>();
		}
		return out;
	}

	static Item itemOf_(String path) {
		return itemOf(path);
	}

	static Item itemOf(String path) {
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
		o.created = System.currentTimeMillis();
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
				+ Pieniadze.formatShort(o.price) + ". Czeka na kupca 24 h (wycofasz: /wycofaj albo w /sklep -> jabłko).").withStyle(ChatFormatting.GREEN), false);
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
			giveItems(p, o);
		}
		src.sendSuccess(() -> Component.literal("[RYNEK] Wycofano " + mine.size() + " ofert. Przedmioty wróciły do ekwipunku.")
				.withStyle(ChatFormatting.YELLOW), false);
		return 1;
	}

	// ===== menu Rynek =====

	private enum Mode {
		ALL, EXPIRED, SEARCH
	}

	private static final class View {
		Mode mode = Mode.ALL;
		String query = "";
		int page = 0;
		int group = 0; // 0 = bez filtru, 1 = narzędzia, 2 = uzbrojenie, 3 = księgi, 4 = specjalne
		int filter = 0; // 0 = "Wszystko" w danej grupie
		final Offer[] shown = new Offer[40];
	}

	// Układ: kolumna 0 to filtry, kolumny 1-8 w rzędach 0-4 to oferty (40 na stronę), dolny rząd to przyciski.
	private static final int PER_PAGE = 40;
	private static final int[] OFFER_SLOTS = new int[PER_PAGE];
	private static final int[] SLOT_POS = new int[54];

	static {
		java.util.Arrays.fill(SLOT_POS, -1);
		int n = 0;
		for (int row = 0; row < 5; row++) {
			for (int col = 1; col < 9; col++) {
				OFFER_SLOTS[n] = row * 9 + col;
				SLOT_POS[row * 9 + col] = n;
				n++;
			}
		}
	}

	private static final int S_NARZEDZIA = 0;
	private static final int S_UZBROJENIE = 9;
	private static final int S_KSIEGI = 18;
	private static final int S_SPECJALNE = 27;
	private static final int S_RYNEK = 45;
	private static final int S_WYGASLE = 46;
	private static final int S_SZUKAJ = 47;
	private static final int S_ZAMKNIJ = 49;
	private static final int S_STRONA = 51;
	private static final Map<UUID, Boolean> SZUKAJ = new HashMap<>();
	private static final String[] NARZEDZIA = { "Wszystko", "Miecze", "Kilofy", "Łopaty", "Siekiery", "Motyki", "Łuki", "Trójzęby" };
	private static final String[] UZBROJENIE = { "Wszystko", "Hełmy", "Napierśniki", "Spodnie", "Buty", "Elytry", "Kostiumy" };

	private static boolean narzedzie(String path, int f) {
		return switch (f) {
			case 1 -> path.endsWith("_sword") || path.contains("miecz");
			case 2 -> path.endsWith("_pickaxe") || path.contains("kilof");
			case 3 -> path.endsWith("_shovel") || path.contains("lopata");
			case 4 -> path.endsWith("_axe") || path.contains("siekiera") || path.contains("topor");
			case 5 -> path.endsWith("_hoe") || path.contains("motyka");
			case 6 -> path.equals("bow") || path.equals("crossbow") || path.contains("luk") || path.contains("kusza");
			case 7 -> path.equals("trident") || path.contains("trojzab");
			default -> {
				boolean any = false;
				for (int i = 1; i < NARZEDZIA.length; i++) {
					any |= narzedzie(path, i);
				}
				yield any;
			}
		};
	}

	private static boolean uzbrojenie(String path, int f) {
		boolean zbroja = path.contains("zbroja");
		boolean modowy = path.endsWith("_helm") || path.endsWith("_klata") || path.endsWith("_spodnie") || path.endsWith("_buty");
		return switch (f) {
			case 1 -> path.endsWith("_helmet") || (zbroja && path.endsWith("_helm"));
			case 2 -> path.endsWith("_chestplate") || (zbroja && path.endsWith("_klata"));
			case 3 -> path.endsWith("_leggings") || (zbroja && path.endsWith("_spodnie"));
			case 4 -> path.endsWith("_boots") || (zbroja && path.endsWith("_buty"));
			case 5 -> path.contains("elytra");
			case 6 -> modowy && !zbroja;
			default -> {
				boolean any = false;
				for (int i = 1; i < UZBROJENIE.length; i++) {
					any |= uzbrojenie(path, i);
				}
				yield any;
			}
		};
	}

	private static final String[] KSIEGI_ID = { "regeneracji", "krytyczne", "ulaskawienia", "wampiryzmu", "wytrzymalosci",
			"szczescia", "szybkosci", "wolnosci", "spadania", "niepodpalenia", "mima", "zamrozenia", "kzm", "piorunow", "ciernie",
			"serc", "odbicia", "niewidzialnosci", "oddychania", "autowytapiania", "magnesu", "doswiadczenia" };
	private static final String[] KSIEGI = { "Wszystko", "Regeneracji", "Obrażenia krytycznego", "Ułaskawienia", "Wampiryzmu",
			"Wytrzymałości", "Szczęścia", "Szybkości", "Wolności", "Spadania", "Niepodpalenia", "Mima", "Zamrożenia",
			"Kradzieży życia maks.", "Piorunów", "Cierni", "Serc", "Odbicia", "Niewidzialności", "Oddychania", "Auto-wytapiania",
			"Magnesu", "Doświadczenia" };

	private static boolean ksiega(String path, int f) {
		if (f <= 0 || f > KSIEGI_ID.length) {
			return path.startsWith("ksiega_") || path.equals("enchanted_book");
		}
		return path.equals("ksiega_" + KSIEGI_ID[f - 1]);
	}

	private static final String[] SPECJALNE = { "Wszystko", "Serca", "Eventówki (stałe)", "Eventówki (zużywalne)", "Przydatne", "Klucze",
			"Pety", "Potki" };
	private static final java.util.Set<String> EVENT_ZUZYWALNE = java.util.Set.of("zatopiony_statek", "platek_sniegu",
			"czerwony_balon", "parasol", "serce", "blok_widmo", "krew_wampira", "cudowna_latarnia", "bomba_maksima",
			"wampirze_jablko", "jajko_zmutowanego_creepera", "cieple_mleko", "wata_cukrowa", "piernik", "zatruty_trunek",
			"splesniala_kanapka", "rozgotowana_kukurydza", "kulka_balwana", "perelka_zamianka", "wilk", "dzialko");
	private static final java.util.Set<String> EVENT_STALE = java.util.Set.of("chydro_klatka", "siekiera_grincha",
			"wodny_trojzab", "excalibur", "rozdzka_iluzjonisty", "wzmocniona_elytra", "wedka_nielota", "rozga", "smoczy_miecz",
			"rog_jednorozca", "kosa", "kostka_rubika", "roza_kupidyna", "lizak", "piekielny_miecz", "kroliczy_miecz", "turbo_trap",
			"lopata_grincha", "wedka_gwardiana", "zatruty_olowek", "arcus_magnus", "marchewkowy_miecz", "marchewkowa_kusza",
			"balonik_z_helem", "anarchiczny_kilof", "luk_kupidyna", "anarchia_korona", "slimowy_wiosna", "wedka_18",
			"fajerwerka_wyrzutni", "rozdzka_magika", "wedeczka_perelka", "boski_topor", "rog_koozy");
	private static final java.util.Set<String> PRZYDATNE = java.util.Set.of("zmianki", "sakiewka", "shulek_boxek",
			"totem_ulaskawienia", "dynamit");

	private static boolean specjalne(String path, int f) {
		return switch (f) {
			case 1 -> path.equals("serce_zycia");
			case 2 -> EVENT_STALE.contains(path);
			case 3 -> EVENT_ZUZYWALNE.contains(path);
			case 4 -> PRZYDATNE.contains(path);
			case 5 -> path.startsWith("klucz_");
			case 6 -> path.startsWith("pet_");
			case 7 -> path.equals("potion") || path.equals("splash_potion") || path.equals("lingering_potion");
			default -> {
				boolean any = false;
				for (int i = 1; i < SPECJALNE.length; i++) {
					any |= specjalne(path, i);
				}
				yield any;
			}
		};
	}

	private static boolean pasuje(String path, int group, int filter) {
		if (group == 4) {
			return specjalne(path, filter);
		}
		if (group == 3) {
			return ksiega(path, filter);
		}
		if (group == 1) {
			return narzedzie(path, filter);
		}
		if (group == 2) {
			return uzbrojenie(path, filter);
		}
		return true;
	}

	/** Otwiera Rynek (jabłko w /sklep). */
	public static void showOffers(ServerPlayer p) {
		open(p, Mode.ALL, "");
	}

	private static void open(ServerPlayer p, Mode mode, String query) {
		SimpleContainer box = new SimpleContainer(54);
		View v = new View();
		v.mode = mode;
		v.query = query;
		render(box, p, v);
		p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new SklepMenu(id, inv, box, (sp, slot, shift, prawy) -> click(sp, box, v, slot, shift, prawy)),
				Component.literal(mode == Mode.EXPIRED ? "Rynek: wygasłe" : "Rynek")));
	}

	/** Oferty widoczne w danym trybie (bez filtru kategorii), najnowsze pierwsze. */
	private static List<Offer> base(ServerPlayer p, View v) {
		List<Offer> out = new ArrayList<>();
		String me = p.getUUID().toString();
		for (Offer o : OFFERS) {
			boolean exp = expired(o);
			if (v.mode == Mode.EXPIRED) {
				if (exp && o.seller.equals(me)) {
					out.add(o);
				}
				continue;
			}
			if (exp) {
				continue;
			}
			if (v.mode == Mode.SEARCH && !(nameOf(o.item).toLowerCase(Locale.ROOT).contains(v.query)
					|| o.item.toLowerCase(Locale.ROOT).contains(v.query))) {
				continue;
			}
			out.add(o);
		}
		out.sort(Comparator.comparingLong((Offer o) -> o.created).reversed());
		return out;
	}

	private static List<Offer> visible(ServerPlayer p, View v) {
		List<Offer> out = new ArrayList<>();
		for (Offer o : base(p, v)) {
			if (v.mode == Mode.EXPIRED || pasuje(o.item, v.group, v.filter)) {
				out.add(o);
			}
		}
		return out;
	}

	private static int count(List<Offer> base, int group, int filter) {
		int n = 0;
		for (Offer o : base) {
			if (pasuje(o.item, group, filter)) {
				n++;
			}
		}
		return n;
	}

	private static Component lore(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(color).withStyle(st -> st.withItalic(false));
	}

	private static ItemStack button(Item item, String name, ChatFormatting color, String opis, int count) {
		ItemStack s = new ItemStack(item, Math.max(1, Math.min(64, count)));
		s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(color, ChatFormatting.BOLD)
				.withStyle(st -> st.withItalic(false)));
		if (opis != null) {
			s.set(DataComponents.LORE, new ItemLore(List.of(lore(opis, ChatFormatting.GRAY))));
		}
		return s;
	}

	private static ItemStack offerIcon(Offer o, ServerPlayer p, Mode mode) {
		Item item = itemOf(o.item);
		ItemStack s = new ItemStack(item == null ? Items.BARRIER : item);
		s.setCount(Math.max(1, Math.min(o.count, s.getMaxStackSize())));
		boolean mine = o.seller.equals(p.getUUID().toString());
		long unit = Math.round((double) o.price / (double) o.count);
		long leftMs = o.created + WAZNOSC_MS - System.currentTimeMillis();
		List<Component> l = new ArrayList<>();
		l.add(lore("Sprzedający: " + o.sellerName, ChatFormatting.GRAY));
		l.add(lore("Ilość: " + o.count, ChatFormatting.GRAY));
		l.add(lore("Cena: " + Pieniadze.formatShort(o.price) + " (" + Pieniadze.format(unit) + " za sztukę)", ChatFormatting.GOLD));
		if (mode == Mode.EXPIRED) {
			l.add(lore("Oferta wygasła.", ChatFormatting.RED));
			l.add(lore("Kliknij, aby odebrać przedmioty.", ChatFormatting.GREEN));
		} else {
			l.add(lore("Wygasa za: " + (Math.max(0, leftMs) / 3600000L) + " h " + ((Math.max(0, leftMs) / 60000L) % 60) + " min",
					ChatFormatting.GRAY));
			l.add(lore(mine ? "To twoja oferta. Kliknij, aby ją wycofać." : "Kliknij, aby kupić.", ChatFormatting.GREEN));
		}
		s.set(DataComponents.LORE, new ItemLore(l));
		return s;
	}

	/** Przycisk filtru w stylu: Filtruj według NAZWA, liczba produktów, lista kategorii z zaznaczoną bieżącą. */
	private static ItemStack filterButton(Item icon, String nazwa, ChatFormatting kolor, String[] kategorie, int gr, View v,
			List<Offer> base) {
		int cur = v.group == gr ? v.filter : 0;
		ItemStack s = new ItemStack(icon);
		s.set(DataComponents.CUSTOM_NAME, Component.literal("Filtruj według ").withStyle(ChatFormatting.WHITE)
				.append(Component.literal(nazwa).withStyle(kolor, ChatFormatting.BOLD)).withStyle(st -> st.withItalic(false)));
		List<Component> l = new ArrayList<>();
		l.add(lore("» Produktów w tej kategorii: " + count(base, gr, cur), ChatFormatting.WHITE));
		l.add(lore(" ", ChatFormatting.GRAY));
		l.add(lore("» Dostępne kategorie:", kolor));
		for (int i = 0; i < kategorie.length; i++) {
			boolean sel = v.group == gr && i == v.filter;
			MutableComponent line = Component.literal("  ▶ ").withStyle(ChatFormatting.GRAY).withStyle(st -> st.withItalic(false));
			line.append(Component.literal(kategorie[i]).withStyle(sel ? kolor : ChatFormatting.WHITE)
					.withStyle(st -> st.withItalic(false).withUnderlined(sel)));
			l.add(line);
		}
		l.add(lore(" ", ChatFormatting.GRAY));
		l.add(lore("Kliknij lewym, aby przełączyć filtr!", ChatFormatting.GREEN));
		l.add(lore("Kliknij prawym, aby wyłączyć filtr!", ChatFormatting.RED));
		s.set(DataComponents.LORE, new ItemLore(l));
		return s;
	}

	private static void render(SimpleContainer box, ServerPlayer p, View v) {
		box.clearContent();
		List<Offer> base = base(p, v);
		List<Offer> list = visible(p, v);
		int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
		v.page = Math.max(0, Math.min(v.page, pages - 1));
		for (int i = 0; i < PER_PAGE; i++) {
			int idx = v.page * PER_PAGE + i;
			v.shown[i] = idx < list.size() ? list.get(idx) : null;
			if (v.shown[i] != null) {
				box.setItem(OFFER_SLOTS[i], offerIcon(v.shown[i], p, v.mode));
			}
		}
		box.setItem(S_NARZEDZIA, filterButton(Items.IRON_AXE, "NARZĘDZI", ChatFormatting.AQUA, NARZEDZIA, 1, v, base));
		box.setItem(S_UZBROJENIE, filterButton(Items.DIAMOND_HELMET, "UZBROJENIA", ChatFormatting.GOLD, UZBROJENIE, 2, v, base));
		box.setItem(S_KSIEGI, filterButton(Items.ENCHANTED_BOOK, "KSIĄG", ChatFormatting.LIGHT_PURPLE, KSIEGI, 3, v, base));
		box.setItem(S_SPECJALNE, filterButton(Items.NETHER_STAR, "SPECJALNE", ChatFormatting.RED, SPECJALNE, 4, v, base));
		box.setItem(S_RYNEK, button(Items.ENDER_CHEST, "Przedmioty na rynku", ChatFormatting.LIGHT_PURPLE,
				"Wszystkie oferty, bez filtrów. W tym widoku: " + list.size() + ".", 1));
		box.setItem(S_WYGASLE, button(Items.CHEST, "Wygasłe przedmioty", ChatFormatting.GOLD,
				"Twoje oferty, które wygasły po 24 h. Kliknij, aby je odebrać.", 1));
		box.setItem(S_SZUKAJ, button(Items.OAK_SIGN, "Wyszukaj produkt", ChatFormatting.YELLOW,
				v.mode == Mode.SEARCH ? "Szukasz: " + v.query : "Kliknij, a potem napisz nazwę na czacie.", 1));
		box.setItem(S_ZAMKNIJ, button(Items.BARRIER, "Zamknij", ChatFormatting.RED, null, 1));
		box.setItem(S_STRONA, button(Items.LIME_DYE, "Następna strona", ChatFormatting.GREEN,
				"Strona " + (v.page + 1) + " z " + pages + ". Prawy klik: poprzednia.", v.page + 1));
	}

	private static void nextFilter(View v, int gr, int size, boolean shift) {
		if (v.group != gr) {
			v.group = gr;
			v.filter = 0;
		} else {
			v.filter = (v.filter + (shift ? size - 1 : 1)) % size;
		}
		v.page = 0;
	}

	private static void click(ServerPlayer sp, SimpleContainer box, View v, int slot, boolean shift, boolean prawy) {
		if (slot == S_NARZEDZIA || slot == S_UZBROJENIE || slot == S_KSIEGI || slot == S_SPECJALNE) {
			if (v.mode == Mode.EXPIRED) {
				v.mode = Mode.ALL;
			}
			if (prawy) {
				// Prawy klik wyłącza filtr.
				v.group = 0;
				v.filter = 0;
				v.page = 0;
			} else if (slot == S_NARZEDZIA) {
				nextFilter(v, 1, NARZEDZIA.length, false);
			} else if (slot == S_UZBROJENIE) {
				nextFilter(v, 2, UZBROJENIE.length, false);
			} else if (slot == S_KSIEGI) {
				nextFilter(v, 3, KSIEGI.length, false);
			} else {
				nextFilter(v, 4, SPECJALNE.length, false);
			}
		} else if (slot == S_RYNEK) {
			v.mode = Mode.ALL;
			v.query = "";
			v.group = 0;
			v.filter = 0;
			v.page = 0;
		} else if (slot == S_WYGASLE) {
			v.mode = Mode.EXPIRED;
			v.page = 0;
		} else if (slot == S_SZUKAJ) {
			SZUKAJ.put(sp.getUUID(), Boolean.TRUE);
			sp.closeContainer();
			sp.sendSystemMessage(Component.literal("[RYNEK] Napisz na czacie nazwę produktu (np. diament). Napisz 'anuluj', żeby przerwać.")
					.withStyle(ChatFormatting.YELLOW));
			return;
		} else if (slot == S_ZAMKNIJ) {
			sp.closeContainer();
			return;
		} else if (slot == S_STRONA) {
			v.page += (shift || prawy) ? -1 : 1;
			int pages = Math.max(1, (visible(sp, v).size() + PER_PAGE - 1) / PER_PAGE);
			if (v.page >= pages) {
				v.page = 0;
			}
			if (v.page < 0) {
				v.page = pages - 1;
			}
		} else if (slot >= 0 && slot < 54 && SLOT_POS[slot] >= 0) {
			Offer o = v.shown[SLOT_POS[slot]];
			if (o == null) {
				return;
			}
			if (!OFFERS.contains(o)) {
				sp.sendSystemMessage(Component.literal("[RYNEK] Ta oferta jest już nieaktualna."), true);
			} else if (v.mode == Mode.EXPIRED || o.seller.equals(sp.getUUID().toString())) {
				returnOffer(sp, o);
			} else if (expired(o)) {
				sp.sendSystemMessage(Component.literal("[RYNEK] Ta oferta wygasła."), true);
			} else if (Pieniadze.get(sp) < o.price) {
				sp.sendSystemMessage(Component.literal("[RYNEK] Masz za mało pieniędzy (potrzeba " + Pieniadze.formatShort(o.price)
						+ ", masz " + Pieniadze.formatShort(Pieniadze.get(sp)) + ")."), true);
			} else if (!settle(sp.level().getServer(), o, sp.getName().getString(), sp, null)) {
				sp.sendSystemMessage(Component.literal("[RYNEK] Zakup się nie powiódł, nic nie pobrano."), true);
			}
		} else {
			return;
		}
		render(box, sp, v);
	}

	/** Zwrot przedmiotów z własnej oferty (wycofanie albo odbiór wygasłej). */
	private static void returnOffer(ServerPlayer sp, Offer o) {
		OFFERS.remove(o);
		if (!save()) {
			OFFERS.add(o);
			sp.sendSystemMessage(Component.literal("[RYNEK] Nie udało się zapisać rynku, spróbuj ponownie."), true);
			return;
		}
		giveItems(sp, o);
		sp.sendSystemMessage(Component.literal("[RYNEK] Odebrano " + o.count + "x " + nameOf(o.item) + "."), true);
	}

	/** Wywoływane z czatu: jeśli gracz kliknął tabliczkę, jego następna wiadomość to fraza wyszukiwania. */
	public static boolean captureChat(ServerPlayer sender, String text) {
		if (SZUKAJ.remove(sender.getUUID()) == null) {
			return false;
		}
		String q = text.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty() || q.equals("anuluj")) {
			sender.sendSystemMessage(Component.literal("[RYNEK] Wyszukiwanie anulowane."));
			return true;
		}
		sender.level().getServer().execute(() -> open(sender, Mode.SEARCH, q));
		return true;
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

	/**
	 * Wspólne rozliczenie zakupu (bot albo gracz). Najpierw zapis usunięcia oferty, potem jedna wypłata
	 * (sprzedający, prowizje, a przy graczu także pobranie ceny). Każdy błąd cofa całość.
	 */
	private static boolean settle(MinecraftServer server, Offer o, String buyerName, ServerPlayer buyer, Bot bot) {
		if (!OFFERS.contains(o) || expired(o)) {
			return false;
		}
		long total = o.price;
		if (buyer != null && Pieniadze.get(buyer) < total) {
			return false;
		}
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
		if (buyer != null) {
			payouts.merge(buyer.getUUID().toString(), -total, Long::sum);
		}
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
		if (bot != null) {
			bot.budget -= total;
		}
		double d = POPYT.getOrDefault(o.item, 1.0);
		POPYT.put(o.item, Math.max(0.6, d * 0.92));
		if (buyer != null) {
			giveItems(buyer, o);
		}

		String nazwa = nameOf(o.item);
		server.getPlayerList().broadcastSystemMessage(Component.literal("[RYNEK] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(buyerName + " kupił " + o.count + "x " + nazwa + " za " + Pieniadze.formatShort(total)
						+ " od gracza " + o.sellerName + "!").withStyle(ChatFormatting.WHITE)), false);
		try {
			ServerPlayer seller = server.getPlayerList().getPlayer(UUID.fromString(o.seller));
			if (seller != null) {
				seller.sendSystemMessage(Component.literal("[RYNEK] Gracz " + buyerName + " kupił Twój przedmiot z rynku!")
						.withStyle(ChatFormatting.GREEN));
				seller.sendSystemMessage(Component.literal("Wpłynęło " + Pieniadze.formatShort(sellerGets) + " (po prowizji).")
						.withStyle(ChatFormatting.GRAY));
			}
		} catch (IllegalArgumentException ignored) {
			// zły UUID w pliku: pomijamy powiadomienie
		}
		return true;
	}

	private static void giveItems(ServerPlayer p, Offer o) {
		Item item = itemOf(o.item);
		if (item == null) {
			return;
		}
		int left = o.count;
		int max = new ItemStack(item).getMaxStackSize();
		while (left > 0) {
			int n = Math.min(left, max);
			give(p, new ItemStack(item, n));
			left -= n;
		}
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
				if (item == null || expired(o)) {
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
				settle(server, best, b.name, null, b);
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
