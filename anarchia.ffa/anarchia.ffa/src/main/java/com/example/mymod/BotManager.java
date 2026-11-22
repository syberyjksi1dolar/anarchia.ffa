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
	public static final int COUNT = 100;
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
		boolean mats;
		String clan;
		String role;
		String buddy;
		boolean hasHome;
		int slot;
		double hx, hz;
		String partner; // UUID gracza, z którym bot zawarł sojusz
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
		return INV.computeIfAbsent(nick, k -> new SimpleContainer(36));
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
						SimpleContainer c = new SimpleContainer(36);
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
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mannequin m && isBot(m) && source.getEntity() instanceof ServerPlayer
					&& entity.level() instanceof ServerLevel lv && RNG.nextDouble() < 0.03) {
				dropBook(lv, m);
			}
		});
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
		for (Rec r : ROSTER) { // dawne klany K1.. znikają, sojusze tworzą się teraz w grze
			if (r.clan != null && r.clan.matches("K\\d+")) {
				r.clan = null;
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
		for (Rec r : ROSTER) {
			State s = STATES.computeIfAbsent(r.nick, k -> {
				State n = new State();
				n.respawnIn = RNG.nextInt(100) + 20; // boty pojawiają się stopniowo
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
				if (--s.respawnIn <= 0) {
					spawn(server, level, r, s);
					s.respawnIn = 100; // ponowna próba po 5 s, jeśli się nie udało
				}
				continue;
			}
			s.lx = s.entity.getX();
			s.ly = s.entity.getY();
			s.lz = s.entity.getZ();
			moveBot(level, r, s);
			if (t % 10 == 0) {
				activity(level, r, s);
				maybeStartFight(level, r, s);
				maybeStartMine(level, r, s);
				maybeAlly(level, r, s);
			}
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

	private static void dropAll(ServerLevel level, Rec r, State s) {
		BotChat.event(level.getServer(), "death", r.nick);
		Mannequin dead = s.entity;
		if (dead != null) {
			net.minecraft.world.entity.EquipmentSlot[] all = {net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.entity.EquipmentSlot.HEAD,
					net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
			for (net.minecraft.world.entity.EquipmentSlot sl : all) {
				try {
					ItemStack w = dead.getItemBySlot(sl);
					if (!w.isEmpty()) {
						level.addFreshEntity(new ItemEntity(level, s.lx, s.ly + 0.5, s.lz, w.copy()));
						dead.setItemSlot(sl, ItemStack.EMPTY);
					}
				} catch (Exception e) {
					MyMod.LOGGER.warn("Wyrzucanie ubioru bota: {}", e.toString());
				}
			}
		}
		SimpleContainer c = inv(r.nick);
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack st = c.removeItemNoUpdate(i);
			if (!st.isEmpty()) {
				level.addFreshEntity(new ItemEntity(level, s.lx, s.ly + 0.5, s.lz, st));
			}
		}
		saveInv();
	}

	/** Co 0,5 s: podnoszenie przedmiotów z ziemi i zbieranie drewna/rud z najbliższego otoczenia. */
	private static void activity(ServerLevel level, Rec r, State s) {
		Mannequin m = s.entity;
		eatIfHungry(r, m);
		if (s.fleeT > 0) s.fleeT -= 10;
		if (m.getHealth() < m.getMaxHealth() && level.getServer().getTickCount() % 100 == 0) m.heal(1f); // powolna regeneracja
		if (s.foe == null && s.foeP == null && s.mine == null && !BotBuilder.busy(r.nick) && s.rtpT <= 0) seekWood(level, r, s);
		AABB near = m.getBoundingBox().inflate(1.5, 1.0, 1.5);
		for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, near)) {
			if (ie.isRemoved()) continue;
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
		if (BotBuilder.busy(r.nick)) {
			if (s.act++ % 4 == 0) BotBuilder.step(level, r.nick, m);
			return;
		}
		if (++s.act % 4 != 0) return;
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
		c.addItem(new ItemStack(Items.COBBLESTONE, 160 + l * 16));
		c.addItem(new ItemStack(Items.COAL, 8 + l * 4));
		c.addItem(new ItemStack(Items.OAK_LOG, 48 + l * 16));
		c.addItem(new ItemStack(Items.OAK_PLANKS, 96 + l * 8));
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
			c.addItem(new ItemStack(Items.ARROW, l * 6));
			c.addItem(new ItemStack(Items.GOLD_INGOT, l));
			c.addItem(new ItemStack(Items.LAPIS_LAZULI, l * 2));
			c.addItem(new ItemStack(Items.SNOWBALL, 8));
		}
		if (l >= 4) {
			c.addItem(new ItemStack(Items.SHIELD, 1));
			c.addItem(new ItemStack(Items.SLIME_BALL, l));
			c.addItem(new ItemStack(Items.QUARTZ, l * 2));
		}
		if (l >= 6) {
			c.addItem(new ItemStack(Items.DIAMOND, l - 3));
			c.addItem(new ItemStack(Items.ENDER_PEARL, 2));
			c.addItem(new ItemStack(Items.TNT, 3));
		}
		if (l >= 8) {
			c.addItem(new ItemStack(Items.EMERALD, l - 4));
			c.addItem(new ItemStack(Items.GOLDEN_CARROT, 8));
		}
		if (l >= 9) c.addItem(new ItemStack(Items.GOLDEN_APPLE, 3));
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
			"diamond", "tnt", "emerald", "golden_apple", "water_bucket");

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
	private static double[] pickSpot(ServerLevel level, String self) {
		for (int tries = 0; tries < 40; tries++) {
			double ax = RNG.nextInt(401) - 200 + 0.5, az = RNG.nextInt(401) - 200 + 0.5;
			if (Math.abs(ax) < 14 && Math.abs(az) < 14) continue;
			if (!level.hasChunkAt(new BlockPos((int) ax, 64, (int) az))) continue;
			if (isWaterAt(level, ax, az)) continue;
			boolean close = false;
			for (Map.Entry<String, State> e : STATES.entrySet()) {
				Mannequin o = e.getValue().entity;
				if (o == null || o.isRemoved() || e.getKey().equals(self)) continue;
				double dx = o.getX() - ax, dz = o.getZ() - az;
				if (dx * dx + dz * dz < 400) { close = true; break; }
			}
			if (!close) return new double[] {ax, az};
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
				|| RNG.nextInt("B".equals(r.role) ? 120 : 240) != 0) return;
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

	private static void endMine(Mannequin m, State s) {
		Mine k = s.mine;
		s.mine = null;
		s.wait = 0;
		if (k != null) {
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
			endMine(m, s);
			return;
		}
		m.setPos(k.x, k.y, k.z);
	}

	/** Gracz kuca 5 razy szybko obok bota: 25% szans na sojusz (bot chodzi za graczem i go broni). */
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
			if (best == null) continue;
			c[3] = (int) t + 100; // 5 s przerwy między próbami
			int mine = 0;
			String pid = p.getUUID().toString();
			for (Rec r : ROSTER) if (pid.equals(r.partner)) mine++;
			if (mine >= 5) {
				p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Masz już 5 sojuszników-botów.").withStyle(net.minecraft.ChatFormatting.RED));
				continue;
			}
			Rec r = recOf(best);
			if (RNG.nextDouble() < 0.25) {
				String pc = playerClan(pid);
				r.clan = pc != null ? pc : newClanName();
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
				server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal(p.getName().getString() + " zawarł sojusz z botem "
						+ best + "!").withStyle(net.minecraft.ChatFormatting.AQUA), false);
			} else {
				p.sendSystemMessage(net.minecraft.network.chat.Component.literal(best + " nie chce sojuszu. Spróbuj ponownie za chwilę.").withStyle(net.minecraft.ChatFormatting.GRAY));
			}
		}
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
				String sig = r.clan + "|" + (same ? "g" : "b");
				if (sig.equals(seen.get(sn))) continue;
				seen.put(sn, sig);
				try {
					net.minecraft.ChatFormatting col = same ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.AQUA;
					net.minecraft.world.scores.PlayerTeam team = new net.minecraft.world.scores.PlayerTeam(server.getScoreboard(), "al_" + r.clan + (same ? "_g" : "_b"));
					team.setColor(col);
					team.setPlayerPrefix(net.minecraft.network.chat.Component.literal("[" + r.clan + "] ").withStyle(col));
					team.getPlayers().add(sn); // pakiet tworzenia drużyny od razu zawiera tego bota
					viewer.connection.send(net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
				} catch (Exception ex) {
					MyMod.LOGGER.warn("Znacznik sojuszu: {}", ex.toString());
				}
			}
		}
	}

	/** Działka bota (posesja): siatka 4x4 pól co 13 bloków wokół jego pierwszego spawnu; null, gdy brak domu albo wszystkie pola w strefie spawnu. */
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

	public static boolean isBuilder(String nick) {
		for (Rec r : ROSTER) if (r.nick.equals(nick)) return "B".equals(r.role);
		return false;
	}

	private static Rec recOf(String nick) {
		for (Rec r : ROSTER) if (r.nick.equals(nick)) return r;
		return null;
	}

	private static int clanSize(String clan) {
		int n = 0;
		for (Rec r : ROSTER) if (clan.equals(r.clan)) n++;
		return n;
	}

	private static String newClanName() {
		while (true) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 4; i++) sb.append((char) ('A' + RNG.nextInt(26)));
			sb.append(1 + RNG.nextInt(9));
			String n = sb.toString();
			boolean used = false;
			for (Rec r : ROSTER) if (n.equals(r.clan)) used = true;
			if (!used) return n;
		}
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
			Rec ro = recOf(e.getKey());
			if (ro == null || (r.clan != null && ro.clan != null)) continue;
			String c = r.clan != null ? r.clan : ro.clan;
			if (c != null && clanSize(c) >= 5) continue;
			s.ritual = 40;
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
		}
		save();
		BotChat.event(SRV, "ally", a.nick);
		if (SRV != null) {
			SRV.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("[" + clan + "] " + a.nick + " i " + b.nick
					+ " zawarli sojusz (klan " + clan + ").").withStyle(net.minecraft.ChatFormatting.AQUA), false);
		}
	}

	/** Noc według czasu gry (przybliżenie: doba trwa 24000 tików, noc to 13000-23000). */
	private static boolean isNightTime(ServerLevel level) {
		long t = level.getGameTime() % 24000L;
		return t >= 13000L && t < 23000L;
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
		if (logs >= 60 || RNG.nextInt(25) != 0) return;
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

	/** Walka bota z innym botem: podchodzi i bije co ok. 0,6 s; siła zależy od poziomu. Zwraca true, gdy walczy w tym ticku. */
	private static boolean fightStep(ServerLevel level, Rec r, State s) {
		Mannequin m = s.entity;
		if (s.foeP != null && r.partner != null && s.foeP.toString().equals(r.partner)) { // sojusznika gracza nie bijemy
			s.foeP = null;
			s.retal = false;
			return false;
		}
		eatIfHungry(r, m);
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
		double dx = f.getX() - m.getX(), dz = f.getZ() - m.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		// od czasu do czasu podskakują i sprintują
		if (s.jump > 0) s.jump--; else if (RNG.nextInt(40) == 0) s.jump = 10;
		if (s.sprintT > 0) s.sprintT--; else if (RNG.nextInt(60) == 0) s.sprintT = 30;
		boolean sprint = s.sprintT > 0;
		double jh = s.jump > 0 ? 0.6 * Math.sin(Math.PI * (10 - s.jump) / 10.0) : 0.0;
		boolean los = m.hasLineOfSight(f); // przez ściany nie biją
		if (d > 4.0 || !los) {
			double speed = (0.12 + 0.015 * r.level) * (sprint ? 1.7 : 1.0);
			double step = Math.max(0.03, Math.min(speed, d - 3.0));
			double nx = m.getX() + dx / d * step, nz = m.getZ() + dz / d * step;
			if (level.hasChunkAt(new BlockPos((int) Math.floor(nx), 64, (int) Math.floor(nz))) && !isWaterAt(level, nx, nz)) {
				int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(nx), (int) Math.floor(nz));
				m.setPos(nx, gy + jh, nz);
			}
			m.setSprinting(sprint);
		} else {
			m.setSprinting(false);
			if (s.jump > 0) {
				int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(m.getX()), (int) Math.floor(m.getZ()));
				m.setPos(m.getX(), gy + jh, m.getZ());
			}
			if (--s.cd <= 0) {
				s.cd = 20; // zasięg 4 bloki, jedno uderzenie na sekundę
				float dmg = vsPlayer ? (float) (1.0 + r.level * 0.35) : (float) (2.0 + r.level * 0.8);
				f.hurtServer(level, level.damageSources().mobAttack(m), dmg);
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
		if (s.foeP == null && !inSpawnArea(m.getX(), m.getZ()) && !inPond(m.getX(), m.getZ())) {
			ServerPlayer near = null;
			double nd = 14 * 14;
			for (ServerPlayer p : level.players()) {
				if (!p.isAlive() || p.isCreative() || p.isSpectator() || inSpawnArea(p.getX(), p.getZ())) continue;
				if (p.getUUID().toString().equals(r.partner)) continue; // sojusznik gracz
				double d = p.distanceToSqr(m);
				if (d < nd) { nd = d; near = p; }
			}
			if (near != null) { // boty są agresywne wobec graczy
				s.foeP = near.getUUID();
				s.foe = null;
				s.fight = 600;
				return;
			}
		}
		if (s.foe != null || s.foeP != null || RNG.nextInt(20) != 0 || inSpawnArea(m.getX(), m.getZ()) || inPond(m.getX(), m.getZ())) return;
		String best = null;
		double bd = 15 * 15;
		for (Map.Entry<String, State> e : STATES.entrySet()) {
			Mannequin o = e.getValue().entity;
			if (o == null || o == m || o.isRemoved() || !o.isAlive() || inSpawnArea(o.getX(), o.getZ())) continue;
			String mc = r.clan;
			if (mc != null && mc.equals(clanOf(e.getKey()))) continue; // sojusznik
			double dx = o.getX() - m.getX(), dz = o.getZ() - m.getZ();
			double d = dx * dx + dz * dz;
			if (d < bd) { bd = d; best = e.getKey(); }
		}
		if (best == null) return;
		s.foe = best;
		s.fight = 400;
		State other = STATES.get(best);
		if (other.foe == null) { // zaczepiony oddaje
			other.foe = r.nick;
			other.fight = 400;
		}
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
				&& !BotBuilder.busy(r.nick) && s.ritual <= 0) { // w nocy wracają do domu i tam stoją
			double hx = r.hx - m.getX(), hz = r.hz - m.getZ();
			if (hx * hx + hz * hz < 16) return;
			s.tx = r.hx;
			s.tz = r.hz;
			s.wait = 0;
		}
		if (s.ritual > 0 && ritualStep(level, r, s)) return;
		if ((s.foe != null || s.foeP != null) && fightStep(level, r, s)) return;
		if (s.mine != null) { mineStep(level, r, s); return; }
		if (BotBuilder.busy(r.nick)) return; // buduje, stoi w miejscu
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
		double speed = 0.10 + 0.015 * r.level; // bloków na tick
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
		m.setPos(nx, water ? gy - 0.85 : gy, nz); // w stawie pływają zanurzeni, nie chodzą po tafli
		m.setSwimming(water);
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
