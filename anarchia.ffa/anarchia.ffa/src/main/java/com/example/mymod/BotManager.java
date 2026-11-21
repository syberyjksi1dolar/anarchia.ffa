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
	public static final int COUNT = 40;
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
		java.util.UUID foeP;
		int fight;
		int cd;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Random RNG = new Random();
	private static final List<Rec> ROSTER = new ArrayList<>();
	private static final Map<String, State> STATES = new HashMap<>();
	private static boolean cleaned;
	/** Miejsce pojawienia się nowo stworzonego bota (tam, gdzie stał admin). */
	private static final Map<String, double[]> PENDING = new HashMap<>();
	private static final Map<String, SimpleContainer> INV = new HashMap<>();
	/** Saldo startowe bota na poziom (w groszach): poziom * 50 zł. Zmień tu, jeśli ma być inaczej. */
	private static final long FUNDS_PER_LEVEL = 5000;
	// staw: 11 100 13 / -11 100 35
	private static final int POND_X1 = -11, POND_X2 = 11, POND_Z1 = 13, POND_Z2 = 35;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("anarchia_ffa_boty.json");
	}

	private static Path invFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("anarchia_ffa_boty_ekw.json");
	}

	// ---------- API ekwipunku ----------
	public static List<String> nicks() {
		List<String> l = new ArrayList<>();
		for (Rec r : ROSTER) l.add(r.nick);
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
		loadOrCreate();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			cleaned = false;
			loadInv();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> saveInv());
		ServerTickEvents.END_SERVER_TICK.register(BotManager::tick);
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
				if (l != null && !l.isEmpty()) {
					ROSTER.addAll(l);
					return;
				}
			}
		} catch (Exception e) {
			MyMod.LOGGER.warn("Nie udało się wczytać botów: {}", e.toString());
		}
		// rozkład dla 40 botów: 4 x poz.1, 6 x 2-3, 20 x 4-5, 1 x 6-7, 8 x 8-9, 1 x 10
		List<Integer> levels = new ArrayList<>();
		for (int i = 0; i < 4; i++) levels.add(1);
		for (int i = 0; i < 6; i++) levels.add(2 + RNG.nextInt(2));
		for (int i = 0; i < 20; i++) levels.add(4 + RNG.nextInt(2));
		levels.add(6 + RNG.nextInt(2));
		for (int i = 0; i < 8; i++) levels.add(8 + RNG.nextInt(2));
		levels.add(10);
		Collections.shuffle(levels, RNG);
		Set<String> used = new HashSet<>();
		for (int i = 0; i < COUNT; i++) {
			Rec r = new Rec();
			String n;
			do {
				String p = PREFIX[RNG.nextInt(PREFIX.length)];
				int form = RNG.nextInt(3);
				n = form == 0 ? p + (10 + RNG.nextInt(90)) : form == 1 ? p + (100 + RNG.nextInt(900)) : p + "_PL";
			} while (!used.add(n));
			r.nick = n;
			r.level = levels.get(i);
			r.skin = SKINS[RNG.nextInt(SKINS.length)];
			ROSTER.add(r);
		}
		save();
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
		} else if (isWaterAt(level, x, z)) { // nie pojawiamy się na wodzie (boty nie umieją pływać)
			x = SPAWN_X + RNG.nextInt(7) - 3;
			z = SPAWN_Z + RNG.nextInt(7) - 3;
		}
		final double sy = pend != null ? pend[1] : Math.max(SPAWN_Y, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z)));
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
		r.kit = false; // po śmierci bot dostaje nowy starter pack przy kolejnym pojawieniu się
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
	private static void giveKit(Rec r) {
		SimpleContainer c = inv(r.nick);
		int l = r.level;
		c.addItem(new ItemStack(Items.BREAD, 6 + l * 2));
		c.addItem(new ItemStack(Items.COBBLESTONE, 16 + l * 6));
		c.addItem(new ItemStack(Items.COAL, 4 + l * 3));
		c.addItem(new ItemStack(Items.OAK_LOG, l >= 5 ? 80 : 8 + l * 4));
		c.addItem(new ItemStack(Items.APPLE, 2 + l));
		if (l >= 2) c.addItem(new ItemStack(Items.IRON_INGOT, l * 2));
		if (l >= 3) c.addItem(new ItemStack(Items.ARROW, l * 4));
		if (l >= 4) {
			c.addItem(new ItemStack(Items.WHEAT_SEEDS, 8));
			c.addItem(new ItemStack(Items.WATER_BUCKET, 1));
		}
		if (l >= 6) c.addItem(new ItemStack(Items.DIAMOND, l - 4));
		if (l >= 8) c.addItem(new ItemStack(Items.EMERALD, l - 5));
		if (l >= 9) c.addItem(new ItemStack(Items.GOLDEN_APPLE, 2));
		// starter pack: miecz i zbroja zależne od poziomu (prawdziwe przedmioty)
		c.addItem(new ItemStack(swordFor(l)));
		for (Item a : armorFor(l)) c.addItem(new ItemStack(a));
	}

	private static Item swordFor(int l) {
		return l >= 10 ? Items.NETHERITE_SWORD : l >= 7 ? Items.DIAMOND_SWORD : l >= 4 ? Items.IRON_SWORD : l >= 2 ? Items.STONE_SWORD : Items.WOODEN_SWORD;
	}

	private static Item[] armorFor(int l) {
		if (l >= 10) return new Item[] {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
		if (l >= 7) return new Item[] {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
		if (l >= 4) return new Item[] {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
		if (l >= 2) return new Item[] {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
		return new Item[0];
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

	private static boolean inSpawnArea(double x, double z) {
		return x >= -14 && x <= 15 && z >= -14 && z <= 15;
	}

	/** Walka bota z innym botem: podchodzi i bije co ok. 0,6 s; siła zależy od poziomu. Zwraca true, gdy walczy w tym ticku. */
	private static boolean fightStep(ServerLevel level, Rec r, State s) {
		Mannequin m = s.entity;
		net.minecraft.world.entity.LivingEntity f;
		boolean vsPlayer = s.foeP != null;
		if (vsPlayer) {
			f = level.getServer().getPlayerList().getPlayer(s.foeP);
			if (f == null || f.level() != level || !f.isAlive() || ((ServerPlayer) f).isCreative() || ((ServerPlayer) f).isSpectator()
					|| --s.fight <= 0 || inSpawnArea(f.getX(), f.getZ()) || f.distanceToSqr(m) > 30 * 30) {
				s.foeP = null;
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
		if (inSpawnArea(m.getX(), m.getZ()) && vsPlayer) {
			s.foeP = null;
			return false;
		}
		double dx = f.getX() - m.getX(), dz = f.getZ() - m.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		if (d > 2.0) {
			double step = Math.min(0.12 + 0.015 * r.level, d - 1.8);
			double nx = m.getX() + dx / d * step, nz = m.getZ() + dz / d * step;
			if (level.hasChunkAt(new BlockPos((int) Math.floor(nx), 64, (int) Math.floor(nz))) && !isWaterAt(level, nx, nz)) {
				int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(nx), (int) Math.floor(nz));
				m.setPos(nx, gy, nz);
			}
		} else if (--s.cd <= 0) {
			s.cd = 12;
			float dmg = vsPlayer ? (float) (1.0 + r.level * 0.35) : (float) (2.0 + r.level * 0.8);
			f.hurtServer(level, level.damageSources().mobAttack(m), dmg);
			if (vsPlayer) Antylogout.tagPlayer((ServerPlayer) f);
			m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		}
		m.setYRot(yaw);
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
		return true;
	}

	/** Czasem bot zaczepia najbliższego bota w pobliżu (poza spawnem i stawem) i zaczyna się bójka. */
	private static void maybeStartFight(ServerLevel level, Rec r, State s) {
		Mannequin m = s.entity;
		if (s.foeP == null && !inSpawnArea(m.getX(), m.getZ()) && !inPond(m.getX(), m.getZ())) {
			ServerPlayer near = null;
			double nd = 14 * 14;
			for (ServerPlayer p : level.players()) {
				if (!p.isAlive() || p.isCreative() || p.isSpectator() || inSpawnArea(p.getX(), p.getZ())) continue;
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
		if ((s.foe != null || s.foeP != null) && fightStep(level, r, s)) return;
		if (BotBuilder.busy(r.nick)) return; // buduje, stoi w miejscu
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
			if (RNG.nextInt(25) == 0) { // czasem idą popływać w stawie
				s.tx = POND_X1 + 1 + RNG.nextDouble() * (POND_X2 - POND_X1 - 2);
				s.tz = POND_Z1 + 1 + RNG.nextDouble() * (POND_Z2 - POND_Z1 - 2);
			}
			s.wait = RNG.nextInt(Math.max(5, 60 - r.level * 5));
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
