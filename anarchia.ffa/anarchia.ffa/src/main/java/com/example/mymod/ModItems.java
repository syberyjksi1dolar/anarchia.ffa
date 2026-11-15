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
import net.minecraft.world.item.component.DyedItemColor;
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

	// ===== Eventówki 30-46 =====
	public static final Item PIEKIELNY_MIECZ = register("piekielny_miecz", Item::new,
			new Item.Properties().sword(ToolMaterial.NETHERITE, 3.0F, -2.4F)
					.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

	public static final Item WATA_CUKROWA = register("wata_cukrowa", WataCukrowaItem::new,
			new Item.Properties().stacksTo(64));

	public static final Item KROLICZY_MIECZ = register("kroliczy_miecz", Item::new,
			new Item.Properties().sword(ToolMaterial.NETHERITE, 3.0F, -2.4F)
					.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

	public static final Item TURBO_TRAP = register("turbo_trap", TurboTrapItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item LOPATA_GRINCHA = register("lopata_grincha", Item::new,
			new Item.Properties().shovel(ToolMaterial.NETHERITE, 1.5F, -3.0F));

	public static final Item WEDKA_GWARDIANA = register("wedka_gwardiana", WedkaGwardianaItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item ZATRUTY_OLOWEK = register("zatruty_olowek", Item::new,
			new Item.Properties().stacksTo(1));

	public static final Item ARCUS_MAGNUS = register("arcus_magnus", ArcusMagnusItem::new,
			new Item.Properties().stacksTo(1).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

	public static final Item MARCHEWKOWY_MIECZ = register("marchewkowy_miecz", Item::new,
			new Item.Properties().sword(ToolMaterial.NETHERITE, 3.0F, -2.4F));

	public static final Item MARCHEWKOWA_KUSZA = register("marchewkowa_kusza", net.minecraft.world.item.CrossbowItem::new,
			new Item.Properties().durability(465));

	public static final Item BALONIK_Z_HELEM = register("balonik_z_helem", BalonikZHeliemItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item PIERNIK = register("piernik", PiernikItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item ZATRUTY_TRUNEK = register("zatruty_trunek", ZatrutyTrunekItem::new,
			new Item.Properties().stacksTo(16));

	public static final Item SPLESNIALA_KANAPKA = register("splesniala_kanapka", Item::new,
			new Item.Properties().stacksTo(1));

	public static final Item ANARCHICZNY_KILOF = register("anarchiczny_kilof", Item::new,
			new Item.Properties().pickaxe(ToolMaterial.NETHERITE, 1.0F, -2.8F)
					.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

	public static final Item ROZGOTOWANA_KUKURYDZA = register("rozgotowana_kukurydza", ZatrutyTrunekItem::new,
			new Item.Properties().stacksTo(16));

	public static final Item LUK_KUPIDYNA = register("luk_kupidyna", net.minecraft.world.item.BowItem::new,
			new Item.Properties().durability(384));

	/** Model korony noszonej na głowie: assets/mymod/equipment/anarchia_korona.json */
	public static final ResourceKey<EquipmentAsset> KORONA_ASSET = ResourceKey.create(
			EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(MyMod.MOD_ID, "anarchia_korona"));

	// Bez wytrzymałości (durability), więc korona nigdy się nie niszczy. Zakłada się tylko na głowę.
	public static final Item ANARCHIA_KORONA = register("anarchia_korona", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.HEAD).setAsset(KORONA_ASSET).build()));


	// ===== Kostium Siłacza (komplet 4 części, bez wytrzymałości = niezniszczalny) =====
	public static final ResourceKey<EquipmentAsset> SILACZ_ASSET = ResourceKey.create(
			EquipmentAssets.ROOT_ID, Identifier.withDefaultNamespace("leather"));

	public static final Item SILACZ_HELM = register("silacz_helm", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x965C3E))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.HEAD).setAsset(SILACZ_ASSET).setDamageOnHurt(false).build()));
	public static final Item SILACZ_KLATA = register("silacz_klata", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x965C3E))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.CHEST).setAsset(SILACZ_ASSET).setDamageOnHurt(false).build()));
	public static final Item SILACZ_SPODNIE = register("silacz_spodnie", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x965C3E))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.LEGS).setAsset(SILACZ_ASSET).setDamageOnHurt(false).build()));
	public static final Item SILACZ_BUTY = register("silacz_buty", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x965C3E))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.FEET).setAsset(SILACZ_ASSET).setDamageOnHurt(false).build()));


	// ===== Kostium Dolara (komplet 4 części, niezniszczalny) =====
	public static final ResourceKey<EquipmentAsset> DOLAR_ASSET = ResourceKey.create(
			EquipmentAssets.ROOT_ID, Identifier.withDefaultNamespace("leather"));

	public static final Item DOLAR_HELM = register("dolar_helm", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x5AC85A))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.HEAD).setAsset(DOLAR_ASSET).setDamageOnHurt(false).build()));
	public static final Item DOLAR_KLATA = register("dolar_klata", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x5AC85A))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.CHEST).setAsset(DOLAR_ASSET).setDamageOnHurt(false).build()));
	public static final Item DOLAR_SPODNIE = register("dolar_spodnie", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x5AC85A))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.LEGS).setAsset(DOLAR_ASSET).setDamageOnHurt(false).build()));
	public static final Item DOLAR_BUTY = register("dolar_buty", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x5AC85A))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.FEET).setAsset(DOLAR_ASSET).setDamageOnHurt(false).build()));

	// ===== Kostium Walentynkowy (komplet 4 części, niezniszczalny, różowy) =====
	public static final ResourceKey<EquipmentAsset> WALENTYNKI_ASSET = ResourceKey.create(
			EquipmentAssets.ROOT_ID, Identifier.withDefaultNamespace("leather"));

	public static final Item WALENTYNKI_HELM = register("walentynki_helm", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xFF80BF))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.HEAD).setAsset(WALENTYNKI_ASSET).setDamageOnHurt(false).build()));
	public static final Item WALENTYNKI_KLATA = register("walentynki_klata", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xFF80BF))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.CHEST).setAsset(WALENTYNKI_ASSET).setDamageOnHurt(false).build()));
	public static final Item WALENTYNKI_SPODNIE = register("walentynki_spodnie", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xFF80BF))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.LEGS).setAsset(WALENTYNKI_ASSET).setDamageOnHurt(false).build()));
	public static final Item WALENTYNKI_BUTY = register("walentynki_buty", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xFF80BF))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.FEET).setAsset(WALENTYNKI_ASSET).setDamageOnHurt(false).build()));

	// ===== Kostium Mima (komplet 4 części, niezniszczalny, czarny - wygląd skórzanej zbroi) =====
	public static final ResourceKey<EquipmentAsset> MIM_ASSET = ResourceKey.create(
			EquipmentAssets.ROOT_ID, Identifier.withDefaultNamespace("leather"));

	public static final Item MIM_HELM = register("mim_helm", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x1F1F24))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.HEAD).setAsset(MIM_ASSET).setDamageOnHurt(false).build()));
	public static final Item MIM_KLATA = register("mim_klata", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x1F1F24))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.CHEST).setAsset(MIM_ASSET).setDamageOnHurt(false).build()));
	public static final Item MIM_SPODNIE = register("mim_spodnie", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x1F1F24))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.LEGS).setAsset(MIM_ASSET).setDamageOnHurt(false).build()));
	public static final Item MIM_BUTY = register("mim_buty", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x1F1F24))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.FEET).setAsset(MIM_ASSET).setDamageOnHurt(false).build()));

	// ===== Kostium Pirata (komplet 4 części, niezniszczalny, ciemnobrązowy - wygląd skórzanej zbroi) =====
	public static final ResourceKey<EquipmentAsset> PIRAT_ASSET = ResourceKey.create(
			EquipmentAssets.ROOT_ID, Identifier.withDefaultNamespace("leather"));

	public static final Item PIRAT_HELM = register("pirat_helm", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x5A3A22))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.HEAD).setAsset(PIRAT_ASSET).setDamageOnHurt(false).build()));
	public static final Item PIRAT_KLATA = register("pirat_klata", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x5A3A22))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.CHEST).setAsset(PIRAT_ASSET).setDamageOnHurt(false).build()));
	public static final Item PIRAT_SPODNIE = register("pirat_spodnie", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x5A3A22))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.LEGS).setAsset(PIRAT_ASSET).setDamageOnHurt(false).build()));
	public static final Item PIRAT_BUTY = register("pirat_buty", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x5A3A22))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.FEET).setAsset(PIRAT_ASSET).setDamageOnHurt(false).build()));

	// ===== Kostium Przeciwzakażeniowy (komplet 4 części, niezniszczalny, żółty - wygląd skórzanej zbroi) =====
	public static final ResourceKey<EquipmentAsset> PZ_ASSET = ResourceKey.create(
			EquipmentAssets.ROOT_ID, Identifier.withDefaultNamespace("leather"));

	public static final Item PZ_HELM = register("przeciwzakazeniowy_helm", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xF5D020))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.HEAD).setAsset(PZ_ASSET).setDamageOnHurt(false).build()));
	public static final Item PZ_KLATA = register("przeciwzakazeniowy_klata", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xF5D020))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.CHEST).setAsset(PZ_ASSET).setDamageOnHurt(false).build()));
	public static final Item PZ_SPODNIE = register("przeciwzakazeniowy_spodnie", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xF5D020))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.LEGS).setAsset(PZ_ASSET).setDamageOnHurt(false).build()));
	public static final Item PZ_BUTY = register("przeciwzakazeniowy_buty", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xF5D020))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.FEET).setAsset(PZ_ASSET).setDamageOnHurt(false).build()));

	// ===== Kostium Zabójczego Królika (komplet 4 części, niezniszczalny, biały - wygląd skórzanej zbroi) =====
	public static final ResourceKey<EquipmentAsset> KROLIK_ASSET = ResourceKey.create(
			EquipmentAssets.ROOT_ID, Identifier.withDefaultNamespace("leather"));

	public static final Item KROLIK_HELM = register("krolik_helm", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xF2F2F2))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.HEAD).setAsset(KROLIK_ASSET).setDamageOnHurt(false).build()));
	public static final Item KROLIK_KLATA = register("krolik_klata", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xF2F2F2))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.CHEST).setAsset(KROLIK_ASSET).setDamageOnHurt(false).build()));
	public static final Item KROLIK_SPODNIE = register("krolik_spodnie", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xF2F2F2))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.LEGS).setAsset(KROLIK_ASSET).setDamageOnHurt(false).build()));
	public static final Item KROLIK_BUTY = register("krolik_buty", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xF2F2F2))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.FEET).setAsset(KROLIK_ASSET).setDamageOnHurt(false).build()));

	// ===== Kostium Małego Urwisa (komplet 4 części, niezniszczalny, jasnożółty - wygląd skórzanej zbroi) =====
	public static final ResourceKey<EquipmentAsset> URWIS_ASSET = ResourceKey.create(
			EquipmentAssets.ROOT_ID, Identifier.withDefaultNamespace("leather"));

	public static final Item URWIS_HELM = register("urwis_helm", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xFFF0A0))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.HEAD).setAsset(URWIS_ASSET).setDamageOnHurt(false).build()));
	public static final Item URWIS_KLATA = register("urwis_klata", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xFFF0A0))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.CHEST).setAsset(URWIS_ASSET).setDamageOnHurt(false).build()));
	public static final Item URWIS_SPODNIE = register("urwis_spodnie", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xFFF0A0))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.LEGS).setAsset(URWIS_ASSET).setDamageOnHurt(false).build()));
	public static final Item URWIS_BUTY = register("urwis_buty", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xFFF0A0))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.FEET).setAsset(URWIS_ASSET).setDamageOnHurt(false).build()));

	// ===== Kostium Nurka Głębinowego (komplet 4 części, niezniszczalny, ciemnozielony - wygląd skórzanej zbroi) =====
	public static final ResourceKey<EquipmentAsset> NUREK_ASSET = ResourceKey.create(
			EquipmentAssets.ROOT_ID, Identifier.withDefaultNamespace("leather"));

	public static final Item NUREK_HELM = register("nurek_helm", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x1E5A2A))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.HEAD).setAsset(NUREK_ASSET).setDamageOnHurt(false).build()));
	public static final Item NUREK_KLATA = register("nurek_klata", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x1E5A2A))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.CHEST).setAsset(NUREK_ASSET).setDamageOnHurt(false).build()));
	public static final Item NUREK_SPODNIE = register("nurek_spodnie", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x1E5A2A))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.LEGS).setAsset(NUREK_ASSET).setDamageOnHurt(false).build()));
	public static final Item NUREK_BUTY = register("nurek_buty", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x1E5A2A))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.FEET).setAsset(NUREK_ASSET).setDamageOnHurt(false).build()));

	// ===== Kostium Wampira (komplet 4 części, niezniszczalny, karmazynowy - wygląd skórzanej zbroi) =====
	public static final ResourceKey<EquipmentAsset> WAMPIR_ASSET = ResourceKey.create(
			EquipmentAssets.ROOT_ID, Identifier.withDefaultNamespace("leather"));

	public static final Item WAMPIR_HELM = register("wampir_helm", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xB01030))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.HEAD).setAsset(WAMPIR_ASSET).setDamageOnHurt(false).build()));
	public static final Item WAMPIR_KLATA = register("wampir_klata", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xB01030))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.CHEST).setAsset(WAMPIR_ASSET).setDamageOnHurt(false).build()));
	public static final Item WAMPIR_SPODNIE = register("wampir_spodnie", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xB01030))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.LEGS).setAsset(WAMPIR_ASSET).setDamageOnHurt(false).build()));
	public static final Item WAMPIR_BUTY = register("wampir_buty", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xB01030))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.FEET).setAsset(WAMPIR_ASSET).setDamageOnHurt(false).build()));

	// ===== Kostium Grincha (komplet 4 części, niezniszczalny, zielony - wygląd skórzanej zbroi) =====
	public static final ResourceKey<EquipmentAsset> KGRINCH_ASSET = ResourceKey.create(
			EquipmentAssets.ROOT_ID, Identifier.withDefaultNamespace("leather"));

	public static final Item KGRINCH_HELM = register("kgrinch_helm", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x2FB52F))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.HEAD).setAsset(KGRINCH_ASSET).setDamageOnHurt(false).build()));
	public static final Item KGRINCH_KLATA = register("kgrinch_klata", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x2FB52F))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.CHEST).setAsset(KGRINCH_ASSET).setDamageOnHurt(false).build()));
	public static final Item KGRINCH_SPODNIE = register("kgrinch_spodnie", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x2FB52F))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.LEGS).setAsset(KGRINCH_ASSET).setDamageOnHurt(false).build()));
	public static final Item KGRINCH_BUTY = register("kgrinch_buty", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x2FB52F))
					.component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.FEET).setAsset(KGRINCH_ASSET).setDamageOnHurt(false).build()));

	public static final Item PET_DRAKULA = register("pet_drakula", PetDrakulaItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item SERCE_ZYCIA = register("serce_zycia", SerceItem::new, new Item.Properties().stacksTo(16));

	public static final Item ZMIANKI = register("zmianki", ZmiankiItem::new, new Item.Properties().stacksTo(64));

	public static final Item KSIEGA_REGENERACJI = register("ksiega_regeneracji", p -> new KsiegaItem(p, KsiegaItem.REGENERACJA), new Item.Properties().stacksTo(16));

	public static final Item KSIEGA_KRYTYCZNE = register("ksiega_krytyczne", p -> new KsiegaItem(p, KsiegaItem.KRYTYCZNE), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_ULASKAWIENIE = register("ksiega_ulaskawienia", p -> new KsiegaItem(p, KsiegaItem.ULASKAWIENIE), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_WAMPIRYZM = register("ksiega_wampiryzmu", p -> new KsiegaItem(p, KsiegaItem.WAMPIRYZM), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_WYTRZYMALOSC = register("ksiega_wytrzymalosci", p -> new KsiegaItem(p, KsiegaItem.WYTRZYMALOSC), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_SZCZESCIE = register("ksiega_szczescia", p -> new KsiegaItem(p, KsiegaItem.SZCZESCIE), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_SZYBKOSC = register("ksiega_szybkosci", p -> new KsiegaItem(p, KsiegaItem.SZYBKOSC), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_WOLNOSC = register("ksiega_wolnosci", p -> new KsiegaItem(p, KsiegaItem.WOLNOSC), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_SPADANIE = register("ksiega_spadania", p -> new KsiegaItem(p, KsiegaItem.SPADANIE), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_NIEPODPALENIE = register("ksiega_niepodpalenia", p -> new KsiegaItem(p, KsiegaItem.NIEPODPALENIE), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_MIM = register("ksiega_mima", p -> new KsiegaItem(p, KsiegaItem.MIM), new Item.Properties().stacksTo(16));

	public static final Item KSIEGA_ZAMROZENIE = register("ksiega_zamrozenia", p -> new KsiegaItem(p, KsiegaItem.ZAMROZENIE), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_KZM = register("ksiega_kzm", p -> new KsiegaItem(p, KsiegaItem.KZM), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_PIORUNY = register("ksiega_piorunow", p -> new KsiegaItem(p, KsiegaItem.PIORUNY), new Item.Properties().stacksTo(16));

	public static final Item KSIEGA_CIERNIE = register("ksiega_ciernie", p -> new KsiegaItem(p, KsiegaItem.CIERNIE), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_SERCA = register("ksiega_serc", p -> new KsiegaItem(p, KsiegaItem.SERCA), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_ODBICIE = register("ksiega_odbicia", p -> new KsiegaItem(p, KsiegaItem.ODBICIE), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_NIEWIDZIALNOSC = register("ksiega_niewidzialnosci", p -> new KsiegaItem(p, KsiegaItem.NIEWIDZIALNOSC), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_ODDYCHANIE = register("ksiega_oddychania", p -> new KsiegaItem(p, KsiegaItem.ODDYCHANIE), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_AUTOWYTAPIANIE = register("ksiega_autowytapiania", p -> new KsiegaItem(p, KsiegaItem.AUTOWYTAPIANIE), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_MAGNES = register("ksiega_magnesu", p -> new KsiegaItem(p, KsiegaItem.MAGNES), new Item.Properties().stacksTo(16));
	public static final Item KSIEGA_DOSWIADCZENIE = register("ksiega_doswiadczenia", p -> new KsiegaItem(p, KsiegaItem.DOSWIADCZENIE), new Item.Properties().stacksTo(16));

	public static final Item PET_BALWANEK = register("pet_balwanek", PetBalwanekItem::new,
			new Item.Properties().stacksTo(1));


	// ===== 13 nowych eventówek =====
	public static final Item SLIMOWY_WIOSNA = register("slimowy_wiosna", SlimowyWiosnaItem::new,
			new Item.Properties().stacksTo(1));
	public static final Item KULKA_BALWANA = register("kulka_balwana", KulkaBalwanaItem::new,
			new Item.Properties().stacksTo(32));
	public static final Item WEDKA_18 = register("wedka_18", Wedka18Item::new,
			new Item.Properties().stacksTo(1));
	public static final Item FAJERWERKA_WYRZUTNI = register("fajerwerka_wyrzutni", FajerwerkaWyrzutniItem::new,
			new Item.Properties().stacksTo(1));
	public static final Item ROZDZKA_MAGIKA = register("rozdzka_magika", RozdzkaMagikaItem::new,
			new Item.Properties().stacksTo(1));
	public static final Item PERELKA_ZAMIANKA = register("perelka_zamianka", PerelkaZamiankaItem::new,
			new Item.Properties().stacksTo(16));
	public static final Item WEDECZKA_PERELECZKA = register("wedeczka_perelka", FishingRodItem::new,
			new Item.Properties().stacksTo(1));
	public static final Item WILK = register("wilk", WilkItem::new,
			new Item.Properties().stacksTo(16));
	public static final Item DZIALKO = register("dzialko",
			props -> new BlockItem(ModBlocks.DZIALKO, props),
			new Item.Properties().stacksTo(64));
	public static final Item SHULEK_BOXEK = register("shulek_boxek", ShulekBoxekItem::new,
			new Item.Properties().stacksTo(1));
	public static final Item DYNAMIT = register("dynamit", DynamitItem::new,
			new Item.Properties().stacksTo(64));
	public static final Item BOSKI_TOPOR = register("boski_topor", BoskiToporItem::new,
			new Item.Properties().stacksTo(1).axe(ToolMaterial.NETHERITE, 5.0F, -3.0F));
	public static final Item ROG_KOOZY = register("rog_koozy", RogKoozyItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item SKRZYNIA_RZADKA = register("skrzynia_rzadka",
			props -> new BlockItem(ModBlocks.SKRZYNIA_RZADKA, props), new Item.Properties().stacksTo(64));
	public static final Item KLUCZ_RZADKA = register("klucz_rzadka", Item::new, new Item.Properties().stacksTo(64));
	public static final Item SKRZYNIA_EPICKA = register("skrzynia_epicka",
			props -> new BlockItem(ModBlocks.SKRZYNIA_EPICKA, props), new Item.Properties().stacksTo(64));
	public static final Item KLUCZ_EPICKA = register("klucz_epicka", Item::new, new Item.Properties().stacksTo(64));
	public static final Item SKRZYNIA_ANARCHICZNA = register("skrzynia_anarchiczna",
			props -> new BlockItem(ModBlocks.SKRZYNIA_ANARCHICZNA, props), new Item.Properties().stacksTo(64));
	public static final Item KLUCZ_ANARCHICZNA = register("klucz_anarchiczna", Item::new, new Item.Properties().stacksTo(64));
	public static final Item SKRZYNIA_PETY = register("skrzynia_pety",
			props -> new BlockItem(ModBlocks.SKRZYNIA_PETY, props), new Item.Properties().stacksTo(64));
	public static final Item KLUCZ_PETY = register("klucz_pety", Item::new, new Item.Properties().stacksTo(64));
	public static final Item SKRZYNIA_KOSTIUMY = register("skrzynia_kostiumy",
			props -> new BlockItem(ModBlocks.SKRZYNIA_KOSTIUMY, props), new Item.Properties().stacksTo(64));
	public static final Item KLUCZ_KOSTIUMY = register("klucz_kostiumy", Item::new, new Item.Properties().stacksTo(64));
	public static final Item SKRZYNIA_DOLAROWA = register("skrzynia_dolarowa",
			props -> new BlockItem(ModBlocks.SKRZYNIA_DOLAROWA, props), new Item.Properties().stacksTo(64));
	public static final Item KLUCZ_DOLAROWA = register("klucz_dolarowa", Item::new, new Item.Properties().stacksTo(64));
	public static final Item SKRZYNIA_EVENTOWA = register("skrzynia_eventowa",
			props -> new BlockItem(ModBlocks.SKRZYNIA_EVENTOWA, props), new Item.Properties().stacksTo(64));
	public static final Item KLUCZ_EVENTOWA = register("klucz_eventowa", Item::new, new Item.Properties().stacksTo(64));

	public static final Item SKRZYNIA_SWIATECZNA = register("skrzynia_swiateczna",
			props -> new BlockItem(ModBlocks.SKRZYNIA_SWIATECZNA, props), new Item.Properties().stacksTo(64));
	public static final Item KLUCZ_SWIATECZNA = register("klucz_swiateczna", Item::new, new Item.Properties().stacksTo(64));
	public static final Item SKRZYNIA_WALENTYNKOWA = register("skrzynia_walentynkowa",
			props -> new BlockItem(ModBlocks.SKRZYNIA_WALENTYNKOWA, props), new Item.Properties().stacksTo(64));
	public static final Item KLUCZ_WALENTYNKOWA = register("klucz_walentynkowa", Item::new, new Item.Properties().stacksTo(64));
	public static final Item SKRZYNIA_HALLOWEENOWA = register("skrzynia_halloweenowa",
			props -> new BlockItem(ModBlocks.SKRZYNIA_HALLOWEENOWA, props), new Item.Properties().stacksTo(64));
	public static final Item KLUCZ_HALLOWEENOWA = register("klucz_halloweenowa", Item::new, new Item.Properties().stacksTo(64));
	public static final Item SKRZYNIA_SZKOLNA = register("skrzynia_szkolna",
			props -> new BlockItem(ModBlocks.SKRZYNIA_SZKOLNA, props), new Item.Properties().stacksTo(64));
	public static final Item KLUCZ_SZKOLNA = register("klucz_szkolna", Item::new, new Item.Properties().stacksTo(64));
	public static final Item SKRZYNIA_NOWE_EVENTOWKI = register("skrzynia_nowe_eventowki",
			props -> new BlockItem(ModBlocks.SKRZYNIA_NOWE_EVENTOWKI, props), new Item.Properties().stacksTo(64));
	public static final Item KLUCZ_NOWE_EVENTOWKI = register("klucz_nowe_eventowki", Item::new, new Item.Properties().stacksTo(64));
	public static final Item SKRZYNIA_ZAJACA = register("skrzynia_zajaca",
			props -> new BlockItem(ModBlocks.SKRZYNIA_ZAJACA, props), new Item.Properties().stacksTo(64));
	public static final Item KLUCZ_ZAJACA = register("klucz_zajaca", Item::new, new Item.Properties().stacksTo(64));


	// ===== Zbroje (netherytowe, zaklęcia nakłada ZbrojeHandler) =====
	public static final Item ZBROJA_DOLARA_HELM = register("zbroja_dolara_helm", Item::new,
			new Item.Properties().humanoidArmor(net.minecraft.world.item.equipment.ArmorMaterials.NETHERITE,
					net.minecraft.world.item.equipment.ArmorType.HELMET));
	public static final Item ZBROJA_DOLARA_KLATA = register("zbroja_dolara_klata", Item::new,
			new Item.Properties().humanoidArmor(net.minecraft.world.item.equipment.ArmorMaterials.NETHERITE,
					net.minecraft.world.item.equipment.ArmorType.CHESTPLATE));
	public static final Item ZBROJA_DOLARA_SPODNIE = register("zbroja_dolara_spodnie", Item::new,
			new Item.Properties().humanoidArmor(net.minecraft.world.item.equipment.ArmorMaterials.NETHERITE,
					net.minecraft.world.item.equipment.ArmorType.LEGGINGS));
	public static final Item ZBROJA_DOLARA_BUTY = register("zbroja_dolara_buty", Item::new,
			new Item.Properties().humanoidArmor(net.minecraft.world.item.equipment.ArmorMaterials.NETHERITE,
					net.minecraft.world.item.equipment.ArmorType.BOOTS));
	public static final Item ANARCHICZNA_ZBROJA_2_HELM = register("anarchiczna_zbroja_2_helm", Item::new,
			new Item.Properties().humanoidArmor(net.minecraft.world.item.equipment.ArmorMaterials.NETHERITE,
					net.minecraft.world.item.equipment.ArmorType.HELMET));
	public static final Item ANARCHICZNA_ZBROJA_2_KLATA = register("anarchiczna_zbroja_2_klata", Item::new,
			new Item.Properties().humanoidArmor(net.minecraft.world.item.equipment.ArmorMaterials.NETHERITE,
					net.minecraft.world.item.equipment.ArmorType.CHESTPLATE));
	public static final Item ANARCHICZNA_ZBROJA_2_SPODNIE = register("anarchiczna_zbroja_2_spodnie", Item::new,
			new Item.Properties().humanoidArmor(net.minecraft.world.item.equipment.ArmorMaterials.NETHERITE,
					net.minecraft.world.item.equipment.ArmorType.LEGGINGS));
	public static final Item ANARCHICZNA_ZBROJA_2_BUTY = register("anarchiczna_zbroja_2_buty", Item::new,
			new Item.Properties().humanoidArmor(net.minecraft.world.item.equipment.ArmorMaterials.NETHERITE,
					net.minecraft.world.item.equipment.ArmorType.BOOTS));

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
