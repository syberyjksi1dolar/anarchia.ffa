package com.example.mymod;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Rangi (vip, svip, mvip, gvip, dvip) i komendy: /vip /svip /mvip /gvip /dvip (dzienny kit rangi),
 * /klucz (dzienne klucze rangi), /ranga, oraz dla administracji /dajrange i /gift.
 * Dane trzymane w config/anarchia_ffa_rangi.json. Administratorzy: nicki w config/anarchia_ffa_admini.txt
 * (jeden na linię) lub konsola serwera.
 */
public final class RangiManager {
	public static final String[] RANKS = { "vip", "svip", "mvip", "gvip", "dvip" };
	/** Kolory rang: VIP żółty, SVIP zielony, MVIP jasnozielony, GVIP niebieski, DVIP ciemnozielony, TWÓRCA fioletowy. */
	public static final int[] RGB = { 0xFFFF55, 0x2EB82E, 0x55FF55, 0x5555FF, 0x00AA00 };
	public static final int RGB_TWORCA = 0xAA00AA;
	/** Przybliżone kolory dla drużyn scoreboardu (nick nad głową). */
	private static final ChatFormatting[] COLORS = { ChatFormatting.YELLOW, ChatFormatting.GREEN, ChatFormatting.GREEN,
			ChatFormatting.BLUE, ChatFormatting.DARK_GREEN };
	public static final String TWORCA = "tworca";
	private static final long DAY_MS = 24L * 60L * 60L * 1000L;

	/** Dane gracza zapisywane w pliku. */
	public static class Data {
		public String rank = "";
		public Map<String, Long> claims = new HashMap<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Data> DATA = new HashMap<>();
	private static final List<String> ADMINS = new ArrayList<>();

	private RangiManager() {
	}

	private static Path dataFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("anarchia_ffa_rangi.json");
	}

