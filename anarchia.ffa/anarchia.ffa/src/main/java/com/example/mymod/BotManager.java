package com.example.mymod;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Boty-gracze (etap 1): 40 manekinów z nickiem, losowym skinem i poziomem 1-10,
 * chodzą po obszarze 1500 x 1500, nie wychodzą poza niego, po śmierci wracają na spawn.
 * Granica jest widoczna dla graczy jako smuga cząsteczek (gracz może ją przekraczać).
 */
public final class BotManager {
	private BotManager() {}

	public static final String TAG = "anarchia_bot";
	public static final int COUNT = 300;
	public static final int HALF = 750;               // obszar 1500 x 1500 wokół 0,0
	public static final double SPAWN_X = 0.5, SPAWN_Y = 100, SPAWN_Z = 0.5;
	private static final String[] SKINS = {"steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"};
	private static final String[] PREFIX = {"Desik", "Kuba", "Mati", "Szymon", "Wojtek", "Ola", "Kacper", "Bartek", "Jasiek", "Marek",
			"Pawel", "Antek", "Filip", "Dawid", "Michal", "Zuza", "Hubert", "Olek", "Piotrek", "Adrian"};

	private static final class Rec {
		String nick;
		int level;
		String skin;
		boolean funded;
		boolean kit;
		boolean event;
		boolean gear;
		boolean mats, mats2;
		boolean wallDone;
		int wallP, wallH, wallY;
		boolean cwDone; // mur sojuszu (zapisany u właściciela bazy klanu)
		int cwP, cwH, cwY;
		boolean hasWs; // warsztat: piec w (wsx, wsy, wsz), stół obok
		int wsx, wsy, wsz;
		int kills, deaths;
		String clan;
		String role;
		String buddy;
		boolean hasHome;
		int slot;
		double hx, hz;
		String partner; // UUID gracza, z którym bot zawarł sojusz (gildię)
		String leaderP; // UUID gracza, który założył gildię i jest jej przywódcą (null = przywódcą jest bot)
		int shaftX, shaftZ, shaftTop, shaftDepth; // kopalnia hutnika: szyb 3x3 przy murze bazy (shaftTop > 0 = rozpoczęty)
		boolean shaftDone;
		String baseClan; // gildia, która przejęła bazę tego bota (po wygranej wojnie); null = baza należy do jego gildii
	}

	private static final class Mine {
		double sx, sy, sz, x, y, z;
		int phase, targetY, dir, steps, turns, tick, maxSteps;
	}

	private static final class Slot {
		String i;
		int c;
	}

	private static final class State {
		Mannequin entity;
		double tx, tz;
		int wait;
		int respawnIn;
		double lx, ly, lz;
		boolean first = true;
		int act;
		String foe;
		int jump, sprintT, ritual, fleeT, rtpT;
		boolean rtpBack;
		boolean strafe;
		int stunT;
		java.util.UUID proposeTo;
		int propT, propToggles, propTick;
		long actT;
		double[] tourPts; // oprowadzanie nowego członka sojuszu: punkty x,z
		int tourIdx, tourDwell, tourT;
		String escort; // sojusznik, który oprowadza nowego członka
		long awayUntil;
		String ritualWith;
		boolean lead;
		boolean retal;
		Mine mine;
		java.util.UUID foeP;
		int fight;
		int cd;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Random RNG = new Random();
	private static final List<Rec> ROSTER = new ArrayList<>();
	private static final Map<String, State> STATES = new HashMap<>();
	private static boolean cleaned;
	private static MinecraftServer SRV;
	/** Co widz widział: gracz -> (scoreboardName bota -> "klan|kolor"). */
	private static final Map<java.util.UUID, Map<String, String>> SEEN = new HashMap<>();
	private static final Map<java.util.UUID, int[]> CROUCH = new HashMap<>(); // {liczba, tick pierwszego, był w dole, cooldown do}
	/** Miejsce pojawienia się nowo stworzonego bota (tam, gdzie stał admin). */
	private static final Map<String, double[]> PENDING = new HashMap<>();
	private static final Map<String, SimpleContainer> INV = new HashMap<>();
	/** Saldo startowe bota na poziom (w groszach): poziom * 50 zł. Zmień tu, jeśli ma być inaczej. */
	private static final long FUNDS_PER_LEVEL = 5000;
	// staw: 11 100 13 / -11 100 35
	private static final int POND_X1 = -11, POND_X2 = 11, POND_Z1 = 13, POND_Z2 = 35;

	private static Path file() {
		return WorldData.file("anarchia_ffa_boty.json");
	}

	private static Path invFile() {
		return WorldData.file("anarchia_ffa_boty_ekw.json");
	}

	// ---------- API ekwipunku ----------
	/** Nick bota bez względu na wielkość liter albo null. */
	public static String findNick(String raw) {
		if (raw == null) return null;
		for (Rec r : ROSTER) if (r.nick.equalsIgnoreCase(raw)) return r.nick;
		return null;
	}

	public static List<String> nicks() {
		List<String> l = new ArrayList<>();
		for (Rec r : ROSTER) l.add(r.nick);
		return l;
	}

	/** Tylko boty, które teraz żyją w świecie (handlują wyłącznie takie, jak widzisz na mapie). */
	public static List<String> aliveNicks() {
		List<String> l = new ArrayList<>();
		for (Map.Entry<String, State> e : STATES.entrySet()) {
			Mannequin m = e.getValue().entity;
			if (m != null && !m.isRemoved() && m.isAlive()) l.add(e.getKey());
		}
		return l;
	}

	public static SimpleContainer inv(String nick) {
		return INV.computeIfAbsent(nick, k -> new SimpleContainer(54));
	}

	/** Ekwipunek bota albo null, gdy takiego bota nie ma. */
	public static SimpleContainer invOf(String nick) {
		for (Rec r : ROSTER) if (r.nick.equalsIgnoreCase(nick)) return inv(r.nick);
		return null;
	}

	/** Dodaje przedmiot do ekwipunku bota; zwraca liczbę sztuk, które się nie zmieściły. */
	public static int addItem(String nick, String itemPath, int count) {
		Item it = Rynek.itemOf(itemPath);
		if (it == null || count <= 0) return count;
		ItemStack st = new ItemStack(it, count);
		return addStack(inv(nick), st);
	}

	private static int addStack(SimpleContainer c, ItemStack st) {
		ItemStack left = c.addItem(st);
		return left.getCount();
	}

	/** Tworzy nowego bota (z /stwórz). Zwraca komunikat błędu albo null, gdy się udało. */
	public static String createBot(String nick, String skin, int level, List<ItemStack> items, double px, double py, double pz) {
		for (Rec r : ROSTER) if (r.nick.equalsIgnoreCase(nick)) return "Bot o takim nicku już istnieje.";
		Rec r = new Rec();
		r.nick = nick;
		r.skin = skin;
		r.level = Math.max(1, Math.min(10, level));
		r.kit = true; // admin sam dał przedmioty
		ROSTER.add(r);
		PENDING.put(nick, new double[] {px, py, pz});
		SimpleContainer c = inv(nick);
		for (ItemStack s : items) addStack(c, s);
		save();
		saveInv();
		return null;
	}

	private static void loadInv() {
		INV.clear();
		try {
			if (Files.exists(invFile())) {
				Type t = new TypeToken<Map<String, List<Slot>>>() {}.getType();
				Map<String, List<Slot>> m = GSON.fromJson(Files.readString(invFile(), StandardCharsets.UTF_8), t);
				if (m != null) {
					for (Map.Entry<String, List<Slot>> e : m.entrySet()) {
						SimpleContainer c = new SimpleContainer(54);
						for (Slot sl : e.getValue()) {
							Item it = Rynek.itemOf(sl.i);
							if (it != null && sl.c > 0) c.addItem(new ItemStack(it, sl.c));
						}
						INV.put(e.getKey(), c);
					}
				}
			}
		} catch (Exception e) {
			MyMod.LOGGER.warn("Nie udało się wczytać ekwipunków botów: {}", e.toString());
		}
	}

	private static void saveInv() {
		try {
			Map<String, List<Slot>> m = new HashMap<>();
			for (Map.Entry<String, SimpleContainer> e : INV.entrySet()) {
				List<Slot> l = new ArrayList<>();
				for (int i = 0; i < e.getValue().getContainerSize(); i++) {
					ItemStack s = e.getValue().getItem(i);
					if (s.isEmpty()) continue;
					Slot sl = new Slot();
					sl.i = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
					sl.c = s.getCount();
					l.add(sl);
				}
				m.put(e.getKey(), l);
			}
			Files.writeString(invFile(), GSON.toJson(m), StandardCharsets.UTF_8);
		} catch (Exception e) {
			MyMod.LOGGER.warn("Nie udało się zapisać ekwipunków botów: {}", e.toString());
		}
	}

