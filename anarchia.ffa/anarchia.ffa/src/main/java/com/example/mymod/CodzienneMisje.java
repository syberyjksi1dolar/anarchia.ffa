package com.example.mymod;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.Command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * /codziennemisje: menu (GUI skrzyni, 3 rzędy) z 4 misjami dnia. Postęp zeruje się o północy (czas polski).
 * Misje: zabij 5 botów, sojusz z 5 botami, mur 25x25x8 + farma 9x9, wykop 16 diamentów i 64 żelaza.
 * Nagroda odbierana kliknięciem w ikonę, gdy misja jest wykonana (raz dziennie).
 */
public final class CodzienneMisje {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int[] SLOTS = { 10, 12, 14, 16 };

	private static final class Prog {
		long day;
		int kills, diamonds, iron;
		boolean[] claimed = new boolean[4];
	}

	private static final Map<UUID, Prog> DATA = new HashMap<>();
	/** Każda misja dnia może zostać wykonana tylko raz na całym serwerze (przez gracza albo bota): kto pierwszy. */
	private static String[] taken = new String[4];
	private static long takenDay = 0;
	private static final Map<String, int[]> BOT_KILLS = new HashMap<>(); // nick -> {dzień, zabójstwa}
	private static final java.util.Set<String> BOT_LOGGED = new java.util.HashSet<>();

	private static String[] taken() {
		if (takenDay != today()) {
			takenDay = today();
			taken = new String[4];
		}
		return taken;
	}

	private static Path takenFile() {
		return WorldData.file("anarchia_ffa_misje_dzien.json");
	}


	private CodzienneMisje() {
	}

	private static Path file() {
		return WorldData.file("anarchia_ffa_misje.json");
	}

	private static long today() {
		return LocalDate.now(ZoneId.of("Europe/Warsaw")).toEpochDay();
	}

	private static Prog prog(UUID id) {
		Prog p = DATA.computeIfAbsent(id, k -> new Prog());
		if (p.day != today()) {
			p.day = today();
			p.kills = 0;
			p.diamonds = 0;
			p.iron = 0;
			p.claimed = new boolean[4];
		}
		return p;
	}

