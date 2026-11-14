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

	private static ItemStack diamond(RegistryAccess ra, Item piece) {
		ItemStack s = new ItemStack(piece);
		ench(ra, s, Enchantments.UNBREAKING, 2);
		ench(ra, s, Enchantments.PROTECTION, 1);
		return s;
	}

	private static void add(String id, String name, Item key, List<Entry> entries) {
		CRATES.put(id, new Crate(id, name, key, entries));
	}

	static {
		// ---------- RZADKA (równe szanse) ----------
		List<Entry> rare = new ArrayList<>();
		rare.add(new Entry(
				ra -> List.of(diamond(ra, Items.DIAMOND_HELMET), diamond(ra, Items.DIAMOND_CHESTPLATE),
						diamond(ra, Items.DIAMOND_LEGGINGS), diamond(ra, Items.DIAMOND_BOOTS)),
				ra -> {
					ItemStack s = diamond(ra, Items.DIAMOND_CHESTPLATE);
					s.set(DataComponents.CUSTOM_NAME, Component.literal("Diamentowy komplet (4 części)"));
					return s;
				}));
		rare.add(custom(ra -> {
			ItemStack s = new ItemStack(Items.DIAMOND_SWORD);
			ench(ra, s, Enchantments.FIRE_ASPECT, 1);
			ench(ra, s, Enchantments.UNBREAKING, 2);
			ench(ra, s, Enchantments.SHARPNESS, 3);
			return s;
		}));
		rare.add(simple(Items.GOLDEN_APPLE, 16));
		rare.add(simple(Items.ENCHANTED_GOLDEN_APPLE, 1));
		rare.add(simple(Items.ENCHANTED_GOLDEN_APPLE, 2));
		rare.add(simple(ModItems.BOMBA_MAKSIMA, 1));
		rare.add(simple(ModItems.SLIMOWY_WIOSNA, 1));
		rare.add(simple(Items.ENDER_PEARL, 1));
		rare.add(simple(Items.OBSIDIAN, 64));
		rare.add(custom(ra -> {
			ItemStack s = new ItemStack(Items.DIAMOND_PICKAXE);
			ench(ra, s, Enchantments.UNBREAKING, 3);
			ench(ra, s, Enchantments.EFFICIENCY, 2);
			return s;
		}));
		rare.add(simple(Items.COBWEB, 32));
		rare.add(simple(Items.RESPAWN_ANCHOR, 8));
		rare.add(simple(Items.GLOWSTONE, 32));
		rare.add(simple(Items.SHULKER_BOX, 1));
		rare.add(simple(Items.WATER_BUCKET, 1));
		rare.add(simple(Items.LAVA_BUCKET, 1));
		rare.add(simple(Items.SHIELD, 1));
		rare.add(simple(Items.TOTEM_OF_UNDYING, 1));
		rare.add(simple(Items.CHORUS_FRUIT, 4));
		rare.add(custom(ra -> PotionContents.createItemStack(Items.POTION, Potions.LONG_STRENGTH))); // Siła 8:00
		rare.add(simple(Items.IRON_GOLEM_SPAWN_EGG, 1));
		rare.add(simple(ModItems.DZIALKO, 1));
		rare.add(simple(ModItems.KULKA_BALWANA, 2));
		add("rzadka", "Rzadka Skrzynia", ModItems.KLUCZ_RZADKA, rare);

		// ---------- Pozostałe skrzynie: nagrody czekają na listę od właściciela ----------
		add("epicka", "Epicka Skrzynia", ModItems.KLUCZ_EPICKA, new ArrayList<>());
		add("anarchiczna", "Anarchiczna Skrzynia", ModItems.KLUCZ_ANARCHICZNA, new ArrayList<>());
		// Pety i kostiumy, które już są w modzie (równe szanse).
		List<Entry> pets = new ArrayList<>();
		pets.add(simple(ModItems.PET_BALWANEK, 1));
		add("pety", "Skrzynia Petów", ModItems.KLUCZ_PETY, pets);
		List<Entry> costumes = new ArrayList<>();
		costumes.add(simple(ModItems.SILACZ_KLATA, 1));
		costumes.add(simple(ModItems.DOLAR_KLATA, 1));
		add("kostiumy", "Skrzynia Kostiumów", ModItems.KLUCZ_KOSTIUMY, costumes);
		add("dolarowa", "Dolarowa Skrzynia", ModItems.KLUCZ_DOLAROWA, new ArrayList<>());
		add("eventowa", "Eventowa Skrzynia", ModItems.KLUCZ_EVENTOWA, new ArrayList<>());
	}
}
