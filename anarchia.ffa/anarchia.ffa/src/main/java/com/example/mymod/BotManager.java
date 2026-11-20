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
import net.minecraft.world.entity.Entity;
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
	}

	private static final class State {
		Mannequin entity;
		double tx, tz;
		int wait;
		int respawnIn;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Random RNG = new Random();
	private static final List<Rec> ROSTER = new ArrayList<>();
	private static final Map<String, State> STATES = new HashMap<>();
	private static boolean cleaned;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("anarchia_ffa_boty.json");
	}

	public static void initialize() {
		loadOrCreate();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			cleaned = false;
		});
		ServerTickEvents.END_SERVER_TICK.register(BotManager::tick);
	}

	// ---------- roster ----------
	private static void loadOrCreate() {
		ROSTER.clear();
		try {
			if (Files.exists(file())) {
				Type t = new TypeToken<List<Rec>>() {}.getType();
				List<Rec> l = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), t);
				if (l != null && l.size() == COUNT) {
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
				s.entity = null;
				if (--s.respawnIn <= 0) {
					spawn(server, level, r, s);
					s.respawnIn = 100; // ponowna próba po 5 s, jeśli się nie udało
				}
				continue;
			}
			moveBot(level, r, s);
		}
		if (t % 10 == 0) drawBorder(level);
	}

	private static void spawn(MinecraftServer server, ServerLevel level, Rec r, State s) {
		double x = SPAWN_X + RNG.nextInt(7) - 3, z = SPAWN_Z + RNG.nextInt(7) - 3;
		String base = "summon minecraft:mannequin " + x + " " + SPAWN_Y + " " + z
				+ " {CustomName:\"" + r.nick + "\",CustomNameVisible:1b,Tags:[\"" + TAG + "\"],hide_description:1b,";
		String withSkin = base + "profile:{texture:\"minecraft:entity/player/wide/" + r.skin + "\"}}";
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
		AABB box = new AABB(x - 3, SPAWN_Y - 3, z - 3, x + 3, SPAWN_Y + 6, z + 3);
		for (Mannequin m : level.getEntitiesOfClass(Mannequin.class, box, BotManager::isBot)) {
			if (r.nick.equals(m.getName().getString()) && !isTracked(m)) {
				s.entity = m;
				s.wait = 0;
				s.tx = m.getX();
				s.tz = m.getZ();
				return;
			}
		}
	}

	private static boolean isBot(Mannequin m) {
		String n = m.getName().getString();
		for (Rec r : ROSTER) if (r.nick.equals(n)) return true;
		return false;
	}

	private static boolean isTracked(Mannequin m) {
		for (State s : STATES.values()) if (s.entity == m) return true;
		return false;
	}

	private static void moveBot(ServerLevel level, Rec r, State s) {
		Mannequin m = s.entity;
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
		m.setPos(nx, gy, nz);
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