	private static void load() {
		DATA.clear();
		try {
			Path tf = takenFile();
			if (Files.exists(tf)) {
				String[] raw = GSON.fromJson(Files.readString(tf, StandardCharsets.UTF_8), String[].class);
				if (raw != null && raw.length == 5) {
					takenDay = Long.parseLong(raw[4]);
					taken = java.util.Arrays.copyOf(raw, 4);
				}
			}
			Path f = file();
			if (Files.exists(f)) {
				Type t = new TypeToken<Map<UUID, Prog>>() {}.getType();
				Map<UUID, Prog> m = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), t);
				if (m != null) {
					DATA.putAll(m);
				}
			}
		} catch (Exception e) {
			MyMod.LOGGER.warn("Misje: nie wczytano: {}", e.toString());
		}
	}

	private static void save() {
		try {
			Files.writeString(file(), GSON.toJson(DATA), StandardCharsets.UTF_8);
			String[] t = taken();
			String[] raw = java.util.Arrays.copyOf(t, 5);
			raw[4] = Long.toString(takenDay);
			Files.writeString(takenFile(), GSON.toJson(raw), StandardCharsets.UTF_8);
		} catch (IOException e) {
			MyMod.LOGGER.warn("Misje: nie zapisano: {}", e.toString());
		}
	}

	public static void initialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> load());
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> save());

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mannequin m && BotManager.isBot(m) && source.getEntity() instanceof ServerPlayer sp) {
				prog(sp.getUUID()).kills++;
				save();
			} else if (entity instanceof Mannequin m2 && BotManager.isBot(m2) && source.getEntity() != null) {
				String kn = BotManager.nickOf(source.getEntity());
				if (kn != null) {
					int[] k = BOT_KILLS.computeIfAbsent(kn, x -> new int[] { 0, 0 });
					if (k[0] != (int) today()) {
						k[0] = (int) today();
						k[1] = 0;
					}
					k[1]++;
				}
			}
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (!(player instanceof ServerPlayer sp)) {
				return;
			}
			Block b = state.getBlock();
			if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE) {
				prog(sp.getUUID()).diamonds++;
				save();
			} else if (b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE) {
				prog(sp.getUUID()).iron++;
				save();
			}
		});

		CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(
				net.minecraft.commands.Commands.literal("codziennemisje").executes(c -> {
					open(c.getSource().getPlayerOrException());
					return Command.SINGLE_SUCCESS;
				})));
	}

	// ---------- GUI ----------

	private static ItemStack named(Item item, String name, List<Component> lore) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.YELLOW));
		s.set(DataComponents.LORE, new ItemLore(lore));
		return s;
	}

	private static Component line(String t, ChatFormatting f) {
		return Component.literal(t).withStyle(f);
	}

	private static void open(ServerPlayer sp) {
		SimpleContainer box = new SimpleContainer(27);
		fill(sp, box);
		sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new RangiMenu(id, inv, box, 3, (pl, slot) -> {
			for (int i = 0; i < SLOTS.length; i++) {
				if (SLOTS[i] == slot) {
					claim(pl, i);
					fill(pl, box);
				}
			}
		}), Component.literal("Codzienne misje")));
	}

	private static void fill(ServerPlayer sp, SimpleContainer box) {
		Prog p = prog(sp.getUUID());
		int allies = BotManager.allyCount(sp.getUUID());
		box.setItem(SLOTS[0], withTaken(mission(Items.PLAYER_HEAD, "Zabij 5 botów", p.claimed[0], p.kills >= 5,
				"Postęp: " + Math.min(p.kills, 5) + "/5",
				"Nagroda: anarchiczny helm i buty (zbroja 2) oraz diamentowy napierśnik i spodnie"), 0, p));
		box.setItem(SLOTS[1], withTaken(mission(Items.GREEN_BANNER, "Załóż sojusz z 5 osób", p.claimed[1], allies >= 5,
				"Postęp: " + Math.min(allies, 5) + "/5 sojuszników",
				"Nagroda: 4 zaklęte złote jabłka i 16 złotych jabłek"), 1, p));
		box.setItem(SLOTS[2], withTaken(mission(Items.VILLAGER_SPAWN_EGG, "Postaw mur 25x25x8 i farmę 9x9", p.claimed[2], false,
				"Stań przy murze i farmie, potem kliknij: sprawdzę",
				"Nagroda: 1 złoty bilet"), 2, p));
		box.setItem(SLOTS[3], withTaken(mission(Items.DIAMOND_PICKAXE, "Wykop 16 diamentów i 64 żelaza", p.claimed[3], p.diamonds >= 16 && p.iron >= 64,
				"Diamenty: " + Math.min(p.diamonds, 16) + "/16, żelazo: " + Math.min(p.iron, 64) + "/64 (rudy)",
				"Nagroda: diamentowy kilof, wydajność 6, niezniszczalność 7"), 3, p));
	}

	private static ItemStack mission(Item icon, String name, boolean claimed, boolean ready, String progress, String reward) {
		List<Component> lore = new ArrayList<>();
		lore.add(line(progress, ChatFormatting.GRAY));
		lore.add(line(reward, ChatFormatting.AQUA));
		if (claimed) {
			lore.add(line("✔ Odebrane (nowa misja jutro)", ChatFormatting.GREEN));
		} else if (ready) {
			lore.add(line("Kliknij, aby odebrać nagrodę!", ChatFormatting.GREEN));
		} else {
			lore.add(line("Kliknij, aby sprawdzić", ChatFormatting.DARK_GRAY));
		}
		return named(icon, name, lore);
	}

	private static ItemStack withTaken(ItemStack st, int i, Prog p) {
		String who = taken()[i];
		if (who != null && !p.claimed[i]) {
			List<Component> l = new ArrayList<>(st.get(DataComponents.LORE).lines());
			l.add(line("🔒 Dzisiaj ją wykonał: " + who + " (tylko 1 osoba na serwerze)", ChatFormatting.RED));
			st.set(DataComponents.LORE, new ItemLore(l));
		}
		return st;
	}

	// ---------- odbieranie ----------

	private static void claim(ServerPlayer sp, int i) {
		Prog p = prog(sp.getUUID());
		if (p.claimed[i]) {
			sp.sendSystemMessage(line("Tę misję już odebrałeś dzisiaj.", ChatFormatting.GRAY));
			return;
		}
		if (taken()[i] != null) {
			sp.sendSystemMessage(line("🔒 Tę misję dzisiaj wykonał już: " + taken()[i], ChatFormatting.RED));
			return;
		}
		boolean ok = switch (i) {
			case 0 -> p.kills >= 5;
			case 1 -> BotManager.allyCount(sp.getUUID()) >= 5;
			case 2 -> wallOk(sp) && farmOk(sp);
			default -> p.diamonds >= 16 && p.iron >= 64;
		};
		if (!ok) {
			sp.sendSystemMessage(line(i == 2 ? "Nie widzę muru 25x25x8 i farmy 9x9 w pobliżu - stań przy nich." : "Misja jeszcze niewykonana.", ChatFormatting.RED));
			return;
		}
		p.claimed[i] = true;
		taken()[i] = sp.getGameProfile().name();
		save();
		sp.level().getServer().getPlayerList().broadcastSystemMessage(line("🎯 " + sp.getGameProfile().name() + " wykonał codzienną misję: " + MISSION_NAMES[i] + " 🏆", ChatFormatting.GOLD), false);
		RegistryAccess ra = sp.level().registryAccess();
		List<ItemStack> rewards = giveRewards(ra, i);
		for (ItemStack s : rewards) {
			if (!sp.getInventory().add(s)) {
				sp.drop(s, false);
			}
		}
		sp.sendSystemMessage(line("✅ Misja wykonana! Nagroda odebrana 🎁", ChatFormatting.GREEN));
	}

	private static final String[] MISSION_NAMES = { "Zabij 5 botów", "Sojusz z 5 osób", "Mur 25x25x8 i farma 9x9", "Wykop 16 diamentów i 64 żelaza" };

	private static List<ItemStack> giveRewards(RegistryAccess ra, int i) {
		List<ItemStack> rewards = new ArrayList<>();
		switch (i) {
			case 0 -> {
				rewards.add(Crates.enchanted(ra, ModItems.ANARCHICZNA_ZBROJA_2_HELM, Enchantments.UNBREAKING, 10, Enchantments.PROTECTION, 10));
				rewards.add(Crates.enchanted(ra, ModItems.ANARCHICZNA_ZBROJA_2_BUTY, Enchantments.UNBREAKING, 10, Enchantments.PROTECTION, 10));
				rewards.add(Crates.enchanted(ra, Items.DIAMOND_CHESTPLATE, Enchantments.UNBREAKING, 5, Enchantments.PROTECTION, 5));
				rewards.add(Crates.enchanted(ra, Items.DIAMOND_LEGGINGS, Enchantments.UNBREAKING, 5, Enchantments.PROTECTION, 5));
			}
			case 1 -> {
				rewards.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 4));
				rewards.add(new ItemStack(Items.GOLDEN_APPLE, 16));
			}
			case 2 -> rewards.add(new ItemStack(ModItems.ZLOTY_BILET));
			default -> rewards.add(Crates.enchanted(ra, Items.DIAMOND_PICKAXE, Enchantments.EFFICIENCY, 6, Enchantments.UNBREAKING, 7));
		}
		return rewards;
	}


	// ---------- boty (87% chce robić misje, 13% nie) ----------

	public static boolean botWants(String nick) {
		return Math.floorMod(nick.hashCode(), 100) < 87;
	}

	private static int count(net.minecraft.world.SimpleContainer inv, Item... items) {
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			for (Item it : items) {
				if (st.is(it)) {
					n += st.getCount();
				}
			}
		}
		return n;
	}

	/** Co 0,5 s dla bota: z małym prawdopodobieństwem sprawdza, czy spełnił jakąś jeszcze wolną misję dnia. */
	public static void botTick(ServerLevel level, String nick, net.minecraft.world.SimpleContainer inv, net.minecraft.world.entity.Entity e) {
		if (!botWants(nick) || level.getRandom().nextInt(20) != 0) {
			return;
		}
		String[] t = taken();
		for (int i = 0; i < 4; i++) {
			if (t[i] != null) {
				continue;
			}
			boolean ok = switch (i) {
				case 0 -> {
					int[] k = BOT_KILLS.get(nick);
					yield k != null && k[0] == (int) today() && k[1] >= 5;
				}
				case 1 -> BotManager.clanMembers(nick) >= 5;
				case 2 -> {
					int[] w = BotManager.wallInfo(nick);
					yield w[2] == 1 && w[0] >= 25 && w[1] >= 8 && farmOk(level, e.blockPosition());
				}
				default -> count(inv, Items.DIAMOND) >= 16 && count(inv, Items.IRON_INGOT, Items.RAW_IRON, Items.IRON_ORE, Items.DEEPSLATE_IRON_ORE) >= 64;
			};
			if (!ok) {
				continue;
			}
			t[i] = nick;
			save();
			for (ItemStack st : giveRewards(level.registryAccess(), i)) {
				inv.addItem(st);
			}
			level.getServer().getPlayerList().broadcastSystemMessage(line("🤖 Bot " + nick + " wykonał codzienną misję: " + MISSION_NAMES[i] + " 🏆", ChatFormatting.GOLD), false);
			return;
		}
	}

	// ---------- sprawdzanie muru i farmy ----------

	private static boolean solid(ServerLevel l, int x, int y, int z) {
		BlockState b = l.getBlockState(new BlockPos(x, y, z));
		return !b.isAir() && !b.canBeReplaced();
	}

	/** Mur: kwadratowy pierścień 25x25 (obwód) o wysokości 8, w którym stoi gracz; do 10% braków (brama). */
	private static boolean wallOk(ServerPlayer sp) {
		return wallOk((ServerLevel) sp.level(), sp.blockPosition());
	}

	private static boolean wallOk(ServerLevel l, BlockPos at) {
		int px = at.getX(), py = at.getY(), pz = at.getZ();
		int total = 96 * 8, allowed = total / 10;
		for (int ox = px - 24; ox <= px; ox++) {
			for (int oz = pz - 24; oz <= pz; oz++) {
				for (int y0 = py - 5; y0 <= py + 1; y0++) {
					int miss = 0;
					outer:
					for (int k = 0; k < 25; k++) {
						for (int[] c : new int[][] { { ox + k, oz }, { ox + k, oz + 24 }, { ox, oz + k }, { ox + 24, oz + k } }) {
							for (int h = 0; h < 8; h++) {
								if (!solid(l, c[0], y0 + h, c[1]) && ++miss > allowed) {
									break outer;
								}
							}
						}
					}
					if (miss <= allowed) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Farma: kwadrat 9x9, w którym prawie wszystko to grządki lub woda (min. 60 grządek). */
	private static boolean farmOk(ServerPlayer sp) {
		return farmOk((ServerLevel) sp.level(), sp.blockPosition());
	}

	private static boolean farmOk(ServerLevel l, BlockPos at) {
		int px = at.getX(), py = at.getY(), pz = at.getZ();
		for (int x0 = px - 30; x0 <= px + 22; x0++) {
			for (int z0 = pz - 30; z0 <= pz + 22; z0++) {
				for (int y = py - 4; y <= py + 1; y++) {
					int farm = 0, other = 0;
					for (int dx = 0; dx < 9 && other <= 5; dx++) {
						for (int dz = 0; dz < 9 && other <= 5; dz++) {
							Block b = l.getBlockState(new BlockPos(x0 + dx, y, z0 + dz)).getBlock();
							if (b == Blocks.FARMLAND) {
								farm++;
							} else if (b != Blocks.WATER) {
								other++;
							}
						}
					}
					if (other <= 5 && farm >= 60) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