	private static Path adminFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("anarchia_ffa_admini.txt");
	}

	private static void load() {
		DATA.clear();
		ADMINS.clear();
		try {
			Path f = dataFile();
			if (Files.exists(f)) {
				Type type = new TypeToken<Map<String, Data>>() {
				}.getType();
				Map<String, Data> read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), type);
				if (read != null) {
					DATA.putAll(read);
				}
			}
			Path a = adminFile();
			if (!Files.exists(a)) {
				Files.writeString(a, "# Wpisz tu nicki administratorow Anarchia FFA, jeden na linie.\n", StandardCharsets.UTF_8);
			}
			for (String line : Files.readAllLines(a, StandardCharsets.UTF_8)) {
				String s = line.trim();
				if (!s.isEmpty() && !s.startsWith("#")) {
					ADMINS.add(s.toLowerCase(Locale.ROOT));
				}
			}
		} catch (IOException | RuntimeException e) {
			MyMod.LOGGER.warn("Nie udało się wczytać rang: {}", e.toString());
		}
	}

	private static void save() {
		try {
			Files.writeString(dataFile(), GSON.toJson(DATA), StandardCharsets.UTF_8);
		} catch (IOException e) {
			MyMod.LOGGER.warn("Nie udało się zapisać rang: {}", e.toString());
		}
	}

	private static Data data(ServerPlayer p) {
		return DATA.computeIfAbsent(p.getUUID().toString(), k -> new Data());
	}

	/** Poziom rangi: 0 = brak, 1 = vip ... 5 = dvip. */
	public static int level(ServerPlayer p) {
		String r = data(p).rank;
		for (int i = 0; i < RANKS.length; i++) {
			if (RANKS[i].equals(r)) {
				return i + 1;
			}
		}
		return 0;
	}

	/** Etykieta rangi do wyświetlania: [VIP], [TWÓRCA] lub „brak”. */
	public static Component rankTag(ServerPlayer p) {
		int lvl = level(p);
		if (lvl > 0) {
			return Component.literal("[").withStyle(ChatFormatting.GRAY).append(rankName(lvl)).append(Component.literal("]").withStyle(ChatFormatting.GRAY));
		}
		if (isCreator(p)) {
			return Component.literal("[").withStyle(ChatFormatting.GRAY).append(creatorName()).append(Component.literal("]").withStyle(ChatFormatting.GRAY));
		}
		return Component.literal("brak").withStyle(ChatFormatting.GRAY);
	}

	public static boolean isCreator(ServerPlayer p) {
		return TWORCA.equals(data(p).rank);
	}

	private static Component creatorName() {
		return Component.literal("TWÓRCA").withStyle(Style.EMPTY.withColor(RGB_TWORCA).withBold(true));
	}

	private static Component rankName(int lvl) {
		return Component.literal(RANKS[lvl - 1].toUpperCase(Locale.ROOT)).withStyle(Style.EMPTY.withColor(RGB[lvl - 1]).withBold(true));
	}

	private static boolean isAdmin(CommandSourceStack s) {
		ServerPlayer p = s.getPlayer();
		return p == null || ADMINS.contains(p.getName().getString().toLowerCase(Locale.ROOT));
	}

	/** Ranga nad głową, w tabie i przy nicku: drużyna scoreboardu z prefiksem [RANGA]. */
	private static void applyTeam(MinecraftServer server, ServerPlayer p) {
		var sb = server.getScoreboard();
		String name = p.getScoreboardName();
		PlayerTeam cur = sb.getPlayersTeam(name);
		if (cur != null && !cur.getName().startsWith("rg_")) {
			return; // gracz jest w cudzej drużynie, nie ruszamy
		}
		int lvl = level(p);
		String wanted = lvl > 0 ? "rg_" + RANKS[lvl - 1] : (isCreator(p) ? "rg_" + TWORCA : null);
		if (cur != null && (wanted == null || !cur.getName().equals(wanted))) {
			sb.removePlayerFromTeam(name, cur);
		}
		if (wanted == null) {
			return;
		}
		PlayerTeam team = sb.getPlayerTeam(wanted);
		if (team == null) {
			team = sb.addPlayerTeam(wanted);
		}
		ChatFormatting color = lvl > 0 ? COLORS[lvl - 1] : ChatFormatting.DARK_PURPLE;
		Component label = lvl > 0 ? rankName(lvl) : creatorName();
		team.setColor(color);
		team.setPlayerPrefix(Component.literal("[").withStyle(ChatFormatting.GRAY).append(label)
				.append(Component.literal("] ").withStyle(ChatFormatting.GRAY)));
		if (sb.getPlayersTeam(name) != team) {
			sb.addPlayerToTeam(name, team);
		}
	}

	private static void give(ServerPlayer p, ItemStack stack) {
		if (!p.getInventory().add(stack)) {
			p.drop(stack, false);
		}
	}

	private static String left(long ms) {
		long m = ms / 60000L;
		return (m / 60) + " h " + (m % 60) + " min";
	}

	/** Czy gracz może odebrać nagrodę o danym id (cooldown 24 h); jeśli tak, zapisuje czas. */
	private static boolean tryClaim(CommandSourceStack src, ServerPlayer p, String id) {
		long now = System.currentTimeMillis();
		Long last = data(p).claims.get(id);
		if (last != null && now - last < DAY_MS) {
			src.sendFailure(Component.literal("Odbierzesz to ponownie za " + left(DAY_MS - (now - last)) + "."));
			return false;
		}
		data(p).claims.put(id, now);
		save();
		return true;
	}

	private static ItemStack ench(RegistryAccess ra, ItemStack s, ResourceKey<Enchantment> key, int level) {
		s.enchant(ra.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), level);
		return s;
	}

	@SafeVarargs
	private static ItemStack gear(RegistryAccess ra, Item item, int level, ResourceKey<Enchantment>... keys) {
		ItemStack s = new ItemStack(item);
		for (ResourceKey<Enchantment> k : keys) {
			ench(ra, s, k, level);
		}
		return s;
	}

	/** Przedmioty kitu danej rangi: diamentowy (VIP, SVIP) lub netherytowy (MVIP+) zestaw, jedzenie, wiadra i dodatki. */
	@SuppressWarnings("unchecked")
	private static List<ItemStack> kitItems(int need, RegistryAccess ra) {
		int lvl = 2 + need; // poziom zaklęć: VIP 3, SVIP 4, MVIP 5, GVIP 6, DVIP 7
		boolean nether = need >= 3;
		List<ItemStack> items = new ArrayList<>();
		if (need == 3) {
			// Kit MVIP wg zrzutu: zbroja, miecz, kilof i buława (netheryt), mięso, ramki, jabłka, serce, zmianki.
			items.add(gear(ra, Items.NETHERITE_HELMET, lvl, Enchantments.UNBREAKING, Enchantments.PROTECTION));
			items.add(gear(ra, Items.NETHERITE_CHESTPLATE, lvl, Enchantments.UNBREAKING, Enchantments.PROTECTION));
			items.add(gear(ra, Items.NETHERITE_LEGGINGS, lvl, Enchantments.UNBREAKING, Enchantments.PROTECTION));
			items.add(gear(ra, Items.NETHERITE_BOOTS, lvl, Enchantments.UNBREAKING, Enchantments.PROTECTION));
			ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
			ench(ra, sword, Enchantments.SHARPNESS, 7);
			ench(ra, sword, Enchantments.UNBREAKING, 7);
			ench(ra, sword, Enchantments.FIRE_ASPECT, 3);
			ench(ra, sword, Enchantments.SWEEPING_EDGE, 4);
			items.add(sword);
			items.add(gear(ra, Items.NETHERITE_PICKAXE, lvl, Enchantments.UNBREAKING, Enchantments.EFFICIENCY));
			items.add(new ItemStack(Items.COOKED_BEEF, 64));
			items.add(new ItemStack(Items.COOKED_PORKCHOP, 64));
			items.add(new ItemStack(Items.COOKED_MUTTON, 64));
			items.add(new ItemStack(Items.ITEM_FRAME, 64));
			items.add(new ItemStack(Items.GOLDEN_APPLE, 32));
			items.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 3));
			items.add(new ItemStack(ModItems.SERCE_ZYCIA, 1));
			items.add(new ItemStack(ModItems.ZMIANKI, 5));
			items.add(gear(ra, Items.MACE, lvl, Enchantments.UNBREAKING));
			return items;
		}
		items.add(gear(ra, nether ? Items.NETHERITE_HELMET : Items.DIAMOND_HELMET, lvl, Enchantments.UNBREAKING, Enchantments.PROTECTION));
		items.add(gear(ra, nether ? Items.NETHERITE_CHESTPLATE : Items.DIAMOND_CHESTPLATE, lvl, Enchantments.UNBREAKING, Enchantments.PROTECTION));
		items.add(gear(ra, nether ? Items.NETHERITE_LEGGINGS : Items.DIAMOND_LEGGINGS, lvl, Enchantments.UNBREAKING, Enchantments.PROTECTION));
		items.add(gear(ra, nether ? Items.NETHERITE_BOOTS : Items.DIAMOND_BOOTS, lvl, Enchantments.UNBREAKING, Enchantments.PROTECTION));
		items.add(gear(ra, nether ? Items.NETHERITE_SHOVEL : Items.DIAMOND_SHOVEL, lvl, Enchantments.UNBREAKING, Enchantments.EFFICIENCY));
		items.add(gear(ra, nether ? Items.NETHERITE_PICKAXE : Items.DIAMOND_PICKAXE, lvl, Enchantments.UNBREAKING, Enchantments.EFFICIENCY));
		items.add(gear(ra, nether ? Items.NETHERITE_AXE : Items.DIAMOND_AXE, lvl, Enchantments.UNBREAKING, Enchantments.EFFICIENCY));
		if (need >= 2) {
			items.add(gear(ra, nether ? Items.NETHERITE_HOE : Items.DIAMOND_HOE, lvl, Enchantments.UNBREAKING, Enchantments.EFFICIENCY));
		}
		items.add(gear(ra, nether ? Items.NETHERITE_SWORD : Items.DIAMOND_SWORD, lvl, Enchantments.UNBREAKING, Enchantments.SHARPNESS));
		items.add(new ItemStack(Items.GOLDEN_APPLE, need == 1 ? 5 : 15));
		if (need >= 2) {
			items.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, need - 1));
		}
		items.add(new ItemStack(Items.WATER_BUCKET));
		items.add(new ItemStack(Items.LAVA_BUCKET));
		items.add(new ItemStack(Items.ITEM_FRAME, need == 1 ? 32 : 64));
		items.add(new ItemStack(Items.ENDER_CHEST, need == 1 ? 1 : 4));
		items.add(new ItemStack(Items.COOKED_BEEF, 64));
		if (need >= 2) {
			items.add(new ItemStack(Items.COOKED_PORKCHOP, 64));
			items.add(new ItemStack(ModItems.ZMIANKI, need - 1));
		}
		if (need >= 3) {
			items.add(new ItemStack(ModItems.SERCE_ZYCIA, need >= 5 ? 2 : 1));
		}
		return items;
	}

	private static ItemStack greenButton() {
		ItemStack b = new ItemStack(Items.LIME_DYE);
		b.set(DataComponents.CUSTOM_NAME, Component.literal("Odbierz").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
		return b;
	}

	private static void open(ServerPlayer p, Component title, SimpleContainer box, java.util.function.BiConsumer<ServerPlayer, Integer> click) {
		p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new RangiMenu(id, inv, box, click), title));
	}

	/** /vip ... /dvip: podgląd kitu i zielony przycisk „Odbierz” (raz na 24 h). */
	private static int kit(CommandSourceStack src, int need) {
		ServerPlayer p = src.getPlayer();
		if (p == null) {
			src.sendFailure(Component.literal("Tej komendy może użyć tylko gracz."));
			return 0;
		}
		if (level(p) < need) {
			src.sendFailure(Component.literal("Potrzebujesz rangi ").append(rankName(need)).append(Component.literal(" lub wyższej.")));
			return 0;
		}
		SimpleContainer box = new SimpleContainer(27);
		List<ItemStack> items = kitItems(need, p.level().registryAccess());
		for (int i = 0; i < items.size() && i < 26; i++) {
			box.setItem(i, items.get(i).copy());
		}
		box.setItem(26, greenButton());
		open(p, Component.literal("Kit ").append(rankName(need)), box, (sp, index) -> {
			if (index != 26) {
				return;
			}
			sp.closeContainer();
			long now = System.currentTimeMillis();
			String id = "kit_" + RANKS[need - 1];
			Long last = data(sp).claims.get(id);
			if (last != null && now - last < DAY_MS) {
				sp.sendSystemMessage(Component.literal("Odbierzesz ten kit ponownie za " + left(DAY_MS - (now - last)) + "."));
				return;
			}
			data(sp).claims.put(id, now);
			save();
			for (ItemStack s : kitItems(need, sp.level().registryAccess())) {
				give(sp, s);
			}
			sp.sendSystemMessage(Component.literal("Odebrano kit rangi ").append(rankName(need)).append(Component.literal("!")));
		});
		return 1;
	}

	/** Klucze dla rangi: pary (id skrzyni, liczba). Rzadkie to zielone, epickie fioletowe. */
	private static final String[][][] KEY_REWARDS = {
			{ { "rzadka", "1" } },
			{ { "rzadka", "2" }, { "epicka", "1" } },
			{ { "rzadka", "5" }, { "epicka", "1" }, { "anarchiczna", "1" } },
			{ { "rzadka", "5" }, { "epicka", "3" }, { "anarchiczna", "2" } },
			{ { "rzadka", "5" }, { "epicka", "5" }, { "anarchiczna", "3" }, { "dolarowa", "1" } } };

	/** Klucze rangi; tier 0 = TWÓRCA: po 10 kluczy każdego rodzaju. */
	private static List<ItemStack> keyItems(int tier) {
		List<ItemStack> items = new ArrayList<>();
		if (tier == 0) {
			for (Crates.Crate c : Crates.all()) {
				items.add(new ItemStack(c.key(), 10));
			}
			return items;
		}
		for (String[] r : KEY_REWARDS[tier - 1]) {
			Crates.Crate c = Crates.get(r[0]);
			if (c != null) {
				items.add(new ItemStack(c.key(), Integer.parseInt(r[1])));
			}
		}
		return items;
	}

	private static Component label(int tier) {
		return tier == 0 ? creatorName() : rankName(tier);
	}

	/** Okno z kluczami rangi (tier 0 = TWÓRCA) i zielonym przyciskiem; klik w przycisk lub klucz odbiera komplet (raz na 24 h). */
	private static int keys(CommandSourceStack src, int tier) {
		ServerPlayer p = src.getPlayer();
		if (p == null) {
			src.sendFailure(Component.literal("Tej komendy może użyć tylko gracz."));
			return 0;
		}
		boolean allowed = tier == 0 ? isCreator(p) : level(p) >= tier;
		if (!allowed) {
			src.sendFailure(Component.literal("Potrzebujesz rangi ").append(label(tier)).append(tier == 0 ? Component.literal(".") : Component.literal(" lub wyższej.")));
			return 0;
		}
		SimpleContainer box = new SimpleContainer(27);
		List<ItemStack> items = keyItems(tier);
		for (int i = 0; i < items.size() && i < 26; i++) {
			box.setItem(i, items.get(i).copy());
		}
		box.setItem(26, greenButton());
		final String claimId = "klucze_" + (tier == 0 ? TWORCA : RANKS[tier - 1]);
		open(p, Component.literal("Klucze ").append(label(tier)), box, (sp, index) -> {
			sp.closeContainer();
			long now = System.currentTimeMillis();
			Long last = data(sp).claims.get(claimId);
			if (last != null && now - last < DAY_MS) {
				sp.sendSystemMessage(Component.literal("Klucze odbierzesz ponownie za " + left(DAY_MS - (now - last)) + "."));
				return;
			}
			data(sp).claims.put(claimId, now);
			save();
			for (ItemStack s : keyItems(tier)) {
				give(sp, s);
			}
			sp.sendSystemMessage(Component.literal("Odebrano klucze rangi ").append(label(tier)).append(Component.literal("!")));
		});
		return 1;
	}

	/** Numer rangi z nazwy: 1-5 = vip..dvip, 0 = twórca, -1 = nieznana. */
	private static int tierOf(String name) {
		String n = name.toLowerCase(Locale.ROOT).replace('ó', 'o');
		if (n.equals(TWORCA)) {
			return 0;
		}
		for (int i = 0; i < RANKS.length; i++) {
			if (RANKS[i].equals(n)) {
				return i + 1;
			}
		}
		return -1;
	}

	/** /klucze: okno kluczy twojej (najwyższej) rangi. */
	private static int keysOwn(CommandSourceStack src) {
		ServerPlayer p = src.getPlayer();
		if (p == null) {
			src.sendFailure(Component.literal("Tej komendy może użyć tylko gracz."));
			return 0;
		}
		int lvl = level(p);
		if (lvl > 0) {
			return keys(src, lvl);
		}
		if (isCreator(p)) {
			return keys(src, 0);
		}
		src.sendFailure(Component.literal("Nie masz żadnej rangi. Dostępne rangi: VIP, SVIP, MVIP, GVIP, DVIP, TWÓRCA."));
		return 0;
	}

	/** /klucze [ranga]: okno kluczy wskazanej rangi (musisz ją mieć lub wyższą). */
	private static int keysRank(CommandSourceStack src, String name) {
		int tier = tierOf(name);
		if (tier < 0) {
			src.sendFailure(Component.literal("Nieznana ranga. Dostępne: vip, svip, mvip, gvip, dvip, tworca."));
			return 0;
		}
		return keys(src, tier);
	}

	private static int info(CommandSourceStack src) {
		ServerPlayer p = src.getPlayer();
		if (p == null) {
			src.sendFailure(Component.literal("Tej komendy może użyć tylko gracz."));
			return 0;
		}
		int lvl = level(p);
		Component msg = Component.literal("Twoja ranga: ");
		if (lvl > 0) {
			msg = msg.copy().append(rankName(lvl));
		} else if (isCreator(p)) {
			msg = msg.copy().append(creatorName());
		} else {
			msg = msg.copy().append(Component.literal("brak").withStyle(ChatFormatting.GRAY));
		}
		p.sendSystemMessage(msg);
		Component all = Component.literal("Rangi: ");
		for (int i = 1; i <= RANKS.length; i++) {
			all = all.copy().append(Component.literal("[").withStyle(ChatFormatting.GRAY)).append(rankName(i))
					.append(Component.literal("] ").withStyle(ChatFormatting.GRAY));
		}
		all = all.copy().append(Component.literal("[").withStyle(ChatFormatting.GRAY)).append(creatorName())
				.append(Component.literal("]").withStyle(ChatFormatting.GRAY));
		p.sendSystemMessage(all);
		p.sendSystemMessage(Component.literal("Komendy: /vip /svip /mvip /gvip /dvip (kit na 24 h), /klucze oraz /klucze [ranga] (klucze na 24 h)."));
		return 1;
	}

	private static Item findItem(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		if (n.contains(":")) {
			n = n.substring(n.indexOf(':') + 1);
		}
		for (Item item : BuiltInRegistries.ITEM) {
			if (BuiltInRegistries.ITEM.getKey(item).getPath().equals(n)) {
				return item;
			}
		}
		return null;
	}


	/**
	 * Aktywacja rangi z przedmiotu. Wywoływane wyłącznie po stronie serwera
	 * przez RangaItem po kliknięciu PPM.
	 */
	public static boolean activateFromItem(ServerPlayer player, String rank) {
		String r = rank.toLowerCase(Locale.ROOT).replace('ó', 'o');
		boolean valid = r.equals(TWORCA);
		for (String s : RANKS) {
			valid |= s.equals(r);
		}
		if (!valid) {
			player.sendSystemMessage(Component.literal("Ten przedmiot nie zawiera poprawnej rangi."));
			return false;
		}

		Data playerData = data(player);
		if (r.equals(playerData.rank)) {
			player.sendSystemMessage(Component.literal("Masz już aktywną rangę " + r.toUpperCase(Locale.ROOT) + "."));
			return false;
		}

		playerData.rank = r;
		save();
		applyTeam(player.getServer(), player);

		Component rankLabel = r.equals(TWORCA) ? creatorName() : rankName(rankLevel(r));
		player.sendSystemMessage(Component.literal("Pomyślnie aktywowano rangę ").append(rankLabel).append(Component.literal("!")));
		return true;
	}

	private static int rankLevel(String rank) {
		for (int i = 0; i < RANKS.length; i++) {
			if (RANKS[i].equals(rank)) {
				return i + 1;
			}
		}
		return 0;
	}

	private static int setRank(CommandSourceStack src, String playerName, String rank) {
		if (!isAdmin(src)) {
			src.sendFailure(Component.literal("Tylko administrator może użyć tej komendy."));
			return 0;
		}
		ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(playerName);
		if (target == null) {
			src.sendFailure(Component.literal("Nie ma takiego gracza online: " + playerName));
			return 0;
		}
		String r = rank.toLowerCase(Locale.ROOT).replace('ó', 'o');
		boolean valid = r.equals("brak") || r.equals(TWORCA);
		for (String s : RANKS) {
			valid |= s.equals(r);
		}
		if (!valid) {
			src.sendFailure(Component.literal("Nieznana ranga. Dostępne: vip, svip, mvip, gvip, dvip, tworca, brak."));
			return 0;
		}
		data(target).rank = r.equals("brak") ? "" : r;
		save();
		applyTeam(src.getServer(), target);
		src.sendSuccess(() -> Component.literal("Ustawiono rangę gracza " + playerName + ": " + r), true);
		target.sendSystemMessage(Component.literal("Twoja ranga to teraz: " + r));
		return 1;
	}

	private static int gift(CommandSourceStack src, String playerName, String itemName, int count) {
		if (!isAdmin(src)) {
			src.sendFailure(Component.literal("Tylko administrator może użyć tej komendy."));
			return 0;
		}
		ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(playerName);
		if (target == null) {
			src.sendFailure(Component.literal("Nie ma takiego gracza online: " + playerName));
			return 0;
		}
		Item item = findItem(itemName);
		if (item == null) {
			src.sendFailure(Component.literal("Nie znaleziono przedmiotu: " + itemName + " (np. serce_zycia, ksiega_mima, klucz_rzadka)"));
			return 0;
		}
		give(target, new ItemStack(item, count));
		src.sendSuccess(() -> Component.literal("Dano " + count + "x " + itemName + " graczowi " + playerName), true);
		target.sendSystemMessage(Component.literal("Dostałeś prezent: " + count + "x " + itemName + "!"));
		return 1;
	}

	private static void register(CommandDispatcher<CommandSourceStack> d) {
		for (int i = 0; i < RANKS.length; i++) {
			final int need = i + 1;
			d.register(Commands.literal(RANKS[i]).executes(ctx -> kit(ctx.getSource(), need)));
		}
		d.register(Commands.literal("klucze")
				.executes(ctx -> keysOwn(ctx.getSource()))
				.then(Commands.argument("ranga", StringArgumentType.word())
						.executes(ctx -> keysRank(ctx.getSource(), StringArgumentType.getString(ctx, "ranga")))));
		for (int i = 0; i < RANKS.length; i++) {
			final int tier = i + 1;
			d.register(Commands.literal("klucze" + RANKS[i]).executes(ctx -> keys(ctx.getSource(), tier)));
		}
		d.register(Commands.literal("ranga").executes(ctx -> info(ctx.getSource())));
		d.register(Commands.literal("dajrange")
				.then(Commands.argument("gracz", StringArgumentType.word())
						.then(Commands.argument("ranga", StringArgumentType.word())
								.executes(ctx -> setRank(ctx.getSource(), StringArgumentType.getString(ctx, "gracz"),
										StringArgumentType.getString(ctx, "ranga"))))));
		d.register(Commands.literal("gift")
				.then(Commands.argument("gracz", StringArgumentType.word())
						.then(Commands.argument("item", StringArgumentType.word())
								.executes(ctx -> gift(ctx.getSource(), StringArgumentType.getString(ctx, "gracz"),
										StringArgumentType.getString(ctx, "item"), 1))
								.then(Commands.argument("ilosc", IntegerArgumentType.integer(1, 64))
										.executes(ctx -> gift(ctx.getSource(), StringArgumentType.getString(ctx, "gracz"),
												StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "ilosc")))))));
	}

	public static void initialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> load());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> applyTeam(server, handler.getPlayer()));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));

		// Czat: [RANGA] Nick: wiadomość (tylko gracze z rangą; reszta jak w grze).
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			int lvl = level(sender);
			boolean creator = lvl == 0 && isCreator(sender);
			if (lvl == 0 && !creator) {
				return true;
			}
			Component line = Component.literal("[").withStyle(ChatFormatting.GRAY)
					.append(creator ? creatorName() : rankName(lvl))
					.append(Component.literal("] ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(sender.getName().getString()).withStyle(Style.EMPTY.withColor(creator ? RGB_TWORCA : RGB[lvl - 1])))
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(message.decoratedContent().getString()).withStyle(ChatFormatting.WHITE));
			sender.level().getServer().getPlayerList().broadcastSystemMessage(line, false);
			return false;
		});
	}
}
