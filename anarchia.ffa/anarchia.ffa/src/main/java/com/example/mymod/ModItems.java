package com.example.mymod;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
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
			props -> new ZatopionyStatekItem(ModBlocks.ZATOPIONY_STATEK, props),
			new Item.Properties().stacksTo(1));

	public static final Item PLATEK_SNIEGU = register("platek_sniegu", Item::new,
			new Item.Properties().stacksTo(64));

	public static final Item CZERWONY_BALON = register("czerwony_balon", CzerwonyBalonItem::new,
			new Item.Properties().stacksTo(64));

	public static final Item PARASOL = register("parasol", ParasolItem::new,
			new Item.Properties().stacksTo(64));

	public static final Item SERCE = register("serce", Item::new,
			new Item.Properties().stacksTo(64));

	public static final Item EXCALIBUR = register("excalibur", Item::new,
			new Item.Properties().stacksTo(1)
					.sword(ToolMaterial.NETHERITE, 3.0F, -2.4F)
					.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

	public static final Item TOTEM_ULASKAWIENIA = register("totem_ulaskawienia", Item::new,
			new Item.Properties().stacksTo(1));

	public static final Item ROZDZKA_ILUZJONISTY = register("rozdzka_iluzjonisty", RozdzkaIluzjonistyItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item BLOK_WIDMO = register("blok_widmo",
			props -> new BlokWidmoItem(ModBlocks.BLOK_WIDMO, props),
			new Item.Properties().stacksTo(1));

	public static final Item WZMOCNIONA_ELYTRA = register("wzmocniona_elytra", Item::new,
			new Item.Properties().stacksTo(1)
					.component(DataComponents.GLIDER, Unit.INSTANCE)
					.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST)
							.setAsset(EquipmentAssets.ELYTRA).setDamageOnHurt(false).build()));

	public static final Item SAKIEWKA = register("sakiewka", SakiewkaItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item KREW_WAMPIRA = register("krew_wampira", KrewWampiraItem::new,
			new Item.Properties().stacksTo(16));

	public static final Item CUDOWNA_LATARNIA = register("cudowna_latarnia",
			props -> new CudownaLatarniaItem(ModBlocks.CUDOWNA_LATARNIA, props),
			new Item.Properties().stacksTo(1));

	public static final Item BOMBA_MAKSIMA = register("bomba_maksima", BombaMaksimaItem::new,
			new Item.Properties().stacksTo(64));

	public static final Item WAMPIRZE_JABLKO = register("wampirze_jablko", WampirzeJablkoItem::new,
			new Item.Properties().stacksTo(64)
					.food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.3F).alwaysEdible().build()));

	// Zwykła wędka bez wytrzymałości (nie niszczy się); działanie w WedkaNielotaManager.
	public static final Item WEDKA_NIELOTA = register("wedka_nielota", FishingRodItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item JAJKO_ZMUTOWANEGO_CREEPERA = register("jajko_zmutowanego_creepera", JajkoCreeperaItem::new,
			new Item.Properties().stacksTo(64));

	public static final Item ROZGA = register("rozga", Item::new, new Item.Properties().stacksTo(1));

	public static final Item SMOCZY_MIECZ = register("smoczy_miecz", SmoczyMieczItem::new,
			new Item.Properties().stacksTo(1)
					.sword(ToolMaterial.NETHERITE, 3.0F, -2.4F)
					.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

	public static final Item ROG_JEDNOROZCA = register("rog_jednorozca", RogJednorozcaItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item KOSA = register("kosa", KosaItem::new, new Item.Properties().stacksTo(1));

	public static final Item KOSTKA_RUBIKA = register("kostka_rubika", KostkaRubikaItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item ROZA_KUPIDYNA = register("roza_kupidyna", Item::new, new Item.Properties().stacksTo(1));

	public static final Item CIEPLE_MLEKO = register("cieple_mleko", CieploMlekoItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item LIZAK = register("lizak", Item::new, new Item.Properties().stacksTo(1));

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
