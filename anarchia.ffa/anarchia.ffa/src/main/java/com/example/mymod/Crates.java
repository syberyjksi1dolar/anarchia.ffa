package com.example.mymod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/** Definicje wszystkich skrzyń: nazwa, klucz i lista nagród (równe szanse dla każdej pozycji). */
public final class Crates {
	/** Jedna pozycja puli: co dostajesz (może być kilka przedmiotów) i ikona w podglądzie. */
	public record Entry(Function<RegistryAccess, List<ItemStack>> give, Function<RegistryAccess, ItemStack> icon) {
	}

	public record Crate(String id, String name, Item key, List<Entry> entries) {
	}

	private static final Map<String, Crate> CRATES = new LinkedHashMap<>();

	private Crates() {
	}

	public static Crate get(String id) {
		return CRATES.get(id);
	}

	public static Iterable<Crate> all() {
		return CRATES.values();
	}

	// ---------- pomocnicze ----------
	private static ItemStack ench(RegistryAccess ra, ItemStack stack, ResourceKey<Enchantment> key, int level) {
		var holder = ra.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
		stack.enchant(holder, level);
		return stack;
	}

	private static Entry simple(Item item, int count) {
		return new Entry(ra -> List.of(new ItemStack(item, count)), ra -> new ItemStack(item, count));
	}

	private static Entry custom(Function<RegistryAccess, ItemStack> make) {
		return new Entry(ra -> List.of(make.apply(ra)), make);
	}

