package com.example.mymod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyMod implements ModInitializer {
	public static final String MOD_ID = "mymod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ResourceKey<CreativeModeTab> KSIEGI_TAB_KEY = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MOD_ID, "ksiegi"));

	public static final ResourceKey<CreativeModeTab> EVENT_TAB_KEY = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MOD_ID, "eventowki"));

	public static final ResourceKey<CreativeModeTab> PETY_TAB_KEY = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MOD_ID, "pety_kostiumy"));

	public static final ResourceKey<CreativeModeTab> SKRZYNIE_TAB_KEY = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MOD_ID, "skrzynie"));

	@Override
	public void onInitialize() {
		ModBlocks.initialize();
		ModItems.initialize();
		ChydroKlatkaManager.initialize();
		GrinchAxeHandler.initialize();
		AnarchiaKoronaHandler.initialize();
		WodnyTrojzabManager.initialize();
		ZatopionyStatekManager.initialize();
		EventoweAtakiHandler.initialize();
		ExcaliburHandler.initialize();
		TotemUlaskawieniaHandler.initialize();
		RozdzkaIluzjonistyManager.initialize();
		WzmocnionaElytraHandler.initialize();
		SakiewkaItem.initialize();
		LatarniaManager.initialize();
		WampirzeJablkoItem.initialize();
		WedkaNielotaManager.initialize();
		ZmutowanyCreeperManager.initialize();
		RozgaHandler.initialize();
		SmoczyMieczHandler.initialize();
		JednorozecManager.initialize();
		KosaKlatkaManager.initialize();
		OffhandEventowkiHandler.initialize();
		Eventowki46Handler.initialize();
		BalonikZHeliemItem.initialize();
		WedkaGwardianaItem.initialize();
		ZatrutyTrunekItem.initialize();
		ArcusMagnusItem.initialize();
		SilaczHandler.initialize();
		DolarHandler.initialize();
		BotManager.initialize();
		Inkognito.initialize();
		AdminCommands.initialize();
		Antylogout.initialize();
		SpawnManager.initialize();
		ZlotyBilet.initialize();
		KluczykManager.initialize();
		WalentynkiHandler.initialize();
		MimHandler.initialize();
		PiratHandler.initialize();
		PrzeciwzakazeniowyHandler.initialize();
		KrolikHandler.initialize();
		UrwisHandler.initialize();
		NurekHandler.initialize();
		WampirHandler.initialize();
		SerceItem.initialize();
		ZmiankiItem.initialize();
		KsiegaItem.initialize();
		KsiegiHandler.initialize();
		RangiManager.initialize();
		StatsSidebar.initialize();
		SklepManager.initialize();
		// Skrzyń nie da się zniszczyć, nawet w trybie kreatywnym.
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register(
				(level, player, pos, state, blockEntity) -> !(state.getBlock() instanceof CrateBlock));
		GrinchCostumeHandler.initialize();
		KostiumDresser.initialize();
		PetBalwanekItem.initialize();
		PetDrakulaItem.initialize();
		SlimowyWiosnaItem.initialize();
		ThrowTracker.initialize();
		KostkaRubikaItem.initialize();
		WedkiHandler.initialize();
		FajerwerkaWyrzutniItem.initialize();
		RozdzkaMagikaItem.initialize();
		WilkItem.initialize();
		DzialkoManager.initialize();
		ShulekBoxekItem.initialize();
		DynamitItem.initialize();
		BoskiToporItem.initialize();
		SkrzynieManager.initialize();
		ZbrojeHandler.initialize();

		// Zakładka z księgami zaklęć - rejestrowana przed Eventówkami, więc jest przed nimi.
		CreativeModeTab ksiegiTab = FabricCreativeModeTab.builder()
				.icon(() -> new ItemStack(ModItems.KSIEGA_REGENERACJI))
				.title(Component.translatable("creativeTab.mymod.ksiegi"))
				.displayItems((params, output) -> {
					output.accept(ModItems.KSIEGA_REGENERACJI);
					output.accept(ModItems.KSIEGA_KRYTYCZNE);
					output.accept(ModItems.KSIEGA_ULASKAWIENIE);
					output.accept(ModItems.KSIEGA_WAMPIRYZM);
					output.accept(ModItems.KSIEGA_WYTRZYMALOSC);
					output.accept(ModItems.KSIEGA_SZCZESCIE);
					output.accept(ModItems.KSIEGA_SZYBKOSC);
					output.accept(ModItems.KSIEGA_WOLNOSC);
					output.accept(ModItems.KSIEGA_SPADANIE);
					output.accept(ModItems.KSIEGA_NIEPODPALENIE);
					output.accept(ModItems.KSIEGA_MIM);
					output.accept(ModItems.KSIEGA_ZAMROZENIE);
					output.accept(ModItems.KSIEGA_KZM);
					output.accept(ModItems.KSIEGA_PIORUNY);
					output.accept(ModItems.KSIEGA_CIERNIE);
					output.accept(ModItems.KSIEGA_SERCA);
					output.accept(ModItems.KSIEGA_ODBICIE);
					output.accept(ModItems.KSIEGA_NIEWIDZIALNOSC);
					output.accept(ModItems.KSIEGA_ODDYCHANIE);
					output.accept(ModItems.KSIEGA_AUTOWYTAPIANIE);
					output.accept(ModItems.KSIEGA_MAGNES);
					output.accept(ModItems.KSIEGA_DOSWIADCZENIE);
					output.accept(ModItems.RANGA_VIP);
					output.accept(ModItems.RANGA_SVIP);
					output.accept(ModItems.RANGA_MVIP);
					output.accept(ModItems.RANGA_GVIP);
					output.accept(ModItems.RANGA_DVIP);
					output.accept(ModItems.RANGA_TWORCA);
				})
				.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KSIEGI_TAB_KEY, ksiegiTab);

		CreativeModeTab eventTab = FabricCreativeModeTab.builder()
				.icon(() -> new ItemStack(ModItems.GRINCH_AXE))
				.title(Component.translatable("creativeTab.mymod.eventowki"))
				.displayItems((params, output) -> {
					output.accept(ModItems.CHYDRO_KLATKA);
					output.accept(ModItems.GRINCH_AXE);
					output.accept(ModItems.ANARCHIA_KORONA);
					output.accept(ModItems.WODNY_TROJZAB);
					output.accept(ModItems.ZATOPIONY_STATEK);
					output.accept(ModItems.PLATEK_SNIEGU);
					output.accept(ModItems.CZERWONY_BALON);
					output.accept(ModItems.PARASOL);
					output.accept(ModItems.SERCE);
					output.accept(ModItems.EXCALIBUR);
					output.accept(ModItems.BLOK_WIDMO);
					output.accept(ModItems.TOTEM_ULASKAWIENIA);
					output.accept(ModItems.ROZDZKA_ILUZJONISTY);
					output.accept(ModItems.WZMOCNIONA_ELYTRA);
					output.accept(ModItems.SAKIEWKA);
					output.accept(ModItems.KREW_WAMPIRA);
					output.accept(ModItems.CUDOWNA_LATARNIA);
					output.accept(ModItems.BOMBA_MAKSIMA);
					output.accept(ModItems.WAMPIRZE_JABLKO);
					output.accept(ModItems.WEDKA_NIELOTA);
					output.accept(ModItems.JAJKO_ZMUTOWANEGO_CREEPERA);
					output.accept(ModItems.ROZGA);
					output.accept(ModItems.SMOCZY_MIECZ);
					output.accept(ModItems.ROG_JEDNOROZCA);
					output.accept(ModItems.KOSA);
					output.accept(ModItems.KOSTKA_RUBIKA);
					output.accept(ModItems.ROZA_KUPIDYNA);
					output.accept(ModItems.CIEPLE_MLEKO);
					output.accept(ModItems.LIZAK);
					output.accept(ModItems.PIEKIELNY_MIECZ);
					output.accept(ModItems.WATA_CUKROWA);
					output.accept(ModItems.KROLICZY_MIECZ);
					output.accept(ModItems.TURBO_TRAP);
					output.accept(ModItems.LOPATA_GRINCHA);
					output.accept(ModItems.WEDKA_GWARDIANA);
					output.accept(ModItems.ZATRUTY_OLOWEK);
					output.accept(ModItems.ARCUS_MAGNUS);
					output.accept(ModItems.MARCHEWKOWY_MIECZ);
					output.accept(ModItems.MARCHEWKOWA_KUSZA);
					output.accept(ModItems.BALONIK_Z_HELEM);
					output.accept(ModItems.PIERNIK);
					output.accept(ModItems.ZATRUTY_TRUNEK);
					output.accept(ModItems.SPLESNIALA_KANAPKA);
					output.accept(ModItems.ANARCHICZNY_KILOF);
					output.accept(ModItems.ROZGOTOWANA_KUKURYDZA);
					output.accept(ModItems.LUK_KUPIDYNA);
					output.accept(ModItems.SLIMOWY_WIOSNA);
					output.accept(ModItems.KULKA_BALWANA);
					output.accept(ModItems.WEDKA_18);
					output.accept(ModItems.FAJERWERKA_WYRZUTNI);
					output.accept(ModItems.ROZDZKA_MAGIKA);
					output.accept(ModItems.PERELKA_ZAMIANKA);
					output.accept(ModItems.WEDECZKA_PERELECZKA);
					output.accept(ModItems.WILK);
					output.accept(ModItems.DZIALKO);
					output.accept(ModItems.SHULEK_BOXEK);
					output.accept(ModItems.DYNAMIT);
					output.accept(ModItems.BOSKI_TOPOR);
					output.accept(ModItems.ZBROJA_DOLARA_HELM);
					output.accept(ModItems.ZBROJA_DOLARA_KLATA);
					output.accept(ModItems.ZBROJA_DOLARA_SPODNIE);
					output.accept(ModItems.ZBROJA_DOLARA_BUTY);
					output.accept(ModItems.ANARCHICZNA_ZBROJA_2_HELM);
					output.accept(ModItems.ANARCHICZNA_ZBROJA_2_KLATA);
					output.accept(ModItems.ANARCHICZNA_ZBROJA_2_SPODNIE);
					output.accept(ModItems.ANARCHICZNA_ZBROJA_2_BUTY);
					output.accept(ModItems.ROG_KOOZY);
					// Kolejne eventówki dopisujemy tutaj.
				})
				.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, EVENT_TAB_KEY, eventTab);

		CreativeModeTab petyTab = FabricCreativeModeTab.builder()
				.icon(() -> new ItemStack(ModItems.DOLAR_KLATA))
				.title(Component.translatable("creativeTab.mymod.pety_kostiumy"))
				.displayItems((params, output) -> {
					output.accept(ModItems.SILACZ_KLATA);
					output.accept(ModItems.DOLAR_KLATA);
					output.accept(ModItems.WALENTYNKI_KLATA);
					output.accept(ModItems.MIM_KLATA);
					output.accept(ModItems.PIRAT_KLATA);
					output.accept(ModItems.PZ_KLATA);
					output.accept(ModItems.KROLIK_KLATA);
					output.accept(ModItems.URWIS_KLATA);
					output.accept(ModItems.NUREK_KLATA);
					output.accept(ModItems.WAMPIR_KLATA);
					output.accept(ModItems.KGRINCH_KLATA);
					output.accept(ModItems.PET_BALWANEK);
					output.accept(ModItems.PET_DRAKULA);
					output.accept(ModItems.SERCE_ZYCIA);
					output.accept(ModItems.ZMIANKI);
					// Kolejne kostiumy i pety dopisujemy tutaj.
				})
				.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, PETY_TAB_KEY, petyTab);

		CreativeModeTab skrzynieTab = FabricCreativeModeTab.builder()
				.icon(() -> new ItemStack(ModItems.SKRZYNIA_RZADKA))
				.title(Component.translatable("creativeTab.mymod.skrzynie"))
				.displayItems((params, output) -> {
					output.accept(ModItems.SKRZYNIA_RZADKA);
					output.accept(ModItems.KLUCZ_RZADKA);
					output.accept(ModItems.SKRZYNIA_EPICKA);
					output.accept(ModItems.KLUCZ_EPICKA);
					output.accept(ModItems.SKRZYNIA_ANARCHICZNA);
					output.accept(ModItems.KLUCZ_ANARCHICZNA);
					output.accept(ModItems.SKRZYNIA_PETY);
					output.accept(ModItems.KLUCZ_PETY);
					output.accept(ModItems.SKRZYNIA_KOSTIUMY);
					output.accept(ModItems.KLUCZ_KOSTIUMY);
					output.accept(ModItems.SKRZYNIA_DOLAROWA);
					output.accept(ModItems.KLUCZ_DOLAROWA);
					output.accept(ModItems.SKRZYNIA_EVENTOWA);
					output.accept(ModItems.KLUCZ_EVENTOWA);
					output.accept(ModItems.SKRZYNIA_SWIATECZNA);
					output.accept(ModItems.KLUCZ_SWIATECZNA);
					output.accept(ModItems.SKRZYNIA_WALENTYNKOWA);
					output.accept(ModItems.KLUCZ_WALENTYNKOWA);
					output.accept(ModItems.SKRZYNIA_HALLOWEENOWA);
					output.accept(ModItems.KLUCZ_HALLOWEENOWA);
					output.accept(ModItems.SKRZYNIA_SZKOLNA);
					output.accept(ModItems.KLUCZ_SZKOLNA);
					output.accept(ModItems.SKRZYNIA_NOWE_EVENTOWKI);
					output.accept(ModItems.KLUCZ_NOWE_EVENTOWKI);
					output.accept(ModItems.SKRZYNIA_ZAJACA);
					output.accept(ModItems.KLUCZ_ZAJACA);
					// Kolejne skrzynie i klucze dopisujemy tutaj.
				})
				.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, SKRZYNIE_TAB_KEY, skrzynieTab);

		LOGGER.info("My Mod załadowany!");
	}
}
