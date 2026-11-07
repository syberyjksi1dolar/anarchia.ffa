package com.example.mymod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricCreativeModeTab;
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

	public static final ResourceKey<CreativeModeTab> EVENT_TAB_KEY = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MOD_ID, "eventowki"));

	@Override
	public void onInitialize() {
		ModBlocks.initialize();
		ModItems.initialize();
		ChydroKlatkaManager.initialize();
		GrinchAxeHandler.initialize();

		CreativeModeTab eventTab = FabricCreativeModeTab.builder()
				.icon(() -> new ItemStack(ModItems.GRINCH_AXE))
				.title(Component.translatable("creativeTab.mymod.eventowki"))
				.displayItems((params, output) -> {
					output.accept(ModItems.CHYDRO_KLATKA);
					output.accept(ModItems.GRINCH_AXE);
					// Kolejne eventówki dopisujemy tutaj.
				})
				.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, EVENT_TAB_KEY, eventTab);

		LOGGER.info("My Mod załadowany!");
	}
}