	private static ItemStack enchanted(RegistryAccess ra, Item item, Object... pairs) {
		ItemStack s = new ItemStack(item);
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			@SuppressWarnings("unchecked")
			ResourceKey<Enchantment> key = (ResourceKey<Enchantment>) pairs[i];
			ench(ra, s, key, (Integer) pairs[i + 1]);
		}
		return s;
	}

	private static ItemStack armor(RegistryAccess ra, Item piece) {
		ItemStack s = new ItemStack(piece);
		ench(ra, s, Enchantments.UNBREAKING, 1);
		ench(ra, s, Enchantments.PROTECTION, 2);
		return s;
	}

	private static ItemStack tool(RegistryAccess ra, Item item) {
		ItemStack s = new ItemStack(item);
		ench(ra, s, Enchantments.UNBREAKING, 2);
		ench(ra, s, Enchantments.EFFICIENCY, 3);
		return s;
	}

	/** Złowieszczy sztandar (Raid.getOminousBannerInstance przez refleksję; w razie braku - biały sztandar z nazwą). */
	private static ItemStack ominousBanner(RegistryAccess ra, int count) {
		ItemStack s = null;
		try {
			Class<?> raid = Class.forName("net.minecraft.world.entity.raid.Raid");
			for (var m : raid.getMethods()) {
				if (m.getName().equals("getOminousBannerInstance") && m.getParameterCount() == 1
						&& java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
					Object r = m.invoke(null, ra.lookupOrThrow(Registries.BANNER_PATTERN));
					if (r instanceof ItemStack is) {
						s = is.copy();
					}
					break;
				}
			}
		} catch (Throwable t) {
			// użyjemy zwykłego sztandaru
		}
		if (s == null) {
			s = new ItemStack(Items.WHITE_BANNER);
			s.set(DataComponents.CUSTOM_NAME, Component.literal("Złowieszczy sztandar"));
		}
		s.setCount(count);
		return s;
	}

	private static void add(String id, String name, Item key, List<Entry> entries) {
		CRATES.put(id, new Crate(id, name, key, entries));
	}

	static {
		// ---------- RZADKA (równe szanse) - według screena od właściciela ----------
		List<Entry> rare = new ArrayList<>();
		rare.add(simple(Items.ENDER_PEARL, 2));
		rare.add(simple(Items.LAPIS_LAZULI, 64));
		rare.add(simple(Items.OBSIDIAN, 64));
		rare.add(simple(Items.DIAMOND, 64));
		rare.add(simple(Items.EMERALD, 64));
		rare.add(custom(ra -> armor(ra, Items.DIAMOND_HELMET)));
		rare.add(custom(ra -> armor(ra, Items.DIAMOND_CHESTPLATE)));
		rare.add(custom(ra -> armor(ra, Items.DIAMOND_LEGGINGS)));
		rare.add(custom(ra -> armor(ra, Items.DIAMOND_BOOTS)));
		rare.add(custom(ra -> {
			ItemStack s = new ItemStack(Items.DIAMOND_SWORD);
			ench(ra, s, Enchantments.SWEEPING_EDGE, 1);
			ench(ra, s, Enchantments.SHARPNESS, 3);
			ench(ra, s, Enchantments.UNBREAKING, 1);
			return s;
		}));
		rare.add(custom(ra -> ominousBanner(ra, 16)));
		rare.add(custom(ra -> tool(ra, Items.DIAMOND_PICKAXE)));
		rare.add(custom(ra -> tool(ra, Items.DIAMOND_AXE)));
		rare.add(custom(ra -> PotionContents.createItemStack(Items.POTION, Potions.LONG_STRENGTH))); // Siła 8:00
		rare.add(simple(Items.CHORUS_FRUIT, 3));
		rare.add(simple(Items.GOLDEN_APPLE, 8));
		rare.add(simple(Items.ENCHANTED_GOLDEN_APPLE, 1));
		rare.add(simple(Items.FIRE_CHARGE, 64));
		rare.add(simple(Items.OMINOUS_TRIAL_KEY, 1));
		rare.add(simple(Items.TRIAL_KEY, 4));
		add("rzadka", "Rzadka Skrzynia", ModItems.KLUCZ_RZADKA, rare);

		// ---------- Pozostałe skrzynie: nagrody czekają na listę od właściciela ----------
		// ---------- EPICKA (równe szanse) - według screena od właściciela ----------
		List<Entry> epic = new ArrayList<>();
		for (Item p : new Item[] { Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS }) {
			epic.add(custom(ra -> enchanted(ra, p, Enchantments.UNBREAKING, 3, Enchantments.PROTECTION, 3)));
		}
		for (Item p : new Item[] { Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS }) {
			epic.add(custom(ra -> enchanted(ra, p, Enchantments.UNBREAKING, 5, Enchantments.PROTECTION, 5)));
		}
		epic.add(simple(Items.TOTEM_OF_UNDYING, 1));
		epic.add(custom(ra -> enchanted(ra, Items.NETHERITE_SWORD, Enchantments.FIRE_ASPECT, 3, Enchantments.UNBREAKING, 10,
				Enchantments.SHARPNESS, 7)));
		epic.add(custom(ra -> enchanted(ra, Items.NETHERITE_PICKAXE, Enchantments.EFFICIENCY, 10, Enchantments.UNBREAKING, 10)));
		epic.add(simple(Items.GOLDEN_APPLE, 64));
		epic.add(simple(Items.ENCHANTED_GOLDEN_APPLE, 4));
		epic.add(simple(Items.ENDER_PEARL, 16));
		epic.add(simple(Items.OBSIDIAN, 64));
		epic.add(custom(ra -> PotionContents.createItemStack(Items.POTION, Potions.LONG_FIRE_RESISTANCE)));
		epic.add(simple(Items.COBWEB, 64));
		epic.add(simple(ModItems.BOMBA_MAKSIMA, 2));
		epic.add(simple(ModItems.TURBO_TRAP, 1));
		epic.add(simple(ModItems.KREW_WAMPIRA, 1));
		epic.add(simple(Items.FIREWORK_ROCKET, 64));
		epic.add(simple(Items.WIND_CHARGE, 32));
		epic.add(simple(Items.ARROW, 32));
		epic.add(custom(ra -> enchanted(ra, Items.BOW, Enchantments.FLAME, 1, Enchantments.POWER, 5, Enchantments.UNBREAKING, 3)));
		epic.add(simple(Items.NETHERITE_INGOT, 4));
		epic.add(simple(Items.MACE, 1));
		add("epicka", "Epicka Skrzynia", ModItems.KLUCZ_EPICKA, epic);
		// ---------- ANARCHICZNA (równe szanse) - według screena od właściciela ----------
		List<Entry> anar = new ArrayList<>();
		for (Item p : new Item[] { ModItems.ANARCHICZNA_ZBROJA_2_HELM, ModItems.ANARCHICZNA_ZBROJA_2_KLATA,
				ModItems.ANARCHICZNA_ZBROJA_2_SPODNIE, ModItems.ANARCHICZNA_ZBROJA_2_BUTY }) {
			anar.add(custom(ra -> enchanted(ra, p, Enchantments.UNBREAKING, 10, Enchantments.PROTECTION, 10)));
		}
		for (Item p : new Item[] { Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS }) {
			anar.add(custom(ra -> enchanted(ra, p, Enchantments.UNBREAKING, 5, Enchantments.PROTECTION, 5)));
		}
		anar.add(simple(ModItems.BOMBA_MAKSIMA, 64));
		anar.add(simple(ModItems.BOMBA_MAKSIMA, 32));
		anar.add(simple(ModItems.PIEKIELNY_MIECZ, 1));
		anar.add(simple(ModItems.SMOCZY_MIECZ, 1));
		anar.add(simple(Items.GOLDEN_APPLE, 32));
		anar.add(simple(Items.ENCHANTED_GOLDEN_APPLE, 64));
		anar.add(simple(Items.CHORUS_FRUIT, 32));
		anar.add(custom(ra -> PotionContents.createItemStack(Items.POTION, Potions.FIRE_RESISTANCE)));
		anar.add(custom(ra -> PotionContents.createItemStack(Items.POTION, Potions.LONG_FIRE_RESISTANCE)));
		anar.add(simple(Items.OMINOUS_BOTTLE, 1));
		anar.add(simple(Items.MACE, 1));
		anar.add(simple(Items.TOTEM_OF_UNDYING, 1));
		anar.add(simple(Items.ENDER_PEARL, 16));
		anar.add(simple(Items.NETHERITE_INGOT, 16));
		anar.add(simple(Items.OBSIDIAN, 64));
		anar.add(simple(Items.COBWEB, 64));
		anar.add(simple(Items.BEDROCK, 1));
		anar.add(simple(Items.OMINOUS_TRIAL_KEY, 16));
		anar.add(custom(ra -> PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING)));
		anar.add(custom(ra -> enchanted(ra, Items.NETHERITE_PICKAXE, Enchantments.EFFICIENCY, 10, Enchantments.UNBREAKING, 10)));
		anar.add(simple(ModItems.TURBO_TRAP, 1));
		anar.add(simple(ModItems.BLOK_WIDMO, 32));
		anar.add(simple(ModItems.CIEPLE_MLEKO, 1));
		anar.add(simple(ModItems.KOSTKA_RUBIKA, 1));
		anar.add(custom(ra -> enchanted(ra, Items.NETHERITE_SWORD, Enchantments.FIRE_ASPECT, 3, Enchantments.UNBREAKING, 10,
				Enchantments.SHARPNESS, 7)));
		add("anarchiczna", "Anarchiczna Skrzynia", ModItems.KLUCZ_ANARCHICZNA, anar);
		// Pety i kostiumy, które już są w modzie (równe szanse).
		List<Entry> pets = new ArrayList<>();
		pets.add(simple(ModItems.PET_BALWANEK, 1));
		add("pety", "Skrzynia Petów", ModItems.KLUCZ_PETY, pets);
		List<Entry> costumes = new ArrayList<>();
		costumes.add(simple(ModItems.SILACZ_KLATA, 1));
		costumes.add(simple(ModItems.DOLAR_KLATA, 1));
		add("kostiumy", "Skrzynia Kostiumów", ModItems.KLUCZ_KOSTIUMY, costumes);
		// ---------- DOLAROWA (równe szanse) - według screena od właściciela ----------
		List<Entry> dol = new ArrayList<>();
		dol.add(simple(ModItems.DOLAR_KLATA, 1));
		dol.add(simple(ModItems.TOTEM_ULASKAWIENIA, 1));
		dol.add(simple(ModItems.EXCALIBUR, 1));
		dol.add(simple(ModItems.CHYDRO_KLATKA, 1));
		dol.add(simple(ModItems.GRINCH_AXE, 1));
		dol.add(simple(ModItems.SAKIEWKA, 1));
		dol.add(simple(ModItems.KREW_WAMPIRA, 16));
		dol.add(simple(ModItems.BOMBA_MAKSIMA, 64));
		dol.add(custom(ra -> enchanted(ra, Items.NETHERITE_SWORD, Enchantments.FIRE_ASPECT, 3, Enchantments.UNBREAKING, 10,
				Enchantments.SHARPNESS, 7)));
		dol.add(custom(ra -> enchanted(ra, Items.NETHERITE_PICKAXE, Enchantments.EFFICIENCY, 10, Enchantments.UNBREAKING, 10)));
		for (Item p : new Item[] { ModItems.ZBROJA_DOLARA_HELM, ModItems.ZBROJA_DOLARA_KLATA, ModItems.ZBROJA_DOLARA_SPODNIE,
				ModItems.ZBROJA_DOLARA_BUTY }) {
			dol.add(custom(ra -> enchanted(ra, p, Enchantments.UNBREAKING, 10, Enchantments.PROTECTION, 10)));
		}
		dol.add(simple(Items.EMERALD, 64));
		dol.add(custom(ra -> PotionContents.createItemStack(Items.POTION, Potions.LONG_FIRE_RESISTANCE)));
		dol.add(simple(Items.GOLDEN_APPLE, 64));
		dol.add(simple(Items.ENCHANTED_GOLDEN_APPLE, 64));
		dol.add(simple(Items.OBSIDIAN, 64));
		dol.add(simple(Items.COBWEB, 64));
		dol.add(simple(Items.BEDROCK, 1));
		dol.add(simple(Items.MACE, 1));
		dol.add(simple(Items.TOTEM_OF_UNDYING, 1));
		dol.add(simple(Items.CHORUS_FRUIT, 64));
		dol.add(simple(Items.END_CRYSTAL, 64));
		dol.add(simple(Items.GLOWSTONE, 64));
		dol.add(simple(Items.RESPAWN_ANCHOR, 64));
		dol.add(simple(ModItems.LIZAK, 1));
		dol.add(simple(ModItems.BLOK_WIDMO, 1));
		dol.add(simple(ModItems.ZATOPIONY_STATEK, 1));
		dol.add(simple(ModItems.CUDOWNA_LATARNIA, 1));
		dol.add(simple(ModItems.BOSKI_TOPOR, 1));
		add("dolarowa", "Dolarowa Skrzynia", ModItems.KLUCZ_DOLAROWA, dol);
		// ---------- EVENTOWA (równe szanse) - według screena od właściciela ----------
		List<Entry> ev = new ArrayList<>();
		ev.add(simple(ModItems.CHYDRO_KLATKA, 1));
		ev.add(simple(ModItems.GRINCH_AXE, 1));
		ev.add(simple(ModItems.WODNY_TROJZAB, 1));
		ev.add(simple(ModItems.ANARCHIA_KORONA, 1));
		ev.add(simple(ModItems.ZATOPIONY_STATEK, 1));
		ev.add(simple(ModItems.PLATEK_SNIEGU, 64));
		ev.add(simple(ModItems.CZERWONY_BALON, 64));
		ev.add(simple(ModItems.PARASOL, 64));
		ev.add(simple(ModItems.SERCE, 64));
		ev.add(simple(ModItems.BLOK_WIDMO, 1));
		ev.add(simple(ModItems.EXCALIBUR, 1));
		ev.add(simple(ModItems.TOTEM_ULASKAWIENIA, 1));
		ev.add(simple(ModItems.ROZDZKA_ILUZJONISTY, 1));
		ev.add(simple(ModItems.WZMOCNIONA_ELYTRA, 1));
		ev.add(simple(ModItems.SAKIEWKA, 1));
		ev.add(simple(ModItems.KREW_WAMPIRA, 16));
		ev.add(simple(ModItems.CUDOWNA_LATARNIA, 1));
		ev.add(simple(ModItems.BOMBA_MAKSIMA, 64));
		ev.add(simple(ModItems.WAMPIRZE_JABLKO, 64));
		ev.add(simple(ModItems.WEDKA_NIELOTA, 1));
		ev.add(simple(ModItems.JAJKO_ZMUTOWANEGO_CREEPERA, 64));
		ev.add(simple(ModItems.ROZGA, 1));
		ev.add(simple(ModItems.SMOCZY_MIECZ, 1));
		ev.add(simple(ModItems.ROG_JEDNOROZCA, 1));
		ev.add(simple(ModItems.KOSA, 1));
		ev.add(simple(ModItems.KOSTKA_RUBIKA, 1));
		ev.add(simple(ModItems.ROZA_KUPIDYNA, 1));
		ev.add(simple(ModItems.CIEPLE_MLEKO, 1));
		ev.add(simple(ModItems.LIZAK, 1));
		ev.add(simple(ModItems.PIEKIELNY_MIECZ, 1));
		ev.add(simple(ModItems.WATA_CUKROWA, 64));
		ev.add(simple(ModItems.KROLICZY_MIECZ, 1));
		ev.add(simple(ModItems.LOPATA_GRINCHA, 1));
		ev.add(simple(ModItems.ZATRUTY_OLOWEK, 1));
		ev.add(simple(ModItems.WEDECZKA_PERELECZKA, 1));
		ev.add(simple(ModItems.TURBO_TRAP, 1));
		ev.add(simple(ModItems.ARCUS_MAGNUS, 1));
		ev.add(simple(ModItems.MARCHEWKOWY_MIECZ, 1));
		ev.add(simple(ModItems.MARCHEWKOWA_KUSZA, 1));
		ev.add(simple(ModItems.BALONIK_Z_HELEM, 1));
		ev.add(simple(ModItems.PIERNIK, 1));
		ev.add(simple(ModItems.ZATRUTY_TRUNEK, 16));
		ev.add(simple(ModItems.SPLESNIALA_KANAPKA, 1));
		ev.add(simple(ModItems.ANARCHICZNY_KILOF, 1));
		ev.add(simple(ModItems.ROZGOTOWANA_KUKURYDZA, 16));
		add("eventowa", "Eventowa Skrzynia", ModItems.KLUCZ_EVENTOWA, ev);
		// ---------- ŚWIĄTECZNA (równe szanse) - według screena od właściciela ----------
		List<Entry> sw = new ArrayList<>();
		sw.add(simple(ModItems.PLATEK_SNIEGU, 64));
		sw.add(simple(ModItems.CUDOWNA_LATARNIA, 1));
		sw.add(simple(ModItems.SAKIEWKA, 1));
		sw.add(simple(ModItems.CIEPLE_MLEKO, 1));
		for (Item p : new Item[] { ModItems.ANARCHICZNA_ZBROJA_2_HELM, ModItems.ANARCHICZNA_ZBROJA_2_KLATA,
				ModItems.ANARCHICZNA_ZBROJA_2_SPODNIE, ModItems.ANARCHICZNA_ZBROJA_2_BUTY }) {
			sw.add(custom(ra -> enchanted(ra, p, Enchantments.UNBREAKING, 10, Enchantments.PROTECTION, 10)));
		}
		for (Item p : new Item[] { Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS }) {
			sw.add(custom(ra -> enchanted(ra, p, Enchantments.UNBREAKING, 5, Enchantments.PROTECTION, 5)));
		}
		sw.add(simple(Items.WIND_CHARGE, 64));
		sw.add(simple(Items.TOTEM_OF_UNDYING, 1));
		sw.add(simple(Items.ENDER_PEARL, 16));
		sw.add(simple(Items.MACE, 1));
		sw.add(simple(Items.COBWEB, 64));
		sw.add(simple(Items.OBSIDIAN, 64));
		sw.add(simple(ModItems.PIERNIK, 1));
		sw.add(simple(ModItems.KULKA_BALWANA, 32));
		sw.add(simple(Items.GOLDEN_APPLE, 64));
		sw.add(simple(Items.ENCHANTED_GOLDEN_APPLE, 32));
		sw.add(simple(Items.ENCHANTED_GOLDEN_APPLE, 16));
		sw.add(custom(ra -> enchanted(ra, Items.NETHERITE_SWORD, Enchantments.FIRE_ASPECT, 3, Enchantments.UNBREAKING, 10,
				Enchantments.SHARPNESS, 7)));
		sw.add(custom(ra -> enchanted(ra, Items.NETHERITE_PICKAXE, Enchantments.EFFICIENCY, 10, Enchantments.UNBREAKING, 10)));
		sw.add(simple(ModItems.CHYDRO_KLATKA, 1));
		sw.add(simple(ModItems.ROZGA, 1));
		add("swiateczna", "Świąteczna Skrzynia", ModItems.KLUCZ_SWIATECZNA, sw);
		add("walentynkowa", "Walentynkowa Skrzynia", ModItems.KLUCZ_WALENTYNKOWA, new ArrayList<>());
		// ---------- HALLOWEENOWA (równe szanse) - według screena od właściciela ----------
		List<Entry> hw = new ArrayList<>();
		hw.add(simple(ModItems.TOTEM_ULASKAWIENIA, 1));
		hw.add(simple(ModItems.EXCALIBUR, 1));
		hw.add(simple(ModItems.BLOK_WIDMO, 1));
		hw.add(simple(ModItems.BOMBA_MAKSIMA, 64));
		hw.add(simple(Items.ENDER_PEARL, 16));
		hw.add(simple(ModItems.LUK_KUPIDYNA, 1));
		hw.add(simple(ModItems.ARCUS_MAGNUS, 1));
		hw.add(simple(ModItems.KOSA, 1));
		hw.add(simple(Items.TOTEM_OF_UNDYING, 1));
		for (Item p : new Item[] { ModItems.ANARCHICZNA_ZBROJA_2_HELM, ModItems.ANARCHICZNA_ZBROJA_2_KLATA,
				ModItems.ANARCHICZNA_ZBROJA_2_SPODNIE, ModItems.ANARCHICZNA_ZBROJA_2_BUTY }) {
			hw.add(custom(ra -> enchanted(ra, p, Enchantments.UNBREAKING, 10, Enchantments.PROTECTION, 10)));
		}
		for (Item p : new Item[] { Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS }) {
			hw.add(custom(ra -> enchanted(ra, p, Enchantments.UNBREAKING, 5, Enchantments.PROTECTION, 5)));
		}
		hw.add(simple(Items.GOLDEN_APPLE, 64));
		hw.add(custom(ra -> PotionContents.createItemStack(Items.POTION, Potions.LONG_FIRE_RESISTANCE)));
		hw.add(simple(Items.ENCHANTED_GOLDEN_APPLE, 64));
		hw.add(simple(Items.ENCHANTED_GOLDEN_APPLE, 32));
		hw.add(simple(Items.RESPAWN_ANCHOR, 64));
		hw.add(simple(Items.END_CRYSTAL, 64));
		hw.add(simple(Items.COBWEB, 64));
		hw.add(simple(Items.OBSIDIAN, 64));
		hw.add(custom(ra -> enchanted(ra, Items.NETHERITE_PICKAXE, Enchantments.EFFICIENCY, 10, Enchantments.UNBREAKING, 10)));
		hw.add(simple(Items.GLOWSTONE, 64));
		hw.add(custom(ra -> enchanted(ra, Items.NETHERITE_SWORD, Enchantments.FIRE_ASPECT, 3, Enchantments.UNBREAKING, 10,
				Enchantments.SHARPNESS, 7)));
		add("halloweenowa", "Halloweenowa Skrzynia", ModItems.KLUCZ_HALLOWEENOWA, hw);
		add("szkolna", "Szkolna Skrzynia", ModItems.KLUCZ_SZKOLNA, new ArrayList<>());
		// ---------- NOWE EVENTÓWKI (równe szanse) - według screena od właściciela ----------
		List<Entry> nev = new ArrayList<>();
		nev.add(simple(ModItems.DZIALKO, 64));
		nev.add(simple(ModItems.SHULEK_BOXEK, 1));
		nev.add(simple(ModItems.DYNAMIT, 64));
		nev.add(simple(ModItems.ROG_KOOZY, 1));
		nev.add(simple(ModItems.ROZDZKA_MAGIKA, 1));
		nev.add(simple(ModItems.WEDKA_18, 1));
		nev.add(simple(ModItems.WILK, 16));
		nev.add(simple(ModItems.PERELKA_ZAMIANKA, 16));
		nev.add(simple(ModItems.FAJERWERKA_WYRZUTNI, 1));
		nev.add(simple(ModItems.KULKA_BALWANA, 32));
		nev.add(simple(ModItems.SLIMOWY_WIOSNA, 1));
		add("nowe_eventowki", "Skrzynia Nowych Eventówek", ModItems.KLUCZ_NOWE_EVENTOWKI, nev);
	}
}
