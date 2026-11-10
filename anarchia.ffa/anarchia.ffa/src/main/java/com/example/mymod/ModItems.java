package com.example.mymod;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

public class ModItems {
	public static final Item CHYDRO_KLATKA = register("chydro_klatka", ChydroKlatkaItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item GRINCH_AXE = register("siekiera_grincha", GrinchAxeItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item WODNY_TROJZAB = register("wodny_trojzab", WodnyTrojzabItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item ZATOPIONY_STATEK = register("zatopiony_statek",
			props -> new BlockItem(ModBlocks.ZATOPIONY_STATEK, props),
			new Item.Properties());

	public static final Item PLATEK_SNIEGU = register("platek_sniegu", Item::new,
			new Item.Properties().stacksTo(1));

	public static final Item CZERWONY_BALON = register("czerwony_balon", CzerwonyBalonItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item PARASOL = register("parasol", ParasolItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item SERCE = register("serce", Item::new,
			new Item.Properties().stacksTo(1));

	/** Model korony noszonej na głowie: assets/mymod/equipment/anarchia_korona.json */
	public static final ResourceKey<EquipmentAsset> KORONA_ASSET = ResourceKey.create(
			EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(MyMod.MOD_ID, "anarchia_korona"));

	// Bez wytrzymałości (durability), więc korona nigdy się nie niszczy. Zakłada się tylko na głowę.
	public static final Item ANARCHIA_KORONA = register("anarchia_korona", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.HEAD).setAsset(KORONA_ASSET).build()));

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
