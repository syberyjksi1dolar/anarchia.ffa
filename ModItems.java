package com.example.mymod;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public class ModItems {
	public static final Item CHYDRO_KLATKA = register("chydro_klatka", ChydroKlatkaItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item GRINCH_AXE = register("siekiera_grincha", GrinchAxeItem::new,
			new Item.Properties().stacksTo(1));

	public static <T extends Item> T register(String name, Function<Item.Properties, T> itemFactory, Item.Properties settings) {
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MyMod.MOD_ID, name));
		T item = itemFactory.apply(settings.setId(itemKey));
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		return item;
	}

	public static void initialize() {
		// Wywołanie tej metody ładuje klasę, a razem z nią rejestruje przedmioty.
	}
}