	public static void initialize() {
		BotTab.initialize();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			cleaned = false;
			STATES.clear();
			loadOrCreate();
			loadInv();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> saveInv());
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((h, snd, srv) -> SEEN.remove(h.getPlayer().getUUID()));
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, srv) -> SEEN.remove(h.getPlayer().getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(BotManager::tick);
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!(entity instanceof Mannequin m) || !isBot(m) || !(source.getEntity() instanceof ServerPlayer p)) return;
			for (Map.Entry<String, State> e0 : STATES.entrySet()) {
				Rec r0 = recOf(e0.getKey());
				if (e0.getValue().entity == m && r0 != null && p.getUUID().toString().equals(r0.partner)) return; // własny sojusznik nie oddaje
			}
			for (State s : STATES.values()) { // uderzony bot od razu oddaje
				if (s.entity == m) {
					s.foeP = p.getUUID();
					s.foe = null;
					s.fight = 1200;
					s.retal = true;
					s.proposeTo = null;
					s.cd = 0;
					s.mine = null;
				}
			}
			String cl = null;
			for (Map.Entry<String, State> e : STATES.entrySet()) if (e.getValue().entity == m) cl = clanOf(e.getKey());
			if (cl == null) return;
			for (Map.Entry<String, State> e : STATES.entrySet()) { // sojusznicy z okolicy pomagają
				State a = e.getValue();
				if (a.entity == null || a.entity == m || a.entity.isRemoved() || !cl.equals(clanOf(e.getKey()))) continue;
				if (a.entity.distanceToSqr(m) < 50 * 50) {
					a.foeP = p.getUUID();
					a.foe = null;
					a.fight = 1200;
					a.retal = true;
					a.mine = null;
				}
			}
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!(entity instanceof ServerPlayer owner)) return;
			String id = owner.getUUID().toString();
			Entity att = source.getEntity();
			for (Map.Entry<String, State> e : STATES.entrySet()) {
				Rec r0 = recOf(e.getKey());
				State st = e.getValue();
				if (r0 == null || !id.equals(r0.partner) || st.entity == null || st.entity.isRemoved()) continue;
				if (att instanceof ServerPlayer ap && ap != owner) {
					st.foeP = ap.getUUID();
					st.foe = null;
					st.fight = 1200;
					st.retal = true;
				} else if (att instanceof Mannequin am && isBot(am) && am != st.entity) {
					for (Map.Entry<String, State> e2 : STATES.entrySet()) {
						if (e2.getValue().entity == am) {
							st.foe = e2.getKey();
							st.foeP = null;
							st.fight = 400;
						}
					}
				}
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof ItemEntity ie)) return;
			for (ServerPlayer p : world.players()) {
				if (!p.isSpectator() && p.distanceToSqr(ie) < 16.0) {
					PLAYER_ITEMS.add(ie.getUUID());
					return;
				}
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity instanceof ItemEntity ie) {
				PLAYER_ITEMS.remove(ie.getUUID());
				OWN_DROPS.remove(ie.getUUID());
			}
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof Mannequin km && isBot(km)) {
				for (Map.Entry<String, State> e : STATES.entrySet()) {
					if (e.getValue().entity == km) {
						Rec kr = recOf(e.getKey());
						if (kr != null) kr.kills++;
					}
				}
			}
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof Mannequin vm) || !isBot(vm) || !(entity.level() instanceof ServerLevel lv)) return;
			String vn = null, kn = null;
			for (Map.Entry<String, State> e : STATES.entrySet()) {
				if (e.getValue().entity == vm) vn = e.getKey();
				if (source.getEntity() != null && e.getValue().entity == source.getEntity()) kn = e.getKey();
			}
			if (vn == null) return;
			String killerName = source.getEntity() instanceof ServerPlayer kp ? kp.getGameProfile().name() : kn;
			lv.getServer().getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal(killerName != null
					? "☠ Gracz " + vn + " umarł od gracza " + killerName : "☠ Gracz " + vn + " umarł")
					.withStyle(net.minecraft.ChatFormatting.RED), false);
			warDeath(vn, clanOf(vn));
			int vs = STREAK.getOrDefault(vn, 0);
			STREAK.remove(vn);
			if (source.getEntity() instanceof ServerPlayer gp) {
				GRUDGE.put(vn, gp.getUUID());
				BotChat.say(lv.getServer(), vn, "zapamiętam cię, " + gp.getGameProfile().name() + " 😡");
			}
			if (kn != null) {
				if (vs >= 5) { // nagroda za głowę zabójcy serii
					Pieniadze.addAll(Map.of("bot:" + kn, 200L * vs));
					lv.getServer().getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("💰 " + kn + " zdobył nagrodę za głowę " + vn
							+ " (seria " + vs + " zabójstw): $" + 200 * vs).withStyle(net.minecraft.ChatFormatting.GOLD), false);
				}
				int ks = STREAK.merge(kn, 1, Integer::sum);
				if (ks % 5 == 0) {
					lv.getServer().getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("🔥 " + kn + " ma serię " + ks
							+ " zabójstw! Za jego głowę jest nagroda: $" + 200 * ks).withStyle(net.minecraft.ChatFormatting.GOLD), false);
				}
				// zemsta: sojusznicy poległego z okolicy ruszają na zabójcę
				String vc = clanOf(vn);
				State kst = STATES.get(kn);
				if (vc != null && kst != null && kst.entity != null && !vc.equals(clanOf(kn))) {
					for (Map.Entry<String, State> e : STATES.entrySet()) {
						State a = e.getValue();
						if (a.entity == null || a.entity == vm || !a.entity.isAlive() || a.foe != null || a.foeP != null || !vc.equals(clanOf(e.getKey()))) continue;
						if (a.entity.distanceToSqr(vm) < 60 * 60) {
							a.foe = kn;
							a.fight = 600;
							BotChat.say(lv.getServer(), e.getKey(), "Zemsta za " + vn + "!");
						}
					}
				}
			}
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mannequin m && isBot(m) && source.getEntity() instanceof ServerPlayer
					&& entity.level() instanceof ServerLevel lv && RNG.nextDouble() < 0.03) {
				dropBook(lv, m);
			}
		});
	}

	/** Pierwsze wrażenie bota o graczu: 0 = ignoruje (40%), 1 = atakuje (50%), 2 = proponuje sojusz (10%); ważne ok. 5 minut. */
	private static final Map<String, long[]> SIGHT = new java.util.HashMap<>();

	/** Gracz, który zabił bota, jest dla niego wrogiem numer 1 (po odrodzeniu bot go atakuje i pamięta). */
	private static final Map<String, java.util.UUID> GRUDGE = new java.util.HashMap<>();

	private static int sightDecision(String nick, ServerPlayer p, long now) {
		if (p.getUUID().equals(GRUDGE.get(nick))) return 1;
		Rec gr = recOf(nick);
		if (gr != null && "G".equals(gr.role) && gr.clan != null && gr.partner == null) return 1; // strażnik bramy atakuje każdego poza sojusznikami
		String key = nick + "|" + p.getUUID();
		long[] v = SIGHT.get(key);
		if (v == null || v[1] < now) {
			double x = RNG.nextDouble();
			v = new long[] {x < 0.10 ? 0 : x < 0.55 ? 1 : 2, now + 6000}; // 10% pokój, 45% walka, 45% gildia
			SIGHT.put(key, v);
		}
		return (int) v[0];
	}

	/** Przedmioty, które wypadły przy graczu (wyrzucone, z jego śmierci, ze zniszczonych przez niego bloków): boty ich nie podnoszą. */
	private static final java.util.Set<java.util.UUID> PLAYER_ITEMS = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** Wiersz listy TAB: bot, jego poziom i statystyki; online=false oznacza, że bota nie ma teraz na świecie (czerwony nick). */
	public record TabRow(String nick, int level, int kills, int deaths, boolean online, String clan, long money) {}

	public static java.util.List<TabRow> tabRows() {
		java.util.List<TabRow> l = new java.util.ArrayList<>();
		for (Rec r : ROSTER) {
			State s = STATES.get(r.nick);
			boolean on = s != null && s.entity != null && !s.entity.isRemoved() && s.entity.isAlive();
			l.add(new TabRow(r.nick, r.level, r.kills, r.deaths, on, r.clan, Pieniadze.getById("bot:" + r.nick)));
		}
		return l;
	}

	// ---------- roster ----------
	private static void loadOrCreate() {
		ROSTER.clear();
		try {
			if (Files.exists(file())) {
				Type t = new TypeToken<List<Rec>>() {}.getType();
				List<Rec> l = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), t);
				if (l != null) ROSTER.addAll(l);
			}
		} catch (Exception e) {
			MyMod.LOGGER.warn("Nie udało się wczytać botów: {}", e.toString());
		}
		// uzupełniamy do COUNT botów (istniejące zostają bez zmian); poziomy: ok. 10% poz.1, 15% 2-3, 50% 4-5, 2,5% 6-7, 20% 8-9, 2,5% 10
		Set<String> used = new HashSet<>();
		for (Rec r : ROSTER) used.add(r.nick);
		boolean added = false;
		while (ROSTER.size() < COUNT) {
			Rec r = new Rec();
			String n;
			do {
				String p = PREFIX[RNG.nextInt(PREFIX.length)];
				int form = RNG.nextInt(3);
				n = form == 0 ? p + (10 + RNG.nextInt(90)) : form == 1 ? p + (100 + RNG.nextInt(900)) : p + "_PL";
			} while (!used.add(n));
			r.nick = n;
			int x = RNG.nextInt(40);
			r.level = x < 4 ? 1 : x < 10 ? 2 + RNG.nextInt(2) : x < 30 ? 4 + RNG.nextInt(2) : x < 31 ? 6 + RNG.nextInt(2) : x < 39 ? 8 + RNG.nextInt(2) : 10;
			r.skin = SKINS[RNG.nextInt(SKINS.length)];
			ROSTER.add(r);
			added = true;
		}
		for (Rec r : ROSTER) { // dawne sojusze/klany znikają; gildie D1, D2... tworzą się w grze
			if (r.clan != null && !r.clan.matches("D\\d+")) {
				r.clan = null;
				r.partner = null;
				r.role = null;
				r.buddy = null;
				r.baseClan = null;
				r.cwDone = false;
				r.cwP = 0;
				added = true;
			}
		}
		if (added) save();
	}

	/** Sojusze: boty bez klanu trafiają do grup po 4-6; członkowie klanu się nie biją i pomagają sobie. */
	private static boolean assignClans() {
		List<Rec> free = new ArrayList<>();
		int maxN = 0;
		for (Rec r : ROSTER) {
			if (r.clan == null) free.add(r);
			else try { maxN = Math.max(maxN, Integer.parseInt(r.clan.substring(1))); } catch (Exception ignored) { }
		}
		if (free.isEmpty()) return false;
		Collections.shuffle(free, RNG);
		int i = 0;
		while (i < free.size()) {
			int size = Math.min(4 + RNG.nextInt(3), free.size() - i);
			if (free.size() - i - size < 4 && free.size() - i - size > 0) size = free.size() - i;
			maxN++;
			for (int k = 0; k < size; k++) free.get(i + k).clan = "K" + maxN;
			i += size;
		}
		return true;
	}

	private static String clanOf(String nick) {
		for (Rec r : ROSTER) if (r.nick.equals(nick)) return r.clan;
		return null;
	}

	private static void save() {
		try {
			Files.writeString(file(), GSON.toJson(ROSTER), StandardCharsets.UTF_8);
		} catch (IOException e) {
			MyMod.LOGGER.warn("Nie udało się zapisać botów: {}", e.toString());
		}
	}

	// ---------- tick ----------
	private static void tick(MinecraftServer server) {
		SRV = server;
		crouchTick(server);
		if (server.getTickCount() % 20 == 7) allianceTags(server);
		guildWars(server);
		if (server.getTickCount() % 200 == 11) leaderTags(server);
		if (server.getTickCount() % 200 == 3) ensureBanners(server);
		if (server.getTickCount() % 200 == 21) assignRoles();
		ServerLevel level = server.overworld();
		if (level == null) return;
		if (!cleaned) {
			cleaned = true;
			// po restarcie usuwamy manekiny botów zapisane w świecie
			try {
				AABB all = new AABB(-HALF - 64, -64, -HALF - 64, HALF + 64, 400, HALF + 64);
				for (Mannequin m : level.getEntitiesOfClass(Mannequin.class, all, BotManager::isBot)) {
					m.discard();
				}
			} catch (Exception e) {
				MyMod.LOGGER.warn("Czyszczenie botów: {}", e.toString());
			}
			STATES.clear();
			provision();
		}
		long t = server.getTickCount();
		long t0 = System.nanoTime();
		int total = ROSTER.size(), spawnBudget = 1; // najwyżej jedno pojawienie się bota na tick (ładowanie terenu jest ciężkie)
		if (cursor >= total) cursor = 0;
		int startIdx = cursor, done = 0;
		for (int bi = 0; bi < total; bi++) {
			int idx = (startIdx + bi) % total;
			if (bi > 0 && System.nanoTime() - t0 > BUDGET_NS) { // limit czasu: serwer ma zawsze 20 TPS, reszta botów w następnym ticku
				cursor = idx;
				skipped++;
				break;
			}
			done++;
			Rec r = ROSTER.get(idx);
			final int order = idx;
			State s = STATES.computeIfAbsent(r.nick, k -> {
				State n = new State();
				n.respawnIn = 20 + order * 3 + RNG.nextInt(3); // boty pojawiają się stopniowo, po jednym na ok. 3 ticki
				return n;
			});
			boolean alive = s.entity != null && !s.entity.isRemoved() && s.entity.isAlive();
			if (!alive) {
				if (s.entity != null && !s.entity.isRemoved()) {
					dropAll(level, r, s); // śmierć: ekwipunek wypada na ziemię
				}
				s.entity = null;
				s.foe = null;
				s.foeP = null;
				s.mine = null;
				if (--s.respawnIn <= 0 && spawnBudget > 0) {
					spawnBudget--;
					spawn(server, level, r, s);
					s.respawnIn = 100; // ponowna próba po 5 s, jeśli się nie udało
				}
				continue;
			}
			s.lx = s.entity.getX();
			s.ly = s.entity.getY();
			s.lz = s.entity.getZ();
			if (inCobweb(level, s.entity)) s.stunT = Math.max(s.stunT, 2); // w pajęczynie nie chodzi, dopóki jej nie rozwali
			if (s.stunT > 0) s.stunT--; else moveBot(level, r, s); // unieruchomiony (np. zamrożony eventówką) nie chodzi i nie bije
			if ((t + Math.floorMod(r.nick.hashCode(), 10)) % 10 == 0 || t - s.actT >= 20) { // co 10 ticków, ale każdy bot w innym ticku (bez skoków lagów)
				s.actT = t;
				activity(level, r, s);
				maybeStartFight(level, r, s);
				maybeStartMine(level, r, s);
				maybeAlly(level, r, s);
			}
		}
		profNs += System.nanoTime() - t0;
		if (++profTicks >= 600) { // co 30 s: ile średnio kosztują boty
			MyMod.LOGGER.info("Boty: średnio {} ms/tick, przerwano limitem czasu {} razy w 30 s, ostatnio obsłużono {}/{}", String.format("%.1f", profNs / 1e6 / profTicks), skipped, done, total);
			profNs = 0;
			profTicks = 0;
			skipped = 0;
		}
		if (t % 10 == 0) drawBorder(level);
		if (t % 100 == 50) {
			try { BotRynek.cycle(server); } catch (Exception e) { MyMod.LOGGER.warn("BotRynek: {}", e.toString()); }
		}
		if (t % 1200 == 600) saveInv();
	}

	private static void spawn(MinecraftServer server, ServerLevel level, Rec r, State s) {
		double x = SPAWN_X + RNG.nextInt(7) - 3, z = SPAWN_Z + RNG.nextInt(7) - 3;
		s.first = false;
		double[] spot = pickSpot(level, r.nick);
		if (spot != null) { // każdy bot pojawia się osobno, w innym miejscu
			x = spot[0];
			z = spot[1];
		}
		double[] pend = PENDING.remove(r.nick);
		if (pend != null) {
			x = pend[0];
			z = pend[2];
		} else if (r.hasHome) { // po śmierci wraca tam, gdzie pojawił się po raz pierwszy
			x = r.hx;
			z = r.hz;
			double[] fr = freeNear(level, r.nick, x, z); // nie pojawia się na innym bocie
			if (fr != null) {
				x = fr[0];
				z = fr[1];
			}
		} else if (isWaterAt(level, x, z)) { // nie pojawiamy się na wodzie (boty nie umieją pływać)
			x = SPAWN_X + RNG.nextInt(7) - 3;
			z = SPAWN_Z + RNG.nextInt(7) - 3;
		}
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
		final double sy = pend != null ? pend[1] : (ground > 5 ? ground : SPAWN_Y); // zawsze na ziemi, nie w powietrzu
		String base = "summon minecraft:mannequin " + x + " " + sy + " " + z
				+ " {CustomName:\"" + r.nick + "\",CustomNameVisible:1b,Tags:[\"" + TAG + "\"],hide_description:1b,";
		boolean stock = false;
		for (String k : SKINS) if (k.equals(r.skin)) stock = true;
		String withSkin = stock ? base + "profile:{texture:\"minecraft:entity/player/wide/" + r.skin + "\"}}"
				: base + "profile:\"" + r.skin + "\"}";
		String noSkin = base.substring(0, base.length() - 1) + "}";
		CommandSourceStack src = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
		try {
			server.getCommands().getDispatcher().execute(withSkin, src);
		} catch (Exception e1) {
			try {
				server.getCommands().getDispatcher().execute(noSkin, src);
			} catch (Exception e2) {
				MyMod.LOGGER.warn("Nie udało się stworzyć bota {}: {}", r.nick, e2.toString());
				return;
			}
		}
		AABB box = new AABB(x - 3, sy - 3, z - 3, x + 3, sy + 6, z + 3);
		for (Mannequin m : level.getEntitiesOfClass(Mannequin.class, box, BotManager::isBot)) {
			if (r.nick.equals(m.getName().getString()) && !isTracked(m)) {
				s.entity = m;
				if (!r.hasHome) {
					r.hasHome = true;
					r.hx = m.getX();
					r.hz = m.getZ();
					save();
				}
				if (!r.kit) {
					r.kit = true;
					giveKit(r);
					save();
					saveInv();
				}
				equip(m, r);
				if (!r.funded) {
					r.funded = true;
					Pieniadze.addAll(Map.of("bot:" + r.nick, FUNDS_PER_LEVEL * r.level));
					save();
				}
				s.wait = 0;
				s.tx = m.getX();
				s.tz = m.getZ();
				return;
			}
		}
	}

	/** Przedmioty wyrzucone przy śmierci bota: ten sam bot ich nie podnosi z powrotem (stary kit i eventówka przepadają), inne boty i gracze mogą. */
	private static final Map<java.util.UUID, String> OWN_DROPS = new java.util.concurrent.ConcurrentHashMap<>();

	private static void dropOwn(ServerLevel level, Rec r, State s, ItemStack st) {
		ItemEntity ie = new ItemEntity(level, s.lx, s.ly + 0.5, s.lz, st);
		OWN_DROPS.put(ie.getUUID(), r.nick);
		level.addFreshEntity(ie);
	}

	private static void dropAll(ServerLevel level, Rec r, State s) {
		r.deaths++;
		BotChat.event(level.getServer(), "death", r.nick);
		Mannequin dead = s.entity;
		if (dead != null) {
			net.minecraft.world.entity.EquipmentSlot[] all = {net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.entity.EquipmentSlot.HEAD,
					net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
			for (net.minecraft.world.entity.EquipmentSlot sl : all) {
				try {
					ItemStack w = dead.getItemBySlot(sl);
					if (!w.isEmpty()) { // noszony ubiór też wypada
						dropOwn(level, r, s, w.copy());
						dead.setItemSlot(sl, ItemStack.EMPTY);
					}
				} catch (Exception e) {
					MyMod.LOGGER.warn("Wyrzucanie ubioru bota: {}", e.toString());
				}
			}
		}
		SimpleContainer c = inv(r.nick);
		for (int i = 0; i < c.getContainerSize(); i++) { // cały ekwipunek (kit, eventówki, materiały) wypada na ziemię
			ItemStack st = c.removeItemNoUpdate(i);
			if (!st.isEmpty()) dropOwn(level, r, s, st);
		}
		fillKit(c, 1 + RNG.nextInt(10)); // odradza się z nowym, innym kitem (bez eventówki)
		saveInv();
	}

	/** Co 0,5 s: podnoszenie przedmiotów z ziemi i zbieranie drewna/rud z najbliższego otoczenia. */
	private static void activity(ServerLevel level, Rec r, State s) {
		Mannequin m = s.entity;
		BotEvents.tick(level, r.nick, m, foeOf(level, s));
		SkrzynieManager.botTick(level, r.nick, inv(r.nick));
		CodzienneMisje.botTick(level, r.nick, inv(r.nick), m);
		if (s.foe == null && s.foeP == null && s.mine == null && s.tourPts == null && !BotBuilder.busy(r.nick)) BotCraft.tick(level, r.nick, m); // piec, crafting, rozwój sprzętu
		if (s.fleeT > 0) s.fleeT -= 10;
		if (s.foe == null && s.foeP == null && s.mine == null && !BotBuilder.busy(r.nick) && s.rtpT <= 0) seekWood(level, r, s);
		AABB near = m.getBoundingBox().inflate(1.5, 1.0, 1.5);
		for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, near)) {
			if (ie.isRemoved() || PLAYER_ITEMS.contains(ie.getUUID()) || r.nick.equals(OWN_DROPS.get(ie.getUUID()))) continue; // rzeczy graczy zostają na ziemi
			ItemStack st = ie.getItem();
			if (st.isEmpty()) continue;
			int before = st.getCount();
			int left = addStack(inv(r.nick), st.copy());
			if (left <= 0) {
				ie.discard();
			} else if (left < before) {
				st.setCount(left);
				ie.setItem(st);
			}
		}
		unstick(level, m);
		if (s.foe == null && s.foeP == null && s.mine == null && s.tourPts == null && s.fleeT <= 0 && s.rtpT <= 0 && !BotBuilder.busy(r.nick)
				&& Math.floorMod(r.nick.hashCode() + level.getGameTime(), 40) < 10) { // co ok. 2 s, gdy bot nic nie robi
			upgradeGear(m, r);
			if (roleAct(level, r, s, m)) { /* zajęty zadaniem roli */ }
			else if (!lootScan(level, r, m)) harvest(level, r, m);
		}
		if (BotBuilder.busy(r.nick)) {
			if (s.act++ % 2 == 0) BotBuilder.step(level, r.nick, m); // w trakcie budowy co 1 s
			return;
		}
		if (++s.act % 2 != 0) return;
		if (BotBuilder.step(level, r.nick, m)) return;
		int bx = (int) Math.floor(m.getX()), bz = (int) Math.floor(m.getZ());
		if (Math.abs(bx) < 30 && Math.abs(bz) < 40) return; // okolice spawnu, rynku i stawu zostają nietknięte
		int by = (int) Math.floor(m.getY());
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = 0; dy <= 4; dy++) {
					BlockPos bp = new BlockPos(bx + dx, by + dy, bz + dz);
					BlockState st = level.getBlockState(bp);
					if (st.is(BlockTags.LOGS) || st.is(BlockTags.COAL_ORES) || st.is(BlockTags.IRON_ORES)
							|| st.is(BlockTags.COPPER_ORES) || st.is(BlockTags.DIAMOND_ORES) || st.is(BlockTags.EMERALD_ORES)
							|| st.is(BlockTags.LAPIS_ORES) || st.is(BlockTags.REDSTONE_ORES)) {
						level.destroyBlock(bp, true);
						return;
					}
				}
			}
		}
	}

	/** Każdy bot dostaje zestaw i pieniądze od razu (nie dopiero po pojawieniu się), żeby od startu miał co sprzedawać i za co kupować. */
	private static void provision() {
		boolean ch = false;
		for (Rec r : ROSTER) {
			if (!r.kit) {
				r.kit = true;
				giveKit(r);
				ch = true;
			}
			if (!r.gear && r.kit) { // boty sprzed tej wersji: dostają miecz, kilof i zbroję
				r.gear = true;
				SimpleContainer gc = inv(r.nick);
				gc.addItem(new ItemStack(swordFor(r.level)));
				gc.addItem(new ItemStack(pickFor(r.level)));
				for (Item a : armorFor(r.level)) gc.addItem(new ItemStack(a));
				ch = true;
			}
			if (!r.mats && r.kit) { // boty sprzed tej wersji: dodatkowe materiały budowlane
				r.mats = true;
				SimpleContainer bc = inv(r.nick);
				bc.addItem(new ItemStack(Items.COBBLESTONE, 160));
				bc.addItem(new ItemStack(Items.OAK_PLANKS, 96));
				bc.addItem(new ItemStack(Items.OAK_LOG, 48));
				bc.addItem(new ItemStack(Items.TORCH, 24));
				bc.addItem(new ItemStack(Items.WHEAT_SEEDS, 60));
				ch = true;
			}
			if (!r.mats2 && r.kit) { // boty sprzed tej wersji: bloki na mur dookoła działki
				r.mats2 = true;
				inv(r.nick).addItem(new ItemStack(Items.COBBLESTONE, 480));
				ch = true;
			}
			if (!r.event) { // istniejące boty też dostają jedną eventówkę
				r.event = true;
				Item ev = Rynek.randomEventItem();
				if (ev != null) inv(r.nick).addItem(new ItemStack(ev));
				ch = true;
			}
			if (!r.funded) {
				r.funded = true;
				Pieniadze.addAll(Map.of("bot:" + r.nick, FUNDS_PER_LEVEL * r.level));
				ch = true;
			}
		}
		if (ch) {
			save();
			saveInv();
		}
	}

	/** Jednorazowy zestaw startowy zależny od poziomu (trafia do ekwipunku, bot może to sprzedać). */
	/** Zestaw startowy bota (większy niż wcześniej); przy pierwszym pojawieniu dochodzi losowa eventówka. */
	private static void giveKit(Rec r) {
		SimpleContainer c = inv(r.nick);
		fillKit(c, r.level);
		r.gear = true;
		r.mats = true;
		r.mats2 = true;
		if (!r.event) { // pierwszy raz: jedna losowa eventówka
			r.event = true;
			Item ev = Rynek.randomEventItem();
			if (ev != null) c.addItem(new ItemStack(ev));
		}
	}

	/** Zestaw startowy zależny od poziomu 1-10 (używany też dla graczy przy pierwszym wejściu). */
	public static void fillKit(SimpleContainer c, int l) {
		c.addItem(new ItemStack(Items.BREAD, 10 + l * 3));
		c.addItem(new ItemStack(Items.COOKED_BEEF, 4 + l * 2));
		c.addItem(new ItemStack(Items.COBBLESTONE, 480 + l * 16));
		c.addItem(new ItemStack(Items.COAL, 8 + l * 4));
		c.addItem(new ItemStack(Items.OAK_LOG, 64 + l * 8));
		c.addItem(new ItemStack(Items.OAK_PLANKS, 160 + l * 8));
		c.addItem(new ItemStack(Items.APPLE, 3 + l));
		c.addItem(new ItemStack(Items.TORCH, 32 + l * 4));
		c.addItem(new ItemStack(Items.STICK, 8));
		c.addItem(new ItemStack(Items.WHEAT_SEEDS, 40 + l * 12));
		c.addItem(new ItemStack(Items.WATER_BUCKET, 1));
		c.addItem(new ItemStack(Items.BUCKET, 1));
		if (l >= 2) {
			c.addItem(new ItemStack(Items.IRON_INGOT, l * 3));
			c.addItem(new ItemStack(Items.RAW_COPPER, l * 2));
			c.addItem(new ItemStack(Items.REDSTONE, l * 2));
		}
		if (l >= 3) {
			c.addItem(new ItemStack(Items.ARROW, l * 12));
			c.addItem(new ItemStack(Items.GOLD_INGOT, l));
			c.addItem(new ItemStack(Items.LAPIS_LAZULI, l * 2));
			c.addItem(new ItemStack(Items.SNOWBALL, 32));
		}
		if (l >= 3) c.addItem(new ItemStack(Items.COBWEB, l * 6)); // PvP: pajęczyny pod nogi wroga
		if (l >= 4) c.addItem(new ItemStack(Items.ENDER_PEARL, l >= 8 ? 12 : l >= 6 ? 8 : 4)); // ucieczka do domu
		if (l >= 5) {
			c.addItem(new ItemStack(Items.OBSIDIAN, l * 10)); // tarcza z obsydianu w walce
			c.addItem(new ItemStack(Items.GOLDEN_APPLE, l >= 8 ? 10 : 5)); // leczenie w walce
		}
		if (l >= 4) {
			c.addItem(new ItemStack(Items.SHIELD, 1));
			c.addItem(new ItemStack(Items.SLIME_BALL, l));
			c.addItem(new ItemStack(Items.QUARTZ, l * 2));
		}
		if (l >= 6) {
			c.addItem(new ItemStack(Items.DIAMOND, l - 3));
			c.addItem(new ItemStack(Items.TNT, l >= 8 ? 12 : 6));
		}
		if (l >= 8) {
			c.addItem(new ItemStack(Items.EMERALD, l - 4));
			c.addItem(new ItemStack(Items.GOLDEN_CARROT, 8));
		}
		if (l >= 9) c.addItem(new ItemStack(Items.GOLDEN_APPLE, 16));
		// starter pack: miecz, zbroja i kilof zależne od poziomu (prawdziwe przedmioty)
		c.addItem(new ItemStack(swordFor(l)));
		c.addItem(new ItemStack(pickFor(l)));
		for (Item a : armorFor(l)) c.addItem(new ItemStack(a));
	}

	private static Item pickFor(int l) {
		return l >= 10 ? Items.NETHERITE_PICKAXE : l >= 7 ? Items.DIAMOND_PICKAXE : l >= 4 ? Items.IRON_PICKAXE : l >= 2 ? Items.STONE_PICKAXE : Items.WOODEN_PICKAXE;
	}

	public static final java.util.Set<String> KIT_PATHS = java.util.Set.of("bread", "cooked_beef", "cobblestone", "coal", "apple",
			"iron_ingot", "raw_copper", "redstone", "arrow", "gold_ingot", "lapis_lazuli", "snowball", "slime_ball", "quartz",
			"diamond", "tnt", "emerald", "golden_apple", "water_bucket", "cobweb", "obsidian", "ender_pearl");

	private static Item swordFor(int l) {
		return l >= 10 ? Items.NETHERITE_SWORD : l >= 7 ? Items.DIAMOND_SWORD : l >= 4 ? Items.IRON_SWORD : l >= 2 ? Items.STONE_SWORD : Items.WOODEN_SWORD;
	}

	private static Item[] armorFor(int l) {
		if (l >= 10) return new Item[] {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
		if (l >= 7) return new Item[] {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
		if (l >= 4) return new Item[] {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
		return new Item[] {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
	}

	private static final net.minecraft.world.entity.EquipmentSlot[] ARMOR_SLOTS = {net.minecraft.world.entity.EquipmentSlot.HEAD,
			net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};

	/** Zakłada na bota miecz i zbroję, zabierając je z jego ekwipunku (to prawdziwe przedmioty, wypadają po śmierci). */
	private static void equip(Mannequin m, Rec r) {
		SimpleContainer c = inv(r.nick);
		try {
			ItemStack sw = takeOne(c, swordFor(r.level));
			if (!sw.isEmpty()) m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, sw);
			Item[] armor = armorFor(r.level);
			for (int i = 0; i < armor.length; i++) {
				ItemStack a = takeOne(c, armor[i]);
				if (!a.isEmpty()) m.setItemSlot(ARMOR_SLOTS[i], a);
			}
		} catch (Exception e) {
			MyMod.LOGGER.warn("Ubiór bota {}: {}", r.nick, e.toString());
		}
	}

	private static ItemStack takeOne(SimpleContainer c, Item it) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (s.is(it)) {
				ItemStack one = s.copyWithCount(1);
				s.shrink(1);
				if (s.isEmpty()) c.setItem(i, ItemStack.EMPTY);
				c.setChanged();
				return one;
			}
		}
		return ItemStack.EMPTY;
	}

	/** 3% szansy po zabiciu bota przez gracza: wypada jedna losowa księga ze wszystkich. */
	private static void dropBook(ServerLevel lv, Mannequin m) {
		List<Item> books = new ArrayList<>();
		for (Item it : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
			net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it);
			if ("mymod".equals(id.getNamespace()) && id.getPath().startsWith("ksiega_")) books.add(it);
		}
		if (books.isEmpty()) return;
		Item pick = books.get(RNG.nextInt(books.size()));
		lv.addFreshEntity(new ItemEntity(lv, m.getX(), m.getY() + 0.5, m.getZ(), new ItemStack(pick)));
	}

	/** Losuje miejsce na lądzie w załadowanym terenie, min. 20 bloków od innych botów i poza spawnem; null = brak. */
	/** Cudze bazy (środek x, z i połowa boku muru): domy botów z murem oraz mury sojuszy; bez baz bota self i jego sojuszu. */
	public static List<int[]> bases(String self) {
		Rec me = self == null ? null : recOf(self);
		List<int[]> l = new ArrayList<>();
		for (Rec x : ROSTER) {
			if (!x.hasHome || x.nick.equals(self)) continue;
			if (me != null && me.clan != null && me.clan.equals(x.clan)) continue; // baza wspólna z sojusznikami
			l.add(new int[] {(int) Math.floor(x.hx), (int) Math.floor(x.hz), x.wallP > 0 ? x.wallP / 2 : 11});
			if (x.cwP > 0) l.add(new int[] {(int) Math.floor(x.hx), (int) Math.floor(x.hz), x.cwP / 2});
		}
		return l;
	}

	/** Czy baza o środku (x, z) i połowie boku half jest co najmniej 14 bloków od krawędzi każdej cudzej bazy. */
	public static boolean baseGapOk(String self, double x, double z, int half) {
		for (int[] b : bases(self)) {
			if (Math.max(Math.abs(b[0] - x), Math.abs(b[1] - z)) < half + b[2] + 14) return false;
		}
		return true;
	}

	private static double[] pickSpot(ServerLevel level, String self) {
		for (int pass = 0; pass < 2; pass++) { // 1. w załadowanych chunkach, 2. szerzej (ładuje chunk)
			int range = pass == 0 ? 250 : 600;
			for (int tries = 0; tries < 60; tries++) {
				double ax = RNG.nextInt(2 * range + 1) - range + 0.5, az = RNG.nextInt(2 * range + 1) - range + 0.5;
				if (Math.abs(ax) < 14 && Math.abs(az) < 14) continue;
				if (pass == 0 && !level.hasChunkAt(new BlockPos((int) ax, 64, (int) az))) continue;
				if (pass == 1) level.getChunk((int) Math.floor(ax) >> 4, (int) Math.floor(az) >> 4);
				if (isWaterAt(level, ax, az)) continue;
				boolean close = false;
				for (Map.Entry<String, State> e : STATES.entrySet()) {
					Mannequin o = e.getValue().entity;
					if (o == null || o.isRemoved() || e.getKey().equals(self)) continue;
					double dx = o.getX() - ax, dz = o.getZ() - az;
					if (dx * dx + dz * dz < 400) { close = true; break; }
				}
				if (!close && baseGapOk(self, ax, az, 11)) return new double[] {ax, az}; // nowa baza min. 14 bloków od cudzej
			}
		}
		return null;
	}

	/** Czy w promieniu radius bloków od punktu stoi inny bot. */
	private static boolean botWithin(String self, double x, double z, double radius) {
		for (Map.Entry<String, State> e : STATES.entrySet()) {
			Mannequin o = e.getValue().entity;
			if (o == null || o.isRemoved() || !o.isAlive() || e.getKey().equals(self)) continue;
			double dx = o.getX() - x, dz = o.getZ() - z;
			if (dx * dx + dz * dz < radius * radius) return true;
		}
		return false;
	}

	/** Wolne miejsce przy domu bota (min. 5 bloków od innych botów, 5-9 bloków od zajętego miejsca) albo null, gdy sam dom jest wolny lub nic nie znaleziono. */
	private static double[] freeNear(ServerLevel level, String self, double hx, double hz) {
		if (!botWithin(self, hx, hz, 5)) return null;
		for (int t = 0; t < 40; t++) {
			double ang = RNG.nextDouble() * Math.PI * 2, rad = 5 + RNG.nextDouble() * 4; // 5-9 bloków od zajętego miejsca
			double x = hx + Math.cos(ang) * rad, z = hz + Math.sin(ang) * rad;
			if (!level.hasChunkAt(new BlockPos((int) Math.floor(x), 64, (int) Math.floor(z))) || isWaterAt(level, x, z)) continue;
			if (!botWithin(self, x, z, 5)) return new double[] {x, z};
		}
		return null;
	}

	private static boolean isWaterAt(ServerLevel level, double x, double z) {
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
		return level.getFluidState(new BlockPos(bx, gy - 1, bz)).is(net.minecraft.tags.FluidTags.WATER);
	}

	private static boolean inPond(double x, double z) {
		return x >= POND_X1 && x <= POND_X2 && z >= POND_Z1 && z <= POND_Z2;
	}

	/** Liczba botów sprzymierzonych z danym graczem (sojusz zawarty kuckami). */
	public static int allyCount(java.util.UUID player) {
		String pid = player.toString();
		int n = 0;
		for (Rec r : ROSTER) if (pid.equals(r.partner)) n++;
		return n;
	}

	public static boolean isBot(Mannequin m) {
		String n = m.getName().getString();
		for (Rec r : ROSTER) if (r.nick.equals(n)) return true;
		return false;
	}

	private static boolean isTracked(Mannequin m) {
		for (State s : STATES.values()) if (s.entity == m) return true;
		return false;
	}

	private static final int[] DX = {1, 0, -1, 0};
	private static final int[] DZ = {0, 1, 0, -1};

	private static boolean isOre(BlockState st) {
		return st.is(BlockTags.COAL_ORES) || st.is(BlockTags.IRON_ORES) || st.is(BlockTags.COPPER_ORES) || st.is(BlockTags.GOLD_ORES)
				|| st.is(BlockTags.DIAMOND_ORES) || st.is(BlockTags.EMERALD_ORES) || st.is(BlockTags.LAPIS_ORES) || st.is(BlockTags.REDSTONE_ORES);
	}

	private static boolean breakable(ServerLevel level, BlockPos p) {
		BlockState st = level.getBlockState(p);
		return st.isAir() || (st.getDestroySpeed(level, p) >= 0 && !st.hasBlockEntity() && level.getFluidState(p).isEmpty());
	}

	/** Czasem bot z kilofem schodzi pod ziemię (szyb w dół, potem tunel), wykopuje rudy i wraca na powierzchnię. */
	private static void maybeStartMine(ServerLevel level, Rec r, State s) {
		Mannequin m = s.entity;
		if (s.mine != null || s.foe != null || s.foeP != null || BotBuilder.busy(r.nick) || "G".equals(r.role) || r.partner != null || s.ritual > 0
				|| "F".equals(r.role) || "L".equals(r.role)
				|| RNG.nextInt("H".equals(r.role) ? 20 : "Z".equals(r.role) ? 60 : "B".equals(r.role) ? 120 : 240) != 0) return;
		if ("H".equals(r.role)) { // hutnik schodzi własnym szybem 3x3 przy bazie (dopóki go nie wykopie, nie chodzi na zwykłe wyprawy)
			if (!r.shaftDone) return;
			Mine hk = new Mine();
			hk.sx = hk.x = r.shaftX + 0.5;
			hk.sz = hk.z = r.shaftZ + 0.5;
			hk.sy = hk.y = r.shaftTop - r.shaftDepth;
			hk.targetY = (int) hk.y;
			hk.phase = 1;
			hk.maxSteps = 90;
			hk.dir = RNG.nextInt(4);
			s.mine = hk;
			m.setPos(hk.x, hk.y, hk.z); // zjeżdża na dół po drabince
			return;
		}
		double x = m.getX(), z = m.getZ();
		if (Math.abs(x) < 40 && Math.abs(z) < 50) return;
		if (!hasPickaxe(r.nick) || isWaterAt(level, x, z)) return;
		Mine k = new Mine();
		k.sx = k.x = x;
		k.sz = k.z = z;
		k.sy = k.y = m.getY();
		boolean deep = RNG.nextBoolean(); // połowa wypraw idzie na poziom diamentów (jak zwykły gracz)
		k.targetY = deep ? -52 - RNG.nextInt(8) : (int) Math.max(-48, k.sy - (20 + RNG.nextInt(35)));
		if (k.targetY > k.sy - 5) k.targetY = (int) k.sy - 5;
		k.maxSteps = deep ? 120 : 70;
		k.dir = RNG.nextInt(4);
		s.mine = k;
	}

	private static boolean hasPickaxe(String nick) {
		SimpleContainer c = inv(nick);
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).is(net.minecraft.tags.ItemTags.PICKAXES)) return true;
		}
		return false;
	}

	private static void endMine(Mannequin m, State s, Rec r) {
		Mine k = s.mine;
		s.mine = null;
		s.wait = 0;
		if (k != null) {
			if (r != null && "H".equals(r.role) && r.shaftDone) { // hutnik wspina się po drabince na górę, do bazy
				m.setPos(r.shaftX + 0.5, r.shaftTop, r.shaftZ + 0.5);
				s.tx = r.shaftX + 0.5;
				s.tz = r.shaftZ + 0.5;
				return;
			}
			m.setPos(k.sx, k.sy, k.sz); // wracają na powierzchnię w to samo miejsce
			s.tx = k.sx;
			s.tz = k.sz;
		}
	}

	private static void mineStep(ServerLevel level, Rec r, State s) {
		Mine k = s.mine;
		Mannequin m = s.entity;
		if (++k.tick % 10 != 0) {
			m.setPos(k.x, k.y, k.z);
			return;
		}
		if (k.phase == 0) { // szyb w dół
			BlockPos below = BlockPos.containing(k.x, k.y - 1, k.z);
			if (!breakable(level, below) || !breakable(level, below.below()) || !level.getFluidState(below.below()).isEmpty()) {
				k.phase = 1; // trafił w coś, czego nie rusza: kopie tunel w tym miejscu
			} else {
				level.destroyBlock(below, true);
				k.y -= 1;
				if (k.y <= k.targetY) k.phase = 1;
			}
		} else if (k.phase == 1) { // tunel 1x2
			int dx = DX[k.dir], dz = DZ[k.dir];
			BlockPos f1 = BlockPos.containing(k.x + dx, k.y, k.z + dz);
			BlockPos f2 = f1.above();
			if (!breakable(level, f1) || !breakable(level, f2) || !breakable(level, f1.below()) || !level.getFluidState(f1.below()).isEmpty()) {
				k.dir = (k.dir + 1) % 4;
				if (++k.turns > 8) k.phase = 2;
			} else {
				level.destroyBlock(f1, true);
				level.destroyBlock(f2, true);
				k.x += dx;
				k.z += dz;
				for (int ox = -1; ox <= 1; ox++) {
					for (int oy = -1; oy <= 2; oy++) {
						for (int oz = -1; oz <= 1; oz++) {
							BlockPos o = BlockPos.containing(k.x + ox, k.y + oy, k.z + oz);
							if (isOre(level.getBlockState(o))) level.destroyBlock(o, true);
						}
					}
				}
				if (++k.steps >= k.maxSteps) k.phase = 2;
			}
		} else {
			endMine(m, s, r);
			return;
		}
		m.setPos(k.x, k.y, k.z);
	}

	/** Gracz kuca 5 razy szybko (Shift) obok bota: bot zakłada z nim gildię (gracz zostaje jej przywódcą). */
	private static void crouchTick(MinecraftServer server) {
		long t = server.getTickCount();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			int[] c = CROUCH.computeIfAbsent(p.getUUID(), k -> new int[4]);
			boolean down = p.isShiftKeyDown();
			boolean was = c[2] == 1;
			c[2] = down ? 1 : 0;
			if (!(down && !was) || t < c[3]) continue;
			if (c[0] == 0 || t - c[1] > 60) { c[0] = 0; c[1] = (int) t; }
			c[0]++;
			if (c[0] < 5) continue;
			c[0] = 0;
			String best = null;
			double bd = 5 * 5;
			for (Map.Entry<String, State> e : STATES.entrySet()) {
				Mannequin o = e.getValue().entity;
				Rec ro = recOf(e.getKey());
				if (o == null || o.isRemoved() || !o.isAlive() || ro == null || ro.partner != null) continue;
				double d = o.distanceToSqr(p);
				if (d < bd) { bd = d; best = e.getKey(); }
			}
			for (Map.Entry<String, State> e : STATES.entrySet()) { // bot, który sam zaprosił, ma pierwszeństwo
				Mannequin o = e.getValue().entity;
				if (o != null && !o.isRemoved() && o.isAlive() && p.getUUID().equals(e.getValue().proposeTo) && o.distanceToSqr(p) < 8 * 8) best = e.getKey();
			}
			if (best == null) continue;
			c[3] = (int) t + 100; // 5 s przerwy między próbami
			int mine = 0;
			String pid = p.getUUID().toString();
			for (Rec r : ROSTER) if (pid.equals(r.partner)) mine++;
			if (mine >= 9) {
				p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Masz już 9 botów w swojej gildii (maks. 10 członków).").withStyle(net.minecraft.ChatFormatting.RED));
				continue;
			}
			Rec r = recOf(best);
			State inviter = STATES.get(best);
			boolean invited = inviter != null && p.getUUID().equals(inviter.proposeTo);
			if (true) { // 5 kucnięć obok bota = gildia
				if (inviter != null) {
					inviter.proposeTo = null;
					if (inviter.entity != null) inviter.entity.setShiftKeyDown(false);
				}
				String pc = playerClan(pid);
				r.clan = pc != null ? pc : newClanName();
				if (pc == null) r.leaderP = pid; // założyciel jest przywódcą
				r.partner = pid;
				r.role = null;
				r.buddy = null;
				State bst = STATES.get(best);
				if (bst != null) { // koniec dotychczasowej walki z tym graczem
					bst.foeP = null;
					bst.foe = null;
					bst.retal = false;
					bst.fight = 0;
				}
				save();
				server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal(p.getName().getString() + (pc == null ? " założył gildię " + r.clan + " i jest jej przywódcą (z botem " : " dołączył do gildii " + r.clan + " razem z botem ")
						+ best + (pc == null ? ")!" : "!")).withStyle(net.minecraft.ChatFormatting.AQUA), false);
			} else {
				p.sendSystemMessage(net.minecraft.network.chat.Component.literal(best + " nie chce gildii. Spróbuj ponownie za chwilę.").withStyle(net.minecraft.ChatFormatting.GRAY));
			}
		}
	}

	/** Gildia gracza (np. D7) albo null - do panelu statystyk. */
	public static String guildOf(java.util.UUID id) {
		return playerClan(id.toString());
	}

	/** Kolor gildii do wyświetlania nazwy. */
	public static net.minecraft.ChatFormatting guildFormat(String clan) {
		return TAG_COLORS[guildColor(clan)];
	}

	/** Klan (sojusz) gracza: klan botów, z którymi jest sprzymierzony; null, gdy nie ma. */
	private static String playerClan(String uuid) {
		for (Rec r : ROSTER) if (uuid.equals(r.partner) && r.clan != null) return r.clan;
		return null;
	}

	/** Obok nicku bota z sojuszem pokazuje [KLAN]: zielony dla członków tego samego sojuszu, niebieski dla pozostałych (osobno dla każdego gracza). */
	private static void allianceTags(MinecraftServer server) {
		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			Map<String, String> seen = SEEN.computeIfAbsent(viewer.getUUID(), k -> new HashMap<>());
			String mine = playerClan(viewer.getUUID().toString());
			for (Map.Entry<String, State> e : STATES.entrySet()) {
				Rec r = recOf(e.getKey());
				Mannequin m = e.getValue().entity;
				if (r == null || m == null || m.isRemoved() || r.clan == null) continue;
				boolean same = mine != null && mine.equals(r.clan);
				String sn = m.getScoreboardName();
				boolean lead = r.nick.equals(leaderOf(r.clan));
				String sig = r.clan + "|" + (same ? "g" : "b") + (lead ? "|L" : "");
				if (sig.equals(seen.get(sn))) continue;
				seen.put(sn, sig);
				try {
					net.minecraft.ChatFormatting col = TAG_COLORS[guildColor(r.clan)];
					net.minecraft.world.scores.PlayerTeam team = new net.minecraft.world.scores.PlayerTeam(server.getScoreboard(), "al_" + r.clan + (same ? "_g" : "_b") + (lead ? "_L" : ""));
					team.setColor(col);
					team.setPlayerPrefix(net.minecraft.network.chat.Component.literal("[" + r.clan + (lead ? " przywódca" : "") + "] ").withStyle(col));
					team.getPlayers().add(sn); // pakiet tworzenia drużyny od razu zawiera tego bota
					viewer.connection.send(net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
				} catch (Exception ex) {
					MyMod.LOGGER.warn("Znacznik sojuszu: {}", ex.toString());
				}
			}
		}
	}

	/** Działka bota (posesja): siatka 4x4 pól co 13 bloków wokół jego pierwszego spawnu; null, gdy brak domu albo wszystkie pola w strefie spawnu. */
	/** Baza sojuszu: bot z klanu (bez sojuszu z graczem), który zaczął mur sojuszu albo ma gotowy własny mur; z wyłączeniem exclude. */
	private static Rec clanBaseRec(String clan, Rec exclude) {
		if (clan == null) return null;
		Rec walled = null, first = null;
		for (Rec x : ROSTER) {
			if (x == exclude || !clan.equals(x.baseClan != null ? x.baseClan : x.clan) || x.partner != null || !x.hasHome) continue;
			if (x.cwP > 0) return x;
			if (walled == null && x.wallDone) walled = x;
			if (first == null) first = x;
		}
		return walled != null ? walled : first;
	}

	/** Środek bazy sojuszu bota albo null, gdy bot nie ma sojuszu z botami. */
	public static double[] clanBaseHome(String nick) {
		Rec r = recOf(nick);
		if (r == null || r.clan == null || r.partner != null) return null;
		Rec b = clanBaseRec(r.clan, null);
		return b == null ? null : new double[] {b.hx, b.hz};
	}

	/** {rozmiar muru sojuszu, wysokość, 1 gdy gotowy, poziom gruntu, rozmiar własnego muru właściciela bazy, 1 gdy jego własny mur gotowy, jego poziom gruntu}. */
	public static int[] clanWallInfo(String nick) {
		Rec r = recOf(nick);
		Rec b = r == null || r.clan == null ? null : clanBaseRec(r.clan, null);
		if (b == null) return null;
		return new int[] {b.cwP, b.cwH, b.cwDone ? 1 : 0, b.cwY, b.wallP, b.wallDone ? 1 : 0, b.wallY};
	}

	public static void setClanWall(String nick, int size, int h, int y, boolean done) {
		Rec r = recOf(nick);
		Rec b = r == null || r.clan == null ? null : clanBaseRec(r.clan, null);
		if (b == null) return;
		b.cwP = size;
		b.cwH = h;
		b.cwY = y;
		b.cwDone = done;
		save();
	}

	/** {x, y, z pieca, 1 gdy bot ma warsztat}. */
	public static int[] workshop(String nick) {
		Rec r = recOf(nick);
		return r == null ? new int[] {0, 0, 0, 0} : new int[] {r.wsx, r.wsy, r.wsz, r.hasWs ? 1 : 0};
	}

	public static void setWorkshop(String nick, int x, int y, int z, boolean has) {
		Rec r = recOf(nick);
		if (r == null) return;
		r.wsx = x;
		r.wsy = y;
		r.wsz = z;
		r.hasWs = has;
		save();
	}

	/** Dom bota (środek jego działki) albo null, gdy jeszcze się nie pojawił. */
	public static double[] home(String nick) {
		Rec r = recOf(nick);
		return r == null || !r.hasHome ? null : new double[] {r.hx, r.hz};
	}

	/** {rozmiar muru, wysokość, 1 gdy gotowy, poziom gruntu}; rozmiar 0 = mur jeszcze nie zaczęty. */
	public static int[] wallInfo(String nick) {
		Rec r = recOf(nick);
		return r == null ? new int[] {0, 0, 0, 0} : new int[] {r.wallP, r.wallH, r.wallDone ? 1 : 0, r.wallY};
	}

	public static void setWall(String nick, int size, int h, int y, boolean done) {
		Rec r = recOf(nick);
		if (r == null) return;
		r.wallP = size;
		r.wallH = h;
		r.wallY = y;
		r.wallDone = done;
		save();
	}

	public static double[] plotSlot(String nick) {
		Rec r = recOf(nick);
		if (r == null || !r.hasHome) return null;
		for (int k = 0; k < 36; k++) {
			int i = (r.slot + k) % 36;
			double x = r.hx + (i % 6) * 13 - 32;
			double z = r.hz + (i / 6) * 13 - 32;
			if (Math.abs(x) < 40 && Math.abs(z) < 50) continue; // nie w okolicy spawnu, rynku i stawu
			if (k > 0) r.slot = i;
			return new double[] {x, z};
		}
		return null;
	}

	public static void plotAdvance(String nick) {
		Rec r = recOf(nick);
		if (r != null) r.slot = (r.slot + 1) % 36;
	}

	/** Ustawia cel marszu bota (np. na jego działkę). */
	public static void goTo(String nick, double x, double z) {
		State s = STATES.get(nick);
		if (s != null) {
			s.tx = x;
			s.tz = z;
			s.wait = 0;
		}
	}

	public static boolean isGuard(String nick) {
		for (Rec r : ROSTER) if (r.nick.equals(nick)) return "G".equals(r.role) || r.partner != null;
		return false;
	}

	/** Członek gildii, który nie buduje nowych budowli (robi to budowniczy B): przywódca, zbieracz, farmer, hutnik. */
	public static boolean noStructures(String nick) {
		Rec r = recOf(nick);
		return r != null && r.clan != null && r.partner == null && ("L".equals(r.role) || "Z".equals(r.role) || "F".equals(r.role) || "H".equals(r.role));
	}

	public static boolean isBuilder(String nick) {
		for (Rec r : ROSTER) if (r.nick.equals(nick)) return "B".equals(r.role);
		return false;
	}

	private static final long BUDGET_NS = 20_000_000L; // 20 ms na tick dla wszystkich botów
	private static int cursor = 0, skipped = 0, profTicks = 0;
	private static long profNs = 0;

	private static final Map<String, Rec> REC_IDX = new HashMap<>();

	private static Rec recOf(String nick) {
		if (REC_IDX.size() != ROSTER.size()) { // indeks po nicku (zamiast przeszukiwania 300 botów za każdym razem)
			REC_IDX.clear();
			for (Rec r : ROSTER) REC_IDX.put(r.nick, r);
		}
		return REC_IDX.get(nick);
	}

	/** Liczba członków sojuszu bota (0, gdy nie ma sojuszu). */
	public static int clanMembers(String nick) {
		Rec r = recOf(nick);
		return r == null || r.clan == null ? 0 : clanSize(r.clan);
	}

	private static int clanSize(String clan) {
		int n = 0;
		for (Rec r : ROSTER) if (clan.equals(r.clan)) n++;
		return n;
	}

	/** Nowa gildia dostaje najmniejszy wolny numer: D1, D2, D3... */
	private static String newClanName() {
		for (int n = 1; ; n++) {
			String name = "D" + n;
			boolean used = false;
			for (Rec r : ROSTER) if (name.equals(r.clan)) used = true;
			if (!used) return name;
		}
	}

	/** Seria zabójstw bota (do nagrody za głowę). */
	private static final Map<String, Integer> STREAK = new java.util.HashMap<>();

	// ---------- gildie: kolory, wojny nocne, przejmowanie baz ----------

	private static final net.minecraft.world.level.block.Block[] BANNERS = {net.minecraft.world.level.block.Blocks.WHITE_BANNER, net.minecraft.world.level.block.Blocks.ORANGE_BANNER, net.minecraft.world.level.block.Blocks.MAGENTA_BANNER,
			net.minecraft.world.level.block.Blocks.LIGHT_BLUE_BANNER, net.minecraft.world.level.block.Blocks.YELLOW_BANNER, net.minecraft.world.level.block.Blocks.LIME_BANNER, net.minecraft.world.level.block.Blocks.PINK_BANNER, net.minecraft.world.level.block.Blocks.GRAY_BANNER, net.minecraft.world.level.block.Blocks.LIGHT_GRAY_BANNER,
			net.minecraft.world.level.block.Blocks.CYAN_BANNER, net.minecraft.world.level.block.Blocks.PURPLE_BANNER, net.minecraft.world.level.block.Blocks.BLUE_BANNER, net.minecraft.world.level.block.Blocks.BROWN_BANNER, net.minecraft.world.level.block.Blocks.GREEN_BANNER, net.minecraft.world.level.block.Blocks.RED_BANNER, net.minecraft.world.level.block.Blocks.BLACK_BANNER};
	private static final net.minecraft.ChatFormatting[] TAG_COLORS = {net.minecraft.ChatFormatting.WHITE, net.minecraft.ChatFormatting.GOLD,
			net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.YELLOW,
			net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.DARK_GRAY,
			net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.DARK_AQUA, net.minecraft.ChatFormatting.DARK_PURPLE,
			net.minecraft.ChatFormatting.BLUE, net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.DARK_GREEN,
			net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.DARK_GRAY};

	/** Numer koloru gildii: D1 biała, D2 pomarańczowa ... D7 różowa (wg kolejności barw wełny/sztandarów). */
	private static int guildColor(String clan) {
		try {
			return (Integer.parseInt(clan.substring(1)) - 1) % 16;
		} catch (Exception e) {
			return 0;
		}
	}

	private static String warA, warB; // atakujący i broniący
	private static final java.util.Set<String> deadA = new java.util.HashSet<>(), deadB = new java.util.HashSet<>();
	private static java.util.List<String> membersA = new java.util.ArrayList<>(), membersB = new java.util.ArrayList<>();
	private static long warStart = 0, warNext = 0;

	private static boolean atWar(String c1, String c2) {
		return warA != null && c1 != null && c2 != null && ((c1.equals(warA) && c2.equals(warB)) || (c1.equals(warB) && c2.equals(warA)));
	}

	/** Przywódca gildii: najstarszy członek na liście botów (bez botów sprzymierzonych z graczem). */
	private static String leaderOf(String clan) {
		for (Rec r : ROSTER) if (clan.equals(r.clan) && r.leaderP != null) return null; // przywódcą jest gracz
		for (Rec r : ROSTER) if (clan.equals(r.clan) && r.partner == null) return r.nick;
		for (Rec r : ROSTER) if (clan.equals(r.clan)) return r.nick;
		return null;
	}

	private static java.util.List<String> membersOf(String clan) {
		java.util.List<String> l = new java.util.ArrayList<>();
		for (Rec r : ROSTER) if (clan.equals(r.clan) && r.partner == null) l.add(r.nick);
		return l;
	}

	private static void broadcast(MinecraftServer server, String text, net.minecraft.ChatFormatting f) {
		server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal(text).withStyle(f), false);
	}

	/** Wojny gildii: w nocy przywódca (najstarszy członek) losowej gildii wybiera cel i atakuje; walczą wszyscy; kończy się, gdy polegną wszyscy z jednej strony. */
	private static void guildWars(MinecraftServer server) {
		long t = server.getTickCount();
		if (warA != null) {
			boolean aDead = deadA.containsAll(membersA), bDead = deadB.containsAll(membersB);
			if (bDead || aDead || t - warStart > 24000) {
				String win = bDead && !aDead ? warA : aDead && !bDead ? warB : null;
				String lose = win == null ? null : win.equals(warA) ? warB : warA;
				if (win != null) {
					Rec o = clanBaseRec(lose, null);
					if (o != null) {
						o.baseClan = win.equals(o.clan) ? null : win;
						save();
						broadcast(server, "🏴 Gildia " + win + " pokonała gildię " + lose + " i przejęła jej bazę! Przywódca " + win + " zamienia sztandar na swój.", net.minecraft.ChatFormatting.GOLD);
					} else {
						broadcast(server, "🏴 Gildia " + win + " pokonała gildię " + lose + "!", net.minecraft.ChatFormatting.GOLD);
					}
				} else {
					broadcast(server, "Wojna gildii " + warA + " i " + warB + " zakończona remisem.", net.minecraft.ChatFormatting.GRAY);
				}
				warA = null;
				warB = null;
				deadA.clear();
				deadB.clear();
				warNext = t + 12000;
			}
			return;
		}
		if (t < warNext || t % 400 != 0 || RNG.nextInt(4) != 0) return;
		ServerLevel ow = server.overworld();
		long dt = ow.getGameTime() % 24000L;
		if (dt < 13000L || dt >= 23000L) return; // napady tylko w nocy
		java.util.List<String> clans = new java.util.ArrayList<>();
		for (Rec r : ROSTER) if (r.clan != null && !clans.contains(r.clan) && r.partner == null && membersOf(r.clan).size() >= 2 && clanBaseRec(r.clan, null) != null) clans.add(r.clan);
		if (clans.size() < 2) return;
		String a = clans.get(RNG.nextInt(clans.size()));
		String b;
		do { b = clans.get(RNG.nextInt(clans.size())); } while (b.equals(a));
		warA = a;
		warB = b;
		membersA = membersOf(a);
		membersB = membersOf(b);
		deadA.clear();
		deadB.clear();
		warStart = t;
		broadcast(server, "⚔ " + a + " zaatakował " + b + "!", net.minecraft.ChatFormatting.RED);
	}

	/** Gracz-przywódca gildii ma przy nicku [D6 przywódca] w kolorze gildii (drużyna na tablicy wyników). */
	private static void leaderTags(MinecraftServer server) {
		net.minecraft.server.ServerScoreboard sb = server.getScoreboard();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			String pid = p.getUUID().toString();
			String clan = null;
			for (Rec r : ROSTER) if (pid.equals(r.leaderP) && r.clan != null) clan = r.clan;
			if (clan == null) continue;
			try {
				String tn = "gld_" + clan;
				net.minecraft.world.scores.PlayerTeam team = sb.getPlayerTeam(tn);
				if (team == null) team = sb.addPlayerTeam(tn);
				net.minecraft.ChatFormatting col = TAG_COLORS[guildColor(clan)];
				team.setColor(col);
				team.setPlayerPrefix(net.minecraft.network.chat.Component.literal("[" + clan + " przywódca] ").withStyle(col));
				if (sb.getPlayersTeam(p.getScoreboardName()) != team) sb.addPlayerToTeam(p.getScoreboardName(), team);
			} catch (Exception ex) {
				MyMod.LOGGER.warn("Znacznik przywódcy: {}", ex.toString());
			}
		}
	}

	/** Zapamiętuje poległego w wojnie (wołane przy śmierci bota). */
	private static void warDeath(String nick, String clan) {
		if (warA == null || clan == null) return;
		if (clan.equals(warA)) deadA.add(nick);
		else if (clan.equals(warB)) deadB.add(nick);
	}

	/** Sztandar gildii w kolorze jej numeru na rogu muru bazy (po jej ukończeniu); po przejęciu bazy zostaje zamieniony na sztandar zdobywcy. */
	private static void ensureBanners(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (Rec x : ROSTER) {
			if (!x.hasHome || x.partner != null || !x.cwDone || x.cwP < 5 || x.clan == null) continue;
			String owner = x.baseClan != null ? x.baseClan : x.clan;
			int cx = (int) Math.floor(x.hx) + x.cwP / 2, cz = (int) Math.floor(x.hz) + x.cwP / 2;
			if (!level.hasChunkAt(new BlockPos(cx, 64, cz))) continue;
			net.minecraft.world.level.block.Block want = BANNERS[guildColor(owner)];
			for (int y = x.cwY + x.cwH + 3; y >= x.cwY; y--) {
				BlockPos p = new BlockPos(cx, y, cz);
				net.minecraft.world.level.block.state.BlockState st = level.getBlockState(p);
				if (st.getBlock() instanceof net.minecraft.world.level.block.BannerBlock) {
					if (st.getBlock() != want) level.setBlock(p, want.defaultBlockState(), 3);
					break;
				}
				if (!st.isAir() && level.getBlockState(p.above()).isAir()) {
					level.setBlock(p.above(), want.defaultBlockState(), 3);
					break;
				}
			}
		}
	}

	/** Spotkanie dwóch botów: 0 = walka (45%), 1 = pokój (10%), 2 = gildia (45%); ważne ok. 5 minut dla pary. */
	private static final Map<String, long[]> MEET = new java.util.HashMap<>();

	private static int meetDecision(String a, String b, long now) {
		String key = a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
		long[] v = MEET.get(key);
		if (v == null || v[1] < now) {
			double x = RNG.nextDouble();
			v = new long[] {x < 0.45 ? 0 : x < 0.55 ? 1 : 2, now + 6000}; // 45% walka, 10% pokój, 45% gildia
			MEET.put(key, v);
		}
		return (int) v[0];
	}

	/** Bot bez sojuszu, który stoi blisko innego wolnego bota, zaczyna rytuał: 5 szybkich kucnięć w jego stronę. */
	private static void maybeAlly(ServerLevel level, Rec r, State s) {
		Mannequin m = s.entity;
		if (s.ritual > 0 || s.foe != null || s.foeP != null || s.mine != null || BotBuilder.busy(r.nick) || RNG.nextInt(40) != 0
				|| inSpawnArea(m.getX(), m.getZ())) return;
		for (Map.Entry<String, State> e : STATES.entrySet()) {
			State os = e.getValue();
			Mannequin o = os.entity;
			if (o == null || o == m || o.isRemoved() || !o.isAlive() || os.ritual > 0 || os.foe != null || os.foeP != null
					|| os.mine != null || BotBuilder.busy(e.getKey())) continue;
			if (o.distanceToSqr(m) > 36) continue;
			if (meetDecision(r.nick, e.getKey(), level.getGameTime()) != 2) continue; // gildia tylko w 45% spotkań
			Rec ro = recOf(e.getKey());
			if (ro == null || (r.clan != null && ro.clan != null)) continue;
			String c = r.clan != null ? r.clan : ro.clan;
			if (c != null && clanSize(c) >= 10) continue;
			s.ritual = 40; // 5 kucnięć
			os.ritual = 40;
			s.ritualWith = e.getKey();
			os.ritualWith = r.nick;
			s.lead = true;
			os.lead = false;
			return;
		}
	}

	/** Zwraca true, gdy bot w tym ticku wykonuje rytuał sojuszu (patrzy na partnera i kuca). */
	private static boolean ritualStep(ServerLevel level, Rec r, State s) {
		Mannequin m = s.entity;
		State ps = s.ritualWith == null ? null : STATES.get(s.ritualWith);
		Mannequin p = ps == null ? null : ps.entity;
		if (p == null || p.isRemoved() || !p.isAlive() || s.foe != null || s.foeP != null) {
			s.ritual = 0;
			m.setShiftKeyDown(false);
			return false;
		}
		double dx = p.getX() - m.getX(), dz = p.getZ() - m.getZ();
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		m.setYRot(yaw);
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
		s.ritual--;
		if (s.ritual % 4 == 0) m.setShiftKeyDown(!m.isShiftKeyDown());
		if (s.ritual <= 0) {
			m.setShiftKeyDown(false);
			if (s.lead) formAlliance(r, recOf(s.ritualWith));
		}
		return true;
	}

	/** Nowy członek sojuszu: sojusznik prowadzi go dookoła muru i bazy, a potem pod bramę. */
	private static void startTour(Rec joiner, String clan) {
		Rec base = clanBaseRec(clan, joiner);
		State js = STATES.get(joiner.nick);
		if (base == null || js == null || js.entity == null || js.entity.isRemoved()) return;
		String escort = null;
		for (Rec x : ROSTER) {
			if (x == joiner || !clan.equals(x.clan)) continue;
			State xs = STATES.get(x.nick);
			if (xs != null && xs.entity != null && !xs.entity.isRemoved() && xs.entity.isAlive()) {
				escort = x.nick;
				break;
			}
		}
		int half = (base.cwP > 0 ? base.cwP : base.wallP > 0 ? base.wallP : 15) / 2 + 3;
		double cx = base.hx, cz = base.hz;
		js.tourPts = new double[] {cx, cz - half - 1, cx + half, cz - half, cx + half, cz + half, cx - half, cz + half, cx - half, cz - half, cx, cz - half - 1};
		js.tourIdx = 0;
		js.tourDwell = 0;
		js.tourT = 2400;
		js.foe = null;
		js.foeP = null;
		if (escort != null) {
			State es = STATES.get(escort);
			es.escort = joiner.nick;
			es.tourT = 2400;
			if (SRV != null) BotChat.say(SRV, escort, "Witaj w sojuszu, " + joiner.nick + "! Chodź, pokażę ci nasz mur i bazę.");
		}
	}

	/** Jeden krok w stronę punktu (z omijaniem przeszkód); true, gdy bot jest już na miejscu. */
	private static boolean stepToward(ServerLevel level, Mannequin m, double tx, double tz, double speed) {
		double dx = tx - m.getX(), dz = tz - m.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d < 0.8) return true;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		m.setYRot(yaw);
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
		double step = Math.min(speed, d);
		double nx = m.getX() + dx / d * step, nz = m.getZ() + dz / d * step;
		if (!level.hasChunkAt(new BlockPos((int) Math.floor(nx), 64, (int) Math.floor(nz))) || isWaterAt(level, nx, nz)) return false;
		double wy = walkY(level, m.getY(), (int) Math.floor(nx), (int) Math.floor(nz));
		if (Double.isNaN(wy) || !free(level, m, nx, wy, nz)) {
			if (!clearObstacle(level, m, nx, nz)) { // nie do wycięcia: przesuwa się bokiem
				double sx = m.getX() - dz / d * 0.1, sz = m.getZ() + dx / d * 0.1;
				double sy = walkY(level, m.getY(), (int) Math.floor(sx), (int) Math.floor(sz));
				if (!Double.isNaN(sy) && free(level, m, sx, sy, sz)) m.setPos(sx, sy, sz);
			}
			return false;
		}
		m.setPos(nx, wy, nz);
		return false;
	}

	/** Nowy członek sojuszu obchodzi mur i bazę, w każdym rogu chwilę stoi i się rozgląda. */
	private static boolean tourStep(ServerLevel level, Rec r, State s) {
		Mannequin m = s.entity;
		int n = s.tourPts.length / 2;
		if (--s.tourT <= 0 || s.tourIdx >= n) {
			boolean finished = s.tourIdx >= n;
			s.tourPts = null;
			if (finished && SRV != null) BotChat.say(SRV, r.nick, "Fajna baza, dzięki za oprowadzenie!");
			return false;
		}
		double tx = s.tourPts[2 * s.tourIdx], tz = s.tourPts[2 * s.tourIdx + 1];
		if (s.tourIdx == 0 && s.tourDwell == 0 && (tx - m.getX()) * (tx - m.getX()) + (tz - m.getZ()) * (tz - m.getZ()) > 80 * 80) {
			int gy = BotBuilder.ground(level, (int) Math.floor(tx), (int) Math.floor(tz)); // za daleko: pojawia się pod bramą
			level.getChunk((int) Math.floor(tx) >> 4, (int) Math.floor(tz) >> 4);
			m.setPos(tx, gy, tz);
			s.tx = tx;
			s.tz = tz;
		}
		if (s.tourDwell > 0) {
			s.tourDwell--;
			if (s.tourDwell % 10 == 0) {
				float yaw = (float) (RNG.nextDouble() * 360 - 180);
				m.setYRot(yaw);
				m.setYHeadRot(yaw);
				m.setYBodyRot(yaw);
			}
			return true;
		}
		if (stepToward(level, m, tx, tz, 0.13)) {
			s.tourDwell = 40;
			s.tourIdx++;
		}
		return true;
	}

	/** Sojusznik idzie obok nowego członka podczas oprowadzania. */
	private static boolean escortStep(ServerLevel level, Rec r, State s) {
		State ns = STATES.get(s.escort);
		Mannequin m = s.entity;
		if (ns == null || ns.tourPts == null || ns.entity == null || ns.entity.isRemoved() || --s.tourT <= 0) {
			s.escort = null;
			return false;
		}
		Mannequin nm = ns.entity;
		double dx = nm.getX() - m.getX(), dz = nm.getZ() - m.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d > 40) {
			m.setPos(nm.getX() + 2, nm.getY(), nm.getZ() + 2);
		} else if (d > 3.5) {
			stepToward(level, m, nm.getX(), nm.getZ(), 0.14);
		} else {
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			m.setYRot(yaw);
			m.setYHeadRot(yaw);
		}
		return true;
	}

	private static void formAlliance(Rec a, Rec b) {
		if (b == null || (a.clan != null && b.clan != null)) return;
		boolean aHad = a.clan != null, bHad = b.clan != null;
		String clan = aHad ? a.clan : bHad ? b.clan : newClanName();
		a.clan = clan;
		b.clan = clan;
		if (!aHad && !bHad) { // pierwsza osoba zbiera i buduje, druga ją chroni
			boolean aBuilds = RNG.nextBoolean();
			a.role = aBuilds ? "B" : "G";
			b.role = aBuilds ? "G" : "B";
			a.buddy = aBuilds ? null : b.nick;
			b.buddy = aBuilds ? a.nick : null;
		} else {
			Rec joiner = aHad ? b : a;
			Rec lonely = null;
			for (Rec x : ROSTER) {
				if (clan.equals(x.clan) && "B".equals(x.role) && x != joiner) {
					boolean has = false;
					for (Rec y : ROSTER) if ("G".equals(y.role) && x.nick.equals(y.buddy)) has = true;
					if (!has) lonely = x;
				}
			}
			if (lonely != null) {
				joiner.role = "G";
				joiner.buddy = lonely.nick;
			} else {
				joiner.role = "B";
				joiner.buddy = null;
			}
			startTour(joiner, clan);
		}
		save();
		BotChat.event(SRV, "ally", a.nick);
		if (SRV != null) {
			boolean fresh = !aHad && !bHad;
			SRV.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal(fresh
					? "🤝 Bot " + a.nick + " założył gildię " + clan + " z botem " + b.nick + "!"
					: "🤝 Bot " + (aHad ? b.nick : a.nick) + " dołączył do gildii " + clan + " (bot " + (aHad ? a.nick : b.nick) + ")!")
					.withStyle(net.minecraft.ChatFormatting.AQUA), false);
		}
	}

	/** Noc według czasu gry (przybliżenie: doba trwa 24000 tików, noc to 13000-23000). */
	private static boolean isNightTime(ServerLevel level) {
		long t = level.getGameTime() % 24000L;
		return (t >= 13000L && t < 23000L) || level.isRaining(); // w nocy i w deszczu chowają się w domu
	}

	/** Wyprawa /rtp: odliczanie 5 s, potem teleport w losowe miejsce 1500x1500 (albo z powrotem do domu). */
	private static void doRtp(ServerLevel level, Rec r, State s) {
		Mannequin m = s.entity;
		if (s.rtpBack) {
			s.rtpBack = false;
			if (r.hasHome) {
				level.getChunk((int) r.hx >> 4, (int) r.hz >> 4);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) r.hx, (int) r.hz);
				m.setPos(r.hx, y, r.hz);
				s.tx = r.hx;
				s.tz = r.hz;
			}
			return;
		}
		for (int tries = 0; tries < 20; tries++) {
			int x = RNG.nextInt(HALF * 2 + 1) - HALF, z = RNG.nextInt(HALF * 2 + 1) - HALF;
			if (Math.abs(x) < 40 && Math.abs(z) < 50) continue;
			level.getChunk(x >> 4, z >> 4);
			if (isWaterAt(level, x + 0.5, z + 0.5)) continue;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			m.setPos(x + 0.5, y, z + 0.5);
			s.tx = x + 0.5;
			s.tz = z + 0.5;
			s.awayUntil = level.getServer().getTickCount() + 3600 + RNG.nextInt(2400); // 3-5 minut zwiedzania
			return;
		}
	}

	private static void eatIfHungry(Rec r, Mannequin m) {
		float miss = m.getMaxHealth() - m.getHealth();
		if (miss < 6f) return;
		SimpleContainer c = inv(r.nick);
		Item[] food = {Items.GOLDEN_CARROT, Items.COOKED_BEEF, Items.BREAD, Items.APPLE};
		float[] heal = {6f, 8f, 5f, 4f};
		for (int i = 0; i < food.length; i++) {
			if (!takeOne(c, food[i]).isEmpty()) {
				m.heal(heal[i]);
				m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
				return;
			}
		}
	}

	/** Gdy brakuje drewna, bot szuka najbliższego drzewa i idzie do niego. */
	private static void seekWood(ServerLevel level, Rec r, State s) {
		SimpleContainer c = inv(r.nick);
		int logs = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack st = c.getItem(i);
			if (st.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock().defaultBlockState().is(BlockTags.LOGS)) logs += st.getCount();
		}
		if (logs >= 120 || RNG.nextInt(25) != 0) return;
		Mannequin m = s.entity;
		int bx = (int) Math.floor(m.getX()), bz = (int) Math.floor(m.getZ());
		int by = (int) Math.floor(m.getY());
		double best = 1e9;
		BlockPos bp = null;
		for (int dx = -14; dx <= 14; dx += 2) {
			for (int dz = -14; dz <= 14; dz += 2) {
				for (int dy = -2; dy <= 10; dy++) {
					BlockPos p = new BlockPos(bx + dx, by + dy, bz + dz);
					if (level.getBlockState(p).is(BlockTags.LOGS)) {
						double d = dx * dx + dz * dz;
						if (d < best) { best = d; bp = p; }
					}
				}
			}
		}
		if (bp != null) {
			s.tx = bp.getX() + 0.5;
			s.tz = bp.getZ() + 0.5;
			s.wait = 0;
		}
	}

	private static boolean inSpawnArea(double x, double z) {
		return x >= -14 && x <= 15 && z >= -14 && z <= 15;
	}


	// ---------- pajęczyny, zamurowanie, lepszy sprzęt, żniwa ----------

	private static boolean inCobweb(ServerLevel level, Mannequin m) {
		BlockPos bp = m.blockPosition();
		return level.getBlockState(bp).is(net.minecraft.world.level.block.Blocks.COBWEB) || level.getBlockState(bp.above()).is(net.minecraft.world.level.block.Blocks.COBWEB);
	}

	private static boolean passable(ServerLevel level, BlockPos p) {
		BlockState st = level.getBlockState(p);
		return st.isAir() || st.getCollisionShape(level, p).isEmpty();
	}

	/** Bot rozwala pajęczynę, w której utknął, i bloki, którymi ktoś go zamurował (cztery strony na wysokości stóp i głowy). */
	private static void unstick(ServerLevel level, Mannequin m) {
		BlockPos bp = m.blockPosition();
		if (inCobweb(level, m)) {
			if (RNG.nextInt(3) == 0) {
				for (BlockPos q : new BlockPos[] {bp, bp.above()}) if (level.getBlockState(q).is(net.minecraft.world.level.block.Blocks.COBWEB)) level.destroyBlock(q, false);
				m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			}
			return;
		}
		int closed = 0;
		for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			if (!passable(level, bp.relative(d)) && !passable(level, bp.above().relative(d))) closed++;
		}
		if (closed == 4 && RNG.nextInt(2) == 0) {
			net.minecraft.core.Direction d = net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom());
			BlockPos q = RNG.nextBoolean() ? bp.relative(d) : bp.above().relative(d);
			BlockState st = level.getBlockState(q);
			if (!st.isAir() && st.getDestroySpeed(level, q) >= 0) {
				level.destroyBlock(q, true);
				m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			}
		}
	}

	private static int tier(String p) {
		if (p.contains("anarch")) return 7;
		if (p.contains("netherite")) return 6;
		if (p.contains("diamond")) return 5;
		if (p.contains("iron")) return 4;
		if (p.contains("chainmail")) return 3;
		if (p.contains("golden") || p.contains("gold") || p.contains("stone")) return 2;
		return 1;
	}

	/** 0 miecz, 1 hełm, 2 napierśnik, 3 spodnie, 4 buty, 5 kilof, -1 inne. */
	private static int gearKind(String p) {
		if (p.endsWith("_sword")) return 0;
		if (p.endsWith("_helmet") || p.endsWith("_helm")) return 1;
		if (p.endsWith("_chestplate") || p.endsWith("_klata")) return 2;
		if (p.endsWith("_leggings") || p.endsWith("_spodnie")) return 3;
		if (p.endsWith("_boots") || p.endsWith("_buty")) return 4;
		if (p.endsWith("_pickaxe")) return 5;
		return -1;
	}

	private static String pathOf(ItemStack st) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
	}

	private static final net.minecraft.world.entity.EquipmentSlot[] GEAR_SLOTS = {net.minecraft.world.entity.EquipmentSlot.MAINHAND,
			net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
			net.minecraft.world.entity.EquipmentSlot.FEET};

	/** Najlepsza sztuka danego rodzaju, jaką bot ma założoną albo w ekwipunku (0, gdy brak). */
	private static int bestTier(Mannequin m, SimpleContainer c, int kind) {
		int best = 0;
		if (kind < 5) {
			ItemStack worn = m.getItemBySlot(GEAR_SLOTS[kind]);
			if (!worn.isEmpty() && gearKind(pathOf(worn)) == kind) best = tier(pathOf(worn));
		}
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack st = c.getItem(i);
			if (!st.isEmpty() && gearKind(pathOf(st)) == kind) best = Math.max(best, tier(pathOf(st)));
		}
		return best;
	}

	/** Zakłada lepszy miecz i zbroję z ekwipunku zamiast gorszych (stare wracają do plecaka). */
	private static void upgradeGear(Mannequin m, Rec r) {
		SimpleContainer c = inv(r.nick);
		for (int kind = 0; kind < 5; kind++) {
			ItemStack worn = m.getItemBySlot(GEAR_SLOTS[kind]);
			int wt = !worn.isEmpty() && gearKind(pathOf(worn)) == kind ? tier(pathOf(worn)) : 0;
			int bi = -1, bt = wt;
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack st = c.getItem(i);
				if (!st.isEmpty() && gearKind(pathOf(st)) == kind && tier(pathOf(st)) > bt) {
					bt = tier(pathOf(st));
					bi = i;
				}
			}
			if (bi >= 0) {
				ItemStack take = c.getItem(bi).copyWithCount(1);
				c.getItem(bi).shrink(1);
				if (c.getItem(bi).isEmpty()) c.setItem(bi, ItemStack.EMPTY);
				if (!worn.isEmpty()) c.addItem(worn.copy());
				m.setItemSlot(GEAR_SLOTS[kind], take);
				c.setChanged();
			}
		}
	}

	private static boolean wantedItem(Mannequin m, SimpleContainer c, ItemStack st) {
		String p = pathOf(st);
		if (p.startsWith("klucz_") || p.equals("golden_apple") || p.equals("enchanted_golden_apple") || p.equals("diamond") || p.equals("netherite_ingot")) return true;
		int k = gearKind(p);
		return k >= 0 && tier(p) > bestTier(m, c, k);
	}

	/** Bot zauważa w pobliżu (12 bloków) lepszy sprzęt, klucze albo cenne rzeczy i podchodzi je podnieść. */
	private static boolean lootScan(ServerLevel level, Rec r, Mannequin m) {
		SimpleContainer c = inv(r.nick);
		ItemEntity best = null;
		double bd = Double.MAX_VALUE;
		for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, m.getBoundingBox().inflate(12, 4, 12))) {
			if (ie.isRemoved() || PLAYER_ITEMS.contains(ie.getUUID()) || r.nick.equals(OWN_DROPS.get(ie.getUUID())) || ie.getItem().isEmpty()) continue;
			if (!wantedItem(m, c, ie.getItem())) continue;
			double d = ie.distanceToSqr(m);
			if (d < bd) { bd = d; best = ie; }
		}
		if (best == null) return false;
		goTo(r.nick, best.getX(), best.getZ());
		return true;
	}

	/** Dojrzałe uprawy w pobliżu: bot je zbiera i od razu sadzi od nowa. */
	private static boolean harvest(ServerLevel level, Rec r, Mannequin m) {
		BlockPos bp = m.blockPosition();
		BlockPos best = null;
		double bd = Double.MAX_VALUE;
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				for (int dy = -2; dy <= 2; dy++) {
					BlockPos q = bp.offset(dx, dy, dz);
					BlockState st = level.getBlockState(q);
					if (st.getBlock() instanceof net.minecraft.world.level.block.CropBlock cb && cb.isMaxAge(st)) {
						double d = q.distSqr(bp);
						if (d < bd) { bd = d; best = q; }
					}
				}
			}
		}
		if (best == null) return false;
		if (bd <= 6.25) {
			BlockState st = level.getBlockState(best);
			level.destroyBlock(best, true);
			level.setBlock(best, st.getBlock().defaultBlockState(), 3); // sadzi od nowa
			m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		} else {
			goTo(r.nick, best.getX() + 0.5, best.getZ() + 0.5);
		}
		return true;
	}

	/** W wojnie bot z kamieniem czasem zamurowuje wroga: bloki po czterech stronach na wysokości stóp i głowy (wróg je rozwali). */
	private static void wallIn(ServerLevel level, Rec r, net.minecraft.world.entity.LivingEntity f) {
		if (RNG.nextInt(120) != 0) return;
		SimpleContainer c = inv(r.nick);
		int have = 0;
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(Items.COBBLESTONE)) have += c.getItem(i).getCount();
		if (have < 16) return;
		BlockPos fp = f.blockPosition();
		int used = 0;
		for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			for (BlockPos q : new BlockPos[] {fp.relative(d), fp.above().relative(d)}) {
				if (level.getBlockState(q).isAir()) {
					level.setBlock(q, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(), 3);
					used++;
				}
			}
		}
		for (int i = 0; i < c.getContainerSize() && used > 0; i++) {
			ItemStack st = c.getItem(i);
			if (st.is(Items.COBBLESTONE)) {
				int t = Math.min(used, st.getCount());
				st.shrink(t);
				used -= t;
			}
		}
	}


	// ---------- role w gildii: przywódca, budowniczy, strażnicy bramy, zbieracze, farmerzy, hutnicy ----------

	private static final String[] ROLE_ORDER = {"L", "B", "G", "G", "Z", "F", "H", "Z", "F", "H"};

	/** Przydziela role w każdej gildii według kolejności członków: przywódca (1), budowniczy (1), 2 strażników, potem zbieracze, farmerzy i hutnicy. */
	private static void assignRoles() {
		Map<String, List<Rec>> by = new java.util.LinkedHashMap<>();
		for (Rec r : ROSTER) if (r.clan != null && r.partner == null) by.computeIfAbsent(r.clan, k -> new ArrayList<>()).add(r);
		boolean changed = false;
		for (List<Rec> l : by.values()) {
			for (int i = 0; i < l.size(); i++) {
				Rec r = l.get(i);
				String want = i < ROLE_ORDER.length ? ROLE_ORDER[i] : "Z";
				if (l.size() == 2) want = i == 0 ? "L" : "B";
				if (!want.equals(r.role)) {
					r.role = want;
					r.buddy = null;
					changed = true;
				}
			}
		}
		if (changed) save();
	}

	/** Pozycja wejścia do muru bazy (brama od północy), tuż wewnątrz muru. */
	private static double[] gatePos(String nick) {
		double[] cb = clanBaseHome(nick);
		int[] cw = clanWallInfo(nick);
		if (cb == null || cw == null || cw[0] < 5) return null;
		return new double[] {Math.floor(cb[0]) + 0.5, Math.floor(cb[1]) - cw[0] / 2 + 3};
	}

	/** Strażnicy stoją przy bramie bazy, przywódca zostaje w bazie. Zwraca true, gdy bot ma w tym ticku robić tylko to. */
	private static boolean guildStep(ServerLevel level, Rec r, State s) {
		if (r.clan == null || r.partner != null || s.foe != null || s.foeP != null || s.mine != null || BotBuilder.busy(r.nick) || s.ritual > 0 || s.tourPts != null) return false;
		if ("G".equals(r.role)) {
			double[] g = gatePos(r.nick);
			if (g == null) return false;
			double dx = g[0] - s.entity.getX(), dz = g[1] - s.entity.getZ();
			if (dx * dx + dz * dz > 36) {
				s.tx = g[0];
				s.tz = g[1];
				s.wait = 0;
				return false; // idzie do bramy zwykłym ruchem
			}
			pace(level, s.entity, s, g[0], g[1], 2.5); // przy bramie stoi i się rozgląda
			return true;
		}
		if ("L".equals(r.role)) {
			double[] cb = clanBaseHome(r.nick);
			if (cb != null) {
				double dx = cb[0] - s.entity.getX(), dz = cb[1] - s.entity.getZ();
				if (dx * dx + dz * dz > 30 * 30) {
					s.tx = cb[0];
					s.tz = cb[1];
					s.wait = 0;
				}
			}
		}
		return false;
	}

	private static int carried(SimpleContainer c) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack st = c.getItem(i);
			if (st.is(net.minecraft.tags.ItemTags.LOGS) || st.is(Items.COBBLESTONE)) n += st.getCount();
		}
		return n;
	}

	/** Zadania ról co ok. 2 s, gdy bot nic innego nie robi. Zwraca true, gdy bot zajął się rolą. */
	private static boolean roleAct(ServerLevel level, Rec r, State s, Mannequin m) {
		if (r.clan == null || r.partner != null || r.role == null) return false;
		double[] cb = clanBaseHome(r.nick);
		int[] cw = clanWallInfo(r.nick);
		if (cb == null || cw == null) return false;
		switch (r.role) {
			case "Z" -> { // zbieracz: kamień i drewno oddaje budowniczemu (wyrzuca przedmioty, jak klawiszem Q)
				SimpleContainer c = inv(r.nick);
				if (carried(c) < 300) return false;
				Mannequin bm = null;
				for (Rec o : ROSTER) {
					if (!r.clan.equals(o.clan) || !"B".equals(o.role) || o.partner != null) continue;
					State bs = STATES.get(o.nick);
					if (bs != null && bs.entity != null && bs.entity.isAlive() && !bs.entity.isRemoved()) bm = bs.entity;
				}
				if (bm == null) return false;
				double d = bm.distanceToSqr(m);
				if (d > 9) {
					s.tx = bm.getX();
					s.tz = bm.getZ();
					s.wait = 0;
					return true;
				}
				for (int i = 0; i < c.getContainerSize(); i++) {
					ItemStack st = c.getItem(i);
					if (st.is(net.minecraft.tags.ItemTags.LOGS) || st.is(Items.COBBLESTONE)) {
						int give = Math.min(st.getCount(), 64);
						ItemStack out = st.copyWithCount(give);
						st.shrink(give);
						if (st.isEmpty()) c.setItem(i, ItemStack.EMPTY);
						ItemEntity ie = new ItemEntity(level, m.getX(), m.getY() + 1.0, m.getZ(), out);
						ie.setDeltaMovement((bm.getX() - m.getX()) * 0.12, 0.15, (bm.getZ() - m.getZ()) * 0.12);
						OWN_DROPS.put(ie.getUUID(), r.nick); // zbieracz nie podnosi z powrotem tego, co oddał
						level.addFreshEntity(ie);
						m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
						c.setChanged();
						break;
					}
				}
				return true;
			}
			case "F" -> { // farmer: plony w bazie, zwierzęta na mięso (piec w warsztacie przepala je sam)
				BlockPos bp = BlockPos.containing(cb[0], m.getY(), cb[1]);
				BlockPos crop = null;
				double bd = Double.MAX_VALUE;
				int rad = Math.max(8, cw[0] / 2);
				for (int dx = -rad; dx <= rad; dx += 1) {
					for (int dz = -rad; dz <= rad; dz += 1) {
						for (int dy = -2; dy <= 2; dy++) {
							BlockPos q = bp.offset(dx, dy, dz);
							BlockState st = level.getBlockState(q);
							if (st.getBlock() instanceof net.minecraft.world.level.block.CropBlock cb2 && cb2.isMaxAge(st)) {
								double d = q.distSqr(m.blockPosition());
								if (d < bd) { bd = d; crop = q; }
							}
						}
					}
				}
				if (crop != null) {
					if (bd <= 6.25) {
						BlockState st = level.getBlockState(crop);
						level.destroyBlock(crop, true);
						level.setBlock(crop, st.getBlock().defaultBlockState(), 3); // sadzi od nowa
						m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
					} else {
						s.tx = crop.getX() + 0.5;
						s.tz = crop.getZ() + 0.5;
						s.wait = 0;
					}
					return true;
				}
				net.minecraft.world.entity.animal.Animal prey = null;
				double pd = Double.MAX_VALUE;
				for (net.minecraft.world.entity.animal.Animal a : level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, m.getBoundingBox().inflate(24, 6, 24))) {
					net.minecraft.world.entity.EntityType<?> ty = a.getType();
					if (ty != net.minecraft.world.entity.EntityType.COW && ty != net.minecraft.world.entity.EntityType.PIG
							&& ty != net.minecraft.world.entity.EntityType.SHEEP && ty != net.minecraft.world.entity.EntityType.CHICKEN) continue;
					double d = a.distanceToSqr(m);
					if (d < pd) { pd = d; prey = a; }
				}
				if (prey != null) {
					if (pd <= 6.25) {
						prey.hurtServer(level, level.damageSources().mobAttack(m), 8f);
						m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
					} else {
						s.tx = prey.getX();
						s.tz = prey.getZ();
						s.wait = 0;
					}
					return true;
				}
				return false;
			}
			case "H" -> { // hutnik: najpierw kopie własny szyb 3x3 z drabinką, potem schodzi nim po rudy
				if (r.shaftDone) return false;
				if (r.shaftTop == 0) {
					int half = cw[0] / 2;
					r.shaftX = (int) Math.floor(cb[0]) + half - 3;
					r.shaftZ = (int) Math.floor(cb[1]) + half - 3;
					r.shaftTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, r.shaftX, r.shaftZ);
					r.shaftDepth = 0;
				}
				double dx = r.shaftX + 0.5 - m.getX(), dz = r.shaftZ + 0.5 - m.getZ();
				if (r.shaftDepth == 0 && dx * dx + dz * dz > 25) {
					s.tx = r.shaftX + 0.5;
					s.tz = r.shaftZ + 0.5;
					s.wait = 0;
					return true;
				}
				for (int k = 0; k < 2; k++) { // dwie warstwy co 2 s
					int y = r.shaftTop - 1 - r.shaftDepth;
					if (y <= -50) {
						r.shaftDone = true;
						r.shaftDepth = r.shaftTop + 50; // dno na y = -50
						save();
						BotChat.say(level.getServer(), r.nick, "kopalnia gotowa, schodzę ⛏️");
						return true;
					}
					boolean ok = true;
					for (int ox = -1; ox <= 1 && ok; ox++) {
						for (int oz = -1; oz <= 1; oz++) {
							BlockPos q = new BlockPos(r.shaftX + ox, y, r.shaftZ + oz);
							BlockState st = level.getBlockState(q);
							if (!level.getFluidState(q).isEmpty() || st.getDestroySpeed(level, q) < 0) { ok = false; break; }
							if (!st.isAir()) level.destroyBlock(q, true);
						}
					}
					if (!ok) { // lawa/woda/niezniszczalne: kończy szyb na tej głębokości
						r.shaftDone = true;
						r.shaftDepth = r.shaftTop - y;
						save();
						return true;
					}
					BlockPos lad = new BlockPos(r.shaftX, y, r.shaftZ - 1);
					if (!passable(level, lad.north())) {
						level.setBlock(lad, net.minecraft.world.level.block.Blocks.LADDER.defaultBlockState()
								.setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.SOUTH), 3);
					}
					r.shaftDepth++;
					m.setPos(r.shaftX + 0.5, y, r.shaftZ + 0.5); // schodzi razem z kopaniem
					s.tx = m.getX();
					s.tz = m.getZ();
				}
				return true;
			}
			default -> { }
		}
		return false;
	}

	private static final Map<String, Long> BUFF = new HashMap<>();

	/** Przed walką wysokopoziomowy bot "pije miksturę": siła i szybkość na 30 s (raz na minutę). */
	private static void buff(ServerLevel level, Rec r, Mannequin m) {
		if (r.level < 6) return;
		long now = level.getGameTime();
		Long last = BUFF.get(r.nick);
		if (last != null && now - last < 1200) return;
		BUFF.put(r.nick, now);
		EffectUtil.give(m, "strength", 600, 0);
		EffectUtil.give(m, "speed", 600, 0);
	}

	/** Mało życia i perła w ekwipunku: bot rzuca ją i ląduje przy domu (jeśli teren jest załadowany). */
	private static boolean pearlHome(ServerLevel level, Rec r, State s) {
		SimpleContainer c = inv(r.nick);
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack st = c.getItem(i);
			if (!st.is(Items.ENDER_PEARL)) continue;
			int hx = (int) Math.floor(r.hx), hz = (int) Math.floor(r.hz);
			if (!level.hasChunkAt(new BlockPos(hx, 64, hz))) return false;
			int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hx, hz);
			st.shrink(1);
			s.entity.setPos(r.hx, gy, r.hz);
			s.foe = null;
			s.foeP = null;
			s.retal = false;
			s.fleeT = 200;
			s.tx = r.hx;
			s.tz = r.hz;
			BotChat.say(level.getServer(), r.nick, "perła i spadam 😅");
			return true;
		}
		return false;
	}

	/** Poniżej 50% życia bot z obsydianem stawia dwa bloki między sobą a wrogiem (tarcza), czasem i tylko gdy jest tam wolne miejsce. */
	private static void obsidianShield(ServerLevel level, Rec r, Mannequin m, net.minecraft.world.entity.LivingEntity f) {
		if (m.getHealth() > m.getMaxHealth() * 0.5f || RNG.nextInt(40) != 0) return;
		SimpleContainer c = inv(r.nick);
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack st = c.getItem(i);
			if (!st.is(Items.OBSIDIAN) || st.getCount() < 2) continue;
			double dx = f.getX() - m.getX(), dz = f.getZ() - m.getZ();
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len < 1.5) return;
			BlockPos p = BlockPos.containing(m.getX() + dx / len * 1.2, m.getY(), m.getZ() + dz / len * 1.2);
			if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) {
				level.setBlock(p, net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState(), 3);
				level.setBlock(p.above(), net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState(), 3);
				st.shrink(2);
			}
			return;
		}
	}

	/** Czasem bot rzuca pod nogi wroga pajęczynę (jeśli ją ma), żeby go spowolnić. */
	private static void webTrap(ServerLevel level, Rec r, net.minecraft.world.entity.LivingEntity f, boolean war) {
		if (RNG.nextInt(war ? 8 : 60) != 0) return;
		SimpleContainer c = inv(r.nick);
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack st = c.getItem(i);
			if (!st.is(Items.COBWEB)) continue;
			BlockPos p = f.blockPosition();
			if (level.getBlockState(p).isAir()) {
				level.setBlock(p, net.minecraft.world.level.block.Blocks.COBWEB.defaultBlockState(), 3);
				st.shrink(1);
			}
			return;
		}
	}

	/** Walka bota z innym botem: podchodzi i bije co ok. 0,6 s; siła zależy od poziomu. Zwraca true, gdy walczy w tym ticku. */
	private static boolean fightStep(ServerLevel level, Rec r, State s) {
		Mannequin m = s.entity;
		if (s.foeP != null && r.partner != null && s.foeP.toString().equals(r.partner)) { // sojusznika gracza nie bijemy
			s.foeP = null;
			s.retal = false;
			return false;
		}
		if (m.getHealth() < 9f && r.hasHome && pearlHome(level, r, s)) return false; // perła endermana: teleport do domu
		buff(level, r, m);
		if (m.getHealth() < 6f) { // mało życia: ucieka do domu
			s.foe = null;
			s.foeP = null;
			s.retal = false;
			s.fleeT = 300;
			if (r.hasHome) { s.tx = r.hx; s.tz = r.hz; s.wait = 0; }
			return false;
		}
		net.minecraft.world.entity.LivingEntity f;
		boolean vsPlayer = s.foeP != null;
		if (vsPlayer) {
			f = level.getServer().getPlayerList().getPlayer(s.foeP);
			if (f == null || f.level() != level || !f.isAlive() || ((ServerPlayer) f).isCreative() || ((ServerPlayer) f).isSpectator()
					|| --s.fight <= 0 || (!s.retal && inSpawnArea(f.getX(), f.getZ())) || f.distanceToSqr(m) > (s.retal ? 60 * 60 : 30 * 30)) {
				s.foeP = null;
				s.retal = false;
				return false;
			}
		} else {
			State fs = STATES.get(s.foe);
			f = fs == null ? null : fs.entity;
			if (f == null || f.isRemoved() || !f.isAlive() || --s.fight <= 0 || inSpawnArea(m.getX(), m.getZ())
					|| inSpawnArea(f.getX(), f.getZ()) || inPond(f.getX(), f.getZ())) {
				s.foe = null;
				return false;
			}
		}
		if (inSpawnArea(m.getX(), m.getZ()) && vsPlayer && !s.retal) {
			s.foeP = null;
			return false;
		}
		boolean war = s.foe != null && atWar(r.clan, clanOf(s.foe));
		webTrap(level, r, f, war);
		if (war) wallIn(level, r, f);
		obsidianShield(level, r, m, f);
		double dx = f.getX() - m.getX(), dz = f.getZ() - m.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		// od czasu do czasu podskakują i sprintują
		if (s.jump > 0) s.jump--; else if (RNG.nextInt(40) == 0) s.jump = 10;
		if (s.sprintT > 0) s.sprintT--; else if (RNG.nextInt(60) == 0) s.sprintT = 30;
		boolean sprint = s.sprintT > 0;
		double jh = s.jump > 0 ? 0.6 * Math.sin(Math.PI * (10 - s.jump) / 10.0) : 0.0;
		boolean los = m.hasLineOfSight(f); // przez ściany nie biją
		if (d > 3.0 || !los) {
			double speed = (0.12 + 0.015 * r.level) * (sprint ? 1.7 : 1.0);
			double step = Math.max(0.03, Math.min(speed, d - 2.5));
			double nx = m.getX() + dx / d * step, nz = m.getZ() + dz / d * step;
			if (level.hasChunkAt(new BlockPos((int) Math.floor(nx), 64, (int) Math.floor(nz))) && !isWaterAt(level, nx, nz)) {
				double wy = walkY(level, m.getY(), (int) Math.floor(nx), (int) Math.floor(nz));
				if (Double.isNaN(wy) || !free(level, m, nx, wy, nz)) clearObstacle(level, m, nx, nz); // drzewo na drodze: wycina je zamiast wchodzić w nie
				else m.setPos(nx, wy + jh, nz);
			}
			m.setSprinting(sprint);
		} else {
			m.setSprinting(false);
			if (s.jump > 0) {
				double gy = walkY(level, m.getY(), (int) Math.floor(m.getX()), (int) Math.floor(m.getZ()));
				if (!Double.isNaN(gy)) m.setPos(m.getX(), gy + jh, m.getZ());
			}
			if (los && d > 0.01) { // w walce krąży wokół przeciwnika, nie stoi
				double sd = s.strafe ? 1 : -1;
				double sx = m.getX() + (-dz / d) * 0.05 * sd, sz = m.getZ() + (dx / d) * 0.05 * sd;
				double sy2 = walkY(level, m.getY(), (int) Math.floor(sx), (int) Math.floor(sz));
				if (!Double.isNaN(sy2) && !isWaterAt(level, sx, sz) && free(level, m, sx, sy2, sz)) m.setPos(sx, sy2 + jh, sz);
			}
			if (--s.cd <= 0) {
				s.cd = 12; // jak gracz z mieczem: zasięg 3 bloki, uderzenie co 0,6 s
				float dmg = vsPlayer ? (float) (1.0 + r.level * 0.35) : (float) (2.0 + r.level * 0.8);
				dmg = BotEvents.onHit(level, r.nick, m, f, dmg); // eventówkowa broń w ręku
				f.hurtServer(level, level.damageSources().mobAttack(m), dmg);
				s.strafe = !s.strafe; // po ciosie zmienia stronę, w którą krąży
				if (vsPlayer) Antylogout.tagPlayer((ServerPlayer) f);
				m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			}
		}
		m.setYRot(yaw);
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
		return true;
	}

	/** Czasem bot zaczepia najbliższego bota w pobliżu (poza spawnem i stawem) i zaczyna się bójka. */
	private static void maybeStartFight(ServerLevel level, Rec r, State s) {
		Mannequin m = s.entity;
		if (s.fleeT > 0 || s.rtpT > 0) return;
		if (s.foeP == null && s.foe == null && !inSpawnArea(m.getX(), m.getZ()) && !inPond(m.getX(), m.getZ())) { // bijące się boty nie rzucają walki, gdy ktoś podejdzie
			ServerPlayer near = null;
			double nd = 14 * 14;
			long now = level.getServer().getTickCount();
			for (ServerPlayer p : level.players()) {
				if (!p.isAlive() || p.isCreative() || p.isSpectator() || inSpawnArea(p.getX(), p.getZ())) continue;
				if (p.getUUID().toString().equals(r.partner)) continue; // sojusznik gracz
				if (r.clan != null && r.clan.equals(playerClan(p.getUUID().toString()))) continue; // gracz z tej samej gildii
				double d = p.distanceToSqr(m);
				if (d >= nd) continue;
				if (s.proposeTo != null) continue; // trwa propozycja sojuszu
				int dec = sightDecision(r.nick, p, now);
				if (dec == 0) continue; // ten bot ignoruje gracza
				if (dec == 2) { // chce sojuszu: podchodzi i kuca 5 razy
					if (r.partner == null && !"G".equals(r.role)) {
						s.proposeTo = p.getUUID();
						s.propT = 400;
						s.propToggles = 0;
						s.propTick = 0;
						s.wait = 0;
					}
					continue;
				}
				nd = d;
				near = p;
			}
			if (near != null) { // boty są agresywne wobec graczy
				s.foeP = near.getUUID();
				s.foe = null;
				s.fight = 600;
				return;
			}
		}
		if (s.foe != null || s.foeP != null || (RNG.nextInt(warA != null && r.clan != null ? 4 : 20) != 0) || inSpawnArea(m.getX(), m.getZ()) || inPond(m.getX(), m.getZ())) return;
		String best = null;
		double bd = 15 * 15;
		for (Map.Entry<String, State> e : STATES.entrySet()) {
			Mannequin o = e.getValue().entity;
			if (o == null || o == m || o.isRemoved() || !o.isAlive() || inSpawnArea(o.getX(), o.getZ())) continue;
			String mc = r.clan;
			if (mc != null && mc.equals(clanOf(e.getKey()))) continue; // sojusznik
			boolean war = atWar(mc, clanOf(e.getKey()));
			if (!war && !"G".equals(r.role) && meetDecision(r.nick, e.getKey(), level.getGameTime()) != 0) continue; // pokój albo gildia - nie atakują
			double dx = o.getX() - m.getX(), dz = o.getZ() - m.getZ();
			double d = dx * dx + dz * dz;
			if (war) d = d * 0.1; // w czasie wojny wróg z gildii wrogiej jest celem priorytetowym i widoczny z daleka
			if (d < bd) { bd = d; best = e.getKey(); }
		}
		if (best == null) {
			if (warA != null && r.clan != null && s.tourPts == null && !BotBuilder.busy(r.nick)) { // wojna: idą pod bazę wroga
				String enemy = r.clan.equals(warA) ? warB : r.clan.equals(warB) ? warA : null;
				Rec base = enemy == null ? null : clanBaseRec(enemy, null);
				if (base != null && base.hasHome && RNG.nextInt(6) == 0) {
					s.tx = base.hx + RNG.nextInt(7) - 3;
					s.tz = base.hz + RNG.nextInt(7) - 3;
				}
			}
			return;
		}
		s.foe = best;
		s.fight = 400;
		State other = STATES.get(best);
		if (other.foe == null) { // zaczepiony oddaje
			other.foe = r.nick;
			other.fight = 400;
		}
	}

	/** Bot, który chce sojuszu: podchodzi do gracza, kuca 5 razy szybko i czeka na odpowiedź (gracz kuca 5 razy = sojusz). */
	private static boolean proposeStep(ServerLevel level, Rec r, State s) {
		Mannequin m = s.entity;
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(s.proposeTo);
		if (p == null || !p.isAlive() || p.level() != level || r.partner != null || m.distanceToSqr(p) > 30 * 30 || --s.propT <= 0) {
			String key = r.nick + "|" + s.proposeTo;
			SIGHT.put(key, new long[] {0, level.getServer().getTickCount() + 6000}); // nie ponawia od razu
			s.proposeTo = null;
			m.setShiftKeyDown(false);
			return false;
		}
		double dx = p.getX() - m.getX(), dz = p.getZ() - m.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		m.setYRot(yaw);
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
		if (d > 3.5) {
			m.setShiftKeyDown(false);
			double step = Math.min(0.13, d - 3.0);
			double nx = m.getX() + dx / d * step, nz = m.getZ() + dz / d * step;
			if (level.hasChunkAt(new BlockPos((int) Math.floor(nx), 64, (int) Math.floor(nz))) && !isWaterAt(level, nx, nz)) {
				double wy = walkY(level, m.getY(), (int) Math.floor(nx), (int) Math.floor(nz));
				if (Double.isNaN(wy) || !free(level, m, nx, wy, nz)) clearObstacle(level, m, nx, nz);
				else m.setPos(nx, wy, nz);
			}
			return true;
		}
		if (s.propToggles < 10) { // 5 szybkich kucnięć
			if (s.propTick++ % 4 == 0) {
				m.setShiftKeyDown(!m.isShiftKeyDown());
				s.propToggles++;
			}
		} else {
			m.setShiftKeyDown(false); // czeka na odpowiedź gracza
		}
		return true;
	}

	/** Przeciwnik bota (gracz albo inny bot) albo null. */
	private static net.minecraft.world.entity.LivingEntity foeOf(ServerLevel level, State s) {
		if (s.foeP != null) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(s.foeP);
			return p != null && p.isAlive() ? p : null;
		}
		if (s.foe != null) {
			State fs = STATES.get(s.foe);
			return fs == null || fs.entity == null || fs.entity.isRemoved() || !fs.entity.isAlive() ? null : fs.entity;
		}
		return null;
	}

	static String nickOf(Entity e) {
		for (Map.Entry<String, State> en : STATES.entrySet()) if (en.getValue().entity == e) return en.getKey();
		return null;
	}

	/** Czy e jest sojusznikiem bota m (ten sam klan albo sojusznik gracz) - efekty obszarowe go omijają. */
	static boolean friendly(Mannequin m, Entity e) {
		String mn = nickOf(m);
		Rec mr = mn == null ? null : recOf(mn);
		if (mr == null) return false;
		if (e instanceof ServerPlayer p) return p.getUUID().toString().equals(mr.partner);
		String en = nickOf(e);
		return en != null && mr.clan != null && mr.clan.equals(clanOf(en));
	}

	/** Unieruchamia bota (gracza spowalnia efektem). */
	static void stun(net.minecraft.world.entity.LivingEntity e, int ticks) {
		String n = nickOf(e);
		if (n != null) {
			State st = STATES.get(n);
			if (st != null) st.stunT = Math.max(st.stunT, ticks);
		} else {
			EffectUtil.give(e, "slowness", ticks, 6);
		}
	}

	/** Odrzuca byt od punktu (gracza fizyką, bota przesunięciem). */
	static void pushAway(net.minecraft.world.entity.LivingEntity e, double ox, double oz, double dist) {
		double ax = e.getX() - ox, az = e.getZ() - oz;
		double len = Math.sqrt(ax * ax + az * az);
		double ux = len < 0.01 ? 1.0 : ax / len, uz = len < 0.01 ? 0.0 : az / len;
		if (e instanceof Mannequin && nickOf(e) != null && e.level() instanceof ServerLevel lv) {
			for (double k = dist; k >= 1; k -= 1) {
				double nx = e.getX() + ux * k, nz = e.getZ() + uz * k;
				if (!lv.hasChunkAt(new BlockPos((int) Math.floor(nx), 64, (int) Math.floor(nz))) || isWaterAt(lv, nx, nz)) continue;
				double wy = walkY(lv, e.getY(), (int) Math.floor(nx), (int) Math.floor(nz));
				if (!Double.isNaN(wy) && free(lv, (Mannequin) e, nx, wy, nz)) {
					e.setPos(nx, wy, nz);
					return;
				}
			}
		} else {
			e.setDeltaMovement(ux * Math.min(2.5, dist * 0.3), 0.5, uz * Math.min(2.5, dist * 0.3));
			e.hurtMarked = true;
		}
	}

	/** Powolne kręcenie się w promieniu radius wokół punktu (podczas budowy, w nocy przy domu), żeby bot nigdy nie stał nieruchomo. */
	private static void pace(ServerLevel level, Mannequin m, State s, double cx, double cz, double radius) {
		double dx = s.tx - m.getX(), dz = s.tz - m.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		boolean far = (s.tx - cx) * (s.tx - cx) + (s.tz - cz) * (s.tz - cz) > radius * radius;
		if (dist < 0.3 || far) {
			double ang = RNG.nextDouble() * Math.PI * 2, len = 1 + RNG.nextDouble() * (radius - 1);
			s.tx = cx + Math.cos(ang) * len;
			s.tz = cz + Math.sin(ang) * len;
			return;
		}
		double step = Math.min(0.08, dist);
		double nx = m.getX() + dx / dist * step, nz = m.getZ() + dz / dist * step;
		if (!level.hasChunkAt(new BlockPos((int) Math.floor(nx), 64, (int) Math.floor(nz))) || isWaterAt(level, nx, nz)) {
			s.tx = m.getX();
			s.tz = m.getZ();
			return;
		}
		double wy = walkY(level, m.getY(), (int) Math.floor(nx), (int) Math.floor(nz));
		if (Double.isNaN(wy) || !free(level, m, nx, wy, nz)) {
			s.tx = m.getX();
			s.tz = m.getZ();
			return;
		}
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		m.setPos(nx, wy, nz);
		m.setYRot(yaw);
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
	}

	/** Czy ciało bota zmieści się w nowym miejscu bez wchodzenia w bloki (jak gracz, który nie przechodzi przez ściany). */
	private static boolean free(ServerLevel level, Mannequin m, double nx, double wy, double nz) {
		net.minecraft.world.phys.AABB b = m.getBoundingBox().move(nx - m.getX(), wy - m.getY(), nz - m.getZ()).deflate(0.001);
		return level.noCollision(m, b);
	}

	/** Wysokość podłoża w kolumnie x,z, na którą bot może wejść (stopień do 1 bloku w górę, spadek do 3 w dół); NaN, gdy coś blokuje drogę. */
	private static double walkY(ServerLevel level, double cy, int x, int z) {
		int top = (int) Math.floor(cy + 0.05);
		for (int y = top; y >= top - 3; y--) {
			BlockPos p = new BlockPos(x, y, z);
			net.minecraft.world.level.block.state.BlockState st = level.getBlockState(p);
			if (st.getCollisionShape(level, p).isEmpty() || st.is(net.minecraft.tags.BlockTags.LEAVES)) continue;
			BlockPos a = p.above(), b = a.above();
			if (!level.getBlockState(a).getCollisionShape(level, a).isEmpty() || !level.getBlockState(b).getCollisionShape(level, b).isEmpty()) return Double.NaN;
			return y + 1;
		}
		return Double.NaN;
	}

	/** Przeszkoda z kłód lub liści na drodze bota: niszczy najniższy taki blok (drop zbiera potem bot). Zwraca false, gdy to coś innego. */
	private static boolean clearObstacle(ServerLevel level, Mannequin m, double nx, double nz) {
		int x = (int) Math.floor(nx), z = (int) Math.floor(nz), y0 = (int) Math.floor(m.getY() + 0.05);
		for (int dy = 0; dy <= 2; dy++) {
			BlockPos p = new BlockPos(x, y0 + dy, z);
			net.minecraft.world.level.block.state.BlockState st = level.getBlockState(p);
			if (st.is(net.minecraft.tags.BlockTags.LOGS) || st.is(net.minecraft.tags.BlockTags.LEAVES)) {
				level.destroyBlock(p, true, m);
				m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
				return true;
			}
		}
		return false;
	}

	/** Przeszkoda, której nie da się wyciąć (skała, uskok): bot skręca w bok i wybiera nowy cel. */
	private static void sidestep(Mannequin m, State s, double dx, double dz) {
		double ang = Math.atan2(dz, dx) + (RNG.nextBoolean() ? 1 : -1) * Math.toRadians(60 + RNG.nextInt(60));
		double len = 6 + RNG.nextInt(8);
		s.tx = Math.max(-HALF + 1, Math.min(HALF - 1, m.getX() + Math.cos(ang) * len));
		s.tz = Math.max(-HALF + 1, Math.min(HALF - 1, m.getZ() + Math.sin(ang) * len));
		s.wait = 0;
	}

	private static void moveBot(ServerLevel level, Rec r, State s) {
		Mannequin m = s.entity;
		if (s.rtpT > 0) { // odliczanie przed teleportacją (jak /rtp u gracza)
			if (--s.rtpT == 0) doRtp(level, r, s);
			return;
		}
		if (s.awayUntil > 0 && level.getServer().getTickCount() >= s.awayUntil && s.foe == null && s.foeP == null) {
			s.awayUntil = 0;
			s.rtpBack = true; // wyprawa skończona: wraca na swoją posesję
			s.rtpT = 100;
			return;
		}
		if (isNightTime(level) && r.hasHome && r.partner == null && !"G".equals(r.role) && s.foe == null && s.foeP == null && s.mine == null
				&& !BotBuilder.busy(r.nick) && s.ritual <= 0 && s.tourPts == null && s.escort == null) { // w nocy wracają do domu i tam stoją
			double hx = r.hx - m.getX(), hz = r.hz - m.getZ();
			if (hx * hx + hz * hz < 16) { // przy domu się kręci, nie stoi
				pace(level, m, s, r.hx, r.hz, 6);
				return;
			}
			s.tx = r.hx;
			s.tz = r.hz;
			s.wait = 0;
		}
		if (s.ritual > 0 && ritualStep(level, r, s)) return;
		if ((s.foe != null || s.foeP != null) && fightStep(level, r, s)) return;
		if (s.proposeTo != null && proposeStep(level, r, s)) return;
		if (s.tourPts != null && tourStep(level, r, s)) return;
		if (s.escort != null && escortStep(level, r, s)) return;
		if (s.mine != null) { mineStep(level, r, s); return; }
		if (BotBuilder.busy(r.nick)) { // buduje, ale się kręci przy budowie
			double[] sl = BotBuilder.focus(r.nick);
			pace(level, m, s, sl == null ? m.getX() : sl[0], sl == null ? m.getZ() : sl[1], 3);
			return;
		}
		if (guildStep(level, r, s)) return;
		if (r.partner != null) { // bot sprzymierzony z graczem chodzi za nim
			ServerPlayer ow = level.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(r.partner));
			if (ow != null && ow.level() == level && ow.isAlive()) {
				double ddx = ow.getX() - m.getX(), ddz = ow.getZ() - m.getZ();
				double dd = ddx * ddx + ddz * ddz;
				if (dd > 60 * 60) { // za daleko: dołącza do gracza
					m.setPos(ow.getX() + RNG.nextInt(5) - 2, ow.getY(), ow.getZ() + RNG.nextInt(5) - 2);
					s.tx = m.getX();
					s.tz = m.getZ();
				} else if (dd > 36 || (s.tx - ow.getX()) * (s.tx - ow.getX()) + (s.tz - ow.getZ()) * (s.tz - ow.getZ()) > 64) {
					s.tx = ow.getX() + RNG.nextInt(7) - 3;
					s.tz = ow.getZ() + RNG.nextInt(7) - 3;
					s.wait = 0;
				}
			}
		}
		if ("G".equals(r.role) && r.buddy != null) { // strażnik trzyma się przy podopiecznym
			State bs = STATES.get(r.buddy);
			Mannequin bd = bs == null ? null : bs.entity;
			if (bd != null && !bd.isRemoved() && bd.isAlive()) {
				double ddx = s.tx - bd.getX(), ddz = s.tz - bd.getZ();
				if (ddx * ddx + ddz * ddz > 36) {
					s.tx = bd.getX() + RNG.nextInt(7) - 3;
					s.tz = bd.getZ() + RNG.nextInt(7) - 3;
					s.wait = 0;
				}
			}
		}
		if (s.wait > 0) { s.wait--; return; }
		double dx = s.tx - m.getX(), dz = s.tz - m.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist < 0.4) {
			// nowy cel; wyższy poziom = dalsze i bardziej zdecydowane wędrówki
			double range = 8 + r.level * 4;
			double ang = RNG.nextDouble() * Math.PI * 2;
			double nx = m.getX() + Math.cos(ang) * range * RNG.nextDouble();
			double nz = m.getZ() + Math.sin(ang) * range * RNG.nextDouble();
			s.tx = Math.max(-HALF + 1, Math.min(HALF - 1, nx));
			s.tz = Math.max(-HALF + 1, Math.min(HALF - 1, nz));
			if (r.clan != null && RNG.nextInt(8) == 0) { // trzymają się razem z sojusznikami
				for (Map.Entry<String, State> e : STATES.entrySet()) {
					Mannequin a = e.getValue().entity;
					if (a != null && a != m && !a.isRemoved() && r.clan.equals(clanOf(e.getKey()))) {
						s.tx = a.getX() + RNG.nextInt(17) - 8;
						s.tz = a.getZ() + RNG.nextInt(17) - 8;
						break;
					}
				}
			}
			if (RNG.nextInt(25) == 0) { // czasem idą popływać w stawie
				s.tx = POND_X1 + 1 + RNG.nextDouble() * (POND_X2 - POND_X1 - 2);
				s.tz = POND_Z1 + 1 + RNG.nextDouble() * (POND_Z2 - POND_Z1 - 2);
			}
			s.wait = RNG.nextInt(8); // prawie bez postojów: boty nie stoją AFK
			return;
		}
		if (s.sprintT > 0) s.sprintT--; else if (RNG.nextInt(150) == 0) s.sprintT = 40 + RNG.nextInt(60); // czasem biegną sprintem jak gracze SMP
		double speed = (0.13 + 0.018 * r.level) * (s.sprintT > 0 ? 1.6 : 1.0); // bloków na tick
		double step = Math.min(speed, dist);
		double nx = m.getX() + dx / dist * step, nz = m.getZ() + dz / dist * step;
		nx = Math.max(-HALF + 0.5, Math.min(HALF - 0.5, nx));
		nz = Math.max(-HALF + 0.5, Math.min(HALF - 0.5, nz));
		if (!level.hasChunkAt(new net.minecraft.core.BlockPos((int) Math.floor(nx), 64, (int) Math.floor(nz)))) {
			s.tx = m.getX(); s.tz = m.getZ(); // nie wchodzimy w niezaładowany teren
			return;
		}
		int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(nx), (int) Math.floor(nz));
		float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
		boolean water = level.getFluidState(new BlockPos((int) Math.floor(nx), gy - 1, (int) Math.floor(nz))).is(net.minecraft.tags.FluidTags.WATER);
		if (water && !inPond(nx, nz)) {
			s.tx = m.getX(); s.tz = m.getZ(); // poza stawem nie wchodzą do wody
			s.wait = 5;
			return;
		}
		if (water) {
			m.setPos(nx, gy - 0.85, nz); // w stawie pływają zanurzeni, nie chodzą po tafli
			m.setSwimming(true);
		} else {
			double wy = walkY(level, m.getY(), (int) Math.floor(nx), (int) Math.floor(nz));
			if (Double.isNaN(wy) || !free(level, m, nx, wy, nz)) { // przeszkoda (np. drzewo): wycina ją albo ją omija, zamiast wskakiwać na szczyt
				if (!clearObstacle(level, m, nx, nz)) sidestep(m, s, dx, dz);
				else s.wait = 6;
				return;
			}
			m.setPos(nx, wy, nz);
			m.setSwimming(false);
		}
		m.setYRot(yaw);
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
	}

	// ---------- granica ----------
	private static void drawBorder(ServerLevel level) {
		for (ServerPlayer p : level.players()) {
			double px = p.getX(), pz = p.getZ(), py = p.getY();
			for (int sx = -1; sx <= 1; sx += 2) {
				double wx = sx * HALF;
				if (Math.abs(px - wx) < 40) line(level, wx, pz, py, true);
			}
			for (int sz = -1; sz <= 1; sz += 2) {
				double wz = sz * HALF;
				if (Math.abs(pz - wz) < 40) line(level, wz, px, py, false);
			}
		}
	}

	private static void line(ServerLevel level, double fixed, double center, double py, boolean fixedIsX) {
		for (int d = -24; d <= 24; d += 3) {
			for (int h = -2; h <= 10; h += 3) {
				double a = fixedIsX ? fixed : center + d;
				double b = fixedIsX ? center + d : fixed;
				level.sendParticles(ParticleTypes.END_ROD, a, py + h, b, 1, 0, 0, 0, 0);
			}
		}
	}

	public static Entity find(String nick) {
		State s = STATES.get(nick);
		return s == null ? null : s.entity;
	}
}
